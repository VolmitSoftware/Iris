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

import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.UpperDimensionContext;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.mantle.MantleWriter;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.terrain.carving.IrisCaveProfile;
import art.arcane.iris.engine.object.terrain.carving.IrisDimensionCarvingEntry;
import art.arcane.iris.engine.object.terrain.carving.IrisDimensionCarvingResolver;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import art.arcane.iris.engine.object.value.IrisRange;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.data.B;
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.iris.util.project.stream.ProceduralStream;
import art.arcane.iris.util.project.stream.utility.ChunkFillableDoubleStream2D;
import art.arcane.iris.util.simd.SimdKernels;
import art.arcane.iris.util.simd.SimdSupport;
import art.arcane.volmlib.util.documentation.ChunkCoordinates;
import art.arcane.volmlib.util.math.PowerOfTwoCoordinates;
import art.arcane.volmlib.util.scheduling.PrecisionStopwatch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Plans one chunk's caves into the mantle for {@link art.arcane.iris.engine.modifier.CarveModule}: the cave
 * profiles of the dimension's carving entries and of the regions and biomes around the chunk, blended over
 * their borders. One per engine runtime, keeping a carver per cave profile.
 */
public class CaveCarving {
    private static final int CHUNK_SIZE = 16;
    private static final int CHUNK_AREA = CHUNK_SIZE * CHUNK_SIZE;
    private static final int BLEND_RADIUS = 3;
    private static final int FIELD_SIZE = CHUNK_SIZE + (BLEND_RADIUS * 2);
    private static final double MIN_WEIGHT = 0.08D;
    private static final double THRESHOLD_PENALTY = 0.24D;
    private static final int MAX_BLENDED_PROFILE_PASSES = 2;
    private static final int KERNEL_WIDTH = (BLEND_RADIUS * 2) + 1;
    private static final int KERNEL_SIZE = KERNEL_WIDTH * KERNEL_WIDTH;
    private static final int[] KERNEL_DX = new int[KERNEL_SIZE];
    private static final int[] KERNEL_DZ = new int[KERNEL_SIZE];
    private static final double[] KERNEL_WEIGHT = new double[KERNEL_SIZE];
    private static final ThreadLocal<BlendScratch> BLEND_SCRATCH = ThreadLocal.withInitial(BlendScratch::new);

    private final Engine engine;
    private final Object profileCarverLock = new Object();
    private volatile Map<IrisCaveProfile, IrisCaveCarver3D> profileCarvers = new IdentityHashMap<>();

    static {
        int kernelIndex = 0;
        for (int offsetX = -BLEND_RADIUS; offsetX <= BLEND_RADIUS; offsetX++) {
            for (int offsetZ = -BLEND_RADIUS; offsetZ <= BLEND_RADIUS; offsetZ++) {
                KERNEL_DX[kernelIndex] = offsetX;
                KERNEL_DZ[kernelIndex] = offsetZ;
                int edgeDistance = Math.max(Math.abs(offsetX), Math.abs(offsetZ));
                KERNEL_WEIGHT[kernelIndex] = (BLEND_RADIUS + 1D) - edgeDistance;
                kernelIndex++;
            }
        }
    }

    public CaveCarving(Engine engine) {
        this.engine = engine;
    }

