package com.volmit.iris.scaffold.cache;

import org.bukkit.Chunk;

public interface Cache<V>
{
    static long key(Chunk chunk) {
        return key(chunk.getX(), chunk.getZ());
    }

    public int getId();

    public V get(int x, int z);

    public static long key(int x, int z)
    {
        return (((long)x) << 32) | (z & 0xffffffffL);
    }

    public static int keyX(long key)
    {
        return (int)(key >> 32);
    }

    public static int keyZ(long key)
    {
        return (int)key;
    }
}
