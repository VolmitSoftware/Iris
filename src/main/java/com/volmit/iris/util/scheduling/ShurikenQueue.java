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

public class ShurikenQueue<T> implements Queue<T> {
    private KList<T> queue;
    private boolean randomPop;
    private boolean reversePop;

    public ShurikenQueue() {
        clear();
    }

    public ShurikenQueue<T> responsiveMode() {
        reversePop = true;
        return this;
    }

    public ShurikenQueue<T> randomMode() {
        randomPop = true;
        return this;
    }

    @Override
    public ShurikenQueue<T> queue(T t) {
        queue.add(t);
        return this;
    }

    @Override
    public ShurikenQueue<T> queue(KList<T> t) {
        queue.add(t);
        return this;
    }

    @Override
    public boolean hasNext(int amt) {
        return queue.size() >= amt;
    }

    @Override
    public boolean hasNext() {
        return !queue.isEmpty();
    }

    @Override
    public T next() {
        return reversePop ? queue.popLast() : randomPop ? queue.popRandom() : queue.pop();
    }

    @Override
    public KList<T> next(int amt) {
        KList<T> t = new KList<>();

        for (int i = 0; i < amt; i++) {
            if (!hasNext()) {
                break;
            }

            t.add(next());
        }

        return t;
    }

    @Override
    public ShurikenQueue<T> clear() {
        queue = new KList<>();
        return this;
    }

    @Override
    public int size() {
        return queue.size();
    }

    @Override
    public boolean contains(T p) {
        return queue.contains(p);
    }
}
