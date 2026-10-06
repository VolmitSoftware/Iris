/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.engine.modifier;

import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineAssignedModifier;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.terrain.deposit.IrisDepositGenerator;
import art.arcane.iris.engine.object.terrain.deposit.IrisDepositHeightDistribution;
import art.arcane.iris.engine.object.terrain.deposit.IrisDepositPlacementScope;
import art.arcane.iris.engine.object.terrain.deposit.IrisDepositVariant;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.terrain.carving.IrisDimensionCarvingResolver;
import art.arcane.iris.engine.object.prefab.IrisObject;
import art.arcane.iris.engine.object.procedural.IrisProceduralBlocks;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.volmlib.util.data.HeightMap;
import art.arcane.iris.util.project.hunk.Hunk;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.iris.util.common.parallel.BurstExecutor;
import art.arcane.volmlib.util.scheduling.PrecisionStopwatch;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.data.B;

public class IrisDepositModifier extends EngineAssignedModifier<PlatformBlockState> {
    private final RNG rng;

    public IrisDepositModifier(Engine engine) {
        super(engine, "Deposit");
        rng = new RNG(getEngine().getSeedManager().getDeposit());
    }

    @Override
    public void onModify(int x, int z, Hunk<PlatformBlockState> output, boolean multicore, ChunkContext context) {
        PrecisionStopwatch p = PrecisionStopwatch.start();
        generateDeposits(output, Math.floorDiv(x, 16), Math.floorDiv(z, 16), multicore, context);
        getEngine().getMetrics().getDeposit().put(p.getMilliseconds());
    }

    public void generateDeposits(Hunk<PlatformBlockState> terrain, int x, int z, boolean multicore, ChunkContext context) {
        IrisRegion region = context.getRegion().get(7, 7);
        IrisBiome biome = context.getBiome().get(7, 7);
        BurstExecutor burst = burst().burst(multicore);

        long seed = x * 341873128712L + z * 132897987541L;
        long mask = 0;
        MantleChunk chunk = getEngine().getMantle().getMantle().getChunk(x, z).use();
        try {
            for (IrisDepositGenerator k : getDimension().getDeposits()) {
                long finalSeed = seed * ++mask;
                burst.queue(() -> generate(k, chunk, terrain, rng.nextParallelRNG(finalSeed), x, z, false, context));
            }

            for (IrisDepositGenerator k : region.getDeposits()) {
                long finalSeed = seed * ++mask;
                burst.queue(() -> generate(k, chunk, terrain, rng.nextParallelRNG(finalSeed), x, z, false, context));
            }

            for (IrisDepositGenerator k : biome.getDeposits()) {
                long finalSeed = seed * ++mask;
                burst.queue(() -> generate(k, chunk, terrain, rng.nextParallelRNG(finalSeed), x, z, false, context));
            }
        } finally {
            // complete() must run before release() even when queueing throws — already
            // submitted burst tasks must never write into a released chunk.
            try {
                burst.complete();
            } finally {
                chunk.release();
            }
        }
    }

    public void generate(IrisDepositGenerator k, MantleChunk chunk, Hunk<PlatformBlockState> data, RNG rng, int cx, int cz, boolean safe, ChunkContext context) {
        generate(k, chunk, data, rng, cx, cz, safe, null, context);
    }

