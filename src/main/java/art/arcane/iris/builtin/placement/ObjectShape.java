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

import art.arcane.volmlib.util.math.IrisBlockVector;

import java.util.List;

/** The object being placed: its declared size, the block its offsets count from, and where it must stand on solid ground. */
public interface ObjectShape {
    int width();

    int height();

    int depth();

    /** The block the object's offsets count from, relative to its corner. */
    int centerX();

    int centerY();

    int centerZ();

    /** The pack's key of the object, or null for an object without one. */
    String key();

    /** The columns of the object's lowest solid layer, as offsets from its anchor before any transform; empty for the origin only. */
    List<IrisBlockVector> surfaceSupportOffsets();
}
