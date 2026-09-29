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
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.volmlib.nativelib.terrain.NativeBiome;
import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.iris.generation.context.ChunkedDataCache;
import art.arcane.volmlib.util.hunk.Hunk;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.documentation.BlockCoordinates;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.scheduling.PrecisionStopwatch;

import java.util.List;
import java.util.Objects;

public class IrisBiomeActuator extends EngineAssignedActuator<NativeBiome> {
    private final RNG rng;
    private final KMap<String, NativeBiome> resolvedBiomes = new KMap<>();
    private final KMap<String, NativeBiome> resolvedPhysicalBiomes = new KMap<>();

    public IrisBiomeActuator(Engine engine) {
        super(engine, "Biome");
        rng = new RNG(engine.getSeedManager().getBiome());
    }

    @BlockCoordinates
    @Override
    public void onActuate(int x, int z, Hunk<NativeBiome> h, boolean multicore, ChunkContext context) {
        PrecisionStopwatch p = PrecisionStopwatch.start();
        int width = h.getWidth();
        int depth = h.getDepth();
        int height = h.getHeight();
        Engine engine = getEngine();
        ChunkedDataCache<IrisBiome> biomeCache = context.getBiome();
        IrisComplex complex = context.getComplex();
        DimensionStackContext dimensionStackContext = engine.getDimensionStackContext();

        for (int xf = 0; xf < width; xf++) {
            for (int zf = 0; zf < depth; zf++) {
                int worldX = x + xf;
                int worldZ = z + zf;
                IrisBiome biome = biomeCache.get(xf, zf);
                NativeBiome platformBiome = resolve(biomeKey(
                        biome,
                        getDimension(),
                        engine,
                        worldX,
                        0,
                        worldZ
                ));
                if (platformBiome != null) {
                    h.set(xf, 0, zf, xf, height - 1, zf, platformBiome);
                }

                if (dimensionStackContext != null) {
                    NativeBiome bottomBiome = platformBiome == null
                            ? h.getRaw(xf, 0, zf)
                            : platformBiome;
                    applyDimensionStackBiomes(
                            xf,
                            zf,
                            worldX,
                            worldZ,
                            h,
                            engine,
                            bottomBiome,
                            context.getDimensionStackLayout(xf, zf)
                    );
                }
                writeHistoricalColumn(h, xf, zf, worldX, worldZ, height, engine, complex);
            }
        }
        engine.getMetrics().getBiome().put(p.getMilliseconds());
    }

    private void writeHistoricalColumn(
            Hunk<NativeBiome> output,
            int localX,
            int localZ,
            int worldX,
            int worldZ,
            int height,
            Engine engine,
            IrisComplex complex
    ) {
        TransitionGenerationPlan transitionPlan = complex.getTransitionGenerationPlan();
        if (transitionPlan == null || !transitionPlan.hasTransitionAtChunk(worldX >> 4, worldZ >> 4)) {
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
                        rangeStart,
                        internalY - 1,
                        output,
                        resolvePhysicalKey(activeKey)
                );
            }
            activeKey = nextKey;
            rangeStart = internalY;
        }
    }

    private void applyDimensionStackBiomes(
            int localX,
            int localZ,
            int worldX,
            int worldZ,
            Hunk<NativeBiome> output,
            Engine engine,
            NativeBiome bottom,
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
                    gapMinY,
                    gapMaxY,
                    output,
                    bottom
            );
            if (!layer.visible()) {
                continue;
            }
            NativeBiome resolved = bottom;
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
                    layer.renderMinY(),
                    layer.renderMaxY(),
                    output,
                    resolved
            );
        }
    }

    private void applyBiomeRange(
            int localX,
            int localZ,
            int minimumY,
            int maximumY,
            Hunk<NativeBiome> output,
            NativeBiome biome
    ) {
        if (minimumY > maximumY || biome == null) {
            return;
        }
        output.set(localX, minimumY, localZ, localX, maximumY, localZ, biome);
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

    private NativeBiome resolve(String key) {
        NativeBiome cached = key == null ? null : resolvedBiomes.get(key);
        if (cached != null) {
            return cached;
        }

        NativeBiome biome = IrisPlatforms.get().registries().biome(key);
        if (key != null && biome != null) {
            resolvedBiomes.put(key, biome);
        }
        return biome;
    }

    private NativeBiome resolvePhysicalKey(String key) {
        NativeBiome cached = resolvedPhysicalBiomes.get(key);
        if (cached != null) {
            return cached;
        }
        NativeBiome biome = IrisPlatforms.get().registries().biome(key);
        if (biome != null) {
            resolvedPhysicalBiomes.put(key, biome);
        }
        return biome;
    }
}
