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

import art.arcane.iris.builtin.terrain.OreSettings;
import art.arcane.iris.core.loader.IrisRegistrant;
import art.arcane.iris.engine.framework.Engine;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the terrain module keeps for one world: the ore generators of its dimension, regions and biomes made ready
 * ({@link ResolvedOres}), built from their {@link OreSettings} when first asked for or ahead of generation by {@link OreWarmUp}.
 */
final class TerrainOres {
    private final Engine engine;
    private final Map<OreSettings, ResolvedOres> resolved = new ConcurrentHashMap<>();

    TerrainOres(Engine engine) {
        this.engine = engine;
    }

    /** The ores of the world {@code engine} generates now. */
    static TerrainOres of(Engine engine) {
        return engine.getGeneration().state(TerrainOres.class, TerrainOres::new);
    }

    /** The ore generators {@code owner}, a dimension, region or biome, declares; {@link ResolvedOres#NONE} when it declares none. */
    ResolvedOres of(IrisRegistrant owner) {
        OreSettings settings = owner.getModuleSettings().find(OreSettings.class).orElse(null);
        if (settings == null) {
            return ResolvedOres.NONE;
        }
        return resolved.computeIfAbsent(settings, declared -> ResolvedOres.of(declared, engine));
    }
}
