package art.arcane.iris.util.project.context;

import art.arcane.iris.util.project.stream.ProceduralStream;
import art.arcane.volmlib.util.documentation.BlockCoordinates;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

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
        fill(ForkJoinPool.commonPool());
    }

    public void fill(Executor executor) {
        if (!cache) {
            return;
        }

        List<CompletableFuture<Void>> tasks = new ArrayList<>(16);
        for (int j = 0; j < 16; j++) {
            int row = j;
            tasks.add(CompletableFuture.runAsync(() -> {
                int rowOffset = row * 16;
                double zz = (z + row);
                for (int i = 0; i < 16; i++) {
                    data[rowOffset + i] = stream.get(x + i, zz);
                }
            }, executor));
        }

        for (CompletableFuture<Void> task : tasks) {
            task.join();
        }
    }

    @BlockCoordinates
    @SuppressWarnings("unchecked")
    public T get(int x, int z) {
        if (!cache) {
            return stream.get(this.x + x, this.z + z);
        }

        T value = (T) data[(z * 16) + x];
        if (value != null) {
            return value;
        }

        return stream.get(this.x + x, this.z + z);
    }
}
