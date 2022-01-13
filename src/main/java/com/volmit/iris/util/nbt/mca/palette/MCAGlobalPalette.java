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

import com.volmit.iris.util.nbt.tag.ListTag;

import java.util.function.Predicate;

public class MCAGlobalPalette<T> implements MCAPalette<T> {
    private final MCAIdMapper<T> registry;

    private final T defaultValue;

    public MCAGlobalPalette(MCAIdMapper<T> var0, T var1) {
        this.registry = var0;
        this.defaultValue = var1;
    }

    public int idFor(T var0) {
        int var1 = this.registry.getId(var0);
        return (var1 == -1) ? 0 : var1;
    }

    public boolean maybeHas(Predicate<T> var0) {
        return true;
    }

    public T valueFor(int var0) {
        T var1 = this.registry.byId(var0);
        return (var1 == null) ? this.defaultValue : var1;
    }

    public int getSize() {
        return this.registry.size();
    }

    public void read(ListTag var0) {
    }
}
