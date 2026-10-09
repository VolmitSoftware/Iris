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

package art.arcane.iris.engine.actuator;

import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.terrain.deposit.IrisOreGenerator;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;

import java.util.Comparator;

/**
 * Builds the noise of every ore generator the world's biomes, regions and dimension declare before generation needs it.
 * A generator keeps the noise of the first random it is given, so each one is warmed with the random of its place in
 * this order: the biomes by key, the regions by key, then the dimension. The order is part of what a world generates.
 */
final class OreWarmUp {
    private static final long ROOT_SEED_SALT = 7777L;

    private final Engine engine;
    private final RNG root;
    private int warmed;

    OreWarmUp(Engine engine) {
        this.engine = engine;
        this.root = new RNG(engine.getSeedManager().getComponent() + ROOT_SEED_SALT);
    }

    void warm() {
        KList<IrisBiome> biomes = engine.getAllBiomes();
        biomes.sort(Comparator.comparing(IrisBiome::getLoadKey));
        for (IrisBiome biome : biomes) {
            warmAll(biome.getOres());
        }

        KList<IrisRegion> regions = engine.getDimension().getAllRegions(engine);
        regions.sort(Comparator.comparing(IrisRegion::getLoadKey));
        for (IrisRegion region : regions) {
            warmAll(region.getOres());
        }

        warmAll(engine.getDimension().getOres());
    }

    private void warmAll(KList<IrisOreGenerator> ores) {
        if (ores == null) {
            return;
        }
        for (IrisOreGenerator ore : ores) {
            if (ore != null) {
                ore.warm(root.nextParallelRNG(warmed++), engine.getData());
            }
        }
    }
}
