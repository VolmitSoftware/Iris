/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
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

package com.volmit.iris.util.matter;

import com.volmit.iris.Iris;
import com.volmit.iris.engine.data.cache.Cache;
import com.volmit.iris.util.data.Varint;
import com.volmit.iris.util.hunk.Hunk;
import com.volmit.iris.util.hunk.storage.MappedHunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public interface MatterSlice<T> extends Hunk<T> {
    Class<T> getType();

    void writeNode(T b, DataOutputStream dos) throws IOException;

    T readNode(DataInputStream din) throws IOException;

    <W> MatterWriter<W, T> writeInto(Class<W> mediumType);

    <W> MatterReader<W, T> readFrom(Class<W> mediumType);

    default Class<?> getClass(Object w) {
        Class<?> c = w.getClass();

        if (w instanceof World) {
            c = World.class;
        } else if (w instanceof BlockData) {
            c = BlockData.class;
        } else if (w instanceof Entity) {
            c = Entity.class;
        }

        return c;
    }

    default boolean writeInto(Location location) {
        return writeInto(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    default <W> boolean writeInto(W w, int x, int y, int z) {
        MatterWriter<W, T> injector = (MatterWriter<W, T>) writeInto(getClass(w));

        if (injector == null) {
            return false;
        }

        iterateSync((a,b,c,t) -> injector.writeMatter(w, t, a+x, b+y, c+z));

        return true;
    }

    default boolean readFrom(Location location) {
        return readFrom(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    default <W> boolean readFrom(W w, int x, int y, int z) {
        MatterReader<W, T> ejector = (MatterReader<W, T>) readFrom(getClass(w));

        if (ejector == null) {
            return false;
        }

        for (int i = x; i < x + getWidth(); i++) {
            for (int j = y; j < y + getHeight(); j++) {
                for (int k = z; k < z + getDepth(); k++) {
                    T v = ejector.readMatter(w, i, j, k);

                    if(v != null)
                    {
                        set(i - x, j - y, k - z, v);
                    }
                }
            }
        }

        return true;
    }

    // BlockMatter<T>
    //   RawMatter<T>      ex MappedHunk<T>
    //     IMatterSlice<T> ex Hunk<T>

    default int getCount() {
        return ((MappedHunk<?>) this).getEntryCount();
    }

    default boolean canWrite(Class<?> mediumType) {
        return writeInto(mediumType) != null;
    }

    default boolean canRead(Class<?> mediumType) {
        return readFrom(mediumType) != null;
    }

    default void write(DataOutputStream dos) throws IOException {
        int w = getWidth();
        int h = getHeight();
        dos.writeUTF(getType().getCanonicalName());
        MatterPalette<T> palette = new MatterPalette<T>(this);
        iterateSync((x, y, z, b) -> palette.assign(b));
        palette.writePalette(dos);
        Varint.writeUnsignedVarInt(getCount(), dos);
        iterateSyncIO((x, y, z, b) -> {
            Varint.writeUnsignedVarInt(Cache.to1D(x, y, z, w, h), dos);
            palette.writeNode(b, dos);
        });
    }

    default void read(DataInputStream din) throws IOException {
        int w = getWidth();
        int h = getHeight();

        MatterPalette<T> palette = new MatterPalette<T>(this, din);
        int nodes = Varint.readUnsignedVarInt(din);
        int[] pos;

        while (nodes-- > 0) {
            pos = Cache.to3D(Varint.readUnsignedVarInt(din), w, h);
            setRaw(pos[0], pos[1], pos[2], palette.readNode(din));
        }
    }

    default void rotateSliceInto(Matter n, double x, double y, double z) {
        rotate(x, y, z, (_x, _y, _z) -> n.slice(getType()));
    }
}
