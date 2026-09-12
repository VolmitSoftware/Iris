/*
 * Iris is a World Generator for Minecraft Bukkit Servers
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

package art.arcane.iris.generation.mantle;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.cache.Cache;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.stage.IrisFloatingChildBiomeModifier;
import art.arcane.iris.generation.biome.FloatingIslandBoundarySampler;
import art.arcane.iris.generation.biome.FloatingIslandSample;
import art.arcane.iris.generation.biome.IrisBiome;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.jetbrains.annotations.Nullable;

final class FloatingIslandSampleResolver implements IslandObjectPlacer.SampleProvider {
    private final IrisData data;
    private final Engine engine;
    private final int chunkHeight;
    private final long baseSeed;
    private final FloatingIslandBoundarySampler boundarySampler;
    private final Long2ObjectOpenHashMap<FloatingIslandSample> sampleCache;
    private final LongOpenHashSet resolvedSamples;

    FloatingIslandSampleResolver(EngineMantle engineMantle, FloatingIslandBoundarySampler boundarySampler) {
        this.data = engineMantle.getData();
        this.engine = engineMantle.getEngine();
        this.chunkHeight = engine.getHeight();
        this.baseSeed = engine.getSeedManager().getTerrain() ^ IrisFloatingChildBiomeModifier.FLOATING_BASE_SEED_SALT;
        this.boundarySampler = boundarySampler;
        this.sampleCache = new Long2ObjectOpenHashMap<>();
        this.resolvedSamples = new LongOpenHashSet();
    }

    @Override
    public @Nullable FloatingIslandSample sample(int x, int z) {
        long key = Cache.key(x, z);
        if (resolvedSamples.contains(key)) {
            return sampleCache.get(key);
        }
        IrisBiome parent = parent(x, z);
        if (parent == null || parent.getFloatingChildBiomes() == null || parent.getFloatingChildBiomes().isEmpty()) {
            resolvedSamples.add(key);
            return null;
        }
        FloatingIslandSample sample = FloatingIslandSample.sample(parent, x, z, chunkHeight, baseSeed, data, engine, boundarySampler);
        resolvedSamples.add(key);
        if (sample != null) {
            sampleCache.put(key, sample);
        }
        return sample;
    }

    @Nullable IrisBiome parent(int x, int z) {
        return boundarySampler.parent(x, z);
    }
}
