package com.volmit.tooling;

import com.volmit.resource.ResourceId;
import com.volmit.world.PackWorld;

/**
 * A view a module adds to the studio, of one of the kinds the studio draws. The studio asks on its own threads, several
 * at once, for a world of the pack it shows; a view keeps nothing between calls that the world's state
 * ({@link PackWorld#state}) should keep.
 */
public sealed interface StudioView permits SceneView, PatternView {
    /** The module's id, or one starting with it and {@code /}, such as {@code iris:objects}. */
    ResourceId id();

    /** The view's name in the studio's list of views, such as "Objects". */
    String label();

    /** One line on what the view shows. */
    String description();
}
