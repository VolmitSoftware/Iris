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

import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisSlopeClip;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.structure.object.IrisObjectPlacement;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.cache.AtomicCache;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.generation.block.B;
import art.arcane.iris.generation.block.BoundBlockState;
import art.arcane.volmlib.util.noise.CNG;
import art.arcane.volmlib.util.stream.ProceduralStream;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;

/**
 * Layer (block palette) generation for {@link IrisBiome}. IrisBiome is Gson deserialized from pack
 * JSON, so its fields stay put and only the behavior lives here.
 */
final class IrisBiomeLayerGenerator {
    private static final BoundBlockState BARRIER = BoundBlockState.of("BARRIER");

    private IrisBiomeLayerGenerator() {
    }

    static KList<PlatformBlockState> generateLayers(IrisBiome biome, IrisDimension dim, double wx, double wz, RNG random, int maxDepth, int height, IrisData rdata, IrisComplex complex) {
        return generateLayers(biome, dim, wx, wz, random, maxDepth, height, rdata, complex, null);
    }

    static KList<PlatformBlockState> generateLayers(IrisBiome biome, IrisDimension dim, double wx, double wz, RNG random, int maxDepth, int height, IrisData rdata, ProceduralStream<Double> slopeStream) {
        return generateLayers(biome, dim, wx, wz, random, maxDepth, height, rdata, null, slopeStream);
    }

    private static KList<PlatformBlockState> generateLayers(IrisBiome biome, IrisDimension dim, double wx, double wz, RNG random, int maxDepth, int height, IrisData rdata, IrisComplex complex, ProceduralStream<Double> slopeStream) {
        if (biome.isLockLayers()) {
            return generateLockedLayers(biome, wx, wz, random, maxDepth, height, rdata, complex, slopeStream);
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
        double surfaceSlope = 0D;
        boolean sampledSlope = false;

        for (int i = 0; i < layerCount; i++) {
            IrisBiomePaletteLayer layer = layers.get(i);
            double zoom = layer.getZoom();
            CNG hgen = heightGenerators.get(i);
            int d = hgen.fit(layer.getMinHeight(), layer.getMaxHeight(), wx / zoom, wz / zoom);

            IrisSlopeClip sc = layer.getSlopeCondition();

            if (!sc.isDefault()) {
                if (!sampledSlope) {
                    surfaceSlope = resolveSurfaceSlope(complex, slopeStream, wx, height, wz);
                    sampledSlope = true;
                }
                if (!sc.isValid(surfaceSlope)) {
                    d = 0;
                }
            }

            if (d <= 0) {
                continue;
            }

            appendLayer(data, layer, i, d, wx, wz, random, maxDepth, rdata);

            if (data.size() >= maxDepth) {
                break;
            }

            if (dim.isExplodeBiomePalettes()) {
                for (int j = 0; j < dim.getExplodeBiomePaletteSize(); j++) {
                    data.add(BARRIER.get());

                    if (data.size() >= maxDepth) {
                        break;
                    }
                }
            }
        }

        return data;
    }

    static KList<PlatformBlockState> generateCeilingLayers(IrisBiome biome, IrisDimension dim, double wx, double wz, RNG random, int maxDepth, int height, IrisData rdata, IrisComplex complex) {
        KList<PlatformBlockState> data = new KList<>();

        if (maxDepth <= 0) {
            return data;
        }

        KList<IrisBiomePaletteLayer> ceilingLayers = biome.getCaveCeilingLayers();
        int layerCount = ceilingLayers.size();

        if (layerCount <= 0) {
            return data;
        }

        KList<CNG> heightGenerators = getHeightGenerators(
                biome.getLayerCeilingHeightGenerators(), ceilingLayers, 7235, random, rdata);

        for (int i = 0; i < layerCount; i++) {
            IrisBiomePaletteLayer layer = ceilingLayers.get(i);
            double zoom = layer.getZoom();
            CNG hgen = heightGenerators.get(i);
            int d = hgen.fit(layer.getMinHeight(), layer.getMaxHeight(), wx / zoom, wz / zoom);

            if (d <= 0) {
                continue;
            }

            appendLayer(data, layer, i, d, wx, wz, random, maxDepth, rdata);

            if (data.size() >= maxDepth) {
                break;
            }

            if (dim.isExplodeBiomePalettes()) {
                for (int j = 0; j < dim.getExplodeBiomePaletteSize(); j++) {
                    data.add(BARRIER.get());

                    if (data.size() >= maxDepth) {
                        break;
                    }
                }
            }
        }

        return data;
    }

    static KList<PlatformBlockState> generateLockedLayers(IrisBiome biome, double wx, double wz, RNG random, int maxDepthf, int height, IrisData rdata, IrisComplex complex) {
        return generateLockedLayers(biome, wx, wz, random, maxDepthf, height, rdata, complex, null);
    }

    private static KList<PlatformBlockState> generateLockedLayers(IrisBiome biome, double wx, double wz, RNG random, int maxDepthf, int height, IrisData rdata, IrisComplex complex, ProceduralStream<Double> slopeStream) {
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
            double surfaceSlope = 0D;
            boolean sampledSlope = false;

            for (int i = 0; i < layerCount; i++) {
                IrisBiomePaletteLayer layer = layers.get(i);
                double zoom = layer.getZoom();
                CNG hgen = heightGenerators.get(i);
                int d = hgen.fit(layer.getMinHeight(), layer.getMaxHeight(), wx / zoom, wz / zoom);

                IrisSlopeClip sc = layer.getSlopeCondition();

                if (!sc.isDefault()) {
                    if (!sampledSlope) {
                        surfaceSlope = resolveSurfaceSlope(complex, slopeStream, wx, height, wz);
                        sampledSlope = true;
                    }
                    if (!sc.isValid(surfaceSlope)) {
                        d = 0;
                    }
                }

                if (d <= 0) {
                    continue;
                }

                appendLayer(data, layer, i, d, wx, wz, random, Integer.MAX_VALUE, rdata);
            }
        }

        if (data.isEmpty()) {
            return real;
        }

        for (int i = 0; i < maxDepth; i++) {
            long offset = 512L - height - i;
            real.add(data.get(Math.floorMod(offset, data.size())));
        }

        return real;
    }

