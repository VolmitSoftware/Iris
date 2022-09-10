package com.volmit.iris.util.context;

import com.volmit.iris.util.documentation.BlockCoordinates;
import com.volmit.iris.util.parallel.BurstExecutor;
import com.volmit.iris.util.stream.ProceduralStream;

public class ChunkedDataCache<T> {
    private final int x;
    private final int z;
    private final Object[] data;
    private final boolean cache;
    private final ProceduralStream<T> stream;

    @BlockCoordinates
    public ChunkedDataCache(BurstExecutor burst, ProceduralStream<T> stream, int x, int z) {
        this(burst, stream, x, z, true);
    }
    @BlockCoordinates
    public ChunkedDataCache(BurstExecutor burst, ProceduralStream<T> stream, int x, int z, boolean cache) {
        this.stream = stream;
        this.cache = cache;
        this.x = x;
        this.z = z;
        if(cache) {
            data = new Object[256];
            int i,j;

            for(i = 0; i < 16; i++) {
                int finalI = i;
                for(j = 0; j < 16; j++) {
                    int finalJ = j;
                    burst.queue(() -> data[(finalJ * 16) + finalI] = stream.get(x+ finalI, z+ finalJ));
                }
            }
        }

        else {
            data = new Object[0];
        }
    }

    @SuppressWarnings("unchecked")
    @BlockCoordinates
    public T get(int x, int z) {
        if(!cache) {
            return stream.get(this.x + x, this.z + z);
        }

        T t = (T) data[(z * 16) + x];
        return t == null ? stream.get(this.x + x, this.z + z) : t;
    }
}
