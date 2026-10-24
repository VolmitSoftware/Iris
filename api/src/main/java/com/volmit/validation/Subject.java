package com.volmit.validation;

import com.volmit.resource.ResourceId;
import java.util.Objects;
import java.util.regex.Pattern;

/** What a {@link Problem} concerns: a resource of the pack, by id, or a file of the pack, by its path. */
public sealed interface Subject {
    /** The resource {@code id}, such as {@code overworld:castle}. */
    static Subject resource(ResourceId id) {
        return new Resource(id);
    }

    /** The file at {@code path} below the pack root, with {@code /} between folders, such as {@code structures/castle.json}. */
    static Subject file(String path) {
        return new PackFile(path);
    }

    /** A resource of the pack. One id can name resources of several kinds, so a file is clearer where the kind matters. */
    record Resource(ResourceId id) implements Subject {
        public Resource {
            Objects.requireNonNull(id, "id");
        }

        @Override
        public String toString() {
            return id.toString();
        }
    }

    /** A file below the pack root. */
    record PackFile(String path) implements Subject {
        private static final Pattern SEGMENTS = Pattern.compile("[^/\\\\]+(/[^/\\\\]+)*");
        private static final Pattern RELATIVE_SEGMENT = Pattern.compile("(^|/)\\.\\.?(/|$)");

        public PackFile {
            Objects.requireNonNull(path, "path");
            if (!SEGMENTS.matcher(path).matches() || RELATIVE_SEGMENT.matcher(path).find()) {
                throw new IllegalArgumentException("File '" + path + "' is not a path below the pack root;"
                        + " write it relative to the pack with '/' between folders, such as structures/castle.json");
            }
        }

        @Override
        public String toString() {
            return path;
        }
    }
}
