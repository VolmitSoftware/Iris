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

package com.volmit.iris.util.nbt.tag;

import java.lang.reflect.Array;

/**
 * ArrayTag is an abstract representation of any NBT array tag.
 * For implementations see {@link ByteArrayTag}, {@link IntArrayTag}, {@link LongArrayTag}.
 *
 * @param <T> The array type.
 */
public abstract class ArrayTag<T> extends Tag<T> {

    public ArrayTag(T value) {
        super(value);
        if (!value.getClass().isArray()) {
            throw new UnsupportedOperationException("type of array tag must be an array");
        }
    }

    public int length() {
        return Array.getLength(getValue());
    }

    @Override
    public T getValue() {
        return super.getValue();
    }

    @Override
    public void setValue(T value) {
        super.setValue(value);
    }

    @Override
    public String valueToString(int maxDepth) {
        return arrayToString("", "");
    }

    protected String arrayToString(@SuppressWarnings("SameParameterValue") String prefix, @SuppressWarnings("SameParameterValue") String suffix) {
        StringBuilder sb = new StringBuilder("[").append(prefix).append("".equals(prefix) ? "" : ";");
        for (int i = 0; i < length(); i++) {
            sb.append(i == 0 ? "" : ",").append(Array.get(getValue(), i)).append(suffix);
        }
        sb.append("]");
        return sb.toString();
    }
}