    /** Carves the caves of the chunk at chunk coordinates {@code x}, {@code z} into {@code writer}. */
    public void carve(MantleWriter writer, int x, int z, ChunkContext context) {
        IrisComplex complex = context.getComplex();
        IrisDimensionCarvingResolver.State resolverState = new IrisDimensionCarvingResolver.State();
        BlendScratch blendScratch = BLEND_SCRATCH.get();
        int[] chunkSurfaceHeights = prepareChunkSurfaceHeights(x, z, context, blendScratch.chunkSurfaceHeights);
        PrecisionStopwatch resolveStopwatch = PrecisionStopwatch.start();
        List<WeightedProfile> weightedProfiles = resolveWeightedProfiles(x, z, complex, resolverState);
        engine.getMetrics().getCarveResolve().put(resolveStopwatch.getMilliseconds());
        int fluidHeight = engine.getDimension().getFluidHeight();
        int[] surfaceFluidBoundaryStartY = blendScratch.surfaceFluidBoundaryStartY;
        SurfaceFluidBoundaryPlan.fill(
                chunkSurfaceHeights,
                blendScratch.fieldSurfaceHeights,
                blendScratch.fieldHasFluid,
                FIELD_SIZE,
                BLEND_RADIUS,
                fluidHeight,
                surfaceFluidBoundaryStartY
        );
        CaveFluidSupportPlan fluidSupportPlan = new CaveFluidSupportPlan();
        for (WeightedProfile weightedProfile : weightedProfiles) {
            carveProfile(weightedProfile, writer, x, z, chunkSurfaceHeights, surfaceFluidBoundaryStartY, fluidSupportPlan);
        }

        UpperDimensionContext upperCtx = engine.getUpperContext();
        if (upperCtx != null && engine.getDimension().isUpperDimensionCarving()) {
            carveUpperTerrain(upperCtx, weightedProfiles, writer, x, z, chunkSurfaceHeights, fluidSupportPlan);
        }
        fluidSupportPlan.resolve(writer.acquireChunk(x, z));

        if (!weightedProfiles.isEmpty()) {
            CarveOrphanSweep.sweepChunk(
                    writer.acquireChunk(x, z),
                    chunkSurfaceHeights,
                    maxSurfaceBreakDepth(weightedProfiles),
                    writer.getMantle().getWorldHeight() - 1,
                    surfaceFluidBoundaryStartY,
                    fluidHeight
            );
        }
    }

    private static int maxSurfaceBreakDepth(List<WeightedProfile> weightedProfiles) {
        int maxDepth = 0;
        for (WeightedProfile weightedProfile : weightedProfiles) {
            maxDepth = Math.max(maxDepth, Math.max(0, weightedProfile.profile.getSurfaceBreakDepth()));
        }
        return maxDepth;
    }

    @ChunkCoordinates
    private void carveProfile(WeightedProfile weightedProfile, MantleWriter writer, int cx, int cz,
                              int[] chunkSurfaceHeights, int[] surfaceFluidBoundaryStartY,
                              CaveFluidSupportPlan fluidSupportPlan) {
        IrisCaveCarver3D carver = getCarver(weightedProfile.profile);
        carver.carve(writer, cx, cz, weightedProfile.columnWeights, MIN_WEIGHT, THRESHOLD_PENALTY,
                weightedProfile.worldYRange, chunkSurfaceHeights, surfaceFluidBoundaryStartY, null, fluidSupportPlan);
    }

    private void carveUpperTerrain(UpperDimensionContext upperCtx, List<WeightedProfile> normalProfiles,
                                   MantleWriter writer, int cx, int cz, int[] lowerSurfaceHeights,
                                   CaveFluidSupportPlan fluidSupportPlan) {
        int chunkHeight = engine.getHeight();
        int worldMinHeight = engine.getWorld().minHeight();
        int gap = engine.getDimension().getUpperDimensionGap();
        int baseX = PowerOfTwoCoordinates.chunkToBlock(cx);
        int baseZ = PowerOfTwoCoordinates.chunkToBlock(cz);

        int minUpperSurfaceY = chunkHeight;
        for (int localX = 0; localX < CHUNK_SIZE; localX++) {
            int worldX = baseX + localX;
            for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {
                int worldZ = baseZ + localZ;
                int columnIndex = PowerOfTwoCoordinates.packLocal16(localX, localZ);
                int rawUpper = upperCtx.getUpperSurfaceY(worldX, worldZ);
                int upperY = Math.max(rawUpper, lowerSurfaceHeights[columnIndex] + gap);
                if (upperY < minUpperSurfaceY) {
                    minUpperSurfaceY = upperY;
                }
            }
        }

        if (minUpperSurfaceY >= chunkHeight - 2) {
            return;
        }

        IrisRange upperYRange = new IrisRange(
                minUpperSurfaceY + worldMinHeight,
                chunkHeight - 1 + worldMinHeight
        );
        IrisRange fullVerticalRange = new IrisRange(0, chunkHeight);

        int[] ceilingSurfaceHeights = new int[CHUNK_AREA];
        Arrays.fill(ceilingSurfaceHeights, chunkHeight - 1);

        for (WeightedProfile weightedProfile : normalProfiles) {
            IrisRange constrainedRange;
            if (weightedProfile.worldYRange != null) {
                double min = Math.max(weightedProfile.worldYRange.getMin(), upperYRange.getMin());
                double max = Math.min(weightedProfile.worldYRange.getMax(), upperYRange.getMax());
                if (min >= max) {
                    continue;
                }
                constrainedRange = new IrisRange(min, max);
            } else {
                constrainedRange = upperYRange;
            }
            IrisCaveCarver3D carver = getCarver(weightedProfile.profile);
            carver.carve(writer, cx, cz, weightedProfile.columnWeights, MIN_WEIGHT, THRESHOLD_PENALTY,
                    constrainedRange, ceilingSurfaceHeights, null, fullVerticalRange, fluidSupportPlan);
        }
    }

