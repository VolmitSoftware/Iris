package art.arcane.iris.generation.hydrology.grid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;

public final class HeightLattice {
    private static final int TILE_SHIFT = 5;
    private static final int COLUMN_TILE_SHIFT = 3;
    private static final int COLUMN_CELLS = 1 << 20;
    private static final CompletableFuture<Void> READY = CompletableFuture.completedFuture(null);
    private static final int[] NO_NODES = new int[0];
    private static final Tile[] NO_TILES = new Tile[0];

    private final HydrologyTerrainSource source;
    private final int cell;
    private final boolean baseOnly;
    private final int tileShift;
    private final int tileMask;
    private final int tileCells;
    private final int capacityTiles;
    private final ConcurrentHashMap<Long, Tile> tiles;
    private final AtomicLong clock;
    private final AtomicBoolean evicting;
    private final HeightLattice columns;

    public HeightLattice(HydrologyTerrainSource source, int cell, boolean baseOnly, int capacityCells) {
        this(source, cell, baseOnly, capacityCells, TILE_SHIFT, new HeightLattice(source, 1, baseOnly, COLUMN_CELLS, COLUMN_TILE_SHIFT, null));
    }

    private HeightLattice(HydrologyTerrainSource source, int cell, boolean baseOnly, int capacityCells, int tileShift, HeightLattice columns) {
        if (cell <= 0 || capacityCells <= 0) {
            throw new IllegalArgumentException("Invalid lattice cell=" + cell + " capacityCells=" + capacityCells);
        }
        this.source = Objects.requireNonNull(source, "source");
        this.cell = cell;
        this.baseOnly = baseOnly;
        this.tileShift = tileShift;
        this.tileMask = (1 << tileShift) - 1;
        this.tileCells = 1 << (2 * tileShift);
        this.capacityTiles = Math.ceilDiv(capacityCells, tileCells);
        this.tiles = new ConcurrentHashMap<>();
        this.clock = new AtomicLong();
        this.evicting = new AtomicBoolean();
        this.columns = columns;
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
        if (xs.length == 0) {
            return;
        }
        if (columns == null) {
            source.sample(xs, zs, out, baseOnly);
            return;
        }
        columns.resolve(new Scatter(xs, zs), xs.length, out);
    }

