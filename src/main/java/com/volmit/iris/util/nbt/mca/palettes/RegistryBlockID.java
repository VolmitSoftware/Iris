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

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;

public class RegistryBlockID<T> implements Registry<T> {
    public static final int a = -1;
    private int b;
    private final IdentityHashMap<T, Integer> c;
    private final List<T> d;

    public RegistryBlockID() {
        this(512);
    }

    public RegistryBlockID(int var0) {
        this.d = Lists.newArrayListWithExpectedSize(var0);
        this.c = new IdentityHashMap(var0);
    }

    public void a(T var0, int var1) {
        this.c.put(var0, var1);

        while (this.d.size() <= var1) {
            this.d.add(null);
        }

        this.d.set(var1, var0);
        if (this.b <= var1) {
            this.b = var1 + 1;
        }

    }

    public void b(T var0) {
        this.a(var0, this.b);
    }

    public int getId(T var0) {
        Integer var1 = (Integer) this.c.get(var0);
        return var1 == null ? -1 : var1;
    }

    public final T fromId(int var0) {
        return var0 >= 0 && var0 < this.d.size() ? this.d.get(var0) : null;
    }

    public Iterator<T> iterator() {
        return Iterators.filter(this.d.iterator(), Predicates.notNull());
    }

    public boolean b(int var0) {
        return this.fromId(var0) != null;
    }

    public int a() {
        return this.c.size();
    }
}
