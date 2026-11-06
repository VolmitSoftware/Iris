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

@Desc("How the parts of a cluster are laid out around its center.")
public enum ClusterLayout {
    @Desc("Anywhere inside the radius.")
    RANDOM,
    @Desc("Evenly spaced on a circle of the radius.")
    RING,
    @Desc("In a row across the radius.")
    LINE,
    @Desc("A sunflower spiral filling the radius evenly.")
    GOLDEN
}
