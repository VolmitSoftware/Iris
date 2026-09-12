package art.arcane.iris.world.pregen;

import art.arcane.iris.generation.runtime.PreservationRegistry;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisServices;
import art.arcane.volmlib.util.data.Varint;
import art.arcane.volmlib.util.documentation.ChunkCoordinates;
import art.arcane.volmlib.util.documentation.RegionCoordinates;
import art.arcane.volmlib.util.io.IO;
import art.arcane.volmlib.util.math.PowerOfTwoCoordinates;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.jpountz.lz4.LZ4BlockInputStream;
import net.jpountz.lz4.LZ4BlockOutputStream;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class PregenCacheImpl implements PregenCache {
    // Daemon threads: this static pool is never shut down, and non-daemon threads here block JVM exit
    // (test workers and integrated servers) the moment this class is initialized.
    private static final ExecutorService DISPATCHER = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "Iris Pregen Cache Dispatcher");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicBoolean DISPATCHER_REGISTERED = new AtomicBoolean();
    private static final int SIZE = 1024;
    private static final int RETIRED_WRITE_RETRIES = 4;

    private final File directory;
    private final int maxSize;
    private final AtomicBoolean missingStorageReported = new AtomicBoolean();
    private final Object residencyLock = new Object();
    private final Long2ObjectLinkedOpenHashMap<Plate> cache = new Long2ObjectLinkedOpenHashMap<>();
    private volatile Plate recent;

    public PregenCacheImpl(File directory, int maxSize) {
        this.directory = directory;
        this.maxSize = maxSize;
        PreservationRegistry preservation = IrisServices.getOrNull(PreservationRegistry.class);
        if (preservation != null && DISPATCHER_REGISTERED.compareAndSet(false, true)) {
            preservation.register(DISPATCHER);
        }
    }

    @Override
    @ChunkCoordinates
    public boolean isChunkCached(int x, int z) {
        return getPlate(PowerOfTwoCoordinates.floorDivPow2(x, 10), PowerOfTwoCoordinates.floorDivPow2(z, 10)).isCached(
                PowerOfTwoCoordinates.localMaskPow2(PowerOfTwoCoordinates.floorDivPow2(x, 5), PowerOfTwoCoordinates.REGION_CHUNK_BITS),
                PowerOfTwoCoordinates.localMaskPow2(PowerOfTwoCoordinates.floorDivPow2(z, 5), PowerOfTwoCoordinates.REGION_CHUNK_BITS),
                region -> region.isCached(
                        PowerOfTwoCoordinates.localMaskPow2(x, PowerOfTwoCoordinates.REGION_CHUNK_BITS),
                        PowerOfTwoCoordinates.localMaskPow2(z, PowerOfTwoCoordinates.REGION_CHUNK_BITS)
                )
        );
    }

    @Override
    @RegionCoordinates
    public boolean isRegionCached(int x, int z) {
        return getPlate(PowerOfTwoCoordinates.floorDivPow2(x, 5), PowerOfTwoCoordinates.floorDivPow2(z, 5)).isCached(
                PowerOfTwoCoordinates.localMaskPow2(x, PowerOfTwoCoordinates.REGION_CHUNK_BITS),
                PowerOfTwoCoordinates.localMaskPow2(z, PowerOfTwoCoordinates.REGION_CHUNK_BITS),
                Region::isCached
        );
    }

    @Override
    @ChunkCoordinates
    public void cacheChunk(int x, int z) {
        int plateX = PowerOfTwoCoordinates.floorDivPow2(x, 10);
        int plateZ = PowerOfTwoCoordinates.floorDivPow2(z, 10);
        int regionX = PowerOfTwoCoordinates.localMaskPow2(PowerOfTwoCoordinates.floorDivPow2(x, 5), PowerOfTwoCoordinates.REGION_CHUNK_BITS);
        int regionZ = PowerOfTwoCoordinates.localMaskPow2(PowerOfTwoCoordinates.floorDivPow2(z, 5), PowerOfTwoCoordinates.REGION_CHUNK_BITS);
        int localX = PowerOfTwoCoordinates.localMaskPow2(x, PowerOfTwoCoordinates.REGION_CHUNK_BITS);
        int localZ = PowerOfTwoCoordinates.localMaskPow2(z, PowerOfTwoCoordinates.REGION_CHUNK_BITS);

        for (int attempt = 0; attempt < RETIRED_WRITE_RETRIES; attempt++) {
            Plate plate = getPlate(plateX, plateZ);
            plate.cache(regionX, regionZ, region -> region.cache(localX, localZ));
            if (!plate.retired) {
                return;
            }
        }
    }

    @Override
    @RegionCoordinates
    public void cacheRegion(int x, int z) {
        int plateX = PowerOfTwoCoordinates.floorDivPow2(x, 5);
        int plateZ = PowerOfTwoCoordinates.floorDivPow2(z, 5);
        int regionX = PowerOfTwoCoordinates.localMaskPow2(x, PowerOfTwoCoordinates.REGION_CHUNK_BITS);
        int regionZ = PowerOfTwoCoordinates.localMaskPow2(z, PowerOfTwoCoordinates.REGION_CHUNK_BITS);

        for (int attempt = 0; attempt < RETIRED_WRITE_RETRIES; attempt++) {
            Plate plate = getPlate(plateX, plateZ);
            plate.cache(regionX, regionZ, Region::cache);
            if (!plate.retired) {
                return;
            }
        }
    }

    @Override
    public void write() {
        List<Plate> resident;
        synchronized (residencyLock) {
            if (cache.isEmpty()) {
                return;
            }
            resident = new ArrayList<>(cache.values());
        }

        writePlates(resident);
    }

    @Override
    public void trim(long unloadDuration) {
        synchronized (residencyLock) {
            if (cache.isEmpty()) {
                return;
            }

            long threshold = System.currentTimeMillis() - unloadDuration;
            List<Plate> resident = new ArrayList<>(cache.size());
            Iterator<Plate> iterator = cache.values().iterator();
            while (iterator.hasNext()) {
                Plate plate = iterator.next();
                if (plate.lastAccess < threshold) {
                    iterator.remove();
                    plate.retired = true;
                }
                resident.add(plate);
            }

            writePlates(resident);
        }
    }

    private Plate getPlate(int x, int z) {
        long key = key(x, z);
        Plate memo = recent;
        if (memo != null && memo.key == key && !memo.retired) {
            return memo;
        }

        return residePlate(key, x, z);
    }

    private Plate residePlate(long key, int x, int z) {
        Plate resolved;
        synchronized (residencyLock) {
            resolved = cache.getAndMoveToFirst(key);
            if (resolved == null) {
                resolved = readPlate(key, x, z);
                cache.putAndMoveToFirst(key, resolved);
                List<Plate> evicted = null;
                while (cache.size() > maxSize) {
                    Plate removed = cache.removeLast();
                    removed.retired = true;
                    if (evicted == null) {
                        evicted = new ArrayList<>(2);
                    }
                    evicted.add(removed);
                }
                if (evicted != null) {
                    writePlates(evicted);
                }
            }
        }

        recent = resolved;
        return resolved;
    }

    private void writePlates(List<Plate> plates) {
        if (plates.isEmpty()) {
            return;
        }

        List<CompletableFuture<Void>> futures = new ArrayList<>(plates.size());
        for (Plate plate : plates) {
            if (!plate.dirty.get()) {
                continue;
            }
            futures.add(CompletableFuture.runAsync(() -> writePlate(plate), DISPATCHER));
        }

        for (CompletableFuture<Void> future : futures) {
            future.join();
        }
    }

    private Plate readPlate(long key, int x, int z) {
        File file = fileForPlate(x, z);
        if (!file.exists()) {
            return new Plate(key, x, z);
        }

        try (DataInputStream input = new DataInputStream(new LZ4BlockInputStream(new FileInputStream(file)))) {
            return readPlate(key, x, z, input);
        } catch (IOException e) {
            IrisLogging.error("Failed to read pregen cache " + file);
            IrisLogging.reportError(e);
        }

        return new Plate(key, x, z);
    }

    private void writePlate(Plate plate) {
        synchronized (plate) {
            if (!plate.dirty.compareAndSet(true, false)) {
                return;
            }
            if (!prepareCacheDirectory()) {
                plate.dirty.set(true);
                return;
            }

            File file = null;
            try {
                file = fileForPlate(plate.x, plate.z);
                IO.write(file, output -> new DataOutputStream(new LZ4BlockOutputStream(output)), plate::write);
            } catch (Throwable e) {
                plate.dirty.set(true);
                IrisLogging.error("Failed to write pregen cache " + (file != null ? file : "c." + plate.x + "." + plate.z));
                IrisLogging.reportError(e);
            }
        }
    }

    /**
     * Creates the cache directory under an existing parent only. This is a cache: when the storage it lives
     * in has been deleted the plate is dropped rather than rebuilt, so a trim cannot resurrect a world tree.
     */
    private boolean prepareCacheDirectory() {
        if (directory.isDirectory()) {
            return true;
        }
        File parent = directory.getParentFile();
        if (parent == null || !parent.isDirectory()) {
            if (missingStorageReported.compareAndSet(false, true)) {
                IrisLogging.warn("Pregen cache storage is gone at %s; cached plates are being dropped.",
                        directory.getAbsolutePath());
            }
            return false;
        }
        if (!directory.mkdirs() && !directory.isDirectory()) {
            throw new IllegalStateException("Cannot create directory: " + directory.getAbsolutePath());
        }
        return true;
    }

    private File fileForPlate(int x, int z) {
        return new File(directory, "c." + x + "." + z + ".lz4b");
    }

    private static long key(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xffffffffL);
    }

    private interface RegionPredicate {
        boolean test(Region region);
    }

    private enum CacheResult {
        NOOP,
        SET,
        COMPLETED
    }

    private interface RegionCacheOp {
        CacheResult apply(Region region);
    }

    private static class Plate {
        private final long key;
        private final int x;
        private final int z;
        private final AtomicInteger count;
        private final AtomicReferenceArray<Region> regions;
        private final AtomicBoolean dirty = new AtomicBoolean();
        private volatile long lastAccess;
        private volatile boolean retired;

        private Plate(long key, int x, int z) {
            this(key, x, z, 0, new AtomicReferenceArray<>(SIZE));
        }

        private Plate(long key, int x, int z, int count, AtomicReferenceArray<Region> regions) {
            this.key = key;
            this.x = x;
            this.z = z;
            this.count = new AtomicInteger(count);
            this.regions = regions;
            this.lastAccess = System.currentTimeMillis();
        }

        private int completed() {
            return Math.min(count.get(), SIZE);
        }

        private boolean cache(int x, int z, RegionCacheOp op) {
            lastAccess = System.currentTimeMillis();
            if (completed() == SIZE) {
                return false;
            }

            AtomicReferenceArray<Region> current = regions;
            if (current == null) {
                return false;
            }

            int index = PowerOfTwoCoordinates.packLocal32(x, z);
            Region region = current.get(index);
            if (region == null) {
                Region created = new Region();
                region = current.compareAndSet(index, null, created) ? created : current.get(index);
            }

            CacheResult result = op.apply(region);
            if (result == CacheResult.NOOP) {
                return false;
            }

            dirty.set(true);
            if (result != CacheResult.COMPLETED) {
                return false;
            }

            return count.incrementAndGet() >= SIZE;
        }

        private boolean isCached(int x, int z, RegionPredicate predicate) {
            lastAccess = System.currentTimeMillis();
            if (completed() == SIZE) {
                return true;
            }

            AtomicReferenceArray<Region> current = regions;
            if (current == null) {
                return true;
            }

            Region region = current.get(PowerOfTwoCoordinates.packLocal32(x, z));
            if (region == null) {
                return false;
            }
            return predicate.test(region);
        }

        private void write(DataOutput output) throws IOException {
            int total = completed();
            Varint.writeSignedVarInt(total, output);
            AtomicReferenceArray<Region> current = regions;
            if (total == SIZE || current == null) {
                return;
            }

            for (int index = 0; index < SIZE; index++) {
                Region region = current.get(index);
                output.writeBoolean(region == null);
                if (region != null) {
                    region.write(output);
                }
            }
        }
    }

    private static class Region {
        private final AtomicInteger count;
        private final AtomicLongArray words;

        private Region() {
            this(0, new AtomicLongArray(64));
        }

        private Region(int count, AtomicLongArray words) {
            this.count = new AtomicInteger(count);
            this.words = words;
        }

        private int completed() {
            return Math.min(count.get(), SIZE);
        }

        private CacheResult cache() {
            return count.getAndSet(SIZE) >= SIZE ? CacheResult.NOOP : CacheResult.COMPLETED;
        }

        private CacheResult cache(int x, int z) {
            if (completed() == SIZE) {
                return CacheResult.NOOP;
            }

            AtomicLongArray value = words;
            if (value == null) {
                return CacheResult.NOOP;
            }

            int index = PowerOfTwoCoordinates.packLocal32(x, z);
            int wordIndex = index >> 6;
            long bit = 1L << (index & 63);
            while (true) {
                long current = value.get(wordIndex);
                if ((current & bit) != 0L) {
                    return CacheResult.NOOP;
                }
                if (value.compareAndSet(wordIndex, current, current | bit)) {
                    break;
                }
            }

            return count.incrementAndGet() >= SIZE ? CacheResult.COMPLETED : CacheResult.SET;
        }

        private boolean isCached() {
            return completed() == SIZE;
        }

        private boolean isCached(int x, int z) {
            if (completed() == SIZE) {
                return true;
            }

            AtomicLongArray value = words;
            if (value == null) {
                return true;
            }

            int index = PowerOfTwoCoordinates.packLocal32(x, z);
            return (value.get(index >> 6) & (1L << (index & 63))) != 0L;
        }

        private void write(DataOutput output) throws IOException {
            int total = completed();
            Varint.writeSignedVarInt(total, output);
            AtomicLongArray value = words;
            if (total == SIZE || value == null) {
                return;
            }

            for (int index = 0; index < value.length(); index++) {
                Varint.writeUnsignedVarLong(value.get(index), output);
            }
        }
    }

    private static Plate readPlate(long key, int x, int z, DataInput input) throws IOException {
        int count = Varint.readSignedVarInt(input);
        if (count == SIZE) {
            return new Plate(key, x, z, SIZE, null);
        }

        AtomicReferenceArray<Region> regions = new AtomicReferenceArray<>(SIZE);
        for (int index = 0; index < SIZE; index++) {
            boolean isNull = input.readBoolean();
            if (!isNull) {
                regions.set(index, readRegion(input));
            }
        }

        return new Plate(key, x, z, count, regions);
    }

    private static Region readRegion(DataInput input) throws IOException {
        int count = Varint.readSignedVarInt(input);
        if (count == SIZE) {
            return new Region(SIZE, null);
        }

        AtomicLongArray words = new AtomicLongArray(64);
        for (int index = 0; index < words.length(); index++) {
            words.set(index, Varint.readUnsignedVarLong(input));
        }

        return new Region(count, words);
    }
}
