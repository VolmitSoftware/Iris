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

import art.arcane.iris.builtin.MatterMantle;
import art.arcane.iris.builtin.carve.CaveProfile;
import art.arcane.iris.engine.object.terrain.carving.CarvingResolver;
import art.arcane.iris.engine.object.terrain.carving.DeclaredCaveProfiles;
import art.arcane.iris.engine.object.terrain.carving.ResolvedCarvingEntry;
import art.arcane.volmlib.util.math.PowerOfTwoCoordinates;
import com.volmit.config.Range;
import com.volmit.resource.ResourceId;
import com.volmit.world.BiomeSample;
import com.volmit.world.PackBiome;
import com.volmit.world.PackWorld;
import com.volmit.world.TerrainSampler;

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
    /** A column's cave biome is the one the world places this far below its surface (at least at height 1). */
    private static final int CAVE_SAMPLE_DEPTH_BELOW_SURFACE_BLOCKS = 56;
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

    private final CarveWorld world;
    private final DeclaredCaveProfiles declaredProfiles;
    private final CarvingResolver resolver;
    private final FluidColumns fluidColumns;
    private final boolean upperDimensionCarving;
    private final Object profileCarverLock = new Object();
    private volatile Map<CaveProfile, IrisCaveCarver3D> profileCarvers = new IdentityHashMap<>();

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

    /** Whether the dimension's fluid palette gives a column a fluid, which caves near it blend towards. */
    @FunctionalInterface
    public interface FluidColumns {
        boolean hasFluid(int worldX, int worldZ);
    }

    public CaveCarving(PackWorld packWorld, CarveWorld world, boolean upperDimensionCarving, FluidColumns fluidColumns) {
        this.world = world;
        this.declaredProfiles = DeclaredCaveProfiles.of(packWorld);
        this.resolver = CarvingResolver.of(packWorld);
        this.fluidColumns = fluidColumns;
        this.upperDimensionCarving = upperDimensionCarving;
    }

    /** Carves the caves of the chunk at chunk coordinates {@code x}, {@code z} into {@code mantle}, reading the terrain around it from {@code terrain}. */
    public void carve(MatterMantle mantle, TerrainSampler terrain, int x, int z) {
        BlendScratch blendScratch = BLEND_SCRATCH.get();
        int[] chunkSurfaceHeights = prepareChunkSurfaceHeights(x, z, terrain, blendScratch.chunkSurfaceHeights);
        List<WeightedProfile> weightedProfiles = resolveWeightedProfiles(x, z, terrain);
        int fluidHeight = world.fluidHeight();
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
            carveProfile(weightedProfile, mantle, x, z, chunkSurfaceHeights, surfaceFluidBoundaryStartY, fluidSupportPlan);
        }

        if (upperDimensionCarving && hasUpperDimension(terrain, x, z)) {
            carveUpperTerrain(terrain, weightedProfiles, mantle, x, z, fluidSupportPlan);
        }
        fluidSupportPlan.resolve(mantle.chunk(x, z));

        if (!weightedProfiles.isEmpty()) {
            CarveOrphanSweep.sweepChunk(
                    mantle.chunk(x, z),
                    chunkSurfaceHeights,
                    maxSurfaceBreakDepth(weightedProfiles),
                    world.height() - 1,
                    surfaceFluidBoundaryStartY,
                    fluidHeight
            );
        }
    }

    private static boolean hasUpperDimension(TerrainSampler terrain, int chunkX, int chunkZ) {
        return terrain.upperDimensionY(PowerOfTwoCoordinates.chunkToBlock(chunkX), PowerOfTwoCoordinates.chunkToBlock(chunkZ)).isPresent();
    }

    private static int maxSurfaceBreakDepth(List<WeightedProfile> weightedProfiles) {
        int maxDepth = 0;
        for (WeightedProfile weightedProfile : weightedProfiles) {
            maxDepth = Math.max(maxDepth, Math.max(0, weightedProfile.profile.surfaceBreakDepth()));
        }
        return maxDepth;
    }

    private void carveProfile(WeightedProfile weightedProfile, MatterMantle mantle, int cx, int cz,
                              int[] chunkSurfaceHeights, int[] surfaceFluidBoundaryStartY,
                              CaveFluidSupportPlan fluidSupportPlan) {
        IrisCaveCarver3D carver = getCarver(weightedProfile.profile);
        carver.carve(mantle, cx, cz, weightedProfile.columnWeights, MIN_WEIGHT, THRESHOLD_PENALTY,
                weightedProfile.worldYRange, chunkSurfaceHeights, surfaceFluidBoundaryStartY, null, fluidSupportPlan);
    }

    private void carveUpperTerrain(TerrainSampler terrain, List<WeightedProfile> normalProfiles,
                                   MatterMantle mantle, int cx, int cz,
                                   CaveFluidSupportPlan fluidSupportPlan) {
        int chunkHeight = world.height();
        int worldMinHeight = world.minHeight();
        int baseX = PowerOfTwoCoordinates.chunkToBlock(cx);
        int baseZ = PowerOfTwoCoordinates.chunkToBlock(cz);

        int minUpperSurfaceY = chunkHeight;
        for (int localX = 0; localX < CHUNK_SIZE; localX++) {
            int worldX = baseX + localX;
            for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {
                int worldZ = baseZ + localZ;
                int upperY = terrain.upperDimensionY(worldX, worldZ).getAsInt();
                if (upperY < minUpperSurfaceY) {
                    minUpperSurfaceY = upperY;
                }
            }
        }

        if (minUpperSurfaceY >= chunkHeight - 2) {
            return;
        }

        Range upperYRange = Range.of(
                minUpperSurfaceY + worldMinHeight,
                chunkHeight - 1 + worldMinHeight
        );
        Range fullVerticalRange = Range.of(0, chunkHeight);

        int[] ceilingSurfaceHeights = new int[CHUNK_AREA];
        Arrays.fill(ceilingSurfaceHeights, chunkHeight - 1);

        for (WeightedProfile weightedProfile : normalProfiles) {
            Range constrainedRange;
            if (weightedProfile.worldYRange != null) {
                double min = Math.max(weightedProfile.worldYRange.min(), upperYRange.min());
                double max = Math.min(weightedProfile.worldYRange.max(), upperYRange.max());
                if (min >= max) {
                    continue;
                }
                constrainedRange = Range.of(min, max);
            } else {
                constrainedRange = upperYRange;
            }
            IrisCaveCarver3D carver = getCarver(weightedProfile.profile);
            carver.carve(mantle, cx, cz, weightedProfile.columnWeights, MIN_WEIGHT, THRESHOLD_PENALTY,
                    constrainedRange, ceilingSurfaceHeights, null, fullVerticalRange, fluidSupportPlan);
        }
    }

    private List<WeightedProfile> resolveWeightedProfiles(int chunkX, int chunkZ, TerrainSampler terrain) {
        BlendScratch blendScratch = BLEND_SCRATCH.get();
        CaveProfile[] profileField = blendScratch.profileField;
        Map<CaveProfile, double[]> columnProfileWeights = blendScratch.columnProfileWeights;
        IdentityHashMap<CaveProfile, Boolean> activeProfiles = blendScratch.activeProfiles;
        List<CaveProfile> profileOrder = blendScratch.profileOrder;
        CaveProfile[] kernelProfiles = blendScratch.kernelProfiles;
        double[] kernelProfileWeights = blendScratch.kernelProfileWeights;
        activeProfiles.clear();
        profileOrder.clear();
        fillProfileField(profileField, chunkX, chunkZ, terrain, blendScratch);

        for (int localX = 0; localX < CHUNK_SIZE; localX++) {
            for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {
                int profileCount = 0;
                int centerX = localX + BLEND_RADIUS;
                int centerZ = localZ + BLEND_RADIUS;
                double totalKernelWeight = 0D;

                for (int kernelIndex = 0; kernelIndex < KERNEL_SIZE; kernelIndex++) {
                    int sampleX = centerX + KERNEL_DX[kernelIndex];
                    int sampleZ = centerZ + KERNEL_DZ[kernelIndex];
                    CaveProfile profile = profileField[(sampleX * FIELD_SIZE) + sampleZ];
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
                    CaveProfile profile = kernelProfiles[profileIndex];
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
        for (CaveProfile profile : profileOrder) {
            double[] weights = columnProfileWeights.get(profile);
            if (weights == null) {
                continue;
            }

            // averageWeight ranks and orders carve passes, so it must be bit-identical across
            // installations: kernels.sum reassociates FP addition under the vector kernel,
            // making profile order depend on a JVM flag. max() is order-independent and safe.
            double maxWeight = maxOf(weights);

            if (maxWeight < MIN_WEIGHT) {
                continue;
            }

            double averageWeight = computeAverageWeight(weights);
            columnWeightedProfiles.add(new WeightedProfile(profile, weights, averageWeight, null, columnWeightedProfiles.size()));
        }

        List<WeightedProfile> blendedProfiles = limitAndMergeBlendedProfiles(columnWeightedProfiles, MAX_BLENDED_PROFILE_PASSES, CHUNK_AREA);
        List<WeightedProfile> resolvedProfiles = resolveDimensionCarvingProfiles(chunkX, chunkZ, blendScratch);
        resolvedProfiles.addAll(blendedProfiles);
        return resolvedProfiles;
    }

    private List<WeightedProfile> resolveDimensionCarvingProfiles(int chunkX, int chunkZ, BlendScratch blendScratch) {
        List<WeightedProfile> weightedProfiles = new ArrayList<>();
        List<ResolvedCarvingEntry> entries = resolver.entries();
        if (entries.isEmpty()) {
            return weightedProfiles;
        }

        Map<ResolvedCarvingEntry, ResolvedCarvingEntry[]> dimensionColumnPlans = blendScratch.dimensionColumnPlans;
        dimensionColumnPlans.clear();

        for (ResolvedCarvingEntry entry : entries) {
            if (!entry.enabled()) {
                continue;
            }

            PackBiome rootBiome = resolver.entryBiome(entry);
            if (rootBiome == null) {
                continue;
            }

            ResolvedCarvingEntry[] columnPlan = dimensionColumnPlans.computeIfAbsent(entry, key -> new ResolvedCarvingEntry[CHUNK_AREA]);
            buildDimensionColumnPlan(columnPlan, chunkX, chunkZ, entry);

            Map<CaveProfile, double[]> rootProfileColumnWeights = new IdentityHashMap<>();
            List<CaveProfile> rootProfileOrder = new ArrayList<>();
            Range worldYRange = entry.worldYRange();
            for (int columnIndex = 0; columnIndex < CHUNK_AREA; columnIndex++) {
                ResolvedCarvingEntry resolvedEntry = columnPlan[columnIndex];
                PackBiome resolvedBiome = resolver.entryBiome(resolvedEntry);
                if (resolvedBiome == null) {
                    continue;
                }

                CaveProfile profile = declaredProfiles.ofBiome(resolvedBiome.id());
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

            for (CaveProfile profile : rootProfileOrder) {
                weightedProfiles.add(new WeightedProfile(profile, rootProfileColumnWeights.get(profile), -1D, worldYRange, weightedProfiles.size()));
            }
        }

        return weightedProfiles;
    }

    private void buildDimensionColumnPlan(ResolvedCarvingEntry[] columnPlan, int chunkX, int chunkZ, ResolvedCarvingEntry entry) {
        int baseX = PowerOfTwoCoordinates.chunkToBlock(chunkX);
        int baseZ = PowerOfTwoCoordinates.chunkToBlock(chunkZ);
        for (int localX = 0; localX < CHUNK_SIZE; localX++) {
            int worldX = baseX + localX;
            for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {
                int worldZ = baseZ + localZ;
                int columnIndex = PowerOfTwoCoordinates.packLocal16(localX, localZ);
                columnPlan[columnIndex] = resolver.resolveFromRoot(entry, worldX, worldZ);
            }
        }
    }

    private void fillProfileField(CaveProfile[] profileField, int chunkX, int chunkZ, TerrainSampler terrain, BlendScratch blendScratch) {
        int startX = PowerOfTwoCoordinates.chunkToBlock(chunkX) - BLEND_RADIUS;
        int startZ = PowerOfTwoCoordinates.chunkToBlock(chunkZ) - BLEND_RADIUS;
        prefillFieldHeightsAndFluid(terrain, startX, startZ, blendScratch);

        for (int fieldX = 0; fieldX < FIELD_SIZE; fieldX++) {
            int worldX = startX + fieldX;
            for (int fieldZ = 0; fieldZ < FIELD_SIZE; fieldZ++) {
                int worldZ = startZ + fieldZ;
                int fieldIndex = (fieldX * FIELD_SIZE) + fieldZ;
                profileField[fieldIndex] = resolveColumnProfile(terrain, worldX, worldZ, blendScratch.fieldSurfaceHeights[fieldIndex]);
            }
        }
    }

    private int findProfileIndex(CaveProfile[] profiles, int size, CaveProfile profile) {
        for (int index = 0; index < size; index++) {
            if (profiles[index] == profile) {
                return index;
            }
        }

        return -1;
    }

    /**
     * The profile that carves a column: the dimension's, replaced by the region's, the surface biome's and the cave biome's
     * wherever they declare an enabled one. The cave biome is the one the world places at the depth below the surface this
     * column's caves start at ({@link #CAVE_SAMPLE_DEPTH_BELOW_SURFACE_BLOCKS}), after the dimension's carving bands.
     */
    private CaveProfile resolveColumnProfile(TerrainSampler terrain, int worldX, int worldZ, double surfaceHeight) {
        CaveProfile resolved = null;
        CaveProfile dimensionProfile = declaredProfiles.ofDimension();
        if (isProfileEnabled(dimensionProfile)) {
            resolved = dimensionProfile;
        }

        int roundedSurfaceY = (int) Math.round(surfaceHeight);
        int sampleY = Math.max(1, roundedSurfaceY - CAVE_SAMPLE_DEPTH_BELOW_SURFACE_BLOCKS);
        BiomeSample sample = terrain.biome(worldX, sampleY, worldZ);

        CaveProfile regionProfile = declaredProfiles.ofRegion(sample.region());
        if (isProfileEnabled(regionProfile)) {
            resolved = regionProfile;
        }

        CaveProfile surfaceProfile = declaredProfiles.ofBiome(sample.surfaceBiome());
        if (isProfileEnabled(surfaceProfile)) {
            resolved = surfaceProfile;
        }

        if (sample.caveBiome().isPresent()) {
            CaveProfile caveProfile = declaredProfiles.ofBiome(sample.caveBiome().get());
            if (isProfileEnabled(caveProfile)) {
                resolved = caveProfile;
            }
        }

        return resolved;
    }

    private void prefillFieldHeightsAndFluid(TerrainSampler terrain, int startX, int startZ, BlendScratch blendScratch) {
        for (int fieldX = 0; fieldX < FIELD_SIZE; fieldX++) {
            int worldX = startX + fieldX;
            for (int fieldZ = 0; fieldZ < FIELD_SIZE; fieldZ++) {
                int worldZ = startZ + fieldZ;
                int fieldIndex = (fieldX * FIELD_SIZE) + fieldZ;
                blendScratch.fieldSurfaceHeights[fieldIndex] = terrain.surfaceY(worldX, worldZ);
                blendScratch.fieldHasFluid[fieldIndex] = fluidColumns.hasFluid(worldX, worldZ);
            }
        }
    }

    private IrisCaveCarver3D getCarver(CaveProfile profile) {
        IrisCaveCarver3D carver = profileCarvers.get(profile);
        if (carver != null) {
            return carver;
        }

        IrisCaveCarver3D createdCarver = new IrisCaveCarver3D(world, profile);
        synchronized (profileCarverLock) {
            IrisCaveCarver3D published = profileCarvers.get(profile);
            if (published != null) {
                return published;
            }

            Map<CaveProfile, IrisCaveCarver3D> updated = new IdentityHashMap<>(profileCarvers);
            updated.put(profile, createdCarver);
            profileCarvers = updated;
            return createdCarver;
        }
    }

    private boolean isProfileEnabled(CaveProfile profile) {
        return profile != null && profile.enabled();
    }

    private int[] prepareChunkSurfaceHeights(int chunkX, int chunkZ, TerrainSampler terrain, int[] scratch) {
        int baseX = PowerOfTwoCoordinates.chunkToBlock(chunkX);
        int baseZ = PowerOfTwoCoordinates.chunkToBlock(chunkZ);
        for (int localX = 0; localX < CHUNK_SIZE; localX++) {
            int worldX = baseX + localX;
            for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {
                int columnIndex = PowerOfTwoCoordinates.packLocal16(localX, localZ);
                scratch[columnIndex] = terrain.surfaceHeight(worldX, baseZ + localZ);
            }
        }
        return scratch;
    }

    private static double maxOf(double[] values) {
        double max = Double.NEGATIVE_INFINITY;
        for (double value : values) {
            max = Math.max(max, value);
        }
        return max;
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
        private final CaveProfile profile;
        private final double[] columnWeights;
        private final double averageWeight;
        private final Range worldYRange;
        private final int sequence;

        private WeightedProfile(CaveProfile profile, double[] columnWeights, double averageWeight, Range worldYRange, int sequence) {
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
        private final CaveProfile[] profileField = new CaveProfile[FIELD_SIZE * FIELD_SIZE];
        private final CaveProfile[] kernelProfiles = new CaveProfile[KERNEL_SIZE];
        private final double[] kernelProfileWeights = new double[KERNEL_SIZE];
        private final IdentityHashMap<CaveProfile, double[]> columnProfileWeights = new IdentityHashMap<>();
        private final IdentityHashMap<ResolvedCarvingEntry, ResolvedCarvingEntry[]> dimensionColumnPlans = new IdentityHashMap<>();
        private final IdentityHashMap<CaveProfile, Boolean> activeProfiles = new IdentityHashMap<>();
        private final List<CaveProfile> profileOrder = new ArrayList<>();
        private final double[] fieldSurfaceHeights = new double[FIELD_SIZE * FIELD_SIZE];
        private final boolean[] fieldHasFluid = new boolean[FIELD_SIZE * FIELD_SIZE];
        private final int[] chunkSurfaceHeights = new int[CHUNK_AREA];
        private final int[] surfaceFluidBoundaryStartY = new int[CHUNK_AREA];
    }
}
