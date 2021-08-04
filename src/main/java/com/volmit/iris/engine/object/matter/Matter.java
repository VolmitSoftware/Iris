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

package com.volmit.iris.engine.object.matter;

import com.volmit.iris.util.data.Varint;
import com.volmit.iris.util.math.BlockPosition;

import java.io.*;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * When Red Matter isn't enough
 *
 * UVI width
 * UVI height
 * UVI depth
 * UVI sliceCount
 * UTF author
 * UVL createdAt
 * UVI version
 *   UTF sliceType (canonical class name)
 *   UVI nodeCount (for each slice)
 *     UVI position [(z * w * h) + (y * w) + x]
 *     ??? nodeData
 *
 */
public interface Matter {
    int VERSION = 1;

    /**
     * Get the header information
     * @return the header info
     */
    MatterHeader getHeader();

    /**
     * Get the width of this matter
     * @return the width
     */
    int getWidth();

    /**
     * Get the height of this matter
     * @return the height
     */
    int getHeight();

    /**
     * Get the depth of this matter
     * @return the depth
     */
    int getDepth();

    /**
     * Get the center of this matter
     * @return the center
     */
    default BlockPosition getCenter()
    {
        return new BlockPosition(getCenterX(), getCenterY(), getCenterZ());
    }

    /**
     * Create a slice from the given type
     * @param type the type class
     * @param matter the matter this slice will go into (size provider)
     * @param <T> the type
     * @return the slice (or null if not supported)
     */
    <T> MatterSlice<T> createSlice(Class<T> type, Matter matter);

    /**
     * Get the size of this matter
     * @return the size
     */
    default BlockPosition getSize()
    {
        return new BlockPosition(getWidth(), getHeight(), getDepth());
    }

    /**
     * Get the center X of this matter
     * @return the center X
     */
    default int getCenterX()
    {
        return Math.round(getWidth() / 2);
    }

    /**
     * Get the center Y of this matter
     * @return the center Y
     */
    default int getCenterY()
    {
        return Math.round(getHeight() / 2);
    }

    /**
     * Get the center Z of this matter
     * @return the center Z
     */
    default int getCenterZ()
    {
        return Math.round(getDepth() / 2);
    }

    /**
     * Return the slice for the given type
     * @param t the type class
     * @param <T> the type
     * @return the slice or null
     */
    default <T> MatterSlice<T> getSlice(Class<T> t)
    {
        return (MatterSlice<T>) getSliceMap().get(t);
    }

    /**
     * Delete the slice for the given type
     * @param c the type class
     * @param <T> the type
     * @return the deleted slice, or null if it diddn't exist
     */
    default <T> MatterSlice<T> deleteSlice(Class<?> c)
    {
        return (MatterSlice<T>) getSliceMap().remove(c);
    }

    /**
     * Put a given slice type
     * @param c the slice type class
     * @param slice the slice to assign to the type
     * @param <T> the slice type
     * @return the overwritten slice if there was an existing slice of that type
     */
    default <T> MatterSlice<T> putSlice(Class<?> c, MatterSlice<T> slice)
    {
        return (MatterSlice<T>) getSliceMap().put(c, slice);
    }

    /**
     * Check if a slice exists for a given type
     * @param c the slice class type
     * @return true if it exists
     */
    default boolean hasSlice(Class<?> c)
    {
        return getSlice(c) != null;
    }

    /**
     * Remove all slices
     */
    default void clearSlices()
    {
        getSliceMap().clear();
    }

    /**
     * Get the set backing the slice map keys (slice types)
     * @return the slice types
     */
    default Set<Class<?>> getSliceTypes()
    {
        return getSliceMap().keySet();
    }

    /**
     * Get all slices
     * @return the real slice map
     */
    Map<Class<?>, MatterSlice<?>> getSliceMap();

    /**
     * Writes the data to the output stream. The data will be flushed to the provided output
     * stream however the provided stream will NOT BE CLOSED, so be sure to actually close it
     * @param out the output stream
     * @throws IOException shit happens yo
     */
    default void write(OutputStream out) throws IOException
    {
        DataOutputStream dos = new DataOutputStream(out);
        // Write size
        Varint.writeUnsignedVarInt(getWidth(), dos);
        Varint.writeUnsignedVarInt(getHeight(), dos);
        Varint.writeUnsignedVarInt(getDepth(), dos);
        dos.writeByte(getSliceTypes().size() + Byte.MIN_VALUE);
        getHeader().write(dos);

        for(Class<?> i : getSliceTypes())
        {
            getSlice(i).write(dos);
        }

        dos.flush();
    }

    /**
     * Reads the input stream into a matter object using a matter factory.
     * Does not close the input stream. Be a man, close it yourself.
     * @param in the input stream
     * @param matterFactory the matter factory (size) -> new MatterImpl(size);
     * @return the matter object
     * @throws IOException shit happens yo
     */
    static Matter read(InputStream in, Function<BlockPosition, Matter> matterFactory) throws IOException, ClassNotFoundException {
        DataInputStream din = new DataInputStream(in);
        // Read size into new matter object
        Matter matter = matterFactory.apply(new BlockPosition(
                Varint.readUnsignedVarInt(din),
                Varint.readUnsignedVarInt(din),
                Varint.readUnsignedVarInt(din)));
        int sliceCount = din.readByte() - Byte.MIN_VALUE;
        matter.getHeader().read(din);

        while(sliceCount-- > 0)
        {
            Class<?> type = Class.forName(din.readUTF());
            MatterSlice<?> slice = matter.createSlice(type, matter);
            slice.read(din);
            matter.putSlice(type, slice);
        }

        return matter;
    }
}
