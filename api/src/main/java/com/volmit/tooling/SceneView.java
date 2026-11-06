package com.volmit.tooling;

import com.volmit.object.GeneratedObject;
import com.volmit.resource.ResourceId;
import com.volmit.world.PackWorld;

import java.util.List;
import java.util.Optional;

/**
 * A studio view of the block scenes a module builds from the pack, such as its objects: a catalogue of entries, each
 * with variants the studio draws in 3D. The studio asks on its own threads, several at once, for a world of the pack
 * it shows; a view keeps nothing between calls that the world's state ({@link PackWorld#state}) should keep.
 */
public interface SceneView {
    /** The module's id, or one starting with it and {@code /}, such as {@code iris:objects}. */
    ResourceId id();

    /** The view's name in the studio's list of views, such as "Objects". */
    String label();

    /** One line on what the view shows. */
    String description();

    /** The view's entries in the world's pack, in the order the studio lists them. */
    List<SceneEntry> entries(PackWorld world);

    /**
     * The blocks of one variant of the entry named {@code key}, its anchor where the studio centres the scene; empty
     * when the pack has no such entry, or the entry has fewer variants.
     *
     * @param variant from 0 to the entry's {@link SceneEntry#variants()}, exclusive
     */
    Optional<GeneratedObject> scene(PackWorld world, String key, int variant);
}
