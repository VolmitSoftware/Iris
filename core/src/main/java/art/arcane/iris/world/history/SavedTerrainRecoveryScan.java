package art.arcane.iris.world.history;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

final class SavedTerrainRecoveryScan implements AutoCloseable {
    private static final long WORKER_HEAP_BYTES = 768L * 1024 * 1024;
    private static final int MAX_REGION_BYTES = 64 * 1024 * 1024;
    private static final int MIN_PARALLEL_CHUNKS = 128;

    private final Path world;
    private final int regionByteLimit;
    private final SavedTerrainChunkReader.StatusReader[] readers;
    private final ExecutorService executor;
    private byte[] regionBuffer = new byte[0];

    SavedTerrainRecoveryScan(Path world, Limits limits) {
        this.world = world;
        regionByteLimit = limits.regionBytes();
        readers = new SavedTerrainChunkReader.StatusReader[limits.workers()];
        for (int index = 0; index < readers.length; index++) {
            readers[index] = new SavedTerrainChunkReader.StatusReader(world);
        }
        executor = readers.length == 1 ? null : Executors.newFixedThreadPool(readers.length,
                Thread.ofPlatform().daemon().name("Iris terrain recovery ", 1).factory());
    }

    static WorldChunkInventory scan(Path world, WorldChunkInventory inventory,
            WorldChunkInventory.ChunkPredicate retained) throws IOException {
        Runtime runtime = Runtime.getRuntime();
        try (SavedTerrainRecoveryScan scan = new SavedTerrainRecoveryScan(world,
                Limits.forRuntime(runtime.availableProcessors(), runtime.maxMemory()))) {
            return scan.filter(inventory, retained);
        }
    }

    WorldChunkInventory filter(WorldChunkInventory inventory, WorldChunkInventory.ChunkPredicate retained)
            throws IOException {
        return inventory.filterRegions((regionX, regionZ, allocated) ->
                filterRegion(new Region(regionX, regionZ, allocated), retained));
    }

    @Override
    public void close() throws IOException {
        if (executor != null) {
            executor.close();
        }
        IOException failure = null;
        for (SavedTerrainChunkReader.StatusReader reader : readers) {
            try {
                reader.close();
            } catch (IOException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private BitSet filterRegion(Region region, WorldChunkInventory.ChunkPredicate retained) throws IOException {
        requireNotInterrupted();
        BitSet accepted = new BitSet(1024);
        BitSet pending = (BitSet) region.allocated().clone();
        for (int slot = pending.nextSetBit(0); slot >= 0; slot = pending.nextSetBit(slot + 1)) {
            if (retained.test(region.chunkX(slot), region.chunkZ(slot))) {
                accepted.set(slot);
                pending.clear(slot);
            }
        }
        if (pending.isEmpty()) {
            return accepted;
        }
        Path file = world.resolve("region/r." + region.x() + "." + region.z() + ".mca");
        int snapshotLimit = Math.min(regionByteLimit, pending.cardinality() * 64 * 1024);
        Optional<SavedTerrainRegionSnapshot> captured = SavedTerrainRegionSnapshot.read(file, snapshotLimit, regionBuffer);
        if (captured.isEmpty()) {
            for (int slot = pending.nextSetBit(0); slot >= 0; slot = pending.nextSetBit(slot + 1)) {
                requireNotInterrupted();
                if (SavedTerrainChunk.hasTerrain(readers[0].readStatus(region.chunkX(slot), region.chunkZ(slot)))) {
                    accepted.set(slot);
                }
            }
            return accepted;
        }
        SavedTerrainRegionSnapshot snapshot = captured.get();
        regionBuffer = snapshot.bytes();
        for (int slot = pending.nextSetBit(0); slot >= 0; slot = pending.nextSetBit(slot + 1)) {
            requireNotInterrupted();
            if (snapshot.payload(slot).external()) {
                if (SavedTerrainChunk.hasTerrain(readers[0].readStatus(region.chunkX(slot), region.chunkZ(slot)))) {
                    accepted.set(slot);
                }
                pending.clear(slot);
            }
        }
        if (executor == null || pending.cardinality() < MIN_PARALLEL_CHUNKS) {
            accepted.or(decode(new DecodeRegion(region, pending, snapshot), 0, 1));
        } else {
            List<Future<BitSet>> tasks = new ArrayList<>(readers.length);
            DecodeRegion input = new DecodeRegion(region, pending, snapshot);
            for (int index = 0; index < readers.length; index++) {
                int worker = index;
                tasks.add(executor.submit(() -> decode(input, worker, readers.length)));
            }
            accepted.or(await(tasks));
        }
        snapshot.validateUnchanged();
        return accepted;
    }

    private BitSet decode(DecodeRegion input, int worker, int stride) throws IOException {
        BitSet accepted = new BitSet(1024);
        int ordinal = 0;
        for (int slot = input.pending().nextSetBit(0); slot >= 0; slot = input.pending().nextSetBit(slot + 1)) {
            if (ordinal++ % stride != worker) {
                continue;
            }
            requireNotInterrupted();
            String status = readers[worker].readStatus(input.snapshot().bytes(), input.snapshot().payload(slot),
                    input.region().chunkX(slot), input.region().chunkZ(slot));
            if (SavedTerrainChunk.hasTerrain(status)) {
                accepted.set(slot);
            }
        }
        return accepted;
    }

    private BitSet await(List<Future<BitSet>> tasks) throws IOException {
        BitSet accepted = new BitSet(1024);
        Throwable failure = null;
        boolean interrupted = false;
        try {
            for (Future<BitSet> task : tasks) {
                boolean completed = false;
                while (!completed) {
                    try {
                        accepted.or(task.get());
                        completed = true;
                    } catch (InterruptedException exception) {
                        interrupted = true;
                        if (failure == null) {
                            failure = new IOException("Interrupted while reading saved terrain", exception);
                        }
                    } catch (ExecutionException exception) {
                        if (failure == null) {
                            failure = exception.getCause();
                        } else if (failure != exception.getCause()) {
                            failure.addSuppressed(exception.getCause());
                        }
                        completed = true;
                    }
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        if (failure instanceof IOException exception) {
            throw exception;
        }
        if (failure instanceof RuntimeException exception) {
            throw exception;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        if (failure != null) {
            throw new IOException("Saved terrain recovery failed", failure);
        }
        return accepted;
    }

    private void requireNotInterrupted() throws IOException {
        if (Thread.currentThread().isInterrupted()) {
            throw new IOException("Saved terrain recovery interrupted");
        }
    }

    record Limits(int workers, int regionBytes) {
        Limits {
            if (workers < 1 || workers > 4 || regionBytes < 8192 || regionBytes > MAX_REGION_BYTES) {
                throw new IllegalArgumentException("Invalid saved terrain recovery limits");
            }
        }

        static Limits forRuntime(int processors, long heapBytes) {
            int workers = (int) Math.max(1, Math.min(Math.min(processors, 4), heapBytes / WORKER_HEAP_BYTES));
            int regionBytes = (int) Math.max(8192, Math.min(MAX_REGION_BYTES, heapBytes / 16));
            return new Limits(workers, regionBytes);
        }
    }

    private record Region(int x, int z, BitSet allocated) {
        int chunkX(int slot) {
            return x * 32 + (slot & 31);
        }

        int chunkZ(int slot) {
            return z * 32 + (slot >> 5);
        }
    }

    private record DecodeRegion(Region region, BitSet pending, SavedTerrainRegionSnapshot snapshot) {
    }
}
