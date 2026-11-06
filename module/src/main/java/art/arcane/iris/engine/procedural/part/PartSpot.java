/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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
package art.arcane.iris.engine.procedural.part;

/**
 * Where a part starts: its position, the direction it grows, where it sits on its parent from base (0) to tip (1),
 * how long that parent is, and the compass direction pointing away from the parent's center (NaN when there is none).
 */
record PartSpot(double[] origin, double[] heading, double parentT, double parentLength, double outward) {
    static PartSpot root() {
        return new PartSpot(new double[]{0, 0, 0}, Vectors.UP.clone(), 0, 0, Double.NaN);
    }

    PartSpot movedBy(double[] offset) {
        return new PartSpot(Vectors.add(origin, offset), heading, parentT, parentLength, outward);
    }

    boolean hasOutward() {
        return !Double.isNaN(outward);
    }
}
