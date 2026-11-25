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

package art.arcane.iris.generation.cave.field;

/**
 * The heights a carve is limited to, counted from the dimension's bottom and both included. A cave carved inside a
 * band is the very cave it would be without one, cut off at the band's ends.
 */
public record HeightBand(int lowestY, int highestY) {
    public static final HeightBand UNLIMITED = new HeightBand(Integer.MIN_VALUE, Integer.MAX_VALUE);
}
