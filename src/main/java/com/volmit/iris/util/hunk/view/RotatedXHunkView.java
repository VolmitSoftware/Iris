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

public class RotatedXHunkView<T> implements Hunk<T> {
    private final Hunk<T> src;
    private final double sin;
    private final double cos;

    public RotatedXHunkView(Hunk<T> src, double deg) {
        this.src = src;
        this.sin = Math.sin(Math.toRadians(deg));
        this.cos = Math.cos(Math.toRadians(deg));
    }

    @Override
    public void setRaw(int x, int y, int z, T t) {
        int yc = (int) Math.round(cos * (getHeight() / 2f) - sin * (getDepth() / 2f));
        int zc = (int) Math.round(sin * (getHeight() / 2f) + cos * (getDepth() / 2f));
        src.setIfExists(x,
                (int) Math.round(cos * (y - yc) - sin * (z - zc)) - yc,
                (int) Math.round(sin * y - yc + cos * (z - zc)) - zc,
                t);
    }

    @Override
    public T getRaw(int x, int y, int z) {
        int yc = (int) Math.round(cos * (getHeight() / 2f) - sin * (getDepth() / 2f));
        int zc = (int) Math.round(sin * (getHeight() / 2f) + cos * (getDepth() / 2f));
        return src.getIfExists(x,
                (int) Math.round(cos * (y - yc) - sin * (z - zc)) - yc,
                (int) Math.round(sin * y - yc + cos * (z - zc)) - zc
        );
    }

    @Override
    public int getWidth() {
        return src.getWidth();
    }

    @Override
    public int getDepth() {
        return src.getDepth();
    }

    @Override
    public int getHeight() {
        return src.getHeight();
    }

    @Override
    public Hunk<T> getSource() {
        return src;
    }
}
