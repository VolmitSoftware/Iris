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

package art.arcane.iris.builtin.floating;

/**
 * The face of one island an object is placed against, as {@code iris:floating} samples it: how high the face is, which
 * blocks of the island are solid, and where the object may write without hanging in the air beside the island.
 */
public interface IslandAnchor {
    /** The height of the face at the column; the anchor's own height where the island does not reach. */
    int faceY(int worldX, int worldZ);

    /** Whether an island lies in the column; where none does, the world's blocks say what is solid. */
    boolean hasIsland(int worldX, int worldZ);

    /** Whether the island of the column fills the block. */
    boolean islandSolid(int worldX, int y, int worldZ);

    /** Whether the object may write the block. */
    boolean writes(int worldX, int y, int worldZ);
}
