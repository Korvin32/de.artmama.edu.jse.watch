package de.artmama.edu.jse.watch;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * See: https://docs.oracle.com/javase/tutorial/essential/io/notification.html#concerns
 * @author Oracle Tuorial
 * @author Kostya-Julia
 * @since 18.06.2015
 */
public class WatchDir {
    
    protected final class WatchServiceRegistraionVisitor extends SimpleFileVisitor<Path> {
		private final Logger ILOG = LoggerFactory.getLogger(WatchServiceRegistraionVisitor.class);

		@Override
		public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
		    ILOG.info("{}dir={}.", M_IN, dir);
		    register(dir);
		    ILOG.info(M_OUT);
		    return FileVisitResult.CONTINUE;
		}
	}

    private static final Logger LOG = LoggerFactory.getLogger(WatchDir.class);
    
    private static final String WATCH_TARGET_PATH_PROPERTY_NAME = "watch.target.path";
    
	private static final String M_IN = "-->|";
	private static final String M_M = "---|";
	private static final String M_OUT = "<--|";

    private final WatchService watcher;
    private final Map<WatchKey, Path> keys;
    private final Path targetPath;
    private final boolean recursive;
    private boolean trace = false;

    /**
     * Creates a WatchService and registers the given directory
     */
    public WatchDir(Path startDir, boolean recursive) throws IOException {
        LOG.info("{}WatchDir: dir={}, recursive={}.", M_IN, startDir, recursive);
        this.watcher = FileSystems.getDefault().newWatchService();
        this.keys = new HashMap<WatchKey, Path>();
        this.targetPath = startDir;
        this.recursive = recursive;
        LOG.info("{}WatchDir.", M_OUT);
    }

	public void init() throws IOException {
		LOG.info(M_IN);
		if (recursive) {
            LOG.info("{}Scanning {} ...", M_M, targetPath);
            registerAll(targetPath);
            LOG.info("{}Done.", M_M);
        } else {
            register(targetPath);
        }
        // enable trace after initial registration
        this.trace = true;
        LOG.info(M_OUT);
	}

    /**
     * Register the given directory, and all its sub-directories, with the
     * WatchService.
     */
    protected void registerAll(final Path start) throws IOException {
        LOG.info("{}start={}.", M_IN, start);
        // register directory and sub-directories
        WatchServiceRegistraionVisitor visitor = new WatchServiceRegistraionVisitor();
		Files.walkFileTree(start, visitor);
        LOG.info(M_OUT);
    }

    /**
     * Register the given directory with the WatchService
     */
    protected void register(Path dir) throws IOException {
        LOG.info("{}dir={}.", M_IN, dir);
        WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
        if (trace) {
            Path prev = keys.get(key);
            if (prev == null) {
                LOG.info("{}{}.", M_M, dir);
            } else {
                if (!dir.equals(prev)) {
                    LOG.info("{}{} -> {}.", M_M, prev, dir);
                }
            }
        }
        keys.put(key, dir);
        LOG.info(M_OUT);
    }
    
    /**
     * Process all events for keys queued to the watcher
     */
    @SuppressWarnings("rawtypes")
    public void processEvents() {
        LOG.info("{}Enter event processing loop...", M_IN);
        for (;;) {

            // wait for key to be signaled
            WatchKey key;
            try {
                key = watcher.take();
            } catch (InterruptedException e) {
                LOG.error(e.getMessage(), e);
                return;
            }

            Path dir = keys.get(key);
            
            if (dir == null) {
                LOG.info("{}WatchKey '{}' not recognized!!", M_M, key);
                continue;
            }

            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind eventKind = event.kind();

                // TBD - provide example of how OVERFLOW event is handled
                if (eventKind == OVERFLOW) {
                    LOG.info("{}{}: Not yet handler provided for this kind of event.", M_M, eventKind);
                    continue;
                }

                // Context for directory entry event is the file name of entry
                WatchEvent<Path> ev = WatchUtils.cast(event);
                Path name = ev.context();
                Path child = dir.resolve(name);
                boolean isDirectory = Files.isDirectory(child, NOFOLLOW_LINKS);

                // print out event
                LOG.info("{}{}: {} {}", M_M, eventKind, child, isDirectory ? "[DIRECTORY]" : "");

                // if directory is created, and watching is recursively, then register it and its sub-directories
                boolean isEventKindCreated = eventKind == ENTRY_CREATE;
				if (recursive && isEventKindCreated && isDirectory) {
                    try {
                    	registerAll(child);
                    } catch (IOException x) {
                        // ignore to keep sample readable
                    }
                }
            }

            // reset key and remove from set if directory no longer accessible
            boolean valid = key.reset();
            if (!valid) {
                keys.remove(key);

                // all directories are inaccessible
                if (keys.isEmpty()) {
                	LOG.info("{}Key map is empty - nothing to watch!", M_M);
                	break;
                }
            }
        }
        LOG.info(M_OUT);
    }

    private static void usage() {
        LOG.error("usage: java WatchDir [-r] dir");
        System.exit(-1);
    }

    public static void main(String[] args) throws IOException {
    	LOG.info(M_IN);
        // parse arguments
//        if (args.length == 0 || args.length > 2) {
//            usage();
//        }
//        boolean recursive = false;
//        int dirArg = 0;
//        if (args[0].equals("-r")) {
//            if (args.length < 2) {
//                usage();
//            }
//            recursive = true;
//            dirArg++;
//        }

        
        // register directory and process its events
//        Path dir = Paths.get(args[dirArg]);
        
    	String watchTargetPath = System.getProperties().getProperty(WATCH_TARGET_PATH_PROPERTY_NAME);
    	if (watchTargetPath == null) {
    	    LOG.error("Path to watch is not specified! Set the value for property:" + WATCH_TARGET_PATH_PROPERTY_NAME);
    	    LOG.info(M_OUT);
    	    System.exit(-1);
    	}
    	
        Path dir = Paths.get(watchTargetPath);
        boolean recursive = true;
        WatchDir watchDir = new WatchDir(dir, recursive);
        watchDir.init();
		watchDir.processEvents();
		LOG.info(M_OUT);
    }
}
