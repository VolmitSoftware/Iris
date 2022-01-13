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

package com.volmit.iris.util.matter.slices;

import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.hunk.storage.MappedHunk;
import com.volmit.iris.util.hunk.storage.PaletteOrHunk;
import com.volmit.iris.util.matter.MatterReader;
import com.volmit.iris.util.matter.MatterSlice;
import com.volmit.iris.util.matter.MatterWriter;
import lombok.Getter;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public abstract class RawMatter<T> extends PaletteOrHunk<T> implements MatterSlice<T> {
    protected final KMap<Class<?>, MatterWriter<?, T>> writers;
    protected final KMap<Class<?>, MatterReader<?, T>> readers;
    @Getter
    private final Class<T> type;

    public RawMatter(int width, int height, int depth, Class<T> type) {
        super(width, height, depth, true, () -> new MappedHunk<>(width, height, depth));
        writers = new KMap<>();
        readers = new KMap<>();
        this.type = type;
    }

    protected <W> void registerWriter(Class<W> mediumType, MatterWriter<W, T> injector) {
        writers.put(mediumType, injector);
    }

    protected <W> void registerReader(Class<W> mediumType, MatterReader<W, T> injector) {
        readers.put(mediumType, injector);
    }

    @Override
    public <W> MatterWriter<W, T> writeInto(Class<W> mediumType) {
        return (MatterWriter<W, T>) writers.get(mediumType);
    }

    @Override
    public <W> MatterReader<W, T> readFrom(Class<W> mediumType) {
        return (MatterReader<W, T>) readers.get(mediumType);
    }

    @Override
    public abstract void writeNode(T b, DataOutputStream dos) throws IOException;

    @Override
    public abstract T readNode(DataInputStream din) throws IOException;
}
