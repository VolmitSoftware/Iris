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

package art.arcane.iris.builtin.spawn;

import java.util.function.ToLongFunction;

/**
 * When spawners last fired, which Iris keeps with the world's data so that a spawner's rates hold across restarts. Rates
 * count per spawner, by the key the pack names it by, in the world and in each chunk. Iris offers it as an extension of
 * its worlds: {@code world.extension(SpawnCooldowns.class)}.
 */
public interface SpawnCooldowns {
    /** Whether the spawner may fire in the world again, {@code intervalMilliseconds} after it last did. */
    boolean canSpawn(String spawner, long intervalMilliseconds);

    /** Notes that the spawner fired in the world now. */
    void spawned(String spawner);

    /** Whether the spawner may fire in the chunk again, {@code intervalMilliseconds} after it last did there. */
    boolean canSpawnInChunk(int chunkX, int chunkZ, String spawner, long intervalMilliseconds);

    /** Notes that the spawner fired in the chunk now. */
    void spawnedInChunk(int chunkX, int chunkZ, String spawner);

    /**
     * Forgets the cooldowns that ran out and those of spawners the pack no longer has, so the world's data does not grow.
     *
     * @param intervalMilliseconds the interval of the world rate of a spawner by its key, or a negative number for a spawner the pack lacks
     */
    void forgetExpired(ToLongFunction<String> intervalMilliseconds);
}
