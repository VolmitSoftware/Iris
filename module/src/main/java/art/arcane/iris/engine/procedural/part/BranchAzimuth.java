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

@Desc("Which way around its parent each new branch points.")
public enum BranchAzimuth {
    @Desc("Any direction.")
    RANDOM,
    @Desc("Each branch turns the golden angle (about 137.5 degrees) from the last, so none line up.")
    GOLDEN,
    @Desc("Each branch points opposite the last.")
    ALTERNATE,
    @Desc("Branches come in rings of 'whorl' evenly spaced branches.")
    WHORL,
    @Desc("All branches are spread evenly around the parent.")
    EVEN,
    @Desc("Branches fan out side by side around the parent's own direction, 'fan' degrees apart.")
    FAN
}