    private List<WeightedProfile> resolveWeightedProfiles(int chunkX, int chunkZ, IrisComplex complex, IrisDimensionCarvingResolver.State resolverState) {
        BlendScratch blendScratch = BLEND_SCRATCH.get();
        IrisCaveProfile[] profileField = blendScratch.profileField;
        Map<IrisCaveProfile, double[]> columnProfileWeights = blendScratch.columnProfileWeights;
        IdentityHashMap<IrisCaveProfile, Boolean> activeProfiles = blendScratch.activeProfiles;
        List<IrisCaveProfile> profileOrder = blendScratch.profileOrder;
        IrisCaveProfile[] kernelProfiles = blendScratch.kernelProfiles;
        double[] kernelProfileWeights = blendScratch.kernelProfileWeights;
        activeProfiles.clear();
        profileOrder.clear();
        fillProfileField(profileField, chunkX, chunkZ, complex, resolverState, blendScratch);

        for (int localX = 0; localX < CHUNK_SIZE; localX++) {
            for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {
                int profileCount = 0;
                int centerX = localX + BLEND_RADIUS;
                int centerZ = localZ + BLEND_RADIUS;
                double totalKernelWeight = 0D;

                for (int kernelIndex = 0; kernelIndex < KERNEL_SIZE; kernelIndex++) {
                    int sampleX = centerX + KERNEL_DX[kernelIndex];
                    int sampleZ = centerZ + KERNEL_DZ[kernelIndex];
                    IrisCaveProfile profile = profileField[(sampleX * FIELD_SIZE) + sampleZ];
                    if (!isProfileEnabled(profile)) {
                        continue;
                    }

                    double kernelWeight = KERNEL_WEIGHT[kernelIndex];
                    int existingIndex = findProfileIndex(kernelProfiles, profileCount, profile);
                    if (existingIndex >= 0) {
                        kernelProfileWeights[existingIndex] += kernelWeight;
                    } else {
                        kernelProfiles[profileCount] = profile;
                        kernelProfileWeights[profileCount] = kernelWeight;
                        profileCount++;
                    }
                    totalKernelWeight += kernelWeight;
                }

                if (totalKernelWeight <= 0D || profileCount == 0) {
                    continue;
                }

                int columnIndex = PowerOfTwoCoordinates.packLocal16(localX, localZ);
                for (int profileIndex = 0; profileIndex < profileCount; profileIndex++) {
                    IrisCaveProfile profile = kernelProfiles[profileIndex];
                    double kernelWeight = kernelProfileWeights[profileIndex];
                    kernelProfiles[profileIndex] = null;
                    kernelProfileWeights[profileIndex] = 0D;

                    double columnWeight = clampWeight(kernelWeight / totalKernelWeight);
                    if (columnWeight < MIN_WEIGHT) {
                        continue;
                    }

                    double[] weights = columnProfileWeights.get(profile);
                    if (weights == null) {
                        weights = new double[CHUNK_AREA];
                        columnProfileWeights.put(profile, weights);
                    } else if (!activeProfiles.containsKey(profile)) {
                        Arrays.fill(weights, 0D);
                    }
                    if (activeProfiles.put(profile, Boolean.TRUE) == null) {
                        profileOrder.add(profile);
                    }
                    weights[columnIndex] = columnWeight;
                }
            }
        }

        List<WeightedProfile> columnWeightedProfiles = new ArrayList<>();
        for (IrisCaveProfile profile : profileOrder) {
            double[] weights = columnProfileWeights.get(profile);
            if (weights == null) {
                continue;
            }

            // averageWeight ranks and orders carve passes, so it must be bit-identical across
            // installations: kernels.sum reassociates FP addition under the vector kernel,
            // making profile order depend on a JVM flag. max() is order-independent and safe.
            double maxWeight = SimdSupport.kernels().max(weights, weights.length);

            if (maxWeight < MIN_WEIGHT) {
                continue;
            }

            double averageWeight = computeAverageWeight(weights);
            columnWeightedProfiles.add(new WeightedProfile(profile, weights, averageWeight, null, columnWeightedProfiles.size()));
        }

        List<WeightedProfile> blendedProfiles = limitAndMergeBlendedProfiles(columnWeightedProfiles, MAX_BLENDED_PROFILE_PASSES, CHUNK_AREA);
        List<WeightedProfile> resolvedProfiles = resolveDimensionCarvingProfiles(chunkX, chunkZ, resolverState, blendScratch);
        resolvedProfiles.addAll(blendedProfiles);
        return resolvedProfiles;
    }

