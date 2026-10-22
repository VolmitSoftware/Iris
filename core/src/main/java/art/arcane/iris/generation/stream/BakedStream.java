package art.arcane.iris.generation.stream;

import art.arcane.iris.generation.bake.BakePlane;
import art.arcane.iris.generation.bake.ChunkBakeCache;
import art.arcane.iris.generation.bake.ChunkPlanes;
import art.arcane.volmlib.util.stream.BasicStream;
import art.arcane.volmlib.util.stream.ProceduralStream;

import java.util.Objects;

public final class BakedStream<T> extends BasicStream<T> implements ChunkFillableStream2D {
    private final ChunkBakeCache cache;
    private final BakePlane plane;
    private final ProceduralStream<T> scalar;

    public BakedStream(ChunkBakeCache cache, BakePlane plane, ProceduralStream<T> scalar) {
        super();
        this.cache = Objects.requireNonNull(cache, "Bake cache");
        this.plane = Objects.requireNonNull(plane, "Bake plane");
        this.scalar = Objects.requireNonNull(scalar, "Scalar source");
    }

    @Override
    @SuppressWarnings("unchecked")
    public T get(double x, double z) {
        int blockX = (int) x;
        int blockZ = (int) z;
        int index = ((blockZ & 15) << 4) | (blockX & 15);
        ChunkPlanes.Plane values = cache.planeIfPresent(plane, blockX >> 4, blockZ >> 4);
        if (values != null && values.ready(index)) {
            return (T) values.objectAt(index);
        }
        if (!cache.memoAllowed()) {
            return scalar.get(blockX, blockZ);
        }
        long revision = ProvisionalSampling.revision();
        T value = scalar.get(blockX, blockZ);
        cache.storeObject(plane, blockX, blockZ, value, revision);
        return value;
    }

    @Override
    public T get(double x, double y, double z) {
        return scalar.get(x, y, z);
    }

    @Override
    public double toDouble(T t) {
        return scalar.toDouble(t);
    }

    @Override
    public T fromDouble(double d) {
        return scalar.fromDouble(d);
    }

    @Override
    public void fillChunkRaw(int worldX, int worldZ, Object[] target) {
        int chunkX = worldX >> 4;
        int chunkZ = worldZ >> 4;
        if (cache.memoAllowed()) {
            System.arraycopy(cache.complete(plane, chunkX, chunkZ), 0, target, 0, 256);
            return;
        }
        int originX = chunkX << 4;
        int originZ = chunkZ << 4;
        for (int index = 0; index < 256; index++) {
            target[index] = get(originX + (index & 15), originZ + (index >> 4));
        }
    }
}