    public void generate(IrisDepositGenerator k, MantleChunk chunk, Hunk<PlatformBlockState> data, RNG rng, int cx, int cz, boolean safe, HeightMap he, ChunkContext context) {
        IrisDimensionCarvingResolver.State carvingState = new IrisDimensionCarvingResolver.State();
        if (k.getSpawnChance() < rng.d())
            return;

        for (int l = 0; l < rng.i(k.getMinPerChunk(), k.getMaxPerChunk() + 1); l++) {
            if (k.getPerClumpSpawnChance() < rng.d())
                continue;

            IrisObject clump = k.getClump(getEngine(), rng, getData());

            int dim = clump.getW();
            int min = dim / 2;
            int max = (int) (16D - dim / 2D);

            if (min > max || min < 0 || max > 15) {
                min = 6;
                max = 9;
            }

            int x = rng.i(min, max + 1);
            int z = rng.i(min, max + 1);
            int terrainSurface = getDepositTerrainSurface(cx, cz, x, z, he, context);
            int height = k.getPlacementScope() == IrisDepositPlacementScope.TERRAIN
                    ? depositSurfaceLimit(terrainSurface, k.getSurfaceClearance())
                    : getEngine().getHeight() - 1;

            if (height < 0) {
                continue;
            }

            int y = sampleHeight(
                    k.getHeightDistribution(), rng, k.getMinHeight(), k.getMaxHeight(),
                    Math.min(height, getEngine().getHeight() - 1));
            if (y == Integer.MIN_VALUE) {
                continue;
            }

            boolean clippedHeight = k.getHeightDistribution() == IrisDepositHeightDistribution.CLIPPED_UNIFORM;
            if (clippedHeight && y > height - 2)
                continue;

            int biomeY = Math.max(0, Math.min(getEngine().getHeight() - 1, y));
            boolean oreDeposit = k.isOre(getData());
            IrisBiome surfaceBiome = context.getBiome().get(x, z);
            IrisBiome depositBiome = oreDeposit || k.usesCaveBiomeFilter()
                    ? getEngine().getCaveBiome(
                            (cx << 4) + x, biomeY, (cz << 4) + z, carvingState)
                    : null;
            if (!k.matchesBiome(surfaceBiome, depositBiome)) {
                continue;
            }

            if (oreDeposit) {
                if (depositBiome != null) {
                    double frequencyMultiplier = depositBiome.getOreDepositFrequencyMultiplier();
                    if (frequencyMultiplier < 1D
                            && !passesOreFrequency(frequencyMultiplier, rng.d())) {
                        continue;
                    }

                    double sizeMultiplier = depositBiome.getOreDepositSizeMultiplier();
                    if (sizeMultiplier != 1D) {
                        IrisObject scaledClump = k.getClump(getEngine(), rng, getData(), sizeMultiplier);
                        int scaledDimension = scaledClump.getW();
                        x = clampDepositCenter(x, scaledDimension, 16);
                        if (clippedHeight) {
                            y = clampDepositCenter(y, scaledDimension, getEngine().getHeight());
                        }
                        z = clampDepositCenter(z, scaledDimension, 16);
                        clump = scaledClump;
                    }
                }
            }

            IrisDimension dimension = getDimension();

            for (art.arcane.iris.util.common.math.IrisBlockVector j : clump.getBlocks().keys()) {
                int nx = j.getBlockX() + x;
                int ny = j.getBlockY() + y;
                int nz = j.getBlockZ() + z;

                if (nx > 15 || nx < 0 || ny >= getEngine().getHeight() || ny < 0 || nz < 0 || nz > 15) {
                    continue;
                }
                int columnSurface = getDepositTerrainSurface(cx, cz, nx, nz, he, context);
                if (!placementSurfaceAllows(
                        k.getPlacementScope(), ny, columnSurface, k.getSurfaceClearance())) {
                    continue;
                }

                PlatformBlockState current = data.get(nx, ny, nz);
                if (!canReplaceDepositTarget(current)) {
                    continue;
                }
                if (!k.canReplace(current)) {
                    continue;
                }
                if (!k.isReplaceBedrock() && IrisProceduralBlocks.materialKey(current).equals("minecraft:bedrock")) {
                    continue;
                }
                if (shouldDiscardExposed(
                        k.getDiscardChanceOnAirExposure(), rng.d(), isAdjacentToAir(data, nx, ny, nz))) {
                    continue;
                }

                if (chunk.get(nx, ny, nz, MatterCavern.class) == null) {
                    PlatformBlockState ore = clump.getBlocks().get(j);
                    PlatformBlockState remapped = resolveDepositVariant(
                            cx, cz, nx, ny, nz, ore, dimension, context, carvingState);
                    PlatformBlockState finalBlock = remapped != null
                            ? remapped
                            : B.toDeepSlateOre(current, ore);
                    data.set(nx, ny, nz, finalBlock);
                }
            }
        }
    }

    private int getDepositTerrainSurface(
            int cx, int cz, int localX, int localZ, HeightMap heightMap,
            ChunkContext context) {
        return heightMap != null
                ? heightMap.getHeight((cx << 4) + localX, (cz << 4) + localZ)
                : context.getRoundedHeight(localX, localZ);
    }

    static int depositSurfaceLimit(int surfaceY) {
        return depositSurfaceLimit(surfaceY, 7);
    }

    static int depositSurfaceLimit(int surfaceY, int surfaceClearance) {
        return surfaceY - Math.max(0, surfaceClearance);
    }

