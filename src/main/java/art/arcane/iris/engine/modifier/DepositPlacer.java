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

import art.arcane.iris.builtin.deposit.DepositGenerator;
import art.arcane.iris.builtin.deposit.DepositHeightDistribution;
import art.arcane.iris.builtin.deposit.DepositPlacementScope;
import art.arcane.iris.builtin.deposit.DepositVariant;
import art.arcane.iris.builtin.deposit.DepositVariantSettings;
import art.arcane.iris.builtin.deposit.OreFrequencySettings;
import art.arcane.iris.builtin.deposit.OreSizeSettings;
import art.arcane.iris.core.loader.IrisRegistrant;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineAssignedComponent;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
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

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Places deposit clumps into one chunk for {@link DepositModule}: one per engine runtime, holding the deposit
 * stream's random and each generator made ready for this engine ({@link ResolvedDeposit}: resolved blocks and clump
 * shapes). Each generator gets its own random, derived from the chunk and its position in the list.
 */
public class DepositPlacer extends EngineAssignedComponent {
    private final RNG rng;
    private static final double UNCHANGED_ORE_MULTIPLIER = 1D;

    private final Map<DepositGenerator, ResolvedDeposit> resolved = new ConcurrentHashMap<>();
    private final Map<DepositVariant, ResolvedDepositVariant> variants = new ConcurrentHashMap<>();

    public DepositPlacer(Engine engine) {
        super(engine, "Deposit");
        rng = new RNG(getEngine().getSeedManager().getDeposit());
    }

    /** Runs {@code generators}, in order, on the chunk at chunk coordinates {@code x}, {@code z}. */
    public void generate(List<DepositGenerator> generators, Hunk<PlatformBlockState> terrain, int x, int z, boolean multicore,
                         ChunkContext context) {
        PrecisionStopwatch p = PrecisionStopwatch.start();
        generateDeposits(generators, terrain, x, z, multicore, context);
        getEngine().getMetrics().getDeposit().put(p.getMilliseconds());
    }

