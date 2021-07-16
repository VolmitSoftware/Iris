/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
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

package com.volmit.iris.util.oldnbt;

import java.util.Arrays;

/**
 * The <code>TAG_Int_Array</code> tag.
 *
 * @author Neil Wightman
 */
public final class IntArrayTag extends Tag {

    /**
     * The value.
     */
    private final int[] value;

    /**
     * Creates the tag.
     *
     * @param name  The name.
     * @param value The value.
     */
    public IntArrayTag(String name, int[] value) {
        super(name);
        this.value = value;
    }

    @Override
    public int[] getValue() {
        return value;
    }

    @Override
    public String toString() {
        String name = getName();
        String append = "";
        if (name != null && !name.equals("")) {
            append = "(\"" + this.getName() + "\")";
        }
        return "TAG_Int_Array" + append + ": " + Arrays.toString(value);
    }

}
