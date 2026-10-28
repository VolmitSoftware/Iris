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

package art.arcane.iris.modules.objects;

/**
 * The outcome of placing an object in a cave.
 *
 * @param resultY the height the object was placed at, negative when the placement rejected it
 * @param commit what became of the blocks the object wrote; {@link TerrainTransaction.Commit#EMPTY} for a rejected
 *               placement, {@link TerrainTransaction.Commit#REJECTED} when some block lay outside the cave
 */
public record CavePlacement(int resultY, TerrainTransaction.Commit commit) {
    public boolean wroteBlocks() {
        return commit == TerrainTransaction.Commit.COMMITTED;
    }
}
