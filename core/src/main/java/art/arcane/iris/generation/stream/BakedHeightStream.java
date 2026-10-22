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
        return column((int) x, (int) z, !ChunkBakeCache.inSparseScope());
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
        boolean memo = !main && !ChunkBakeCache.inSparseScope();
        if (memo && (worldX & 15) == 0 && (worldZ & 15) == 0) {
            System.arraycopy(cache.complete(BakePlane.HEIGHT, worldX >> 4, worldZ >> 4), 0, target, 0, 256);
            return;
        }
        for (int index = 0; index < 256; index++) {
            int x = worldX + (index & 15);
            int z = worldZ + (index >> 4);
            target[index] = main ? nonblocking.noise(x, z) : column(x, z, memo);
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

    private double column(int x, int z, boolean memo) {
        int index = ((z & 15) << 4) | (x & 15);
        ChunkPlanes.Plane values = cache.planeIfPresent(BakePlane.HEIGHT, x >> 4, z >> 4);
        if (values != null && values.ready(index)) {
            return values.doubleAt(index);
        }
        if (!memo) {
            return resolved.noise(x, z);
        }
        long revision = ProvisionalSampling.revision();
        double value = resolved.noise(x, z);
        cache.storeDouble(BakePlane.HEIGHT, x, z, value, revision);
        return value;
    }
}
