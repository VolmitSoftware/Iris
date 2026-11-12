package com.volmit.resource;

import java.util.List;
import java.util.Objects;

/**
 * One snippet file of a pack, read as the type its folder holds.
 *
 * @param path    the file below the type's snippet folder, without {@code .json}, such as {@code meadow/flowers}
 * @param entries what the file holds: one entry, or several when it holds a list; none when it could not be read
 */
public record PackSnippet<T>(String path, List<T> entries) {
    public PackSnippet {
        Objects.requireNonNull(path, "path");
        entries = List.copyOf(entries);
    }

    /**
     * Where in the pack's snippet files a settings value was read from.
     *
     * @param path  the file, as {@link PackSnippet#path()}
     * @param entry which of the file's {@link PackSnippet#entries()} the value is, from 0
     */
    public record Source(String path, int entry) {
        public Source {
            Objects.requireNonNull(path, "path");
            if (entry < 0) {
                throw new IllegalArgumentException("A snippet file's entries count from 0, not " + entry);
            }
        }
    }
}
