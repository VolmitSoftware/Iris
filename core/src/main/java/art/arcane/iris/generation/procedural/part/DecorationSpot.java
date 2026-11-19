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
package art.arcane.iris.generation.procedural.part;

import art.arcane.volmlib.util.documentation.Description;

@Description("Where a decoration is placed on the object.")
public enum DecorationSpot {
    @Description("On every block that is open above.")
    ABOVE,
    @Description("Under every block that is open below; set length for hanging strands.")
    BELOW,
    @Description("Against every open horizontal side.")
    SIDE,
    @Description("On the highest block of each column.")
    TOP,
    @Description("Under the lowest block of each column; set length for hanging strands.")
    BOTTOM,
    @Description("Just past the end of every stem.")
    TIPS,
    @Description("On the ground around where the object stands, out to the radius.")
    GROUND
}
