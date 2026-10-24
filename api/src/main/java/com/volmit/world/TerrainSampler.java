package com.volmit.world;

import com.volmit.block.BlockState;
import com.volmit.resource.ResourceId;
import com.volmit.geometry.BlockBox;
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

    /** The column's surface height in whole blocks: {@link #surfaceY} rounded to the nearest block, for a module that
     * places blocks at the surface. The host may answer it from the whole height it keeps. Outside the bounds it throws
     * like {@link #column}.
     */
    default int surfaceHeight(int worldX, int worldZ) {
        return (int) Math.round(surfaceY(worldX, worldZ));
    }
    BiomeSample biome(int worldX, int y, int worldZ);

    /** The {@link BiomeSample#caveBiome()} alone, for a module that asks it block by block and needs neither the region
     * nor the surface biome: the host may answer it without working out the rest of {@link #biome}. Outside the bounds it
     * throws like {@link #biome}.
     */
    default Optional<ResourceId> caveBiome(int worldX, int y, int worldZ) {
        return biome(worldX, y, worldZ).caveBiome();
    }

    /** The height up to which the host fills the world with fluid wherever the terrain lies lower, the sea level of the
     * dimension: a column's {@link TerrainSample#fluidY()} is this height when its surface is below it.
     */
    int fluidHeight();

    /** Where another dimension stacked above this one begins in the column, when one does: generation of this
     * dimension leaves that height and everything above it to the dimension above, so a module writes nothing there.
     * Empty when nothing sits above. Outside the bounds it throws like {@link #column}.
     */
    OptionalInt upperDimensionY(int worldX, int worldZ);

    /** The id of the column's surface biome, as {@link BiomeSample#surfaceBiome()} gives it, for a module that asks about every
     * column of a chunk and has no use for the rest of the sample. The surface biome is the column's, the same at every
     * height, so this takes no Y; the default samples at the bottom. The host may answer it without working out the cave
     * biome. Outside the bounds it throws like {@link #column}.
     */
    default ResourceId surfaceBiome(int worldX, int worldZ) {
        return biome(worldX, bounds().minY(), worldZ).surfaceBiome();
    }

    /** What the surface biome of the column was generated as. A cave biome ({@link BiomeSample#caveBiome()}) is always
     * {@link BiomeKind#CAVE}, so the answer is about the surface biome only. Outside the bounds it throws like {@link #column}.
     */
    BiomeKind surfaceKind(int worldX, int worldZ);

    /** The block an image map of the dimension gives the column's ground, replacing what its biome would put there; empty
     * where no map does. Outside the bounds it throws like {@link #column}.
     */
    Optional<BlockState> surfaceBlock(int worldX, int worldZ);

    /** The Y in the world of the dimension's bottom block, such as {@code -64}: what to add to a height above the bottom
     * to get the Y a pack's settings name when they speak of world heights.
     */
    int minWorldY();

    /** The dimension stacked above this one, whose ground starts at {@link #upperDimensionY}; empty when nothing sits above. */
    Optional<UpperDimension> upperDimension();
}
