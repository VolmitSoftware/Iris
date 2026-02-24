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

import art.arcane.iris.engine.data.cache.Cache;
import art.arcane.iris.engine.mantle.ComponentFlag;
import art.arcane.iris.engine.mantle.EngineMantle;
import art.arcane.iris.engine.mantle.IrisMantleComponent;
import art.arcane.iris.engine.mantle.MantleWriter;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisCaveProfile;
import art.arcane.iris.engine.object.IrisDimensionCarvingEntry;
import art.arcane.iris.engine.object.IrisDimensionCarvingResolver;
import art.arcane.iris.engine.object.IrisRegion;
import art.arcane.iris.engine.object.IrisRange;
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.volmlib.util.documentation.ChunkCoordinates;
import art.arcane.volmlib.util.mantle.flag.ReservedFlag;
import art.arcane.volmlib.util.scheduling.PrecisionStopwatch;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

@ComponentFlag(ReservedFlag.CARVED)
public class MantleCarvingComponent extends IrisMantleComponent {
    private static final int CHUNK_SIZE = 16;
    private static final int CHUNK_AREA = CHUNK_SIZE * CHUNK_SIZE;
    private static final int TILE_SIZE = 2;
    private static final int TILE_COUNT = CHUNK_SIZE / TILE_SIZE;
    private static final int TILE_AREA = TILE_COUNT * TILE_COUNT;
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

    private final Map<IrisCaveProfile, IrisCaveCarver3D> profileCarvers = new IdentityHashMap<>();

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
    public void generateLayer(MantleWriter writer, int x, int z, ChunkContext context) {
        IrisDimensionCarvingResolver.State resolverState = new IrisDimensionCarvingResolver.State();
        Long2ObjectOpenHashMap<IrisBiome> caveBiomeCache = new Long2ObjectOpenHashMap<>(FIELD_SIZE * FIELD_SIZE);
        BlendScratch blendScratch = BLEND_SCRATCH.get();
        int[] chunkSurfaceHeights = prepareChunkSurfaceHeights(x, z, context, blendScratch.chunkSurfaceHeights);
        PrecisionStopwatch resolveStopwatch = PrecisionStopwatch.start();
        List<WeightedProfile> weightedProfiles = resolveWeightedProfiles(x, z, resolverState, caveBiomeCache);
        getEngineMantle().getEngine().getMetrics().getCarveResolve().put(resolveStopwatch.getMilliseconds());
        for (WeightedProfile weightedProfile : weightedProfiles) {
            carveProfile(weightedProfile, writer, x, z, chunkSurfaceHeights);
        }
    }

    @ChunkCoordinates
    private void carveProfile(WeightedProfile weightedProfile, MantleWriter writer, int cx, int cz, int[] chunkSurfaceHeights) {
        IrisCaveCarver3D carver = getCarver(weightedProfile.profile);
        carver.carve(writer, cx, cz, weightedProfile.columnWeights, MIN_WEIGHT, THRESHOLD_PENALTY, weightedProfile.worldYRange, chunkSurfaceHeights);
    }

