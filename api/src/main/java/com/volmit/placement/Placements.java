package com.volmit.placement;

/**
 * Runs the pack's placement stages for an object a module places itself, such as the objects of a biome or a growing
 * tree. The host offers it for every world ({@link com.volmit.world.PackWorld#placements()}); the stages are every
 * module's placement stages of the pack, in the order the stage graph gives them.
 */
public interface Placements {
    /**
     * Places {@code run}'s object: the stages run with buffered writes, and the host writes the blocks, then the markers,
     * into the run's terrain only when no stage rejects the placement and none throws. A stage that throws fails the call and nothing
     * is written. Call it from the invocation the terrain belongs to.
     */
    PlacementOutcome place(PlacementRun run);
}
