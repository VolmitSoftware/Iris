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

import com.volmit.geometry.BlockBox;
import com.volmit.mantle.CarvedVoid;
import com.volmit.mantle.MantleLayer;

import java.util.Optional;

/**
 * The voids the cave carver planned in the mantle around the chunk being placed, which cave objects anchor in. Heights
 * are above the dimension's bottom; outside the mantle the invocation reaches nothing is carved.
 */
public final class CarvedSpace {
    private final MantleLayer<CarvedVoid> voids;
    private final BlockBox reach;

    public CarvedSpace(MantleLayer<CarvedVoid> voids, BlockBox reach) {
        this.voids = voids;
        this.reach = reach;
    }

    /** Whether the position is carved. */
    public boolean isCarved(int x, int y, int z) {
        return voidAt(x, y, z).isPresent();
    }

    /** Whether each height from 0 up to {@code height} is carved at the column, 1 for carved and 0 for not. */
    public byte[] column(int x, int z, int height) {
        int cappedHeight = Math.min(Math.max(height, 0), reach.maxY());
        byte[] carvedColumn = new byte[cappedHeight];
        for (int y = 0; y < cappedHeight; y++) {
            if (reach.contains(x, y, z) && voids.get(x, y, z) != null) {
                carvedColumn[y] = 1;
            }
        }
        return carvedColumn;
    }

    /** The void at the position; empty where nothing was carved. */
    public Optional<CarvedVoid> voidAt(int x, int y, int z) {
        if (!reach.contains(x, y, z)) {
            return Optional.empty();
        }
        return Optional.ofNullable(voids.get(x, y, z));
    }
}
