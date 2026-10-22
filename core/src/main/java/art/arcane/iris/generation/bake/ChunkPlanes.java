package art.arcane.iris.generation.bake;

import art.arcane.iris.generation.stream.ProvisionalSampling;

import java.util.concurrent.atomic.AtomicReferenceArray;

public final class ChunkPlanes {
    private final int chunkX;
    private final int chunkZ;
    private final AtomicReferenceArray<Object> planes = new AtomicReferenceArray<>(BakePlane.PLANES.length);
    private volatile ColumnBake columnBake;

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

    public Object published(BakePlane plane) {
        Object state = planes.getAcquire(plane.ordinal());
        return state instanceof Pending ? null : state;
    }

    Object plane(BakePlane plane, PlaneSource source) {
        int slot = plane.ordinal();
        while (true) {
            Object state = planes.getAcquire(slot);
            if (state != null && !(state instanceof Pending)) {
                return state;
            }
            if (state instanceof Pending pending) {
                if (pending.owner == Thread.currentThread()) {
                    return source.compute(plane, chunkX, chunkZ);
                }
                pending.await();
                continue;
            }
            Pending pending = new Pending(Thread.currentThread());
            if (!planes.compareAndSet(slot, null, pending)) {
                continue;
            }
            Object computed = null;
            boolean publishable = false;
            try {
                long revision = ProvisionalSampling.revision();
                computed = source.compute(plane, chunkX, chunkZ);
                publishable = revision == ProvisionalSampling.revision();
            } finally {
                planes.setRelease(slot, publishable ? computed : null);
                pending.complete();
            }
            return computed;
        }
    }

    ColumnBake columnBake() {
        return columnBake;
    }

    ColumnBake columnBake(PlaneSource source) {
        ColumnBake bake = columnBake;
        if (bake != null) {
            return bake;
        }
        long revision = ProvisionalSampling.revision();
        bake = source.assemble(this);
        if (revision == ProvisionalSampling.revision()) {
            columnBake = bake;
        }
        return bake;
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
