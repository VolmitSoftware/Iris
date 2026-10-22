package art.arcane.iris.generation.bake;

import art.arcane.iris.generation.stream.ProvisionalSampling;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;

public final class ChunkPlanes {
    private static final VarHandle READY = MethodHandles.arrayElementVarHandle(long[].class);

    private final int chunkX;
    private final int chunkZ;
    private final AtomicReferenceArray<Plane> planes = new AtomicReferenceArray<>(BakePlane.PLANES.length);

    ChunkPlanes(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    public int chunkX() {
        return chunkX;
    }

    public int chunkZ() {
        return chunkZ;
    }

    public Plane planeIfPresent(BakePlane kind) {
        return planes.getAcquire(kind.ordinal());
    }

    public Plane plane(BakePlane kind) {
        int slot = kind.ordinal();
        Plane plane = planes.getAcquire(slot);
        if (plane != null) {
            return plane;
        }
        Plane created = new Plane(kind);
        Plane witness = planes.compareAndExchangeRelease(slot, null, created);
        return witness == null ? created : witness;
    }

    Object complete(BakePlane kind, PlaneSource source) {
        Plane plane = plane(kind);
        while (true) {
            if (plane.complete) {
                return plane.values;
            }
            Pending running = plane.filling.get();
            if (running != null) {
                if (running.owner == Thread.currentThread()) {
                    return plane.unpublishedFill(source, chunkX, chunkZ);
                }
                running.await();
                continue;
            }
            Pending pending = new Pending(Thread.currentThread());
            if (!plane.filling.compareAndSet(null, pending)) {
                continue;
            }
            try {
                return plane.fill(source, chunkX, chunkZ);
            } finally {
                plane.filling.set(null);
                pending.complete();
            }
        }
    }

    void release() {
        for (int slot = 0; slot < planes.length(); slot++) {
            planes.setRelease(slot, null);
        }
    }

    public static final class Plane {
        private final BakePlane kind;
        private final Object values;
        private final long[] ready = new long[4];
        private final AtomicReference<Pending> filling = new AtomicReference<>();
        private volatile boolean complete;

        private Plane(BakePlane kind) {
            this.kind = kind;
            this.values = kind.doubles() ? new double[256] : new Object[256];
        }

        public boolean ready(int index) {
            return complete || ((long) READY.getAcquire(ready, index >>> 6) & (1L << (index & 63))) != 0L;
        }

        public double doubleAt(int index) {
            return ((double[]) values)[index];
        }

        public Object objectAt(int index) {
            return ((Object[]) values)[index];
        }

        public void storeDouble(int index, double value) {
            ((double[]) values)[index] = value;
            markReady(index);
        }

        public void storeObject(int index, Object value) {
            ((Object[]) values)[index] = value;
            markReady(index);
        }

        private void markReady(int index) {
            READY.getAndBitwiseOrRelease(ready, index >>> 6, 1L << (index & 63));
        }

        private long[] missing() {
            long[] missing = new long[4];
            for (int word = 0; word < 4; word++) {
                missing[word] = ~(long) READY.getAcquire(ready, word);
            }
            return missing;
        }

        private Object fill(PlaneSource source, int chunkX, int chunkZ) {
            long[] missing = missing();
            Object computed = kind.doubles() ? new double[256] : new Object[256];
            long revision = ProvisionalSampling.revision();
            source.fill(kind, chunkX, chunkZ, missing, computed);
            if (revision != ProvisionalSampling.revision()) {
                return merge(missing, computed);
            }
            for (int index = 0; index < 256; index++) {
                if ((missing[index >>> 6] & (1L << (index & 63))) != 0L) {
                    if (kind.doubles()) {
                        ((double[]) values)[index] = ((double[]) computed)[index];
                    } else {
                        ((Object[]) values)[index] = ((Object[]) computed)[index];
                    }
                }
            }
            for (int word = 0; word < 4; word++) {
                READY.setRelease(ready, word, -1L);
            }
            complete = true;
            return values;
        }

        void fillColumns(PlaneSource source, int chunkX, int chunkZ, long[] columns, Object target) {
            long[] missing = new long[4];
            boolean any = false;
            if (!complete) {
                for (int word = 0; word < 4; word++) {
                    missing[word] = columns[word] & ~(long) READY.getAcquire(ready, word);
                    any |= missing[word] != 0L;
                }
            }
            if (!any) {
                copyColumns(columns, values, target);
                return;
            }
            long revision = ProvisionalSampling.revision();
            source.fill(kind, chunkX, chunkZ, missing, target);
            long[] known = new long[4];
            for (int word = 0; word < 4; word++) {
                known[word] = columns[word] & ~missing[word];
            }
            copyColumns(known, values, target);
            if (revision == ProvisionalSampling.revision()) {
                publish(missing, target);
            }
        }

        private void publish(long[] missing, Object computed) {
            copyColumns(missing, computed, values);
            for (int word = 0; word < 4; word++) {
                if (missing[word] != 0L) {
                    READY.getAndBitwiseOrRelease(ready, word, missing[word]);
                }
            }
        }

        private void copyColumns(long[] columns, Object from, Object to) {
            for (int word = 0; word < 4; word++) {
                long bits = columns[word];
                while (bits != 0L) {
                    int index = (word << 6) | Long.numberOfTrailingZeros(bits);
                    if (kind.doubles()) {
                        ((double[]) to)[index] = ((double[]) from)[index];
                    } else {
                        ((Object[]) to)[index] = ((Object[]) from)[index];
                    }
                    bits &= bits - 1L;
                }
            }
        }

        private Object unpublishedFill(PlaneSource source, int chunkX, int chunkZ) {
            long[] missing = missing();
            Object computed = kind.doubles() ? new double[256] : new Object[256];
            source.fill(kind, chunkX, chunkZ, missing, computed);
            return merge(missing, computed);
        }

        private Object merge(long[] missing, Object computed) {
            for (int index = 0; index < 256; index++) {
                if ((missing[index >>> 6] & (1L << (index & 63))) == 0L) {
                    if (kind.doubles()) {
                        ((double[]) computed)[index] = ((double[]) values)[index];
                    } else {
                        ((Object[]) computed)[index] = ((Object[]) values)[index];
                    }
                }
            }
            return computed;
        }
    }

    private static final class Pending {
        private final Thread owner;
        private boolean done;

        private Pending(Thread owner) {
            this.owner = owner;
        }

        private synchronized void complete() {
            done = true;
            notifyAll();
        }

        private synchronized void await() {
            boolean interrupted = false;
            while (!done) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
