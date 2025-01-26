package com.volmit.iris.core.nms;

import com.volmit.iris.core.pregenerator.PregenListener;
import com.volmit.iris.util.documentation.ChunkCoordinates;
import com.volmit.iris.util.documentation.RegionCoordinates;
import com.volmit.iris.util.parallel.MultiBurst;

import java.io.Closeable;
import java.util.concurrent.CompletableFuture;

public interface IHeadless extends Closeable {
    int getLoadedChunks();

    @ChunkCoordinates
    boolean exists(int x, int z);

    @RegionCoordinates
    CompletableFuture<Void> generateRegion(MultiBurst burst, int x, int z, int maxConcurrent, PregenListener listener);

    @ChunkCoordinates
    void generateChunk(int x, int z);
}
