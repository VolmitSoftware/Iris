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

package com.volmit.iris.util.matter;

import com.volmit.iris.Iris;
import com.volmit.iris.engine.object.IrisObject;
import com.volmit.iris.engine.object.IrisPosition;
import com.volmit.iris.util.collection.KSet;
import com.volmit.iris.util.hunk.Hunk;
import com.volmit.iris.util.math.BlockPosition;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.util.BlockVector;

import java.io.*;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * When Red Matter isn't enough
 * <p>
 * UVI width
 * UVI height
 * UVI depth
 * UVI sliceCount
 * UTF author
 * UVL createdAt
 * UVI version
 * UTF sliceType (canonical class name)
 * UVI nodeCount (for each slice)
 * UVI position [(z * w * h) + (y * w) + x]
 * ??? nodeData
 */
public interface Matter {
    int VERSION = 1;

    static long convert(File folder) {
        if (folder.isDirectory()) {
            long v = 0;

            for (File i : folder.listFiles()) {
                v += convert(i);
            }

            return v;
        } else {
            IrisObject object = new IrisObject(1, 1, 1);
            try {
                long fs = folder.length();
                object.read(folder);
                Matter.from(object).write(folder);
                Iris.info("Converted " + folder.getPath() + " Saved " + (fs - folder.length()));
            } catch (Throwable e) {
                Iris.error("Failed to convert " + folder.getPath());
                e.printStackTrace();
            }
        }

        return 0;
    }

    static Matter from(IrisObject object) {
        object.clean();
        object.shrinkwrap();
        BlockVector min = new BlockVector();
        Matter m = new IrisMatter(Math.max(object.getW(), 1) + 1, Math.max(object.getH(), 1) + 1, Math.max(object.getD(), 1) + 1);

        for (BlockVector i : object.getBlocks().keySet()) {
            min.setX(Math.min(min.getX(), i.getX()));
            min.setY(Math.min(min.getY(), i.getY()));
            min.setZ(Math.min(min.getZ(), i.getZ()));
        }

        for (BlockVector i : object.getBlocks().keySet()) {
            m.slice(BlockData.class).set(i.getBlockX() - min.getBlockX(), i.getBlockY() - min.getBlockY(), i.getBlockZ() - min.getBlockZ(), object.getBlocks().get(i));
        }

        return m;
    }

    static Matter read(File f) throws IOException, ClassNotFoundException {
        FileInputStream in = new FileInputStream(f);
        Matter m = read(in);
        in.close();
        return m;
    }

    static Matter read(InputStream in) throws IOException, ClassNotFoundException {
        return read(in, (b) -> new IrisMatter(b.getX(), b.getY(), b.getZ()));
    }

    static Matter readDin(DataInputStream in) throws IOException, ClassNotFoundException {
        return readDin(in, (b) -> new IrisMatter(b.getX(), b.getY(), b.getZ()));
    }

    /**
     * Reads the input stream into a matter object using a matter factory.
     * Does not close the input stream. Be a man, close it yourself.
     *
     * @param in            the input stream
     * @param matterFactory the matter factory (size) -> new MatterImpl(size);
     * @return the matter object
     * @throws IOException shit happens yo
     */
    static Matter read(InputStream in, Function<BlockPosition, Matter> matterFactory) throws IOException, ClassNotFoundException {
        return readDin(new DataInputStream(in), matterFactory);
    }

    static Matter readDin(DataInputStream din, Function<BlockPosition, Matter> matterFactory) throws IOException, ClassNotFoundException {
        Matter matter = matterFactory.apply(new BlockPosition(
                din.readInt(),
                din.readInt(),
                din.readInt()));
        Iris.addPanic("read.matter.size", matter.getWidth() + "x" + matter.getHeight() + "x" + matter.getDepth());
        int sliceCount = din.readByte();
        Iris.addPanic("read.matter.slicecount", sliceCount + "");

        matter.getHeader().read(din);
        Iris.addPanic("read.matter.header", matter.getHeader().toString());

        for (int i = 0; i < sliceCount; i++) {
            Iris.addPanic("read.matter.slice", i + "");
            String cn = din.readUTF();
            Iris.addPanic("read.matter.slice.class", cn);
            try {
                Class<?> type = Class.forName(cn);
                MatterSlice<?> slice = matter.createSlice(type, matter);
                slice.read(din);
                matter.putSlice(type, slice);
            } catch (Throwable e) {
                e.printStackTrace();
                throw new IOException("Can't read class '" + cn + "' (slice count reverse at " + sliceCount + ")");
            }
        }

        return matter;
    }

    default Matter copy() {
        Matter m = new IrisMatter(getWidth(), getHeight(), getDepth());
        getSliceMap().forEach((k, v) -> m.slice(k).forceInject(v));
        return m;
    }

    /**
     * Get the header information
     *
     * @return the header info
     */
    MatterHeader getHeader();

    /**
     * Get the width of this matter
     *
     * @return the width
     */
    int getWidth();

    /**
     * Get the height of this matter
     *
     * @return the height
     */
    int getHeight();

    /**
     * Get the depth of this matter
     *
     * @return the depth
     */
    int getDepth();

    /**
     * Get the center of this matter
     *
     * @return the center
     */
    default BlockPosition getCenter() {
        return new BlockPosition(getCenterX(), getCenterY(), getCenterZ());
    }

