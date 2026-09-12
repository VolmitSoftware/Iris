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

package art.arcane.iris.world.loot;

import art.arcane.volmlib.util.documentation.Description;

@Description("A loot mode is used to describe what to do with the existing loot layers before adding this loot. ADD appends to the building list of tables (dimension tables, then region tables, then biome tables). REPLACE swaps the parent tables for this reference's tables. CLEAR suppresses loot entirely at this point.")
public enum IrisLootMode {
    @Description("Add to the existing parent loot tables")
    ADD,
    @Description("Clear all parent loot tables and contribute nothing at this level. Any tables listed here are ignored.")
    CLEAR,
    @Description("Replace all parent loot tables with this reference's tables")
    REPLACE,
    @Description("Only use when there was no loot table defined by an object")
    FALLBACK
}
