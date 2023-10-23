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

public class O<T> implements Observable<T> {
    private T t = null;
    private KList<Observer<T>> observers;

    @Override
    public T get() {
        return t;
    }

    @Override
    public O<T> set(T t) {
        this.t = t;

        if (observers != null && observers.hasElements()) {
            observers.forEach((o) -> o.onChanged(t, t));
        }

        return this;
    }

    @Override
    public boolean has() {
        return t != null;
    }

    @Override
    public O<T> clearObservers() {
        observers.clear();
        return this;
    }

    @Override
    public O<T> observe(Observer<T> t) {
        if (observers == null) {
            observers = new KList<>();
        }

        observers.add(t);

        return this;
    }
}
