/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.builtin.loot;

import com.volmit.config.Desc;

@Desc("A loot mode is used to describe what to do with the existing loot layers before adding this loot. ADD appends to the building list of tables (dimension tables, then region tables, then biome tables). REPLACE swaps the parent tables for this reference's tables. CLEAR suppresses loot entirely at this point.")
public enum LootMode {
    @Desc("Add to the existing parent loot tables")
    ADD,
    @Desc("Clear all parent loot tables and contribute nothing at this level. Any tables listed here are ignored.")
    CLEAR,
    @Desc("Replace all parent loot tables with this reference's tables")
    REPLACE,
    @Desc("Only use when there was no loot table defined by an object")
    FALLBACK
}