    private List<WeightedProfile> resolveDimensionCarvingProfiles(int chunkX, int chunkZ, IrisDimensionCarvingResolver.State resolverState, BlendScratch blendScratch) {
        List<WeightedProfile> weightedProfiles = new ArrayList<>();
        List<IrisDimensionCarvingEntry> entries = engine.getDimension().getCarving();
        if (entries == null || entries.isEmpty()) {
            return weightedProfiles;
        }

        Map<IrisDimensionCarvingEntry, IrisDimensionCarvingEntry[]> dimensionColumnPlans = blendScratch.dimensionColumnPlans;
        dimensionColumnPlans.clear();

        for (IrisDimensionCarvingEntry entry : entries) {
            if (entry == null || !entry.isEnabled()) {
                continue;
            }

            IrisBiome rootBiome = IrisDimensionCarvingResolver.resolveEntryBiome(engine, entry, resolverState);
            if (rootBiome == null) {
                continue;
            }

            IrisDimensionCarvingEntry[] columnPlan = dimensionColumnPlans.computeIfAbsent(entry, key -> new IrisDimensionCarvingEntry[CHUNK_AREA]);
            buildDimensionColumnPlan(columnPlan, chunkX, chunkZ, entry, resolverState);

            Map<IrisCaveProfile, double[]> rootProfileColumnWeights = new IdentityHashMap<>();
            List<IrisCaveProfile> rootProfileOrder = new ArrayList<>();
            IrisRange worldYRange = entry.getWorldYRange();
            for (int columnIndex = 0; columnIndex < CHUNK_AREA; columnIndex++) {
                IrisDimensionCarvingEntry resolvedEntry = columnPlan[columnIndex];
                IrisBiome resolvedBiome = IrisDimensionCarvingResolver.resolveEntryBiome(engine, resolvedEntry, resolverState);
                if (resolvedBiome == null) {
                    continue;
                }

                IrisCaveProfile profile = resolvedBiome.getCaveProfile();
                if (!isProfileEnabled(profile)) {
                    continue;
                }

                double[] columnWeights = rootProfileColumnWeights.get(profile);
                if (columnWeights == null) {
                    columnWeights = new double[CHUNK_AREA];
                    rootProfileColumnWeights.put(profile, columnWeights);
                    rootProfileOrder.add(profile);
                }
                columnWeights[columnIndex] = 1D;
            }

            for (IrisCaveProfile profile : rootProfileOrder) {
                weightedProfiles.add(new WeightedProfile(profile, rootProfileColumnWeights.get(profile), -1D, worldYRange, weightedProfiles.size()));
            }
        }

        return weightedProfiles;
    }

