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

package com.volmit.iris.util.nbt.mca.palette;

import com.volmit.iris.util.nbt.tag.CompoundTag;
import com.volmit.iris.util.nbt.tag.ListTag;

import java.util.function.Function;
import java.util.function.Predicate;

public class MCALinearPalette<T> implements MCAPalette<T> {
    private final MCAIdMapper<T> registry;

    private final T[] values;

    private final MCAPaletteResize<T> resizeHandler;

    private final Function<CompoundTag, T> reader;

    private final int bits;

    private int size;

    public MCALinearPalette(MCAIdMapper<T> var0, int var1, MCAPaletteResize<T> var2, Function<CompoundTag, T> var3) {
        this.registry = var0;
        this.values = (T[]) new Object[1 << var1];
        this.bits = var1;
        this.resizeHandler = var2;
        this.reader = var3;
    }

    public int idFor(T var0) {
        int var1;
        for (var1 = 0; var1 < this.size; var1++) {
            if (this.values[var1] == var0)
                return var1;
        }
        var1 = this.size;
        if (var1 < this.values.length) {
            this.values[var1] = var0;
            this.size++;
            return var1;
        }
        return this.resizeHandler.onResize(this.bits + 1, var0);
    }

    public boolean maybeHas(Predicate<T> var0) {
        for (int var1 = 0; var1 < this.size; var1++) {
            if (var0.test(this.values[var1]))
                return true;
        }
        return false;
    }

    public T valueFor(int var0) {
        if (var0 >= 0 && var0 < this.size)
            return this.values[var0];
        return null;
    }

    public int getSize() {
        return this.size;
    }

    public void read(ListTag var0) {
        for (int var1 = 0; var1 < var0.size(); var1++) {
            this.values[var1] = this.reader.apply((CompoundTag) var0.get(var1));
        }
        this.size = var0.size();
    }
}
