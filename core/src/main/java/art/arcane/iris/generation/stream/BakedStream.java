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

    public BakePlane plane() {
        return plane;
    }

    public ProceduralStream<T> scalar() {
        return scalar;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T get(double x, double z) {
        int blockX = (int) x;
        int blockZ = (int) z;
        Object values = cache.read(plane, blockX >> 4, blockZ >> 4);
        return values == null ? scalar.get(blockX, blockZ) : (T) ((Object[]) values)[((blockZ & 15) << 4) | (blockX & 15)];
    }

    @SuppressWarnings("unchecked")
    public T sparse(int blockX, int blockZ) {
        Object published = publishedPlane(blockX >> 4, blockZ >> 4);
        if (published != null) {
            return (T) ((Object[]) published)[((blockZ & 15) << 4) | (blockX & 15)];
        }
        ChunkBakeCache.enterSparse();
        try {
            return scalar.get(blockX, blockZ);
        } finally {
            ChunkBakeCache.exitSparse();
        }
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
        Object values = cache.bakingAllowed() ? cache.bake(plane, chunkX, chunkZ) : publishedPlane(chunkX, chunkZ);
        if (values != null) {
            System.arraycopy(values, 0, target, 0, 256);
            return;
        }
        int originX = chunkX << 4;
        int originZ = chunkZ << 4;
        for (int index = 0; index < 256; index++) {
            target[index] = scalar.get(originX + (index & 15), originZ + (index >> 4));
        }
    }

    private Object publishedPlane(int chunkX, int chunkZ) {
        ChunkPlanes planes = cache.planesIfPresent(chunkX, chunkZ);
        return planes == null ? null : planes.published(plane);
    }
}
