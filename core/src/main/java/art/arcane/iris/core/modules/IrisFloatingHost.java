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

import art.arcane.iris.builtin.floating.FloatingHost;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.biome.custom.IrisBiomeCustom;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.matter.MatterBiomeInject;
import art.arcane.volmlib.util.matter.slices.BiomeInjectMatter;
import com.volmit.resource.ResourceId;

/** Iris's answers to {@code iris:floating}: a biome's terrain height by its generators, and sky biomes into the engine's mantle. */
final class IrisFloatingHost implements FloatingHost {
    private final Engine engine;

    IrisFloatingHost(Engine engine) {
        this.engine = engine;
    }

    @Override
    public double biomeHeight(ResourceId biome, int worldX, int worldZ, long seed) {
        return irisBiome(biome).getHeight(engine, worldX, worldZ, seed);
    }

    @Override
    public void paintSkyBiome(ResourceId biome, int worldX, int worldZ, int[] ys, RNG random) {
        MatterBiomeInject matter = skyBiomeMatter(irisBiome(biome), worldX, worldZ, random);
        for (int y : ys) {
            engine.getMantle().getMantle().set(worldX, y, worldZ, matter);
        }
    }

    private MatterBiomeInject skyBiomeMatter(IrisBiome target, int worldX, int worldZ, RNG random) {
        if (target.isCustom()) {
            IrisBiomeCustom custom = target.getCustomBiome(random, engine, worldX, 0, worldZ);
            return BiomeInjectMatter.get(IrisPlatforms.get().biomeWriter().biomeIdFor(engine.getDimension().getLoadKey() + ":" + custom.getId()));
        }
        return BiomeInjectMatter.get(IrisPlatforms.get().biomeWriter().biomeIdFor(target.getSkyBiomeKey(random, engine, worldX, 0, worldZ)));
    }

    private IrisBiome irisBiome(ResourceId biome) {
        return ((IrisPackResources) engine.getData().getPackResources()).irisBiome(biome)
                .orElseThrow(() -> new IllegalArgumentException("The pack has no biome " + biome));
    }
}
