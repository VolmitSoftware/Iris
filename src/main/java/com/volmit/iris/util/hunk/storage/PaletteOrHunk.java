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

package com.volmit.iris.util.hunk.storage;

import com.volmit.iris.util.function.Consumer4;
import com.volmit.iris.util.function.Consumer4IO;
import com.volmit.iris.util.hunk.Hunk;
import com.volmit.iris.util.hunk.bits.DataContainer;
import com.volmit.iris.util.hunk.bits.Writable;

import java.io.IOException;
import java.util.function.Supplier;

public abstract class PaletteOrHunk<T> extends StorageHunk<T> implements Hunk<T>, Writable<T> {
    private final Hunk<T> hunk;

    public PaletteOrHunk(int width, int height, int depth, boolean allow, Supplier<Hunk<T>> factory) {
        super(width, height, depth);
        hunk = (allow && (width * height * depth <= 4096)) ? new PaletteHunk<>(width, height, depth, this) : factory.get();
    }

    public DataContainer<T> palette() {
        return isPalette() ? ((PaletteHunk<T>) hunk).getData() : null;
    }

    public boolean isPalette() {
        return hunk instanceof PaletteHunk;
    }

    public void setPalette(DataContainer<T> c) {
        if (isPalette()) {
            ((PaletteHunk<T>) hunk).setPalette(c);
        }
    }

    @Override
    public void setRaw(int x, int y, int z, T t) {
        hunk.setRaw(x, y, z, t);
    }

    @Override
    public T getRaw(int x, int y, int z) {
        return hunk.getRaw(x, y, z);
    }

    public int getEntryCount() {
        return hunk.getEntryCount();
    }

    public boolean isMapped() {
        return hunk.isMapped();
    }

    public boolean isEmpty() {
        return hunk.isMapped();
    }

    @Override
    public synchronized Hunk<T> iterateSync(Consumer4<Integer, Integer, Integer, T> c) {
        hunk.iterateSync(c);
        return this;
    }

    @Override
    public synchronized Hunk<T> iterateSyncIO(Consumer4IO<Integer, Integer, Integer, T> c) throws IOException {
        hunk.iterateSyncIO(c);
        return this;
    }

    @Override
    public void empty(T b) {
        hunk.empty(b);
    }
}
