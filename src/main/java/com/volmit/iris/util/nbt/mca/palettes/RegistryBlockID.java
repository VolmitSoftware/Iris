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
import com.volmit.iris.util.nbt.tag.CompoundTag;

import java.util.*;

public class RegistryBlockID implements Registry {
    public static final int a = -1;
    private int b;
    private final Map<CompoundTag, Integer> indexMap;
    private final List<CompoundTag> indexes;

    public RegistryBlockID(Map<CompoundTag, Integer> c, List<CompoundTag> d, int b) {
        this.indexMap = c;
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

    public int getId(CompoundTag var0) {
        Integer var1 = this.indexMap.get(var0);
        return var1 == null ? -1 : var1;
    }

    public final CompoundTag fromId(int var0) {
        return var0 >= 0 && var0 < this.indexes.size() ? this.indexes.get(var0) : null;
    }

    public Iterator<CompoundTag> iterator() {
        return Iterators.filter(this.indexes.iterator(), Predicates.notNull());
    }

    public boolean hasIndex(int var0) {
        return this.fromId(var0) != null;
    }

    public int size() {
        return this.indexMap.size();
    }
}
