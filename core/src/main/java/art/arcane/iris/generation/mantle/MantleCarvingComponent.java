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

import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.UpperDimensionContext;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.cave.IrisCaveProfile;
import art.arcane.iris.generation.terrain.IrisDimensionCarvingEntry;
import art.arcane.iris.generation.terrain.IrisDimensionCarvingResolver;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.pack.value.IrisRange;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.generation.block.B;
import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.volmlib.util.stream.ProceduralStream;
import art.arcane.iris.generation.stream.ChunkFillableDoubleStream2D;
import art.arcane.iris.generation.simd.SimdSupport;
import art.arcane.volmlib.util.documentation.ChunkCoordinates;
import art.arcane.volmlib.util.mantle.flag.ReservedFlag;
import art.arcane.volmlib.util.math.PowerOfTwoCoordinates;
import art.arcane.volmlib.util.scheduling.PrecisionStopwatch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

@ComponentFlag(ReservedFlag.CARVED)
public class MantleCarvingComponent extends IrisMantleComponent {
    private static final int CHUNK_SIZE = 16;
    private static final int CHUNK_AREA = CHUNK_SIZE * CHUNK_SIZE;
    private static final int BLEND_RADIUS = 3;
    private static final int FIELD_SIZE = CHUNK_SIZE + (BLEND_RADIUS * 2);
    private static final double MIN_WEIGHT = 0.08D;
    private static final double THRESHOLD_PENALTY = 0.24D;
    private static final int MAX_BLENDED_PROFILE_PASSES = 2;
    private static final int MAX_POOLED_WEIGHT_BUFFERS = 64;
    private static final int KERNEL_WIDTH = (BLEND_RADIUS * 2) + 1;
    private static final int KERNEL_SIZE = KERNEL_WIDTH * KERNEL_WIDTH;
    private static final int[] KERNEL_DX = new int[KERNEL_SIZE];
    private static final int[] KERNEL_DZ = new int[KERNEL_SIZE];
    private static final double[] KERNEL_WEIGHT = new double[KERNEL_SIZE];
    private static final ThreadLocal<BlendScratch> BLEND_SCRATCH = ThreadLocal.withInitial(BlendScratch::new);

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

    public MantleCarvingComponent(EngineMantle engineMantle) {
        super(engineMantle, ReservedFlag.CARVED, 0);
    }

    @Override
    public MatterGenerationPhase getGenerationPhase() {
        return MatterGenerationPhase.TERRAIN;
    }

    @Override
    public void hotload() {
        super.hotload();
        // Hotload swaps in fresh IrisCaveProfile instances (identity keys), so retained
        // entries would strand a full carver set pinning the closed IrisData per reload.
        // Carvers rebuild deterministically from the carve seed, so output is unchanged.
        synchronized (profileCarverLock) {
            profileCarvers = new IdentityHashMap<>();
        }
    }

    @Override
    public void generateLayer(MantleWriter writer, int x, int z, ChunkContext context) {
        IrisComplex complex = context.getComplex();
        if (!complex.allowsMantleChunkWrite(x, z)) {
            return;
        }
        BlendScratch previous = BLEND_SCRATCH.get();
        BlendScratch scratch = previous.inUse ? new BlendScratch() : previous;
        if (scratch != previous) {
            BLEND_SCRATCH.set(scratch);
        }
        scratch.inUse = true;
        try {
            generateLayer(writer, x, z, context, scratch);
        } finally {
            scratch.release();
            if (scratch != previous) {
                BLEND_SCRATCH.set(previous);
            }
        }
    }

