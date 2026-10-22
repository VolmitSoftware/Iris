package art.arcane.iris.generation.bake;

import art.arcane.volmlib.util.cache.CacheKey;

import java.util.concurrent.atomic.AtomicReferenceArray;

public final class SparseHeightMemo {
    private final AtomicReferenceArray<Node> nodes;
    private final int mask;

    public SparseHeightMemo(int capacity) {
        if (capacity <= 0 || Integer.bitCount(capacity) != 1) {
            throw new IllegalArgumentException("Sparse height memo capacity must be a power of two");
        }
        nodes = new AtomicReferenceArray<>(capacity);
        mask = capacity - 1;
    }

    public double get(int x, int z) {
        long key = ((long) x << 32) ^ (z & 0xffffffffL);
        Node node = nodes.getAcquire((int) CacheKey.mix(key) & mask);
        return node != null && node.key == key ? node.height : Double.NaN;
    }

    public void put(int x, int z, double height) {
        if (!Double.isFinite(height)) {
            return;
        }
        long key = ((long) x << 32) ^ (z & 0xffffffffL);
        nodes.setRelease((int) CacheKey.mix(key) & mask, new Node(key, height));
    }

    public void clear() {
        for (int index = 0; index < nodes.length(); index++) {
            nodes.setRelease(index, null);
        }
    }

    private record Node(long key, double height) {
    }
}