    private List<WeightedProfile> resolveWeightedProfiles(int chunkX, int chunkZ, IrisDimensionCarvingResolver.State resolverState, Long2ObjectOpenHashMap<IrisBiome> caveBiomeCache) {
        BlendScratch blendScratch = BLEND_SCRATCH.get();
        IrisCaveProfile[] profileField = blendScratch.profileField;
        Map<IrisCaveProfile, double[]> tileProfileWeights = blendScratch.tileProfileWeights;
        IdentityHashMap<IrisCaveProfile, Boolean> activeProfiles = blendScratch.activeProfiles;
        IrisCaveProfile[] kernelProfiles = blendScratch.kernelProfiles;
        double[] kernelProfileWeights = blendScratch.kernelProfileWeights;
        activeProfiles.clear();
        fillProfileField(profileField, chunkX, chunkZ, resolverState, caveBiomeCache);

        for (int tileX = 0; tileX < TILE_COUNT; tileX++) {
            for (int tileZ = 0; tileZ < TILE_COUNT; tileZ++) {
                int profileCount = 0;
                int sampleLocalX = (tileX * TILE_SIZE) + 1;
                int sampleLocalZ = (tileZ * TILE_SIZE) + 1;
                int centerX = sampleLocalX + BLEND_RADIUS;
                int centerZ = sampleLocalZ + BLEND_RADIUS;
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

                IrisCaveProfile dominantProfile = null;
                double dominantKernelWeight = Double.NEGATIVE_INFINITY;
                for (int profileIndex = 0; profileIndex < profileCount; profileIndex++) {
                    IrisCaveProfile profile = kernelProfiles[profileIndex];
                    double kernelWeight = kernelProfileWeights[profileIndex];
                    if (kernelWeight > dominantKernelWeight) {
                        dominantProfile = profile;
                        dominantKernelWeight = kernelWeight;
                    } else if (kernelWeight == dominantKernelWeight
                            && profileSortKey(profile) < profileSortKey(dominantProfile)) {
                        dominantProfile = profile;
                    }
                    kernelProfiles[profileIndex] = null;
                    kernelProfileWeights[profileIndex] = 0D;
                }

                if (dominantProfile == null) {
                    continue;
                }

                int tileIndex = tileIndex(tileX, tileZ);
                double dominantWeight = clampWeight(dominantKernelWeight / totalKernelWeight);
                double[] tileWeights = tileProfileWeights.get(dominantProfile);
                if (tileWeights == null) {
                    tileWeights = new double[TILE_AREA];
                    tileProfileWeights.put(dominantProfile, tileWeights);
                } else if (!activeProfiles.containsKey(dominantProfile)) {
                    Arrays.fill(tileWeights, 0D);
                }
                activeProfiles.put(dominantProfile, Boolean.TRUE);
                tileWeights[tileIndex] = dominantWeight;
            }
        }

        List<WeightedProfile> tileWeightedProfiles = new ArrayList<>();
        for (IrisCaveProfile profile : activeProfiles.keySet()) {
            double[] tileWeights = tileProfileWeights.get(profile);
            if (tileWeights == null) {
                continue;
            }

            double totalWeight = 0D;
            double maxWeight = 0D;
            for (double weight : tileWeights) {
                totalWeight += weight;
                if (weight > maxWeight) {
                    maxWeight = weight;
                }
            }

            if (maxWeight < MIN_WEIGHT) {
                continue;
            }

            double averageWeight = totalWeight / TILE_AREA;
            tileWeightedProfiles.add(new WeightedProfile(profile, tileWeights, averageWeight, null));
        }

        List<WeightedProfile> boundedTileProfiles = limitAndMergeBlendedProfiles(tileWeightedProfiles, MAX_BLENDED_PROFILE_PASSES, TILE_AREA);
        List<WeightedProfile> blendedProfiles = expandTileWeightedProfiles(boundedTileProfiles);
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

        Map<IrisDimensionCarvingEntry, IrisDimensionCarvingEntry[]> dimensionTilePlans = blendScratch.dimensionTilePlans;
        dimensionTilePlans.clear();

        for (IrisDimensionCarvingEntry entry : entries) {
            if (entry == null || !entry.isEnabled()) {
                continue;
            }

            IrisBiome rootBiome = IrisDimensionCarvingResolver.resolveEntryBiome(getEngineMantle().getEngine(), entry, resolverState);
            if (rootBiome == null) {
                continue;
            }

            IrisDimensionCarvingEntry[] tilePlan = dimensionTilePlans.computeIfAbsent(entry, key -> new IrisDimensionCarvingEntry[TILE_AREA]);
            buildDimensionTilePlan(tilePlan, chunkX, chunkZ, entry, resolverState);

            Map<IrisCaveProfile, double[]> rootProfileTileWeights = new IdentityHashMap<>();
            IrisRange worldYRange = entry.getWorldYRange();
            for (int tileIndex = 0; tileIndex < TILE_AREA; tileIndex++) {
                IrisDimensionCarvingEntry resolvedEntry = tilePlan[tileIndex];
                IrisBiome resolvedBiome = IrisDimensionCarvingResolver.resolveEntryBiome(getEngineMantle().getEngine(), resolvedEntry, resolverState);
                if (resolvedBiome == null) {
                    continue;
                }

                IrisCaveProfile profile = resolvedBiome.getCaveProfile();
                if (!isProfileEnabled(profile)) {
                    continue;
                }

                double[] tileWeights = rootProfileTileWeights.computeIfAbsent(profile, key -> new double[TILE_AREA]);
                tileWeights[tileIndex] = 1D;
            }

            List<Map.Entry<IrisCaveProfile, double[]>> profileEntries = new ArrayList<>(rootProfileTileWeights.entrySet());
            profileEntries.sort((a, b) -> Integer.compare(a.getKey().hashCode(), b.getKey().hashCode()));
            for (Map.Entry<IrisCaveProfile, double[]> profileEntry : profileEntries) {
                double[] columnWeights = expandTileWeightsToColumns(profileEntry.getValue());
                weightedProfiles.add(new WeightedProfile(profileEntry.getKey(), columnWeights, -1D, worldYRange));
            }
        }

        return weightedProfiles;
    }

