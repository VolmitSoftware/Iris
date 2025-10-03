/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package com.volmit.iris.util.mantle;

import com.volmit.iris.Iris;
import com.volmit.iris.util.data.Varint;
import com.volmit.iris.util.documentation.ChunkCoordinates;
import com.volmit.iris.util.documentation.ChunkRelativeBlockCoordinates;
import com.volmit.iris.util.function.Consumer4;
import com.volmit.iris.util.io.CountingDataInputStream;
import com.volmit.iris.util.mantle.flag.MantleFlag;
import com.volmit.iris.util.matter.IrisMatter;
import com.volmit.iris.util.matter.Matter;
import com.volmit.iris.util.matter.MatterSlice;
import com.volmit.iris.util.parallel.AtomicBooleanArray;
import lombok.Getter;
import lombok.SneakyThrows;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Represents a mantle chunk. Mantle chunks contain sections of matter (see matter api)
 * Mantle Chunks are fully atomic & thread safe
 */
public class MantleChunk {
    @Getter
    private final int x;
    @Getter
    private final int z;
    private final AtomicBooleanArray flags;
    private final Object[] flagLocks;
    private final AtomicReferenceArray<Matter> sections;
    private final Semaphore ref = new Semaphore(Integer.MAX_VALUE, true);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * Create a mantle chunk
     *
     * @param sectionHeight the height of the world in sections (blocks >> 4)
     */
    @ChunkCoordinates
    public MantleChunk(int sectionHeight, int x, int z) {
        sections = new AtomicReferenceArray<>(sectionHeight);
        flags = new AtomicBooleanArray(MantleFlag.MAX_ORDINAL + 1);
        flagLocks = new Object[flags.length()];
        this.x = x;
        this.z = z;

        for (int i = 0; i < flags.length(); i++) {
            flagLocks[i] = new Object();
        }
    }

    /**
     * Load a mantle chunk from a data stream
     *
     * @param sectionHeight the height of the world in sections (blocks >> 4)
     * @param din           the data input
     * @throws IOException            shit happens
     * @throws ClassNotFoundException shit happens
     */
    public MantleChunk(int version, int sectionHeight, CountingDataInputStream din) throws IOException {
        this(sectionHeight, din.readByte(), din.readByte());
        int s = din.readByte();
        int l = version < 0 ? 16 : Varint.readUnsignedVarInt(din);

        if (version >= 1) {
            for (int i = 0; i < l;) {
                byte f = din.readByte();
                for (int j = 0; j < Byte.SIZE && i < flags.length(); j++, i++) {
                    flags.set(i, (f & (1 << j)) != 0);
                }
            }
        } else {
            for (int i = 0; i < flags.length() && i < l; i++) {
                flags.set(i, din.readBoolean());
            }
        }

        for (int i = 0; i < s; i++) {
            Iris.addPanic("read.section", "Section[" + i + "]");
            long size = din.readInt();
            if (size == 0) continue;
            long start = din.count();
            if (i >= sectionHeight) {
                din.skipTo(start + size);
                continue;
            }

            try {
                sections.set(i, Matter.readDin(din));
            } catch (IOException e) {
                long end = start + size;
                Iris.error("Failed to read chunk section, skipping it.");
                Iris.addPanic("read.byte.range", start + " " + end);
                Iris.addPanic("read.byte.current", din.count() + "");
                Iris.reportError(e);
                e.printStackTrace();
                Iris.panic();

                din.skipTo(end);
                TectonicPlate.addError();
            }
            if (din.count() != start + size) {
                throw new IOException("Chunk section read size mismatch!");
            }
        }
    }

    @SneakyThrows
    public void close() {
        closed.set(true);
        ref.acquire(Integer.MAX_VALUE);
        ref.release(Integer.MAX_VALUE);
    }

    public boolean inUse() {
        return ref.availablePermits() < Integer.MAX_VALUE;
    }

    public MantleChunk use() {
        if (closed.get()) throw new IllegalStateException("Chunk is closed!");
        ref.acquireUninterruptibly();
        if (closed.get()) {
            ref.release();
            throw new IllegalStateException("Chunk is closed!");
        }
        return this;
    }

    public void release() {
        ref.release();
    }

    public void copyFlags(MantleChunk chunk) {
        use();
        for (int i = 0; i < flags.length(); i++) {
            flags.set(i, chunk.flags.get(i));
        }
        release();
    }

    public void flag(MantleFlag flag, boolean f) {
        if (closed.get()) throw new IllegalStateException("Chunk is closed!");
        flags.set(flag.ordinal(), f);
    }

    public void raiseFlag(MantleFlag flag, Runnable r) {
        raiseFlag(null, flag, r);
    }

