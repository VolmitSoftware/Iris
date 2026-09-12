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

package art.arcane.iris.generation.mantle;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.cave.IrisCaveFieldModule;
import art.arcane.iris.generation.cave.IrisCaveProfile;
import art.arcane.iris.pack.value.IrisRange;
import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.generation.noise.IrisStyledRange;
import art.arcane.volmlib.util.noise.CNG;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.math.M;
import art.arcane.volmlib.util.math.PowerOfTwoCoordinates;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.volmlib.util.matter.MatterSlice;
import art.arcane.volmlib.util.scheduling.PrecisionStopwatch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

public class IrisCaveCarver3D {
    private static final byte LIQUID_AIR = 0;
    private static final byte LIQUID_FLUID = 1;
    private static final byte LIQUID_LAVA = 2;
    private static final byte LIQUID_FORCED_AIR = 3;
    private static final int ADAPTIVE_MIN_PLANE_COLUMNS = 16;
    private static final int ADAPTIVE_DEEP_SAMPLE_STEP = 8;
    private static final double ADAPTIVE_LOCAL_RANGE_SCALE = 0.125D;
    private static final double ADAPTIVE_DEEP_MARGIN_BOOST = 0.015D;
    private static final int SURFACE_CEILING_FADE_DEPTH = 12;
    private static final double SURFACE_CEILING_SOLID_EPSILON = 0.000001D;

    private static final byte DENSITY_CARVE = 1;
    private static final byte DENSITY_FLUID = 2;
    private static final int MINIMUM_PARALLEL_DENSITY_CELLS = 16_384;
    private static final int MAXIMUM_DENSITY_WORKERS = 4;
    private static final int[] LATTICE_TILE_OFFSETS = {0, 1, 16, 17};

    private final Engine engine;
    private final IrisData data;
    private final IrisCaveProfile profile;
    private final CNG baseDensity;
    private final CNG detailDensity;
    private final CNG warpDensity;
    private final CNG surfaceBreakDensity;
    private final RNG thresholdRng;
    private final CaveFieldModuleState[] modules;
    private final double inverseNormalization;
    private final MatterCavern carveAir;
    private final MatterCavern carveFluid;
    private final MatterCavern carveLava;
    private final MatterCavern carveForcedAir;
    private final double normalizationFactor;
    private final double baseWeight;
    private final double detailWeight;
    private final double detailMinContribution;
    private final double detailMaxContribution;
    private final double warpStrength;
    private final boolean hasWarp;
    private final boolean hasModules;
    private final int warpResolution;
    private final boolean allowFluid;
    private final boolean fluidRequiresFloor;
    private final int fluidMinDepthBelowSurface;
    private final int fluidHeight;
    private final int aquiferCeilingY;
    private final boolean parallelDensity;
    private final ThreadLocal<Boolean> carving = ThreadLocal.withInitial(() -> false);
    private final ThreadLocal<CaveCarveScratch> scratchCache = ThreadLocal.withInitial(CaveCarveScratch::new);

    public IrisCaveCarver3D(Engine engine, IrisCaveProfile profile) {
        this.engine = engine;
        this.data = engine.getData();
        this.profile = profile;
        this.carveAir = new MatterCavern(true, "", LIQUID_AIR);
        this.carveFluid = new MatterCavern(true, "", LIQUID_FLUID);
        this.carveLava = new MatterCavern(true, "", LIQUID_LAVA);
        this.carveForcedAir = new MatterCavern(true, "", LIQUID_FORCED_AIR);
        List<CaveFieldModuleState> moduleStates = new ArrayList<>();

        RNG baseRng = new RNG(engine.getSeedManager().getCarve());
        this.baseDensity = profile.getBaseDensityStyle().create(baseRng.nextParallelRNG(934_447), data);
        this.detailDensity = profile.getDetailDensityStyle().create(baseRng.nextParallelRNG(612_991), data);
        this.warpDensity = profile.getWarpStyle().create(baseRng.nextParallelRNG(770_713), data);
        this.surfaceBreakDensity = profile.getSurfaceBreakStyle().create(baseRng.nextParallelRNG(341_219), data);
        this.thresholdRng = baseRng.nextParallelRNG(489_112);
        this.baseWeight = profile.getBaseWeight();
        this.detailWeight = profile.getDetailWeight();
        this.warpStrength = profile.getWarpStrength();
        this.hasWarp = this.warpStrength > 0D;
        this.warpResolution = 2;
        this.allowFluid = profile.isAllowFluid();
        this.fluidRequiresFloor = profile.isFluidRequiresFloor();
        this.fluidMinDepthBelowSurface = Math.max(0, profile.getFluidMinDepthBelowSurface());
        this.fluidHeight = engine.getDimension().getFluidHeight();
        this.aquiferCeilingY = engine.getHeight() - 1;

        double weight = Math.abs(baseWeight) + Math.abs(detailWeight);
        int index = 0;
        for (IrisCaveFieldModule module : profile.getModules()) {
            CNG moduleDensity = module.getStyle().create(baseRng.nextParallelRNG(1_000_003L + (index * 65_537L)), data);
            CaveFieldModuleState state = new CaveFieldModuleState(module, moduleDensity);
            moduleStates.add(state);
            weight += Math.abs(state.weight);
            index++;
        }

        this.modules = moduleStates.toArray(new CaveFieldModuleState[0]);
        double normalization = weight <= 0 ? 1 : weight;
        normalizationFactor = normalization;
        inverseNormalization = 1D / normalization;
        hasModules = modules.length > 0;
        detailMinContribution = -detailWeight;
        detailMaxContribution = detailWeight;
        parallelDensity = fixedDensityStyles(profile);
    }

    public int carve(MantleWriter writer, int chunkX, int chunkZ) {
        CaveCarveScratch scratch = scratchCache.get();
        if (!scratch.fullWeightsInitialized) {
            Arrays.fill(scratch.fullWeights, 1D);
            scratch.fullWeightsInitialized = true;
        }
        return carve(writer, chunkX, chunkZ, scratch.fullWeights, 0D, 0D, null, null);
    }

    public int carve(
            MantleWriter writer,
            int chunkX,
            int chunkZ,
            double[] columnWeights,
            double minWeight,
            double thresholdPenalty
    ) {
        return carve(writer, chunkX, chunkZ, columnWeights, minWeight, thresholdPenalty, null, null);
    }

    public int carve(
            MantleWriter writer,
            int chunkX,
            int chunkZ,
            double[] columnWeights,
            double minWeight,
            double thresholdPenalty,
            IrisRange worldYRange
    ) {
        return carve(writer, chunkX, chunkZ, columnWeights, minWeight, thresholdPenalty, worldYRange, null);
    }

    public int carve(
            MantleWriter writer,
            int chunkX,
            int chunkZ,
            double[] columnWeights,
            double minWeight,
            double thresholdPenalty,
            IrisRange worldYRange,
            int[] precomputedSurfaceHeights
    ) {
        return carve(writer, chunkX, chunkZ, columnWeights, minWeight, thresholdPenalty, worldYRange, precomputedSurfaceHeights, null);
    }

    public int carve(
            MantleWriter writer,
            int chunkX,
            int chunkZ,
            double[] columnWeights,
            double minWeight,
            double thresholdPenalty,
            IrisRange worldYRange,
            int[] precomputedSurfaceHeights,
            IrisRange overrideVerticalRange
    ) {
        CaveFluidSupportPlan fluidSupportPlan = new CaveFluidSupportPlan();
        int carved = carve(writer, chunkX, chunkZ, columnWeights, minWeight, thresholdPenalty,
                worldYRange, precomputedSurfaceHeights, null, overrideVerticalRange, fluidSupportPlan);
        fluidSupportPlan.resolve(writer.acquireChunk(chunkX, chunkZ));
        return carved;
    }

    int carve(
            MantleWriter writer,
            int chunkX,
            int chunkZ,
            double[] columnWeights,
            double minWeight,
            double thresholdPenalty,
            IrisRange worldYRange,
            int[] precomputedSurfaceHeights,
            long[] surfaceFluidBoundaries,
            IrisRange overrideVerticalRange,
            CaveFluidSupportPlan fluidSupportPlan
    ) {
        PrecisionStopwatch applyStopwatch = PrecisionStopwatch.start();
        boolean nested = carving.get();
        CaveCarveScratch previousScratch = scratchCache.get();
        if (nested) {
            scratchCache.set(new CaveCarveScratch());
        }
        carving.set(true);
        try {
            CaveCarveScratch scratch = scratchCache.get();
            if (columnWeights == null || columnWeights.length < 256) {
                if (!scratch.fullWeightsInitialized) {
                    Arrays.fill(scratch.fullWeights, 1D);
                    scratch.fullWeightsInitialized = true;
                }
                columnWeights = scratch.fullWeights;
            }

            double resolvedMinWeight = Math.max(0D, Math.min(1D, minWeight));
            double resolvedThresholdPenalty = Math.max(0D, thresholdPenalty);
            int worldHeight = writer.getMantle().getWorldHeight();
            IrisRange effectiveVerticalRange = overrideVerticalRange != null ? overrideVerticalRange : profile.getVerticalRange();
            int minY = Math.max(0, (int) Math.floor(effectiveVerticalRange.getMin()));
            int maxY = Math.min(worldHeight - 1, (int) Math.ceil(effectiveVerticalRange.getMax()));
            if (worldYRange != null) {
                int worldMinHeight = engine.getWorld().minHeight();
                int rangeMinY = (int) Math.floor(worldYRange.getMin() - worldMinHeight);
                int rangeMaxY = (int) Math.ceil(worldYRange.getMax() - worldMinHeight);
                minY = Math.max(minY, rangeMinY);
                maxY = Math.min(maxY, rangeMaxY);
            }
            int sampleStep = Math.max(1, profile.getSampleStep());
            boolean exactSampling = sampleStep <= 2;
            boolean adaptiveSampling = exactSampling && profile.isAdaptiveSampling();
            int adaptiveSampleStep = Math.max(2, profile.getAdaptiveSampleStep());
            double adaptiveThresholdMargin = Math.max(0D, profile.getAdaptiveThresholdMargin());
            int surfaceClearance = Math.max(0, profile.getSurfaceClearance());
            int surfaceBreakDepth = Math.max(0, profile.getSurfaceBreakDepth());
            double surfaceBreakNoiseThreshold = profile.getSurfaceBreakNoiseThreshold();
            double surfaceBreakThresholdBoost = Math.max(0, profile.getSurfaceBreakThresholdBoost());
            boolean allowSurfaceBreak = profile.isAllowSurfaceBreak();
            if (maxY < minY) {
                return 0;
            }

            MantleChunk<Matter> chunk = writer.acquireChunk(chunkX, chunkZ);
            if (chunk == null) {
                return 0;
            }

            int x0 = PowerOfTwoCoordinates.chunkToBlock(chunkX);
            int z0 = PowerOfTwoCoordinates.chunkToBlock(chunkZ);
            int[] columnMaxY = scratch.columnMaxY;
            int[] fluidMaxY = scratch.fluidMaxY;
            int[] surfaceBreakFloorY = scratch.surfaceBreakFloorY;
            boolean[] surfaceBreakColumn = scratch.surfaceBreakColumn;
            boolean[] surfaceCeilingColumn = scratch.surfaceCeilingColumn;
            double[] columnThreshold = scratch.columnThreshold;
            double[] clampedWeights = scratch.clampedColumnWeights;
            double[] verticalEdgeFade = prepareVerticalEdgeFadeTable(scratch, minY, maxY);
            prepareSurfaceClosureThresholdTable(scratch, minY, maxY);
            MatterCavern[] matterByY = prepareMatterByYTable(scratch, minY, maxY);
            prepareSectionCaches(scratch, minY, maxY);

            // IrisStyledRange.get() resolves its CNG per call, which costs a cache-key allocation,
            // a capturing lambda and a shared LRU touch. The CNG is constant for (thresholdRng, data),
            // so resolve it once here and replicate the min==max / isFlat short circuits verbatim.
            IrisStyledRange densityThreshold = profile.getDensityThreshold();
            double thresholdMin = densityThreshold.getMin();
            double thresholdMax = densityThreshold.getMax();
            double thresholdBias = profile.getThresholdBias();
            CNG thresholdDensity = null;
            double constantThreshold = 0D;
            if (thresholdMin == thresholdMax) {
                constantThreshold = thresholdMin;
            } else if (densityThreshold.getStyle().isFlat()) {
                constantThreshold = M.lerp(thresholdMin, thresholdMax, 0.5);
            } else {
                thresholdDensity = densityThreshold.getStyle().create(thresholdRng, data);
            }

            for (int lx = 0; lx < 16; lx++) {
                int x = x0 + lx;
                for (int lz = 0; lz < 16; lz++) {
                    int z = z0 + lz;
                    int index = PowerOfTwoCoordinates.packLocal16(lx, lz);
                    int columnSurfaceY;
                    if (precomputedSurfaceHeights != null && precomputedSurfaceHeights.length > index) {
                        columnSurfaceY = precomputedSurfaceHeights[index];
                    } else {
                        columnSurfaceY = Engine.hostHeight(engine, x, z, true);
                    }
                    int unclampedClearanceTopY = columnSurfaceY - surfaceClearance;
                    int clearanceTopY = Math.min(maxY, Math.max(minY, unclampedClearanceTopY));
                    boolean breakColumn = allowSurfaceBreak
                            && surfaceBreakDensity.noiseFastSigned2D(x, z) >= surfaceBreakNoiseThreshold;
                    int columnTopY = breakColumn
                            ? Math.min(maxY, Math.max(minY, columnSurfaceY))
                            : clearanceTopY;

                    columnMaxY[index] = columnTopY;
                    fluidMaxY[index] = allowFluid
                            ? Math.min(fluidHeight, columnSurfaceY - fluidMinDepthBelowSurface)
                            : Integer.MIN_VALUE;
                    surfaceBreakFloorY[index] = Math.max(minY, columnSurfaceY - surfaceBreakDepth);
                    surfaceBreakColumn[index] = breakColumn;
                    surfaceCeilingColumn[index] = !breakColumn && unclampedClearanceTopY <= maxY;
                    columnThreshold[index] = (thresholdDensity == null
                            ? constantThreshold
                            : thresholdDensity.fitDouble(thresholdMin, thresholdMax, x, z)) - thresholdBias;
                    clampedWeights[index] = clampColumnWeight(columnWeights[index]);
                }
            }

            int carved;
            if (exactSampling) {
                if (adaptiveSampling) {
                    carved = carvePassAdaptive(
                            chunk,
                            x0,
                            z0,
                            minY,
                            maxY,
                            adaptiveSampleStep,
                            adaptiveThresholdMargin,
                            surfaceBreakThresholdBoost,
                            columnMaxY,
                            fluidMaxY,
                            surfaceFluidBoundaries,
                            surfaceBreakFloorY,
                            surfaceBreakColumn,
                            columnThreshold,
                            clampedWeights,
                            verticalEdgeFade,
                            matterByY,
                            fluidRequiresFloor ? fluidSupportPlan : null,
                            resolvedMinWeight,
                            resolvedThresholdPenalty,
                            0D,
                            false
                    );
                } else {
                    carved = carvePassExact(
                            chunk,
                            x0,
                            z0,
                            minY,
                            maxY,
                            surfaceBreakThresholdBoost,
                            columnMaxY,
                            fluidMaxY,
                            surfaceFluidBoundaries,
                            surfaceBreakFloorY,
                            surfaceBreakColumn,
                            columnThreshold,
                            clampedWeights,
                            verticalEdgeFade,
                            matterByY,
                            fluidRequiresFloor ? fluidSupportPlan : null,
                            resolvedMinWeight,
                            resolvedThresholdPenalty,
                            0D,
                            false
                    );
                }
            } else {
                int latticeStep = sampleStep;
                carved = carvePassLattice(
                        chunk,
                        x0,
                        z0,
                        minY,
                        maxY,
                        latticeStep,
                        surfaceBreakThresholdBoost,
                        columnMaxY,
                        fluidMaxY,
                        surfaceFluidBoundaries,
                        surfaceBreakFloorY,
                        surfaceBreakColumn,
                        columnThreshold,
                        clampedWeights,
                        verticalEdgeFade,
                        matterByY,
                        fluidRequiresFloor ? fluidSupportPlan : null,
                        resolvedMinWeight,
                        resolvedThresholdPenalty,
                        0D,
                        false
                );
                if (carved == 0 && hasFallbackCandidates(columnMaxY, clampedWeights, minY, resolvedMinWeight)) {
                    carved += carvePassFallback(
                            chunk,
                            x0,
                            z0,
                            minY,
                            maxY,
                            sampleStep,
                            surfaceBreakThresholdBoost,
                            columnMaxY,
                            fluidMaxY,
                            surfaceFluidBoundaries,
                            surfaceBreakFloorY,
                            surfaceBreakColumn,
                            columnThreshold,
                            clampedWeights,
                            verticalEdgeFade,
                            matterByY,
                            fluidRequiresFloor ? fluidSupportPlan : null,
                            resolvedMinWeight,
                            resolvedThresholdPenalty,
                            0D,
                            false
                    );
                }
            }

            return carved;
        } finally {
            scratchCache.get().releaseSections();
            carving.set(nested);
            if (nested) {
                scratchCache.set(previousScratch);
            }
            engine.getMetrics().getCarveApply().put(applyStopwatch.getMilliseconds());
        }
    }