    private void generateDeposits(List<DepositGenerator> generators, Hunk<PlatformBlockState> terrain, int x, int z,
                                  boolean multicore, ChunkContext context) {
        BurstExecutor burst = burst().burst(multicore);

        long seed = x * 341873128712L + z * 132897987541L;
        long mask = 0;
        MantleChunk chunk = getEngine().getMantle().getMantle().getChunk(x, z).use();
        try {
            for (DepositGenerator k : generators) {
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

    public void generate(DepositGenerator k, MantleChunk chunk, Hunk<PlatformBlockState> data, RNG rng, int cx, int cz, boolean safe, ChunkContext context) {
        generate(k, chunk, data, rng, cx, cz, safe, null, context);
    }

    public void generate(DepositGenerator k, MantleChunk chunk, Hunk<PlatformBlockState> data, RNG rng, int cx, int cz, boolean safe, HeightMap he, ChunkContext context) {
        IrisDimensionCarvingResolver.State carvingState = new IrisDimensionCarvingResolver.State();
        ResolvedDeposit deposit = resolved(k);
        if (k.spawnChance() < rng.d())
            return;

        for (int l = 0; l < rng.i(k.minPerChunk(), k.maxPerChunk() + 1); l++) {
            if (k.perClumpSpawnChance() < rng.d())
                continue;

            IrisObject clump = deposit.clump(rng);

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
            int height = k.placementScope() == DepositPlacementScope.TERRAIN
                    ? depositSurfaceLimit(terrainSurface, k.surfaceClearance())
                    : getEngine().getHeight() - 1;

            if (height < 0) {
                continue;
            }

            int y = sampleHeight(
                    k.heightDistribution(), rng, k.minHeight(), k.maxHeight(),
                    Math.min(height, getEngine().getHeight() - 1));
            if (y == Integer.MIN_VALUE) {
                continue;
            }

            boolean clippedHeight = k.heightDistribution() == DepositHeightDistribution.CLIPPED_UNIFORM;
            if (clippedHeight && y > height - 2)
                continue;

            int biomeY = Math.max(0, Math.min(getEngine().getHeight() - 1, y));
            boolean oreDeposit = deposit.isOre();
            IrisBiome surfaceBiome = context.getBiome().get(x, z);
            IrisBiome depositBiome = oreDeposit || k.usesCaveBiomeFilter()
                    ? getEngine().getCaveBiome(
                            (cx << 4) + x, biomeY, (cz << 4) + z, carvingState)
                    : null;
            if (!deposit.matchesBiome(surfaceBiome, depositBiome)) {
                continue;
            }

            if (oreDeposit) {
                if (depositBiome != null) {
                    double frequencyMultiplier = oreFrequencyMultiplier(depositBiome);
                    if (frequencyMultiplier < 1D
                            && !passesOreFrequency(frequencyMultiplier, rng.d())) {
                        continue;
                    }

                    double sizeMultiplier = oreSizeMultiplier(depositBiome);
                    if (sizeMultiplier != 1D) {
                        IrisObject scaledClump = deposit.clump(rng, sizeMultiplier);
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
                        k.placementScope(), ny, columnSurface, k.surfaceClearance())) {
                    continue;
                }

                PlatformBlockState current = data.get(nx, ny, nz);
                if (!canReplaceDepositTarget(current)) {
                    continue;
                }
                if (!deposit.canReplace(current)) {
                    continue;
                }
                if (!k.replaceBedrock() && IrisProceduralBlocks.materialKey(current).equals("minecraft:bedrock")) {
                    continue;
                }
                if (shouldDiscardExposed(
                        k.discardChanceOnAirExposure(), rng.d(), isAdjacentToAir(data, nx, ny, nz))) {
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

    private ResolvedDeposit resolved(DepositGenerator generator) {
        return resolved.computeIfAbsent(generator, ignored -> ResolvedDeposit.of(generator, getEngine()));
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
            DepositPlacementScope scope, int candidateY, int surfaceY, int surfaceClearance) {
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
            DepositHeightDistribution distribution, RNG rng,
            int configuredMinimum, int configuredMaximum, int clippedMaximum) {
        int minimum = distribution == DepositHeightDistribution.CLIPPED_UNIFORM
                ? Math.max(0, configuredMinimum)
                : configuredMinimum;
        int maximum = distribution == DepositHeightDistribution.CLIPPED_UNIFORM
                ? Math.min(clippedMaximum, configuredMaximum)
                : configuredMaximum;
        if (minimum > maximum) {
            return Integer.MIN_VALUE;
        }
        if (minimum == maximum) {
            return minimum;
        }
        if (distribution != DepositHeightDistribution.TRIANGLE) {
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
            PlatformBlockState match = matchDepositVariant(biome, ore, worldY);
            if (match != null) {
                return match;
            }
        }

        IrisRegion region = context.getRegion().get(nx, nz);
        if (region != null) {
            PlatformBlockState match = matchDepositVariant(region, ore, worldY);
            if (match != null) {
                return match;
            }
        }

        if (dimension != null) {
            PlatformBlockState match = matchDepositVariant(dimension, ore, worldY);
            if (match != null) {
                return match;
            }
        }

        return null;
    }

    private PlatformBlockState matchDepositVariant(IrisRegistrant owner, PlatformBlockState ore, int y) {
        for (DepositVariant declared : variantsOf(owner)) {
            ResolvedDepositVariant variant = variants.computeIfAbsent(declared, ResolvedDepositVariant::new);
            if (!variant.appliesAt(y)) {
                continue;
            }

            PlatformBlockState swapped = variant.remapOrNull(ore);
            if (swapped != null) {
                return swapped;
            }
        }

        return null;
    }

    private static List<DepositVariant> variantsOf(IrisRegistrant owner) {
        return owner.getModuleSettings().find(DepositVariantSettings.class).map(DepositVariantSettings::variants).orElse(List.of());
    }

    private static double oreFrequencyMultiplier(IrisBiome biome) {
        return biome.getModuleSettings().find(OreFrequencySettings.class).map(OreFrequencySettings::multiplier).orElse(UNCHANGED_ORE_MULTIPLIER);
    }

    private static double oreSizeMultiplier(IrisBiome biome) {
        return biome.getModuleSettings().find(OreSizeSettings.class).map(OreSizeSettings::multiplier).orElse(UNCHANGED_ORE_MULTIPLIER);
    }
}
