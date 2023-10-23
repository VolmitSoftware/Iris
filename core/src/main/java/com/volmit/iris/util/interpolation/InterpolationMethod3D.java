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

package com.volmit.iris.util.interpolation;

import com.volmit.iris.engine.object.annotations.Desc;

@Desc("An interpolation method (or function) is simply a method of smoothing a position based on surrounding points on a grid. Bicubic for example is smoother, but has 4 times the checks than Bilinear for example. Try using BILINEAR_STARCAST_9 for beautiful results.")
public enum InterpolationMethod3D {
    TRILINEAR,
    TRICUBIC,
    TRIHERMITE,
    TRISTARCAST_3,
    TRISTARCAST_6,
    TRISTARCAST_9,
    TRISTARCAST_12,
    TRILINEAR_TRISTARCAST_3,
    TRILINEAR_TRISTARCAST_6,
    TRILINEAR_TRISTARCAST_9,
    TRILINEAR_TRISTARCAST_12,
    NONE
}
