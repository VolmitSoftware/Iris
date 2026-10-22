package art.arcane.iris.generation.stream;

import art.arcane.iris.generation.bake.BakePlane;
import art.arcane.iris.generation.bake.ChunkBakeCache;
import art.arcane.iris.generation.bake.ChunkPlanes;
import art.arcane.volmlib.util.stream.BasicStream;
import art.arcane.volmlib.util.stream.ProceduralStream;

import java.util.Objects;

public final class BakedDoubleStream extends BasicStream<Double> implements ChunkFillableStream2D, ChunkFillableDoubleStream2D {
    private final ChunkBakeCache cache;
    private final BakePlane plane;
    private final ProceduralStream<Double> scalar;

    public BakedDoubleStream(ChunkBakeCache cache, BakePlane plane, ProceduralStream<Double> scalar) {
        super();
        this.cache = Objects.requireNonNull(cache, "Bake cache");
        this.plane = Objects.requireNonNull(plane, "Bake plane");
        this.scalar = Objects.requireNonNull(scalar, "Scalar source");
    }

    public BakePlane plane() {
        return plane;
    }

    public ProceduralStream<Double> scalar() {
        return scalar;
    }

    @Override
    public Double get(double x, double z) {
        return getDouble(x, z);
    }

    @Override
    public double getDouble(double x, double z) {
        int blockX = (int) x;
        int blockZ = (int) z;
        Object values = cache.read(plane, blockX >> 4, blockZ >> 4);
        return values == null ? scalar.getDouble(blockX, blockZ) : ((double[]) values)[((blockZ & 15) << 4) | (blockX & 15)];
    }

    public double sparse(int blockX, int blockZ) {
        Object published = publishedPlane(blockX >> 4, blockZ >> 4);
        if (published != null) {
            return ((double[]) published)[((blockZ & 15) << 4) | (blockX & 15)];
        }
        ChunkBakeCache.enterSparse();
        try {
            return scalar.getDouble(blockX, blockZ);
        } finally {
            ChunkBakeCache.exitSparse();
        }
    }

    @Override
    public Double get(double x, double y, double z) {
        return scalar.get(x, y, z);
    }

    @Override
    public double getDouble(double x, double y, double z) {
        return scalar.getDouble(x, y, z);
    }

    @Override
    public double toDouble(Double t) {
        return t;
    }

    @Override
    public Double fromDouble(double d) {
        return d;
    }

    @Override
    public void fillChunkDoubles(int worldX, int worldZ, double[] target) {
        if (target == null || target.length != 256) {
            throw new IllegalArgumentException("Expected a 16x16 target array.");
        }
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
            target[index] = scalar.getDouble(originX + (index & 15), originZ + (index >> 4));
        }
    }

    @Override
    public void fillChunkRaw(int worldX, int worldZ, Object[] target) {
        double[] values = new double[256];
        fillChunkDoubles(worldX, worldZ, values);
        for (int index = 0; index < 256; index++) {
            target[index] = values[index];
        }
    }

    private Object publishedPlane(int chunkX, int chunkZ) {
        ChunkPlanes planes = cache.planesIfPresent(chunkX, chunkZ);
        return planes == null ? null : planes.published(plane);
    }
}
