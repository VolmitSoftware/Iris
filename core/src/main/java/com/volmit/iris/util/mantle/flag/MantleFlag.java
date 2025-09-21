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

package com.volmit.iris.util.mantle.flag;

import org.jetbrains.annotations.Contract;

public sealed interface MantleFlag permits CustomFlag, ReservedFlag {
    int MIN_ORDINAL = 64;
    int MAX_ORDINAL = 255;

    MantleFlag OBJECT = ReservedFlag.OBJECT;
    MantleFlag UPDATE = ReservedFlag.UPDATE;
    MantleFlag JIGSAW = ReservedFlag.JIGSAW;
    MantleFlag FEATURE = ReservedFlag.FEATURE;
    MantleFlag INITIAL_SPAWNED = ReservedFlag.INITIAL_SPAWNED;
    MantleFlag REAL = ReservedFlag.REAL;
    MantleFlag CARVED = ReservedFlag.CARVED;
    MantleFlag FLUID_BODIES = ReservedFlag.FLUID_BODIES;
    MantleFlag INITIAL_SPAWNED_MARKER = ReservedFlag.INITIAL_SPAWNED_MARKER;
    MantleFlag CLEANED = ReservedFlag.CLEANED;
    MantleFlag PLANNED = ReservedFlag.PLANNED;
    MantleFlag ETCHED = ReservedFlag.ETCHED;
    MantleFlag TILE = ReservedFlag.TILE;
    MantleFlag CUSTOM = ReservedFlag.CUSTOM;
    MantleFlag DISCOVERED = ReservedFlag.DISCOVERED;
    MantleFlag CUSTOM_ACTIVE = ReservedFlag.CUSTOM_ACTIVE;
    MantleFlag SCRIPT = ReservedFlag.SCRIPT;

    int RESERVED_FLAGS = ReservedFlag.values().length;

    String name();
    int ordinal();

    boolean isCustom();

    @Contract(value = "_ -> new", pure = true)
    static MantleFlag of(int ordinal) {
        if (ordinal < MIN_ORDINAL || ordinal > MAX_ORDINAL)
            throw new IllegalArgumentException("Ordinal must be between " + MIN_ORDINAL + " and " + MAX_ORDINAL);
        return new CustomFlag("CUSTOM:"+ordinal, ordinal);
    }
}
