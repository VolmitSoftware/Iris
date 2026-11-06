package com.volmit.placement;

/** Host-owned buffered placement writes, with world X and Z and Y as the height above the
 * dimension's bottom (0 = bottom), including cross-chunk writes.
 * All reads see the overlay. Block/entity/marker/mantle mutations participate in one commit.
 * Out-of-bounds access fails, never clips. The host excludes competing writers and handles
 * block entity replacement/removal when states change. Nested mantle edits are unsupported.
 * Successful commit ends the edit; failure leaves the parent unchanged and permits only close.
 * Close is idempotent and discards uncommitted work. Views expire when this edit ends.
 * This is a generation-storage transaction, not live-world or disk/crash atomicity.
 */
public interface PlacementEdit extends PlacementAccess, AutoCloseable {
    void commit();
    @Override void close();
}
