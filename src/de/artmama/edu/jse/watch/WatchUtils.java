package de.artmama.edu.jse.watch;

import java.nio.file.WatchEvent;

public final class WatchUtils {

    @SuppressWarnings("unchecked")
    public static <T> WatchEvent<T> cast(WatchEvent<?> event) {
        return (WatchEvent<T>) event;
    }

}
