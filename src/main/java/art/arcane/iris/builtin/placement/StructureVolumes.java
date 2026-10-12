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

package art.arcane.iris.builtin.placement;

/** The world-space bounds of the native structure pieces around a placement, in the world's real Y. */
public interface StructureVolumes {
    boolean isEmpty();

    /** Whether any piece meets the box, bounds inclusive. */
    boolean intersects(int minX, int minY, int minZ, int maxX, int maxY, int maxZ);

    /** Whether any piece contains the block or comes within {@code margin} blocks of it. */
    boolean containsWithin(int x, int y, int z, int margin);
}
