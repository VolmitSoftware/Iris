package art.arcane.iris.util.project.context;

import art.arcane.iris.util.project.stream.ProceduralStream;
import art.arcane.iris.util.project.stream.utility.CachedStream2D;
import art.arcane.volmlib.util.documentation.BlockCoordinates;
import java.util.concurrent.Executor;

public class ChunkedDataCache<T> {
    private final int x;
    private final int z;
    private final ProceduralStream<T> stream;
    private final boolean cache;
    private final Object[] data;

    @BlockCoordinates
    public ChunkedDataCache(ProceduralStream<T> stream, int x, int z) {
        this(stream, x, z, true);
    }

    @BlockCoordinates
    public ChunkedDataCache(ProceduralStream<T> stream, int x, int z, boolean cache) {
        this.x = x;
        this.z = z;
        this.stream = stream;
        this.cache = cache;
        this.data = new Object[cache ? 256 : 0];
    }

    public void fill() {
        fill(null);
    }

    public void fill(Executor executor) {
        if (!cache) {
            return;
        }

        if (stream instanceof CachedStream2D<?> cachedStream) {
            cachedStream.fillChunk(x, z, data);
            return;
        }

        for (int row = 0; row < 16; row++) {
            int rowOffset = row * 16;
            int worldZ = z + row;
            for (int column = 0; column < 16; column++) {
                data[rowOffset + column] = stream.get(x + column, worldZ);
            }
        }
    }

    @BlockCoordinates
    @SuppressWarnings("unchecked")
    public T get(int x, int z) {
        if (!cache) {
            return stream.get(this.x + x, this.z + z);
        }

        int index = (z * 16) + x;
        T value = (T) data[index];
        if (value != null) {
            return value;
        }

        T sampled = stream.get(this.x + x, this.z + z);
        data[index] = sampled;
        return sampled;
    }
}
