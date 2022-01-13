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

package com.volmit.iris.util.scheduling;

import com.volmit.iris.util.collection.KList;

@SuppressWarnings("ALL")
public interface Queue<T> {
    static <T> Queue<T> create(KList<T> t) {
        return new ShurikenQueue<T>().queue(t);
    }

    @SuppressWarnings("unchecked")
    static <T> Queue<T> create(T... t) {
        return new ShurikenQueue<T>().queue(new KList<T>().add(t));
    }

    Queue<T> queue(T t);

    Queue<T> queue(KList<T> t);

    boolean hasNext(int amt);

    boolean hasNext();

    T next();

    KList<T> next(int amt);

    Queue<T> clear();

    int size();

    boolean contains(T p);
}
