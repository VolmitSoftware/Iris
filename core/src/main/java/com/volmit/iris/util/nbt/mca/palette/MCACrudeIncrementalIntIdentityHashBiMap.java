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

import java.util.Arrays;
import java.util.Iterator;

public class MCACrudeIncrementalIntIdentityHashBiMap<K> implements MCAIdMap<K> {
    public static final int NOT_FOUND = -1;

    private static final Object EMPTY_SLOT = null;

    private static final float LOADFACTOR = 0.8F;

    private K[] keys;

    private int[] values;

    private K[] byId;

    private int nextId;

    private int size;

    public MCACrudeIncrementalIntIdentityHashBiMap(int var0) {
        var0 = (int) (var0 / 0.8F);
        this.keys = (K[]) new Object[var0];
        this.values = new int[var0];
        this.byId = (K[]) new Object[var0];
    }

    public int getId(K var0) {
        return getValue(indexOf(var0, hash(var0)));
    }


    public K byId(int var0) {
        if (var0 < 0 || var0 >= this.byId.length)
            return null;
        return this.byId[var0];
    }

    private int getValue(int var0) {
        if (var0 == -1)
            return -1;
        return this.values[var0];
    }

    public boolean contains(K var0) {
        return (getId(var0) != -1);
    }

    public boolean contains(int var0) {
        return (byId(var0) != null);
    }

    public int add(K var0) {
        int var1 = nextId();
        addMapping(var0, var1);
        return var1;
    }

    private int nextId() {
        while (this.nextId < this.byId.length && this.byId[this.nextId] != null)
            this.nextId++;
        return this.nextId;
    }

    private void grow(int var0) {
        K[] var1 = this.keys;
        int[] var2 = this.values;
        this.keys = (K[]) new Object[var0];
        this.values = new int[var0];
        this.byId = (K[]) new Object[var0];
        this.nextId = 0;
        this.size = 0;
        for (int var3 = 0; var3 < var1.length; var3++) {
            if (var1[var3] != null)
                addMapping(var1[var3], var2[var3]);
        }
    }

    public void addMapping(K var0, int var1) {
        int var2 = Math.max(var1, this.size + 1);
        if (var2 >= this.keys.length * 0.8F) {
            int i = this.keys.length << 1;
            while (i < var1)
                i <<= 1;
            grow(i);
        }
        int var3 = findEmpty(hash(var0));
        this.keys[var3] = var0;
        this.values[var3] = var1;
        this.byId[var1] = var0;
        this.size++;
        if (var1 == this.nextId)
            this.nextId++;
    }

    private int hash(K var0) {
        return (MCAMth.murmurHash3Mixer(System.identityHashCode(var0)) & Integer.MAX_VALUE) % this.keys.length;
    }

    private int indexOf(K var0, int var1) {
        int var2;
        for (var2 = var1; var2 < this.keys.length; var2++) {
            if (this.keys[var2] == var0)
                return var2;
            if (this.keys[var2] == EMPTY_SLOT)
                return -1;
        }
        for (var2 = 0; var2 < var1; var2++) {
            if (this.keys[var2] == var0)
                return var2;
            if (this.keys[var2] == EMPTY_SLOT)
                return -1;
        }
        return -1;
    }

    private int findEmpty(int var0) {
        int var1;
        for (var1 = var0; var1 < this.keys.length; var1++) {
            if (this.keys[var1] == EMPTY_SLOT)
                return var1;
        }
        for (var1 = 0; var1 < var0; var1++) {
            if (this.keys[var1] == EMPTY_SLOT)
                return var1;
        }
        throw new RuntimeException("Overflowed :(");
    }

    public Iterator<K> iterator() {
        return (Iterator<K>) Iterators.filter(Iterators.forArray((Object[]) this.byId), Predicates.notNull());
    }

    public void clear() {
        Arrays.fill(this.keys, null);
        Arrays.fill(this.byId, null);
        this.nextId = 0;
        this.size = 0;
    }

    public int size() {
        return this.size;
    }
}
