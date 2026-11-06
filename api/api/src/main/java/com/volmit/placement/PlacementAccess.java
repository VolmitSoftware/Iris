package com.volmit.placement;

import com.volmit.block.BlockState;
import com.volmit.mantle.Mantle;
import com.volmit.world.BlockView;

/** Buffered access for placement stages, without ownership of commit/close. X and Z are world coordinates;
 * Y is the height above the dimension's bottom (0 = bottom).
 * Reads include the overlay. Bounds are the host-prepared placement area.
 * Block/entity/marker/mantle writes share the host edit.
 * Out-of-area access throws; nested mantle edits are unsupported. Do not retain this view.
 */
public interface PlacementAccess extends BlockView {
    /** Writes {@code state} without a block entity, replacing any block entity buffered there. The per-block write. */
    void set(int x, int y, int z, BlockState state);

    /** Writes a block with its block entity, if it has one. */
    void set(PlacedBlock block);
    void marker(PlacedMarker marker);
    Mantle mantle();
}
