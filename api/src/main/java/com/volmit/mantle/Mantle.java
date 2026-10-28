package com.volmit.mantle;

import com.volmit.block.BlockState;
import com.volmit.geometry.BlockBox;
import com.volmit.resource.ResourceId;

/**
 * Invocation-scoped access to typed mantle data. X and Z are world block coordinates; Y is
 * the height above the dimension's bottom (0 = bottom), so it is never negative. The host translates
 * them to storage coordinates.
 *
 * <p>The host owns chunk acquisition, release, persistence and the permitted access area.
 * Out-of-area access throws IndexOutOfBoundsException. Access after the invocation ends
 * throws IllegalStateException. Do not retain this interface or use it from detached tasks.
 * Concurrent use of one instance is not supported unless the host explicitly permits it.
 *
 * <p>Only registered layers are supported. Unknown IDs or mismatched registered types throw
 * IllegalArgumentException. Null arguments are rejected. Layer views share this view's lifetime.
 *
 * <p>Writes affect generation storage, not necessarily the current chunk Hunk or live world.
 * Direct writes have no rollback guarantee. Use edit() to buffer multi-layer changes.
 * The host must serialize overlapping writes; read-modify-write is not independently atomic.
 */
public interface Mantle {
    BlockBox bounds();

    <T> MantleLayer<T> layer(MantleKey<T> key);

    /** The blocks the mantle holds for chunks to take over: what placements wrote, such as objects and structures.
     * A chunk stage copies them into its chunk (Iris's {@code iris:matter}); absent positions read null, and air a
     * placement wrote is a block like any other. The host owns this layer, so it needs no registration. Stages read
     * it only, and writes throw UnsupportedOperationException: placements write blocks with
     * {@link com.volmit.placement.PlacementAccess#set}. A dimension that keeps no mantle holds none.
     */
    MantleLayer<BlockState> blocks();

    /** Whether the mantle holds a carved void at the position for the chunk to take over: a cave's air or fluid, planned
     * before the chunks were generated. Also true where the chunk's own blocks show no void, such as under a dimension
     * stacked above. The host owns the carving; stages only ask. A dimension that keeps no mantle carves nothing.
     */
    boolean isCarved(int x, int y, int z);

    /** Shows the block as platform biome {@code biome}, such as {@code minecraft:plains} or a pack's custom biome, instead
     * of the biome its column generates as; the host's biome pass reads it when it writes the chunk's biomes. The host
     * owns this layer and keeps it in its own form, so it is written only and needs no registration. A chunk stage
     * sets it inside its own chunk only, like module layers.
     */
    void setPlatformBiome(int x, int y, int z, ResourceId biome);

    /** Only one edit may be active per view. Parent reads remain allowed, but parent
     * mutations are prohibited until the edit ends (IllegalStateException).
     */
    MantleEdit edit();
}
