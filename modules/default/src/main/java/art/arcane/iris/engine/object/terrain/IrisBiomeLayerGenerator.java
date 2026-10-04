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

package art.arcane.iris.engine.object.terrain;

import art.arcane.iris.engine.object.placement.IrisObjectPlacement;
import art.arcane.iris.engine.object.placement.IrisSlopeClip;
import art.arcane.iris.engine.object.prefab.IrisObject;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.biome.IrisBiomeGeneratorLink;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.data.cache.AtomicCache;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.data.B;
import art.arcane.iris.util.project.noise.CNG;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;

/**
 * Layer (block palette) generation for {@link IrisBiome}. IrisBiome is Gson deserialized from pack
 * JSON, so its fields stay put and only the behavior lives here.
 */
public final class IrisBiomeLayerGenerator {
    private static final class States {
        private static final PlatformBlockState BARRIER = B.getState("BARRIER");
    }

    private IrisBiomeLayerGenerator() {
    }

    public static KList<PlatformBlockState> generateLayers(IrisBiome biome, IrisDimension dim, double wx, double wz, RNG random, int maxDepth, int height, IrisData rdata, IrisComplex complex) {
        if (biome.isLockLayers()) {
            return generateLockedLayers(biome, wx, wz, random, maxDepth, height, rdata, complex);
        }

        KList<PlatformBlockState> data = new KList<>();

        if (maxDepth <= 0) {
            return data;
        }

        KList<IrisBiomePaletteLayer> layers = biome.getLayers();
        int layerCount = layers.size();

        if (layerCount <= 0) {
            return data;
        }

        KList<CNG> heightGenerators = getLayerHeightGenerators(biome, random, rdata);

        for (int i = 0; i < layerCount; i++) {
            IrisBiomePaletteLayer layer = layers.get(i);
            double zoom = layer.getZoom();
            CNG hgen = heightGenerators.get(i);
            double d = hgen.fit(layer.getMinHeight(), layer.getMaxHeight(), wx / zoom, wz / zoom);

            IrisSlopeClip sc = layer.getSlopeCondition();

            if (!sc.isDefault()) {
                if (!sc.isValid(complex.getSlopeStream().get(wx, wz))) {
                    d = 0;
                }
            }

            if (d <= 0) {
                continue;
            }

            for (int j = 0; j < d; j++) {
                if (data.size() >= maxDepth) {
                    break;
                }

                try {
                    data.add(layer.get(random, i + j, (wx + j) / zoom, j, (wz - j) / zoom, rdata));
                } catch (Throwable e) {
                    IrisLogging.reportError(e);
                }
            }

            if (data.size() >= maxDepth) {
                break;
            }

            if (dim.isExplodeBiomePalettes()) {
                for (int j = 0; j < dim.getExplodeBiomePaletteSize(); j++) {
                    data.add(States.BARRIER);

                    if (data.size() >= maxDepth) {
                        break;
                    }
                }
            }
        }

        return data;
    }

    public static KList<PlatformBlockState> generateCeilingLayers(IrisBiome biome, IrisDimension dim, double wx, double wz, RNG random, int maxDepth, int height, IrisData rdata, IrisComplex complex) {
        KList<PlatformBlockState> data = new KList<>();

        if (maxDepth <= 0) {
            return data;
        }

        KList<IrisBiomePaletteLayer> ceilingLayers = biome.getCaveCeilingLayers();
        int layerCount = ceilingLayers.size();

        if (layerCount <= 0) {
            return data;
        }

        KList<CNG> heightGenerators = getLayerHeightGenerators(biome, random, rdata);
        // Ceiling layers reuse the surface-layer height generators, so entries beyond layers.size() have no generator; skip them.
        int usableLayers = Math.min(layerCount, heightGenerators.size());

        for (int i = 0; i < usableLayers; i++) {
            IrisBiomePaletteLayer layer = ceilingLayers.get(i);
            double zoom = layer.getZoom();
            CNG hgen = heightGenerators.get(i);
            double d = hgen.fit(layer.getMinHeight(), layer.getMaxHeight(), wx / zoom, wz / zoom);

            if (d <= 0) {
                continue;
            }

            for (int j = 0; j < d; j++) {
                if (data.size() >= maxDepth) {
                    break;
                }

                try {
                    data.add(layer.get(random, i + j, (wx + j) / zoom, j, (wz - j) / zoom, rdata));
                } catch (Throwable e) {
                    IrisLogging.reportError(e);
                }
            }

            if (data.size() >= maxDepth) {
                break;
            }

            if (dim.isExplodeBiomePalettes()) {
                for (int j = 0; j < dim.getExplodeBiomePaletteSize(); j++) {
                    data.add(States.BARRIER);

                    if (data.size() >= maxDepth) {
                        break;
                    }
                }
            }
        }

        return data;
    }

