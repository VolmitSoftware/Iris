package com.volmit.world;

import com.volmit.block.BlockState;
import com.volmit.mantle.BlockBox;
import java.util.Optional;
import java.util.OptionalInt;

/** Read-only host sampling for the same pack snapshot as the invocation.
 * X and Z are world coordinates; Y is the height above the dimension's bottom (0 = bottom).
 * Bounds use the same coordinates, maxima exclusive. Sampling outside prepared bounds fails with
 * IndexOutOfBoundsException. Views expire with the invocation and must not escape it.
 */
public interface TerrainSampler {
    BlockBox bounds();
    TerrainSample column(int worldX, int worldZ);

    /** The column's {@link TerrainSample#surfaceY()} alone, for a module that needs neither slope nor fluid: the host
     * may answer it without working out the rest of {@link #column}. Outside the bounds it throws like {@link #column}.
     */
    default double surfaceY(int worldX, int worldZ) {
        return column(worldX, worldZ).surfaceY();
    }
    BiomeSample biome(int worldX, int y, int worldZ);

    /** The height up to which the host fills the world with fluid wherever the terrain lies lower, the sea level of the
     * dimension: a column's {@link TerrainSample#fluidY()} is this height when its surface is below it.
     */
    int fluidHeight();

    /** Where another dimension stacked above this one begins in the column, when one does: generation of this
     * dimension leaves that height and everything above it to the dimension above, so a module writes nothing there.
     * Empty when nothing sits above. Outside the bounds it throws like {@link #column}.
     */
    OptionalInt upperDimensionY(int worldX, int worldZ);

    /** What the surface biome of the column was generated as. A cave biome ({@link BiomeSample#caveBiome()}) is always
     * {@link BiomeKind#CAVE}, so the answer is about the surface biome only. Outside the bounds it throws like {@link #column}.
     */
    BiomeKind surfaceKind(int worldX, int worldZ);

    /** The block an image map of the dimension gives the column's ground, replacing what its biome would put there; empty
     * where no map does. Outside the bounds it throws like {@link #column}.
     */
    default Optional<BlockState> surfaceBlock(int worldX, int worldZ) {
        return Optional.empty();
    }

    /** The Y in the world of the dimension's bottom block, such as {@code -64}: what to add to a height above the bottom
     * to get the Y a pack's settings name when they speak of world heights.
     */
    int minWorldY();

    /** The dimension stacked above this one, whose ground starts at {@link #upperDimensionY}; empty when nothing sits above. */
    default Optional<UpperDimension> upperDimension() {
        return Optional.empty();
    }
}