    static boolean placementSurfaceAllows(
            IrisDepositPlacementScope scope, int candidateY, int surfaceY, int surfaceClearance) {
        int clearance = Math.max(0, surfaceClearance);
        return switch (scope) {
            case ABOVE_TERRAIN -> candidateY > surfaceY + clearance;
            case FULL_HEIGHT -> true;
            case TERRAIN -> candidateY <= surfaceY - clearance;
        };
    }

    static int absoluteWorldY(int minHeight, int localY) {
        return minHeight + localY;
    }

    static boolean canReplaceDepositTarget(PlatformBlockState state) {
        return state != null && !state.isAir() && !state.isFluid();
    }

    static int sampleHeight(
            IrisDepositHeightDistribution distribution, RNG rng,
            int configuredMinimum, int configuredMaximum, int clippedMaximum) {
        int minimum = distribution == IrisDepositHeightDistribution.CLIPPED_UNIFORM
                ? Math.max(0, configuredMinimum)
                : configuredMinimum;
        int maximum = distribution == IrisDepositHeightDistribution.CLIPPED_UNIFORM
                ? Math.min(clippedMaximum, configuredMaximum)
                : configuredMaximum;
        if (minimum > maximum) {
            return Integer.MIN_VALUE;
        }
        if (minimum == maximum) {
            return minimum;
        }
        if (distribution != IrisDepositHeightDistribution.TRIANGLE) {
            return minimum + rng.nextInt(maximum - minimum + 1);
        }

        int span = maximum - minimum;
        int lowerHalf = span / 2;
        int upperHalf = span - lowerHalf;
        return minimum + rng.nextInt(upperHalf + 1) + rng.nextInt(lowerHalf + 1);
    }

    static boolean shouldDiscardExposed(double chance, double sample, boolean adjacentToAir) {
        return adjacentToAir && chance > 0D && sample < Math.min(1D, chance);
    }

    static boolean isAdjacentToAir(Hunk<PlatformBlockState> data, int x, int y, int z) {
        return isAirAt(data, x - 1, y, z)
                || isAirAt(data, x + 1, y, z)
                || isAirAt(data, x, y - 1, z)
                || isAirAt(data, x, y + 1, z)
                || isAirAt(data, x, y, z - 1)
                || isAirAt(data, x, y, z + 1);
    }

    private static boolean isAirAt(Hunk<PlatformBlockState> data, int x, int y, int z) {
        if (x < 0 || x >= data.getWidth()
                || y < 0 || y >= data.getHeight()
                || z < 0 || z >= data.getDepth()) {
            return false;
        }
        PlatformBlockState state = data.getRaw(x, y, z);
        return state == null || state.isAir();
    }

    static boolean passesOreFrequency(double multiplier, double sample) {
        return multiplier >= 1D || sample < Math.max(0D, multiplier);
    }

    static int clampDepositCenter(int center, int dimension, int limit) {
        int minimum = dimension / 2;
        int maximum = (int) (limit - dimension / 2D);
        return Math.max(minimum, Math.min(center, maximum));
    }

    private PlatformBlockState resolveDepositVariant(int cx, int cz, int nx, int localY, int nz, PlatformBlockState ore, IrisDimension dimension, ChunkContext context, IrisDimensionCarvingResolver.State carvingState) {
        int worldX = (cx << 4) + nx;
        int worldZ = (cz << 4) + nz;
        int worldY = absoluteWorldY(getEngine().getMinHeight(), localY);

        IrisBiome biome = getEngine().getCaveBiome(worldX, localY, worldZ, carvingState);
        if (biome != null) {
            PlatformBlockState match = matchDepositVariant(biome.getDepositVariants(), ore, worldY);
            if (match != null) {
                return match;
            }
        }

        IrisRegion region = context.getRegion().get(nx, nz);
        if (region != null) {
            PlatformBlockState match = matchDepositVariant(region.getDepositVariants(), ore, worldY);
            if (match != null) {
                return match;
            }
        }

        if (dimension != null) {
            PlatformBlockState match = matchDepositVariant(dimension.getDepositVariants(), ore, worldY);
            if (match != null) {
                return match;
            }
        }

        return null;
    }

    private PlatformBlockState matchDepositVariant(java.util.List<IrisDepositVariant> variants, PlatformBlockState ore, int y) {
        if (variants == null || variants.isEmpty()) {
            return null;
        }

        for (IrisDepositVariant variant : variants) {
            if (y < variant.getMinHeight() || y > variant.getMaxHeight()) {
                continue;
            }

            PlatformBlockState swapped = variant.remapOrNull(ore, getData());
            if (swapped != null) {
                return swapped;
            }
        }

        return null;
    }
}
