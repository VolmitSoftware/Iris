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

import com.volmit.block.BlockState;

/** Where an object placed through {@link ObjectHost#placeInto} reads the world around it and writes its blocks. */
public interface ObjectSink {
    /** The height of the highest block at the column; {@code ignoreFluid} leaves out the fluid above the ground. */
    int highest(int x, int z, boolean ignoreFluid);

    BlockState get(int x, int y, int z);

    void set(int x, int y, int z, BlockState state);

    /** The height of the dimension's fluid. */
    int fluidHeight();
}
