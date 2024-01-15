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

public class FloatTag extends NumberTag<Float> implements Comparable<FloatTag> {

    public static final byte ID = 5;
    public static final float ZERO_VALUE = 0.0F;

    public FloatTag() {
        super(ZERO_VALUE);
    }

    public FloatTag(float value) {
        super(value);
    }

    @Override
    public byte getID() {
        return ID;
    }

    public void setValue(float value) {
        super.setValue(value);
    }

    @Override
    public boolean equals(Object other) {
        return super.equals(other) && getValue().equals(((FloatTag) other).getValue());
    }

    @Override
    public int compareTo(FloatTag other) {
        return getValue().compareTo(other.getValue());
    }

    @Override
    public FloatTag clone() {
        return new FloatTag(getValue());
    }
}
