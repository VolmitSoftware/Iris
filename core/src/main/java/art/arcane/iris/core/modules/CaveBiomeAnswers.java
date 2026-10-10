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

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import com.volmit.service.CaveBiomes;
import com.volmit.world.PackWorld;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the world's {@link CaveBiomes} provider says, kept for the world. The host asks it for every cave block it gives a
 * biome, so the provider is found once per world, each biome's minimum depth asked once and each overriding biome
 * loaded once.
 */
public final class CaveBiomeAnswers {
    private static final CaveBiomes NO_PROVIDER = new CaveBiomes() {
        @Override
        public Optional<String> overriddenBiome(PackWorld world, int x, int y, int z) {
            return Optional.empty();
        }

        @Override
        public int minDepthBelowSurface(PackWorld world, String biomeKey) {
            return 0;
        }
    };

    private final PackWorld world;
    private final IrisData data;
    private final CaveBiomes provider;
    private final Map<String, IrisBiome> overridingBiomes = new ConcurrentHashMap<>();
    private final Map<String, Integer> minDepths = new ConcurrentHashMap<>();

    CaveBiomeAnswers(Engine engine, CaveBiomes provider) {
        this.world = engine.getGeneration().world();
        this.data = engine.getData();
        this.provider = provider == null ? NO_PROVIDER : provider;
    }

    /** The pack biome the module puts at the position whatever the regions say; null where it overrides nothing or names a biome the pack lacks. {@code y} is the height above the dimension's bottom. */
    public IrisBiome overriddenBiome(int x, int y, int z) {
        Optional<String> key = provider.overriddenBiome(world, x, y, z);
        return key.isEmpty() ? null : overridingBiome(key.get());
    }

    /** How many blocks below the surface the caves of the pack biome {@code biomeKey} begin; 0 where nothing limits them. */
    public int minDepthBelowSurface(String biomeKey) {
        Integer depth = minDepths.get(biomeKey);
        if (depth != null) {
            return depth;
        }
        return minDepths.computeIfAbsent(biomeKey, key -> provider.minDepthBelowSurface(world, key));
    }

    private IrisBiome overridingBiome(String key) {
        IrisBiome biome = overridingBiomes.get(key);
        if (biome != null) {
            return biome;
        }
        biome = data.getBiomeLoader().load(key);
        if (biome != null) {
            overridingBiomes.putIfAbsent(key, biome);
        }
        return biome;
    }
}
