package com.volmit.tooling;

import java.io.IOException;
import java.nio.file.Path;

/** A change to the pack's files, run by {@link ToolPack#edit}. */
@FunctionalInterface
public interface PackEdit<T> {
    /** Changes files below {@code packRoot}; atomicity across files is the edit's own job. */
    T apply(Path packRoot) throws IOException;
}
