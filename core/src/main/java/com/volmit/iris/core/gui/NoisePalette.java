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

package com.volmit.iris.core.gui;

import java.awt.Color;

enum NoisePalette {
    TERRAIN("Terrain", 0D, 1D, new int[]{0x071A2F, 0x155E75, 0x2A9D8F, 0xE9C46A, 0xF4F1DE}),
    SIGNED("Signed", -1D, 1D, new int[]{0x173B66, 0x4F86C6, 0xE7EDF3, 0xE89555, 0x9D3B35}),
    GRAYSCALE("Grayscale", 0D, 1D, new int[]{0x050505, 0xFFFFFF});

    static final int INVALID_COLOR = 0xFF2DAA;

    private final String label;
    private final double minimum;
    private final double maximum;
    private final int[] lookup;
    private final Color[] displayColors;

    NoisePalette(String label, double minimum, double maximum, int[] stops) {
        this.label = label;
        this.minimum = minimum;
        this.maximum = maximum;
        this.lookup = buildLookup(stops);
        this.displayColors = buildDisplayColors(lookup);
    }

    int color(double value) {
        if (!Double.isFinite(value)) {
            return INVALID_COLOR;
        }
        return colorFinite(value);
    }

    int colorFinite(double value) {
        double normalized = (value - minimum) / (maximum - minimum);
        return colorNormalized(normalized);
    }

    int colorNormalized(double normalized) {
        double clipped = Math.max(0D, Math.min(1D, normalized));
        return lookup[(int) Math.round(clipped * (lookup.length - 1))];
    }

    Color displayColorNormalized(double normalized) {
        double clipped = Math.max(0D, Math.min(1D, normalized));
        return displayColors[(int) Math.round(clipped * (displayColors.length - 1))];
    }

    String label() {
        return label;
    }

    double minimum() {
        return minimum;
    }

    double maximum() {
        return maximum;
    }

    @Override
    public String toString() {
        return label;
    }

    private static int[] buildLookup(int[] stops) {
        int[] values = new int[256];
        for (int index = 0; index < values.length; index++) {
            double position = (index / 255D) * (stops.length - 1);
            int lowerIndex = Math.min(stops.length - 2, (int) position);
            double fraction = position - lowerIndex;
            values[index] = interpolate(stops[lowerIndex], stops[lowerIndex + 1], fraction);
        }
        return values;
    }

    private static Color[] buildDisplayColors(int[] lookup) {
        Color[] colors = new Color[lookup.length];
        for (int index = 0; index < lookup.length; index++) {
            colors[index] = new Color(lookup[index]);
        }
        return colors;
    }

    private static int interpolate(int from, int to, double fraction) {
        int red = channel(from, 16, to, fraction);
        int green = channel(from, 8, to, fraction);
        int blue = channel(from, 0, to, fraction);
        return (red << 16) | (green << 8) | blue;
    }

    private static int channel(int from, int shift, int to, double fraction) {
        int fromChannel = (from >>> shift) & 0xFF;
        int toChannel = (to >>> shift) & 0xFF;
        return (int) Math.round(fromChannel + ((toChannel - fromChannel) * fraction));
    }
}
