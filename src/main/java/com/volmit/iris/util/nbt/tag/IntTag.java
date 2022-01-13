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

public class IntTag extends NumberTag<Integer> implements Comparable<IntTag> {

    public static final byte ID = 3;
    public static final int ZERO_VALUE = 0;

    public IntTag() {
        super(ZERO_VALUE);
    }

    public IntTag(int value) {
        super(value);
    }

    @Override
    public byte getID() {
        return ID;
    }

    public void setValue(int value) {
        super.setValue(value);
    }

    @Override
    public boolean equals(Object other) {
        return super.equals(other) && asInt() == ((IntTag) other).asInt();
    }

    @Override
    public int compareTo(IntTag other) {
        return getValue().compareTo(other.getValue());
    }

    @Override
    public IntTag clone() {
        return new IntTag(getValue());
    }
}
