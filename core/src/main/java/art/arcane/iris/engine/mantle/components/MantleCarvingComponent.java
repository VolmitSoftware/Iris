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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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

    private final Map<IrisCaveProfile, IrisCaveCarver3D> profileCarvers = new IdentityHashMap<>();

    public MantleCarvingComponent(EngineMantle engineMantle) {
        super(engineMantle, ReservedFlag.CARVED, 0);
    }

    @Override
    public void generateLayer(MantleWriter writer, int x, int z, ChunkContext context) {
        List<WeightedProfile> weightedProfiles = resolveWeightedProfiles(x, z);
        for (WeightedProfile weightedProfile : weightedProfiles) {
            carveProfile(weightedProfile, writer, x, z);
        }
    }

    @ChunkCoordinates
    private void carveProfile(WeightedProfile weightedProfile, MantleWriter writer, int cx, int cz) {
        IrisCaveCarver3D carver = getCarver(weightedProfile.profile);
        carver.carve(writer, cx, cz, weightedProfile.columnWeights, MIN_WEIGHT, THRESHOLD_PENALTY, weightedProfile.worldYRange);
    }

    private List<WeightedProfile> resolveWeightedProfiles(int chunkX, int chunkZ) {
        IrisCaveProfile[] profileField = buildProfileField(chunkX, chunkZ);
        Map<IrisCaveProfile, double[]> profileWeights = new IdentityHashMap<>();

        for (int localX = 0; localX < CHUNK_SIZE; localX++) {
            for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {
                int columnIndex = (localX << 4) | localZ;
                Map<IrisCaveProfile, Double> columnInfluence = sampleColumnInfluence(profileField, localX, localZ);
                for (Map.Entry<IrisCaveProfile, Double> entry : columnInfluence.entrySet()) {
                    double[] weights = profileWeights.computeIfAbsent(entry.getKey(), key -> new double[CHUNK_AREA]);
                    weights[columnIndex] = entry.getValue();
                }
            }
        }

        List<WeightedProfile> weightedProfiles = new ArrayList<>();
        for (Map.Entry<IrisCaveProfile, double[]> entry : profileWeights.entrySet()) {
            IrisCaveProfile profile = entry.getKey();
            double[] weights = entry.getValue();
            double totalWeight = 0D;
            double maxWeight = 0D;

            for (double weight : weights) {
                totalWeight += weight;
                if (weight > maxWeight) {
                    maxWeight = weight;
                }
            }

            if (maxWeight < MIN_WEIGHT) {
                continue;
            }

            double averageWeight = totalWeight / CHUNK_AREA;
            weightedProfiles.add(new WeightedProfile(profile, weights, averageWeight, null));
        }

        weightedProfiles.sort(Comparator.comparingDouble(WeightedProfile::averageWeight));
        weightedProfiles.addAll(0, resolveDimensionCarvingProfiles(chunkX, chunkZ));
        return weightedProfiles;
    }

    private List<WeightedProfile> resolveDimensionCarvingProfiles(int chunkX, int chunkZ) {
        List<WeightedProfile> weightedProfiles = new ArrayList<>();
        List<IrisDimensionCarvingEntry> entries = getDimension().getCarving();
        if (entries == null || entries.isEmpty()) {
            return weightedProfiles;
        }

        for (IrisDimensionCarvingEntry entry : entries) {
            if (entry == null || !entry.isEnabled()) {
                continue;
            }

            IrisBiome rootBiome = IrisDimensionCarvingResolver.resolveEntryBiome(getEngineMantle().getEngine(), entry);
            if (rootBiome == null) {
                continue;
            }

            Map<IrisCaveProfile, double[]> rootProfileWeights = new IdentityHashMap<>();
            IrisRange worldYRange = entry.getWorldYRange();
            for (int localX = 0; localX < CHUNK_SIZE; localX++) {
                for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {
                    int worldX = (chunkX << 4) + localX;
                    int worldZ = (chunkZ << 4) + localZ;
                    int columnIndex = (localX << 4) | localZ;
                    IrisDimensionCarvingEntry resolvedEntry = IrisDimensionCarvingResolver.resolveFromRoot(getEngineMantle().getEngine(), entry, worldX, worldZ);
                    IrisBiome resolvedBiome = IrisDimensionCarvingResolver.resolveEntryBiome(getEngineMantle().getEngine(), resolvedEntry);
                    if (resolvedBiome == null) {
                        continue;
                    }

                    IrisCaveProfile profile = resolvedBiome.getCaveProfile();
                    if (!isProfileEnabled(profile)) {
                        continue;
                    }

                    double[] weights = rootProfileWeights.computeIfAbsent(profile, key -> new double[CHUNK_AREA]);
                    weights[columnIndex] = 1D;
                }
            }

            List<Map.Entry<IrisCaveProfile, double[]>> profileEntries = new ArrayList<>(rootProfileWeights.entrySet());
            profileEntries.sort((a, b) -> Integer.compare(a.getKey().hashCode(), b.getKey().hashCode()));
            for (Map.Entry<IrisCaveProfile, double[]> profileEntry : profileEntries) {
                weightedProfiles.add(new WeightedProfile(profileEntry.getKey(), profileEntry.getValue(), -1D, worldYRange));
            }
        }

        return weightedProfiles;
    }

    private Map<IrisCaveProfile, Double> sampleColumnInfluence(IrisCaveProfile[] profileField, int localX, int localZ) {
        Map<IrisCaveProfile, Double> profileBlend = new IdentityHashMap<>();
        int centerX = localX + BLEND_RADIUS;
        int centerZ = localZ + BLEND_RADIUS;
        double totalKernelWeight = 0D;

        for (int offsetX = -BLEND_RADIUS; offsetX <= BLEND_RADIUS; offsetX++) {
            for (int offsetZ = -BLEND_RADIUS; offsetZ <= BLEND_RADIUS; offsetZ++) {
                int sampleX = centerX + offsetX;
                int sampleZ = centerZ + offsetZ;
                IrisCaveProfile profile = profileField[(sampleX * FIELD_SIZE) + sampleZ];
                if (!isProfileEnabled(profile)) {
                    continue;
                }

                double kernelWeight = haloWeight(offsetX, offsetZ);
                profileBlend.merge(profile, kernelWeight, Double::sum);
                totalKernelWeight += kernelWeight;
            }
        }

        if (totalKernelWeight <= 0D || profileBlend.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<IrisCaveProfile, Double> normalized = new IdentityHashMap<>();
        for (Map.Entry<IrisCaveProfile, Double> entry : profileBlend.entrySet()) {
            normalized.put(entry.getKey(), entry.getValue() / totalKernelWeight);
        }

        return normalized;
    }

    private IrisCaveProfile[] buildProfileField(int chunkX, int chunkZ) {
        IrisCaveProfile[] profileField = new IrisCaveProfile[FIELD_SIZE * FIELD_SIZE];
        int startX = (chunkX << 4) - BLEND_RADIUS;
        int startZ = (chunkZ << 4) - BLEND_RADIUS;

        for (int fieldX = 0; fieldX < FIELD_SIZE; fieldX++) {
            int worldX = startX + fieldX;
            for (int fieldZ = 0; fieldZ < FIELD_SIZE; fieldZ++) {
                int worldZ = startZ + fieldZ;
                profileField[(fieldX * FIELD_SIZE) + fieldZ] = resolveColumnProfile(worldX, worldZ);
            }
        }

        return profileField;
    }

    private double haloWeight(int offsetX, int offsetZ) {
        int edgeDistance = Math.max(Math.abs(offsetX), Math.abs(offsetZ));
        return (BLEND_RADIUS + 1D) - edgeDistance;
    }

    private IrisCaveProfile resolveColumnProfile(int worldX, int worldZ) {
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
        IrisBiome caveBiome = getEngineMantle().getEngine().getCaveBiome(worldX, sampleY, worldZ);
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
}
