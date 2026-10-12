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

import art.arcane.iris.builtin.IrisSeed;
import art.arcane.iris.builtin.terrain.OreSettings;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.FeatureAttachment.Target;
import com.volmit.config.FeatureSettings.Entry;
import com.volmit.resource.ResourceId;
import com.volmit.world.PackWorld;

import java.util.Comparator;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * Builds the noise of every ore generator the world's biomes, regions and dimension declare before generation needs it.
 * A generator keeps the noise of the first seed it is given, so each one is warmed with the seed of its place in
 * this order: the biomes by key, the regions by key, then the dimension. The order is part of what a world generates.
 */
final class OreWarmUp {
    private static final long ROOT_SEED_SALT = 7777L;

    private final PackWorld world;
    private final TerrainOres ores;
    private final RNG root;
    private int warmed;

    OreWarmUp(PackWorld world) {
        this(world, TerrainOres.of(world));
    }

    OreWarmUp(PackWorld world, TerrainOres ores) {
        this.world = world;
        this.ores = ores;
        this.root = new RNG(IrisSeed.COMPONENT.of(world) + ROOT_SEED_SALT);
    }

    void warm() {
        LongSupplier seeds = () -> root.nextParallelRNG(warmed++).getSeed();

        List<Entry<OreSettings>> declared = world.settings().declared(OreSettings.class);
        warmLevel(declared, Target.BIOME, seeds);
        warmLevel(declared, Target.REGION, seeds);
        warmLevel(declared, Target.DIMENSION, seeds);
    }

    /** The ores of every owner at {@code level}, ordered by the key of the biome, region or dimension that declares them. */
    private void warmLevel(List<Entry<OreSettings>> declared, Target level, LongSupplier seeds) {
        declared.stream()
                .filter(entry -> entry.level() == level)
                .sorted(Comparator.comparing((Entry<OreSettings> entry) -> key(entry.source())))
                .forEach(entry -> ores.of(entry.value()).warm(seeds));
    }

    private static String key(ResourceId source) {
        String path = source.path();
        return path.substring(path.indexOf('/') + 1);
    }
}
