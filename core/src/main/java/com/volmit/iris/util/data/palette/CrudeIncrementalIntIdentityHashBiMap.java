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

import com.google.common.collect.Iterators;

import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class CrudeIncrementalIntIdentityHashBiMap<K> implements IdMap<K> {
    public static final int NOT_FOUND = -1;
    private static final Object EMPTY_SLOT = null;
    private static final float LOADFACTOR = 0.8F;
    private AtomicReferenceArray<K> keys;
    private AtomicIntegerArray values;
    private AtomicReferenceArray<K> byId;
    private int nextId;
    private int size;

    public CrudeIncrementalIntIdentityHashBiMap(int var0) {
        var0 = (int) (var0 / 0.8F);
        this.keys = new AtomicReferenceArray<>(var0);
        this.values = new AtomicIntegerArray(var0);
        this.byId = new AtomicReferenceArray<>(var0);
    }

    public int getId(K var0) {
        return getValue(indexOf(var0, hash(var0)));
    }


    public K byId(int var0) {
        if (var0 < 0 || var0 >= this.byId.length()) {
            return null;
        }
        return this.byId.get(var0);
    }

    private int getValue(int var0) {
        if (var0 == -1) {
            return -1;
        }
        return this.values.get(var0);
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
        while (nextId < byId.length() && byId.get(nextId) != null) {
            nextId++;
        }
        return nextId;
    }

    private void grow(int var0) {
        AtomicReferenceArray<K> var1 = this.keys;
        AtomicIntegerArray var2 = this.values;
        this.keys = new AtomicReferenceArray<>(var0);
        this.values = new AtomicIntegerArray(var0);
        this.byId = new AtomicReferenceArray<>(var0);
        this.nextId = 0;
        this.size = 0;
        for (int var3 = 0; var3 < var1.length(); var3++) {
            if (var1.get(var3) != null) {
                addMapping(var1.get(var3), var2.get(var3));
            }
        }
    }

    public void addMapping(K var0, int var1) {
        int var2 = Math.max(var1, this.size + 1);
        if (var2 >= this.keys.length() * 0.8F) {
            int i = this.keys.length() << 1;
            while (i < var1)
                i <<= 1;
            grow(i);
        }
        int var3 = findEmpty(hash(var0));
        this.keys.set(var3, var0);
        this.values.set(var3, var1);
        this.byId.set(var1, var0);
        this.size++;
        if (var1 == this.nextId)
            this.nextId++;
    }

    private int hash(K var0) {
        return (Mth.murmurHash3Mixer(System.identityHashCode(var0)) & Integer.MAX_VALUE) % this.keys.length();
    }

    private int indexOf(K var0, int var1) {
        int var2;
        for (var2 = var1; var2 < this.keys.length(); var2++) {
            if (this.keys.get(var2) == null) {
                return 0;
            }
            if (this.keys.get(var2).equals(var0))
                return var2;
            if (this.keys.get(var2) == EMPTY_SLOT)
                return -1;
        }
        for (var2 = 0; var2 < var1; var2++) {
            if (this.keys.get(var2).equals(var0))
                return var2;
            if (this.keys.get(var2) == EMPTY_SLOT)
                return -1;
        }
        return -1;
    }

    private int findEmpty(int var0) {
        int var1;
        for (var1 = var0; var1 < this.keys.length(); var1++) {
            if (this.keys.get(var1) == EMPTY_SLOT)
                return var1;
        }
        for (var1 = 0; var1 < var0; var1++) {
            if (this.keys.get(var1) == EMPTY_SLOT)
                return var1;
        }
        throw new RuntimeException("Overflowed :(");
    }

    public Iterator<K> iterator() {
        return Iterators.filter(new Iterator<K>() {
            int i = 0;

            @Override
            public boolean hasNext() {
                return i < byId.length() - 1;
            }

            @Override
            public K next() {
                return byId.get(i++);
            }
        }, Objects::nonNull);
    }

    public void clear() {

        for (int i = 0; i < Math.max(keys.length(), byId.length()); i++) {
            if (i < keys.length() - 1) {
                keys.set(i, null);
            }

            if (i < byId.length() - 1) {
                byId.set(i, null);
            }
        }

        this.nextId = 0;
        this.size = 0;
    }

    public int size() {
        return this.size;
    }
}
