package com.volmit.placement;

import com.volmit.mantle.Mantle;
import com.volmit.world.BlockView;

/** Buffered world-space access for placement hooks, without ownership of commit/close.
 * Reads include the overlay. Bounds cover the object's footprint plus declared horizontal reach
 * and host-authorized vertical area. Block/entity/marker/mantle writes share the host edit.
 * Out-of-area access throws; nested mantle edits are unsupported. Do not retain this view.
 */
public interface PlacementAccess extends BlockView {
    void set(PlacedBlock block);
    void marker(PlacedMarker marker);
    Mantle mantle();
}
