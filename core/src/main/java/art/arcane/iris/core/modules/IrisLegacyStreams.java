/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.core.modules;

import art.arcane.iris.builtin.LegacyStreams;
import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.SeedManager;
import art.arcane.iris.engine.object.noise.IrisNoiseGenerator;
import art.arcane.iris.engine.object.placement.IrisPlacementForm;
import art.arcane.iris.util.project.noise.CNG;
import art.arcane.iris.util.project.noise.NoiseType;
import art.arcane.volmlib.util.cache.CacheKey;
import art.arcane.volmlib.util.math.RNG;

/** Iris's own random streams and placement noise, from the world's legacy seeds and the pack's data. */
final class IrisLegacyStreams implements LegacyStreams {
    private final IrisData data;
    private final SeedManager seeds;

    IrisLegacyStreams(IrisData data, SeedManager seeds) {
        this.data = data;
        this.seeds = seeds;
    }

    @Override
    public RNG objectChunkRandom(int chunkX, int chunkZ) {
        long seed = CacheKey.key(chunkX, chunkZ) + seeds.getMantle();
        CNG noise = CNG.signatureFast(new RNG(seed), NoiseType.WHITE, NoiseType.GLOB);
        return new RNG((long) (seed * noise.noise(chunkX, chunkZ)));
    }

    @Override
    public int density(ObjectPlacement placement, RNG rng, double x, double z) {
        return IrisPlacementForm.of(placement, data).density(placement, rng, x, z, data);
    }

    @Override
    public int heightmapSurface(ObjectPlacement placement, long seed, int x, int z) {
        IrisNoiseGenerator heightmap = IrisPlacementForm.of(placement, data).heightmap();
        if (heightmap == null) {
            throw new IllegalArgumentException("The placement has no virtual heightmap");
        }
        return (int) Math.round(heightmap.getNoise(seed, x, z, data));
    }
}
