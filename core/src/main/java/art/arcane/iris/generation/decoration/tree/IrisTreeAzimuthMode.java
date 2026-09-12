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

@Description("Controls the compass direction (azimuth) of trunk lean as it rises, or the direction each branch is thrown.")
public enum IrisTreeAzimuthMode {
    @Description("A single fixed azimuth.")
    CONSTANT,
    @Description("Azimuth ramps straight from start to end with height.")
    LINEAR,
    @Description("Azimuth winds around the trunk a number of turns (corkscrew).")
    SPIRAL,
    @Description("Azimuth oscillates with a sine wave.")
    SINE,
    @Description("Azimuth wanders using deterministic noise.")
    NOISE,
    @Description("Each branch picks a uniformly random azimuth (falls back to constant for trunk lean).")
    RANDOM,
    @Description("Phyllotaxis: each successive branch is offset by the 137.5 degree golden angle for the most natural spiral packing (branches).")
    GOLDEN_ANGLE,
    @Description("Each successive branch is thrown to the opposite side (180 degrees apart).")
    ALTERNATING,
    @Description("Branches are grouped into evenly spaced whorls of azimuthWhorlCount around each ring.")
    WHORL,
    @Description("Azimuth zigzags by +/- amplitude on alternating branches.")
    ZIGZAG
}
