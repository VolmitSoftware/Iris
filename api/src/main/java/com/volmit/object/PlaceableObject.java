package com.volmit.object;

import com.volmit.geometry.BlockPosition;

/**
 * An object ready to be placed: one of the pack's, a scaled variant of one, or one a module built
 * ({@link PackObjects}). Its size is the size it was declared with, which boring and terrain bends cover even where it
 * has no block. Immutable; safe to share between threads.
 */
public interface PlaceableObject {
    /**
     * The object's name: the key the pack's files name it by, such as {@code trees/oak}, also for its scaled variants, or
     * the name the module that built it gave it.
     */
    String name();

    int width();

    int height();

    int depth();

    /** The block the object's offsets count from, relative to its corner. */
    BlockPosition center();

    /** The object's blocks at their offsets from {@link #center()}, so the geometry's anchor is the origin. */
    GeneratedObject geometry();
}
