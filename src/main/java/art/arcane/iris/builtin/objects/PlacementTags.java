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

package art.arcane.iris.builtin.objects;

/**
 * What a placement writes into the mantle besides the blocks, beside each block of the object.
 *
 * @param marker the placement marker to write beside every block the object places, or null for none
 * @param treeMaterial whether the block is also written as the tree material the mantle keeps for felling and growth
 * @param buriedTreasure whether storage chests among the blocks are marked as buried treasure
 */
public record PlacementTags(String marker, boolean treeMaterial, boolean buriedTreasure) {
}