    private static boolean fixedDensityStyles(IrisCaveProfile profile) {
        if (!fixedStyle(profile.getBaseDensityStyle()) || !fixedStyle(profile.getDetailDensityStyle())
                || !fixedStyle(profile.getWarpStyle()) || !fixedStyle(profile.getSurfaceBreakStyle())
                || !fixedStyle(profile.getDensityThreshold().getStyle())) {
            return false;
        }
        for (IrisCaveFieldModule module : profile.getModules()) {
            if (!fixedStyle(module.getStyle())) {
                return false;
            }
        }
        return true;
    }

    private static boolean fixedStyle(IrisGeneratorStyle style) {
        for (IrisGeneratorStyle current = style; current != null; current = current.getFracture()) {
            if (current.getExpression() != null) {
                return false;
            }
        }
        return true;
    }

    private byte[] classifyPassParallel(DensityPass pass) {
        int height = pass.maximumY() - pass.minimumY() + 1;
        if (!parallelDensity || height < 64
                || (long) height * pass.columnCount() < MINIMUM_PARALLEL_DENSITY_CELLS
                || !(Thread.currentThread() instanceof ForkJoinWorkerThread worker)) {
            return null;
        }
        ForkJoinPool pool = worker.getPool();
        int workers = Math.min(MAXIMUM_DENSITY_WORKERS, pool.getParallelism());
        if (workers < 2) {
            return null;
        }
        byte[] classified = new byte[Math.multiplyExact(height, 256)];
        int rows = Math.ceilDiv(height, workers);
        ArrayList<Runnable> tasks = new ArrayList<>(workers);
        for (int index = 0; index < workers; index++) {
            int minimumY = pass.minimumY() + index * rows;
            int maximumY = Math.min(pass.maximumY(), minimumY + rows - 1);
            if (minimumY <= maximumY) {
                tasks.add(() -> classifyRows(pass, minimumY, maximumY, classified));
            }
        }
        runDensityTasks(tasks, pool);
        return classified;
    }

    private void classifyRows(DensityPass pass, int minimumY, int maximumY, byte[] classified) {
        CaveCarveScratch scratch = new CaveCarveScratch();
        for (int y = minimumY; y <= maximumY; y++) {
            int count = 0;
            for (int index = 0; index < pass.columnCount(); index++) {
                int column = pass.columns()[index];
                if (pass.columnTops()[index] < y
                        || SurfaceFluidBoundaryPlan.protects(pass.fluidBoundaries(), column, y)) {
                    continue;
                }
                double threshold = pass.thresholds()[column];
                if (pass.surfaceBreakColumns()[column] && y >= pass.surfaceBreakFloors()[column]) {
                    threshold += pass.surfaceBreakBoost();
                }
                threshold -= pass.verticalFade()[y - pass.minimumY()];
                threshold = applySurfaceCeilingFade(pass.sourceScratch(), threshold, column, y, pass.minimumY());
                scratch.planeColumnIndices[count] = column;
                scratch.planeThresholdLimit[count++] = threshold * normalizationFactor;
            }
            if (count == 0) {
                continue;
            }
            if (pass.adaptiveStep() == 0) {
                classifyDensityPlane(scratch, pass.x(), pass.z(), y, scratch.planeColumnIndices,
                        scratch.planeThresholdLimit, count, scratch.planeCarve);
            } else {
                classifyDensityPlaneAdaptive(scratch, pass.x(), pass.z(), y, scratch.planeColumnIndices,
                        scratch.planeThresholdLimit, count, scratch.planeCarve,
                        pass.adaptiveStep(), pass.adaptiveMargin());
            }
            int offset = (y - pass.minimumY()) * 256;
            for (int index = 0; index < count; index++) {
                if (!scratch.planeCarve[index]) {
                    continue;
                }
                int column = scratch.planeColumnIndices[index];
                double threshold = scratch.planeThresholdLimit[index] * inverseNormalization;
                MatterCavern matter = resolveMatter(scratch, pass.matterByY()[y - pass.minimumY()],
                        pass.x() + PowerOfTwoCoordinates.unpackLocal16X(column), y, pass.z() + (column & 15),
                        column, pass.fluidTops(), threshold);
                classified[offset + column] = matter == carveFluid
                        ? DENSITY_CARVE | DENSITY_FLUID : DENSITY_CARVE;
            }
        }
    }

    private double[] sampleLatticeParallel(
            int x, int z, int minimumY, int maximumY, int step, double[] thresholds, int[] tops
    ) {
        int rows = (maximumY - minimumY) / step + 1;
        if (!parallelDensity || rows < 64
                || !(Thread.currentThread() instanceof ForkJoinWorkerThread worker)) {
            return null;
        }
        ForkJoinPool pool = worker.getPool();
        int workers = Math.min(MAXIMUM_DENSITY_WORKERS, pool.getParallelism());
        if (workers < 2) {
            return null;
        }
        int[] tileTops = new int[64];
        int samples = 0;
        for (int tile = 0; tile < 64; tile++) {
            int column = (tile >> 3) * 32 + (tile & 7) * 2;
            int top = Integer.MIN_VALUE;
            for (int offset : LATTICE_TILE_OFFSETS) {
                if (!Double.isNaN(thresholds[column + offset])) {
                    top = Math.max(top, tops[column + offset]);
                }
            }
            tileTops[tile] = top;
            if (top >= minimumY) {
                samples += (top - minimumY) / step + 1;
            }
        }
        if (samples < 4096) {
            return null;
        }
        double[] densities = new double[Math.multiplyExact(rows, 64)];
        LatticePass pass = new LatticePass(x, z, minimumY, step, rows, tileTops);
        int tilesPerWorker = Math.ceilDiv(64, workers);
        ArrayList<Runnable> tasks = new ArrayList<>(workers);
        for (int index = 0; index < workers; index++) {
            int firstTile = index * tilesPerWorker;
            int lastTile = Math.min(64, firstTile + tilesPerWorker);
            tasks.add(() -> sampleLatticeTiles(pass, firstTile, lastTile, densities));
        }
        runDensityTasks(tasks, pool);
        return densities;
    }

    private void sampleLatticeTiles(LatticePass pass, int firstTile, int lastTile, double[] densities) {
        CaveCarveScratch scratch = new CaveCarveScratch();
        for (int tile = firstTile; tile < lastTile; tile++) {
            int blockX = pass.x() + (tile >> 3) * 2;
            int blockZ = pass.z() + (tile & 7) * 2;
            int offset = tile * pass.rows();
            for (int y = pass.minimumY(); y <= pass.tileTops()[tile]; y += pass.step()) {
                densities[offset++] = sampleDensityOptimized(scratch, blockX, y, blockZ);
            }
        }
    }

    private static void copyClassifiedPlane(byte[] classified, int row, int[] columns, int count, boolean[] result) {
        int offset = row * 256;
        for (int index = 0; index < count; index++) {
            result[index] = (classified[offset + columns[index]] & DENSITY_CARVE) != 0;
        }
    }

    static void runDensityTasks(List<Runnable> work, ForkJoinPool pool) {
        ArrayList<DensityTask> tasks = new ArrayList<>(work.size());
        for (Runnable runnable : work) {
            tasks.add(new DensityTask(runnable));
        }
        Throwable failure = null;
        for (int index = 1; index < tasks.size(); index++) {
            try {
                pool.execute(tasks.get(index));
            } catch (RuntimeException | Error rejected) {
                failure = rejected;
                break;
            }
        }
        for (DensityTask task : tasks) {
            task.run();
        }
        boolean interrupted = Thread.interrupted();
        for (DensityTask task : tasks) {
            boolean complete = false;
            boolean managed = true;
            while (!complete) {
                try {
                    if (managed) {
                        ForkJoinPool.managedBlock(task);
                    } else {
                        task.complete.await();
                    }
                    complete = true;
                } catch (InterruptedException interruption) {
                    interrupted = true;
                    managed = false;
                } catch (RejectedExecutionException unavailable) {
                    managed = false;
                } catch (RuntimeException | Error blockingFailure) {
                    if (failure == null) {
                        failure = blockingFailure;
                    } else if (failure != blockingFailure) {
                        failure.addSuppressed(blockingFailure);
                    }
                    managed = false;
                }
            }
            if (task.failure != null) {
                if (failure == null) {
                    failure = task.failure;
                } else if (failure != task.failure) {
                    failure.addSuppressed(task.failure);
                }
            }
        }
        interrupted |= Thread.interrupted();
        if (interrupted) {
            Thread.currentThread().interrupt();
            InterruptedException interruption = new InterruptedException("Cave density classification interrupted.");
            if (failure == null) {
                failure = new IllegalStateException(interruption);
            } else {
                failure.addSuppressed(interruption);
            }
        }
        if (failure instanceof Error error) {
            throw error;
        }
        if (failure instanceof RuntimeException runtimeFailure) {
            throw runtimeFailure;
        }
    }

    private record LatticePass(int x, int z, int minimumY, int step, int rows, int[] tileTops) {
    }

    private record DensityPass(
            int x, int z, int minimumY, int maximumY,
            int[] columns, int[] columnTops, int columnCount, double[] thresholds,
            long[] fluidBoundaries, boolean[] surfaceBreakColumns, int[] surfaceBreakFloors,
            double surfaceBreakBoost, double[] verticalFade, CaveCarveScratch sourceScratch,
            int adaptiveStep, double adaptiveMargin, int[] fluidTops, MatterCavern[] matterByY
    ) {
    }

    private static final class DensityTask implements Runnable, ForkJoinPool.ManagedBlocker {
        private final Runnable work;
        private final AtomicBoolean claimed = new AtomicBoolean();
        private final CountDownLatch complete = new CountDownLatch(1);
        private Throwable failure;

        private DensityTask(Runnable work) {
            this.work = work;
        }

        @Override
        public boolean isReleasable() {
            return complete.getCount() == 0;
        }

        @Override
        public boolean block() throws InterruptedException {
            complete.await();
            return true;
        }

        @Override
        public void run() {
            if (!claimed.compareAndSet(false, true)) {
                return;
            }
            try {
                work.run();
            } catch (RuntimeException | Error failed) {
                failure = failed;
            } finally {
                complete.countDown();
            }
        }
    }

