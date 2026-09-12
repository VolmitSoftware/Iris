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

package art.arcane.iris.generation.stage;

import art.arcane.iris.generation.runtime.DimensionStackContext;
import art.arcane.iris.generation.runtime.DimensionStackLayout;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.EngineAssignedActuator;
import art.arcane.iris.world.history.TransitionGenerationPlan;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.biome.IrisBiomeCustom;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBiome;
import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.iris.generation.context.ChunkedDataCache;
import art.arcane.volmlib.util.hunk.Hunk;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.documentation.BlockCoordinates;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterBiomeInject;
import art.arcane.volmlib.util.matter.slices.BiomeInjectMatter;
import art.arcane.volmlib.util.scheduling.PrecisionStopwatch;

import java.util.List;
import java.util.Objects;

public class IrisBiomeActuator extends EngineAssignedActuator<PlatformBiome> {
    private final RNG rng;
    private final KMap<String, ResolvedBiome> resolvedBiomes = new KMap<>();
    private final KMap<String, ResolvedBiome> resolvedPhysicalBiomes = new KMap<>();

    public IrisBiomeActuator(Engine engine) {
        super(engine, "Biome");
        rng = new RNG(engine.getSeedManager().getBiome());
    }

    @BlockCoordinates
    @Override
    public void onActuate(int x, int z, Hunk<PlatformBiome> h, boolean multicore, ChunkContext context) {
        PrecisionStopwatch p = PrecisionStopwatch.start();
        int width = h.getWidth();
        int depth = h.getDepth();
        int height = h.getHeight();
        Engine engine = getEngine();
        Mantle<Matter> mantle = context.isSpeculativeTerrain() ? null : engine.getMantle().getMantle();
        ChunkedDataCache<IrisBiome> biomeCache = context.getBiome();
        IrisComplex complex = context.getComplex();
        DimensionStackContext dimensionStackContext = engine.getDimensionStackContext();

        for (int xf = 0; xf < width; xf++) {
            for (int zf = 0; zf < depth; zf++) {
                int worldX = x + xf;
                int worldZ = z + zf;
                IrisBiome biome = biomeCache.get(xf, zf);
                ResolvedBiome resolved = resolve(biomeKey(
                        biome,
                        getDimension(),
                        engine,
                        worldX,
                        0,
                        worldZ
                ));
                PlatformBiome platformBiome = resolved.biome();
                writeColumn(h, mantle, xf, zf, worldX, worldZ, height, resolved);

                if (dimensionStackContext != null) {
                    PlatformBiome bottomBiome = platformBiome == null
                            ? h.getRaw(xf, 0, zf)
                            : platformBiome;
                    applyDimensionStackBiomes(
                            xf,
                            zf,
                            worldX,
                            worldZ,
                            h,
                            mantle,
                            engine,
                            new ResolvedBiome(bottomBiome, resolved.matter()),
                            context.getDimensionStackLayout(xf, zf)
                    );
                }
                writeHistoricalColumn(h, mantle, xf, zf, worldX, worldZ, height, engine, complex);
            }
        }
        engine.getMetrics().getBiome().put(p.getMilliseconds());
    }

    public static void publishNaturalMetadata(Engine engine, int x, int z, Hunk<PlatformBiome> biomes,
                                               ChunkContext context) {
        if (context.isSpeculativeTerrain() || !context.getComplex().allowsMantleChunkWrite(x >> 4, z >> 4)) {
            return;
        }
        Mantle<Matter> mantle = engine.getMantle().getMantle();
        MantleChunk<Matter> chunk = mantle.getChunk(x >> 4, z >> 4).use();
        try {
            synchronized (chunk) {
                IrisDimensionStackActuator.clearNaturalMetadata(chunk, context, biomes.getHeight());
                chunk.deleteSlices(MatterBiomeInject.class);
                for (int localX = 0; localX < biomes.getWidth(); localX += 4) {
                    for (int localZ = 0; localZ < biomes.getDepth(); localZ += 4) {
                        for (int y = 0; y < biomes.getHeight(); y += 4) {
                            PlatformBiome biome = biomes.getRaw(localX, y, localZ);
                            if (biome != null) {
                                mantle.set(x + localX, y, z + localZ, BiomeInjectMatter.get(biome.key()));
                            }
                        }
                    }
                }
            }
        } finally {
            chunk.release();
        }
    }

    private void writeHistoricalColumn(
            Hunk<PlatformBiome> output,
            Mantle<Matter> mantle,
            int localX,
            int localZ,
            int worldX,
            int worldZ,
            int height,
            Engine engine,
            IrisComplex complex
    ) {
        TransitionGenerationPlan transitionPlan = complex.getTransitionGenerationPlan();
        if (transitionPlan == null) {
            return;
        }
        TransitionGenerationPlan.TerrainSample terrainSample = transitionPlan.terrainSampleAt(worldX, worldZ);
        if (terrainSample.newEpochWeight() == 1D) {
            return;
        }
        int minimumWorldY = engine.getMinHeight();
        String activeKey = transitionPlan.historicalPhysicalBiomeKeyAt(worldX, minimumWorldY, worldZ, terrainSample).orElse(null);
        int rangeStart = 0;
        for (int internalY = 1; internalY <= height; internalY++) {
            String nextKey = internalY == height
                    ? null
                    : transitionPlan.historicalPhysicalBiomeKeyAt(worldX, minimumWorldY + internalY, worldZ, terrainSample).orElse(null);
            if (Objects.equals(activeKey, nextKey)) {
                continue;
            }
            if (activeKey != null) {
                applyBiomeRange(
                        localX,
                        localZ,
                        worldX,
                        worldZ,
                        rangeStart,
                        internalY - 1,
                        output,
                        mantle,
                        resolvePhysicalKey(activeKey)
                );
            }
            activeKey = nextKey;
            rangeStart = internalY;
        }
    }

