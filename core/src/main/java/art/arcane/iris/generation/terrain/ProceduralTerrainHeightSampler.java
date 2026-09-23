package art.arcane.iris.generation.terrain;

import art.arcane.volmlib.util.cache.CacheKey;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.DoubleBinaryOperator;

public final class ProceduralTerrainHeightSampler {
    private static final int MAXIMUM_NODES = 262_144;

    private final DoubleBinaryOperator source;
    private final int step;
    private final AtomicReferenceArray<Node> nodes;

    public ProceduralTerrainHeightSampler(DoubleBinaryOperator source, int step) {
        if (step != 1 && step != 2 && step != 4 && step != 8) {
            throw new IllegalArgumentException("Terrain sampling step must be 1, 2, 4, or 8");
        }
        this.source = Objects.requireNonNull(source, "Procedural terrain height source");
        this.step = step;
        nodes = step == 1 ? null : new AtomicReferenceArray<>(MAXIMUM_NODES);
    }

    public double sample(double x, double z) {
        if (step == 1) {
            return source.applyAsDouble(x, z);
        }
        double lowerX = Math.floor(x / step) * step;
        double lowerZ = Math.floor(z / step) * step;
        double dx = (x - lowerX) / step;
        double dz = (z - lowerZ) / step;
        double northWest = node(lowerX, lowerZ);
        double north = dx == 0D ? northWest
                : northWest + (node(lowerX + step, lowerZ) - northWest) * dx;
        if (dz == 0D) {
            return north;
        }
        double southWest = node(lowerX, lowerZ + step);
        double south = dx == 0D ? southWest
                : southWest + (node(lowerX + step, lowerZ + step) - southWest) * dx;
        return north + (south - north) * dz;
    }

    int cachedNodeCount() {
        if (nodes == null) {
            return 0;
        }
        int count = 0;
        for (int index = 0; index < nodes.length(); index++) {
            if (nodes.get(index) != null) {
                count++;
            }
        }
        return count;
    }

    private double node(double x, double z) {
        long xBits = Double.doubleToLongBits(x);
        long zBits = Double.doubleToLongBits(z);
        int index = (int) CacheKey.mix(xBits ^ Long.rotateLeft(zBits, 32)) & (MAXIMUM_NODES - 1);
        Node cached = nodes.get(index);
        if (cached != null && cached.xBits() == xBits && cached.zBits() == zBits) {
            return cached.height();
        }
        double height = source.applyAsDouble(x, z);
        if (!Double.isFinite(height)) {
            throw new IllegalStateException("Nonfinite procedural terrain height at " + x + "," + z);
        }
        nodes.set(index, new Node(xBits, zBits, height));
        return height;
    }

    private record Node(long xBits, long zBits, double height) {
    }
}
