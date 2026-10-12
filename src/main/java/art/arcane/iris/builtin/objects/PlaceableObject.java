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

/** An object of the pack, or one made for a placement, that {@link ObjectHost} can place; Iris's own object class is one. */
public interface PlaceableObject {
    /** The key the pack loads the object by, or the key a procedural object is given; null for an object made in code. */
    String key();

    /** How wide the object is, in blocks. */
    int width();

    /** How tall the object is, in blocks. */
    int height();

    /** How deep the object is, in blocks. */
    int depth();
}