    private int carvePassExact(
            MantleChunk<Matter> chunk,
            int x0,
            int z0,
            int minY,
            int maxY,
            double surfaceBreakThresholdBoost,
            int[] columnMaxY,
            int[] fluidMaxY,
            long[] surfaceFluidBoundaries,
            int[] surfaceBreakFloorY,
            boolean[] surfaceBreakColumn,
            double[] columnThreshold,
            double[] clampedWeights,
            double[] verticalEdgeFade,
            MatterCavern[] matterByY,
            CaveFluidSupportPlan fluidSupportPlan,
            double minWeight,
            double thresholdPenalty,
            double thresholdBoost,
            boolean skipExistingCarved
    ) {
        int carved = 0;
        CaveCarveScratch scratch = scratchCache.get();
        double[] passThreshold = scratch.passThreshold;
        int[] activeColumnIndices = scratch.activeColumnIndices;
        int[] activeColumnTopY = scratch.activeColumnTopY;
        int activeColumnCount = 0;
        int activeMaxY = Integer.MIN_VALUE;

        for (int index = 0; index < 256; index++) {
            double columnWeight = clampedWeights[index];
            if (columnWeight <= minWeight || columnMaxY[index] < minY) {
                passThreshold[index] = Double.NaN;
                continue;
            }

            passThreshold[index] = columnThreshold[index] + thresholdBoost - ((1D - columnWeight) * thresholdPenalty);
            activeColumnIndices[activeColumnCount] = index;
            activeColumnTopY[activeColumnCount] = columnMaxY[index];
            activeMaxY = Math.max(activeMaxY, columnMaxY[index]);
            activeColumnCount++;
        }

        if (activeColumnCount == 0) {
            return 0;
        }

        int[] planeColumnIndices = scratch.planeColumnIndices;
        double[] planeThresholdLimit = scratch.planeThresholdLimit;
        boolean[] planeCarve = scratch.planeCarve;
        int minSection = PowerOfTwoCoordinates.floorDivPow2(minY, 4);
        int passMaxY = Math.min(maxY, activeMaxY);
        int maxSection = PowerOfTwoCoordinates.floorDivPow2(passMaxY, 4);

        byte[] classified = skipExistingCarved ? null : classifyPassParallel(new DensityPass(
                x0, z0, minY, passMaxY, activeColumnIndices, activeColumnTopY, activeColumnCount,
                passThreshold, surfaceFluidBoundaries, surfaceBreakColumn, surfaceBreakFloorY,
                surfaceBreakThresholdBoost, verticalEdgeFade, scratch, 0, 0D, fluidMaxY, matterByY));

        for (int sectionIndex = minSection; sectionIndex <= maxSection; sectionIndex++) {
            int sectionMinY = Math.max(minY, PowerOfTwoCoordinates.chunkToBlock(sectionIndex));
            int sectionMaxY = Math.min(passMaxY, PowerOfTwoCoordinates.chunkToBlock(sectionIndex) + 15);
            MatterSlice<MatterCavern> cavernSlice = resolveCavernSlice(scratch, chunk, sectionIndex);

            for (int y = sectionMinY; y <= sectionMaxY; y++) {
                int planeCount = 0;
                for (int activeIndex = 0; activeIndex < activeColumnCount; activeIndex++) {
                    if (activeColumnTopY[activeIndex] < y) {
                        continue;
                    }

                    int columnIndex = activeColumnIndices[activeIndex];
                    if (SurfaceFluidBoundaryPlan.protects(surfaceFluidBoundaries, columnIndex, y)) {
                        continue;
                    }
                    planeColumnIndices[planeCount] = columnIndex;
                    double localThreshold = passThreshold[columnIndex];
                    if (surfaceBreakColumn[columnIndex] && y >= surfaceBreakFloorY[columnIndex]) {
                        localThreshold += surfaceBreakThresholdBoost;
                    }
                    localThreshold -= verticalEdgeFade[y - minY];
                    localThreshold = applySurfaceCeilingFade(scratch, localThreshold, columnIndex, y, minY);
                    planeThresholdLimit[planeCount] = localThreshold * normalizationFactor;
                    planeCount++;
                }

                if (planeCount == 0) {
                    continue;
                }

                if (classified == null) {
                    classifyDensityPlane(scratch, x0, z0, y, planeColumnIndices, planeThresholdLimit, planeCount, planeCarve);
                } else {
                    copyClassifiedPlane(classified, y - minY, planeColumnIndices, planeCount, planeCarve);
                }
                int fadeIndex = y - minY;
                int localY = y & 15;
                MatterCavern verticalMatter = matterByY[fadeIndex];

                if (skipExistingCarved) {
                    for (int planeIndex = 0; planeIndex < planeCount; planeIndex++) {
                        if (!planeCarve[planeIndex]) {
                            continue;
                        }

                        int columnIndex = planeColumnIndices[planeIndex];
                        int localX = PowerOfTwoCoordinates.unpackLocal16X(columnIndex);
                        int localZ = columnIndex & 15;
                        if (cavernSlice.get(localX, localY, localZ) != null) {
                            continue;
                        }

                        double localThreshold = planeThresholdLimit[planeIndex] * inverseNormalization;
                        MatterCavern matter = resolveMatter(scratch, verticalMatter, x0 + localX, y, z0 + localZ,
                                columnIndex, fluidMaxY, localThreshold);
                        writeCavern(cavernSlice, localX, y, localZ, matter, fluidSupportPlan);
                        carved++;
                    }
                    continue;
                }

                for (int planeIndex = 0; planeIndex < planeCount; planeIndex++) {
                    if (!planeCarve[planeIndex]) {
                        continue;
                    }

                    int columnIndex = planeColumnIndices[planeIndex];
                    int localX = PowerOfTwoCoordinates.unpackLocal16X(columnIndex);
                    int localZ = columnIndex & 15;
                    double localThreshold = planeThresholdLimit[planeIndex] * inverseNormalization;
                    MatterCavern matter = classified == null
                            ? resolveMatter(scratch, verticalMatter, x0 + localX, y, z0 + localZ,
                                    columnIndex, fluidMaxY, localThreshold)
                            : (classified[fadeIndex * 256 + columnIndex] & DENSITY_FLUID) != 0
                                    ? carveFluid : verticalMatter;
                    writeCavern(cavernSlice, localX, y, localZ, matter, fluidSupportPlan);
                    carved++;
                }
            }
        }

        return carved;
    }

    private int carvePassAdaptive(
            MantleChunk<Matter> chunk,
            int x0,
            int z0,
            int minY,
            int maxY,
            int adaptiveSampleStep,
            double adaptiveThresholdMargin,
            double surfaceBreakThresholdBoost,
            int[] columnMaxY,
            int[] fluidMaxY,
            long[] surfaceFluidBoundaries,
            int[] surfaceBreakFloorY,
            boolean[] surfaceBreakColumn,
            double[] columnThreshold,
            double[] clampedWeights,
            double[] verticalEdgeFade,
            MatterCavern[] matterByY,
            CaveFluidSupportPlan fluidSupportPlan,
            double minWeight,
            double thresholdPenalty,
            double thresholdBoost,
            boolean skipExistingCarved
    ) {
        int carved = 0;
        CaveCarveScratch scratch = scratchCache.get();
        double[] passThreshold = scratch.passThreshold;
        int[] activeColumnIndices = scratch.activeColumnIndices;
        int[] activeColumnTopY = scratch.activeColumnTopY;
        int activeColumnCount = 0;
        int activeMaxY = Integer.MIN_VALUE;

        for (int index = 0; index < 256; index++) {
            double columnWeight = clampedWeights[index];
            if (columnWeight <= minWeight || columnMaxY[index] < minY) {
                passThreshold[index] = Double.NaN;
                continue;
            }

            passThreshold[index] = columnThreshold[index] + thresholdBoost - ((1D - columnWeight) * thresholdPenalty);
            activeColumnIndices[activeColumnCount] = index;
            activeColumnTopY[activeColumnCount] = columnMaxY[index];
            activeMaxY = Math.max(activeMaxY, columnMaxY[index]);
            activeColumnCount++;
        }

        if (activeColumnCount == 0) {
            return 0;
        }

        int[] planeColumnIndices = scratch.planeColumnIndices;
        double[] planeThresholdLimit = scratch.planeThresholdLimit;
        boolean[] planeCarve = scratch.planeCarve;
        int minSection = PowerOfTwoCoordinates.floorDivPow2(minY, 4);
        int passMaxY = Math.min(maxY, activeMaxY);
        int maxSection = PowerOfTwoCoordinates.floorDivPow2(passMaxY, 4);
        int effectiveAdaptiveSampleStep = Math.max(adaptiveSampleStep, ADAPTIVE_DEEP_SAMPLE_STEP);
        double effectiveAdaptiveThresholdMargin = resolveAdaptivePlaneThresholdMargin(
                adaptiveThresholdMargin,
                adaptiveSampleStep,
                effectiveAdaptiveSampleStep
        );

        byte[] classified = skipExistingCarved ? null : classifyPassParallel(new DensityPass(
                x0, z0, minY, passMaxY, activeColumnIndices, activeColumnTopY, activeColumnCount,
                passThreshold, surfaceFluidBoundaries, surfaceBreakColumn, surfaceBreakFloorY,
                surfaceBreakThresholdBoost, verticalEdgeFade, scratch, effectiveAdaptiveSampleStep, effectiveAdaptiveThresholdMargin,
                fluidMaxY, matterByY));

        for (int sectionIndex = minSection; sectionIndex <= maxSection; sectionIndex++) {
            int sectionMinY = Math.max(minY, PowerOfTwoCoordinates.chunkToBlock(sectionIndex));
            int sectionMaxY = Math.min(passMaxY, PowerOfTwoCoordinates.chunkToBlock(sectionIndex) + 15);
            MatterSlice<MatterCavern> cavernSlice = resolveCavernSlice(scratch, chunk, sectionIndex);

            for (int y = sectionMinY; y <= sectionMaxY; y++) {
                int planeCount = 0;
                for (int activeIndex = 0; activeIndex < activeColumnCount; activeIndex++) {
                    if (activeColumnTopY[activeIndex] < y) {
                        continue;
                    }

                    int columnIndex = activeColumnIndices[activeIndex];
                    if (SurfaceFluidBoundaryPlan.protects(surfaceFluidBoundaries, columnIndex, y)) {
                        continue;
                    }
                    planeColumnIndices[planeCount] = columnIndex;
                    double localThreshold = passThreshold[columnIndex];
                    if (surfaceBreakColumn[columnIndex] && y >= surfaceBreakFloorY[columnIndex]) {
                        localThreshold += surfaceBreakThresholdBoost;
                    }
                    localThreshold -= verticalEdgeFade[y - minY];
                    localThreshold = applySurfaceCeilingFade(scratch, localThreshold, columnIndex, y, minY);
                    planeThresholdLimit[planeCount] = localThreshold * normalizationFactor;
                    planeCount++;
                }

                if (planeCount == 0) {
                    continue;
                }

                if (classified == null) {
                    classifyDensityPlaneAdaptive(
                            scratch,
                            x0,
                            z0,
                            y,
                            planeColumnIndices,
                            planeThresholdLimit,
                            planeCount,
                            planeCarve,
                            effectiveAdaptiveSampleStep,
                            effectiveAdaptiveThresholdMargin
                    );
                } else {
                    copyClassifiedPlane(classified, y - minY, planeColumnIndices, planeCount, planeCarve);
                }
                int fadeIndex = y - minY;
                int localY = y & 15;
                MatterCavern verticalMatter = matterByY[fadeIndex];

                if (skipExistingCarved) {
                    for (int planeIndex = 0; planeIndex < planeCount; planeIndex++) {
                        if (!planeCarve[planeIndex]) {
                            continue;
                        }

                        int columnIndex = planeColumnIndices[planeIndex];
                        int localX = PowerOfTwoCoordinates.unpackLocal16X(columnIndex);
                        int localZ = columnIndex & 15;
                        if (cavernSlice.get(localX, localY, localZ) != null) {
                            continue;
                        }

                        double localThreshold = planeThresholdLimit[planeIndex] * inverseNormalization;
                        MatterCavern matter = resolveMatter(scratch, verticalMatter, x0 + localX, y, z0 + localZ,
                                columnIndex, fluidMaxY, localThreshold);
                        writeCavern(cavernSlice, localX, y, localZ, matter, fluidSupportPlan);
                        carved++;
                    }
                    continue;
                }

                for (int planeIndex = 0; planeIndex < planeCount; planeIndex++) {
                    if (!planeCarve[planeIndex]) {
                        continue;
                    }

                    int columnIndex = planeColumnIndices[planeIndex];
                    int localX = PowerOfTwoCoordinates.unpackLocal16X(columnIndex);
                    int localZ = columnIndex & 15;
                    double localThreshold = planeThresholdLimit[planeIndex] * inverseNormalization;
                    MatterCavern matter = classified == null
                            ? resolveMatter(scratch, verticalMatter, x0 + localX, y, z0 + localZ,
                                    columnIndex, fluidMaxY, localThreshold)
                            : (classified[fadeIndex * 256 + columnIndex] & DENSITY_FLUID) != 0
                                    ? carveFluid : verticalMatter;
                    writeCavern(cavernSlice, localX, y, localZ, matter, fluidSupportPlan);
                    carved++;
                }
            }
        }

        return carved;
    }

    private double resolveAdaptivePlaneThresholdMargin(
            double adaptiveThresholdMargin,
            int adaptiveSampleStep,
            int effectiveAdaptiveSampleStep
    ) {
        if (effectiveAdaptiveSampleStep <= adaptiveSampleStep) {
            return adaptiveThresholdMargin;
        }

        return adaptiveThresholdMargin + ((effectiveAdaptiveSampleStep - adaptiveSampleStep) * ADAPTIVE_DEEP_MARGIN_BOOST);
    }

