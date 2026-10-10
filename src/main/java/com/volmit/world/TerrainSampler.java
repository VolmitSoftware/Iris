package com.volmit.world;

import com.volmit.mantle.BlockBox;
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
}
