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

package art.arcane.iris.generation.decoration.coral;

import art.arcane.volmlib.util.documentation.Description;

@Description("How the arms of a BRANCHING coral are distributed around the central stalk by compass azimuth.")
public enum IrisCoralBranchAzimuth {
    @Description("Distribute arms by the golden angle (about 137.5 degrees per arm) for a natural phyllotactic spiral where no two arms overlap.")
    GOLDEN_ANGLE,

    @Description("Distribute arms in evenly spaced spokes, dividing 360 degrees equally between them like a radial whorl.")
    EVEN,

    @Description("Place each arm at a fully random azimuth using the deterministic per-variant RNG, for an irregular wild reef.")
    RANDOM
}
