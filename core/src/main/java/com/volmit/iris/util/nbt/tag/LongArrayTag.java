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

import java.util.Arrays;

public class LongArrayTag extends ArrayTag<long[]> implements Comparable<LongArrayTag> {

    public static final byte ID = 12;
    public static final long[] ZERO_VALUE = new long[0];

    public LongArrayTag() {
        super(ZERO_VALUE);
    }

    public LongArrayTag(long[] value) {
        super(value);
    }

    @Override
    public byte getID() {
        return ID;
    }

    @Override
    public boolean equals(Object other) {
        return super.equals(other) && Arrays.equals(getValue(), ((LongArrayTag) other).getValue());
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(getValue());
    }

    @Override
    public int compareTo(LongArrayTag other) {
        return Integer.compare(length(), other.length());
    }

    @Override
    public LongArrayTag clone() {
        return new LongArrayTag(Arrays.copyOf(getValue(), length()));
    }
}
