package com.volmit.iris.core.nms.headless;

import com.volmit.iris.engine.data.chunk.TerrainChunk;
import com.volmit.iris.util.math.Position2;

public interface SerializableChunk extends TerrainChunk {
    Position2 getPos();

    Object serialize();

    void mark();
}