    private void writeColumn(
            Hunk<PlatformBiome> output,
            Mantle<Matter> mantle,
            int localX,
            int localZ,
            int worldX,
            int worldZ,
            int height,
            ResolvedBiome resolved
    ) {
        if (resolved.biome() != null) {
            output.set(localX, 0, localZ, localX, height - 1, localZ, resolved.biome());
        }
        if (mantle != null) {
            mantle.set(worldX, 0, worldZ, resolved.matter());
        }
    }

    private void applyDimensionStackBiomes(
            int localX,
            int localZ,
            int worldX,
            int worldZ,
            Hunk<PlatformBiome> output,
            Mantle<Matter> mantle,
            Engine engine,
            ResolvedBiome bottom,
            DimensionStackLayout layout
    ) {
        List<DimensionStackLayout.Layer> layers = layout.layersBottomToTop();
        for (int layerIndex = 1; layerIndex < layers.size(); layerIndex++) {
            DimensionStackLayout.Layer lower = layers.get(layerIndex - 1);
            DimensionStackLayout.Layer layer = layers.get(layerIndex);
            int gapMinY = (int) Math.max(0L, (long) lower.contentTopY() + 1L);
            int gapMaxY = (int) Math.min(
                    (long) output.getHeight() - 1L,
                    (long) layer.localBaseY() - 1L
            );
            applyBiomeRange(
                    localX,
                    localZ,
                    worldX,
                    worldZ,
                    gapMinY,
                    gapMaxY,
                    output,
                    mantle,
                    bottom
            );
            if (!layer.visible()) {
                continue;
            }
            ResolvedBiome resolved = bottom;
            if (layer.biome() != null) {
                IrisDimension dimension = layer.terrainContext().getDimension();
                resolved = resolve(biomeKey(
                        layer.biome(),
                        dimension,
                        engine,
                        worldX,
                        layer.clippedSurfaceY(),
                        worldZ
                ));
            }
            applyBiomeRange(
                    localX,
                    localZ,
                    worldX,
                    worldZ,
                    layer.renderMinY(),
                    layer.renderMaxY(),
                    output,
                    mantle,
                    resolved
            );
        }
    }

    private void applyBiomeRange(
            int localX,
            int localZ,
            int worldX,
            int worldZ,
            int minimumY,
            int maximumY,
            Hunk<PlatformBiome> output,
            Mantle<Matter> mantle,
            ResolvedBiome resolved
    ) {
        if (minimumY > maximumY) {
            return;
        }
        if (mantle != null) {
            clearBiomeMatterRange(mantle, worldX, worldZ, minimumY, maximumY);
        }
        if (resolved.biome() != null) {
            output.set(
                    localX,
                    minimumY,
                    localZ,
                    localX,
                    maximumY,
                    localZ,
                    resolved.biome()
            );
        }
        if (mantle != null && resolved.matter() != null) {
            mantle.set(worldX, minimumY, worldZ, resolved.matter());
        }
    }

    static void clearBiomeMatterRange(
            Mantle<Matter> mantle,
            int worldX,
            int worldZ,
            int minimumY,
            int maximumY
    ) {
        for (int y = minimumY; y <= maximumY; y++) {
            mantle.remove(worldX, y, worldZ, MatterBiomeInject.class);
        }
    }

    private String biomeKey(
            IrisBiome biome,
            IrisDimension dimension,
            Engine engine,
            int x,
            int y,
            int z
    ) {
        if (biome.isCustom()) {
            IrisBiomeCustom custom = biome.getCustomBiome(rng, engine, x, y, z);
            return engine.getData().customBiomeResourceKey(dimension, custom);
        }
        return biome.getSkyBiomeKey(rng, engine, x, y, z);
    }

    private ResolvedBiome resolve(String key) {
        ResolvedBiome cached = key == null ? null : resolvedBiomes.get(key);
        if (cached != null) {
            return cached;
        }

        IrisPlatform platform = IrisPlatforms.get();
        PlatformBiome biome = platform.registries().biome(key);
        ResolvedBiome resolved = new ResolvedBiome(
                biome,
                BiomeInjectMatter.get(platform.biomeWriter().biomeIdFor(key))
        );
        if (key != null && biome != null) {
            resolvedBiomes.put(key, resolved);
        }
        return resolved;
    }

    private ResolvedBiome resolvePhysicalKey(String key) {
        ResolvedBiome cached = resolvedPhysicalBiomes.get(key);
        if (cached != null) {
            return cached;
        }
        PlatformBiome biome = IrisPlatforms.get().registries().biome(key);
        ResolvedBiome resolved = new ResolvedBiome(biome, BiomeInjectMatter.get(key));
        if (biome != null) {
            resolvedPhysicalBiomes.put(key, resolved);
        }
        return resolved;
    }

    private record ResolvedBiome(PlatformBiome biome, MatterBiomeInject matter) {
    }
}
