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

package art.arcane.iris.generation.decoration.formation;

import art.arcane.volmlib.util.documentation.Description;

@Description("A width-shaping function evaluated over normalized height (0 at the base, 1 at the top). Controls how the formation radius scales between baseWidth at the bottom and topWidth at the top.")
public enum IrisFormationProfile {
    @Description("Same radius at every height (a straight cylinder of constant baseWidth).")
    CONSTANT,
    @Description("Radius ramps straight from baseWidth at the bottom to topWidth at the top.")
    LINEAR,
    @Description("Radius shrinks smoothly toward a point as height increases (an ease that drives the SPIRE needle and gentle SEA_STACK taper).")
    TAPER,
    @Description("Radius pinches to a narrow waist near profileWaist then widens again toward the top (the eroded waist used by HOODOO columns).")
    PARABOLIC,
    @Description("Radius bulges out to its maximum in the middle of the height range and narrows at both ends (a barrel / bulged column).")
    BULGE
}