    private void buildDimensionColumnPlan(IrisDimensionCarvingEntry[] columnPlan, int chunkX, int chunkZ, IrisDimensionCarvingEntry entry, IrisDimensionCarvingResolver.State resolverState) {
        int baseX = PowerOfTwoCoordinates.chunkToBlock(chunkX);
        int baseZ = PowerOfTwoCoordinates.chunkToBlock(chunkZ);
        for (int localX = 0; localX < CHUNK_SIZE; localX++) {
            int worldX = baseX + localX;
            for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {
                int worldZ = baseZ + localZ;
                int columnIndex = PowerOfTwoCoordinates.packLocal16(localX, localZ);
                columnPlan[columnIndex] = IrisDimensionCarvingResolver.resolveFromRoot(engine, entry, worldX, worldZ, resolverState);
            }
        }
    }

    private void fillProfileField(IrisCaveProfile[] profileField, int chunkX, int chunkZ, IrisComplex complex, IrisDimensionCarvingResolver.State resolverState, BlendScratch blendScratch) {
        int startX = PowerOfTwoCoordinates.chunkToBlock(chunkX) - BLEND_RADIUS;
        int startZ = PowerOfTwoCoordinates.chunkToBlock(chunkZ) - BLEND_RADIUS;
        prefillProfileFieldSamples(startX, startZ, complex, blendScratch);

        for (int fieldX = 0; fieldX < FIELD_SIZE; fieldX++) {
            int worldX = startX + fieldX;
            for (int fieldZ = 0; fieldZ < FIELD_SIZE; fieldZ++) {
                int worldZ = startZ + fieldZ;
                int fieldIndex = (fieldX * FIELD_SIZE) + fieldZ;
                profileField[fieldIndex] = resolveColumnProfile(
                        worldX,
                        worldZ,
                        blendScratch.fieldSurfaceHeights[fieldIndex],
                        blendScratch.fieldRegions[fieldIndex],
                        blendScratch.fieldSurfaceBiomes[fieldIndex],
                        blendScratch.fieldCaveBiomes[fieldIndex],
                        resolverState
                );
            }
        }
    }

    private int findProfileIndex(IrisCaveProfile[] profiles, int size, IrisCaveProfile profile) {
        for (int index = 0; index < size; index++) {
            if (profiles[index] == profile) {
                return index;
            }
        }

        return -1;
    }

    private IrisCaveProfile resolveColumnProfile(
            int worldX,
            int worldZ,
            double surfaceHeight,
            IrisRegion region,
            IrisBiome surfaceBiome,
            IrisBiome caveBiome,
            IrisDimensionCarvingResolver.State resolverState
    ) {
        IrisCaveProfile resolved = null;
        IrisCaveProfile dimensionProfile = engine.getDimension().getCaveProfile();
        if (isProfileEnabled(dimensionProfile)) {
            resolved = dimensionProfile;
        }

        if (region != null) {
            IrisCaveProfile regionProfile = region.getCaveProfile();
            if (isProfileEnabled(regionProfile)) {
                resolved = regionProfile;
            }
        }

        if (surfaceBiome != null) {
            IrisCaveProfile surfaceProfile = surfaceBiome.getCaveProfile();
            if (isProfileEnabled(surfaceProfile)) {
                resolved = surfaceProfile;
            }
        }

        int roundedSurfaceY = (int) Math.round(surfaceHeight);
        int sampleY = Math.max(1, roundedSurfaceY - 56);
        int worldY = sampleY + engine.getWorld().minHeight();
        IrisDimensionCarvingEntry rootCarvingEntry = IrisDimensionCarvingResolver.resolveRootEntry(engine, worldY, resolverState);
        IrisBiome resolvedCarvingBiome = null;
        if (rootCarvingEntry != null) {
            IrisDimensionCarvingEntry resolvedCarvingEntry = IrisDimensionCarvingResolver.resolveFromRoot(engine, rootCarvingEntry, worldX, worldZ, resolverState);
            resolvedCarvingBiome = IrisDimensionCarvingResolver.resolveEntryBiome(engine, resolvedCarvingEntry, resolverState);
            if (resolvedCarvingBiome != null) {
                caveBiome = resolvedCarvingBiome;
            }
        }
        if (caveBiome != null) {
            IrisBiome effectiveCaveBiome = caveBiome;
            if (resolvedCarvingBiome == null && surfaceBiome != null) {
                if (surfaceBiome != null) {
                    int truncatedSurfaceY = (int) surfaceHeight;
                    int depthBelowSurface = truncatedSurfaceY - sampleY;
                    if (depthBelowSurface <= 0) {
                        effectiveCaveBiome = surfaceBiome;
                    } else {
                        int minDepth = Math.max(0, caveBiome.getCaveMinDepthBelowSurface());
                        if (depthBelowSurface < minDepth) {
                            effectiveCaveBiome = surfaceBiome;
                        }
                    }
                }
            }

            IrisCaveProfile caveProfile = effectiveCaveBiome.getCaveProfile();
            if (isProfileEnabled(caveProfile)) {
                resolved = caveProfile;
            }
        }

        return resolved;
    }

