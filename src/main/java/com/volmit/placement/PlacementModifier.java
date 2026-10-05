package com.volmit.placement;

import com.volmit.generation.Dependencies;
import java.util.Optional;

/** Configured placement behavior. IDs are namespaced; dependencies refer only to placement
 * modifier IDs, not chunk modifiers. Snapshot settings; calls may occur concurrently.
 * The host runs prepare for all modifiers, mapBlock for each candidate, then validate for all
 * modifiers before buffering any write. beforePlace runs after validation but before object
 * writes, for boring/preparation. afterPlace runs only after all accepted blocks and
 * markers are buffered, allowing vacuum/stilts to observe the final footprint and overlay.
 * Exceptions discard the attempt. Empty prepare rejects it; empty mapBlock skips one block.
 */
public abstract class PlacementModifier {
    public abstract String id();
    public Dependencies depends() { return Dependencies.none(); }
    /** Horizontal access beyond the resulting object footprint; nonnegative, read at pack load. */
    public int writeRadius() { return 0; }
    public Optional<PlacementRequest> prepare(PlacementContext context, PlacementRequest request) { return Optional.of(request); }
    public Optional<PlacedBlock> mapBlock(PlacementContext context, PlacedBlock block) { return Optional.of(block); }
    public boolean validate(PlacementContext context, PlacementPlan plan) { return true; }
    public void beforePlace(PlacementContext context, PlacementAccess access, PlacementPlan proposed) {}
    public void afterPlace(PlacementContext context, PlacementAccess access, PlacementPlan written) {}
}
