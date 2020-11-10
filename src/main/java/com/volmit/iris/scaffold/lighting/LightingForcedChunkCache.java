package com.volmit.iris.scaffold.lighting;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.World;

import com.bergerkiller.bukkit.common.chunk.ForcedChunk;
import com.bergerkiller.bukkit.common.utils.WorldUtil;

/**
 * Shortly remembers the forced chunks it has kept loaded from a previous operation.
 * Reduces chunk unloading-loading grind.
 */
public class LightingForcedChunkCache {
    private static final Map<Key, ForcedChunk> _cache = new HashMap<Key, ForcedChunk>();

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
            if (o instanceof Key) {
                Key other = (Key) o;
                return other.x == this.x &&
                       other.z == this.z &&
                       other.world == this.world;
            } else {
                return false;
            }
        }
    }
}
