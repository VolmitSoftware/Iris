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

package com.volmit.iris.util;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/*
  Changes : Neil Wightman - Support 19133 Tag_Int_Array tag
 */

/**
 * A class which holds constant values.
 *
 * @author Graham Edgecombe
 */
public final class NBTConstants {

    /**
     * The character set used by NBT (UTF-8).
     */
    public static final Charset CHARSET = StandardCharsets.UTF_8;

    /**
     * Tag type constants.
     */
    public static final int TYPE_END = 0,
            TYPE_BYTE = 1,
            TYPE_SHORT = 2,
            TYPE_INT = 3,
            TYPE_LONG = 4,
            TYPE_FLOAT = 5,
            TYPE_DOUBLE = 6,
            TYPE_BYTE_ARRAY = 7,
            TYPE_STRING = 8,
            TYPE_LIST = 9,
            TYPE_COMPOUND = 10,
            TYPE_INT_ARRAY = 11;

    /**
     * Default private constructor.
     */
    private NBTConstants() {

    }

}
