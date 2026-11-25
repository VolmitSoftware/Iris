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

package art.arcane.iris.generation.mantle;

/**
 * What caves know of the world they carve. Heights count from the dimension's bottom, except
 * {@code minHeight}, the world height of that bottom.
 *
 * @param minHeight     world Y of the dimension's lowest block
 * @param height        the dimension's height in blocks
 * @param carveSeed     the seed caves carve with
 * @param fluidHeight   the height the dimension's fluid fills up to
 * @param caveLavaHeight the height below which caves hold lava
 */
record CaveWorld(int minHeight, int height, long carveSeed, int fluidHeight, int caveLavaHeight) {
}
