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

package com.volmit.iris.util.hunk.view;

import com.volmit.iris.util.function.Consumer4;
import com.volmit.iris.util.hunk.Hunk;

@SuppressWarnings("ClassCanBeRecord")
public class ListeningHunk<T> implements Hunk<T> {
    private final Hunk<T> src;
    private final Consumer4<Integer, Integer, Integer, T> listener;

    public ListeningHunk(Hunk<T> src, Consumer4<Integer, Integer, Integer, T> listener) {
        this.src = src;
        this.listener = listener;
    }

    @Override
    public void setRaw(int x, int y, int z, T t) {
        listener.accept(x, y, z, t);
        src.setRaw(x, y, z, t);
    }

    @Override
    public T getRaw(int x, int y, int z) {
        return src.getRaw(x, y, z);
    }

    @Override
    public int getWidth() {
        return src.getWidth();
    }

    @Override
    public int getHeight() {
        return src.getHeight();
    }

    @Override
    public int getDepth() {
        return src.getDepth();
    }

    @Override
    public Hunk<T> getSource() {
        return src;
    }
}