    /**
     * Create a slice from the given type (full is false)
     *
     * @param type   the type class
     * @param matter the matter this slice will go into (size provider)
     * @param <T>    the type
     * @return the slice (or null if not supported)
     */
    <T> MatterSlice<T> createSlice(Class<T> type, Matter matter);

    /**
     * Get the size of this matter
     *
     * @return the size
     */
    default BlockPosition getSize() {
        return new BlockPosition(getWidth(), getHeight(), getDepth());
    }

    /**
     * Get the center X of this matter
     *
     * @return the center X
     */
    default int getCenterX() {
        return (int) Math.round(getWidth() / 2D);
    }

    /**
     * Get the center Y of this matter
     *
     * @return the center Y
     */
    default int getCenterY() {
        return (int) Math.round(getHeight() / 2D);
    }

    /**
     * Get the center Z of this matter
     *
     * @return the center Z
     */
    default int getCenterZ() {
        return (int) Math.round(getDepth() / 2D);
    }

    /**
     * Return the slice for the given type
     *
     * @param t   the type class
     * @param <T> the type
     * @return the slice or null
     */
    default <T> MatterSlice<T> getSlice(Class<T> t) {
        return (MatterSlice<T>) getSliceMap().get(t);
    }

    /**
     * Delete the slice for the given type
     *
     * @param c   the type class
     * @param <T> the type
     * @return the deleted slice, or null if it diddn't exist
     */
    default <T> MatterSlice<T> deleteSlice(Class<?> c) {
        return (MatterSlice<T>) getSliceMap().remove(c);
    }

    /**
     * Put a given slice type
     *
     * @param c     the slice type class
     * @param slice the slice to assign to the type
     * @param <T>   the slice type
     * @return the overwritten slice if there was an existing slice of that type
     */
    default <T> MatterSlice<T> putSlice(Class<?> c, MatterSlice<T> slice) {
        return (MatterSlice<T>) getSliceMap().put(c, slice);
    }

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

    default <T> MatterSlice<T> slice(Class<?> c) {
        MatterSlice<T> slice = (MatterSlice<T>) getSlice(c);
        if (slice == null) {
            slice = (MatterSlice<T>) createSlice(c, this);

            if (slice == null) {
                try {
                    throw new RuntimeException("Bad slice " + c.getCanonicalName());
                } catch (Throwable e) {
                    e.printStackTrace();
                }

                return null;
            }

            putSlice(c, slice);
        }

        return slice;
    }

    /**
     * Rotate a matter object into a new object
     *
     * @param x the x rotation (degrees)
     * @param y the y rotation (degrees)
     * @param z the z rotation (degrees)
     * @return the new rotated matter object
     */
    default Matter rotate(double x, double y, double z) {
        IrisPosition rs = Hunk.rotatedBounding(getWidth(), getHeight(), getDepth(), x, y, z);
        Matter n = new IrisMatter(rs.getX(), rs.getY(), rs.getZ());
        n.getHeader().setAuthor(getHeader().getAuthor());
        n.getHeader().setCreatedAt(getHeader().getCreatedAt());

        for (Class<?> i : getSliceTypes()) {
            getSlice(i).rotateSliceInto(n, x, y, z);
        }

        return n;
    }

    /**
     * Check if a slice exists for a given type
     *
     * @param c the slice class type
     * @return true if it exists
     */
    default boolean hasSlice(Class<?> c) {
        return getSlice(c) != null;
    }

    /**
     * Remove all slices
     */
    default void clearSlices() {
        getSliceMap().clear();
    }

    /**
     * Get the set backing the slice map keys (slice types)
     *
     * @return the slice types
     */
    default Set<Class<?>> getSliceTypes() {
        return getSliceMap().keySet();
    }

    /**
     * Get all slices
     *
     * @return the real slice map
     */
    Map<Class<?>, MatterSlice<?>> getSliceMap();

    default void write(File f) throws IOException {
        OutputStream out = new FileOutputStream(f);
        write(out);
        out.close();
    }

    /**
     * Remove any slices that are empty
     */
    default void trimSlices() {
        Set<Class<?>> drop = null;

        for (Class<?> i : getSliceTypes()) {
            if (getSlice(i).getEntryCount() == 0) {
                if (drop == null) {
                    drop = new KSet<>();
                }

                drop.add(i);
            }
        }

        if (drop != null) {
            for (Class<?> i : drop) {
                deleteSlice(i);
            }
        }
    }

    /**
     * Writes the data to the output stream. The data will be flushed to the provided output
     * stream however the provided stream will NOT BE CLOSED, so be sure to actually close it
     *
     * @param out the output stream
     * @throws IOException shit happens yo
     */
    default void write(OutputStream out) throws IOException {
        writeDos(new DataOutputStream(out));
    }

    default void writeDos(DataOutputStream dos) throws IOException {
        trimSlices();
        dos.writeInt(getWidth());
        dos.writeInt(getHeight());
        dos.writeInt(getDepth());
        dos.writeByte(getSliceTypes().size());
        getHeader().write(dos);

        for (Class<?> i : getSliceTypes()) {
            getSlice(i).write(dos);
        }
    }

    default int getTotalCount() {
        int m = 0;

        for (MatterSlice<?> i : getSliceMap().values()) {
            m += i.getEntryCount();
        }

        return m;
    }
}
