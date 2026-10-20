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

package com.volmit.iris.engine.object.procedural.tree;

import com.volmit.iris.engine.object.annotations.Desc;

@Desc("A shaping function evaluated over normalized height (0 at the base, 1 at the top). Used for trunk width, trunk curve and branch length.")
public enum TreeFunction {
    @Desc("Same value at every height.")
    CONSTANT,
    @Desc("Ramps straight from start to end.")
    LINEAR,
    @Desc("S-curve transition controlled by steepness.")
    SIGMOID,
    @Desc("Logarithmic falloff controlled by base.")
    LOG,
    @Desc("Sine ripple controlled by period and amplitude.")
    SINE,
    @Desc("Parabolic pinch, narrowest at peakOffset.")
    PARABOLIC,
    @Desc("Geometric (exponential) interpolation from start to end.")
    EXPONENTIAL,
    @Desc("Square-root ease: fast change low, slow high.")
    SQRT,
    @Desc("Hard step that jumps from start to end at the threshold (peakOffset).")
    STEP,
    @Desc("Bell bulge peaking in the middle of the height range.")
    BELL,
    @Desc("Smoothstep ease-in-out (3t^2 - 2t^3).")
    EASE_IN_OUT
}
