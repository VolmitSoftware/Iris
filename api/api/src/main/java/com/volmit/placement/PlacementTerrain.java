package com.volmit.placement;

import com.volmit.block.BlockState;
import com.volmit.object.BlockEntityData;

import java.util.Optional;

/**
 * The world a placement is made in, as its stages and the module that asked for it see it: blocks, the questions about a
 * column the stages ask, and what the host keeps beside each block. X and Z are world coordinates; Y is the height above
 * the dimension's bottom.
 *
 * <p>A module that places objects hands one to {@link Placements#place}: the terrain of its mantle invocation
 * ({@link com.volmit.generation.MantleGenerationContext#placementTerrain()}), or its own, such as one that collects a
 * growing tree's blocks. It may wrap another to change what it answers or to hold writes back. Stages read it through
 * {@link PlacementContext#placementTerrain()} and write through {@link PlacementAccess}; the host writes a placement's
 * blocks into it when the placement commits. Not thread-safe; it lives as long as the invocation it came from.
 */
public interface PlacementTerrain {
    /** The height of the highest block of the column; {@code ignoreFluid} leaves out the fluid above the ground. */
    int highest(int x, int z, boolean ignoreFluid);

    BlockState get(int x, int y, int z);

    /** Writes the block without a block entity. */
    void set(int x, int y, int z, BlockState state);

    /** Writes the block with its block entity, such as a chest's contents. */
    void set(int x, int y, int z, BlockState state, BlockEntityData blockEntity);

    /** Whether the cave carver emptied the block. */
    boolean isCarved(int x, int y, int z);

    /** Whether the block is real, solid ground, which the terrain's heights alone cannot tell before the chunk is built. */
    boolean isSurfaceSolid(int x, int y, int z);

    boolean isUnderwater(int x, int z);

    /** The height up to which the dimension is filled with fluid. */
    int fluidHeight();

    /** Whether leaves written here never decay. */
    boolean preventsDecay();

    /**
     * Records that the block belongs to {@code placement}, one placed object, so a later placement can tell what is
     * already there ({@link #placementAt}). The host keeps the record beside the block in its generation storage.
     */
    void recordPlacement(int x, int y, int z, PlacementRecord placement);

    /**
     * Takes back the block written at the position, so the terrain shows through there. The block entity, placement
     * record and markers kept beside it stay ({@link #clearAttached}).
     *
     * @throws UnsupportedOperationException when this terrain cannot take blocks back
     */
    default void clearBlock(int x, int y, int z) {
        throw new UnsupportedOperationException("This terrain cannot take blocks back");
    }

    /**
     * Takes back the block entity, placement record and markers the host kept beside the block, whether or not a block
     * is still written there.
     *
     * @throws UnsupportedOperationException when this terrain cannot take them back
     */
    default void clearAttached(int x, int y, int z) {
        throw new UnsupportedOperationException("This terrain cannot take what is kept beside a block back");
    }

    /** The placement recorded at the block ({@link #recordPlacement}); empty when none is. */
    Optional<PlacementRecord> placementAt(int x, int y, int z);

    /** The bounds of the platform's structure pieces planned around the columns, for placements that keep out of them. */
    StructureVolumes structureVolumes(int minX, int minZ, int maxX, int maxZ);

    /**
     * Keeps {@code marker} beside its block in the world's generation storage: a stage's marker when the placement
     * commits ({@link PlacementAccess#marker}), or one the placing module adds to a placed block. Its type is something the
     * module that writes it and the module or host that reads it agree on, such as a spawn marker. The host keeps the
     * types it knows in its own form.
     *
     * @throws IllegalArgumentException when the host keeps no marker of this type
     */
    void mark(PlacedMarker marker);
}
