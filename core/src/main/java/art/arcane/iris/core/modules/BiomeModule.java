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

import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.biome.custom.IrisBiomeCustom;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBiome;
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.iris.util.project.context.ChunkedDataCache;
import art.arcane.iris.util.project.hunk.Hunk;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterBiomeInject;
import art.arcane.volmlib.util.matter.slices.BiomeInjectMatter;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.ModuleSetup;
import com.volmit.stage.ChunkStage;
import com.volmit.stage.ChunkStageContext;
import com.volmit.stage.Stage;

/**
 * Module {@code iris:biome}: writes each column's biome into the chunk's biome hunk, a custom biome under the
 * dimension's namespace, and records the biome id in the mantle for platforms that inject biomes later.
 */
public final class BiomeModule implements IrisModule {
    /** Writes every column's biome, sky biome or custom biome, into the chunk. */
    public static final ChunkStage<Void> WRITE = Stage.chunk("iris:biome/write");
    private static final String VERSION = "built-in";

    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("iris:biome", "Biomes", VERSION);
    }

    @Override
    public void setup(ModuleSetup setup) {
        setup.stages().define(WRITE).run(BiomeModule::write);
    }

    private static void write(ChunkStageContext context) {
        IrisChunk chunk = context.extension(IrisChunk.class).orElseThrow();
        chunk.state(BiomeWriter.class, BiomeWriter::new).write(chunk.originX(), chunk.originZ(), chunk.biomes(), chunk.context());
    }

    /** What writing biomes keeps for one engine runtime: the biome stream's random and the platform biomes resolved so far. */
    static final class BiomeWriter {
        private final Engine engine;
        private final RNG rng;
        private final KMap<String, ResolvedBiome> resolvedBiomes = new KMap<>();

        BiomeWriter(Engine engine) {
            this.engine = engine;
            this.rng = new RNG(engine.getSeedManager().getBiome());
        }

        /** A partial biome write fails the chunk: a chunk flagged done with default biomes would keep them forever. */
        void write(int originX, int originZ, Hunk<PlatformBiome> biomes, ChunkContext context) {
            Mantle<Matter> mantle = engine.getMantle().getMantle();
            ChunkedDataCache<IrisBiome> biomeCache = context.getBiome();
            for (int localX = 0; localX < biomes.getWidth(); localX++) {
                for (int localZ = 0; localZ < biomes.getDepth(); localZ++) {
                    int worldX = originX + localX;
                    int worldZ = originZ + localZ;
                    ResolvedBiome resolved = resolve(keyOf(biomeCache.get(localX, localZ), worldX, worldZ));
                    if (resolved.biome() != null) {
                        biomes.set(localX, 0, localZ, localX, biomes.getHeight() - 1, localZ, resolved.biome());
                    }
                    mantle.set(worldX, 0, worldZ, resolved.matter());
                }
            }
        }

        private String keyOf(IrisBiome biome, int worldX, int worldZ) {
            if (!biome.isCustom()) {
                return biome.getSkyBiomeKey(rng, engine, worldX, 0, worldZ);
            }
            IrisBiomeCustom custom = biome.getCustomBiome(rng, engine, worldX, 0, worldZ);
            return engine.getDimension().getLoadKey() + ":" + custom.getId();
        }

        /**
         * The registry lookup and the biome id depend only on the key, so the pair is cached instead of paying two
         * string-keyed lookups per column. Unresolved biomes are never cached, so a biome registered later still
         * resolves.
         */
        private ResolvedBiome resolve(String key) {
            ResolvedBiome cached = key == null ? null : resolvedBiomes.get(key);
            if (cached != null) {
                return cached;
            }
            IrisPlatform platform = IrisPlatforms.get();
            PlatformBiome biome = platform.registries().biome(key);
            ResolvedBiome resolved = new ResolvedBiome(biome, BiomeInjectMatter.get(platform.biomeWriter().biomeIdFor(key)));
            if (key != null && biome != null) {
                resolvedBiomes.put(key, resolved);
            }
            return resolved;
        }
    }

    private record ResolvedBiome(PlatformBiome biome, MatterBiomeInject matter) {
    }
}
