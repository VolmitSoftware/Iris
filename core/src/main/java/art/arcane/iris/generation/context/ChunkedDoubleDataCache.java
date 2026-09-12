package art.arcane.iris.generation.context;

import art.arcane.volmlib.util.stream.ProceduralStream;
import art.arcane.iris.generation.stream.ChunkFillableDoubleStream2D;
import art.arcane.iris.generation.simd.SimdSupport;
import art.arcane.volmlib.util.documentation.BlockCoordinates;

import java.util.Arrays;
import java.util.concurrent.Executor;

public class ChunkedDoubleDataCache {
    private final int x;
    private final int z;
    private final ProceduralStream<Double> stream;
    private final boolean cache;
    private final double[] data;
    private double[] overrides;

    @BlockCoordinates
    public ChunkedDoubleDataCache(ProceduralStream<Double> stream, int x, int z) {
        this(stream, x, z, true);
    }

    @BlockCoordinates
    public ChunkedDoubleDataCache(ProceduralStream<Double> stream, int x, int z, boolean cache) {
        this.x = x;
        this.z = z;
        this.stream = stream;
        this.cache = cache;
        this.data = new double[cache ? 256 : 0];
        if (cache) {
            Arrays.fill(this.data, Double.NaN);
        }
    }

    public void fill() {
        fill(null);
    }

    public void fill(Executor executor) {
        fillRounded(null);
    }

    public void fillRounded(int[] roundedTarget) {
        if (!cache) {
            if (roundedTarget != null) {
                for (int row = 0; row < 16; row++) {
                    int rowOffset = row << 4;
                    int worldZ = z + row;
                    for (int column = 0; column < 16; column++) {
                        roundedTarget[rowOffset + column] = (int) Math.round(stream.getDouble(x + column, worldZ));
                    }
                }
            }
            return;
        }

        if (stream instanceof ChunkFillableDoubleStream2D cachedStream) {
            cachedStream.fillChunkDoubles(x, z, data);
            if (roundedTarget != null) {
                SimdSupport.kernels().roundToInt(data, roundedTarget, 256);
            }
            return;
        }

        for (int row = 0; row < 16; row++) {
            int rowOffset = row << 4;
            int worldZ = z + row;
            for (int column = 0; column < 16; column++) {
                double sampled = stream.getDouble(x + column, worldZ);
                data[rowOffset + column] = sampled;
                if (roundedTarget != null) {
                    roundedTarget[rowOffset + column] = (int) Math.round(sampled);
                }
            }
        }
    }

    public void setDouble(int localX, int localZ, double value) {
        if (localX < 0 || localX >= 16 || localZ < 0 || localZ >= 16 || !Double.isFinite(value)) {
            throw new IllegalArgumentException("Invalid chunk height override");
        }
        if (cache) {
            data[(localZ << 4) + localX] = value;
            return;
        }
        if (overrides == null) {
            overrides = new double[256];
            Arrays.fill(overrides, Double.NaN);
        }
        overrides[(localZ << 4) + localX] = value;
    }

    @BlockCoordinates
    public double getDouble(int x, int z) {
        if (overrides != null && !Double.isNaN(overrides[(z << 4) + x])) {
            return overrides[(z << 4) + x];
        }
        if (!cache) {
            return stream.getDouble(this.x + x, this.z + z);
        }

        int index = (z << 4) + x;
        double value = data[index];
        if (!Double.isNaN(value)) {
            return value;
        }

        double sampled = stream.getDouble(this.x + x, this.z + z);
        data[index] = sampled;
        return sampled;
    }
}
