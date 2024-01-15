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

public class ShortTag extends NumberTag<Short> implements Comparable<ShortTag> {

    public static final byte ID = 2;
    public static final short ZERO_VALUE = 0;

    public ShortTag() {
        super(ZERO_VALUE);
    }

    public ShortTag(short value) {
        super(value);
    }

    @Override
    public byte getID() {
        return ID;
    }

    public void setValue(short value) {
        super.setValue(value);
    }

    @Override
    public boolean equals(Object other) {
        return super.equals(other) && asShort() == ((ShortTag) other).asShort();
    }

    @Override
    public int compareTo(ShortTag other) {
        return getValue().compareTo(other.getValue());
    }

    @Override
    public ShortTag clone() {
        return new ShortTag(getValue());
    }
}
