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

import com.google.common.base.Predicates;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;

public class RegistryBlockID<T> implements Registry<T> {
    public static final int a = -1;
    private int b;
    private final HashMap<T, Integer> indexMap;
    private final List<T> indexes;

    public RegistryBlockID(IdentityHashMap<T, Integer> c, List<T> d, int b) {
        this.indexMap = new HashMap<>(c);
        this.indexes = d;
        this.b = b;
    }

    public RegistryBlockID() {
        this(512);
    }

    public RegistryBlockID(int var0) {
        this.indexes = Lists.newArrayListWithExpectedSize(var0);
        this.indexMap = new HashMap<>(var0);
    }

    public int getId(T var0) {
        Integer var1 = this.indexMap.get(var0);
        return var1 == null ? -1 : var1;
    }

    public final T fromId(int var0) {
        return var0 >= 0 && var0 < this.indexes.size() ? this.indexes.get(var0) : null;
    }

    public Iterator<T> iterator() {
        return Iterators.filter(this.indexes.iterator(), Predicates.notNull());
    }

    public boolean hasIndex(int var0) {
        return this.fromId(var0) != null;
    }

    public int size() {
        return this.indexMap.size();
    }
}
