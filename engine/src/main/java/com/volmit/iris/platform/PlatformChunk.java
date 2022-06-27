package com.volmit.iris.platform;

public interface PlatformChunk {
    int getX();

    int getZ();

    void unload(boolean save, boolean force);
}
