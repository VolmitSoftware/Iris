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

package com.volmit.iris.engine.object;

import com.volmit.iris.engine.object.annotations.Desc;

@Desc("A loot mode is used to describe what to do with the existing loot layers before adding this loot. Using ADD will simply add this table to the building list of tables (i.e. add dimension tables, region tables then biome tables). By using clear or replace, you remove the parent tables before and add just your tables.")
public enum IrisLootMode {
    @Desc("Add to the existing parent loot tables")
    ADD,
    @Desc("Clear all loot tables then add this table")
    CLEAR,
    @Desc("Replace all loot tables with this table (same as clear)")
    REPLACE
}
