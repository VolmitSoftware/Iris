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

import art.arcane.iris.builtin.LiveWorld;
import art.arcane.iris.builtin.LiveWorld.BiomeKind;
import art.arcane.iris.builtin.LiveWorld.Sky;
import art.arcane.iris.builtin.spawn.SpawnRate;
import art.arcane.iris.builtin.spawn.SpawnCooldowns;
import art.arcane.iris.builtin.spawn.Spawner;

import java.util.Optional;

/**
 * Whether a spawner may fire: in a biome of the kind its group wants, in the time and weather it asks for, and not
 * more often than its rates allow. The rates count in the world's data, by the key the pack names the spawner by.
 */
final class SpawnerGate {
    private SpawnerGate() {
    }

    /** Whether a spawner of this group fires in a biome of {@code kind}. */
    static boolean suits(Spawner spawner, BiomeKind kind) {
        return switch (spawner.group()) {
            case NORMAL -> switch (kind) {
                case SHORE, SEA, CAVE -> false;
                case LAND -> true;
            };
            case CAVE -> true;
            case UNDERWATER -> switch (kind) {
                case SHORE, LAND, CAVE -> false;
                case SEA -> true;
            };
            case BEACH -> switch (kind) {
                case SHORE -> true;
                case LAND, CAVE, SEA -> false;
            };
        };
    }

    /** Whether the world is in the time and weather the spawner asks for. */
    static boolean suits(Spawner spawner, Sky sky) {
        return SpawnConditions.allow(spawner.timeBlock(), spawner.weather(), sky);
    }

    /** Whether the spawner may fire in the chunk now. */
    static boolean canSpawn(String key, Spawner spawner, LiveWorld live, int chunkX, int chunkZ) {
        if (!canSpawn(key, spawner, live)) {
            return false;
        }

        SpawnRate rate = spawner.maximumRatePerChunk();
        return rate.isInfinite() || live.spawnCooldowns().canSpawnInChunk(chunkX, chunkZ, key, rate.interval());
    }

    /** Counts a spawn of the spawner in the chunk against its rates. */
    static void spawned(String key, Spawner spawner, LiveWorld live, int chunkX, int chunkZ) {
        spawned(key, spawner, live);
        if (spawner.maximumRatePerChunk().isInfinite()) {
            return;
        }

        live.spawnCooldowns().spawnedInChunk(chunkX, chunkZ, key);
    }

    private static boolean canSpawn(String key, Spawner spawner, LiveWorld live) {
        Optional<Sky> sky = live.sky();
        if (sky.isEmpty() || !suits(spawner, sky.get())) {
            return false;
        }

        SpawnRate rate = spawner.maximumRate();
        return rate.isInfinite() || live.spawnCooldowns().canSpawn(key, rate.interval());
    }

    private static void spawned(String key, Spawner spawner, LiveWorld live) {
        if (spawner.maximumRate().isInfinite()) {
            return;
        }

        live.spawnCooldowns().spawned(key);
    }
}
