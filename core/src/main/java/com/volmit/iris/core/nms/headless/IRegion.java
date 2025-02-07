package com.volmit.iris.core.nms.headless;

import com.volmit.iris.util.documentation.ChunkCoordinates;
import lombok.NonNull;

import java.io.IOException;

public interface IRegion extends AutoCloseable {

    @ChunkCoordinates
    boolean exists(int x, int z);

    void write(@NonNull SerializableChunk chunk) throws IOException;

    @Override
    void close();
}
