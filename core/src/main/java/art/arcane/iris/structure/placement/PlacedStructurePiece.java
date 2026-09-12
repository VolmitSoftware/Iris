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

package art.arcane.iris.structure.placement;

import art.arcane.iris.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.structure.object.IrisObjectRotation;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PlacedStructurePiece {
    private final IrisJigsawPiece piece;
    private final IrisObject object;
    private final int x;
    private final int y;
    private final int z;
    private final IrisObjectRotation rotation;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;

    public boolean intersects(PlacedStructurePiece o) {
        long maxExclusiveX = (long) maxX + 1L;
        long maxExclusiveY = (long) maxY + 1L;
        long maxExclusiveZ = (long) maxZ + 1L;
        long otherMaxExclusiveX = (long) o.maxX + 1L;
        long otherMaxExclusiveY = (long) o.maxY + 1L;
        long otherMaxExclusiveZ = (long) o.maxZ + 1L;
        return minX < otherMaxExclusiveX && maxExclusiveX > o.minX
                && minY < otherMaxExclusiveY && maxExclusiveY > o.minY
                && minZ < otherMaxExclusiveZ && maxExclusiveZ > o.minZ;
    }
}
