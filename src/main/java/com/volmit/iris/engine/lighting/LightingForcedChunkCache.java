/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
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

package com.volmit.iris.engine.lighting;

import com.bergerkiller.bukkit.common.chunk.ForcedChunk;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import org.bukkit.World;

import java.util.HashMap;
import java.util.Map;

/**
 * Shortly remembers the forced chunks it has kept loaded from a previous operation.
 * Reduces chunk unloading-loading grind.
 */
public class LightingForcedChunkCache {
    private static final Map<Key, ForcedChunk> _cache = new HashMap<>();

    public static ForcedChunk get(World world, int x, int z) {
        ForcedChunk cached;
        synchronized (_cache) {
            cached = _cache.get(new Key(world, x, z));
        }
        if (cached != null) {
            return cached.clone();
        } else {
            return WorldUtil.forceChunkLoaded(world, x, z);
        }
    }

    public static void store(ForcedChunk chunk) {
        ForcedChunk prev;
        synchronized (_cache) {
            prev = _cache.put(new Key(chunk.getWorld(), chunk.getX(), chunk.getZ()), chunk.clone());
        }
        if (prev != null) {
            prev.close();
        }
    }

    public static void reset() {
        synchronized (_cache) {
            for (ForcedChunk chunk : _cache.values()) {
                chunk.close();
            }
            _cache.clear();
        }
    }

    @SuppressWarnings("ClassCanBeRecord")
    private static final class Key {
        public final World world;
        public final int x;
        public final int z;

        public Key(World world, int x, int z) {
            this.world = world;
            this.x = x;
            this.z = z;
        }

        @Override
        public int hashCode() {
            return this.x * 31 + this.z;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof Key other) {
                return other.x == this.x &&
                        other.z == this.z &&
                        other.world == this.world;
            } else {
                return false;
            }
        }
    }
}
