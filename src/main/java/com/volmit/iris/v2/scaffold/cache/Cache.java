package com.volmit.iris.v2.scaffold.cache;

public interface Cache<V>
{
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