    private static double resolveSurfaceSlope(
            IrisComplex complex,
            ProceduralStream<Double> slopeStream,
            double x,
            int height,
            double z
    ) {
        if (slopeStream != null) {
            return slopeStream.getDouble(x, z);
        }
        return complex.hasTerrain3D()
                ? complex.terrainSurfaceSlope((int) Math.floor(x), height, (int) Math.floor(z))
                : complex.getSlopeStream().getDouble(x, z);
    }

    static KList<PlatformBlockState> generateSeaLayers(IrisBiome biome, double wx, double wz, RNG random, int maxDepth, IrisData rdata) {
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

            appendLayer(data, layer, i, d, wx, wz, random, maxDepth, rdata);

            if (data.size() >= maxDepth) {
                break;
            }
        }

        return data;
    }

    static KList<CNG> getLayerHeightGenerators(IrisBiome biome, RNG rng, IrisData rdata) {
        return getHeightGenerators(biome.getLayerHeightGenerators(), biome.getLayers(), 7235, rng, rdata);
    }

    static KList<CNG> getLayerSeaHeightGenerators(IrisBiome biome, RNG rng, IrisData data) {
        return getHeightGenerators(biome.getLayerSeaHeightGenerators(), biome.getSeaLayers(), 7735, rng, data);
    }

    private static KList<CNG> getHeightGenerators(AtomicCache<KList<CNG>> cache,
                                                  KList<IrisBiomePaletteLayer> layers,
                                                  int seedOffset, RNG rng, IrisData data) {
        KList<CNG> cached = cache.getIfPresent();

        if (cached != null) {
            return cached;
        }

        return cache.aquire(() ->
        {
            KList<CNG> layerHeightGenerators = new KList<>();

            int m = seedOffset;

            for (IrisBiomePaletteLayer layer : layers) {
                layerHeightGenerators.add(layer.getHeightGenerator(rng.nextParallelRNG((m++) * m * m * m), data));
            }

            return layerHeightGenerators;
        });
    }

    private static void appendLayer(KList<PlatformBlockState> blocks, IrisBiomePaletteLayer layer,
                                    int layerIndex, int thickness, double x, double z,
                                    RNG random, int maxDepth, IrisData data) {
        double zoom = layer.getZoom();
        for (int offset = 0; offset < thickness && blocks.size() < maxDepth; offset++) {
            try {
                blocks.add(layer.get(random, layerIndex + offset,
                        (x + offset) / zoom, offset, (z - offset) / zoom, data));
            } catch (Throwable error) {
                IrisLogging.reportError(error);
            }
        }
    }

    static PlatformBlockState getSurfaceBlock(IrisBiome biome, int x, int z, RNG rng, IrisData idm) {
        KList<IrisBiomePaletteLayer> layers = biome.getLayers();

        if (layers.isEmpty()) {
            return B.getState("AIR");
        }

        return layers.get(0).get(rng, x, 0, z, idm);
    }

    static int getMaxHeight(IrisBiome biome, Engine engine) {
        return biome.getMaxHeight().aquire(() ->
        {
            int maxHeight = 0;

            for (IrisBiomeGeneratorLink i : biome.getGenerators()) {
                maxHeight += i.getMax();
            }

            return maxHeight;
        });
    }

    static int getMaxWithObjectHeight(IrisBiome biome, IrisData data, Engine engine) {
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
