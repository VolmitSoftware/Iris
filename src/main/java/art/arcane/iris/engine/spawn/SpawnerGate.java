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

package art.arcane.iris.engine.spawn;

import art.arcane.iris.builtin.spawn.SpawnRate;
import art.arcane.iris.builtin.spawn.Spawner;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.spi.PlatformWorld;

/**
 * Whether a spawner may fire: in a biome of the kind its group wants, in the time and weather it asks for, and not
 * more often than its rates allow. The rates count in the engine's persisted data, by the key the pack names the
 * spawner by.
 */
final class SpawnerGate {
    private SpawnerGate() {
    }

    /** Whether a spawner of this group fires in {@code biome}. */
    static boolean suits(Spawner spawner, IrisBiome biome) {
        return switch (spawner.group()) {
            case NORMAL -> switch (biome.getInferredType()) {
                case SHORE, SEA, CAVE -> false;
                case LAND -> true;
            };
            case CAVE -> true;
            case UNDERWATER -> switch (biome.getInferredType()) {
                case SHORE, LAND, CAVE -> false;
                case SEA -> true;
            };
            case BEACH -> switch (biome.getInferredType()) {
                case SHORE -> true;
                case LAND, CAVE, SEA -> false;
            };
        };
    }

    /** Whether the world is in the time and weather the spawner asks for. */
    static boolean suits(Spawner spawner, PlatformWorld world) {
        return SpawnConditions.allow(spawner.timeBlock(), spawner.weather(), world);
    }

    /** Whether the spawner may fire in the chunk now. */
    static boolean canSpawn(String key, Spawner spawner, Engine engine, int chunkX, int chunkZ) {
        if (!canSpawn(key, spawner, engine)) {
            return false;
        }

        SpawnRate rate = spawner.maximumRatePerChunk();
        return rate.isInfinite() || engine.getEngineData().getChunk(chunkX, chunkZ).getCooldown(key).canSpawn(rate.interval());
    }

    /** Counts a spawn of the spawner in the chunk against its rates. */
    static void spawned(String key, Spawner spawner, Engine engine, int chunkX, int chunkZ) {
        spawned(key, spawner, engine);
        if (spawner.maximumRatePerChunk().isInfinite()) {
            return;
        }

        engine.getEngineData().getChunk(chunkX, chunkZ).getCooldown(key).spawn();
    }

    private static boolean canSpawn(String key, Spawner spawner, Engine engine) {
        PlatformWorld world = engine.getWorld().platformWorld();
        if (world == null || !suits(spawner, world)) {
            return false;
        }

        SpawnRate rate = spawner.maximumRate();
        return rate.isInfinite() || engine.getEngineData().getCooldown(key).canSpawn(rate.interval());
    }

    private static void spawned(String key, Spawner spawner, Engine engine) {
        if (spawner.maximumRate().isInfinite()) {
            return;
        }

        engine.getEngineData().getCooldown(key).spawn();
    }
}
