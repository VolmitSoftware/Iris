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

@Desc("An inventory slot type is used to represent a type of slot for items to fit into in any given inventory.")
public enum InventorySlotType {
    @Desc("Typically the one you want to go with. Storage represnents most slots in inventories.")
    STORAGE,

    @Desc("Used for the fuel slot in Furnaces, Blast furnaces, smokers etc.")
    FUEL,

    @Desc("Used for the cook slot in furnaces")
    FURNACE,
    @Desc("Used for the cook slot in blast furnaces")
    BLAST_FURNACE,

    @Desc("Used for the cook slot in smokers")
    SMOKER,
}
