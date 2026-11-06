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

package art.arcane.iris.builtin.anchor;

/** What a placement mode grows between the object and the ground or ceiling. */
public enum StiltShape {
    NONE,
    /** Repeats the bottom layer straight down to the surface. */
    STRAIGHT,
    /** Like {@link #STRAIGHT}, tapering toward the edges and broken up at depth. */
    ERODE,
    /** Repeats the bottom layer down to the first solid block, each column a little different. */
    ORGANIC,
    /** Hangs the flipped object from a ceiling and grows its top layer up into it. */
    CEILING
}
