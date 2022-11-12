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

import java.util.function.Function;

public class FunctionalHunkView<R, T> implements Hunk<T> {
    private final Hunk<R> src;
    private final Function<R, T> converter;
    private final Function<T, R> backConverter;

    public FunctionalHunkView(Hunk<R> src, Function<R, T> converter, Function<T, R> backConverter) {
        this.src = src;
        this.converter = converter;
        this.backConverter = backConverter;
    }

    @Override
    public void setRaw(int x, int y, int z, T t) {
        if (backConverter == null) {
            throw new UnsupportedOperationException("You cannot writeNodeData to this hunk (Read Only)");
        }

        src.setRaw(x, y, z, backConverter.apply(t));
    }

    @Override
    public T getRaw(int x, int y, int z) {
        if (converter == null) {
            throw new UnsupportedOperationException("You cannot read this hunk (Write Only)");
        }

        return converter.apply(src.getRaw(x, y, z));
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
        throw new UnsupportedOperationException("You cannot read this hunk's source because it's a different type.");
    }
}
