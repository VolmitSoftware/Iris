package art.arcane.iris.studio.view;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

public final class PregenMapState {
    private static final int MAX_RASTER_SIZE = 1024;
    static final Color WAITING = new Color(29, 35, 46);

    private final PregenRenderSnapshot.Bounds bounds;
    private final int width;
    private final int height;
    private final int[] pixels;
    private final List<View> views = new ArrayList<>(1);
    private final ReentrantLock lock = new ReentrantLock();

    PregenMapState(PregenRenderSnapshot.Bounds bounds) {
        this.bounds = Objects.requireNonNull(bounds);
        width = (int) Math.min(MAX_RASTER_SIZE, bounds.width());
        height = (int) Math.min(MAX_RASTER_SIZE, bounds.height());
        pixels = new int[width * height];
        Arrays.fill(pixels, WAITING.getRGB());
    }

    PregenRenderSnapshot.Bounds bounds() {
        return bounds;
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }

    void submit(int chunkX, int chunkZ, Color color) {
        if (!bounds.contains(chunkX, chunkZ)) {
            return;
        }
        int x = (int) (((long) chunkX - bounds.minX()) * width / bounds.width());
        int z = (int) (((long) chunkZ - bounds.minZ()) * height / bounds.height());
        int index = z * width + x;
        int rgb = color.getRGB();
        lock.lock();
        try {
            if (pixels[index] == rgb) {
                return;
            }
            pixels[index] = rgb;
            for (int viewIndex = 0; viewIndex < views.size(); viewIndex++) {
                View view = views.get(viewIndex);
                view.dirty.set(index);
                view.pending = true;
            }
        } finally {
            lock.unlock();
        }
    }

    View attach(int[] target) {
        if (target.length != pixels.length) {
            throw new IllegalArgumentException("Map image must match the retained raster dimensions");
        }
        View view = new View(target);
        lock.lock();
        try {
            System.arraycopy(pixels, 0, target, 0, pixels.length);
            views.add(view);
        } finally {
            lock.unlock();
        }
        return view;
    }

    final class View {
        private final int[] target;
        private final BitSet dirty = new BitSet(pixels.length);
        private volatile boolean pending;
        private boolean disposed;

        private View(int[] target) {
            this.target = target;
        }

        boolean flush() {
            if (!pending) {
                return false;
            }
            lock.lock();
            try {
                if (disposed || !pending) {
                    return false;
                }
                for (int index = dirty.nextSetBit(0); index >= 0; index = dirty.nextSetBit(index + 1)) {
                    target[index] = pixels[index];
                }
                dirty.clear();
                pending = false;
                return true;
            } finally {
                lock.unlock();
            }
        }

        void close() {
            lock.lock();
            try {
                disposed = true;
                pending = false;
                dirty.clear();
                views.remove(this);
            } finally {
                lock.unlock();
            }
        }
    }
}
