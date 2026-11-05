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

/** A drawn part that others can grow from. */
interface PartShape {
    /** Where a child sitting at {@code t}, from the base (0) to the tip (1), starts. */
    PartSpot spotAt(double t);

    /** The length children measure against, in blocks. */
    double length();

    /** Where children sit when they do not say. */
    double defaultAt();

    /** The parent of the object's root parts: a single point at the base, pointing up. */
    PartShape ROOT = new PartShape() {
        @Override
        public PartSpot spotAt(double t) {
            return PartSpot.root();
        }

        @Override
        public double length() {
            return 0;
        }

        @Override
        public double defaultAt() {
            return 0;
        }
    };
}