    private void prefillProfileFieldSamples(int startX, int startZ, IrisComplex complex, BlendScratch blendScratch) {
        fillFieldHeights(complex.getHeightStream(), startX, startZ, blendScratch.fieldSurfaceHeights);
        fillFieldFluidPresence(complex.getFluidStream(), startX, startZ, blendScratch.fieldHasFluid);
        fillFieldObjects(complex.getRegionStream(), startX, startZ, blendScratch.fieldRegions);
        fillFieldObjects(complex.getTrueBiomeStream(), startX, startZ, blendScratch.fieldSurfaceBiomes);
        fillFieldObjects(complex.getCaveBiomeStream(), startX, startZ, blendScratch.fieldCaveBiomes);
    }

    private <T> void fillFieldObjects(ProceduralStream<T> stream, int startX, int startZ, T[] target) {
        for (int fieldX = 0; fieldX < FIELD_SIZE; fieldX++) {
            int worldX = startX + fieldX;
            for (int fieldZ = 0; fieldZ < FIELD_SIZE; fieldZ++) {
                target[(fieldX * FIELD_SIZE) + fieldZ] = stream.get(worldX, startZ + fieldZ);
            }
        }
    }

    private void fillFieldHeights(ProceduralStream<Double> stream, int startX, int startZ, double[] target) {
        for (int fieldX = 0; fieldX < FIELD_SIZE; fieldX++) {
            int worldX = startX + fieldX;
            for (int fieldZ = 0; fieldZ < FIELD_SIZE; fieldZ++) {
                target[(fieldX * FIELD_SIZE) + fieldZ] = stream.getDouble(worldX, startZ + fieldZ);
            }
        }
    }

    private void fillFieldFluidPresence(ProceduralStream<PlatformBlockState> stream, int startX, int startZ, boolean[] target) {
        for (int fieldX = 0; fieldX < FIELD_SIZE; fieldX++) {
            int worldX = startX + fieldX;
            for (int fieldZ = 0; fieldZ < FIELD_SIZE; fieldZ++) {
                target[(fieldX * FIELD_SIZE) + fieldZ] = B.isFluid(stream.get(worldX, startZ + fieldZ));
            }
        }
    }

    private IrisCaveCarver3D getCarver(IrisCaveProfile profile) {
        IrisCaveCarver3D carver = profileCarvers.get(profile);
        if (carver != null) {
            return carver;
        }

        IrisCaveCarver3D createdCarver = new IrisCaveCarver3D(engine, profile);
        synchronized (profileCarverLock) {
            IrisCaveCarver3D published = profileCarvers.get(profile);
            if (published != null) {
                return published;
            }

            Map<IrisCaveProfile, IrisCaveCarver3D> updated = new IdentityHashMap<>(profileCarvers);
            updated.put(profile, createdCarver);
            profileCarvers = updated;
            return createdCarver;
        }
    }

