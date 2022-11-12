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

import com.volmit.iris.util.hunk.Hunk;

import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings("ClassCanBeRecord")
public class WriteTrackHunk<T> implements Hunk<T> {
    private final Hunk<T> src;
    private final AtomicBoolean b;

    public WriteTrackHunk(Hunk<T> src, AtomicBoolean b) {
        this.src = src;
        this.b = b;
    }

    @Override
    public void setRaw(int x, int y, int z, T t) {
        if (!b.get()) {
            b.set(true);
        }

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
