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

package art.arcane.iris.generation.stage;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.generation.runtime.EngineAssignedModifier;
import art.arcane.iris.generation.runtime.DimensionStackLayout;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.decoration.IrisDepositBiomeScope;
import art.arcane.iris.generation.decoration.IrisDepositGenerator;
import art.arcane.iris.generation.decoration.IrisDepositHeightDistribution;
import art.arcane.iris.generation.decoration.IrisDepositPlacementScope;
import art.arcane.iris.generation.decoration.IrisDepositVariant;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisDimensionCarvingResolver;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.generation.decoration.IrisProceduralBlocks;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.iris.generation.context.IrisContext;
import art.arcane.volmlib.util.data.HeightMap;
import art.arcane.volmlib.util.hunk.Hunk;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.iris.generation.concurrent.BurstExecutor;
import art.arcane.iris.generation.geometry.IrisBlockVector;
import art.arcane.volmlib.util.scheduling.PrecisionStopwatch;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.iris.generation.block.B;
import art.arcane.iris.generation.block.VectorMap;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.IrisRegistrant;
import art.arcane.iris.pack.loading.ResourceLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class IrisDepositModifier extends EngineAssignedModifier<NativeBlockState> {
    private static final int CLUMPS_PER_BATCH = 8;
    private static final int PREPARATION_BATCH_COUNT = 4;

    private final RNG rng;
    private volatile PackScan packScan;

    public IrisDepositModifier(Engine engine) {
        super(engine, "Deposit");
        rng = new RNG(getEngine().getSeedManager().getDeposit());
    }

    @Override
    public void onModify(int x, int z, Hunk<NativeBlockState> output, boolean multicore, ChunkContext context) {
        PrecisionStopwatch p = PrecisionStopwatch.start();
        generateDeposits(output, Math.floorDiv(x, 16), Math.floorDiv(z, 16), multicore, context);
        getEngine().getMetrics().getDeposit().put(p.getMilliseconds());
    }

    public void generateDeposits(Hunk<NativeBlockState> terrain, int x, int z, boolean multicore, ChunkContext context) {
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
        int highestSurface = highestTerrainSurface(x, z, null, context);
        PreparedDeposit[] prepared = new PreparedDeposit[PREPARATION_BATCH_COUNT];
        int pending = 0;
        IrisEngine generationEngine = getEngine() instanceof IrisEngine irisEngine
                && irisEngine.hasGenerationRuntimeScope() ? irisEngine : null;
        PreparationContext preparation = new PreparationContext(context, generationEngine,
                generationEngine == null ? null : generationEngine.captureGenerationRuntimeBinding(), new Throwable[PREPARATION_BATCH_COUNT]);
        MantleChunk chunk = getEngine().getMantle().getMantle().useChunk(x, z);
        Throwable callerFailure = null;
        try {
            for (int i = 0; i < generators.size(); i++) {
                DepositPlan plan = plan(generators.get(i), rng.nextParallelRNG(seed * (i + 1L)), context);
                for (int first = 0; first < plan.attempts(); first += CLUMPS_PER_BATCH) {
                    int firstAttempt = first;
                    int limit = Math.min(plan.attempts(), first + CLUMPS_PER_BATCH);
                    int index = pending++;
                    burst.queue(scopedDepositTask(
                            () -> prepared[index] = prepare(plan, firstAttempt, limit, x, z, null, context, highestSurface),
                            preparation, index));
                    if (pending == prepared.length) {
                        completeBatches(burst, prepared, chunk, terrain, x, z, preparation);
                        pending = 0;
                    }
                }
            }
            completeBatches(burst, prepared, chunk, terrain, x, z, preparation);
        } catch (Throwable failure) {
            callerFailure = failure;
            throw failure;
        } finally {
            // complete() must run before release() even when queueing throws — already
            // submitted burst tasks must never write into a released chunk.
            try {
                burst.complete();
                if (callerFailure != null) {
                    preparation.takeFailure(callerFailure);
                }
            } finally {
                chunk.release();
            }
        }
    }

    private void completeBatches(BurstExecutor burst, PreparedDeposit[] prepared, MantleChunk chunk,
                                 Hunk<NativeBlockState> terrain, int x, int z, PreparationContext preparation) {
        burst.complete();
        Throwable failure = preparation.takeFailure(null);
        if (failure instanceof Error error) {
            throw error;
        }
        if (failure instanceof RuntimeException exception) {
            throw exception;
        }
        if (failure != null) {
            throw new IllegalStateException("Deposit preparation failed", failure);
        }
        try (IrisContext.Scope chunkScope = IrisContext.open(getEngine(), preparation.chunk().getGenerationSessionId(), preparation.chunk())) {
            for (int i = 0; i < prepared.length; i++) {
                PreparedDeposit deposit = prepared[i];
                prepared[i] = null;
                if (deposit != null) {
                    place(deposit, chunk, terrain, x, z, null, preparation.chunk());
                }
            }
        }
    }

    private Runnable scopedDepositTask(Runnable task, PreparationContext preparation, int index) {
        return () -> {
            try (IrisEngine.GenerationRuntimeScope runtimeScope = preparation.engine() == null
                    ? null : preparation.engine().openGenerationRuntimeScope(preparation.binding());
                 IrisContext.Scope chunkScope = IrisContext.open(getEngine(), preparation.chunk().getGenerationSessionId(), preparation.chunk())) {
                task.run();
            } catch (Throwable failure) {
                preparation.failures()[index] = failure;
            }
        };
    }

    public void generate(IrisDepositGenerator k, MantleChunk chunk, Hunk<NativeBlockState> data, RNG rng, int cx, int cz, boolean safe, ChunkContext context) {
        generate(k, chunk, data, rng, cx, cz, safe, null, context);
    }

    public void generate(IrisDepositGenerator k, MantleChunk chunk, Hunk<NativeBlockState> data, RNG rng, int cx, int cz, boolean safe, HeightMap he, ChunkContext context) {
        DepositPlan plan = plan(k, rng, context);
        for (int first = 0; first < plan.attempts(); first += CLUMPS_PER_BATCH) {
            int limit = Math.min(plan.attempts(), first + CLUMPS_PER_BATCH);
            place(prepare(plan, first, limit, cx, cz, he, context, Integer.MIN_VALUE), chunk, data, cx, cz, he, context);
        }
    }

    private DepositPlan plan(IrisDepositGenerator generator, RNG rng, ChunkContext context) {
        if (generator.getSpawnChance() < rng.d()) {
            return new DepositPlan(generator, rng.getSeed(), false, 0);
        }
        boolean ore = generator.isOre(getData());
        int attempts = rng.i(generator.getMinPerChunk(), generator.getMaxPerChunk() + 1);
        return new DepositPlan(generator, rng.getSeed(), ore,
                attempts == 0 || surfaceBiomeMayMatch(generator, context) ? attempts : 0);
    }

    /**
     * A surface-scoped biome filter reads the surface biome of the clump's own column, so when no column of the
     * chunk passes it every clump is rejected at that check. Each clump draws from its own seeded RNG, so skipping
     * them all up front changes nothing else.
     */
    private static boolean surfaceBiomeMayMatch(IrisDepositGenerator generator, ChunkContext context) {
        if (generator.getBiomeScope() != IrisDepositBiomeScope.SURFACE
                || (generator.getIncludedBiomes() == null || generator.getIncludedBiomes().isEmpty())
                && (generator.getExcludedBiomes() == null || generator.getExcludedBiomes().isEmpty())) {
            return true;
        }
        IrisBiome checked = null;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                IrisBiome surface = context.getBiome().get(x, z);
                if (surface == checked) {
                    continue;
                }
                if (generator.matchesBiome(surface, null)) {
                    return true;
                }
                checked = surface;
            }
        }
        return false;
    }

    private PreparedDeposit prepare(DepositPlan plan, int first, int limit, int cx, int cz, HeightMap he,
                                    ChunkContext context, int highestSurface) {
        IrisDepositGenerator k = plan.generator();
        boolean oreDeposit = plan.ore();
        boolean scalable = oreDeposit && packScan().oreScaling();
        boolean needsCaveBiome = scalable || k.usesCaveBiomeFilter();
        IrisDimensionCarvingResolver.State carvingState = needsCaveBiome ? new IrisDimensionCarvingResolver.State() : null;
        boolean terrainScoped = k.getPlacementScope() == IrisDepositPlacementScope.TERRAIN;
        int highest = highestSurface;
        int engineHeight = getEngine().getHeight();
        List<PreparedClump> clumps = new ArrayList<>(limit - first);
        for (int l = first; l < limit; l++) {
            RNG clumpRng = new ClumpRandom(clumpSeed(plan.seed(), l));
            if (k.getPerClumpSpawnChance() < clumpRng.d()) {
                continue;
            }

            IrisDepositGenerator.ClumpFootprint footprint = k.sampleClumpFootprint(clumpRng, getData());
            if (footprint != null && footprint.empty() && !scalable) {
                continue;
            }
            IrisObject clump = footprint == null ? k.getClump(getEngine(), clumpRng, getData()) : null;

            int dim = footprint == null ? clump.getW() : footprint.width();
            int min = dim / 2;
            int max = (int) (16D - dim / 2D);

            if (min > max || min < 0 || max > 15) {
                min = 6;
                max = 9;
            }

            int x = clumpRng.i(min, max + 1);
            int z = clumpRng.i(min, max + 1);
            int terrainSurface = getDepositTerrainSurface(cx, cz, x, z, he, context);
            int height = terrainScoped
                    ? depositSurfaceLimit(terrainSurface, k.getSurfaceClearance())
                    : engineHeight - 1;

            if (height < 0) {
                continue;
            }

            int y = sampleHeight(
                    k.getHeightDistribution(), clumpRng, k.getMinHeight(), k.getMaxHeight(),
                    Math.min(height, engineHeight - 1));
            if (y == Integer.MIN_VALUE) {
                continue;
            }

            boolean clippedHeight = k.getHeightDistribution() == IrisDepositHeightDistribution.CLIPPED_UNIFORM;
            if (clippedHeight && y > height - 2) {
                continue;
            }

            boolean footprintLimited = footprint != null && terrainScoped && !footprint.empty();
            if (footprintLimited && highest == Integer.MIN_VALUE) {
                highest = highestTerrainSurface(cx, cz, he, context);
            }
            int terrainLimit = highest - Math.max(0, k.getSurfaceClearance());
            if (footprintLimited && !scalable && y + footprint.minY() > terrainLimit) {
                continue;
            }

            int biomeY = Math.max(0, Math.min(engineHeight - 1, y));
            IrisBiome surfaceBiome = context.getBiome().get(x, z);
            IrisBiome depositBiome = needsCaveBiome
                    ? getEngine().getCaveBiome(
                            (cx << 4) + x, biomeY, (cz << 4) + z, carvingState)
                    : null;
            if (!k.matchesBiome(surfaceBiome, depositBiome)) {
                continue;
            }

            if (oreDeposit && depositBiome != null) {
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
                        y = clampDepositCenter(y, scaledDimension, engineHeight);
                    }
                    z = clampDepositCenter(z, scaledDimension, 16);
                    clump = scaledClump;
                }
            }

            if (clump != null) {
                clumps.add(new PreparedClump(clump, null, x, y, z, clumpRng));
                continue;
            }
            if (footprint.empty() || footprintLimited && y + footprint.minY() > terrainLimit) {
                continue;
            }
            clumps.add(new PreparedClump(null, footprint.cells(), x, y, z, clumpRng));
        }
        return new PreparedDeposit(k, oreDeposit, clumps);
    }

    private void place(PreparedDeposit deposit, MantleChunk chunk, Hunk<NativeBlockState> data,
                       int cx, int cz, HeightMap he, ChunkContext context) {
        if (deposit.clumps().isEmpty()) {
            return;
        }
        ClumpPlacement placement = new ClumpPlacement(deposit.generator(), deposit.ore(), chunk, data, cx, cz, he,
                context, depositVariantsPossible());
        for (PreparedClump prepared : deposit.clumps()) {
            int x = prepared.x();
            int y = prepared.y();
            int z = prepared.z();
            RNG rng = prepared.rng();
            IrisDepositGenerator.ClumpCells cells = prepared.cells();
            if (cells != null) {
                for (int cell = 0; cell < cells.size(); cell++) {
                    placement.place(cells.x(cell) + x, cells.y(cell) + y, cells.z(cell) + z, cells.block(cell), rng);
                }
                continue;
            }
            VectorMap<NativeBlockState>.Cursor cursor = prepared.object().getBlocks().cursor();
            while (cursor.next()) {
                IrisBlockVector j = cursor.key();
                placement.place(j.getBlockX() + x, j.getBlockY() + y, j.getBlockZ() + z, cursor.value(), rng);
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

    /**
     * The highest terrain surface in the chunk. A TERRAIN-scoped clump whose lowest block sits above it, less the
     * generator's clearance, would have every block rejected by {@link #placementSurfaceAllows}.
     */
    private int highestTerrainSurface(int cx, int cz, HeightMap he, ChunkContext context) {
        int highest = Integer.MIN_VALUE;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                highest = Math.max(highest, getDepositTerrainSurface(cx, cz, x, z, he, context));
            }
        }
        return highest;
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

    static boolean canReplaceDepositTarget(NativeBlockState state) {
        return state != null && !state.isAir() && !state.isFluid();
    }

    static boolean canReplaceDepositHost(
            IrisDepositGenerator generator, NativeBlockState state,
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
            Hunk<NativeBlockState> data, int x, int y, int z, int columnSurface) {
        return y == columnSurface
                || isExteriorAirAt(data, x - 1, y, z)
                || isExteriorAirAt(data, x + 1, y, z)
                || isExteriorAirAt(data, x, y - 1, z)
                || isExteriorAirAt(data, x, y + 1, z)
                || isExteriorAirAt(data, x, y, z - 1)
                || isExteriorAirAt(data, x, y, z + 1);
    }

    static boolean isAdjacentToAir(Hunk<NativeBlockState> data, int x, int y, int z) {
        return isAirAt(data, x - 1, y, z)
                || isAirAt(data, x + 1, y, z)
                || isAirAt(data, x, y - 1, z)
                || isAirAt(data, x, y + 1, z)
                || isAirAt(data, x, y, z - 1)
                || isAirAt(data, x, y, z + 1);
    }

    private static boolean isAirAt(Hunk<NativeBlockState> data, int x, int y, int z) {
        if (x < 0 || x >= data.getWidth()
                || y < 0 || y >= data.getHeight()
                || z < 0 || z >= data.getDepth()) {
            return false;
        }
        NativeBlockState state = data.getRaw(x, y, z);
        return state == null || state.isAir();
    }

    private static boolean isExteriorAirAt(Hunk<NativeBlockState> data, int x, int y, int z) {
        if (x < 0 || x >= data.getWidth()
                || y < 0 || y >= data.getHeight()
                || z < 0 || z >= data.getDepth()) {
            return false;
        }
        NativeBlockState state = data.getRaw(x, y, z);
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

    /**
     * The per-block variant lookup resolves a cave biome for every placed ore, but it can only ever match when
     * some biome, region or the dimension of the loaded pack declares variants. Anything the scan cannot
     * enumerate (stacked dimensions, unreadable loaders) keeps the lookup.
     */
    private boolean depositVariantsPossible() {
        return packScan().variants();
    }

    private PackScan packScan() {
        IrisData data = getData();
        PackScan scan = packScan;
        if (scan == null || scan.data() != data) {
            scan = scanPack(data);
            packScan = scan;
        }
        return scan;
    }

    /**
     * Ore clumps resolve their cave biome only to apply its ore frequency and size multipliers; when no biome of
     * the loaded pack changes either, that lookup can never alter a clump and is skipped.
     */
    private PackScan scanPack(IrisData data) {
        IrisDimension dimension = getDimension();
        if (data == null || dimension == null || getEngine().getDimensionStackContext() != null) {
            return new PackScan(data, true, true);
        }
        Boolean regionVariants = anyLoaded(data.getRegionLoader(), region -> declaresVariants(region.getDepositVariants()));
        Boolean biomeVariants = anyLoaded(data.getBiomeLoader(), biome -> declaresVariants(biome.getDepositVariants()));
        Boolean oreScaling = anyLoaded(data.getBiomeLoader(), IrisDepositModifier::scalesOreDeposits);
        boolean variants = declaresVariants(dimension.getDepositVariants())
                || regionVariants == null || regionVariants || biomeVariants == null || biomeVariants;
        return new PackScan(data, variants, oreScaling == null || oreScaling);
    }

    private static boolean declaresVariants(List<IrisDepositVariant> declared) {
        return declared == null || !declared.isEmpty();
    }

    private static boolean scalesOreDeposits(IrisBiome biome) {
        return biome.getOreDepositFrequencyMultiplier() < 1D || biome.getOreDepositSizeMultiplier() != 1D;
    }

    private static <T extends IrisRegistrant> Boolean anyLoaded(ResourceLoader<T> loader, Predicate<T> test) {
        String[] keys = loader == null ? null : loader.getPossibleKeys();
        if (keys == null) {
            return null;
        }
        for (String key : keys) {
            T loaded = loader.load(key);
            if (loaded != null && test.test(loaded)) {
                return true;
            }
        }
        return false;
    }

    private NativeBlockState resolveDepositVariant(int cx, int cz, int nx, int localY, int nz, NativeBlockState ore, IrisDimension dimension, ChunkContext context, IrisDimensionCarvingResolver.State carvingState) {
        int worldX = (cx << 4) + nx;
        int worldZ = (cz << 4) + nz;
        int worldY = absoluteWorldY(getEngine().getMinHeight(), localY);

        IrisBiome biome = getEngine().getCaveBiome(worldX, localY, worldZ, carvingState);
        if (biome != null) {
            NativeBlockState match = matchDepositVariant(biome.getDepositVariants(), ore, worldY);
            if (match != null) {
                return match;
            }
        }

        IrisRegion region = context.getRegion().get(nx, nz);
        if (region != null) {
            NativeBlockState match = matchDepositVariant(region.getDepositVariants(), ore, worldY);
            if (match != null) {
                return match;
            }
        }

        if (dimension != null) {
            NativeBlockState match = matchDepositVariant(dimension.getDepositVariants(), ore, worldY);
            if (match != null) {
                return match;
            }
        }

        return null;
    }

    private NativeBlockState matchDepositVariant(java.util.List<IrisDepositVariant> variants, NativeBlockState ore, int y) {
        if (variants == null || variants.isEmpty()) {
            return null;
        }

        for (IrisDepositVariant variant : variants) {
            if (y < variant.getMinHeight() || y > variant.getMaxHeight()) {
                continue;
            }

            NativeBlockState swapped = variant.remapOrNull(ore, getData());
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

    private record PreparationContext(ChunkContext chunk, IrisEngine engine, IrisEngine.GenerationRuntimeBinding binding,
                                      Throwable[] failures) {
        private Throwable takeFailure(Throwable primary) {
            for (int i = 0; i < failures.length; i++) {
                Throwable failure = failures[i];
                failures[i] = null;
                if (failure == null || failure == primary) {
                    continue;
                }
                if (primary == null) {
                    primary = failure;
                } else if (!isSuppressed(primary, failure)) {
                    primary.addSuppressed(failure);
                }
            }
            return primary;
        }

        private boolean isSuppressed(Throwable primary, Throwable failure) {
            for (Throwable suppressed : primary.getSuppressed()) {
                if (suppressed == failure) {
                    return true;
                }
            }
            return false;
        }
    }

    private record DepositPlan(IrisDepositGenerator generator, long seed, boolean ore, int attempts) {
    }

    private record PackScan(IrisData data, boolean variants, boolean oreScaling) {
    }

    static final class ClumpRandom extends RNG {
        private static final long MULTIPLIER = 0x5DEECE66DL;
        private static final long ADDEND = 0xBL;
        private static final long MASK = (1L << 48) - 1;

        private long state;

        ClumpRandom(long seed) {
            super(seed);
        }

        @Override
        public synchronized void setSeed(long seed) {
            super.setSeed(seed);
            state = (seed ^ MULTIPLIER) & MASK;
        }

        @Override
        protected int next(int bits) {
            state = (state * MULTIPLIER + ADDEND) & MASK;
            return (int) (state >>> (48 - bits));
        }
    }

    private record PreparedDeposit(IrisDepositGenerator generator, boolean ore, List<PreparedClump> clumps) {
    }

    private record PreparedClump(IrisObject object, IrisDepositGenerator.ClumpCells cells, int x, int y, int z, RNG rng) {
    }

    private final class ClumpPlacement {
        private final IrisDepositGenerator generator;
        private final boolean ore;
        private final MantleChunk chunk;
        private final Hunk<NativeBlockState> data;
        private final int chunkX;
        private final int chunkZ;
        private final HeightMap heightMap;
        private final ChunkContext context;
        private final boolean variants;
        private final IrisDimension dimension;
        private final IrisDimensionCarvingResolver.State carvingState = new IrisDimensionCarvingResolver.State();
        private final IrisDepositPlacementScope scope;
        private final int surfaceClearance;
        private final double discardChance;
        private final int height;

        private ClumpPlacement(IrisDepositGenerator generator, boolean ore, MantleChunk chunk,
                               Hunk<NativeBlockState> data, int chunkX, int chunkZ, HeightMap heightMap,
                               ChunkContext context, boolean variants) {
            this.generator = generator;
            this.ore = ore;
            this.chunk = chunk;
            this.data = data;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.heightMap = heightMap;
            this.context = context;
            this.variants = variants;
            this.dimension = getDimension();
            this.scope = generator.getPlacementScope();
            this.surfaceClearance = generator.getSurfaceClearance();
            this.discardChance = generator.getDiscardChanceOnAirExposure();
            this.height = getEngine().getHeight();
        }

        private void place(int nx, int ny, int nz, NativeBlockState block, RNG rng) {
            if (nx > 15 || nx < 0 || ny >= height || ny < 0 || nz < 0 || nz > 15) {
                return;
            }
            int columnSurface = getDepositTerrainSurface(chunkX, chunkZ, nx, nz, heightMap, context);
            if (!placementSurfaceAllows(scope, ny, columnSurface, surfaceClearance)) {
                return;
            }

            DimensionStackLayout stackLayout = context.getDimensionStackLayout(nx, nz);
            if (stackLayout != null && stackLayout.isHostFeatureProtectedY(ny)) {
                return;
            }

            NativeBlockState current = data.get(nx, ny, nz);
            if (!canReplaceDepositTarget(current)) {
                return;
            }
            if (!generator.isReplaceBedrock() && IrisProceduralBlocks.materialKey(current).equals("minecraft:bedrock")) {
                return;
            }

            IrisBiome candidateSurfaceBiome = null;
            boolean exteriorSurface = false;
            if (ore) {
                candidateSurfaceBiome = context.getBiome().get(nx, nz);
                exteriorSurface = generator.hasSurfaceReplaceableBlocks(candidateSurfaceBiome)
                        && isTerrainSurface(data, nx, ny, nz, columnSurface);
            }
            if (!canReplaceDepositHost(generator, current, candidateSurfaceBiome, exteriorSurface)) {
                return;
            }
            if (discardChance > 0D) {
                boolean adjacentToAir = isAdjacentToAir(data, nx, ny, nz);
                if (shouldDiscardExposed(discardChance, rng.d(), adjacentToAir)) {
                    return;
                }
            }

            if (chunk.get(nx, ny, nz, MatterCavern.class) == null) {
                NativeBlockState remapped = variants ? resolveDepositVariant(
                        chunkX, chunkZ, nx, ny, nz, block, dimension, context, carvingState) : null;
                data.set(nx, ny, nz, remapped != null ? remapped : B.toDeepSlateOre(current, block));
            }
        }
    }
}