    private boolean isProfileEnabled(IrisCaveProfile profile) {
        return profile != null && profile.isEnabled();
    }

    private int[] prepareChunkSurfaceHeights(int chunkX, int chunkZ, ChunkContext context, int[] scratch) {
        int[] surfaceHeights = scratch;
        int baseX = PowerOfTwoCoordinates.chunkToBlock(chunkX);
        int baseZ = PowerOfTwoCoordinates.chunkToBlock(chunkZ);
        boolean useContextHeight = context != null
                && context.getHeight() != null
                && context.getX() == baseX
                && context.getZ() == baseZ;
        double[] cachedChunkHeights = null;
        if (!useContextHeight && context != null) {
            ProceduralStream<Double> heightStream = context.getComplex().getHeightStream();
            if (heightStream instanceof ChunkFillableDoubleStream2D cachedHeightStream) {
                cachedChunkHeights = BLEND_SCRATCH.get().chunkSurfaceHeightSamples;
                cachedHeightStream.fillChunkDoubles(baseX, baseZ, cachedChunkHeights);
            }
        }
        for (int localX = 0; localX < CHUNK_SIZE; localX++) {
            int worldX = baseX + localX;
            for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {
                int worldZ = baseZ + localZ;
                int columnIndex = PowerOfTwoCoordinates.packLocal16(localX, localZ);
                if (useContextHeight) {
                    surfaceHeights[columnIndex] = context.getRoundedHeight(localX, localZ);
                    continue;
                }
                if (cachedChunkHeights != null) {
                    surfaceHeights[columnIndex] = (int) Math.round(cachedChunkHeights[(localZ << 4) + localX]);
                    continue;
                }
                surfaceHeights[columnIndex] = engine.getHeight(worldX, worldZ);
            }
        }
        return surfaceHeights;
    }

    private static List<WeightedProfile> limitAndMergeBlendedProfiles(List<WeightedProfile> blendedProfiles, int maxProfiles) {
        return limitAndMergeBlendedProfiles(blendedProfiles, maxProfiles, CHUNK_AREA);
    }

    private static List<WeightedProfile> limitAndMergeBlendedProfiles(List<WeightedProfile> blendedProfiles, int maxProfiles, int areaSize) {
        if (blendedProfiles == null || blendedProfiles.isEmpty()) {
            return new ArrayList<>();
        }

        int clampedLimit = Math.max(1, maxProfiles);
        List<WeightedProfile> rankedProfiles = new ArrayList<>(blendedProfiles);
        rankedProfiles.sort(CaveCarving::compareBySelectionRank);
        List<WeightedProfile> keptProfiles = new ArrayList<>();
        int keptCount = Math.min(clampedLimit, rankedProfiles.size());
        for (int index = 0; index < keptCount; index++) {
            keptProfiles.add(rankedProfiles.get(index));
        }

        if (rankedProfiles.size() > keptCount) {
            for (int columnIndex = 0; columnIndex < areaSize; columnIndex++) {
                int dominantIndex = 0;
                double dominantWeight = Double.NEGATIVE_INFINITY;
                for (int keptIndex = 0; keptIndex < keptProfiles.size(); keptIndex++) {
                    double keptWeight = keptProfiles.get(keptIndex).columnWeights[columnIndex];
                    if (keptWeight > dominantWeight) {
                        dominantWeight = keptWeight;
                        dominantIndex = keptIndex;
                    }
                }

                double droppedWeight = 0D;
                for (int droppedIndex = keptCount; droppedIndex < rankedProfiles.size(); droppedIndex++) {
                    droppedWeight += rankedProfiles.get(droppedIndex).columnWeights[columnIndex];
                }
                if (droppedWeight <= 0D) {
                    continue;
                }

                WeightedProfile dominantProfile = keptProfiles.get(dominantIndex);
                double mergedWeight = dominantProfile.columnWeights[columnIndex] + droppedWeight;
                dominantProfile.columnWeights[columnIndex] = clampWeight(mergedWeight);
            }
        }

        List<WeightedProfile> mergedProfiles = new ArrayList<>();
        for (WeightedProfile keptProfile : keptProfiles) {
            double averageWeight = computeAverageWeight(keptProfile.columnWeights, areaSize);
            mergedProfiles.add(new WeightedProfile(keptProfile.profile, keptProfile.columnWeights, averageWeight, keptProfile.worldYRange, keptProfile.sequence));
        }
        mergedProfiles.sort(CaveCarving::compareByCarveOrder);
        return mergedProfiles;
    }