    private void buildDimensionTilePlan(IrisDimensionCarvingEntry[] tilePlan, int chunkX, int chunkZ, IrisDimensionCarvingEntry entry, IrisDimensionCarvingResolver.State resolverState) {
        for (int tileX = 0; tileX < TILE_COUNT; tileX++) {
            int worldX = (chunkX << 4) + (tileX * TILE_SIZE);
            for (int tileZ = 0; tileZ < TILE_COUNT; tileZ++) {
                int worldZ = (chunkZ << 4) + (tileZ * TILE_SIZE);
                int tileIndex = tileIndex(tileX, tileZ);
                tilePlan[tileIndex] = IrisDimensionCarvingResolver.resolveFromRoot(getEngineMantle().getEngine(), entry, worldX, worldZ, resolverState);
            }
        }
    }

    private List<WeightedProfile> expandTileWeightedProfiles(List<WeightedProfile> tileWeightedProfiles) {
        List<WeightedProfile> expandedProfiles = new ArrayList<>(tileWeightedProfiles.size());
        for (WeightedProfile tileWeightedProfile : tileWeightedProfiles) {
            double[] columnWeights = expandTileWeightsToColumns(tileWeightedProfile.columnWeights);
            double averageWeight = computeAverageWeight(columnWeights, CHUNK_AREA);
            expandedProfiles.add(new WeightedProfile(tileWeightedProfile.profile, columnWeights, averageWeight, tileWeightedProfile.worldYRange));
        }
        expandedProfiles.sort(MantleCarvingComponent::compareByCarveOrder);
        return expandedProfiles;
    }

    private static double[] expandTileWeightsToColumns(double[] tileWeights) {
        double[] columnWeights = new double[CHUNK_AREA];
        if (tileWeights == null || tileWeights.length == 0) {
            return columnWeights;
        }

        for (int tileX = 0; tileX < TILE_COUNT; tileX++) {
            int columnX = tileX * TILE_SIZE;
            int columnX2 = columnX + 1;
            for (int tileZ = 0; tileZ < TILE_COUNT; tileZ++) {
                int tileIndex = tileIndex(tileX, tileZ);
                double weight = tileWeights[tileIndex];
                if (weight <= 0D) {
                    continue;
                }

                int columnZ = tileZ * TILE_SIZE;
                int columnZ2 = columnZ + 1;
                columnWeights[(columnX << 4) | columnZ] = weight;
                columnWeights[(columnX << 4) | columnZ2] = weight;
                columnWeights[(columnX2 << 4) | columnZ] = weight;
                columnWeights[(columnX2 << 4) | columnZ2] = weight;
            }
        }

        return columnWeights;
    }

    private static int tileIndex(int tileX, int tileZ) {
        return (tileX * TILE_COUNT) + tileZ;
    }

