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

package com.volmit.iris.util.data.palette;

import com.volmit.iris.util.nbt.tag.CompoundTag;
import com.volmit.iris.util.nbt.tag.ListTag;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public class HashMapPalette<T> implements Palette<T> {
    private final CrudeIncrementalIntIdentityHashBiMap<T> values;
    private final PaletteResize<T> resizeHandler;
    private final int bits;

    public HashMapPalette(int var1, PaletteResize<T> var2) {
        this.bits = var1;
        this.resizeHandler = var2;
        this.values = new CrudeIncrementalIntIdentityHashBiMap<>(1 << var1);
    }

    public int idFor(T var0) {
        int var1 = this.values.getId(var0);
        if (var1 == -1) {
            var1 = this.values.add(var0);
            if (var1 >= 1 << this.bits)
            {
                var1 = this.resizeHandler.onResize(this.bits + 1, var0);
            }
        }
        return var1;
    }

    public boolean maybeHas(Predicate<T> var0) {
        for (int var1 = 0; var1 < getSize(); var1++) {
            if (var0.test(this.values.byId(var1)))
                return true;
        }
        return false;
    }

    public T valueFor(int var0) {
        return this.values.byId(var0);
    }

    public int getSize() {
        return this.values.size();
    }

    @Override
    public void read(List<T> data) {
        data.forEach(values::add);
    }

    @Override
    public void write(List<T> toList) {
        this.values.iterator().forEachRemaining(i -> {
            if(i != null)
            {
                toList.add(i);
            }
        });
    }
}