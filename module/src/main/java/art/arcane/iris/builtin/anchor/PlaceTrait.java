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

/** A trait of an object place mode that changes what the placement stages do. */
public enum PlaceTrait {
    /** Stamps the object at the requested position as it is: no terrain sampling, no height recomputation. */
    RAW_STAMP,
    /** Takes its height from the placement alone, so the terrain, water and carving guards do not apply. */
    FREE_HEIGHT,
    /** Flips the object upside down and anchors its top. */
    UPSIDE_DOWN,
    /** Anchors to a cave's floor or ceiling, so it keeps its own mode underground. */
    CAVE_ANCHORED
}
