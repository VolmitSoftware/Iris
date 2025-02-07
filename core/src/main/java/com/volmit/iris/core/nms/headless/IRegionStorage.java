package com.volmit.iris.core.nms.headless;

import com.volmit.iris.util.context.ChunkContext;
import com.volmit.iris.util.documentation.ChunkCoordinates;
import com.volmit.iris.util.documentation.RegionCoordinates;
import lombok.NonNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public interface IRegionStorage {

    @ChunkCoordinates
    boolean exists(int x, int z);

    @Nullable
    @RegionCoordinates
    IRegion getRegion(int x, int z, boolean existingOnly) throws IOException;

    @NonNull
    @ChunkCoordinates
    SerializableChunk createChunk(int x, int z);

    void fillBiomes(@NonNull SerializableChunk chunk, @Nullable ChunkContext ctx);

    void close();
}
