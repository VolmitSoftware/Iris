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

package art.arcane.iris.core.events;

/** The kind of slot of an inventory that loot fills. World generation only ever fills {@link #STORAGE}. */
public enum InventorySlotType {
    /** Most slots of an inventory. */
    STORAGE,
    /** The fuel slot of furnaces, blast furnaces and smokers. */
    FUEL,
    /** The cook slot of furnaces. */
    FURNACE,
    /** The cook slot of blast furnaces. */
    BLAST_FURNACE,
    /** The cook slot of smokers. */
    SMOKER
}
