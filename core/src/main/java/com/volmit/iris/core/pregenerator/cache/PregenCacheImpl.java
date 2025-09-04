package com.volmit.iris.core.pregenerator.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.Scheduler;
import com.volmit.iris.Iris;
import com.volmit.iris.util.data.KCache;
import com.volmit.iris.util.data.Varint;
import com.volmit.iris.util.documentation.ChunkCoordinates;
import com.volmit.iris.util.documentation.RegionCoordinates;
import com.volmit.iris.util.io.IO;
import com.volmit.iris.util.parallel.HyperLock;
import lombok.RequiredArgsConstructor;
import net.jpountz.lz4.LZ4BlockInputStream;
import net.jpountz.lz4.LZ4BlockOutputStream;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

@RequiredArgsConstructor
class PregenCacheImpl implements PregenCache {
    private static final int SIZE = 32;
    private final File directory;
    private final HyperLock hyperLock = new HyperLock(SIZE * 2, true);
    private final LoadingCache<Pos, Plate> cache = Caffeine.newBuilder()
            .expireAfterAccess(10, TimeUnit.SECONDS)
            .executor(KCache.EXECUTOR)
            .scheduler(Scheduler.systemScheduler())
            .maximumSize(SIZE)
            .removalListener(this::onRemoval)
            .evictionListener(this::onRemoval)
            .build(this::load);

    @ChunkCoordinates
    public boolean isChunkCached(int x, int z) {
        var plate = cache.get(new Pos(x >> 10, z >> 10));
        if (plate == null) return false;
        return plate.isCached((x >> 5) & 31, (z >> 5) & 31, r -> r.isCached(x & 31, z & 31));
    }

    @RegionCoordinates
    public boolean isRegionCached(int x, int z) {
        var plate = cache.get(new Pos(x >> 5, z >> 5));
        if (plate == null) return false;
        return plate.isCached(x & 31, z & 31, Region::isCached);
    }

    @ChunkCoordinates
    public void cacheChunk(int x, int z) {
        var plate = cache.get(new Pos(x >> 10, z >> 10));
        plate.cache((x >> 5) & 31, (z >> 5) & 31, r -> r.cache(x & 31, z & 31));
    }

    @RegionCoordinates
    public void cacheRegion(int x, int z) {
        var plate = cache.get(new Pos(x >> 5, z >> 5));
        plate.cache(x & 31, z & 31, Region::cache);
    }

    public void write() {
        cache.asMap().values().forEach(this::write);
    }

    private Plate load(Pos key) {
        hyperLock.lock(key.x, key.z);
        try {
            File file = fileForPlate(key);
            if (!file.exists()) return new Plate(key);
            try (var in = new DataInputStream(new LZ4BlockInputStream(new FileInputStream(file)))) {
                return new Plate(key, in);
            } catch (IOException e){
                Iris.error("Failed to read pregen cache " + file);
                Iris.reportError(e);
                e.printStackTrace();
                return new Plate(key);
            }
        } finally {
            hyperLock.unlock(key.x, key.z);
        }
    }

    private void write(Plate plate) {
        hyperLock.lock(plate.pos.x, plate.pos.z);
        try {
            File file = fileForPlate(plate.pos);
            try {
                IO.write(file, out -> new DataOutputStream(new LZ4BlockOutputStream(out)), plate::write);
            } catch (IOException e) {
                Iris.error("Failed to write pregen cache " + file);
                Iris.reportError(e);
                e.printStackTrace();
            }
        } finally {
            hyperLock.unlock(plate.pos.x, plate.pos.z);
        }
    }

    private void onRemoval(@Nullable Pos key, @Nullable Plate plate, RemovalCause cause) {
        if (plate == null) return;
        write(plate);
    }

    private File fileForPlate(Pos pos) {
        if (!directory.exists() && !directory.mkdirs())
            throw new IllegalStateException("Cannot create directory: " + directory.getAbsolutePath());
        return new File(directory, "c." + pos.x + "." + pos.z + ".lz4b");
    }

    private static class Plate {
        private final Pos pos;
        private short count;
        private Region[] regions;

        public Plate(Pos pos) {
            this.pos = pos;
            count = 0;
            regions = new Region[1024];
        }

        public Plate(Pos pos, DataInput in) throws IOException {
            this.pos = pos;
            count = (short) Varint.readSignedVarInt(in);
            if (count == 1024) return;
            regions = new Region[1024];
            for (int i = 0; i < 1024; i++) {
                if (in.readBoolean()) continue;
                regions[i] = new Region(in);
            }
        }

        public boolean isCached(int x, int z, Predicate<Region> predicate) {
            if (count == 1024) return true;
            Region region = regions[x * 32 + z];
            if (region == null) return false;
            return predicate.test(region);
        }

        public void cache(int x, int z, Predicate<Region> predicate) {
            if (count == 1024) return;
            Region region = regions[x * 32 + z];
            if (region == null) regions[x * 32 + z] = region = new Region();
            if (predicate.test(region)) count++;
        }

        public void write(DataOutput out) throws IOException {
            Varint.writeSignedVarInt(count, out);
            if (count == 1024) return;
            for (Region region : regions) {
                out.writeBoolean(region == null);
                if (region == null) continue;
                region.write(out);
            }
        }
    }

    private static class Region {
        private short count;
        private long[] words;

        public Region() {
            count = 0;
            words = new long[64];
        }

        public Region(DataInput in) throws IOException {
            count = (short) Varint.readSignedVarInt(in);
            if (count == 1024) return;
            words = new long[64];
            for (int i = 0; i < 64; i++) {
                words[i] = Varint.readUnsignedVarLong(in);
            }
        }

        public boolean cache() {
            if (count == 1024) return false;
            count = 1024;
            words = null;
            return true;
        }

        public boolean cache(int x, int z) {
            if (count == 1024) return false;

            int i = x * 32 + z;
            int w = i >> 6;
            long b = 1L << (i & 63);

            var cur = (words[w] & b) != 0;
            if (cur) return false;

            if (++count == 1024) {
                words = null;
                return true;
            } else words[w] |= b;
            return false;
        }

        public boolean isCached() {
            return count == 1024;
        }

        public boolean isCached(int x, int z) {
            int i = x * 32 + z;
            return count == 1024 || (words[i >> 6] &  1L << (i & 63)) != 0;
        }

        public void write(DataOutput out) throws IOException {
            Varint.writeSignedVarInt(count, out);
            if (isCached()) return;
            for (long word : words) {
                Varint.writeUnsignedVarLong(word, out);
            }
        }
    }

    private record Pos(int x, int z) {}
}
