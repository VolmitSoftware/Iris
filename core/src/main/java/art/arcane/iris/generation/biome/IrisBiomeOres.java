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

import art.arcane.iris.generation.decoration.IrisOreGenerator;
import art.arcane.iris.generation.decoration.IrisOreGeneratorBounds;
import art.arcane.iris.generation.decoration.IrisProceduralBlocks;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.cache.AtomicCache;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.generation.block.B;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KSet;
import art.arcane.volmlib.util.math.RNG;

/**
 * Ore generation for {@link IrisBiome}. IrisBiome is Gson deserialized from pack JSON, so its
 * fields stay put and only the behavior lives here. Note that {@code IrisBiome.setOres(KList)}
 * stays on IrisBiome: it is a hand written Lombok setter override that resets these caches.
 */
final class IrisBiomeOres {
    private IrisBiomeOres() {
    }

    static PlatformBlockState generateOres(IrisBiome biome, int x, int y, int z, RNG rng, IrisData data, boolean surface) {
        KList<IrisOreGenerator> localOres = surface ? getSurfaceOres(biome) : getUndergroundOres(biome);
        return generateOres(localOres, x, y, z, rng, data);
    }

    static PlatformBlockState generateSurfaceOres(IrisBiome biome, int x, int y, int z, RNG rng, IrisData data) {
        return generateOres(getSurfaceOres(biome), x, y, z, rng, data);
    }

    static PlatformBlockState generateUndergroundOres(IrisBiome biome, int x, int y, int z, RNG rng, IrisData data) {
        return generateOres(getUndergroundOres(biome), x, y, z, rng, data);
    }

    static boolean hasSurfaceOres(IrisBiome biome) {
        return !getSurfaceOres(biome).isEmpty();
    }

    static boolean hasUndergroundOres(IrisBiome biome) {
        return !getUndergroundOres(biome).isEmpty();
    }

    static boolean hasSurfaceOreReplaceableBlocks(IrisBiome biome) {
        return biome.getSurfaceOreReplaceableBlocks() != null;
    }

    static boolean canReplaceSurfaceOre(IrisBiome biome, PlatformBlockState state) {
        if (!hasSurfaceOreReplaceableBlocks(biome)) {
            return true;
        }
        return biome.getSurfaceOreReplaceableBlockData()
                .aquire(() -> resolveSurfaceOreReplaceableBlocks(biome))
                .contains(IrisProceduralBlocks.materialKey(state));
    }

    static KList<IrisOreGenerator> getSurfaceOreGenerators(IrisBiome biome) {
        return getOres(biome, true);
    }

    static KList<IrisOreGenerator> getUndergroundOreGenerators(IrisBiome biome) {
        return getOres(biome, false);
    }

    static IrisOreGeneratorBounds getSurfaceOreGeneratorBounds(IrisBiome biome) {
        // getIfPresent fast path: aquire allocates a capturing lambda even on a hit, and this
        // runs per column on the terrain hot path.
        IrisOreGeneratorBounds cached = biome.getSurfaceOreBoundsCache().getIfPresent();
        if (cached != null) {
            return cached;
        }
        return biome.getSurfaceOreBoundsCache().aquire(() -> IrisOreGeneratorBounds.of(getSurfaceOres(biome)));
    }

    static IrisOreGeneratorBounds getUndergroundOreGeneratorBounds(IrisBiome biome) {
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

    private static KSet<String> resolveSurfaceOreReplaceableBlocks(IrisBiome biome) {
        KSet<String> resolved = new KSet<>();
        for (String key : biome.getSurfaceOreReplaceableBlocks()) {
            PlatformBlockState state = B.getStateOrNull(key, false);
            if (state != null) {
                resolved.add(IrisProceduralBlocks.materialKey(state));
            }
        }
        return resolved;
    }
}
