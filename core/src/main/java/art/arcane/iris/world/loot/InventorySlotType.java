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

@Description("An inventory slot type is used to represent a type of slot for items to fit into in any given inventory.")
public enum InventorySlotType {
    @Description("Typically the one you want to go with. Storage represnents most slots in inventories.")
    STORAGE,

    @Description("Used for the fuel slot in Furnaces, Blast furnaces, smokers etc.")
    FUEL,

    @Description("Used for the cook slot in furnaces")
    FURNACE,
    @Description("Used for the cook slot in blast furnaces")
    BLAST_FURNACE,

    @Description("Used for the cook slot in smokers")
    SMOKER,
}
