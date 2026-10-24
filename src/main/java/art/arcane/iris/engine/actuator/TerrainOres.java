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

import art.arcane.iris.builtin.IrisSeedPurposes;
import art.arcane.iris.modules.terrain.OreSettings;
import com.volmit.resource.PackResources;
import com.volmit.world.PackWorld;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the terrain module keeps for one world: the ore generators of its dimension, regions and biomes made ready
 * ({@link ResolvedOres}), built from their {@link OreSettings} when first asked for or ahead of generation by {@link OreWarmUp}.
 */
final class TerrainOres {
    private final PackResources resources;
    private final long terrainSeed;
    private final Map<OreSettings, ResolvedOres> resolved = new ConcurrentHashMap<>();

    TerrainOres(PackWorld world) {
        this.resources = world.resources();
        this.terrainSeed = world.seed(IrisSeedPurposes.TERRAIN);
    }

    /** The ores of {@code world}. */
    static TerrainOres of(PackWorld world) {
        return world.state(TerrainOres.class, TerrainOres::new);
    }

    /**
     * The ore generators {@code settings}, those a dimension, region or biome declares, make ready; {@link ResolvedOres#NONE} when it
     * declares none. The terrain asks this for two owners per column, so a resolved set is read without taking the map's lock.
     */
    ResolvedOres of(OreSettings settings) {
        if (settings == null) {
            return ResolvedOres.NONE;
        }
        ResolvedOres existing = resolved.get(settings);
        if (existing != null) {
            return existing;
        }
        return resolved.computeIfAbsent(settings, declared -> ResolvedOres.of(declared, resources, terrainSeed));
    }
}