    public static KList<PlatformBlockState> generateLockedLayers(IrisBiome biome, double wx, double wz, RNG random, int maxDepthf, int height, IrisData rdata, IrisComplex complex) {
        KList<PlatformBlockState> data = new KList<>();
        KList<PlatformBlockState> real = new KList<>();
        int maxDepth = Math.min(maxDepthf, biome.getLockLayersMax());
        if (maxDepth <= 0) {
            return data;
        }

        KList<IrisBiomePaletteLayer> layers = biome.getLayers();
        int layerCount = layers.size();

        if (layerCount > 0) {
            KList<CNG> heightGenerators = getLayerHeightGenerators(biome, random, rdata);

            for (int i = 0; i < layerCount; i++) {
                IrisBiomePaletteLayer layer = layers.get(i);
                double zoom = layer.getZoom();
                CNG hgen = heightGenerators.get(i);
                double d = hgen.fit(layer.getMinHeight(), layer.getMaxHeight(), wx / zoom, wz / zoom);

                IrisSlopeClip sc = layer.getSlopeCondition();

                if (!sc.isDefault()) {
                    if (!sc.isValid(complex.getSlopeStream().get(wx, wz))) {
                        d = 0;
                    }
                }

                if (d <= 0) {
                    continue;
                }

                for (int j = 0; j < d; j++) {
                    try {
                        data.add(layer.get(random, i + j, (wx + j) / zoom, j, (wz - j) / zoom, rdata));
                    } catch (Throwable e) {
                        IrisLogging.reportError(e);
                    }
                }
            }
        }

        if (data.isEmpty()) {
            return real;
        }

        for (int i = 0; i < maxDepth; i++) {
            int offset = (512 - height) - i;
            int index = offset % data.size();
            real.add(data.get(Math.max(index, 0)));
        }

        return real;
    }

    public static KList<PlatformBlockState> generateSeaLayers(IrisBiome biome, double wx, double wz, RNG random, int maxDepth, IrisData rdata) {
        KList<PlatformBlockState> data = new KList<>();

        KList<IrisBiomePaletteLayer> seaLayers = biome.getSeaLayers();
        int layerCount = seaLayers.size();

        if (layerCount <= 0) {
            return data;
        }

        KList<CNG> heightGenerators = getLayerSeaHeightGenerators(biome, random, rdata);

        for (int i = 0; i < layerCount; i++) {
            IrisBiomePaletteLayer layer = seaLayers.get(i);
            double zoom = layer.getZoom();
            CNG hgen = heightGenerators.get(i);
            int d = hgen.fit(layer.getMinHeight(), layer.getMaxHeight(), wx / zoom, wz / zoom);

            if (d < 0) {
                continue;
            }

            for (int j = 0; j < d; j++) {
                if (data.size() >= maxDepth) {
                    break;
                }

                try {
                    data.add(layer.get(random, i + j, (wx + j) / zoom, j, (wz - j) / zoom, rdata));
                } catch (Throwable e) {
                    IrisLogging.reportError(e);
                }
            }

            if (data.size() >= maxDepth) {
                break;
            }
        }

        return data;
    }

    public static KList<CNG> getLayerHeightGenerators(IrisBiome biome, RNG rng, IrisData rdata) {
        AtomicCache<KList<CNG>> cache = biome.getLayerHeightGenerators();
        KList<CNG> cached = cache.getIfPresent();

        if (cached != null) {
            return cached;
        }

        return cache.aquire(() ->
        {
            KList<CNG> layerHeightGenerators = new KList<>();

            int m = 7235;

            for (IrisBiomePaletteLayer i : biome.getLayers()) {
                layerHeightGenerators.add(i.getHeightGenerator(rng.nextParallelRNG((m++) * m * m * m), rdata));
            }

            return layerHeightGenerators;
        });
    }

    public static KList<CNG> getLayerSeaHeightGenerators(IrisBiome biome, RNG rng, IrisData data) {
        AtomicCache<KList<CNG>> cache = biome.getLayerSeaHeightGenerators();
        KList<CNG> cached = cache.getIfPresent();

        if (cached != null) {
            return cached;
        }

        return cache.aquire(() ->
        {
            KList<CNG> layerSeaHeightGenerators = new KList<>();

            int m = 7735;

            for (IrisBiomePaletteLayer i : biome.getSeaLayers()) {
                layerSeaHeightGenerators.add(i.getHeightGenerator(rng.nextParallelRNG((m++) * m * m * m), data));
            }

            return layerSeaHeightGenerators;
        });
    }

    public static PlatformBlockState getSurfaceBlock(IrisBiome biome, int x, int z, RNG rng, IrisData idm) {
        KList<IrisBiomePaletteLayer> layers = biome.getLayers();

        if (layers.isEmpty()) {
            return B.getState("AIR");
        }

        return layers.get(0).get(rng, x, 0, z, idm);
    }

    public static int getMaxHeight(IrisBiome biome, Engine engine) {
        return biome.getMaxHeight().aquire(() ->
        {
            int maxHeight = 0;

            for (IrisBiomeGeneratorLink i : biome.getGenerators()) {
                maxHeight += i.getMax();
            }

            return maxHeight;
        });
    }

    public static int getMaxWithObjectHeight(IrisBiome biome, IrisData data, Engine engine) {
        return biome.getMaxWithObjectHeight().aquire(() ->
        {
            int maxHeight = 0;

            for (IrisBiomeGeneratorLink i : biome.getGenerators()) {
                maxHeight += i.getMax();
            }

            int gg = 0;

            for (IrisObjectPlacement i : biome.getObjects()) {
                for (IrisObject j : data.getObjectLoader().loadAll(i.getPlace())) {
                    gg = Math.max(gg, j.getH());
                }
            }

            return maxHeight + gg + 3;
        });
    }
}
