package com.volmit.iris.util.cache;

import com.volmit.iris.engine.data.cache.Cache;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.context.ChunkedDataCache;
import com.volmit.iris.util.data.KCache;
import com.volmit.iris.util.function.Function2;
import com.volmit.iris.util.mantle.Mantle;
import com.volmit.iris.util.scheduling.ChronoLatch;
import it.unimi.dsi.fastutil.longs.Long2LongMaps;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class WorldCache2D<T> {
    private final KCache<Long, ChunkCache2D<T>> chunks;
    private final Function2<Integer, Integer, T> resolver;

    public WorldCache2D(Function2<Integer, Integer, T> resolver) {
        this.resolver = resolver;
        chunks = new KCache<>((x) -> new ChunkCache2D<>(), 1024);
    }

    public T get(int x, int z) {
        ChunkCache2D<T> chunk = chunks.get(Cache.key(x >> 4, z >> 4));
        return chunk.get(x, z, resolver);
    }

    public long getSize() {
        return chunks.getSize() * 256L;
    }
}
