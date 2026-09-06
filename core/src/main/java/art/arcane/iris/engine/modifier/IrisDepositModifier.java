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
import art.arcane.iris.engine.IrisEngine;
import art.arcane.iris.engine.framework.EngineAssignedModifier;
import art.arcane.iris.engine.DimensionStackLayout;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisDepositGenerator;
import art.arcane.iris.engine.object.IrisDepositHeightDistribution;
import art.arcane.iris.engine.object.IrisDepositPlacementScope;
import art.arcane.iris.engine.object.IrisDepositVariant;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.engine.object.IrisDimensionCarvingResolver;
import art.arcane.iris.engine.object.IrisObject;
import art.arcane.iris.engine.object.IrisProceduralBlocks;
import art.arcane.iris.engine.object.IrisRegion;
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.iris.util.project.context.IrisContext;
import art.arcane.volmlib.util.data.HeightMap;
import art.arcane.iris.util.project.hunk.Hunk;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.iris.util.common.parallel.BurstExecutor;
import art.arcane.iris.util.common.math.IrisBlockVector;
import art.arcane.volmlib.util.scheduling.PrecisionStopwatch;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.data.B;
import art.arcane.iris.util.common.data.VectorMap;

import java.util.ArrayList;
import java.util.List;

public class IrisDepositModifier extends EngineAssignedModifier<PlatformBlockState> {
    private static final int CLUMPS_PER_BATCH = 8;
    private static final int PREPARATION_BATCH_COUNT = 4;

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
        List<IrisDepositGenerator> generators = new ArrayList<>(
                getDimension().getDeposits().size() + region.getDeposits().size() + biome.getDeposits().size());
        generators.addAll(getDimension().getDeposits());
        generators.addAll(region.getDeposits());
        generators.addAll(biome.getDeposits());
        if (generators.isEmpty()) {
            return;
        }