    private static int compareBySelectionRank(WeightedProfile a, WeightedProfile b) {
        int weightOrder = Double.compare(b.averageWeight, a.averageWeight);
        if (weightOrder != 0) {
            return weightOrder;
        }
        return Integer.compare(a.sequence, b.sequence);
    }

    private static int compareByCarveOrder(WeightedProfile a, WeightedProfile b) {
        int weightOrder = Double.compare(a.averageWeight, b.averageWeight);
        if (weightOrder != 0) {
            return weightOrder;
        }
        return Integer.compare(a.sequence, b.sequence);
    }

    private static double computeAverageWeight(double[] weights) {
        return computeAverageWeight(weights, CHUNK_AREA);
    }

    private static double computeAverageWeight(double[] weights, int areaSize) {
        if (weights == null || weights.length == 0) {
            return 0D;
        }
        double sum = 0D;
        for (double weight : weights) {
            sum += weight;
        }
        return sum / Math.max(1, areaSize);
    }

    private static double clampWeight(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0D;
        }
        if (value <= 0D) {
            return 0D;
        }
        if (value >= 1D) {
            return 1D;
        }
        return value;
    }

    private static final class WeightedProfile {
        private final IrisCaveProfile profile;
        private final double[] columnWeights;
        private final double averageWeight;
        private final IrisRange worldYRange;
        private final int sequence;

        private WeightedProfile(IrisCaveProfile profile, double[] columnWeights, double averageWeight, IrisRange worldYRange, int sequence) {
            this.profile = profile;
            this.columnWeights = columnWeights;
            this.averageWeight = averageWeight;
            this.worldYRange = worldYRange;
            this.sequence = sequence;
        }

        private double averageWeight() {
            return averageWeight;
        }
    }

    private static final class BlendScratch {
        private final IrisCaveProfile[] profileField = new IrisCaveProfile[FIELD_SIZE * FIELD_SIZE];
        private final IrisCaveProfile[] kernelProfiles = new IrisCaveProfile[KERNEL_SIZE];
        private final double[] kernelProfileWeights = new double[KERNEL_SIZE];
        private final IdentityHashMap<IrisCaveProfile, double[]> columnProfileWeights = new IdentityHashMap<>();
        private final IdentityHashMap<IrisDimensionCarvingEntry, IrisDimensionCarvingEntry[]> dimensionColumnPlans = new IdentityHashMap<>();
        private final IdentityHashMap<IrisCaveProfile, Boolean> activeProfiles = new IdentityHashMap<>();
        private final List<IrisCaveProfile> profileOrder = new ArrayList<>();
        private final double[] fieldSurfaceHeights = new double[FIELD_SIZE * FIELD_SIZE];
        private final boolean[] fieldHasFluid = new boolean[FIELD_SIZE * FIELD_SIZE];
        private final IrisRegion[] fieldRegions = new IrisRegion[FIELD_SIZE * FIELD_SIZE];
        private final IrisBiome[] fieldSurfaceBiomes = new IrisBiome[FIELD_SIZE * FIELD_SIZE];
        private final IrisBiome[] fieldCaveBiomes = new IrisBiome[FIELD_SIZE * FIELD_SIZE];
        private final int[] chunkSurfaceHeights = new int[CHUNK_AREA];
        private final int[] surfaceFluidBoundaryStartY = new int[CHUNK_AREA];
        private final double[] chunkSurfaceHeightSamples = new double[CHUNK_AREA];
    }
}