    private int carvePassLattice(
            MantleChunk<Matter> chunk,
            int x0,
            int z0,
            int minY,
            int maxY,
            int latticeStep,
            double surfaceBreakThresholdBoost,
            int[] columnMaxY,
            int[] fluidMaxY,
            long[] surfaceFluidBoundaries,
            int[] surfaceBreakFloorY,
            boolean[] surfaceBreakColumn,
            double[] columnThreshold,
            double[] clampedWeights,
            double[] verticalEdgeFade,
            MatterCavern[] matterByY,
            CaveFluidSupportPlan fluidSupportPlan,
            double minWeight,
            double thresholdPenalty,
            double thresholdBoost,
            boolean skipExistingCarved
    ) {
        int carved = 0;
        CaveCarveScratch scratch = scratchCache.get();
        double[] passThreshold = scratch.passThreshold;
        int[] tileIndices = scratch.tileIndices;
        int[] tileLocalX = scratch.tileLocalX;
        int[] tileLocalZ = scratch.tileLocalZ;
        int[] tileTopY = scratch.tileTopY;

        for (int index = 0; index < 256; index++) {
            double columnWeight = clampedWeights[index];
            if (columnWeight <= minWeight || columnMaxY[index] < minY) {
                passThreshold[index] = Double.NaN;
                continue;
            }
            passThreshold[index] = columnThreshold[index] + thresholdBoost - ((1D - columnWeight) * thresholdPenalty);
        }

        double[] latticeDensity = sampleLatticeParallel(x0, z0, minY, maxY, latticeStep, passThreshold, columnMaxY);
        int latticeRows = (maxY - minY) / latticeStep + 1;

        for (int lx = 0; lx < 16; lx += 2) {
            int x = x0 + lx;
            int lx1 = lx + 1;
            for (int lz = 0; lz < 16; lz += 2) {
                int z = z0 + lz;
                int lz1 = lz + 1;
                int activeColumns = 0;

                int index00 = PowerOfTwoCoordinates.packLocal16(lx, lz);
                if (!Double.isNaN(passThreshold[index00])) {
                    tileIndices[activeColumns] = index00;
                    tileLocalX[activeColumns] = lx;
                    tileLocalZ[activeColumns] = lz;
                    tileTopY[activeColumns] = columnMaxY[index00];
                    activeColumns++;
                }

                int index01 = PowerOfTwoCoordinates.packLocal16(lx, lz1);
                if (!Double.isNaN(passThreshold[index01])) {
                    tileIndices[activeColumns] = index01;
                    tileLocalX[activeColumns] = lx;
                    tileLocalZ[activeColumns] = lz1;
                    tileTopY[activeColumns] = columnMaxY[index01];
                    activeColumns++;
                }

                int index10 = PowerOfTwoCoordinates.packLocal16(lx1, lz);
                if (!Double.isNaN(passThreshold[index10])) {
                    tileIndices[activeColumns] = index10;
                    tileLocalX[activeColumns] = lx1;
                    tileLocalZ[activeColumns] = lz;
                    tileTopY[activeColumns] = columnMaxY[index10];
                    activeColumns++;
                }

                int index11 = PowerOfTwoCoordinates.packLocal16(lx1, lz1);
                if (!Double.isNaN(passThreshold[index11])) {
                    tileIndices[activeColumns] = index11;
                    tileLocalX[activeColumns] = lx1;
                    tileLocalZ[activeColumns] = lz1;
                    tileTopY[activeColumns] = columnMaxY[index11];
                    activeColumns++;
                }

                if (activeColumns == 0) {
                    continue;
                }

                int tileMaxY = minY;
                for (int columnIndex = 0; columnIndex < activeColumns; columnIndex++) {
                    if (tileTopY[columnIndex] > tileMaxY) {
                        tileMaxY = tileTopY[columnIndex];
                    }
                }
                if (tileMaxY < minY) {
                    continue;
                }

                for (int y = minY; y <= tileMaxY; y += latticeStep) {
                    double density = latticeDensity == null
                            ? sampleDensityOptimized(scratch, x, y, z)
                            : latticeDensity[((lx >> 1) * 8 + (lz >> 1)) * latticeRows + (y - minY) / latticeStep];
                    int stampMaxY = Math.min(maxY, y + 1);
                    for (int yy = y; yy <= stampMaxY; yy++) {
                        MatterCavern verticalMatter = matterByY[yy - minY];
                        MatterSlice<MatterCavern> cavernSlice = resolveCavernSlice(scratch, chunk, PowerOfTwoCoordinates.floorDivPow2(yy, 4));
                        int localY = yy & 15;
                        int fadeIndex = yy - minY;
                        for (int columnIndex = 0; columnIndex < activeColumns; columnIndex++) {
                            if (yy > tileTopY[columnIndex]) {
                                continue;
                            }

                            int index = tileIndices[columnIndex];
                            if (SurfaceFluidBoundaryPlan.protects(surfaceFluidBoundaries, index, yy)) {
                                continue;
                            }
                            double localThreshold = passThreshold[index];
                            if (surfaceBreakColumn[index] && yy >= surfaceBreakFloorY[index]) {
                                localThreshold += surfaceBreakThresholdBoost;
                            }
                            localThreshold -= verticalEdgeFade[fadeIndex];
                            localThreshold = applySurfaceCeilingFade(scratch, localThreshold, index, yy, minY);
                            if (density > localThreshold) {
                                continue;
                            }

                            int localX = tileLocalX[columnIndex];
                            int localZ = tileLocalZ[columnIndex];
                            int worldX = x0 + localX;
                            int worldZ = z0 + localZ;
                            MatterCavern matter = resolveMatter(scratch, verticalMatter, worldX, yy, worldZ,
                                    index, fluidMaxY, localThreshold);
                            if (skipExistingCarved) {
                                if (cavernSlice.get(localX, localY, localZ) == null) {
                                    writeCavern(cavernSlice, localX, yy, localZ, matter, fluidSupportPlan);
                                    carved++;
                                }
                                continue;
                            }

                            writeCavern(cavernSlice, localX, yy, localZ, matter, fluidSupportPlan);
                            carved++;
                        }
                    }
                }
            }
        }

        return carved;
    }

    private int carvePassFallback(
            MantleChunk<Matter> chunk,
            int x0,
            int z0,
            int minY,
            int maxY,
            int sampleStep,
            double surfaceBreakThresholdBoost,
            int[] columnMaxY,
            int[] fluidMaxY,
            long[] surfaceFluidBoundaries,
            int[] surfaceBreakFloorY,
            boolean[] surfaceBreakColumn,
            double[] columnThreshold,
            double[] clampedWeights,
            double[] verticalEdgeFade,
            MatterCavern[] matterByY,
            CaveFluidSupportPlan fluidSupportPlan,
            double minWeight,
            double thresholdPenalty,
            double thresholdBoost,
            boolean skipExistingCarved
    ) {
        int carved = 0;
        CaveCarveScratch scratch = scratchCache.get();

        for (int lx = 0; lx < 16; lx++) {
            int x = x0 + lx;
            for (int lz = 0; lz < 16; lz++) {
                int z = z0 + lz;
                int index = PowerOfTwoCoordinates.packLocal16(lx, lz);
                double columnWeight = clampedWeights[index];
                if (columnWeight <= minWeight) {
                    continue;
                }

                int columnTopY = columnMaxY[index];
                if (columnTopY < minY) {
                    continue;
                }

                boolean breakColumn = surfaceBreakColumn[index];
                int breakFloorY = surfaceBreakFloorY[index];
                double threshold = columnThreshold[index] + thresholdBoost - ((1D - columnWeight) * thresholdPenalty);

                for (int y = minY; y <= columnTopY; y += sampleStep) {
                    double density = sampleDensityOptimized(scratch, x, y, z);
                    int carveMaxY = Math.min(columnTopY, y + sampleStep - 1);
                    for (int yy = y; yy <= carveMaxY; yy++) {
                        if (SurfaceFluidBoundaryPlan.protects(surfaceFluidBoundaries, index, yy)) {
                            continue;
                        }
                        double localThreshold = threshold;
                        if (breakColumn && yy >= breakFloorY) {
                            localThreshold += surfaceBreakThresholdBoost;
                        }
                        localThreshold -= verticalEdgeFade[yy - minY];
                        localThreshold = applySurfaceCeilingFade(scratch, localThreshold, index, yy, minY);
                        if (density > localThreshold) {
                            continue;
                        }
                        MatterCavern verticalMatter = matterByY[yy - minY];
                        MatterCavern matter = resolveMatter(scratch, verticalMatter, x, yy, z,
                                index, fluidMaxY, localThreshold);
                        MatterSlice<MatterCavern> cavernSlice = resolveCavernSlice(scratch, chunk, PowerOfTwoCoordinates.floorDivPow2(yy, 4));
                        int localY = yy & 15;
                        if (skipExistingCarved) {
                            if (cavernSlice.get(lx, localY, lz) == null) {
                                writeCavern(cavernSlice, lx, yy, lz, matter, fluidSupportPlan);
                                carved++;
                            }
                            continue;
                        }

                        writeCavern(cavernSlice, lx, yy, lz, matter, fluidSupportPlan);
                        carved++;
                    }
                }
            }
        }

        return carved;
    }

    private boolean hasFallbackCandidates(int[] columnMaxY, double[] clampedWeights, int minY, double minWeight) {
        for (int index = 0; index < 256; index++) {
            if (clampedWeights[index] <= minWeight) {
                continue;
            }

            if (columnMaxY[index] >= minY) {
                return true;
            }
        }

        return false;
    }

    private double sampleDensityOptimized(int x, int y, int z) {
        if (!hasWarp && !hasModules) {
            return sampleDensityNoWarpNoModules(x, y, z);
        }

        return sampleDensityOptimized(scratchCache.get(), x, y, z);
    }

    private double sampleDensityOptimized(CaveCarveScratch scratch, int x, int y, int z) {
        if (!hasWarp) {
            if (!hasModules) {
                return sampleDensityNoWarpNoModules(x, y, z);
            }

            return sampleDensityNoWarpModules(scratch, x, y, z);
        }

        if (!hasModules) {
            return sampleDensityWarpOnly(scratch, x, y, z);
        }

        return sampleDensityWarpModules(scratch, x, y, z);
    }

    private void classifyDensityPlane(CaveCarveScratch scratch, int x0, int z0, int y, int[] planeColumnIndices, double[] planeThresholdLimit, int planeCount, boolean[] planeCarve) {
        if (!hasWarp) {
            if (!hasModules) {
                classifyDensityPlaneNoWarpNoModules(x0, z0, y, planeColumnIndices, planeThresholdLimit, planeCount, planeCarve);
                return;
            }

            classifyDensityPlaneNoWarpModules(scratch, x0, z0, y, planeColumnIndices, planeThresholdLimit, planeCount, planeCarve);
            return;
        }

        if (!hasModules) {
            classifyDensityPlaneWarpOnly(scratch, x0, z0, y, planeColumnIndices, planeThresholdLimit, planeCount, planeCarve);
            return;
        }

        classifyDensityPlaneWarpModules(scratch, x0, z0, y, planeColumnIndices, planeThresholdLimit, planeCount, planeCarve);
    }

    private void classifyDensityPlaneAdaptive(
            CaveCarveScratch scratch,
            int x0,
            int z0,
            int y,
            int[] planeColumnIndices,
            double[] planeThresholdLimit,
            int planeCount,
            boolean[] planeCarve,
            int adaptiveSampleStep,
            double adaptiveThresholdMargin
    ) {
        if (adaptiveSampleStep <= 1 || planeCount < ADAPTIVE_MIN_PLANE_COLUMNS) {
            classifyDensityPlane(scratch, x0, z0, y, planeColumnIndices, planeThresholdLimit, planeCount, planeCarve);
            return;
        }

        if (!hasWarp) {
            if (!hasModules) {
                classifyDensityPlaneAdaptiveNoWarpNoModules(scratch, x0, z0, y, planeColumnIndices, planeThresholdLimit, planeCount, planeCarve, adaptiveSampleStep, adaptiveThresholdMargin);
                return;
            }

            classifyDensityPlaneAdaptiveNoWarpModules(scratch, x0, z0, y, planeColumnIndices, planeThresholdLimit, planeCount, planeCarve, adaptiveSampleStep, adaptiveThresholdMargin);
            return;
        }

        if (!hasModules) {
            classifyDensityPlaneAdaptiveWarpOnly(scratch, x0, z0, y, planeColumnIndices, planeThresholdLimit, planeCount, planeCarve, adaptiveSampleStep, adaptiveThresholdMargin);
            return;
        }

        classifyDensityPlaneAdaptiveWarpModules(scratch, x0, z0, y, planeColumnIndices, planeThresholdLimit, planeCount, planeCarve, adaptiveSampleStep, adaptiveThresholdMargin);
    }

    private void classifyDensityPlaneNoWarpNoModules(int x0, int z0, int y, int[] planeColumnIndices, double[] planeThresholdLimit, int planeCount, boolean[] planeCarve) {
        for (int planeIndex = 0; planeIndex < planeCount; planeIndex++) {
            int columnIndex = planeColumnIndices[planeIndex];
            int x = x0 + PowerOfTwoCoordinates.unpackLocal16X(columnIndex);
            int z = z0 + (columnIndex & 15);
            planeCarve[planeIndex] = classifyDensityPointNoWarpNoModules(x, y, z, planeThresholdLimit[planeIndex]);
        }
    }

    private void classifyDensityPlaneNoWarpModules(CaveCarveScratch scratch, int x0, int z0, int y, int[] planeColumnIndices, double[] planeThresholdLimit, int planeCount, boolean[] planeCarve) {
        int activeModuleCount = prepareActiveModules(scratch, y);
        if (activeModuleCount == 0) {
            classifyDensityPlaneNoWarpNoModules(x0, z0, y, planeColumnIndices, planeThresholdLimit, planeCount, planeCarve);
            return;
        }

        CaveFieldModuleState[] localModules = scratch.activeModules;
        double[] remainingMin = scratch.activeModuleRemainingMin;
        double[] remainingMax = scratch.activeModuleRemainingMax;

        for (int planeIndex = 0; planeIndex < planeCount; planeIndex++) {
            int columnIndex = planeColumnIndices[planeIndex];
            int x = x0 + PowerOfTwoCoordinates.unpackLocal16X(columnIndex);
            int z = z0 + (columnIndex & 15);
            planeCarve[planeIndex] = classifyDensityPointNoWarpModules(
                    x,
                    y,
                    z,
                    planeThresholdLimit[planeIndex],
                    localModules,
                    activeModuleCount,
                    remainingMin,
                    remainingMax
            );
        }
    }

