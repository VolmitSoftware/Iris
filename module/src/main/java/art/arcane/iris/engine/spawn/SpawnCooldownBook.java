/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.engine.spawn;

import art.arcane.volmlib.util.cache.CacheKey;
import com.volmit.data.SavedKey;
import com.volmit.data.StorageCodec;
import com.volmit.resource.ResourceId;
import com.volmit.world.PackWorld;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.ToLongFunction;

/**
 * When spawners last fired, in the world and in each chunk, which the host saves with the world so that a spawner's
 * rates hold across restarts. Spawners count by the key the pack names them by. Safe for concurrent use.
 */
final class SpawnCooldownBook {
    static final SavedKey<SpawnCooldownBook> SAVED = new SavedKey<>(ResourceId.parse("iris:spawn/cooldowns"), SpawnCooldownBook.class,
            codec(System::currentTimeMillis), () -> new SpawnCooldownBook(System::currentTimeMillis));

    private static final int FORMAT_VERSION = 1;

    private final LongSupplier clockMilliseconds;
    private final Map<String, Long> worldFiredAt = new ConcurrentHashMap<>();
    private final Map<Long, Map<String, Long>> chunkFiredAt = new ConcurrentHashMap<>();

    SpawnCooldownBook(LongSupplier clockMilliseconds) {
        this.clockMilliseconds = clockMilliseconds;
    }

    /** The book the world keeps. */
    static SpawnCooldownBook of(PackWorld world) {
        return world.running().orElseThrow().saved(SAVED);
    }

    /** How {@link #SAVED} is stored: the books read by this codec count time with {@code clockMilliseconds}. */
    static StorageCodec<SpawnCooldownBook> codec(LongSupplier clockMilliseconds) {
        return new BookCodec(clockMilliseconds);
    }

    /** Whether the spawner may fire in the world again, {@code intervalMilliseconds} after it last did. */
    boolean canSpawn(String spawner, long intervalMilliseconds) {
        return hasElapsed(worldFiredAt.get(spawner), intervalMilliseconds);
    }

    /** Notes that the spawner fired in the world now. */
    void spawned(String spawner) {
        worldFiredAt.put(spawner, clockMilliseconds.getAsLong());
    }

    /** Whether the spawner may fire in the chunk again, {@code intervalMilliseconds} after it last did there. */
    boolean canSpawnInChunk(int chunkX, int chunkZ, String spawner, long intervalMilliseconds) {
        Map<String, Long> spawnersOfChunk = chunkFiredAt.get(CacheKey.key(chunkX, chunkZ));
        return hasElapsed(spawnersOfChunk == null ? null : spawnersOfChunk.get(spawner), intervalMilliseconds);
    }

    /** Notes that the spawner fired in the chunk now. */
    void spawnedInChunk(int chunkX, int chunkZ, String spawner) {
        long firedAt = clockMilliseconds.getAsLong();
        chunkFiredAt.compute(CacheKey.key(chunkX, chunkZ), (chunk, spawnersOfChunk) -> {
            Map<String, Long> kept = spawnersOfChunk == null ? new ConcurrentHashMap<>() : spawnersOfChunk;
            kept.put(spawner, firedAt);
            return kept;
        });
    }

    /**
     * Forgets the cooldowns that ran out and those of spawners the pack no longer has, so the saved data does not grow.
     *
     * @param intervalMilliseconds the interval of the world rate of a spawner by its key, or a negative number for a spawner the pack lacks
     */
    void forgetExpired(ToLongFunction<String> intervalMilliseconds) {
        worldFiredAt.entrySet().removeIf(fired -> isForgotten(fired.getKey(), fired.getValue(), intervalMilliseconds));
        for (Long chunk : List.copyOf(chunkFiredAt.keySet())) {
            chunkFiredAt.computeIfPresent(chunk, (key, spawnersOfChunk) -> {
                spawnersOfChunk.entrySet().removeIf(fired -> isForgotten(fired.getKey(), fired.getValue(), intervalMilliseconds));
                return spawnersOfChunk.isEmpty() ? null : spawnersOfChunk;
            });
        }
    }

    private boolean isForgotten(String spawner, long firedAt, ToLongFunction<String> intervalMilliseconds) {
        long interval = intervalMilliseconds.applyAsLong(spawner);
        return interval < 0 || hasElapsed(firedAt, interval);
    }

    private boolean hasElapsed(Long firedAt, long intervalMilliseconds) {
        return firedAt == null || clockMilliseconds.getAsLong() - firedAt > intervalMilliseconds;
    }

    private record BookCodec(LongSupplier clockMilliseconds) implements StorageCodec<SpawnCooldownBook> {
        @Override
        public int version() {
            return FORMAT_VERSION;
        }

        @Override
        public void write(DataOutput output, SpawnCooldownBook book) throws IOException {
            writeFired(output, book.worldFiredAt);
            List<Map.Entry<Long, Map<String, Long>>> chunks = new ArrayList<>(book.chunkFiredAt.entrySet());
            output.writeInt(chunks.size());
            for (Map.Entry<Long, Map<String, Long>> chunk : chunks) {
                output.writeLong(chunk.getKey());
                writeFired(output, chunk.getValue());
            }
        }

        @Override
        public SpawnCooldownBook read(DataInput input, int storedVersion) throws IOException {
            if (storedVersion != FORMAT_VERSION) {
                throw new IOException("Unsupported spawn cooldown format version " + storedVersion);
            }
            SpawnCooldownBook book = new SpawnCooldownBook(clockMilliseconds);
            readFired(input, book.worldFiredAt);
            int chunks = readCount(input);
            for (int chunk = 0; chunk < chunks; chunk++) {
                Map<String, Long> spawnersOfChunk = new ConcurrentHashMap<>();
                long key = input.readLong();
                readFired(input, spawnersOfChunk);
                if (!spawnersOfChunk.isEmpty()) {
                    book.chunkFiredAt.put(key, spawnersOfChunk);
                }
            }
            return book;
        }

        private static void writeFired(DataOutput output, Map<String, Long> firedAt) throws IOException {
            List<Map.Entry<String, Long>> snapshot = new ArrayList<>(firedAt.entrySet());
            output.writeInt(snapshot.size());
            for (Map.Entry<String, Long> fired : snapshot) {
                output.writeUTF(fired.getKey());
                output.writeLong(fired.getValue());
            }
        }

        private static void readFired(DataInput input, Map<String, Long> firedAt) throws IOException {
            int count = readCount(input);
            for (int index = 0; index < count; index++) {
                firedAt.put(input.readUTF(), input.readLong());
            }
        }

        private static int readCount(DataInput input) throws IOException {
            int count = input.readInt();
            if (count < 0) {
                throw new IOException("Negative entry count " + count);
            }
            return count;
        }
    }
}
