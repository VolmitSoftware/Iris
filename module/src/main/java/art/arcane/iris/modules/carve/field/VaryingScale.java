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

package art.arcane.iris.modules.carve.field;

/**
 * A field whose features' size varies from place to place. A noise read at a scale that changes along the way shears
 * and tears, so the scale a field is read at never changes: a field is built at the smallest and at the largest size
 * and the two are blended, horizontal and vertical sizes alike. Each size that varies doubles the fields read.
 */
final class VaryingScale {
    /** Builds a field whose features lie about {@code horizontal} blocks apart sideways and {@code vertical} blocks apart upwards. */
    @FunctionalInterface
    interface Builder {
        Field3 build(CaveNoise noise, double horizontal, double vertical);
    }

    private VaryingScale() {
    }

    /**
     * The field {@code builder} makes at the sizes {@code horizontal} and {@code vertical} pick. The smallest field
     * takes the seeds of {@code noise}, so a field whose sizes are plain numbers is exactly the one the builder makes;
     * the others are seeded by {@code name}.
     */
    static Field3 of(CaveNoise noise, ValueField horizontal, ValueField vertical, String name, Builder builder) {
        Field3 lowest = alongHorizontal(noise, horizontal, vertical.min(), name, builder);
        if (!vertical.varies()) {
            return lowest;
        }
        Field3 tallest = alongHorizontal(noise.named(name + ".tallest"), horizontal, vertical.max(), name, builder);
        return blended(lowest, tallest, vertical);
    }

    private static Field3 alongHorizontal(CaveNoise noise, ValueField horizontal, double vertical, String name, Builder builder) {
        Field3 smallest = builder.build(noise, horizontal.min(), vertical);
        if (!horizontal.varies()) {
            return smallest;
        }
        Field3 largest = builder.build(noise.named(name + ".largest"), horizontal.max(), vertical);
        return blended(smallest, largest, horizontal);
    }

    /**
     * {@code atMin} where {@code scale} is at its min, {@code atMax} where it is at its max, and both in between,
     * weighted so the blend spreads as far as either field alone and a threshold keeps its share.
     */
    private static Field3 blended(Field3 atMin, Field3 atMax, ValueField scale) {
        return (x, y, z) -> {
            double towardsMax = scale.share(x, y, z);
            double towardsMin = 1D - towardsMax;
            double spread = Math.sqrt((towardsMin * towardsMin) + (towardsMax * towardsMax));
            return ((atMin.at(x, y, z) * towardsMin) + (atMax.at(x, y, z) * towardsMax)) / spread;
        };
    }
}