        BurstExecutor burst = burst().burst(multicore);
        long seed = x * 341873128712L + z * 132897987541L;
        PreparedDeposit[] prepared = new PreparedDeposit[PREPARATION_BATCH_COUNT];
        int pending = 0;
        MantleChunk chunk = getEngine().getMantle().getMantle().getChunk(x, z).use();
        try {
            for (int i = 0; i < generators.size(); i++) {
                DepositPlan plan = plan(generators.get(i), rng.nextParallelRNG(seed * (i + 1L)));
                for (int first = 0; first < plan.attempts(); first += CLUMPS_PER_BATCH) {
                    int firstAttempt = first;
                    int limit = Math.min(plan.attempts(), first + CLUMPS_PER_BATCH);
                    int index = pending++;
                    burst.queue(scopedDepositTask(
                            () -> prepared[index] = prepare(plan, firstAttempt, limit, x, z, null, context), context));
                    if (pending == prepared.length) {
                        completeBatches(burst, prepared, chunk, terrain, x, z, context);
                        pending = 0;
                    }
                }
            }
            completeBatches(burst, prepared, chunk, terrain, x, z, context);
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

    private void completeBatches(BurstExecutor burst, PreparedDeposit[] prepared, MantleChunk chunk,
                                 Hunk<PlatformBlockState> terrain, int x, int z, ChunkContext context) {
        burst.complete();
        try (IrisContext.Scope chunkScope = IrisContext.open(getEngine(), context.getGenerationSessionId(), context)) {
            for (int i = 0; i < prepared.length; i++) {
                PreparedDeposit deposit = prepared[i];
                prepared[i] = null;
                if (deposit != null) {
                    place(deposit, chunk, terrain, x, z, null, context);
                }
            }
        }
    }

    private Runnable scopedDepositTask(Runnable task, ChunkContext context) {
        IrisEngine generationEngine = getEngine() instanceof IrisEngine irisEngine
                && irisEngine.hasGenerationRuntimeScope() ? irisEngine : null;
        IrisEngine.GenerationRuntimeBinding binding = generationEngine == null
                ? null : generationEngine.captureGenerationRuntimeBinding();
        return () -> {
            try (IrisEngine.GenerationRuntimeScope runtimeScope = generationEngine == null
                    ? null : generationEngine.openGenerationRuntimeScope(binding);
                 IrisContext.Scope chunkScope = IrisContext.open(getEngine(), context.getGenerationSessionId(), context)) {
                task.run();
            }
        };
    }

    public void generate(IrisDepositGenerator k, MantleChunk chunk, Hunk<PlatformBlockState> data, RNG rng, int cx, int cz, boolean safe, ChunkContext context) {
        generate(k, chunk, data, rng, cx, cz, safe, null, context);
    }

    public void generate(IrisDepositGenerator k, MantleChunk chunk, Hunk<PlatformBlockState> data, RNG rng, int cx, int cz, boolean safe, HeightMap he, ChunkContext context) {
        DepositPlan plan = plan(k, rng);
        for (int first = 0; first < plan.attempts(); first += CLUMPS_PER_BATCH) {
            int limit = Math.min(plan.attempts(), first + CLUMPS_PER_BATCH);
            place(prepare(plan, first, limit, cx, cz, he, context), chunk, data, cx, cz, he, context);
        }
    }

    private DepositPlan plan(IrisDepositGenerator generator, RNG rng) {
        if (generator.getSpawnChance() < rng.d()) {
            return new DepositPlan(generator, rng.getSeed(), false, 0);
        }
        boolean ore = generator.isOre(getData());
        int attempts = rng.i(generator.getMinPerChunk(), generator.getMaxPerChunk() + 1);
        return new DepositPlan(generator, rng.getSeed(), ore, attempts);
    }

    private PreparedDeposit prepare(DepositPlan plan, int first, int limit, int cx, int cz, HeightMap he, ChunkContext context) {
        IrisDepositGenerator k = plan.generator();
        boolean oreDeposit = plan.ore();
        boolean needsCaveBiome = oreDeposit || k.usesCaveBiomeFilter();
        IrisDimensionCarvingResolver.State carvingState = needsCaveBiome ? new IrisDimensionCarvingResolver.State() : null;
        List<PreparedClump> clumps = new ArrayList<>(limit - first);
        for (int l = first; l < limit; l++) {
            RNG clumpRng = new RNG(clumpSeed(plan.seed(), l));
            if (k.getPerClumpSpawnChance() < clumpRng.d()) {
                continue;
            }

            IrisObject clump = k.getClump(getEngine(), clumpRng, getData());

            int dim = clump.getW();
            int min = dim / 2;
            int max = (int) (16D - dim / 2D);

            if (min > max || min < 0 || max > 15) {
                min = 6;
                max = 9;
            }

            int x = clumpRng.i(min, max + 1);
            int z = clumpRng.i(min, max + 1);
            int terrainSurface = getDepositTerrainSurface(cx, cz, x, z, he, context);
            int height = k.getPlacementScope() == IrisDepositPlacementScope.TERRAIN
                    ? depositSurfaceLimit(terrainSurface, k.getSurfaceClearance())
                    : getEngine().getHeight() - 1;

            if (height < 0) {
                continue;
            }

            int y = sampleHeight(
                    k.getHeightDistribution(), clumpRng, k.getMinHeight(), k.getMaxHeight(),
                    Math.min(height, getEngine().getHeight() - 1));
            if (y == Integer.MIN_VALUE) {
                continue;
            }

            boolean clippedHeight = k.getHeightDistribution() == IrisDepositHeightDistribution.CLIPPED_UNIFORM;
            if (clippedHeight && y > height - 2) {
                continue;
            }

            int biomeY = Math.max(0, Math.min(getEngine().getHeight() - 1, y));
            IrisBiome surfaceBiome = context.getBiome().get(x, z);
            IrisBiome depositBiome = needsCaveBiome
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
                            && !passesOreFrequency(frequencyMultiplier, clumpRng.d())) {
                        continue;
                    }

                    double sizeMultiplier = depositBiome.getOreDepositSizeMultiplier();
                    if (sizeMultiplier != 1D) {
                        IrisObject scaledClump = k.getClump(getEngine(), clumpRng, getData(), sizeMultiplier);
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

            clumps.add(new PreparedClump(clump, x, y, z, clumpRng));
        }
        return new PreparedDeposit(k, oreDeposit, clumps);
    }

    private void place(PreparedDeposit deposit, MantleChunk chunk, Hunk<PlatformBlockState> data,
                       int cx, int cz, HeightMap he, ChunkContext context) {
        if (deposit.clumps().isEmpty()) {
            return;
        }
        IrisDepositGenerator k = deposit.generator();
        boolean oreDeposit = deposit.ore();
        IrisDimensionCarvingResolver.State carvingState = new IrisDimensionCarvingResolver.State();
        IrisDimension dimension = getDimension();
        for (PreparedClump prepared : deposit.clumps()) {
            IrisObject clump = prepared.object();
            int x = prepared.x();
            int y = prepared.y();
            int z = prepared.z();
            RNG rng = prepared.rng();

            VectorMap<PlatformBlockState>.Cursor cursor = clump.getBlocks().cursor();
            while (cursor.next()) {
                IrisBlockVector j = cursor.key();
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

                DimensionStackLayout stackLayout = context.getDimensionStackLayout(nx, nz);
                if (stackLayout != null && stackLayout.isHostFeatureProtectedY(ny)) {
                    continue;
                }

                PlatformBlockState current = data.get(nx, ny, nz);
                if (!canReplaceDepositTarget(current)) {
                    continue;
                }
                if (!k.isReplaceBedrock() && IrisProceduralBlocks.materialKey(current).equals("minecraft:bedrock")) {
                    continue;
                }

                IrisBiome candidateSurfaceBiome = null;
                boolean exteriorSurface = false;
                if (oreDeposit) {
                    candidateSurfaceBiome = context.getBiome().get(nx, nz);
                    exteriorSurface = k.hasSurfaceReplaceableBlocks(candidateSurfaceBiome)
                            && isTerrainSurface(data, nx, ny, nz, columnSurface);
                }
                if (!canReplaceDepositHost(k, current, candidateSurfaceBiome, exteriorSurface)) {
                    continue;
                }
                boolean adjacentToAir = k.getDiscardChanceOnAirExposure() > 0D
                        && isAdjacentToAir(data, nx, ny, nz);
                if (shouldDiscardExposed(k.getDiscardChanceOnAirExposure(), rng.d(), adjacentToAir)) {
                    continue;
                }

                if (chunk.get(nx, ny, nz, MatterCavern.class) == null) {
                    PlatformBlockState ore = cursor.value();
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

    static boolean canReplaceDepositHost(
            IrisDepositGenerator generator, PlatformBlockState state,
            IrisBiome surfaceBiome, boolean terrainSurface) {
        if (terrainSurface && generator.hasSurfaceReplaceableBlocks(surfaceBiome)) {
            return generator.canReplaceSurface(state, surfaceBiome);
        }
        return generator.canReplace(state);
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

    static boolean isTerrainSurface(
            Hunk<PlatformBlockState> data, int x, int y, int z, int columnSurface) {
        return y == columnSurface
                || isExteriorAirAt(data, x - 1, y, z)
                || isExteriorAirAt(data, x + 1, y, z)
                || isExteriorAirAt(data, x, y - 1, z)
                || isExteriorAirAt(data, x, y + 1, z)
                || isExteriorAirAt(data, x, y, z - 1)
                || isExteriorAirAt(data, x, y, z + 1);
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

    private static boolean isExteriorAirAt(Hunk<PlatformBlockState> data, int x, int y, int z) {
        if (x < 0 || x >= data.getWidth()
                || y < 0 || y >= data.getHeight()
                || z < 0 || z >= data.getDepth()) {
            return false;
        }
        PlatformBlockState state = data.getRaw(x, y, z);
        return state == null
                || (state.isAir()
                        && !"minecraft:cave_air".equals(IrisProceduralBlocks.materialKey(state)));
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

    static long clumpSeed(long seed, int attempt) {
        long mixed = seed + 0x9e3779b97f4a7c15L * (attempt + 1L);
        mixed = (mixed ^ (mixed >>> 30)) * 0xbf58476d1ce4e5b9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94d049bb133111ebL;
        return mixed ^ (mixed >>> 31);
    }

    private record DepositPlan(IrisDepositGenerator generator, long seed, boolean ore, int attempts) {
    }

    private record PreparedDeposit(IrisDepositGenerator generator, boolean ore, List<PreparedClump> clumps) {
    }

    private record PreparedClump(IrisObject object, int x, int y, int z, RNG rng) {
    }
}