    private void generateLayer(MantleWriter writer, int x, int z, ChunkContext context, BlendScratch blendScratch) {
        IrisComplex complex = context.getComplex();
        IrisDimensionCarvingResolver.State resolverState = new IrisDimensionCarvingResolver.State();
        int[] chunkSurfaceHeights = prepareChunkSurfaceHeights(x, z, context, blendScratch.chunkSurfaceHeights);
        PrecisionStopwatch resolveStopwatch = PrecisionStopwatch.start();
        List<WeightedProfile> weightedProfiles = resolveWeightedProfiles(x, z, complex, resolverState);
        getEngineMantle().getEngine().getMetrics().getCarveResolve().put(resolveStopwatch.getMilliseconds());
        long[] surfaceFluidBoundaries = blendScratch.surfaceFluidBoundaries;
        SurfaceFluidBoundaryPlan.fill(
                chunkSurfaceHeights,
                blendScratch.fieldSurfaceHeights,
                blendScratch.fieldHasFluid,
                blendScratch.fieldFluidHeights,
                FIELD_SIZE,
                BLEND_RADIUS,
                surfaceFluidBoundaries
        );
        CaveFluidSupportPlan fluidSupportPlan = new CaveFluidSupportPlan();
        for (WeightedProfile weightedProfile : weightedProfiles) {
            carveProfile(weightedProfile, writer, x, z, chunkSurfaceHeights, surfaceFluidBoundaries, fluidSupportPlan);
        }

        UpperDimensionContext upperCtx = getEngineMantle().getEngine().getUpperContext();
        if (upperCtx != null && getDimension().isUpperDimensionCarving()) {
            carveUpperTerrain(upperCtx, weightedProfiles, writer, x, z, fluidSupportPlan);
        }
        fluidSupportPlan.resolve(writer.acquireChunk(x, z));

        if (!weightedProfiles.isEmpty()) {
            CarveOrphanSweep.sweepChunk(
                    writer.acquireChunk(x, z),
                    chunkSurfaceHeights,
                    maxSurfaceBreakDepth(weightedProfiles),
                    writer.getMantle().getWorldHeight() - 1,
                    surfaceFluidBoundaries
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
                              int[] chunkSurfaceHeights, long[] surfaceFluidBoundaries,
                              CaveFluidSupportPlan fluidSupportPlan) {
        IrisCaveCarver3D carver = getCarver(weightedProfile.profile);
        carver.carve(writer, cx, cz, weightedProfile.columnWeights, MIN_WEIGHT, THRESHOLD_PENALTY,
                weightedProfile.worldYRange, chunkSurfaceHeights, surfaceFluidBoundaries, null, fluidSupportPlan);
    }

    private void carveUpperTerrain(UpperDimensionContext upperCtx, List<WeightedProfile> normalProfiles,
                                   MantleWriter writer, int cx, int cz,
                                   CaveFluidSupportPlan fluidSupportPlan) {
        int chunkHeight = getEngineMantle().getEngine().getHeight();
        int worldMinHeight = getEngineMantle().getEngine().getWorld().minHeight();
        int baseX = PowerOfTwoCoordinates.chunkToBlock(cx);
        int baseZ = PowerOfTwoCoordinates.chunkToBlock(cz);

        int minUpperSurfaceY = chunkHeight;
        for (int localX = 0; localX < CHUNK_SIZE; localX++) {
            int worldX = baseX + localX;
            for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {
                int worldZ = baseZ + localZ;
                int upperY = upperCtx.getEffectiveSurfaceY(worldX, worldZ);
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
        List<IrisCaveProfile> profileOrder = blendScratch.profileOrder;
        IrisCaveProfile[] kernelProfiles = blendScratch.kernelProfiles;
        double[] kernelProfileWeights = blendScratch.kernelProfileWeights;
        columnProfileWeights.clear();
        profileOrder.clear();
        blendScratch.nextWeightBuffer = 0;
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
                        weights = blendScratch.acquireWeights();
                        columnProfileWeights.put(profile, weights);
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
        List<IrisDimensionCarvingEntry> entries = getDimension().getCarving();
        if (entries == null || entries.isEmpty()) {
            return weightedProfiles;
        }

        Engine engine = getEngineMantle().getEngine();
        int baseX = PowerOfTwoCoordinates.chunkToBlock(chunkX);
        int baseZ = PowerOfTwoCoordinates.chunkToBlock(chunkZ);

        for (IrisDimensionCarvingEntry entry : entries) {
            if (entry == null || !entry.isEnabled()) {
                continue;
            }

            IrisBiome rootBiome = IrisDimensionCarvingResolver.resolveEntryBiome(engine, entry, resolverState);
            if (rootBiome == null) {
                continue;
            }

            Map<IrisCaveProfile, double[]> rootProfileColumnWeights = new IdentityHashMap<>();
            List<IrisCaveProfile> rootProfileOrder = new ArrayList<>();
            IrisRange worldYRange = entry.getWorldYRange();
            for (int columnIndex = 0; columnIndex < CHUNK_AREA; columnIndex++) {
                IrisDimensionCarvingEntry resolvedEntry = IrisDimensionCarvingResolver.resolveFromRoot(
                        engine, entry, baseX + (columnIndex >> 4), baseZ + (columnIndex & 15), resolverState);
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
                    columnWeights = blendScratch.acquireWeights();
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
        IrisCaveProfile dimensionProfile = getDimension().getCaveProfile();
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
        int worldY = sampleY + getEngineMantle().getEngine().getWorld().minHeight();
        IrisDimensionCarvingEntry rootCarvingEntry = IrisDimensionCarvingResolver.resolveRootEntry(getEngineMantle().getEngine(), worldY, resolverState);
        IrisBiome resolvedCarvingBiome = null;
        if (rootCarvingEntry != null) {
            IrisDimensionCarvingEntry resolvedCarvingEntry = IrisDimensionCarvingResolver.resolveFromRoot(getEngineMantle().getEngine(), rootCarvingEntry, worldX, worldZ, resolverState);
            resolvedCarvingBiome = IrisDimensionCarvingResolver.resolveEntryBiome(getEngineMantle().getEngine(), resolvedCarvingEntry, resolverState);
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
        fillFieldHeights(complex.getNaturalHeightStream(), startX, startZ, blendScratch.fieldSurfaceHeights);
        Arrays.fill(blendScratch.fieldFluidHeights, complex.getFluidHeight());
        fillFieldFluidPresence(complex, startX, startZ, blendScratch.fieldSurfaceHeights,
                blendScratch.fieldFluidHeights, blendScratch.fieldHasFluid);
        fillFieldObjects(complex.getRegionStream(), startX, startZ, blendScratch.fieldRegions);
        fillFieldObjects(complex.getNaturalTrueBiomeStream(), startX, startZ, blendScratch.fieldSurfaceBiomes);
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

    private void fillFieldFluidPresence(
            IrisComplex complex,
            int startX,
            int startZ,
            double[] surfaceHeights,
            double[] fluidHeights,
            boolean[] target
    ) {
        for (int fieldX = 0; fieldX < FIELD_SIZE; fieldX++) {
            int worldX = startX + fieldX;
            for (int fieldZ = 0; fieldZ < FIELD_SIZE; fieldZ++) {
                int fieldIndex = (fieldX * FIELD_SIZE) + fieldZ;
                target[fieldIndex] = B.isFluid(complex.getFluidStream().get(worldX, startZ + fieldZ))
                        && Math.round(surfaceHeights[fieldIndex]) < Math.round(fluidHeights[fieldIndex]);
            }
        }
    }

    private IrisCaveCarver3D getCarver(IrisCaveProfile profile) {
        IrisCaveCarver3D carver = profileCarvers.get(profile);
        if (carver != null) {
            return carver;
        }

        IrisCaveCarver3D createdCarver = new IrisCaveCarver3D(getEngineMantle().getEngine(), profile);
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

    protected int computeRadius() {
        return 1;
    }

    static int[] prepareChunkSurfaceHeights(int chunkX, int chunkZ, ChunkContext context, int[] scratch) {
        int[] surfaceHeights = scratch;
        int baseX = PowerOfTwoCoordinates.chunkToBlock(chunkX);
        int baseZ = PowerOfTwoCoordinates.chunkToBlock(chunkZ);
        ProceduralStream<Double> naturalHeight = context.getComplex().getNaturalHeightStream();
        double[] cachedChunkHeights = null;
        if (naturalHeight instanceof ChunkFillableDoubleStream2D cachedHeightStream) {
            cachedChunkHeights = BLEND_SCRATCH.get().chunkSurfaceHeightSamples;
            cachedHeightStream.fillChunkDoubles(baseX, baseZ, cachedChunkHeights);
        }
        for (int localX = 0; localX < CHUNK_SIZE; localX++) {
            int worldX = baseX + localX;
            for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {
                int worldZ = baseZ + localZ;
                int columnIndex = PowerOfTwoCoordinates.packLocal16(localX, localZ);
                if (cachedChunkHeights != null) {
                    surfaceHeights[columnIndex] = (int) Math.round(cachedChunkHeights[(localZ << 4) + localX]);
                    continue;
                }
                surfaceHeights[columnIndex] = (int) Math.round(naturalHeight.getDouble(worldX, worldZ));
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
        rankedProfiles.sort(MantleCarvingComponent::compareBySelectionRank);
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
        mergedProfiles.sort(MantleCarvingComponent::compareByCarveOrder);
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
        private final List<double[]> weightBuffers = new ArrayList<>();
        private final List<IrisCaveProfile> profileOrder = new ArrayList<>();
        private final double[] fieldSurfaceHeights = new double[FIELD_SIZE * FIELD_SIZE];
        private final double[] fieldFluidHeights = new double[FIELD_SIZE * FIELD_SIZE];
        private final boolean[] fieldHasFluid = new boolean[FIELD_SIZE * FIELD_SIZE];
        private final long[] surfaceFluidBoundaries = new long[CHUNK_AREA];
        private final IrisRegion[] fieldRegions = new IrisRegion[FIELD_SIZE * FIELD_SIZE];
        private final IrisBiome[] fieldSurfaceBiomes = new IrisBiome[FIELD_SIZE * FIELD_SIZE];
        private final IrisBiome[] fieldCaveBiomes = new IrisBiome[FIELD_SIZE * FIELD_SIZE];
        private final int[] chunkSurfaceHeights = new int[CHUNK_AREA];
        private final double[] chunkSurfaceHeightSamples = new double[CHUNK_AREA];
        private int nextWeightBuffer;
        private boolean inUse;

        private double[] acquireWeights() {
            if (nextWeightBuffer < weightBuffers.size()) {
                double[] weights = weightBuffers.get(nextWeightBuffer++);
                Arrays.fill(weights, 0D);
                return weights;
            }
            double[] weights = new double[CHUNK_AREA];
            if (weightBuffers.size() < MAX_POOLED_WEIGHT_BUFFERS) {
                weightBuffers.add(weights);
                nextWeightBuffer++;
            }
            return weights;
        }

        private void release() {
            Arrays.fill(profileField, null);
            Arrays.fill(kernelProfiles, null);
            Arrays.fill(fieldRegions, null);
            Arrays.fill(fieldSurfaceBiomes, null);
            Arrays.fill(fieldCaveBiomes, null);
            columnProfileWeights.clear();
            profileOrder.clear();
            nextWeightBuffer = 0;
            inUse = false;
        }
    }
}
