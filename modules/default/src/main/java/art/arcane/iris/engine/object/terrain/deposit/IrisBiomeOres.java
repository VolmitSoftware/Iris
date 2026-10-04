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

package art.arcane.iris.engine.object.terrain.deposit;

import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.data.cache.AtomicCache;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;

/**
 * Ore generation for {@link IrisBiome}. IrisBiome is Gson deserialized from pack JSON, so its
 * fields stay put and only the behavior lives here. Note that {@code IrisBiome.setOres(KList)}
 * stays on IrisBiome: it is a hand written Lombok setter override that resets these caches.
 */
public final class IrisBiomeOres {
    private IrisBiomeOres() {
    }

    public static PlatformBlockState generateOres(IrisBiome biome, int x, int y, int z, RNG rng, IrisData data, boolean surface) {
        KList<IrisOreGenerator> localOres = surface ? getSurfaceOres(biome) : getUndergroundOres(biome);
        return generateOres(localOres, x, y, z, rng, data);
    }

    public static PlatformBlockState generateSurfaceOres(IrisBiome biome, int x, int y, int z, RNG rng, IrisData data) {
        return generateOres(getSurfaceOres(biome), x, y, z, rng, data);
    }

    public static PlatformBlockState generateUndergroundOres(IrisBiome biome, int x, int y, int z, RNG rng, IrisData data) {
        return generateOres(getUndergroundOres(biome), x, y, z, rng, data);
    }

    public static boolean hasSurfaceOres(IrisBiome biome) {
        return !getSurfaceOres(biome).isEmpty();
    }

    public static boolean hasUndergroundOres(IrisBiome biome) {
        return !getUndergroundOres(biome).isEmpty();
    }

    public static KList<IrisOreGenerator> getSurfaceOreGenerators(IrisBiome biome) {
        return getOres(biome, true);
    }

    public static KList<IrisOreGenerator> getUndergroundOreGenerators(IrisBiome biome) {
        return getOres(biome, false);
    }

    public static IrisOreGeneratorBounds getSurfaceOreGeneratorBounds(IrisBiome biome) {
        // getIfPresent fast path: aquire allocates a capturing lambda even on a hit, and this
        // runs per column on the terrain hot path.
        IrisOreGeneratorBounds cached = biome.getSurfaceOreBoundsCache().getIfPresent();
        if (cached != null) {
            return cached;
        }
        return biome.getSurfaceOreBoundsCache().aquire(() -> IrisOreGeneratorBounds.of(getSurfaceOres(biome)));
    }

    public static IrisOreGeneratorBounds getUndergroundOreGeneratorBounds(IrisBiome biome) {
        IrisOreGeneratorBounds cached = biome.getUndergroundOreBoundsCache().getIfPresent();
        if (cached != null) {
            return cached;
        }
        return biome.getUndergroundOreBoundsCache().aquire(() -> IrisOreGeneratorBounds.of(getUndergroundOres(biome)));
    }

    private static KList<IrisOreGenerator> getSurfaceOres(IrisBiome biome) {
        return getOres(biome, true);
    }

    private static KList<IrisOreGenerator> getUndergroundOres(IrisBiome biome) {
        return getOres(biome, false);
    }

    private static KList<IrisOreGenerator> getOres(IrisBiome biome, boolean surface) {
        AtomicCache<KList<IrisOreGenerator>> oreCache = surface ? biome.getSurfaceOreCache() : biome.getUndergroundOreCache();
        KList<IrisOreGenerator> cached = oreCache.getIfPresent();

        if (cached != null) {
            return cached;
        }

        return oreCache.aquire(() -> {
            KList<IrisOreGenerator> filtered = new KList<>();
            KList<IrisOreGenerator> localOres = biome.getOres();
            int oreCount = localOres.size();
            for (int oreIndex = 0; oreIndex < oreCount; oreIndex++) {
                IrisOreGenerator oreGenerator = localOres.get(oreIndex);
                if (oreGenerator.isGenerateSurface() == surface) {
                    filtered.add(oreGenerator);
                }
            }

            return filtered;
        });
    }

    private static PlatformBlockState generateOres(KList<IrisOreGenerator> localOres, int x, int y, int z, RNG rng, IrisData data) {
        if (localOres.isEmpty()) {
            return null;
        }

        int oreCount = localOres.size();
        for (int oreIndex = 0; oreIndex < oreCount; oreIndex++) {
            IrisOreGenerator oreGenerator = localOres.get(oreIndex);
            PlatformBlockState ore = oreGenerator.generate(x, y, z, rng, data);
            if (ore != null) {
                return ore;
            }
        }
        return null;
    }
}
