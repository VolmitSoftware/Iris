/*
 * Iris is a World Generator for Minecraft Bukkit Servers
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

package art.arcane.iris.generation.biome;

import art.arcane.volmlib.util.noise.CNG;

public final class FloatingIslandEdgeProfile {
    public static final int DEFAULT_WIDTH = 10;
    public static final int MIN_WIDTH = 2;
    public static final int MAX_WIDTH = 32;
    public static final double DEFAULT_EXPONENT = 1.0D;
    public static final double MIN_EXPONENT = 0.25D;
    public static final double MAX_EXPONENT = 4.0D;
    public static final double MAX_VARIATION_AMPLITUDE = 8.0D;
    public static final FloatingIslandEdgeProfile DEFAULT = new FloatingIslandEdgeProfile(
            DEFAULT_WIDTH, DEFAULT_EXPONENT, 0.0D, null);

    private final int width;
    private final double exponent;
    private final double variationAmplitude;
    private final CNG variation;

    public FloatingIslandEdgeProfile(int width, double exponent, double variationAmplitude, CNG variation) {
        this.width = clampWidth(width);
        this.exponent = clampExponent(exponent);
        this.variationAmplitude = clampVariationAmplitude(variationAmplitude, this.width);
        this.variation = variation;
    }

    public int width() {
        return width;
    }

    public double exponent() {
        return exponent;
    }

    public double variationAmplitude() {
        return variationAmplitude;
    }

    public int fieldWidth() {
        return Math.min(MAX_WIDTH, (int) Math.ceil(width + variationAmplitude));
    }

    public double fade(int boundaryDistance, int x, int z) {
        if (boundaryDistance <= 1) {
            return 0.0D;
        }
        double localWidth = width;
        if (variation != null && variationAmplitude > 0.0D) {
            double noise = Math.max(0.0D, Math.min(1.0D, variation.noise(x, z)));
            double signedNoise = (noise * 2.0D) - 1.0D;
            localWidth += signedNoise * variationAmplitude;
        }

        double position = Math.max(0.0D, Math.min(1.0D, (boundaryDistance - 1.0D) / localWidth));
        double smooth = position * position * (3.0D - (2.0D * position));
        return exponent == DEFAULT_EXPONENT ? smooth : Math.pow(smooth, exponent);
    }

    public static int clampWidth(int width) {
        return Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, width));
    }

    public static double clampExponent(double exponent) {
        return Math.max(MIN_EXPONENT, Math.min(MAX_EXPONENT, exponent));
    }

    public static double clampVariationAmplitude(double amplitude, int width) {
        int clampedWidth = clampWidth(width);
        double widthLimit = Math.min(clampedWidth - MIN_WIDTH, MAX_WIDTH - clampedWidth);
        return Math.max(0.0D, Math.min(Math.min(MAX_VARIATION_AMPLITUDE, widthLimit), amplitude));
    }
}
