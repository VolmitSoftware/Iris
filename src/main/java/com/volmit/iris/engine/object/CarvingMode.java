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

package com.volmit.iris.engine.object;

import com.volmit.iris.engine.object.annotations.Desc;

@Desc("Defines if an object is allowed to place in carvings, surfaces or both.")
public enum CarvingMode {
    @Desc("Only place this object on surfaces (NOT under carvings)")
    SURFACE_ONLY,

    @Desc("Only place this object under carvings (NOT on the surface)")
    CARVING_ONLY,

    @Desc("This object can place anywhere")
    ANYWHERE;

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean supportsCarving() {
        return this.equals(ANYWHERE) || this.equals(CARVING_ONLY);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean supportsSurface() {
        return this.equals(ANYWHERE) || this.equals(SURFACE_ONLY);
    }
}
