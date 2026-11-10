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

package art.arcane.iris.core.studio.noise;

/** The curve through four evenly spaced samples that passes through the middle two. */
public final class CatmullRom {
    private CatmullRom() {
    }

    /** The curve between {@code start} and {@code end}, {@code fraction} of the way from one to the other. */
    public static double between(double before, double start, double end, double after, double fraction) {
        double squared = fraction * fraction;
        double cubed = squared * fraction;
        return 0.5D * (2D * start
                + (end - before) * fraction
                + (2D * before - 5D * start + 4D * end - after) * squared
                + (3D * start - before - 3D * end + after) * cubed);
    }
}