    private void resolve(Nodes nodes, int count, HydrologySampleBuffer out) {
        Pass pass = new Pass(nodes, out, clock.incrementAndGet());
        int[] open = null;
        int remaining = count;
        while (remaining > 0) {
            open = pass.run(open, remaining);
            remaining = open == null ? 0 : open.length;
        }
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

    private Tile tile(int tileX, int tileZ) {
        Long key = key(tileX, tileZ);
        Tile known = tiles.get(key);
        if (known != null) {
            return known;
        }
        return tiles.computeIfAbsent(key, ignored -> new Tile(tileCells));
    }

    private void evictOverflow() {
        if (tiles.size() <= capacityTiles || !evicting.compareAndSet(false, true)) {
            return;
        }
        try {
            List<IdleTile> idle = new ArrayList<>(tiles.size());
            for (Map.Entry<Long, Tile> entry : tiles.entrySet()) {
                Tile tile = entry.getValue();
                if (tile.pending.get() == 0) {
                    idle.add(new IdleTile(entry.getKey(), tile, tile.used));
                }
            }
            idle.sort(Comparator.comparingLong(IdleTile::used));
            int target = capacityTiles - (capacityTiles >> 3);
            for (int index = 0; index < idle.size() && tiles.size() > target; index++) {
                IdleTile candidate = idle.get(index);
                if (candidate.tile().pending.get() == 0) {
                    tiles.remove(candidate.key(), candidate.tile());
                }
            }
        } finally {
            evicting.set(false);
        }
    }

    static long key(int cellX, int cellZ) {
        long packed = ((long) cellX << 32) | (cellZ & 0xFFFFFFFFL);
        long mixed = (packed ^ (packed >>> 30)) * 0xbf58476d1ce4e5b9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94d049bb133111ebL;
        return mixed ^ (mixed >>> 31);
    }

    private static boolean await(CompletableFuture<Void> batch) {
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

    private static void copy(HydrologySampleBuffer samples, int sample, HydrologySampleBuffer out, int index) {
        out.height()[index] = samples.height()[sample];
        out.ocean()[index] = samples.ocean()[sample];
        out.rainfall()[index] = samples.rainfall()[sample];
        out.pondDensity()[index] = samples.pondDensity()[sample];
        out.permission()[index] = samples.permission()[sample];
        out.fluidProfile()[index] = samples.fluidProfile()[sample];
    }

    private final class Pass {
        private final Nodes nodes;
        private final HydrologySampleBuffer out;
        private final long stamp;
        private int[] claimedIndex = NO_NODES;
        private Tile[] claimedTile = NO_TILES;
        private int[] claimedLocal = NO_NODES;
        private int claimed;
        private int[] waitingIndex = NO_NODES;
        private Tile[] waitingTile = NO_TILES;
        private int[] waitingLocal = NO_NODES;
        private int waiting;
        private Tile current;
        private int currentX;
        private int currentZ;

        private Pass(Nodes nodes, HydrologySampleBuffer out, long stamp) {
            this.nodes = nodes;
            this.out = out;
            this.stamp = stamp;
        }

        private int[] run(int[] open, int remaining) {
            claimed = 0;
            waiting = 0;
            CompletableFuture<Void> batch = new CompletableFuture<>();
            for (int position = 0; position < remaining; position++) {
                visit(open == null ? position : open[position], batch);
            }
            if (claimed > 0) {
                sample(batch);
            }
            return awaitWaiting();
        }

        private void visit(int index, CompletableFuture<Void> batch) {
            int cellX = nodes.cellX(index);
            int cellZ = nodes.cellZ(index);
            Tile tile = tileOf(cellX >> tileShift, cellZ >> tileShift);
            int local = ((cellZ & tileMask) << tileShift) | (cellX & tileMask);
            while (true) {
                CompletableFuture<Void> mark = tile.marks.get(local);
                if (mark == READY) {
                    tile.copy(local, out, index);
                    return;
                }
                if (mark != null) {
                    addWaiting(index, tile, local);
                    return;
                }
                if (tile.marks.compareAndSet(local, null, batch)) {
                    tile.pending.incrementAndGet();
                    addClaimed(index, tile, local);
                    return;
                }
            }
        }

        private Tile tileOf(int tileX, int tileZ) {
            if (current == null || currentX != tileX || currentZ != tileZ) {
                current = tile(tileX, tileZ);
                currentX = tileX;
                currentZ = tileZ;
                current.used = stamp;
            }
            return current;
        }

        private void sample(CompletableFuture<Void> batch) {
            try {
                int[] xs = new int[claimed];
                int[] zs = new int[claimed];
                for (int position = 0; position < claimed; position++) {
                    xs[position] = nodes.cellX(claimedIndex[position]) * cell;
                    zs[position] = nodes.cellZ(claimedIndex[position]) * cell;
                }
                HydrologySampleBuffer samples = new HydrologySampleBuffer(claimed);
                source.sample(xs, zs, samples, baseOnly);
                for (int position = 0; position < claimed; position++) {
                    Tile tile = claimedTile[position];
                    int local = claimedLocal[position];
                    tile.store(local, samples, position);
                    tile.marks.set(local, READY);
                    tile.pending.decrementAndGet();
                    copy(samples, position, out, claimedIndex[position]);
                }
                batch.complete(null);
            } catch (Throwable failure) {
                try {
                    release(batch);
                } finally {
                    batch.completeExceptionally(failure);
                }
                throw failure;
            }
        }

        private void release(CompletableFuture<Void> batch) {
            for (int position = 0; position < claimed; position++) {
                Tile tile = claimedTile[position];
                if (tile.marks.compareAndSet(claimedLocal[position], batch, null)) {
                    tile.pending.decrementAndGet();
                }
            }
        }

        private int[] awaitWaiting() {
            int[] open = null;
            int unresolved = 0;
            for (int position = 0; position < waiting; position++) {
                Tile tile = waitingTile[position];
                int local = waitingLocal[position];
                CompletableFuture<Void> mark = tile.marks.get(local);
                if (mark != READY && (mark == null || !await(mark) || tile.marks.get(local) != READY)) {
                    if (open == null) {
                        open = new int[waiting - position];
                    }
                    open[unresolved++] = waitingIndex[position];
                    continue;
                }
                tile.copy(local, out, waitingIndex[position]);
            }
            return open == null ? null : Arrays.copyOf(open, unresolved);
        }

        private void addClaimed(int index, Tile tile, int local) {
            if (claimed == claimedIndex.length) {
                int grown = Math.max(16, claimed * 2);
                claimedIndex = Arrays.copyOf(claimedIndex, grown);
                claimedTile = Arrays.copyOf(claimedTile, grown);
                claimedLocal = Arrays.copyOf(claimedLocal, grown);
            }
            claimedIndex[claimed] = index;
            claimedTile[claimed] = tile;
            claimedLocal[claimed] = local;
            claimed++;
        }

        private void addWaiting(int index, Tile tile, int local) {
            if (waiting == waitingIndex.length) {
                int grown = Math.max(16, waiting * 2);
                waitingIndex = Arrays.copyOf(waitingIndex, grown);
                waitingTile = Arrays.copyOf(waitingTile, grown);
                waitingLocal = Arrays.copyOf(waitingLocal, grown);
            }
            waitingIndex[waiting] = index;
            waitingTile[waiting] = tile;
            waitingLocal[waiting] = local;
            waiting++;
        }
    }

    private static final class Tile {
        private final float[] height;
        private final boolean[] ocean;
        private final float[] rainfall;
        private final float[] pondDensity;
        private final byte[] permission;
        private final String[] fluidProfile;
        private final AtomicReferenceArray<CompletableFuture<Void>> marks;
        private final AtomicInteger pending = new AtomicInteger();
        private volatile long used;

        private Tile(int cells) {
            this.height = new float[cells];
            this.ocean = new boolean[cells];
            this.rainfall = new float[cells];
            this.pondDensity = new float[cells];
            this.permission = new byte[cells];
            this.fluidProfile = new String[cells];
            this.marks = new AtomicReferenceArray<>(cells);
        }

        private void store(int local, HydrologySampleBuffer samples, int sample) {
            height[local] = samples.height()[sample];
            ocean[local] = samples.ocean()[sample];
            rainfall[local] = samples.rainfall()[sample];
            pondDensity[local] = samples.pondDensity()[sample];
            permission[local] = samples.permission()[sample];
            fluidProfile[local] = samples.fluidProfile()[sample];
        }

        private void copy(int local, HydrologySampleBuffer out, int index) {
            out.height()[index] = height[local];
            out.ocean()[index] = ocean[local];
            out.rainfall()[index] = rainfall[local];
            out.pondDensity()[index] = pondDensity[local];
            out.permission()[index] = permission[local];
            out.fluidProfile()[index] = fluidProfile[local];
        }
    }

    private interface Nodes {
        int cellX(int index);

        int cellZ(int index);
    }

    private record IdleTile(Long key, Tile tile, long used) {
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
