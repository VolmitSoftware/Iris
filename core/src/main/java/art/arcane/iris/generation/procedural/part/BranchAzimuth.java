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

@Description("Which way around its parent each new branch points.")
public enum BranchAzimuth {
    @Description("Any direction.")
    RANDOM,
    @Description("Each branch turns the golden angle (about 137.5 degrees) from the last, so none line up.")
    GOLDEN,
    @Description("Each branch points opposite the last.")
    ALTERNATE,
    @Description("Branches come in rings of 'whorl' evenly spaced branches.")
    WHORL,
    @Description("All branches are spread evenly around the parent.")
    EVEN,
    @Description("Branches fan out side by side around the parent's own direction, 'fan' degrees apart.")
    FAN
}
