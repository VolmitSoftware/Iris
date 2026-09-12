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

package art.arcane.iris.generation.decoration.tree;

import art.arcane.volmlib.util.documentation.Description;

@Description("A shaping function evaluated over normalized height (0 at the base, 1 at the top). Used for trunk width, trunk curve and branch length.")
public enum IrisTreeFunction {
    @Description("Same value at every height.")
    CONSTANT,
    @Description("Ramps straight from start to end.")
    LINEAR,
    @Description("S-curve transition controlled by steepness.")
    SIGMOID,
    @Description("Logarithmic falloff controlled by base.")
    LOG,
    @Description("Sine ripple controlled by period and amplitude.")
    SINE,
    @Description("Parabolic pinch, narrowest at peakOffset.")
    PARABOLIC,
    @Description("Geometric (exponential) interpolation from start to end.")
    EXPONENTIAL,
    @Description("Square-root ease: fast change low, slow high.")
    SQRT,
    @Description("Hard step that jumps from start to end at the threshold (peakOffset).")
    STEP,
    @Description("Bell bulge peaking in the middle of the height range.")
    BELL,
    @Description("Smoothstep ease-in-out (3t^2 - 2t^3).")
    EASE_IN_OUT
}
