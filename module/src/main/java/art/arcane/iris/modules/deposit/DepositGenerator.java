/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.modules.deposit;

import com.volmit.block.BlockSpec;
import com.volmit.config.Desc;
import com.volmit.config.MaxNumber;
import com.volmit.config.MinNumber;
import com.volmit.config.Required;
import com.volmit.config.Snippet;

import java.util.Collections;
import java.util.List;

/**
 * One kind of deposit a dimension, region or biome generates, as packs write it in the {@code "deposits"} list or under
 * {@code snippet/iris/deposit/deposit/}: how many clumps of which blocks, how large, at which heights and in which biomes. A value
 * of IrisApi types only; the deposit module builds the clumps and resolves the blocks.
 */
@Snippet("iris/deposit/deposit")
@Desc("Creates ore & other block deposits underground")
public final class DepositGenerator {
    private static final int MAX_CLUMP_DIMENSION = 11;

    @Required
    @MinNumber(-8192)
    @MaxNumber(8192)
    @Desc("The inclusive minimum origin height in engine-local Y, where 0 is the bottom of the dimension. Negative values let an unclipped vanilla distribution taper into the world floor.")
    private int minHeight = 1;

    @Required
    @MinNumber(-8192)
    @MaxNumber(8192)
    @Desc("The inclusive maximum origin height in engine-local Y, where 0 is the bottom of the dimension.")
    private int maxHeight = 75;

    @Desc("How origin heights are sampled. CLIPPED_UNIFORM preserves Iris behavior by clipping the band to terrain; UNIFORM and TRIANGLE sample the authored band first and discard cells outside terrain or build height.")
    private DepositHeightDistribution heightDistribution = DepositHeightDistribution.CLIPPED_UNIFORM;

    @Desc("TERRAIN places below the generated surface. ABOVE_TERRAIN places only in existing solid hosts above it. FULL_HEIGHT permits existing solid hosts anywhere in the dimension.")
    private DepositPlacementScope placementScope = DepositPlacementScope.TERRAIN;

    @MinNumber(0)
    @MaxNumber(256)
    @Desc("Solid blocks kept between a deposit and the terrain surface. Vanilla-like deposits use 0; the Iris default remains 7.")
    private int surfaceClearance = 7;

    @Required
    @MinNumber(0)
    @MaxNumber(8192)
    @Desc("The minimum Iris block count or vanilla configured vein size, selected according to shape.")
    private int minSize = 0;

    @Required
    @MinNumber(0)
    @MaxNumber(8192)
    @Desc("The maximum Iris block count or vanilla configured vein size, selected according to shape.")
    private int maxSize = 128;

    @Desc("IRIS places a fixed-count cube clump. VANILLA_ELLIPSOID uses Minecraft's chained ellipsoid geometry. VANILLA_SCATTERED uses Minecraft's sparse candidate offsets.")
    private DepositShape shape = DepositShape.IRIS;

    @Required
    @MinNumber(0)
    @MaxNumber(2048)
    @Desc("The maximum amount of clumps per chunk")
    private int maxPerChunk = 3;

    @Required
    @MinNumber(0)
    @MaxNumber(2048)
    @Desc("The minimum amount of clumps per chunk")
    private int minPerChunk = 0;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("The chance of the deposit spawning in a chunk")
    private double spawnChance = 1;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("The chance of each individual clump spawning in a chunk")
    private double perClumpSpawnChance = 1;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("Chance to discard a candidate ore block when it touches air. This matches vanilla ore exposure reduction.")
    private double discardChanceOnAirExposure = 0;

    @Required
    @Desc("The palette of blocks to be used in this deposit generator. Each entry is picked uniformly; the per-entry weight field is ignored here.")
    private List<BlockSpec> palette = List.of();

    @MinNumber(1)
    @MaxNumber(64)
    @Desc("Ore varience is how many different objects clumps iris will create")
    private int varience = 3;

    @Desc("Optional block ids this deposit may replace. An empty list retains Iris's any-solid behavior.")
    private List<String> replaceableBlocks = List.of();

    @Desc("Chooses whether includedBiomes and excludedBiomes inspect the surface biome or the cave biome at the deposit origin.")
    private DepositBiomeScope biomeScope = DepositBiomeScope.CAVE;

    @Desc("Optional Iris biome keys or vanilla derivative ids allowed for this deposit.")
    private List<String> includedBiomes = List.of();

    @Desc("Optional Iris biome keys or vanilla derivative ids denied for this deposit.")
    private List<String> excludedBiomes = List.of();

    @Desc("If set to true, this deposit will replace bedrock")
    private boolean replaceBedrock = false;

    /** A generator with every option at its default, as packs get it from {@code {}}. */
    public DepositGenerator() {
    }

    public int minHeight() {
        return minHeight;
    }

    public int maxHeight() {
        return maxHeight;
    }

    public DepositHeightDistribution heightDistribution() {
        return heightDistribution == null ? DepositHeightDistribution.CLIPPED_UNIFORM : heightDistribution;
    }

    public DepositPlacementScope placementScope() {
        return placementScope == null ? DepositPlacementScope.TERRAIN : placementScope;
    }

    public int surfaceClearance() {
        return surfaceClearance;
    }

    public int minSize() {
        return minSize;
    }

    public int maxSize() {
        return maxSize;
    }

    public DepositShape shape() {
        return shape == null ? DepositShape.IRIS : shape;
    }

    public int maxPerChunk() {
        return maxPerChunk;
    }

    public int minPerChunk() {
        return minPerChunk;
    }

    public double spawnChance() {
        return spawnChance;
    }

    public double perClumpSpawnChance() {
        return perClumpSpawnChance;
    }

    public double discardChanceOnAirExposure() {
        return discardChanceOnAirExposure;
    }

    public List<BlockSpec> palette() {
        return listOrEmpty(palette);
    }

    public int varience() {
        return varience;
    }

    public List<String> replaceableBlocks() {
        return listOrEmpty(replaceableBlocks);
    }

    public DepositBiomeScope biomeScope() {
        return biomeScope == null ? DepositBiomeScope.CAVE : biomeScope;
    }

    public List<String> includedBiomes() {
        return listOrEmpty(includedBiomes);
    }

    public List<String> excludedBiomes() {
        return listOrEmpty(excludedBiomes);
    }

    public boolean replaceBedrock() {
        return replaceBedrock;
    }

    /** The edge of the cube an {@link DepositShape#IRIS} clump of {@link #maxSize()} blocks fills, at most 11. */
    public int maxDimension() {
        return Math.min(MAX_CLUMP_DIMENSION, (int) Math.ceil(Math.cbrt(maxSize)));
    }

    /** Whether the biome filters need the cave biome at the deposit's origin. */
    public boolean usesCaveBiomeFilter() {
        return biomeScope() == DepositBiomeScope.CAVE && (!includedBiomes().isEmpty() || !excludedBiomes().isEmpty());
    }

    private static <T> List<T> listOrEmpty(List<T> list) {
        return list == null ? List.of() : Collections.unmodifiableList(list);
    }
}
