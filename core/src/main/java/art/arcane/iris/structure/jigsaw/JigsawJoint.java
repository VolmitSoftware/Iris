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

package art.arcane.iris.structure.jigsaw;

import art.arcane.volmlib.util.documentation.Description;

@Description("How a jigsaw connection may be rotated when matching two pieces together.")
public enum JigsawJoint {
    @Description("The connecting piece may rotate freely around the connection axis. Use this for vertical connectors (floors, ceilings) and any joint where roll does not matter.")
    ROLLABLE,

    @Description("The connecting piece's rotation is locked to face the connector. Use this for horizontal connectors where the attached piece must align to a specific facing (doorways, street ends).")
    ALIGNED
}
