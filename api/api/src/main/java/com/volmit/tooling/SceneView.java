package com.volmit.tooling;

import com.volmit.object.GeneratedObject;
import com.volmit.world.PackWorld;

import java.util.List;
import java.util.Optional;

/**
 * A studio view of the block scenes a module builds from the pack, such as its objects: a catalogue of entries, each
 * with variants the studio draws in 3D.
 */
public non-sealed interface SceneView extends StudioView {
    /** The view's entries in the world's pack, in the order the studio lists them. */
    List<SceneEntry> entries(PackWorld world);

    /**
     * The blocks of one variant of the entry named {@code key}, its anchor where the studio centres the scene; empty
     * when the pack has no such entry, or the entry has fewer variants.
     *
     * @param variant from 0 to the entry's {@link SceneEntry#variants()}, exclusive
     */
    Optional<GeneratedObject> scene(PackWorld world, String key, int variant);

    /** Whether the entries are built from parts the studio can pick out of the scene, which {@link #parts} tells apart. */
    default boolean hasParts() {
        return false;
    }

    /**
     * Which part placed each block of the scene {@link #scene} gives for the same arguments, found the way that scene
     * was built. Asked off the server's threads; empty when the view has no parts, the pack has no such entry, or the
     * entry has fewer variants.
     */
    default Optional<BlockParts> parts(PackWorld world, String key, int variant) {
        return Optional.empty();
    }

    /** Whether the entries are baked from settings, which costs generation time {@link #timeBake} can measure. */
    default boolean isBaked() {
        return false;
    }

    /**
     * Bakes the entry named {@code key} once more the way generation does, without keeping the result, and times each
     * step. The studio asks several times and keeps the median, so one call is one run. Asked off the server's threads;
     * empty when the pack has no such entry or the view does not bake.
     */
    default Optional<BakeRun> timeBake(PackWorld world, String key) {
        return Optional.empty();
    }
}
