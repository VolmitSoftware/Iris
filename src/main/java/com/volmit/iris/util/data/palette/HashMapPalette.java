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

package com.volmit.iris.util.data.palette;

import com.volmit.iris.Iris;
import com.volmit.iris.util.collection.KMap;

import java.util.List;

public class HashMapPalette<T> implements Palette<T> {
    private final KMap<T, Integer> values;
    private final PaletteResize<T> resizeHandler;
    private final int bits;
    private int id;

    public HashMapPalette(int var1, PaletteResize<T> var2) {
        this.bits = var1;
        this.resizeHandler = var2;
        this.values = new KMap<>();
        id = 1;
    }

    public int idFor(T var0) {
        if (var0 == null) {
            return 0;
        }

        return this.values.computeIfAbsent(var0, (k) -> {
            int newId = id++;

            if (newId >= 1 << this.bits) {
                Iris.info(newId + " to...");
                newId = this.resizeHandler.onResize(this.bits + 1, var0);
                Iris.info(newId + "..");
            }

            return newId;
        });
    }

    public T valueFor(int var0) {
        return this.values.getKey(var0);
    }

    public int getSize() {
        return this.values.size();
    }

    @Override
    public void read(List<T> data) {
        data.forEach(this::idFor);
    }

    @Override
    public void write(List<T> toList) {
        toList.addAll(values.keySet());
    }
}