    private void classifyDensityPlaneWarpOnly(CaveCarveScratch scratch, int x0, int z0, int y, int[] planeColumnIndices, double[] planeThresholdLimit, int planeCount, boolean[] planeCarve) {
        for (int planeIndex = 0; planeIndex < planeCount; planeIndex++) {
            int columnIndex = planeColumnIndices[planeIndex];
            int x = x0 + PowerOfTwoCoordinates.unpackLocal16X(columnIndex);
            int z = z0 + (columnIndex & 15);
            planeCarve[planeIndex] = classifyDensityPointWarpOnly(scratch, x, y, z, planeThresholdLimit[planeIndex]);
        }
    }

    private void classifyDensityPlaneWarpModules(CaveCarveScratch scratch, int x0, int z0, int y, int[] planeColumnIndices, double[] planeThresholdLimit, int planeCount, boolean[] planeCarve) {
        int activeModuleCount = prepareActiveModules(scratch, y);
        if (activeModuleCount == 0) {
            classifyDensityPlaneWarpOnly(scratch, x0, z0, y, planeColumnIndices, planeThresholdLimit, planeCount, planeCarve);
            return;
        }

        CaveFieldModuleState[] localModules = scratch.activeModules;
        double[] remainingMin = scratch.activeModuleRemainingMin;
        double[] remainingMax = scratch.activeModuleRemainingMax;

        for (int planeIndex = 0; planeIndex < planeCount; planeIndex++) {
            int columnIndex = planeColumnIndices[planeIndex];
            int x = x0 + PowerOfTwoCoordinates.unpackLocal16X(columnIndex);
            int z = z0 + (columnIndex & 15);
            planeCarve[planeIndex] = classifyDensityPointWarpModules(
                    scratch,
                    x,
                    y,
                    z,
                    planeThresholdLimit[planeIndex],
                    localModules,
                    activeModuleCount,
                    remainingMin,
                    remainingMax
            );
        }
    }

    private void classifyDensityPlaneAdaptiveNoWarpNoModules(
            CaveCarveScratch scratch,
            int x0,
            int z0,
            int y,
            int[] planeColumnIndices,
            double[] planeThresholdLimit,
            int planeCount,
            boolean[] planeCarve,
            int adaptiveSampleStep,
            double adaptiveThresholdMargin
    ) {
        double[] adaptivePlaneDensity = scratch.adaptivePlaneDensity;
        int axisCells = (16 + adaptiveSampleStep - 1) / adaptiveSampleStep;
        int axisSamples = axisCells + 1;
        int[] adaptivePlaneSampleBounds = scratch.adaptivePlaneSampleBounds;
        prepareAdaptivePlaneSampleBounds(scratch, planeColumnIndices, planeCount, adaptiveSampleStep, adaptivePlaneSampleBounds, axisCells);
        for (int sampleXIndex = adaptivePlaneSampleBounds[0]; sampleXIndex <= adaptivePlaneSampleBounds[1]; sampleXIndex++) {
            int sampleLocalX = Math.min(sampleXIndex * adaptiveSampleStep, 16);
            int x = x0 + sampleLocalX;
            int rowOffset = sampleXIndex * axisSamples;
            for (int sampleZIndex = adaptivePlaneSampleBounds[2]; sampleZIndex <= adaptivePlaneSampleBounds[3]; sampleZIndex++) {
                int sampleLocalZ = Math.min(sampleZIndex * adaptiveSampleStep, 16);
                adaptivePlaneDensity[rowOffset + sampleZIndex] = sampleDensityNoWarpNoModules(x, y, z0 + sampleLocalZ);
            }
        }

        classifyAdaptivePlaneColumnsNoWarpNoModules(
                scratch,
                x0,
                z0,
                y,
                planeColumnIndices,
                planeThresholdLimit,
                planeCount,
                planeCarve,
                adaptiveSampleStep,
                adaptiveThresholdMargin,
                adaptivePlaneDensity,
                axisCells,
                axisSamples
        );
    }

    private void classifyDensityPlaneAdaptiveNoWarpModules(
            CaveCarveScratch scratch,
            int x0,
            int z0,
            int y,
            int[] planeColumnIndices,
            double[] planeThresholdLimit,
            int planeCount,
            boolean[] planeCarve,
            int adaptiveSampleStep,
            double adaptiveThresholdMargin
    ) {
        int activeModuleCount = prepareActiveModules(scratch, y);
        if (activeModuleCount == 0) {
            classifyDensityPlaneAdaptiveNoWarpNoModules(scratch, x0, z0, y, planeColumnIndices, planeThresholdLimit, planeCount, planeCarve, adaptiveSampleStep, adaptiveThresholdMargin);
            return;
        }

        CaveFieldModuleState[] localModules = scratch.activeModules;
        double[] remainingMin = scratch.activeModuleRemainingMin;
        double[] remainingMax = scratch.activeModuleRemainingMax;
        double[] adaptivePlaneDensity = scratch.adaptivePlaneDensity;
        int axisCells = (16 + adaptiveSampleStep - 1) / adaptiveSampleStep;
        int axisSamples = axisCells + 1;
        int[] adaptivePlaneSampleBounds = scratch.adaptivePlaneSampleBounds;
        prepareAdaptivePlaneSampleBounds(scratch, planeColumnIndices, planeCount, adaptiveSampleStep, adaptivePlaneSampleBounds, axisCells);
        for (int sampleXIndex = adaptivePlaneSampleBounds[0]; sampleXIndex <= adaptivePlaneSampleBounds[1]; sampleXIndex++) {
            int sampleLocalX = Math.min(sampleXIndex * adaptiveSampleStep, 16);
            int x = x0 + sampleLocalX;
            int rowOffset = sampleXIndex * axisSamples;
            for (int sampleZIndex = adaptivePlaneSampleBounds[2]; sampleZIndex <= adaptivePlaneSampleBounds[3]; sampleZIndex++) {
                int sampleLocalZ = Math.min(sampleZIndex * adaptiveSampleStep, 16);
                adaptivePlaneDensity[rowOffset + sampleZIndex] = sampleDensityNoWarpNoModules(x, y, z0 + sampleLocalZ);
            }
        }

        classifyAdaptivePlaneColumnsNoWarpModules(
                scratch,
                x0,
                z0,
                y,
                planeColumnIndices,
                planeThresholdLimit,
                planeCount,
                planeCarve,
                adaptiveSampleStep,
                adaptiveThresholdMargin,
                adaptivePlaneDensity,
                axisCells,
                axisSamples,
                localModules,
                activeModuleCount,
                remainingMin,
                remainingMax
        );
    }

    private void classifyDensityPlaneAdaptiveWarpOnly(
            CaveCarveScratch scratch,
            int x0,
            int z0,
            int y,
            int[] planeColumnIndices,
            double[] planeThresholdLimit,
            int planeCount,
            boolean[] planeCarve,
            int adaptiveSampleStep,
            double adaptiveThresholdMargin
    ) {
        double[] adaptivePlaneDensity = scratch.adaptivePlaneDensity;
        int axisCells = (16 + adaptiveSampleStep - 1) / adaptiveSampleStep;
        int axisSamples = axisCells + 1;
        int[] adaptivePlaneSampleBounds = scratch.adaptivePlaneSampleBounds;
        prepareAdaptivePlaneSampleBounds(scratch, planeColumnIndices, planeCount, adaptiveSampleStep, adaptivePlaneSampleBounds, axisCells);
        for (int sampleXIndex = adaptivePlaneSampleBounds[0]; sampleXIndex <= adaptivePlaneSampleBounds[1]; sampleXIndex++) {
            int sampleLocalX = Math.min(sampleXIndex * adaptiveSampleStep, 16);
            int x = x0 + sampleLocalX;
            int rowOffset = sampleXIndex * axisSamples;
            for (int sampleZIndex = adaptivePlaneSampleBounds[2]; sampleZIndex <= adaptivePlaneSampleBounds[3]; sampleZIndex++) {
                int sampleLocalZ = Math.min(sampleZIndex * adaptiveSampleStep, 16);
                adaptivePlaneDensity[rowOffset + sampleZIndex] = sampleDensityWarpOnly(scratch, x, y, z0 + sampleLocalZ);
            }
        }

        classifyAdaptivePlaneColumnsWarpOnly(
                scratch,
                x0,
                z0,
                y,
                planeColumnIndices,
                planeThresholdLimit,
                planeCount,
                planeCarve,
                adaptiveSampleStep,
                adaptiveThresholdMargin,
                adaptivePlaneDensity,
                axisCells,
                axisSamples
        );
    }

    private void classifyDensityPlaneAdaptiveWarpModules(
            CaveCarveScratch scratch,
            int x0,
            int z0,
            int y,
            int[] planeColumnIndices,
            double[] planeThresholdLimit,
            int planeCount,
            boolean[] planeCarve,
            int adaptiveSampleStep,
            double adaptiveThresholdMargin
    ) {
        int activeModuleCount = prepareActiveModules(scratch, y);
        if (activeModuleCount == 0) {
            classifyDensityPlaneAdaptiveWarpOnly(scratch, x0, z0, y, planeColumnIndices, planeThresholdLimit, planeCount, planeCarve, adaptiveSampleStep, adaptiveThresholdMargin);
            return;
        }

        CaveFieldModuleState[] localModules = scratch.activeModules;
        double[] remainingMin = scratch.activeModuleRemainingMin;
        double[] remainingMax = scratch.activeModuleRemainingMax;
        double[] adaptivePlaneDensity = scratch.adaptivePlaneDensity;
        int axisCells = (16 + adaptiveSampleStep - 1) / adaptiveSampleStep;
        int axisSamples = axisCells + 1;
        int[] adaptivePlaneSampleBounds = scratch.adaptivePlaneSampleBounds;
        prepareAdaptivePlaneSampleBounds(scratch, planeColumnIndices, planeCount, adaptiveSampleStep, adaptivePlaneSampleBounds, axisCells);
        for (int sampleXIndex = adaptivePlaneSampleBounds[0]; sampleXIndex <= adaptivePlaneSampleBounds[1]; sampleXIndex++) {
            int sampleLocalX = Math.min(sampleXIndex * adaptiveSampleStep, 16);
            int x = x0 + sampleLocalX;
            int rowOffset = sampleXIndex * axisSamples;
            for (int sampleZIndex = adaptivePlaneSampleBounds[2]; sampleZIndex <= adaptivePlaneSampleBounds[3]; sampleZIndex++) {
                int sampleLocalZ = Math.min(sampleZIndex * adaptiveSampleStep, 16);
                adaptivePlaneDensity[rowOffset + sampleZIndex] = sampleDensityWarpModules(
                        scratch,
                        x,
                        y,
                        z0 + sampleLocalZ,
                        localModules,
                        activeModuleCount
                );
            }
        }

        classifyAdaptivePlaneColumnsWarpModulesSampled(
                scratch,
                x0,
                z0,
                y,
                planeColumnIndices,
                planeThresholdLimit,
                planeCount,
                planeCarve,
                adaptiveSampleStep,
                adaptiveThresholdMargin,
                adaptivePlaneDensity,
                axisCells,
                axisSamples,
                localModules,
                activeModuleCount,
                remainingMin,
                remainingMax
        );
    }

    private void classifyAdaptivePlaneColumnsNoWarpNoModules(
            CaveCarveScratch scratch,
            int x0,
            int z0,
            int y,
            int[] planeColumnIndices,
            double[] planeThresholdLimit,
            int planeCount,
            boolean[] planeCarve,
            int adaptiveSampleStep,
            double adaptiveThresholdMargin,
            double[] adaptivePlaneDensity,
            int axisCells,
            int axisSamples
    ) {
        double[] adaptivePlanePrediction = scratch.adaptivePlanePrediction;
        double[] adaptivePlaneAmbiguity = scratch.adaptivePlaneAmbiguity;
        prepareAdaptivePlaneColumns(
                scratch,
                planeColumnIndices,
                planeCount,
                adaptiveSampleStep,
                adaptiveThresholdMargin,
                adaptivePlaneDensity,
                axisCells,
                axisSamples,
                adaptivePlanePrediction,
                adaptivePlaneAmbiguity
        );
        for (int planeIndex = 0; planeIndex < planeCount; planeIndex++) {
            int columnIndex = planeColumnIndices[planeIndex];
            int localX = PowerOfTwoCoordinates.unpackLocal16X(columnIndex);
            int localZ = columnIndex & 15;
            double threshold = planeThresholdLimit[planeIndex] * inverseNormalization;
            double predictedDensity = adaptivePlanePrediction[planeIndex];
            double ambiguityMargin = adaptivePlaneAmbiguity[planeIndex];
            if (isAdaptivePlaneSampleAligned(localX, localZ, adaptiveSampleStep)) {
                planeCarve[planeIndex] = predictedDensity <= threshold;
                continue;
            }
            if (predictedDensity <= threshold - ambiguityMargin) {
                planeCarve[planeIndex] = true;
                continue;
            }
            if (predictedDensity > threshold + ambiguityMargin) {
                planeCarve[planeIndex] = false;
                continue;
            }
            if (adaptiveSampleStep >= ADAPTIVE_DEEP_SAMPLE_STEP) {
                planeCarve[planeIndex] = predictedDensity <= threshold;
                continue;
            }

            planeCarve[planeIndex] = classifyDensityPointNoWarpNoModules(x0 + localX, y, z0 + localZ, planeThresholdLimit[planeIndex]);
        }
    }

