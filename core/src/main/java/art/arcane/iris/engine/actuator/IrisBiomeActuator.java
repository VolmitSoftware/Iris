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

package art.arcane.iris.engine.actuator;

import art.arcane.iris.engine.object.world.biome.custom.IrisBiomeCustom;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineAssignedActuator;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.iris.util.project.context.ChunkedDataCache;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.documentation.BlockCoordinates;
import art.arcane.iris.util.project.hunk.Hunk;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterBiomeInject;
import art.arcane.volmlib.util.matter.slices.BiomeInjectMatter;
import art.arcane.volmlib.util.scheduling.ChronoLatch;
import art.arcane.volmlib.util.scheduling.PrecisionStopwatch;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBiome;

public class IrisBiomeActuator extends EngineAssignedActuator<PlatformBiome> {
    private final RNG rng;
    private final ChronoLatch cl = new ChronoLatch(5000);
    private final KMap<String, ResolvedBiome> resolvedBiomes = new KMap<>();

    public IrisBiomeActuator(Engine engine) {
        super(engine, "Biome");
        rng = new RNG(engine.getSeedManager().getBiome());
    }

    @BlockCoordinates
    @Override
    public void onActuate(int x, int z, Hunk<PlatformBiome> h, boolean multicore, ChunkContext context) {
        // No catch here: a partial biome write must fail the chunk (EngineAssignedActuator
        // rethrows), or the chunk is flagged REAL with default plains columns forever.
        PrecisionStopwatch p = PrecisionStopwatch.start();
        int width = h.getWidth();
        int depth = h.getDepth();
        int height = h.getHeight();
        Engine engine = getEngine();
        Mantle<Matter> mantle = engine.getMantle().getMantle();
        ChunkedDataCache<IrisBiome> biomeCache = context.getBiome();

        for (int xf = 0; xf < width; xf++) {
            IrisBiome ib;
            for (int zf = 0; zf < depth; zf++) {
                ib = biomeCache.get(xf, zf);
                String key;

                if (ib.isCustom()) {
                    IrisBiomeCustom custom = ib.getCustomBiome(rng, engine, x + xf, 0, z + zf);
                    key = getDimension().getLoadKey() + ":" + custom.getId();
                } else {
                    key = ib.getSkyBiomeKey(rng, engine, x + xf, 0, z + zf);
                }

                ResolvedBiome resolved = resolve(key);
                PlatformBiome biome = resolved.biome();

                if (biome != null) {
                    h.set(xf, 0, zf, xf, height - 1, zf, biome);
                }

                mantle.set(x + xf, 0, z + zf, resolved.matter());
            }
        }
        engine.getMetrics().getBiome().put(p.getMilliseconds());
    }

    /**
     * The registry lookup and the biome id only depend on the scatter resolved key, so cache the pair
     * instead of paying two string keyed lookups per column. Unresolved biomes are never cached, so a
     * biome registered later still resolves.
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

    private record ResolvedBiome(PlatformBiome biome, MatterBiomeInject matter) {
    }
}
