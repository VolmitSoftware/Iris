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

package com.volmit.iris.util.matter;

import com.volmit.iris.util.mantle.Mantle;

public interface MatterPlacer {
    int getHeight(int x, int z, boolean ignoreFluid);

    Mantle getMantle();

    default <T> void set(int x, int y, int z, T t) {
        getMantle().set(x, y, z, t);
    }

    default <T> T get(int x, int y, int z, Class<T> t) {
        return getMantle().get(x, y, z, t);
    }

    default void set(int x, int y, int z, Matter matter) {
        for (MatterSlice<?> i : matter.getSliceMap().values()) {
            set(x, y, z, i);
        }
    }

    default <T> void set(int x, int y, int z, MatterSlice<T> slice) {
        getMantle().set(x, y, z, slice);
    }

    default int getHeight(int x, int z) {
        return getHeight(x, z, true);
    }

    default int getHeightOrFluid(int x, int z) {
        return getHeight(x, z, false);
    }
}
