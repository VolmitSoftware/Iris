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

package art.arcane.iris.engine.object.placement;

/** How a placement mode finds the height of an object placed without a requested height. */
enum AnchorSampling {
    /** The surface at the object's center, or the floor of the cave it is placed in. */
    CENTER,
    /** The highest surface under any column of the object. */
    HIGHEST,
    /** The highest surface under the object's four corners. */
    HIGHEST_CORNER,
    /** The lowest surface under any column of the object. */
    LOWEST,
    /** The lowest surface under the object's four corners. */
    LOWEST_CORNER,
    /** The surface at the object's center, ignoring caves; each block then follows its own column. */
    SURFACE,
    /** The translation alone, in open air. */
    TRANSLATION,
    /** The requested height, even when negative. */
    REQUESTED
}
