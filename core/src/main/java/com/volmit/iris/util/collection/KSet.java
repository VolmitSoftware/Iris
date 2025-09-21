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

package com.volmit.iris.util.collection;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

public class KSet<T> extends AbstractSet<T> implements Serializable {
    private static final long serialVersionUID = 1L;
    private final ConcurrentHashMap<T, Boolean> map;

    public KSet(Collection<? extends T> c) {
        this(c.size());
        addAll(c);
    }

    @SafeVarargs
    public KSet(T... values) {
        this(values.length);
        addAll(Arrays.asList(values));
    }

    public KSet(int initialCapacity, float loadFactor) {
        map = new ConcurrentHashMap<>(initialCapacity, loadFactor);
    }

    public KSet(int initialCapacity) {
        map = new ConcurrentHashMap<>(initialCapacity);
    }

    public static <T> KSet<T> merge(Collection<? extends T> first, Collection<? extends T> second) {
        var set = new KSet<T>();
        set.addAll(first);
        set.addAll(second);
        return set;
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean contains(Object o) {
        return map.containsKey(o);
    }

    @Override
    public boolean add(T t) {
        return map.putIfAbsent(t, Boolean.TRUE) == null;
    }

    @Override
    public boolean remove(Object o) {
        return map.remove(o) != null;
    }

    @Override
    public void clear() {
        map.clear();
    }

    @NotNull
    @Override
    public Iterator<T> iterator() {
        return map.keySet().iterator();
    }

    public KSet<T> copy() {
        return new KSet<>(this);
    }
}
