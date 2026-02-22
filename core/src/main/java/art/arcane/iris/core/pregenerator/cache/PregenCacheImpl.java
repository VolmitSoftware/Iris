package art.arcane.iris.core.pregenerator.cache;

import art.arcane.iris.Iris;
import art.arcane.volmlib.util.data.Varint;
import art.arcane.volmlib.util.documentation.ChunkCoordinates;
import art.arcane.volmlib.util.documentation.RegionCoordinates;
import art.arcane.volmlib.util.io.IO;
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

public class PregenCacheImpl implements PregenCache {
    private static final ExecutorService DISPATCHER = Executors.newFixedThreadPool(4);
    private static final short SIZE = 1024;

    private final File directory;
    private final int maxSize;
    private final Long2ObjectLinkedOpenHashMap<Plate> cache = new Long2ObjectLinkedOpenHashMap<>();

    public PregenCacheImpl(File directory, int maxSize) {
        this.directory = directory;
        this.maxSize = maxSize;
    }

    @Override
    @ChunkCoordinates
    public boolean isChunkCached(int x, int z) {
        return getPlate(x >> 10, z >> 10).isCached(
                (x >> 5) & 31,
                (z >> 5) & 31,
                region -> region.isCached(x & 31, z & 31)
        );
    }

    @Override
    @RegionCoordinates
    public boolean isRegionCached(int x, int z) {
        return getPlate(x >> 5, z >> 5).isCached(
                x & 31,
                z & 31,
                Region::isCached
        );
    }

    @Override
    @ChunkCoordinates
    public void cacheChunk(int x, int z) {
        getPlate(x >> 10, z >> 10).cache(
                (x >> 5) & 31,
                (z >> 5) & 31,
                region -> region.cache(x & 31, z & 31)
        );
    }

    @Override
    @RegionCoordinates
    public void cacheRegion(int x, int z) {
        getPlate(x >> 5, z >> 5).cache(
                x & 31,
                z & 31,
                Region::cache
        );
    }

    @Override
    public void write() {
        if (cache.isEmpty()) {
            return;
        }

        List<CompletableFuture<Void>> futures = new ArrayList<>(cache.size());
        for (Plate plate : cache.values()) {
            if (!plate.dirty) {
                continue;
            }
            futures.add(CompletableFuture.runAsync(() -> writePlate(plate), DISPATCHER));
        }

        for (CompletableFuture<Void> future : futures) {
            future.join();
        }
    }

    @Override
    public void trim(long unloadDuration) {
        if (cache.isEmpty()) {
            return;
        }

        long threshold = System.currentTimeMillis() - unloadDuration;
        List<CompletableFuture<Void>> futures = new ArrayList<>(cache.size());
        Iterator<Plate> iterator = cache.values().iterator();
        while (iterator.hasNext()) {
            Plate plate = iterator.next();
            if (plate.lastAccess < threshold) {
                iterator.remove();
            }
            futures.add(CompletableFuture.runAsync(() -> writePlate(plate), DISPATCHER));
        }

        for (CompletableFuture<Void> future : futures) {
            future.join();
        }
    }

    private Plate getPlate(int x, int z) {
        long key = key(x, z);
        Plate plate = cache.getAndMoveToFirst(key);
        if (plate != null) {
            return plate;
        }

        Plate loaded = readPlate(x, z);
        cache.putAndMoveToFirst(key, loaded);

        if (cache.size() > maxSize) {
            List<CompletableFuture<Void>> futures = new ArrayList<>(cache.size() - maxSize);
            while (cache.size() > maxSize) {
                Plate evicted = cache.removeLast();
                futures.add(CompletableFuture.runAsync(() -> writePlate(evicted), DISPATCHER));
            }
            for (CompletableFuture<Void> future : futures) {
                future.join();
            }
        }

        return loaded;
    }

    private Plate readPlate(int x, int z) {
        File file = fileForPlate(x, z);
        if (!file.exists()) {
            return new Plate(x, z);
        }

        try (DataInputStream input = new DataInputStream(new LZ4BlockInputStream(new FileInputStream(file)))) {
            return readPlate(x, z, input);
        } catch (IOException e) {
            Iris.error("Failed to read pregen cache " + file);
            e.printStackTrace();
            Iris.reportError(e);
        }

        return new Plate(x, z);
    }

