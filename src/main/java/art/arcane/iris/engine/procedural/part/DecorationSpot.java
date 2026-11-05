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
package art.arcane.iris.engine.procedural.part;

import com.volmit.config.Desc;

@Desc("Where a decoration is placed on the object.")
public enum DecorationSpot {
    @Desc("On every block that is open above.")
    ABOVE,
    @Desc("Under every block that is open below; set length for hanging strands.")
    BELOW,
    @Desc("Against every open horizontal side.")
    SIDE,
    @Desc("On the highest block of each column.")
    TOP,
    @Desc("Under the lowest block of each column; set length for hanging strands.")
    BOTTOM,
    @Desc("Just past the end of every stem.")
    TIPS,
    @Desc("On the ground around where the object stands, out to the radius.")
    GROUND
}
