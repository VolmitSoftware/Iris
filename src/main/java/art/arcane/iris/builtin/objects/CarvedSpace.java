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

import java.util.Optional;

/** The voids the cave carver planned in the mantle around the chunk being placed, which cave objects anchor in. */
public interface CarvedSpace {
    /** Whether the position is carved. */
    boolean isCarved(int x, int y, int z);

    /** Whether each height from 0 up to {@code height} is carved at the column, 1 for carved and 0 for not. */
    byte[] column(int x, int z, int height);

    /** The void at the position; empty where nothing was carved. */
    Optional<Cavern> cavernAt(int x, int y, int z);
}