    private void writePlate(Plate plate) {
        if (!plate.dirty) {
            return;
        }

        File file = fileForPlate(plate.x, plate.z);
        try {
            IO.write(file, output -> new DataOutputStream(new LZ4BlockOutputStream(output)), plate::write);
            plate.dirty = false;
        } catch (IOException e) {
            Iris.error("Failed to write preen cache " + file);
            e.printStackTrace();
            Iris.reportError(e);
        }
    }

    private File fileForPlate(int x, int z) {
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Cannot create directory: " + directory.getAbsolutePath());
        }
        return new File(directory, "c." + x + "." + z + ".lz4b");
    }

    private static long key(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xffffffffL);
    }

    private interface RegionPredicate {
        boolean test(Region region);
    }

    private static class Plate {
        private final int x;
        private final int z;
        private short count;
        private Region[] regions;
        private boolean dirty;
        private long lastAccess;

        private Plate(int x, int z) {
            this(x, z, (short) 0, new Region[1024]);
        }

        private Plate(int x, int z, short count, Region[] regions) {
            this.x = x;
            this.z = z;
            this.count = count;
            this.regions = regions;
            this.lastAccess = System.currentTimeMillis();
        }

        private boolean cache(int x, int z, RegionPredicate predicate) {
            lastAccess = System.currentTimeMillis();
            if (count == SIZE) {
                return false;
            }

            int index = x * 32 + z;
            Region region = regions[index];
            if (region == null) {
                region = new Region();
                regions[index] = region;
            }

            if (!predicate.test(region)) {
                return false;
            }

            count++;
            if (count == SIZE) {
                regions = null;
            }
            dirty = true;
            return true;
        }

        private boolean isCached(int x, int z, RegionPredicate predicate) {
            lastAccess = System.currentTimeMillis();
            if (count == SIZE) {
                return true;
            }

            Region region = regions[x * 32 + z];
            if (region == null) {
                return false;
            }
            return predicate.test(region);
        }

        private void write(DataOutput output) throws IOException {
            Varint.writeSignedVarInt(count, output);
            if (regions == null) {
                return;
            }

            for (Region region : regions) {
                output.writeBoolean(region == null);
                if (region != null) {
                    region.write(output);
                }
            }
        }
    }

    private static class Region {
        private short count;
        private long[] words;

        private Region() {
            this((short) 0, new long[64]);
        }

        private Region(short count, long[] words) {
            this.count = count;
            this.words = words;
        }

        private boolean cache() {
            if (count == SIZE) {
                return false;
            }
            count = SIZE;
            words = null;
            return true;
        }

        private boolean cache(int x, int z) {
            if (count == SIZE) {
                return false;
            }

            long[] value = words;
            if (value == null) {
                return false;
            }

            int index = x * 32 + z;
            int wordIndex = index >> 6;
            long bit = 1L << (index & 63);
            boolean current = (value[wordIndex] & bit) != 0L;
            if (current) {
                return false;
            }

            count++;
            if (count == SIZE) {
                words = null;
                return true;
            }

            value[wordIndex] = value[wordIndex] | bit;
            return false;
        }

        private boolean isCached() {
            return count == SIZE;
        }

        private boolean isCached(int x, int z) {
            int index = x * 32 + z;
            if (count == SIZE) {
                return true;
            }
            return (words[index >> 6] & (1L << (index & 63))) != 0L;
        }

        private void write(DataOutput output) throws IOException {
            Varint.writeSignedVarInt(count, output);
            if (words == null) {
                return;
            }

            for (long word : words) {
                Varint.writeUnsignedVarLong(word, output);
            }
        }
    }

    private static Plate readPlate(int x, int z, DataInput input) throws IOException {
        int count = Varint.readSignedVarInt(input);
        if (count == 1024) {
            return new Plate(x, z, SIZE, null);
        }

        Region[] regions = new Region[1024];
        for (int i = 0; i < regions.length; i++) {
            boolean isNull = input.readBoolean();
            if (!isNull) {
                regions[i] = readRegion(input);
            }
        }

        return new Plate(x, z, (short) count, regions);
    }

    private static Region readRegion(DataInput input) throws IOException {
        int count = Varint.readSignedVarInt(input);
        if (count == 1024) {
            return new Region(SIZE, null);
        }

        long[] words = new long[64];
        for (int i = 0; i < words.length; i++) {
            words[i] = Varint.readUnsignedVarLong(input);
        }

        return new Region((short) count, words);
    }
}
