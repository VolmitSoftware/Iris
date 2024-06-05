package com.volmit.iris.core.nms;

import com.volmit.iris.core.pregenerator.PregenListener;
import com.volmit.iris.util.documentation.ChunkCoordinates;
import com.volmit.iris.util.documentation.RegionCoordinates;
import com.volmit.iris.util.parallel.MultiBurst;

import java.io.Closeable;

public interface IHeadless extends Closeable {

    void save();

    @ChunkCoordinates
    boolean exists(int x, int z);

    @RegionCoordinates
    void generateRegion(MultiBurst burst, int x, int z, PregenListener listener);

    @ChunkCoordinates
    void generateChunk(int x, int z);
}
