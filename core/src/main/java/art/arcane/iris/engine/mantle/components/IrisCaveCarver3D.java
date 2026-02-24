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

package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.mantle.MantleWriter;
import art.arcane.iris.engine.object.IrisCaveFieldModule;
import art.arcane.iris.engine.object.IrisCaveProfile;
import art.arcane.iris.engine.object.IrisRange;
import art.arcane.iris.util.project.noise.CNG;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.volmlib.util.matter.MatterSlice;
import art.arcane.volmlib.util.scheduling.PrecisionStopwatch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class IrisCaveCarver3D {
    private static final byte LIQUID_AIR = 0;
    private static final byte LIQUID_LAVA = 2;
    private static final byte LIQUID_FORCED_AIR = 3;
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    private final Engine engine;
    private final IrisData data;
    private final IrisCaveProfile profile;
    private final CNG baseDensity;
    private final CNG detailDensity;
    private final CNG warpDensity;
    private final CNG surfaceBreakDensity;
    private final RNG thresholdRng;
    private final ModuleState[] modules;
    private final double inverseNormalization;
    private final MatterCavern carveAir;
    private final MatterCavern carveLava;
    private final MatterCavern carveForcedAir;
    private final double baseWeight;
    private final double detailWeight;
    private final double warpStrength;
    private final boolean hasWarp;
    private final boolean hasModules;

    public IrisCaveCarver3D(Engine engine, IrisCaveProfile profile) {
        this.engine = engine;
        this.data = engine.getData();
        this.profile = profile;
        this.carveAir = new MatterCavern(true, "", LIQUID_AIR);
        this.carveLava = new MatterCavern(true, "", LIQUID_LAVA);
        this.carveForcedAir = new MatterCavern(true, "", LIQUID_FORCED_AIR);
        List<ModuleState> moduleStates = new ArrayList<>();

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

        double weight = Math.abs(baseWeight) + Math.abs(detailWeight);
        int index = 0;
        for (IrisCaveFieldModule module : profile.getModules()) {
            CNG moduleDensity = module.getStyle().create(baseRng.nextParallelRNG(1_000_003L + (index * 65_537L)), data);
            ModuleState state = new ModuleState(module, moduleDensity);
            moduleStates.add(state);
            weight += Math.abs(state.weight);
            index++;
        }

        this.modules = moduleStates.toArray(new ModuleState[0]);
        double normalization = weight <= 0 ? 1 : weight;
        inverseNormalization = 1D / normalization;
        hasModules = modules.length > 0;
    }

    public int carve(MantleWriter writer, int chunkX, int chunkZ) {
        Scratch scratch = SCRATCH.get();
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
        PrecisionStopwatch applyStopwatch = PrecisionStopwatch.start();
        try {
            Scratch scratch = SCRATCH.get();
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
            int minY = Math.max(0, (int) Math.floor(profile.getVerticalRange().getMin()));
            int maxY = Math.min(worldHeight - 1, (int) Math.ceil(profile.getVerticalRange().getMax()));
            if (worldYRange != null) {
                int worldMinHeight = engine.getWorld().minHeight();
                int rangeMinY = (int) Math.floor(worldYRange.getMin() - worldMinHeight);
                int rangeMaxY = (int) Math.ceil(worldYRange.getMax() - worldMinHeight);
                minY = Math.max(minY, rangeMinY);
                maxY = Math.min(maxY, rangeMaxY);
            }
            int sampleStep = Math.max(1, profile.getSampleStep());
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

            int x0 = chunkX << 4;
            int z0 = chunkZ << 4;
            int[] columnMaxY = scratch.columnMaxY;
            int[] surfaceBreakFloorY = scratch.surfaceBreakFloorY;
            boolean[] surfaceBreakColumn = scratch.surfaceBreakColumn;
            double[] columnThreshold = scratch.columnThreshold;
            double[] clampedWeights = scratch.clampedColumnWeights;
            double[] verticalEdgeFade = prepareVerticalEdgeFadeTable(scratch, minY, maxY);
            MatterCavern[] matterByY = prepareMatterByYTable(scratch, minY, maxY);
            prepareSectionCaches(scratch, minY, maxY);

            for (int lx = 0; lx < 16; lx++) {
                int x = x0 + lx;
                for (int lz = 0; lz < 16; lz++) {
                    int z = z0 + lz;
                    int index = (lx << 4) | lz;
                    int columnSurfaceY;
                    if (precomputedSurfaceHeights != null && precomputedSurfaceHeights.length > index) {
                        columnSurfaceY = precomputedSurfaceHeights[index];
                    } else {
                        columnSurfaceY = engine.getHeight(x, z);
                    }
                    int clearanceTopY = Math.min(maxY, Math.max(minY, columnSurfaceY - surfaceClearance));
                    boolean breakColumn = allowSurfaceBreak
                            && signed(surfaceBreakDensity.noiseFast2D(x, z)) >= surfaceBreakNoiseThreshold;
                    int columnTopY = breakColumn
                            ? Math.min(maxY, Math.max(minY, columnSurfaceY))
                            : clearanceTopY;

                    columnMaxY[index] = columnTopY;
                    surfaceBreakFloorY[index] = Math.max(minY, columnSurfaceY - surfaceBreakDepth);
                    surfaceBreakColumn[index] = breakColumn;
                    columnThreshold[index] = profile.getDensityThreshold().get(thresholdRng, x, z, data) - profile.getThresholdBias();
                    clampedWeights[index] = clampColumnWeight(columnWeights[index]);
                }
            }

            int latticeStep = Math.max(2, sampleStep);
            int carved = carvePassLattice(
                    chunk,
                    x0,
                    z0,
                    minY,
                    maxY,
                    latticeStep,
                    surfaceBreakThresholdBoost,
                    columnMaxY,
                    surfaceBreakFloorY,
                    surfaceBreakColumn,
                    columnThreshold,
                    clampedWeights,
                    verticalEdgeFade,
                    matterByY,
                    resolvedMinWeight,
                    resolvedThresholdPenalty,
                    0D,
                    false
            );

            int minCarveCells = Math.max(0, profile.getMinCarveCells());
            double recoveryThresholdBoost = Math.max(0, profile.getRecoveryThresholdBoost());
            if (carved < minCarveCells && recoveryThresholdBoost > 0D) {
                carved += carvePassLattice(
                        chunk,
                        x0,
                        z0,
                        minY,
                        maxY,
                        latticeStep,
                        surfaceBreakThresholdBoost,
                        columnMaxY,
                        surfaceBreakFloorY,
                        surfaceBreakColumn,
                        columnThreshold,
                        clampedWeights,
                        verticalEdgeFade,
                        matterByY,
                        resolvedMinWeight,
                        resolvedThresholdPenalty,
                        recoveryThresholdBoost,
                        true
                );
            }

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
                        surfaceBreakFloorY,
                        surfaceBreakColumn,
                        columnThreshold,
                        clampedWeights,
                        verticalEdgeFade,
                        matterByY,
                        resolvedMinWeight,
                        resolvedThresholdPenalty,
                        0D,
                        false
                );
                if (carved < minCarveCells && recoveryThresholdBoost > 0D) {
                    carved += carvePassFallback(
                            chunk,
                            x0,
                            z0,
                            minY,
                            maxY,
                            sampleStep,
                            surfaceBreakThresholdBoost,
                            columnMaxY,
                            surfaceBreakFloorY,
                            surfaceBreakColumn,
                            columnThreshold,
                            clampedWeights,
                            verticalEdgeFade,
                            matterByY,
                            resolvedMinWeight,
                            resolvedThresholdPenalty,
                            recoveryThresholdBoost,
                            true
                    );
                }
            }

            return carved;
        } finally {
            engine.getMetrics().getCarveApply().put(applyStopwatch.getMilliseconds());
        }
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
            int[] surfaceBreakFloorY,
            boolean[] surfaceBreakColumn,
            double[] columnThreshold,
            double[] clampedWeights,
            double[] verticalEdgeFade,
            MatterCavern[] matterByY,
            double minWeight,
            double thresholdPenalty,
            double thresholdBoost,
            boolean skipExistingCarved
    ) {
        int carved = 0;
        Scratch scratch = SCRATCH.get();
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

        for (int lx = 0; lx < 16; lx += 2) {
            int x = x0 + lx;
            int lx1 = lx + 1;
            for (int lz = 0; lz < 16; lz += 2) {
                int z = z0 + lz;
                int lz1 = lz + 1;
                int activeColumns = 0;

                int index00 = (lx << 4) | lz;
                if (!Double.isNaN(passThreshold[index00])) {
                    tileIndices[activeColumns] = index00;
                    tileLocalX[activeColumns] = lx;
                    tileLocalZ[activeColumns] = lz;
                    tileTopY[activeColumns] = columnMaxY[index00];
                    activeColumns++;
                }

                int index01 = (lx << 4) | lz1;
                if (!Double.isNaN(passThreshold[index01])) {
                    tileIndices[activeColumns] = index01;
                    tileLocalX[activeColumns] = lx;
                    tileLocalZ[activeColumns] = lz1;
                    tileTopY[activeColumns] = columnMaxY[index01];
                    activeColumns++;
                }

                int index10 = (lx1 << 4) | lz;
                if (!Double.isNaN(passThreshold[index10])) {
                    tileIndices[activeColumns] = index10;
                    tileLocalX[activeColumns] = lx1;
                    tileLocalZ[activeColumns] = lz;
                    tileTopY[activeColumns] = columnMaxY[index10];
                    activeColumns++;
                }

                int index11 = (lx1 << 4) | lz1;
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
                    double density = sampleDensityOptimized(x, y, z);
                    int stampMaxY = Math.min(maxY, y + 1);
                    for (int yy = y; yy <= stampMaxY; yy++) {
                        MatterCavern matter = matterByY[yy - minY];
                        MatterSlice<MatterCavern> cavernSlice = resolveCavernSlice(scratch, chunk, yy >> 4);
                        int localY = yy & 15;
                        int fadeIndex = yy - minY;
                        for (int columnIndex = 0; columnIndex < activeColumns; columnIndex++) {
                            if (yy > tileTopY[columnIndex]) {
                                continue;
                            }

                            int index = tileIndices[columnIndex];
                            double localThreshold = passThreshold[index];
                            if (surfaceBreakColumn[index] && yy >= surfaceBreakFloorY[index]) {
                                localThreshold += surfaceBreakThresholdBoost;
                            }
                            localThreshold -= verticalEdgeFade[fadeIndex];
                            if (density > localThreshold) {
                                continue;
                            }

                            int localX = tileLocalX[columnIndex];
                            int localZ = tileLocalZ[columnIndex];
                            if (skipExistingCarved) {
                                if (cavernSlice.get(localX, localY, localZ) == null) {
                                    cavernSlice.set(localX, localY, localZ, matter);
                                    carved++;
                                }
                                continue;
                            }

                            cavernSlice.set(localX, localY, localZ, matter);
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
            int[] surfaceBreakFloorY,
            boolean[] surfaceBreakColumn,
            double[] columnThreshold,
            double[] clampedWeights,
            double[] verticalEdgeFade,
            MatterCavern[] matterByY,
            double minWeight,
            double thresholdPenalty,
            double thresholdBoost,
            boolean skipExistingCarved
    ) {
        int carved = 0;
        Scratch scratch = SCRATCH.get();

        for (int lx = 0; lx < 16; lx++) {
            int x = x0 + lx;
            for (int lz = 0; lz < 16; lz++) {
                int z = z0 + lz;
                int index = (lx << 4) | lz;
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
                    double localThreshold = threshold;
                    if (breakColumn && y >= breakFloorY) {
                        localThreshold += surfaceBreakThresholdBoost;
                    }

                    localThreshold -= verticalEdgeFade[y - minY];
                    if (sampleDensityOptimized(x, y, z) > localThreshold) {
                        continue;
                    }

                    int carveMaxY = Math.min(columnTopY, y + sampleStep - 1);
                    for (int yy = y; yy <= carveMaxY; yy++) {
                        MatterCavern matter = matterByY[yy - minY];
                        MatterSlice<MatterCavern> cavernSlice = resolveCavernSlice(scratch, chunk, yy >> 4);
                        int localY = yy & 15;
                        if (skipExistingCarved) {
                            if (cavernSlice.get(lx, localY, lz) == null) {
                                cavernSlice.set(lx, localY, lz, matter);
                                carved++;
                            }
                            continue;
                        }

                        cavernSlice.set(lx, localY, lz, matter);
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
        if (!hasWarp) {
            if (!hasModules) {
                return sampleDensityNoWarpNoModules(x, y, z);
            }

            return sampleDensityNoWarpModules(x, y, z);
        }

        if (!hasModules) {
            return sampleDensityWarpOnly(x, y, z);
        }

        return sampleDensityWarpModules(x, y, z);
    }

    private double sampleDensityNoWarpNoModules(int x, int y, int z) {
        double density = signed(baseDensity.noiseFast3D(x, y, z)) * baseWeight;
        density += signed(detailDensity.noiseFast3D(x, y, z)) * detailWeight;
        return density * inverseNormalization;
    }

    private double sampleDensityNoWarpModules(int x, int y, int z) {
        double density = signed(baseDensity.noiseFast3D(x, y, z)) * baseWeight;
        density += signed(detailDensity.noiseFast3D(x, y, z)) * detailWeight;
        for (int moduleIndex = 0; moduleIndex < modules.length; moduleIndex++) {
            ModuleState module = modules[moduleIndex];
            if (y < module.minY || y > module.maxY) {
                continue;
            }

            double moduleDensity = signed(module.density.noiseFast3D(x, y, z)) - module.threshold;
            if (module.invert) {
                moduleDensity = -moduleDensity;
            }

            density += moduleDensity * module.weight;
        }

        return density * inverseNormalization;
    }

    private double sampleDensityWarpOnly(int x, int y, int z) {
        double warpA = signed(warpDensity.noiseFast3D(x, y, z));
        double warpB = signed(warpDensity.noiseFast3D(x + 31.37D, y - 17.21D, z + 23.91D));
        double warpedX = x + (warpA * warpStrength);
        double warpedY = y + (warpB * warpStrength);
        double warpedZ = z + ((warpA - warpB) * 0.5D * warpStrength);
        double density = signed(baseDensity.noiseFast3D(warpedX, warpedY, warpedZ)) * baseWeight;
        density += signed(detailDensity.noiseFast3D(warpedX, warpedY, warpedZ)) * detailWeight;
        return density * inverseNormalization;
    }

    private double sampleDensityWarpModules(int x, int y, int z) {
        double warpA = signed(warpDensity.noiseFast3D(x, y, z));
        double warpB = signed(warpDensity.noiseFast3D(x + 31.37D, y - 17.21D, z + 23.91D));
        double warpedX = x + (warpA * warpStrength);
        double warpedY = y + (warpB * warpStrength);
        double warpedZ = z + ((warpA - warpB) * 0.5D * warpStrength);
        double density = signed(baseDensity.noiseFast3D(warpedX, warpedY, warpedZ)) * baseWeight;
        density += signed(detailDensity.noiseFast3D(warpedX, warpedY, warpedZ)) * detailWeight;
        for (int moduleIndex = 0; moduleIndex < modules.length; moduleIndex++) {
            ModuleState module = modules[moduleIndex];
            if (y < module.minY || y > module.maxY) {
                continue;
            }

            double moduleDensity = signed(module.density.noiseFast3D(warpedX, warpedY, warpedZ)) - module.threshold;
            if (module.invert) {
                moduleDensity = -moduleDensity;
            }

            density += moduleDensity * module.weight;
        }

        return density * inverseNormalization;
    }

    private MatterSlice<MatterCavern> resolveCavernSlice(Scratch scratch, MantleChunk<Matter> chunk, int sectionIndex) {
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

    private MatterCavern[] prepareMatterByYTable(Scratch scratch, int minY, int maxY) {
        int size = Math.max(0, maxY - minY + 1);
        if (scratch.matterByY.length < size) {
            scratch.matterByY = new MatterCavern[size];
        }

        MatterCavern[] matterByY = scratch.matterByY;
        boolean allowLava = profile.isAllowLava();
        boolean allowWater = profile.isAllowWater();
        int lavaHeight = engine.getDimension().getCaveLavaHeight();
        int fluidHeight = engine.getDimension().getFluidHeight();

        for (int y = minY; y <= maxY; y++) {
            int offset = y - minY;
            if (allowLava && y <= lavaHeight) {
                matterByY[offset] = carveLava;
                continue;
            }
            if (allowWater && y <= fluidHeight) {
                matterByY[offset] = carveAir;
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

    private void prepareSectionCaches(Scratch scratch, int minY, int maxY) {
        int minSection = Math.max(0, minY >> 4);
        int maxSection = Math.max(minSection, maxY >> 4);
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

    private double signed(double value) {
        return (value * 2D) - 1D;
    }

    private double[] prepareVerticalEdgeFadeTable(Scratch scratch, int minY, int maxY) {
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

    private static final class ModuleState {
        private final CNG density;
        private final int minY;
        private final int maxY;
        private final double weight;
        private final double threshold;
        private final boolean invert;

        private ModuleState(IrisCaveFieldModule module, CNG density) {
            IrisRange range = module.getVerticalRange();
            this.density = density;
            this.minY = (int) Math.floor(range.getMin());
            this.maxY = (int) Math.ceil(range.getMax());
            this.weight = module.getWeight();
            this.threshold = module.getThreshold();
            this.invert = module.isInvert();
        }
    }

    private static final class Scratch {
        private final int[] columnMaxY = new int[256];
        private final int[] surfaceBreakFloorY = new int[256];
        private final boolean[] surfaceBreakColumn = new boolean[256];
        private final double[] columnThreshold = new double[256];
        private final double[] passThreshold = new double[256];
        private final double[] fullWeights = new double[256];
        private final double[] clampedColumnWeights = new double[256];
        private final int[] tileIndices = new int[4];
        private final int[] tileLocalX = new int[4];
        private final int[] tileLocalZ = new int[4];
        private final int[] tileTopY = new int[4];
        private double[] verticalEdgeFade = new double[0];
        private MatterCavern[] matterByY = new MatterCavern[0];
        private Matter[] sectionMatter = new Matter[0];
        private MatterSlice<?>[] sectionSlices = new MatterSlice<?>[0];
        private boolean fullWeightsInitialized;
    }
}
