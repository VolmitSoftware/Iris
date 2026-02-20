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
import art.arcane.iris.engine.object.IrisCarving;
import art.arcane.iris.engine.object.IrisCaveProfile;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.engine.object.IrisRegion;
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.volmlib.util.documentation.ChunkCoordinates;
import art.arcane.volmlib.util.mantle.flag.ReservedFlag;
import art.arcane.volmlib.util.math.RNG;

import java.util.IdentityHashMap;
import java.util.Map;

@ComponentFlag(ReservedFlag.CARVED)
public class MantleCarvingComponent extends IrisMantleComponent {
    private final Map<IrisCaveProfile, IrisCaveCarver3D> profileCarvers = new IdentityHashMap<>();

    public MantleCarvingComponent(EngineMantle engineMantle) {
        super(engineMantle, ReservedFlag.CARVED, 0);
    }

    @Override
    public void generateLayer(MantleWriter writer, int x, int z, ChunkContext context) {
        RNG rng = new RNG(Cache.key(x, z) + seed());
        int xxx = 8 + (x << 4);
        int zzz = 8 + (z << 4);
        IrisRegion region = getComplex().getRegionStream().get(xxx, zzz);
        IrisBiome surfaceBiome = getComplex().getTrueBiomeStream().get(xxx, zzz);
        IrisCaveProfile caveBiomeProfile = resolveDominantCaveBiomeProfile(x, z);
        carve(writer, rng, x, z, region, surfaceBiome, caveBiomeProfile);
    }

    @ChunkCoordinates
    private void carve(MantleWriter writer, RNG rng, int cx, int cz, IrisRegion region, IrisBiome surfaceBiome, IrisCaveProfile caveBiomeProfile) {
        IrisCaveProfile dimensionProfile = getDimension().getCaveProfile();
        IrisCaveProfile surfaceBiomeProfile = surfaceBiome.getCaveProfile();
        IrisCaveProfile regionProfile = region.getCaveProfile();
        IrisCaveProfile activeProfile = resolveActiveProfile(dimensionProfile, regionProfile, surfaceBiomeProfile, caveBiomeProfile);
        if (isProfileEnabled(activeProfile)) {
            int carved = carveProfile(activeProfile, writer, cx, cz);
            if (carved > 0) {
                return;
            }

            if (activeProfile != regionProfile && isProfileEnabled(regionProfile)) {
                carved = carveProfile(regionProfile, writer, cx, cz);
                if (carved > 0) {
                    return;
                }
            }

            if (activeProfile != surfaceBiomeProfile && isProfileEnabled(surfaceBiomeProfile)) {
                carved = carveProfile(surfaceBiomeProfile, writer, cx, cz);
                if (carved > 0) {
                    return;
                }
            }

            if (activeProfile != dimensionProfile && isProfileEnabled(dimensionProfile)) {
                carved = carveProfile(dimensionProfile, writer, cx, cz);
                if (carved > 0) {
                    return;
                }
            }
        }

        carve(getDimension().getCarving(), writer, nextCarveRng(rng, cx, cz), cx, cz);
        carve(surfaceBiome.getCarving(), writer, nextCarveRng(rng, cx, cz), cx, cz);
        carve(region.getCarving(), writer, nextCarveRng(rng, cx, cz), cx, cz);
    }

    @ChunkCoordinates
    private void carve(IrisCarving carving, MantleWriter writer, RNG rng, int cx, int cz) {
        carving.doCarving(writer, rng, getEngineMantle().getEngine(), cx << 4, -1, cz << 4, 0);
    }

    private RNG nextCarveRng(RNG rng, int cx, int cz) {
        return new RNG((rng.nextLong() * cx) + 490495L + cz);
    }

    @ChunkCoordinates
    private int carveProfile(IrisCaveProfile profile, MantleWriter writer, int cx, int cz) {
        if (!isProfileEnabled(profile)) {
            return 0;
        }

        IrisCaveCarver3D carver = getCarver(profile);
        return carver.carve(writer, cx, cz);
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

    @ChunkCoordinates
    private IrisCaveProfile resolveDominantCaveBiomeProfile(int chunkX, int chunkZ) {
        int[] offsets = new int[]{1, 4, 8, 12, 15};
        Map<IrisCaveProfile, Integer> profileVotes = new IdentityHashMap<>();
        int validSamples = 0;
        IrisCaveProfile dominantProfile = null;
        int dominantVotes = 0;

        for (int offsetX : offsets) {
            for (int offsetZ : offsets) {
                int sampleX = (chunkX << 4) + offsetX;
                int sampleZ = (chunkZ << 4) + offsetZ;
                int surfaceY = getEngineMantle().getEngine().getHeight(sampleX, sampleZ, true);
                int sampleY = Math.max(1, surfaceY - 56);
                IrisBiome caveBiome = getEngineMantle().getEngine().getCaveBiome(sampleX, sampleY, sampleZ);
                if (caveBiome == null) {
                    continue;
                }

                IrisCaveProfile profile = caveBiome.getCaveProfile();
                if (!isProfileEnabled(profile)) {
                    continue;
                }

                int votes = profileVotes.getOrDefault(profile, 0) + 1;
                profileVotes.put(profile, votes);
                validSamples++;
                if (votes > dominantVotes) {
                    dominantVotes = votes;
                    dominantProfile = profile;
                }
            }
        }

        if (dominantProfile == null || validSamples <= 0) {
            return null;
        }

        int requiredVotes = Math.max(13, (int) Math.ceil(validSamples * 0.65D));
        if (dominantVotes < requiredVotes) {
            return null;
        }

        return dominantProfile;
    }

    private IrisCaveProfile resolveActiveProfile(IrisCaveProfile dimensionProfile, IrisCaveProfile regionProfile, IrisCaveProfile surfaceBiomeProfile, IrisCaveProfile caveBiomeProfile) {
        if (isProfileEnabled(caveBiomeProfile)) {
            return caveBiomeProfile;
        }

        if (isProfileEnabled(surfaceBiomeProfile)) {
            return surfaceBiomeProfile;
        }

        if (isProfileEnabled(regionProfile)) {
            return regionProfile;
        }

        return dimensionProfile;
    }

    protected int computeRadius() {
        IrisDimension dimension = getDimension();
        int max = 0;

        max = Math.max(max, dimension.getCarving().getMaxRange(getData(), 0));

        for (IrisRegion i : dimension.getAllRegions(this::getData)) {
            max = Math.max(max, i.getCarving().getMaxRange(getData(), 0));
        }

        for (IrisBiome i : dimension.getAllBiomes(this::getData)) {
            max = Math.max(max, i.getCarving().getMaxRange(getData(), 0));
        }

        return max;
    }
}
