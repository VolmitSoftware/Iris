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

import art.arcane.iris.engine.object.noise.NoiseStyle;

/** The groups the noise view lists its sources in, in this order. */
public enum NoiseCategory {
    PACK_GENERATORS("Pack generators"),
    PACK_EXPRESSIONS("Pack expressions"),
    SIMPLEX("Simplex"),
    PERLIN("Perlin"),
    CELLULAR("Cellular"),
    IRIS("Iris"),
    CLOVER("Clover"),
    HEXAGON("Hexagon"),
    VASCULAR("Vascular"),
    GLOBE("Globe"),
    CUBIC("Cubic"),
    FRACTAL("Fractal"),
    STATIC("Static"),
    NOWHERE("Nowhere"),
    SIERPINSKI("Sierpinski"),
    UTILITY("Utility"),
    OTHER("Other");

    private final String label;

    NoiseCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    static NoiseCategory of(NoiseStyle style) {
        String name = style.name();
        if (name.startsWith("STATIC")) {
            return STATIC;
        }
        if (name.startsWith("IRIS")) {
            return IRIS;
        }
        if (name.startsWith("CLOVER")) {
            return CLOVER;
        }
        if (name.startsWith("VASCULAR")) {
            return VASCULAR;
        }
        if (name.equals("FLAT")) {
            return UTILITY;
        }
        if (name.startsWith("CELLULAR")) {
            return CELLULAR;
        }
        if (name.startsWith("HEX")) {
            return HEXAGON;
        }
        if (name.startsWith("SIERPINSKI")) {
            return SIERPINSKI;
        }
        if (name.startsWith("NOWHERE")) {
            return NOWHERE;
        }
        if (name.startsWith("GLOB")) {
            return GLOBE;
        }
        if (name.startsWith("PERLIN")) {
            return PERLIN;
        }
        if (name.startsWith("CUBIC") || (name.startsWith("FRACTAL") && name.contains("CUBIC"))) {
            return CUBIC;
        }
        if (name.contains("SIMPLEX") && !name.startsWith("FRACTAL")) {
            return SIMPLEX;
        }
        if (name.startsWith("FRACTAL")) {
            return FRACTAL;
        }
        return OTHER;
    }
}
