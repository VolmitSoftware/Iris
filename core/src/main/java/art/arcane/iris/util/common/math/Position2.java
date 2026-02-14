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

package art.arcane.iris.util.math;

import art.arcane.iris.engine.object.IrisPosition;
import org.bukkit.util.Vector;

public class Position2 extends art.arcane.volmlib.util.math.Position2 {
    public Position2(int x, int z) {
        super(x, z);
    }

    public Position2(Vector center) {
        super(center);
    }

    @Override
    public Position2 regionToChunk() {
        return new Position2(getX() << 5, getZ() << 5);
    }

    @Override
    public Position2 add(int x, int z) {
        return new Position2(getX() + x, getZ() + z);
    }

    @Override
    public Position2 blockToChunk() {
        return new Position2(getX() >> 4, getZ() >> 4);
    }

    public IrisPosition toIris() {
        return new IrisPosition(getX(), 23, getZ());
    }
}