    public void raiseFlag(@Nullable MantleFlag guard, MantleFlag flag, Runnable r) {
        if (closed.get()) throw new IllegalStateException("Chunk is closed!");
        if (guard != null && isFlagged(guard)) return;
        synchronized (flagLocks[flag.ordinal()]) {
            if (flags.compareAndSet(flag.ordinal(), false, true)) {
                try {
                    r.run();
                } catch (RuntimeException | Error e) {
                    flags.set(flag.ordinal(), false);
                    throw e;
                }
            }
        }
    }

    public void raiseFlagUnchecked(MantleFlag flag, Runnable r) {
        if (closed.get()) throw new IllegalStateException("Chunk is closed!");
        if (flags.compareAndSet(flag.ordinal(), false, true)) {
            try {
                r.run();
            } catch (RuntimeException | Error e) {
                flags.set(flag.ordinal(), false);
                throw e;
            }
        }
    }

    public boolean isFlagged(MantleFlag flag) {
        return flags.get(flag.ordinal());
    }

    /**
     * Check if a section exists (same as get(section) != null)
     *
     * @param section the section (0 - (worldHeight >> 4))
     * @return true if it exists
     */
    @ChunkCoordinates
    public boolean exists(int section) {
        return get(section) != null;
    }

    /**
     * Get thje matter at the given section or null if it doesnt exist
     *
     * @param section the section (0 - (worldHeight >> 4))
     * @return the matter or null if it doesnt exist
     */
    @ChunkCoordinates
    public Matter get(int section) {
        return sections.get(section);
    }

    @Nullable
    @ChunkRelativeBlockCoordinates
    @SuppressWarnings("unchecked")
    public <T> T get(int x, int y, int z, Class<T> type) {
        return (T) getOrCreate(y >> 4)
                .slice(type)
                .get(x & 15, y & 15, z & 15);
    }

    /**
     * Clear all matter from this chunk
     */
    public void clear() {
        for (int i = 0; i < sections.length(); i++) {
            delete(i);
        }
    }

    /**
     * Delete the matter from the given section
     *
     * @param section the section (0 - (worldHeight >> 4))
     */
    @ChunkCoordinates
    public void delete(int section) {
        sections.set(section, null);
    }

    /**
     * Get or create a new matter section at the given section
     *
     * @param section the section (0 - (worldHeight >> 4))
     * @return the matter
     */
    @ChunkCoordinates
    public Matter getOrCreate(int section) {
        Matter matter = get(section);

        if (matter == null) {
            matter = new IrisMatter(16, 16, 16);
            if (!sections.compareAndSet(section, null, matter)) {
                matter = get(section);
            }
        }

        return matter;
    }

    /**
     * Write this chunk to a data stream
     *
     * @param dos the stream
     * @throws IOException shit happens
     */
    public void write(DataOutputStream dos) throws IOException {
        close();
        dos.writeByte(x);
        dos.writeByte(z);
        dos.writeByte(sections.length());
        Varint.writeUnsignedVarInt(flags.length(), dos);

        int count = flags.length();
        for (int i = 0; i < count;) {
            int f = 0;
            for (int j = 0; j < Byte.SIZE && i < flags.length(); j++, i++) {
                f |= flags.get(i) ? (1 << j) : 0;
            }
            dos.write(f);
        }

        var bytes = new ByteArrayOutputStream(8192);
        var sub = new DataOutputStream(bytes);
        for (int i = 0; i < sections.length(); i++) {
            trimSlice(i);

            if (exists(i)) {
                try {
                    Matter matter = get(i);
                    matter.writeDos(sub);
                    dos.writeInt(bytes.size());
                    bytes.writeTo(dos);
                } finally {
                    bytes.reset();
                }
            } else {
                dos.writeInt(0);
            }
        }
    }

    private void trimSlice(int i) {
        if (exists(i)) {
            Matter m = get(i);

            if (m.getSliceMap().isEmpty()) {
                sections.set(i, null);
            } else {
                m.trimSlices();
                if (m.getSliceMap().isEmpty()) {
                    sections.set(i, null);
                }
            }
        }
    }

    public <T> void iterate(Class<T> type, Consumer4<Integer, Integer, Integer, T> iterator) {
        for (int i = 0; i < sections.length(); i++) {
            int bs = (i << 4);
            Matter matter = get(i);

            if (matter != null) {
                MatterSlice<T> t = matter.getSlice(type);

                if (t != null) {
                    t.iterateSync((a, b, c, f) -> iterator.accept(a, b + bs, c, f));
                }
            }
        }
    }

    public void deleteSlices(Class<?> c) {
        for (int i = 0; i < sections.length(); i++) {
            Matter m = sections.get(i);
            if (m != null && m.hasSlice(c)) {
                m.deleteSlice(c);
            }
        }
    }

    public void trimSlices() {
        for (int i = 0; i < sections.length(); i++) {
            if (exists(i)) {
                trimSlice(i);
            }
        }
    }
}
