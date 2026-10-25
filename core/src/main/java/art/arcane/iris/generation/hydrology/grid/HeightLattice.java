package art.arcane.iris.generation.hydrology.grid;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class HeightLattice {
    private final HydrologyTerrainSource source;
    private final int cell;
    private final boolean baseOnly;
    private final int capacityCells;
    private final ConcurrentHashMap<Long, Slot> cells;
    private final ConcurrentLinkedQueue<Long> insertionOrder;
    private final AtomicBoolean evicting;

    public HeightLattice(HydrologyTerrainSource source, int cell, boolean baseOnly, int capacityCells) {
        if (cell <= 0 || capacityCells <= 0) {
            throw new IllegalArgumentException("Invalid lattice cell=" + cell + " capacityCells=" + capacityCells);
        }
        this.source = Objects.requireNonNull(source, "source");
        this.cell = cell;
        this.baseOnly = baseOnly;
        this.capacityCells = capacityCells;
        this.cells = new ConcurrentHashMap<>();
        this.insertionOrder = new ConcurrentLinkedQueue<>();
        this.evicting = new AtomicBoolean();
    }

    public int cell() {
        return cell;
    }

    public void fill(int cellX0, int cellZ0, int width, int depth, HydrologySampleBuffer out) {
        fill(cellX0, cellZ0, width, depth, 1, out);
    }

    public void fill(int cellX0, int cellZ0, int width, int depth, int stride, HydrologySampleBuffer out) {
        if (width <= 0 || depth <= 0 || stride <= 0) {
            throw new IllegalArgumentException("Invalid lattice fill width=" + width + " depth=" + depth + " stride=" + stride);
        }
        int count = Math.multiplyExact(width, depth);
        requireCapacity(out, count);
        checkBlockRange(cellX0, width, stride);
        checkBlockRange(cellZ0, depth, stride);
        resolve(new Span(cellX0, cellZ0, width, stride), count, out);
    }

    public void fill(int[] cellXs, int[] cellZs, HydrologySampleBuffer out) {
        if (cellXs.length != cellZs.length) {
            throw new IllegalArgumentException("Lattice fill has " + cellXs.length + " x cells and " + cellZs.length + " z cells");
        }
        requireCapacity(out, cellXs.length);
        for (int index = 0; index < cellXs.length; index++) {
            Math.multiplyExact(cellXs[index], cell);
            Math.multiplyExact(cellZs[index], cell);
        }
        resolve(new Scatter(cellXs, cellZs), cellXs.length, out);
    }

    public void sampleColumns(int[] xs, int[] zs, HydrologySampleBuffer out) {
        if (xs.length != zs.length) {
            throw new IllegalArgumentException("Column sample has " + xs.length + " x and " + zs.length + " z coordinates");
        }
        requireCapacity(out, xs.length);
        if (xs.length > 0) {
            source.sample(xs, zs, out, baseOnly);
        }
    }

    private void resolve(Nodes nodes, int count, HydrologySampleBuffer out) {
        Slot[] slots = new Slot[count];
        int unresolved = count;
        while (unresolved > 0) {
            CompletableFuture<HydrologySampleBuffer> batch = claimAndSample(nodes, slots);
            unresolved = awaitForeign(batch, slots);
        }
        copy(slots, out);
        evictOverflow();
    }

    private static void requireCapacity(HydrologySampleBuffer out, int count) {
        if (out.height().length < count) {
            throw new IllegalArgumentException("Sample buffer capacity " + out.height().length + " is below " + count);
        }
    }

    private void checkBlockRange(int cell0, int extent, int stride) {
        Math.multiplyExact(cell0, cell);
        Math.multiplyExact(Math.addExact(cell0, Math.multiplyExact(extent - 1, stride)), cell);
    }

    private CompletableFuture<HydrologySampleBuffer> claimAndSample(Nodes nodes, Slot[] slots) {
        CompletableFuture<HydrologySampleBuffer> batch = new CompletableFuture<>();
        try {
            int[] xs = new int[slots.length];
            int[] zs = new int[slots.length];
            int claimed = claim(batch, nodes, slots, xs, zs);
            if (claimed > 0) {
                HydrologySampleBuffer samples = new HydrologySampleBuffer(claimed);
                source.sample(Arrays.copyOf(xs, claimed), Arrays.copyOf(zs, claimed), samples, baseOnly);
                batch.complete(samples);
            }
        } catch (Throwable failure) {
            try {
                release(batch, nodes, slots);
            } finally {
                batch.completeExceptionally(failure);
            }
            throw failure;
        }
        return batch;
    }

    private int claim(CompletableFuture<HydrologySampleBuffer> batch, Nodes nodes, Slot[] slots, int[] xs, int[] zs) {
        int claimed = 0;
        for (int index = 0; index < slots.length; index++) {
            if (slots[index] != null) {
                continue;
            }
            int cellX = nodes.cellX(index);
            int cellZ = nodes.cellZ(index);
            Long key = key(cellX, cellZ);
            Slot existing = cells.get(key);
            if (existing == null) {
                Slot fresh = new Slot(batch, claimed);
                existing = cells.putIfAbsent(key, fresh);
                if (existing == null) {
                    slots[index] = fresh;
                    insertionOrder.offer(key);
                    xs[claimed] = cellX * cell;
                    zs[claimed] = cellZ * cell;
                    claimed++;
                    continue;
                }
            }
            slots[index] = existing;
        }
        return claimed;
    }

    private void release(CompletableFuture<HydrologySampleBuffer> batch, Nodes nodes, Slot[] slots) {
        for (int index = 0; index < slots.length; index++) {
            Slot slot = slots[index];
            if (slot != null && slot.batch() == batch) {
                cells.remove(key(nodes.cellX(index), nodes.cellZ(index)), slot);
            }
        }
    }

    private static int awaitForeign(CompletableFuture<HydrologySampleBuffer> own, Slot[] slots) {
        int unresolved = 0;
        for (int index = 0; index < slots.length; index++) {
            CompletableFuture<HydrologySampleBuffer> batch = slots[index].batch();
            if (batch != own && !await(batch)) {
                slots[index] = null;
                unresolved++;
            }
        }
        return unresolved;
    }

    private static boolean await(CompletableFuture<HydrologySampleBuffer> batch) {
        if (!batch.isDone()) {
            try {
                batch.get();
            } catch (ExecutionException | CancellationException failure) {
                return false;
            } catch (InterruptedException interruption) {
                Thread.currentThread().interrupt();
                throw new CancellationException("Hydrology lattice wait interrupted.");
            }
        }
        return !batch.isCompletedExceptionally();
    }

    private static void copy(Slot[] slots, HydrologySampleBuffer out) {
        float[] height = out.height();
        boolean[] ocean = out.ocean();
        float[] rainfall = out.rainfall();
        float[] pondDensity = out.pondDensity();
        byte[] permission = out.permission();
        String[] fluidProfile = out.fluidProfile();
        for (int index = 0; index < slots.length; index++) {
            Slot slot = slots[index];
            HydrologySampleBuffer samples = slot.batch().join();
            int sample = slot.index();
            height[index] = samples.height()[sample];
            ocean[index] = samples.ocean()[sample];
            rainfall[index] = samples.rainfall()[sample];
            pondDensity[index] = samples.pondDensity()[sample];
            permission[index] = samples.permission()[sample];
            fluidProfile[index] = samples.fluidProfile()[sample];
        }
    }

    private void evictOverflow() {
        if (cells.size() <= capacityCells || !evicting.compareAndSet(false, true)) {
            return;
        }
        try {
            int excess = cells.size() - capacityCells;
            while (excess > 0) {
                Long key = insertionOrder.poll();
                if (key == null) {
                    return;
                }
                Slot slot = cells.get(key);
                if (slot == null) {
                    continue;
                }
                if (!slot.batch().isDone()) {
                    insertionOrder.offer(key);
                    excess--;
                } else if (cells.remove(key, slot)) {
                    excess--;
                }
            }
        } finally {
            evicting.set(false);
        }
    }

    private static Long key(int cellX, int cellZ) {
        return ((long) cellX << 32) | (cellZ & 0xFFFFFFFFL);
    }

    private record Slot(CompletableFuture<HydrologySampleBuffer> batch, int index) {
    }

    private interface Nodes {
        int cellX(int index);

        int cellZ(int index);
    }

    private record Span(int cellX0, int cellZ0, int width, int stride) implements Nodes {
        @Override
        public int cellX(int index) {
            return cellX0 + (index % width) * stride;
        }

        @Override
        public int cellZ(int index) {
            return cellZ0 + (index / width) * stride;
        }
    }

    private record Scatter(int[] cellXs, int[] cellZs) implements Nodes {
        @Override
        public int cellX(int index) {
            return cellXs[index];
        }

        @Override
        public int cellZ(int index) {
            return cellZs[index];
        }
    }
}