    private void classifyAdaptivePlaneColumnsNoWarpModules(
            CaveCarveScratch scratch,
            int x0,
            int z0,
            int y,
            int[] planeColumnIndices,
            double[] planeThresholdLimit,
            int planeCount,
            boolean[] planeCarve,
            int adaptiveSampleStep,
            double adaptiveThresholdMargin,
            double[] adaptivePlaneDensity,
            int axisCells,
            int axisSamples,
            CaveFieldModuleState[] localModules,
            int activeModuleCount,
            double[] remainingMin,
            double[] remainingMax
    ) {
        double[] adaptivePlanePrediction = scratch.adaptivePlanePrediction;
        double[] adaptivePlaneAmbiguity = scratch.adaptivePlaneAmbiguity;
        prepareAdaptivePlaneColumns(
                scratch,
                planeColumnIndices,
                planeCount,
                adaptiveSampleStep,
                adaptiveThresholdMargin,
                adaptivePlaneDensity,
                axisCells,
                axisSamples,
                adaptivePlanePrediction,
                adaptivePlaneAmbiguity
        );
        double minRemaining = remainingMin[0] * inverseNormalization;
        double maxRemaining = remainingMax[0] * inverseNormalization;
        for (int planeIndex = 0; planeIndex < planeCount; planeIndex++) {
            int columnIndex = planeColumnIndices[planeIndex];
            int localX = PowerOfTwoCoordinates.unpackLocal16X(columnIndex);
            int localZ = columnIndex & 15;
            double threshold = planeThresholdLimit[planeIndex] * inverseNormalization;
            double predictedDensity = adaptivePlanePrediction[planeIndex];
            double ambiguityMargin = adaptivePlaneAmbiguity[planeIndex];
            if (isAdaptivePlaneSampleAligned(localX, localZ, adaptiveSampleStep)) {
                planeCarve[planeIndex] = classifyDensityPointNoWarpModulesFromExactDensity(
                        x0 + localX,
                        y,
                        z0 + localZ,
                        threshold,
                        predictedDensity,
                        localModules,
                        activeModuleCount,
                        remainingMin,
                        remainingMax
                );
                continue;
            }
            if ((predictedDensity + maxRemaining) <= threshold - ambiguityMargin) {
                planeCarve[planeIndex] = true;
                continue;
            }
            if ((predictedDensity + minRemaining) > threshold + ambiguityMargin) {
                planeCarve[planeIndex] = false;
                continue;
            }
            if (adaptiveSampleStep >= ADAPTIVE_DEEP_SAMPLE_STEP) {
                planeCarve[planeIndex] = predictedDensity <= threshold;
                continue;
            }

            planeCarve[planeIndex] = classifyDensityPointNoWarpModules(
                    x0 + localX,
                    y,
                    z0 + localZ,
                    planeThresholdLimit[planeIndex],
                    localModules,
                    activeModuleCount,
                    remainingMin,
                    remainingMax
            );
        }
    }

    private void classifyDeepAdaptivePlaneFromSamples(
            CaveCarveScratch scratch,
            int[] planeColumnIndices,
            double[] planeThresholdLimit,
            int planeCount,
            boolean[] planeCarve,
            int adaptiveSampleStep,
            double[] adaptivePlaneDensity,
            int axisCells,
            int axisSamples
    ) {
        prepareAdaptiveGeometry(scratch, adaptiveSampleStep, axisCells, axisSamples);
        int[] adaptiveCellZ = scratch.adaptiveCellZ;
        int[] adaptiveRow0 = scratch.adaptiveRow0;
        int[] adaptiveRow1 = scratch.adaptiveRow1;
        double[] adaptiveTx = scratch.adaptiveTx;
        double[] adaptiveTz = scratch.adaptiveTz;
        for (int planeIndex = 0; planeIndex < planeCount; planeIndex++) {
            int columnIndex = planeColumnIndices[planeIndex];
            int cellZ = adaptiveCellZ[columnIndex];
            int row0 = adaptiveRow0[columnIndex];
            int row1 = adaptiveRow1[columnIndex];
            double tx = adaptiveTx[columnIndex];
            double tz = adaptiveTz[columnIndex];
            double d00 = adaptivePlaneDensity[row0 + cellZ];
            double d01 = adaptivePlaneDensity[row0 + cellZ + 1];
            double d10 = adaptivePlaneDensity[row1 + cellZ];
            double d11 = adaptivePlaneDensity[row1 + cellZ + 1];
            double dx0 = d00 + ((d10 - d00) * tx);
            double dx1 = d01 + ((d11 - d01) * tx);
            double predictedDensity = dx0 + ((dx1 - dx0) * tz);
            planeCarve[planeIndex] = predictedDensity <= planeThresholdLimit[planeIndex] * inverseNormalization;
        }
    }

    private void classifyAdaptivePlaneColumnsWarpOnly(
            CaveCarveScratch scratch,
            int x0,
            int z0,
            int y,
            int[] planeColumnIndices,
            double[] planeThresholdLimit,
            int planeCount,
            boolean[] planeCarve,
            int adaptiveSampleStep,
            double adaptiveThresholdMargin,
            double[] adaptivePlaneDensity,
            int axisCells,
            int axisSamples
    ) {
        double[] adaptivePlanePrediction = scratch.adaptivePlanePrediction;
        double[] adaptivePlaneAmbiguity = scratch.adaptivePlaneAmbiguity;
        prepareAdaptivePlaneColumns(
                scratch,
                planeColumnIndices,
                planeCount,
                adaptiveSampleStep,
                adaptiveThresholdMargin,
                adaptivePlaneDensity,
                axisCells,
                axisSamples,
                adaptivePlanePrediction,
                adaptivePlaneAmbiguity
        );
        for (int planeIndex = 0; planeIndex < planeCount; planeIndex++) {
            int columnIndex = planeColumnIndices[planeIndex];
            int localX = PowerOfTwoCoordinates.unpackLocal16X(columnIndex);
            int localZ = columnIndex & 15;
            double threshold = planeThresholdLimit[planeIndex] * inverseNormalization;
            double predictedDensity = adaptivePlanePrediction[planeIndex];
            double ambiguityMargin = adaptivePlaneAmbiguity[planeIndex];
            if (isAdaptivePlaneSampleAligned(localX, localZ, adaptiveSampleStep)) {
                planeCarve[planeIndex] = predictedDensity <= threshold;
                continue;
            }
            if (predictedDensity <= threshold - ambiguityMargin) {
                planeCarve[planeIndex] = true;
                continue;
            }
            if (predictedDensity > threshold + ambiguityMargin) {
                planeCarve[planeIndex] = false;
                continue;
            }
            if (adaptiveSampleStep >= ADAPTIVE_DEEP_SAMPLE_STEP) {
                planeCarve[planeIndex] = predictedDensity <= threshold;
                continue;
            }

            planeCarve[planeIndex] = classifyDensityPointWarpOnly(scratch, x0 + localX, y, z0 + localZ, planeThresholdLimit[planeIndex]);
        }
    }

    private void classifyAdaptivePlaneColumnsWarpModules(
            CaveCarveScratch scratch,
            int x0,
            int z0,
            int y,
            int[] planeColumnIndices,
            double[] planeThresholdLimit,
            int planeCount,
            boolean[] planeCarve,
            int adaptiveSampleStep,
            double adaptiveThresholdMargin,
            double[] adaptivePlaneDensity,
            int axisCells,
            int axisSamples,
            CaveFieldModuleState[] localModules,
            int activeModuleCount,
            double[] remainingMin,
            double[] remainingMax
    ) {
        double[] adaptivePlanePrediction = scratch.adaptivePlanePrediction;
        double[] adaptivePlaneAmbiguity = scratch.adaptivePlaneAmbiguity;
        prepareAdaptivePlaneColumns(
                scratch,
                planeColumnIndices,
                planeCount,
                adaptiveSampleStep,
                adaptiveThresholdMargin,
                adaptivePlaneDensity,
                axisCells,
                axisSamples,
                adaptivePlanePrediction,
                adaptivePlaneAmbiguity
        );
        double minRemaining = remainingMin[0] * inverseNormalization;
        double maxRemaining = remainingMax[0] * inverseNormalization;
        for (int planeIndex = 0; planeIndex < planeCount; planeIndex++) {
            int columnIndex = planeColumnIndices[planeIndex];
            int localX = PowerOfTwoCoordinates.unpackLocal16X(columnIndex);
            int localZ = columnIndex & 15;
            double threshold = planeThresholdLimit[planeIndex] * inverseNormalization;
            double predictedDensity = adaptivePlanePrediction[planeIndex];
            double ambiguityMargin = adaptivePlaneAmbiguity[planeIndex];
            if (isAdaptivePlaneSampleAligned(localX, localZ, adaptiveSampleStep)) {
                planeCarve[planeIndex] = classifyDensityPointWarpModulesFromExactDensity(
                        scratch,
                        x0 + localX,
                        y,
                        z0 + localZ,
                        threshold,
                        predictedDensity,
                        localModules,
                        activeModuleCount,
                        remainingMin,
                        remainingMax
                );
                continue;
            }
            if ((predictedDensity + maxRemaining) <= threshold - ambiguityMargin) {
                planeCarve[planeIndex] = true;
                continue;
            }
            if ((predictedDensity + minRemaining) > threshold + ambiguityMargin) {
                planeCarve[planeIndex] = false;
                continue;
            }

            planeCarve[planeIndex] = classifyDensityPointWarpModules(
                    scratch,
                    x0 + localX,
                    y,
                    z0 + localZ,
                    planeThresholdLimit[planeIndex],
                    localModules,
                    activeModuleCount,
                    remainingMin,
                    remainingMax
            );
        }
    }

    private void classifyAdaptivePlaneColumnsWarpModulesSampled(
            CaveCarveScratch scratch,
            int x0,
            int z0,
            int y,
            int[] planeColumnIndices,
            double[] planeThresholdLimit,
            int planeCount,
            boolean[] planeCarve,
            int adaptiveSampleStep,
            double adaptiveThresholdMargin,
            double[] adaptivePlaneDensity,
            int axisCells,
            int axisSamples,
            CaveFieldModuleState[] localModules,
            int activeModuleCount,
            double[] remainingMin,
            double[] remainingMax
    ) {
        if (adaptiveSampleStep >= ADAPTIVE_DEEP_SAMPLE_STEP) {
            classifyDeepAdaptivePlaneFromSamples(
                    scratch,
                    planeColumnIndices,
                    planeThresholdLimit,
                    planeCount,
                    planeCarve,
                    adaptiveSampleStep,
                    adaptivePlaneDensity,
                    axisCells,
                    axisSamples
            );
            return;
        }

        double[] adaptivePlanePrediction = scratch.adaptivePlanePrediction;
        double[] adaptivePlaneAmbiguity = scratch.adaptivePlaneAmbiguity;
        prepareAdaptivePlaneColumns(
                scratch,
                planeColumnIndices,
                planeCount,
                adaptiveSampleStep,
                adaptiveThresholdMargin,
                adaptivePlaneDensity,
                axisCells,
                axisSamples,
                adaptivePlanePrediction,
                adaptivePlaneAmbiguity
        );
        for (int planeIndex = 0; planeIndex < planeCount; planeIndex++) {
            int columnIndex = planeColumnIndices[planeIndex];
            int localX = PowerOfTwoCoordinates.unpackLocal16X(columnIndex);
            int localZ = columnIndex & 15;
            double threshold = planeThresholdLimit[planeIndex] * inverseNormalization;
            double predictedDensity = adaptivePlanePrediction[planeIndex];
            double ambiguityMargin = adaptivePlaneAmbiguity[planeIndex];
            if (isAdaptivePlaneSampleAligned(localX, localZ, adaptiveSampleStep)) {
                planeCarve[planeIndex] = predictedDensity <= threshold;
                continue;
            }
            if (predictedDensity <= threshold - ambiguityMargin) {
                planeCarve[planeIndex] = true;
                continue;
            }
            if (predictedDensity > threshold + ambiguityMargin) {
                planeCarve[planeIndex] = false;
                continue;
            }
            if (adaptiveSampleStep >= ADAPTIVE_DEEP_SAMPLE_STEP) {
                planeCarve[planeIndex] = predictedDensity <= threshold;
                continue;
            }

            planeCarve[planeIndex] = classifyDensityPointWarpModules(
                    scratch,
                    x0 + localX,
                    y,
                    z0 + localZ,
                    planeThresholdLimit[planeIndex],
                    localModules,
                    activeModuleCount,
                    remainingMin,
                    remainingMax
            );
        }
    }

    private boolean classifyDensityPointNoWarpNoModules(int x, int y, int z, double thresholdLimit) {
        double density = baseDensity.noiseFastSigned3D(x, y, z) * baseWeight;
        if ((density + detailMinContribution) > thresholdLimit) {
            return false;
        }
        if ((density + detailMaxContribution) <= thresholdLimit) {
            return true;
        }

        density += detailDensity.noiseFastSigned3D(x, y, z) * detailWeight;
        return density <= thresholdLimit;
    }

    private boolean classifyDensityPointNoWarpModules(
            int x,
            int y,
            int z,
            double thresholdLimit,
            CaveFieldModuleState[] localModules,
            int activeModuleCount,
            double[] remainingMin,
            double[] remainingMax
    ) {
        if (activeModuleCount == 0) {
            return classifyDensityPointNoWarpNoModules(x, y, z, thresholdLimit);
        }

        double density = baseDensity.noiseFastSigned3D(x, y, z) * baseWeight;
        if ((density + detailMinContribution + remainingMin[0]) > thresholdLimit) {
            return false;
        }
        if ((density + detailMaxContribution + remainingMax[0]) <= thresholdLimit) {
            return true;
        }

        density += detailDensity.noiseFastSigned3D(x, y, z) * detailWeight;
        if ((density + remainingMin[0]) > thresholdLimit) {
            return false;
        }
        if ((density + remainingMax[0]) <= thresholdLimit) {
            return true;
        }

        for (int moduleIndex = 0; moduleIndex < activeModuleCount; moduleIndex++) {
            density += localModules[moduleIndex].sample(x, y, z);
            if ((density + remainingMin[moduleIndex + 1]) > thresholdLimit) {
                return false;
            }
            if ((density + remainingMax[moduleIndex + 1]) <= thresholdLimit) {
                return true;
            }
        }

        return density <= thresholdLimit;
    }