    private void fillProfileField(IrisCaveProfile[] profileField, int chunkX, int chunkZ, IrisDimensionCarvingResolver.State resolverState, Long2ObjectOpenHashMap<IrisBiome> caveBiomeCache) {
        int startX = (chunkX << 4) - BLEND_RADIUS;
        int startZ = (chunkZ << 4) - BLEND_RADIUS;

        for (int fieldX = 0; fieldX < FIELD_SIZE; fieldX++) {
            int worldX = startX + fieldX;
            for (int fieldZ = 0; fieldZ < FIELD_SIZE; fieldZ++) {
                int worldZ = startZ + fieldZ;
                profileField[(fieldX * FIELD_SIZE) + fieldZ] = resolveColumnProfile(worldX, worldZ, resolverState, caveBiomeCache);
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

    private IrisCaveProfile resolveColumnProfile(int worldX, int worldZ, IrisDimensionCarvingResolver.State resolverState, Long2ObjectOpenHashMap<IrisBiome> caveBiomeCache) {
        IrisCaveProfile resolved = null;
        IrisCaveProfile dimensionProfile = getDimension().getCaveProfile();
        if (isProfileEnabled(dimensionProfile)) {
            resolved = dimensionProfile;
        }

        IrisRegion region = getComplex().getRegionStream().get(worldX, worldZ);
        if (region != null) {
            IrisCaveProfile regionProfile = region.getCaveProfile();
            if (isProfileEnabled(regionProfile)) {
                resolved = regionProfile;
            }
        }

        IrisBiome surfaceBiome = getComplex().getTrueBiomeStream().get(worldX, worldZ);
        if (surfaceBiome != null) {
            IrisCaveProfile surfaceProfile = surfaceBiome.getCaveProfile();
            if (isProfileEnabled(surfaceProfile)) {
                resolved = surfaceProfile;
            }
        }

        int surfaceY = getEngineMantle().getEngine().getHeight(worldX, worldZ, true);
        int sampleY = Math.max(1, surfaceY - 56);
        long cacheKey = Cache.key(worldX, worldZ);
        IrisBiome caveBiome = caveBiomeCache.get(cacheKey);
        if (caveBiome == null) {
            caveBiome = getEngineMantle().getEngine().getCaveBiome(worldX, sampleY, worldZ, resolverState);
            if (caveBiome != null) {
                caveBiomeCache.put(cacheKey, caveBiome);
            }
        }
        if (caveBiome != null) {
            IrisCaveProfile caveProfile = caveBiome.getCaveProfile();
            if (isProfileEnabled(caveProfile)) {
                resolved = caveProfile;
            }
        }

        return resolved;
    }

    private IrisCaveCarver3D getCarver(IrisCaveProfile profile) {
        synchronized (profileCarvers) {
            IrisCaveCarver3D carver = profileCarvers.get(profile);
            if (carver != null) {
                return carver;
            }

            IrisCaveCarver3D createdCarver = new IrisCaveCarver3D(getEngineMantle().getEngine(), profile);
            profileCarvers.put(profile, createdCarver);
            return createdCarver;
        }
    }

    private boolean isProfileEnabled(IrisCaveProfile profile) {
        return profile != null && profile.isEnabled();
    }

    protected int computeRadius() {
        return 0;
    }

    private int[] prepareChunkSurfaceHeights(int chunkX, int chunkZ, ChunkContext context, int[] scratch) {
        int[] surfaceHeights = scratch;
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        boolean useContextHeight = context != null
                && context.getHeight() != null
                && context.getX() == baseX
                && context.getZ() == baseZ;
        for (int localX = 0; localX < CHUNK_SIZE; localX++) {
            int worldX = baseX + localX;
            for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {
                int worldZ = baseZ + localZ;
                int columnIndex = (localX << 4) | localZ;
                if (useContextHeight) {
                    Double cachedHeight = context.getHeight().get(localX, localZ);
                    if (cachedHeight != null) {
                        surfaceHeights[columnIndex] = (int) Math.round(cachedHeight);
                        continue;
                    }
                }
                surfaceHeights[columnIndex] = getEngineMantle().getEngine().getHeight(worldX, worldZ);
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
            mergedProfiles.add(new WeightedProfile(keptProfile.profile, keptProfile.columnWeights, averageWeight, keptProfile.worldYRange));
        }
        mergedProfiles.sort(MantleCarvingComponent::compareByCarveOrder);
        return mergedProfiles;
    }

    private static int compareBySelectionRank(WeightedProfile a, WeightedProfile b) {
        int weightOrder = Double.compare(b.averageWeight, a.averageWeight);
        if (weightOrder != 0) {
            return weightOrder;
        }
        return Integer.compare(profileSortKey(a.profile), profileSortKey(b.profile));
    }

    private static int compareByCarveOrder(WeightedProfile a, WeightedProfile b) {
        int weightOrder = Double.compare(a.averageWeight, b.averageWeight);
        if (weightOrder != 0) {
            return weightOrder;
        }
        return Integer.compare(profileSortKey(a.profile), profileSortKey(b.profile));
    }

    private static int profileSortKey(IrisCaveProfile profile) {
        if (profile == null) {
            return 0;
        }
        return profile.hashCode();
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

        private WeightedProfile(IrisCaveProfile profile, double[] columnWeights, double averageWeight, IrisRange worldYRange) {
            this.profile = profile;
            this.columnWeights = columnWeights;
            this.averageWeight = averageWeight;
            this.worldYRange = worldYRange;
        }

        private double averageWeight() {
            return averageWeight;
        }
    }

    private static final class BlendScratch {
        private final IrisCaveProfile[] profileField = new IrisCaveProfile[FIELD_SIZE * FIELD_SIZE];
        private final IrisCaveProfile[] kernelProfiles = new IrisCaveProfile[KERNEL_SIZE];
        private final double[] kernelProfileWeights = new double[KERNEL_SIZE];
        private final IdentityHashMap<IrisCaveProfile, double[]> tileProfileWeights = new IdentityHashMap<>();
        private final IdentityHashMap<IrisDimensionCarvingEntry, IrisDimensionCarvingEntry[]> dimensionTilePlans = new IdentityHashMap<>();
        private final IdentityHashMap<IrisCaveProfile, Boolean> activeProfiles = new IdentityHashMap<>();
        private final int[] chunkSurfaceHeights = new int[CHUNK_AREA];
    }
}
