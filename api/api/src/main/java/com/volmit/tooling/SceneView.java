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
     * The blocks of the scene {@code request} names, its anchor where the studio centres the scene; empty when the pack
     * has no such entry, or the entry has fewer variants.
     */
    Optional<GeneratedObject> scene(PackWorld world, SceneRequest request);

    /**
     * The sizes the studio lets one build every scene at, such as a square of the world a view carves; empty when each
     * entry has a size of its own, which is the default.
     */
    default Optional<SceneSizes> sizes() {
        return Optional.empty();
    }

    /** Whether the entries are built from parts the studio can pick out of the scene, which {@link #parts} tells apart. */
    default boolean hasParts() {
        return false;
    }

    /**
     * Which part placed each block of the scene {@link #scene} gives for the same arguments, found the way that scene
     * was built. Asked off the server's threads; empty when the view has no parts, the pack has no such entry, or the
     * entry has fewer variants.
     */
    default Optional<BlockParts> parts(PackWorld world, SceneRequest request) {
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
