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

/** Noise that moves the columns of a placement sideways, so a square object warps like a flag. */
public interface SurfaceWarp {
    /** How far, in blocks, the warp may move a column in total. */
    double multiplier();

    /** The noise at the position, fitted to the range from {@code min} to {@code max}. */
    double fit(double min, double max, double x, double y, double z);
}
