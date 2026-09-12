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

package art.arcane.iris.structure.nativegen;

public record NativeStructureFrequencyScale(float frequency, int spacing) {
    public static NativeStructureFrequencyScale randomSpread(
            float frequency,
            int spacing,
            int separation,
            double multiplier
    ) {
        if (!Float.isFinite(frequency) || frequency < 0F || frequency > 1F) {
            throw new IllegalArgumentException("Native structure frequency must be between 0 and 1");
        }
        if (spacing <= separation) {
            throw new IllegalArgumentException("Native structure spacing must exceed separation");
        }
        requireMultiplier(multiplier);
        if (frequency == 0F || multiplier == 1D) {
            return new NativeStructureFrequencyScale(frequency, spacing);
        }

        double requestedFrequency = frequency * multiplier;
        float scaledFrequency = (float) Math.min(1D, requestedFrequency);
        double remainingDensity = requestedFrequency / scaledFrequency;
        int scaledSpacing = spacing;
        if (remainingDensity > 1D) {
            int requestedSpacing = (int) Math.round(spacing / Math.sqrt(remainingDensity));
            scaledSpacing = Math.max(separation + 1, requestedSpacing);
        }
        return new NativeStructureFrequencyScale(scaledFrequency, scaledSpacing);
    }

    public static float probability(float frequency, double multiplier) {
        if (!Float.isFinite(frequency) || frequency < 0F || frequency > 1F) {
            throw new IllegalArgumentException("Native structure frequency must be between 0 and 1");
        }
        requireMultiplier(multiplier);
        return (float) Math.min(1D, frequency * multiplier);
    }

    private static void requireMultiplier(double multiplier) {
        if (!Double.isFinite(multiplier) || multiplier < 0.01D || multiplier > 16D) {
            throw new IllegalArgumentException("Native structure frequency multiplier must be between 0.01 and 16");
        }
    }
}
