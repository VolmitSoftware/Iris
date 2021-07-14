package com.volmit.iris.nms;

@FunctionalInterface
public interface BiomeBaseInjector {
    default void setBiome(int x, int z, Object biomeBase)
    {
        setBiome(x, 0, z, biomeBase);
    }

    void setBiome(int x, int y, int z, Object biomeBase);
}
