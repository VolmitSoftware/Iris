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

package com.volmit.iris.util.hunk.storage;

import com.volmit.iris.util.data.palette.PalettedContainer;
import com.volmit.iris.util.function.Consumer4;
import com.volmit.iris.util.function.Consumer4IO;
import com.volmit.iris.util.hunk.Hunk;

import java.io.IOException;
import java.util.Map;
import java.util.function.Supplier;

public class PaletteOrHunk<T> extends StorageHunk<T> implements Hunk<T> {
    private final Hunk<T> hunk;
    public PaletteOrHunk(int width, int height, int depth, Supplier<Hunk<T>> factory) {
        super(width, height, depth);
        hunk = width == 16 && height == 16 && depth == 16 ? new PaletteHunk<>() : factory.get();
    }

    public PalettedContainer<T> palette()
    {
        return isPalette() ? ((PaletteHunk<T>)hunk).getData() : null;
    }

    public void palette(PalettedContainer<T> t)
    {
        if(isPalette()){
            ((PaletteHunk<T>)hunk).setData(t);
        }
    }

    public boolean isPalette()
    {
        return getWidth() == 16 && getHeight() == 16 && getDepth() == 16;
    }

    @Override
    public void setRaw(int x, int y, int z, T t) {
        hunk.setRaw(x,y,z,t);
    }

    @Override
    public T getRaw(int x, int y, int z) {
        return hunk.getRaw(x,y,z);
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
