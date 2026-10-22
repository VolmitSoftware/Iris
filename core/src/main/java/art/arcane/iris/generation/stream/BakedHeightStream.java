package art.arcane.iris.generation.stream;

import art.arcane.iris.generation.bake.BakePlane;
import art.arcane.iris.generation.bake.ChunkBakeCache;
import art.arcane.iris.generation.bake.ChunkPlanes;
import art.arcane.volmlib.util.function.NoiseProvider;
import art.arcane.volmlib.util.stream.BasicStream;

import java.util.Objects;
import java.util.function.BooleanSupplier;

public final class BakedHeightStream extends BasicStream<Double> implements ChunkFillableStream2D, ChunkFillableDoubleStream2D {
    private final ChunkBakeCache cache;
    private final BooleanSupplier mainThread;
    private final NoiseProvider nonblocking;
    private final NoiseProvider resolved;

    public BakedHeightStream(ChunkBakeCache cache, BooleanSupplier mainThread, NoiseProvider nonblocking, NoiseProvider resolved) {
        super();
        this.cache = Objects.requireNonNull(cache, "Bake cache");
        this.mainThread = Objects.requireNonNull(mainThread, "Main thread check");
        this.nonblocking = Objects.requireNonNull(nonblocking, "Nonblocking height");
        this.resolved = Objects.requireNonNull(resolved, "Resolved height");
    }

    @Override
    public Double get(double x, double z) {
        return getDouble(x, z);
    }

    @Override
    public double getDouble(double x, double z) {
        if (mainThread.getAsBoolean()) {
            return nonblocking.noise(x, z);
        }
        int blockX = (int) x;
        int blockZ = (int) z;
        Object values = cache.read(BakePlane.HEIGHT, blockX >> 4, blockZ >> 4);
        return values == null ? resolved.noise(blockX, blockZ) : ((double[]) values)[((blockZ & 15) << 4) | (blockX & 15)];
    }

    @Override
    public Double get(double x, double y, double z) {
        return getDouble(x, z);
    }

    @Override
    public double getDouble(double x, double y, double z) {
        return getDouble(x, z);
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
        boolean main = mainThread.getAsBoolean();
        if (!main && (worldX & 15) == 0 && (worldZ & 15) == 0) {
            Object values = ChunkBakeCache.inSparseScope() ? published(worldX >> 4, worldZ >> 4)
                    : cache.bake(BakePlane.HEIGHT, worldX >> 4, worldZ >> 4);
            if (values != null) {
                System.arraycopy(values, 0, target, 0, 256);
                return;
            }
        }
        for (int index = 0; index < 256; index++) {
            int x = worldX + (index & 15);
            int z = worldZ + (index >> 4);
            target[index] = main ? nonblocking.noise(x, z) : column(x, z);
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

    private double column(int x, int z) {
        Object values = published(x >> 4, z >> 4);
        return values == null ? resolved.noise(x, z) : ((double[]) values)[((z & 15) << 4) | (x & 15)];
    }

    private Object published(int chunkX, int chunkZ) {
        ChunkPlanes planes = cache.planesIfPresent(chunkX, chunkZ);
        return planes == null ? null : planes.published(BakePlane.HEIGHT);
    }
}
