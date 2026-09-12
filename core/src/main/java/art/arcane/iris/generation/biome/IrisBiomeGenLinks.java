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

package art.arcane.iris.generation.biome;

import art.arcane.iris.generation.cache.AtomicCache;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.volmlib.util.collection.KMap;

/**
 * Generator link lookups for {@link IrisBiome}. IrisBiome is Gson deserialized from pack JSON, so
 * its fields stay put and only the behavior lives here. These are resolved per generation column,
 * so the caches are read through {@link AtomicCache#getIfPresent()} first.
 */
final class IrisBiomeGenLinks {
    private IrisBiomeGenLinks() {
    }

    static double getGenLinkMax(IrisBiome biome, String loadKey, Engine engine) {
        if (loadKey == null || loadKey.isBlank()) {
            return 0;
        }

        Integer v = maxIndex(biome).get(loadKey);

        return v == null ? 0 : v;
    }

    static double getGenLinkMin(IrisBiome biome, String loadKey, Engine engine) {
        if (loadKey == null || loadKey.isBlank()) {
            return 0;
        }

        Integer v = minIndex(biome).get(loadKey);

        return v == null ? 0 : v;
    }

    static IrisBiomeGeneratorLink getGenLink(IrisBiome biome, String loadKey) {
        if (loadKey == null || loadKey.isBlank()) {
            return null;
        }

        return linkIndex(biome).get(loadKey);
    }

    private static KMap<String, Integer> maxIndex(IrisBiome biome) {
        AtomicCache<KMap<String, Integer>> cache = biome.getGenCacheMax();
        KMap<String, Integer> cached = cache.getIfPresent();

        if (cached != null) {
            return cached;
        }

        return cache.aquire(() ->
        {
            KMap<String, Integer> l = new KMap<>();

            for (IrisBiomeGeneratorLink i : biome.getGenerators()) {
                String generatorKey = i.getGenerator();
                if (generatorKey == null || generatorKey.isBlank()) {
                    continue;
                }

                l.put(generatorKey, i.getMax());

            }

            return l;
        });
    }

    private static KMap<String, Integer> minIndex(IrisBiome biome) {
        AtomicCache<KMap<String, Integer>> cache = biome.getGenCacheMin();
        KMap<String, Integer> cached = cache.getIfPresent();

        if (cached != null) {
            return cached;
        }

        return cache.aquire(() ->
        {
            KMap<String, Integer> l = new KMap<>();

            for (IrisBiomeGeneratorLink i : biome.getGenerators()) {
                String generatorKey = i.getGenerator();
                if (generatorKey == null || generatorKey.isBlank()) {
                    continue;
                }

                l.put(generatorKey, i.getMin());
            }

            return l;
        });
    }

    private static KMap<String, IrisBiomeGeneratorLink> linkIndex(IrisBiome biome) {
        AtomicCache<KMap<String, IrisBiomeGeneratorLink>> cache = biome.getGenCache();
        KMap<String, IrisBiomeGeneratorLink> cached = cache.getIfPresent();

        if (cached != null) {
            return cached;
        }

        return cache.aquire(() ->
        {
            KMap<String, IrisBiomeGeneratorLink> l = new KMap<>();

            for (IrisBiomeGeneratorLink i : biome.getGenerators()) {
                String generatorKey = i.getGenerator();
                if (generatorKey == null || generatorKey.isBlank()) {
                    continue;
                }

                l.put(generatorKey, i);
            }

            return l;
        });
    }
}