    private int snapWarp(int c) {
        int g = warpResolution;
        if (g <= 1) {
            return c;
        }
        if ((g & (g - 1)) == 0) {
            return c & -g;
        }
        return Math.floorDiv(c, g) * g;
    }

    private boolean classifyDensityPointWarpOnly(CaveCarveScratch scratch, int x, int y, int z, double thresholdLimit) {
        int sx = snapWarp(x);
        int sy = snapWarp(y);
        int sz = snapWarp(z);
        int warpSlot = prepareWarpSample(scratch, sx, sy, sz);
        double warpA = scratch.warpCacheA[warpSlot];
        double warpB = scratch.warpCacheB[warpSlot];
        double warpedX = x + (warpA * warpStrength);
        double warpedY = y + (warpB * warpStrength);
        double warpedZ = z + ((warpA - warpB) * 0.5D * warpStrength);
        double density = baseDensity.noiseFastSigned3D(warpedX, warpedY, warpedZ) * baseWeight;
        if ((density + detailMinContribution) > thresholdLimit) {
            return false;
        }
        if ((density + detailMaxContribution) <= thresholdLimit) {
            return true;
        }

        density += detailDensity.noiseFastSigned3D(warpedX, warpedY, warpedZ) * detailWeight;
        return density <= thresholdLimit;
    }

    private boolean classifyDensityPointWarpModules(
            CaveCarveScratch scratch,
            int x,
            int y,
            int z,
            double thresholdLimit,
            CaveFieldModuleState[] localModules,
            int activeModuleCount,
            double[] remainingMin,
            double[] remainingMax
    ) {
        if (activeModuleCount == 0) {
            return classifyDensityPointWarpOnly(scratch, x, y, z, thresholdLimit);
        }

        int sx = snapWarp(x);
        int sy = snapWarp(y);
        int sz = snapWarp(z);
        int warpSlot = prepareWarpSample(scratch, sx, sy, sz);
        double warpA = scratch.warpCacheA[warpSlot];
        double warpB = scratch.warpCacheB[warpSlot];
        double warpedX = x + (warpA * warpStrength);
        double warpedY = y + (warpB * warpStrength);
        double warpedZ = z + ((warpA - warpB) * 0.5D * warpStrength);
        double density = baseDensity.noiseFastSigned3D(warpedX, warpedY, warpedZ) * baseWeight;
        if ((density + detailMinContribution + remainingMin[0]) > thresholdLimit) {
            return false;
        }
        if ((density + detailMaxContribution + remainingMax[0]) <= thresholdLimit) {
            return true;
        }

        density += detailDensity.noiseFastSigned3D(warpedX, warpedY, warpedZ) * detailWeight;
        if ((density + remainingMin[0]) > thresholdLimit) {
            return false;
        }
        if ((density + remainingMax[0]) <= thresholdLimit) {
            return true;
        }

        for (int moduleIndex = 0; moduleIndex < activeModuleCount; moduleIndex++) {
            density += localModules[moduleIndex].sample(warpedX, warpedY, warpedZ);
            if ((density + remainingMin[moduleIndex + 1]) > thresholdLimit) {
                return false;
            }
            if ((density + remainingMax[moduleIndex + 1]) <= thresholdLimit) {
                return true;
            }
        }

        return density <= thresholdLimit;
    }

    private boolean classifyDensityPointNoWarpModulesFromExactDensity(
            int x,
            int y,
            int z,
            double threshold,
            double density,
            CaveFieldModuleState[] localModules,
            int activeModuleCount,
            double[] remainingMin,
            double[] remainingMax
    ) {
        if (activeModuleCount == 0) {
            return density <= threshold;
        }

        double minRemaining = remainingMin[0] * inverseNormalization;
        double maxRemaining = remainingMax[0] * inverseNormalization;
        if ((density + minRemaining) > threshold) {
            return false;
        }
        if ((density + maxRemaining) <= threshold) {
            return true;
        }

        for (int moduleIndex = 0; moduleIndex < activeModuleCount; moduleIndex++) {
            density += localModules[moduleIndex].sample(x, y, z) * inverseNormalization;
            if ((density + (remainingMin[moduleIndex + 1] * inverseNormalization)) > threshold) {
                return false;
            }
            if ((density + (remainingMax[moduleIndex + 1] * inverseNormalization)) <= threshold) {
                return true;
            }
        }

        return density <= threshold;
    }

    private boolean classifyDensityPointWarpModulesFromExactDensity(
            CaveCarveScratch scratch,
            int x,
            int y,
            int z,
            double threshold,
            double density,
            CaveFieldModuleState[] localModules,
            int activeModuleCount,
            double[] remainingMin,
            double[] remainingMax
    ) {
        if (activeModuleCount == 0) {
            return density <= threshold;
        }

        double minRemaining = remainingMin[0] * inverseNormalization;
        double maxRemaining = remainingMax[0] * inverseNormalization;
        if ((density + minRemaining) > threshold) {
            return false;
        }
        if ((density + maxRemaining) <= threshold) {
            return true;
        }

        int sx = snapWarp(x);
        int sy = snapWarp(y);
        int sz = snapWarp(z);
        int warpSlot = prepareWarpSample(scratch, sx, sy, sz);
        double warpA = scratch.warpCacheA[warpSlot];
        double warpB = scratch.warpCacheB[warpSlot];
        double warpedX = x + (warpA * warpStrength);
        double warpedY = y + (warpB * warpStrength);
        double warpedZ = z + ((warpA - warpB) * 0.5D * warpStrength);
        for (int moduleIndex = 0; moduleIndex < activeModuleCount; moduleIndex++) {
            density += localModules[moduleIndex].sample(warpedX, warpedY, warpedZ) * inverseNormalization;
            if ((density + (remainingMin[moduleIndex + 1] * inverseNormalization)) > threshold) {
                return false;
            }
            if ((density + (remainingMax[moduleIndex + 1] * inverseNormalization)) <= threshold) {
                return true;
            }
        }

        return density <= threshold;
    }

    private boolean isAdaptivePlaneSampleAligned(int localX, int localZ, int adaptiveSampleStep) {
        if ((adaptiveSampleStep & (adaptiveSampleStep - 1)) == 0) {
            int mask = adaptiveSampleStep - 1;
            return (localX & mask) == 0 && (localZ & mask) == 0;
        }
        return localX % adaptiveSampleStep == 0 && localZ % adaptiveSampleStep == 0;
    }

    private void prepareAdaptivePlaneSampleBounds(
            CaveCarveScratch scratch,
            int[] planeColumnIndices,
            int planeCount,
            int adaptiveSampleStep,
            int[] adaptivePlaneSampleBounds,
            int axisCells
    ) {
        prepareAdaptiveGeometry(scratch, adaptiveSampleStep, axisCells, axisCells + 1);
        int[] adaptiveCellX = scratch.adaptiveCellX;
        int[] adaptiveCellZ = scratch.adaptiveCellZ;
        int minSampleX = axisCells;
        int maxSampleX = 0;
        int minSampleZ = axisCells;
        int maxSampleZ = 0;

        for (int planeIndex = 0; planeIndex < planeCount; planeIndex++) {
            int columnIndex = planeColumnIndices[planeIndex];
            int sampleX = adaptiveCellX[columnIndex];
            int sampleZ = adaptiveCellZ[columnIndex];
            if (sampleX < minSampleX) {
                minSampleX = sampleX;
            }
            if (sampleX + 1 > maxSampleX) {
                maxSampleX = sampleX + 1;
            }
            if (sampleZ < minSampleZ) {
                minSampleZ = sampleZ;
            }
            if (sampleZ + 1 > maxSampleZ) {
                maxSampleZ = sampleZ + 1;
            }
        }

        adaptivePlaneSampleBounds[0] = minSampleX;
        adaptivePlaneSampleBounds[1] = maxSampleX;
        adaptivePlaneSampleBounds[2] = minSampleZ;
        adaptivePlaneSampleBounds[3] = maxSampleZ;
    }

    private void prepareAdaptivePlaneColumns(
            CaveCarveScratch scratch,
            int[] planeColumnIndices,
            int planeCount,
            int adaptiveSampleStep,
            double adaptiveThresholdMargin,
            double[] adaptivePlaneDensity,
            int axisCells,
            int axisSamples,
            double[] adaptivePlanePrediction,
            double[] adaptivePlaneAmbiguity
    ) {
        prepareAdaptiveGeometry(scratch, adaptiveSampleStep, axisCells, axisSamples);
        int[] adaptiveCellZ = scratch.adaptiveCellZ;
        int[] adaptiveRow0 = scratch.adaptiveRow0;
        int[] adaptiveRow1 = scratch.adaptiveRow1;
        double[] adaptiveTx = scratch.adaptiveTx;
        double[] adaptiveTz = scratch.adaptiveTz;
        for (int planeIndex = 0; planeIndex < planeCount; planeIndex++) {
            int columnIndex = planeColumnIndices[planeIndex];
            int cellZ = adaptiveCellZ[columnIndex];
            int row0 = adaptiveRow0[columnIndex];
            int row1 = adaptiveRow1[columnIndex];
            double tx = adaptiveTx[columnIndex];
            double tz = adaptiveTz[columnIndex];
            double d00 = adaptivePlaneDensity[row0 + cellZ];
            double d01 = adaptivePlaneDensity[row0 + cellZ + 1];
            double d10 = adaptivePlaneDensity[row1 + cellZ];
            double d11 = adaptivePlaneDensity[row1 + cellZ + 1];
            double dx0 = d00 + ((d10 - d00) * tx);
            double dx1 = d01 + ((d11 - d01) * tx);
            adaptivePlanePrediction[planeIndex] = dx0 + ((dx1 - dx0) * tz);
            double minDensity = Math.min(Math.min(d00, d01), Math.min(d10, d11));
            double maxDensity = Math.max(Math.max(d00, d01), Math.max(d10, d11));
            adaptivePlaneAmbiguity[planeIndex] = adaptiveThresholdMargin + ((maxDensity - minDensity) * ADAPTIVE_LOCAL_RANGE_SCALE);
        }
    }

    private void prepareAdaptiveGeometry(CaveCarveScratch scratch, int adaptiveSampleStep, int axisCells, int axisSamples) {
        if (scratch.adaptiveGeometryStep == adaptiveSampleStep && scratch.adaptiveGeometryAxisCells == axisCells) {
            return;
        }

        for (int columnIndex = 0; columnIndex < 256; columnIndex++) {
            int localX = columnIndex >> 4;
            int localZ = columnIndex & 15;
            int cellX = Math.min(localX / adaptiveSampleStep, axisCells - 1);
            int cellZ = Math.min(localZ / adaptiveSampleStep, axisCells - 1);
            int x0 = cellX * adaptiveSampleStep;
            int z0 = cellZ * adaptiveSampleStep;
            int x1 = Math.min(x0 + adaptiveSampleStep, 16);
            int z1 = Math.min(z0 + adaptiveSampleStep, 16);
            scratch.adaptiveCellX[columnIndex] = cellX;
            scratch.adaptiveCellZ[columnIndex] = cellZ;
            scratch.adaptiveRow0[columnIndex] = cellX * axisSamples;
            scratch.adaptiveRow1[columnIndex] = (cellX + 1) * axisSamples;
            scratch.adaptiveTx[columnIndex] = x1 == x0 ? 0D : (localX - x0) / (double) (x1 - x0);
            scratch.adaptiveTz[columnIndex] = z1 == z0 ? 0D : (localZ - z0) / (double) (z1 - z0);
        }

        scratch.adaptiveGeometryStep = adaptiveSampleStep;
        scratch.adaptiveGeometryAxisCells = axisCells;
    }

    private double sampleDensityNoWarpNoModules(int x, int y, int z) {
        double density = baseDensity.noiseFastSigned3D(x, y, z) * baseWeight;
        density += detailDensity.noiseFastSigned3D(x, y, z) * detailWeight;
        return density * inverseNormalization;
    }

    private double sampleDensityNoWarpModules(CaveCarveScratch scratch, int x, int y, int z) {
        int activeModuleCount = prepareActiveModules(scratch, y);
        if (activeModuleCount == 0) {
            return sampleDensityNoWarpNoModules(x, y, z);
        }

        CaveFieldModuleState[] localModules = scratch.activeModules;
        double density = baseDensity.noiseFastSigned3D(x, y, z) * baseWeight;
        density += detailDensity.noiseFastSigned3D(x, y, z) * detailWeight;
        for (int moduleIndex = 0; moduleIndex < activeModuleCount; moduleIndex++) {
            CaveFieldModuleState module = localModules[moduleIndex];
            double moduleDensity = module.density.noiseFastSigned3D(x, y, z) - module.threshold;
            if (module.invert) {
                moduleDensity = -moduleDensity;
            }

            density += moduleDensity * module.weight;
        }

        return density * inverseNormalization;
    }

    private double sampleDensityWarpOnly(CaveCarveScratch scratch, int x, int y, int z) {
        int sx = snapWarp(x);
        int sy = snapWarp(y);
        int sz = snapWarp(z);
        int warpSlot = prepareWarpSample(scratch, sx, sy, sz);
        double warpA = scratch.warpCacheA[warpSlot];
        double warpB = scratch.warpCacheB[warpSlot];
        double warpedX = x + (warpA * warpStrength);
        double warpedY = y + (warpB * warpStrength);
        double warpedZ = z + ((warpA - warpB) * 0.5D * warpStrength);
        double density = baseDensity.noiseFastSigned3D(warpedX, warpedY, warpedZ) * baseWeight;
        density += detailDensity.noiseFastSigned3D(warpedX, warpedY, warpedZ) * detailWeight;
        return density * inverseNormalization;
    }

