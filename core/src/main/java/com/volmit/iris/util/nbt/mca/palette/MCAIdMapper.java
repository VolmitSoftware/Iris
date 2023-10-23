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

import com.google.common.base.Predicates;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenCustomHashMap;

import java.util.Iterator;
import java.util.List;

public class MCAIdMapper<T> implements MCAIdMap<T> {
    public static final int DEFAULT = -1;
    private final Object2IntMap<T> tToId;
    private final List<T> idToT;
    private int nextId;

    public MCAIdMapper(Object2IntMap<T> tToId, List<T> idToT, int nextId) {
        this.tToId = tToId;
        this.idToT = idToT;
        this.nextId = nextId;
    }

    public MCAIdMapper() {
        this(512);
    }

    public MCAIdMapper(int var0) {
        this.idToT = Lists.newArrayListWithExpectedSize(var0);
        this.tToId = new Object2IntOpenCustomHashMap<>(var0, IdentityStrategy.INSTANCE);
    }

    public void addMapping(T var0, int var1) {
        this.tToId.put(var0, Integer.valueOf(var1));
        while (this.idToT.size() <= var1)
            this.idToT.add(null);
        this.idToT.set(var1, var0);
        if (this.nextId <= var1)
            this.nextId = var1 + 1;
    }

    public void add(T var0) {
        addMapping(var0, this.nextId);
    }

    public int getId(T var0) {
        Integer var1 = this.tToId.get(var0);
        return (var1 == null) ? -1 : var1.intValue();
    }

    public final T byId(int var0) {
        if (var0 >= 0 && var0 < this.idToT.size())
            return this.idToT.get(var0);
        return null;
    }

    public Iterator<T> iterator() {
        return Iterators.filter(this.idToT.iterator(), Predicates.notNull());
    }

    public boolean contains(int var0) {
        return (byId(var0) != null);
    }

    public int size() {
        return this.tToId.size();
    }

    enum IdentityStrategy implements Hash.Strategy<Object> {
        INSTANCE;

        IdentityStrategy() {
        }

        public int hashCode(Object var0) {
            return System.identityHashCode(var0);
        }

        public boolean equals(Object var0, Object var1) {
            return var0 == var1;
        }
    }
}