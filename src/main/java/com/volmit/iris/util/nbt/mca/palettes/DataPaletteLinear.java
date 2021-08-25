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

package com.volmit.iris.util.nbt.mca.palettes;

import com.volmit.iris.util.nbt.tag.CompoundTag;
import com.volmit.iris.util.nbt.tag.ListTag;

import java.util.function.Predicate;

public class DataPaletteLinear implements DataPalette {
    private final CompoundTag[] palette;
    private final DataPaletteExpandable expander;
    private final int e;
    private int size;

    public DataPaletteLinear(int bits, DataPaletteExpandable expander) {
        this.palette = new CompoundTag[1 << bits];
        this.e = bits;
        this.expander = expander;
        this.size = 0;
    }

    public int getIndex(CompoundTag block) {
        int i;
        for (i = 0; i < size; ++i) {
            if (palette[i].equals(block)) {
                return i;
            }
        }

        i = size;
        if (i < palette.length) {
            palette[i] = block;
            ++size;
            return i;
        } else {
            return expander.onResize(e + 1, block);
        }
    }

    public boolean contains(Predicate<CompoundTag> predicate) {
        for (int i = 0; i < this.size; ++i) {
            if (predicate.test(palette[i])) {
                return true;
            }
        }

        return false;
    }

    public CompoundTag getByIndex(int index) {
        return index >= 0 && index < size ? palette[index] : null;
    }

    public int size() {
        return size;
    }

    public void replace(ListTag<CompoundTag> palette) {
        for (int i = 0; i < palette.size(); ++i) {
            this.palette[i] = palette.get(i);
        }

        this.size = palette.size();
    }
}