    private double sampleDensityWarpModules(CaveCarveScratch scratch, int x, int y, int z) {
        int activeModuleCount = prepareActiveModules(scratch, y);
        if (activeModuleCount == 0) {
            return sampleDensityWarpOnly(scratch, x, y, z);
        }

        CaveFieldModuleState[] localModules = scratch.activeModules;
        return sampleDensityWarpModules(scratch, x, y, z, localModules, activeModuleCount);
    }

    private double sampleDensityWarpModules(CaveCarveScratch scratch, int x, int y, int z, CaveFieldModuleState[] localModules, int activeModuleCount) {
        int sx = snapWarp(x);
        int sy = snapWarp(y);
        int sz = snapWarp(z);
        int warpSlot = prepareWarpSample(scratch, sx, sy, sz);
        double warpA = scratch.warpCacheA[warpSlot];
        double warpB = scratch.warpCacheB[warpSlot];
        double warpedX = x + (warpA * warpStrength);
        double warpedY = y + (warpB * warpStrength);
        double warpedZ = z + ((warpA - warpB) * 0.5D * warpStrength);
        double density = baseDensity.noiseFastSigned3D(warpedX, warpedY, warpedZ) * baseWeight;
        density += detailDensity.noiseFastSigned3D(warpedX, warpedY, warpedZ) * detailWeight;
        for (int moduleIndex = 0; moduleIndex < activeModuleCount; moduleIndex++) {
            CaveFieldModuleState module = localModules[moduleIndex];
            double moduleDensity = module.density.noiseFastSigned3D(warpedX, warpedY, warpedZ) - module.threshold;
            if (module.invert) {
                moduleDensity = -moduleDensity;
            }

            density += moduleDensity * module.weight;
        }

        return density * inverseNormalization;
    }

    private int prepareWarpSample(CaveCarveScratch scratch, int sx, int sy, int sz) {
        int slot = mixWarpKey(sx, sy, sz) & (scratch.warpCacheX.length - 1);
        if (scratch.warpCacheSet[slot]
                && scratch.warpCacheX[slot] == sx
                && scratch.warpCacheY[slot] == sy
                && scratch.warpCacheZ[slot] == sz) {
            return slot;
        }

        scratch.warpCacheSet[slot] = true;
        scratch.warpCacheX[slot] = sx;
        scratch.warpCacheY[slot] = sy;
        scratch.warpCacheZ[slot] = sz;
        scratch.warpCacheA[slot] = warpDensity.noiseFastSigned3D(sx, sy, sz);
        scratch.warpCacheB[slot] = warpDensity.noiseFastSigned3D(sx + 31.37D, sy - 17.21D, sz + 23.91D);
        return slot;
    }

    private static int mixWarpKey(int sx, int sy, int sz) {
        int hash = sx * 73428767;
        hash ^= sy * 91227153;
        hash ^= sz * 43828939;
        hash ^= hash >>> 16;
        return hash;
    }

    private int prepareActiveModules(CaveCarveScratch scratch, int y) {
        CaveFieldModuleState[] configuredModules = modules;
        int configuredCount = configuredModules.length;
        if (configuredCount == 0) {
            return 0;
        }

        // Pure function of (y, modules). modules is final and the scratch is per carver per thread,
        // so the band selection and the remaining-contribution prefix sums only change when y does.
        if (scratch.activeModulesY == y) {
            return scratch.activeModuleCount;
        }

        if (scratch.activeModules.length < configuredCount) {
            scratch.activeModules = new CaveFieldModuleState[configuredCount];
        }

        int activeCount = 0;
        for (int moduleIndex = 0; moduleIndex < configuredCount; moduleIndex++) {
            CaveFieldModuleState module = configuredModules[moduleIndex];
            if (y < module.minY || y > module.maxY) {
                continue;
            }

            scratch.activeModules[activeCount] = module;
            activeCount++;
        }

        if (scratch.activeModuleRemainingMin.length < activeCount + 1) {
            scratch.activeModuleRemainingMin = new double[activeCount + 1];
            scratch.activeModuleRemainingMax = new double[activeCount + 1];
        }

        scratch.activeModuleRemainingMin[activeCount] = 0D;
        scratch.activeModuleRemainingMax[activeCount] = 0D;
        for (int moduleIndex = activeCount - 1; moduleIndex >= 0; moduleIndex--) {
            CaveFieldModuleState module = scratch.activeModules[moduleIndex];
            scratch.activeModuleRemainingMin[moduleIndex] = scratch.activeModuleRemainingMin[moduleIndex + 1] + module.minContribution;
            scratch.activeModuleRemainingMax[moduleIndex] = scratch.activeModuleRemainingMax[moduleIndex + 1] + module.maxContribution;
        }

        scratch.activeModulesY = y;
        scratch.activeModuleCount = activeCount;
        return activeCount;
    }

    private MatterSlice<MatterCavern> resolveCavernSlice(CaveCarveScratch scratch, MantleChunk<Matter> chunk, int sectionIndex) {
        @SuppressWarnings("unchecked")
        MatterSlice<MatterCavern> cachedSlice = (MatterSlice<MatterCavern>) scratch.sectionSlices[sectionIndex];
        if (cachedSlice != null) {
            return cachedSlice;
        }

        Matter sectionMatter = scratch.sectionMatter[sectionIndex];
        if (sectionMatter == null) {
            sectionMatter = chunk.getOrCreate(sectionIndex);
            scratch.sectionMatter[sectionIndex] = sectionMatter;
        }

        MatterSlice<MatterCavern> resolvedSlice = sectionMatter.slice(MatterCavern.class);
        scratch.sectionSlices[sectionIndex] = resolvedSlice;
        return resolvedSlice;
    }

    private MatterCavern[] prepareMatterByYTable(CaveCarveScratch scratch, int minY, int maxY) {
        int size = Math.max(0, maxY - minY + 1);
        if (scratch.matterByY.length < size) {
            scratch.matterByY = new MatterCavern[size];
        }

        MatterCavern[] matterByY = scratch.matterByY;
        boolean allowLava = profile.isAllowLava();
        int lavaHeight = engine.getDimension().getCaveLavaHeight();

        for (int y = minY; y <= maxY; y++) {
            int offset = y - minY;
            if (allowLava && y <= lavaHeight) {
                matterByY[offset] = carveLava;
                continue;
            }
            if (!allowLava && y <= lavaHeight) {
                matterByY[offset] = carveForcedAir;
                continue;
            }

            matterByY[offset] = carveAir;
        }

        return matterByY;
    }

    private MatterCavern resolveMatter(CaveCarveScratch scratch, MatterCavern verticalMatter, int x, int y, int z,
                                       int columnIndex, int[] fluidMaxY, double localThreshold) {
        if (verticalMatter != carveLava
                && y <= fluidMaxY[columnIndex]
                && isAquiferCandidate(scratch, x, y, z, localThreshold)) {
            return carveFluid;
        }
        return verticalMatter;
    }

    private boolean isAquiferCandidate(CaveCarveScratch scratch, int x, int y, int z, double localThreshold) {
        double depthFactor = Math.max(0D, Math.min(1.5D, (fluidHeight - y) / 48D));
        double cutoff = 0.35D + (depthFactor * 0.2D);
        if (detailDensity.noiseFastSigned3D(x, y * 0.5D, z) <= cutoff) {
            return false;
        }
        return !fluidRequiresFloor || hasAquiferCupSupport(scratch, x, y, z, localThreshold);
    }

    private boolean isAquiferCandidate(int x, int y, int z, double localThreshold) {
        return isAquiferCandidate(scratchCache.get(), x, y, z, localThreshold);
    }

    private boolean hasAquiferCupSupport(CaveCarveScratch scratch, int x, int y, int z, double threshold) {
        int floorY = Math.max(0, y - 1);
        int deepFloorY = Math.max(0, y - 2);
        int aboveY = Math.min(aquiferCeilingY, y + 1);
        if (!isDensitySolid(scratch, x, floorY, z, threshold)) {
            return false;
        }
        if (!isDensitySolid(scratch, x, deepFloorY, z, threshold - 0.05D)) {
            return false;
        }

        int support = 0;
        int misses = 0;
        if (isDensitySolid(scratch, x + 1, y, z, threshold)) {
            support++;
        } else {
            misses++;
        }
        if (isDensitySolid(scratch, x - 1, y, z, threshold)) {
            support++;
        } else if (++misses >= 2) {
            return false;
        }
        if (isDensitySolid(scratch, x, y, z + 1, threshold)) {
            support++;
        } else if (++misses >= 2) {
            return false;
        }
        if (isDensitySolid(scratch, x, y, z - 1, threshold)) {
            support++;
            if (support >= 4) {
                return true;
            }
        } else if (++misses >= 2) {
            return false;
        }
        if (isDensitySolid(scratch, x, aboveY, z, threshold)) {
            support++;
        }
        return support >= 4;
    }

    private boolean isDensitySolid(CaveCarveScratch scratch, int x, int y, int z, double threshold) {
        return sampleDensityOptimized(scratch, x, y, z) > threshold;
    }

    private void writeCavern(MatterSlice<MatterCavern> cavernSlice, int localX, int y, int localZ,
                             MatterCavern matter, CaveFluidSupportPlan fluidSupportPlan) {
        cavernSlice.set(localX, y & 15, localZ, matter);
        if (fluidSupportPlan != null && matter == carveFluid) {
            fluidSupportPlan.add(localX, y, localZ, carveFluid, carveAir);
        }
    }

    private void prepareSectionCaches(CaveCarveScratch scratch, int minY, int maxY) {
        int minSection = Math.max(0, PowerOfTwoCoordinates.floorDivPow2(minY, 4));
        int maxSection = Math.max(minSection, PowerOfTwoCoordinates.floorDivPow2(maxY, 4));
        int requiredSections = maxSection + 1;
        if (scratch.sectionMatter.length < requiredSections) {
            scratch.sectionMatter = new Matter[requiredSections];
            scratch.sectionSlices = new MatterSlice<?>[requiredSections];
            return;
        }

        for (int section = minSection; section <= maxSection; section++) {
            scratch.sectionMatter[section] = null;
            scratch.sectionSlices[section] = null;
        }
    }

    private double clampColumnWeight(double weight) {
        if (Double.isNaN(weight) || Double.isInfinite(weight)) {
            return 0D;
        }

        if (weight <= 0D) {
            return 0D;
        }

        if (weight >= 1D) {
            return 1D;
        }

        return weight;
    }

    private double applySurfaceCeilingFade(
            CaveCarveScratch scratch,
            double threshold,
            int columnIndex,
            int y,
            int minY
    ) {
        if (!scratch.surfaceCeilingColumn[columnIndex]) {
            return threshold;
        }

        int ceilingDistance = scratch.columnMaxY[columnIndex] - y;
        if (ceilingDistance < 0 || ceilingDistance >= SURFACE_CEILING_FADE_DEPTH) {
            return threshold;
        }

        double closureThreshold = scratch.surfaceClosureThreshold[y - minY];
        if (threshold <= closureThreshold) {
            return threshold;
        }

        double progress = ceilingDistance / (double) SURFACE_CEILING_FADE_DEPTH;
        double smooth = progress * progress * (3D - (2D * progress));
        return closureThreshold + ((threshold - closureThreshold) * smooth);
    }

    private void prepareSurfaceClosureThresholdTable(CaveCarveScratch scratch, int minY, int maxY) {
        int size = Math.max(0, maxY - minY + 1);
        if (scratch.surfaceClosureThreshold.length < size) {
            scratch.surfaceClosureThreshold = new double[size];
        }

        double baseMinimum = -Math.abs(baseWeight) - Math.abs(detailWeight);
        for (int y = minY; y <= maxY; y++) {
            double minimumDensity = baseMinimum;
            for (CaveFieldModuleState module : modules) {
                if (y < module.minY || y > module.maxY) {
                    continue;
                }
                minimumDensity += Math.min(module.minContribution, module.maxContribution);
            }
            scratch.surfaceClosureThreshold[y - minY] =
                    (minimumDensity * inverseNormalization) - SURFACE_CEILING_SOLID_EPSILON;
        }
    }

    private double[] prepareVerticalEdgeFadeTable(CaveCarveScratch scratch, int minY, int maxY) {
        int size = Math.max(0, maxY - minY + 1);
        if (scratch.verticalEdgeFade.length < size) {
            scratch.verticalEdgeFade = new double[size];
        }

        double[] verticalEdgeFade = scratch.verticalEdgeFade;
        int fadeRange = Math.max(0, profile.getVerticalEdgeFade());
        double fadeStrength = Math.max(0D, profile.getVerticalEdgeFadeStrength());
        if (size <= 0 || fadeRange <= 0 || maxY <= minY || fadeStrength <= 0D) {
            Arrays.fill(verticalEdgeFade, 0, size, 0D);
            return verticalEdgeFade;
        }

        for (int y = minY; y <= maxY; y++) {
            int floorDistance = y - minY;
            int ceilingDistance = maxY - y;
            int edgeDistance = Math.min(floorDistance, ceilingDistance);
            int offsetIndex = y - minY;
            if (edgeDistance >= fadeRange) {
                verticalEdgeFade[offsetIndex] = 0D;
                continue;
            }

            double t = Math.max(0D, Math.min(1D, edgeDistance / (double) fadeRange));
            double smooth = t * t * (3D - (2D * t));
            verticalEdgeFade[offsetIndex] = (1D - smooth) * fadeStrength;
        }

        return verticalEdgeFade;
    }
}
