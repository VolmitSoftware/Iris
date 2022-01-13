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

public class StringTag extends Tag<String> implements Comparable<StringTag> {

    public static final byte ID = 8;
    public static final String ZERO_VALUE = "";

    public StringTag() {
        super(ZERO_VALUE);
    }

    public StringTag(String value) {
        super(value);
    }

    @Override
    public byte getID() {
        return ID;
    }

    @Override
    public String getValue() {
        return super.getValue();
    }

    @Override
    public void setValue(String value) {
        super.setValue(value);
    }

    @Override
    public String valueToString(int maxDepth) {
        return escapeString(getValue(), false);
    }

    @Override
    public boolean equals(Object other) {
        return super.equals(other) && getValue().equals(((StringTag) other).getValue());
    }

    @Override
    public int compareTo(StringTag o) {
        return getValue().compareTo(o.getValue());
    }

    @Override
    public StringTag clone() {
        return new StringTag(getValue());
    }
}
