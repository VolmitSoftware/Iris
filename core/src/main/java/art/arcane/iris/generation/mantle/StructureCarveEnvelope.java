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

package art.arcane.iris.generation.mantle;

import art.arcane.iris.structure.placement.IrisStructureCarveShape;
import art.arcane.volmlib.util.noise.CNG;

/**
 * Carve envelope math shared by the Iris-assembled structure path and the native (vanilla template)
 * structure path so both shrinkwrap a structure footprint the same way. The lobe channel and the
 * eroded floor are only sampled by the native path; the Iris-assembled path still carves a uniform
 * side reach and a uniform floor cut.
 */
public final class StructureCarveEnvelope {
    private static final double CEILING_LOBE_RATIO = 0.5D;
    private static final double CEILING_ROLL_FREQUENCY_RATIO = 3D / 7D;
    private static final double FLOOR_ROLL_AMPLITUDE = 0.35D;
    private static final double FLOOR_ROLL_FREQUENCY_RATIO = 5D / 11D;
    // Maps a lobe frequency onto the smooth 2D channel so the auto-derived 0.015 lands near a
    // 40 block wavelength: wide enough that walls bulge and recede instead of tracking the footprint.
    private static final double LOBE_FREQUENCY_SCALE = 256D;

    private StructureCarveEnvelope() {
    }

    /**
     * Per-column horizontal carve reach. The lobe channel only ever removes padding, so the boundary
     * stays inside the footprint bounds the carve was sized against and never falls below
     * {@code horizontalPadding * (1 - lobeStrength)}.
     */
    public static double lobedSideReach(CNG lobe, double lobeFrequency, double lobeStrength,
                                        int x, int z, int horizontalPadding) {
        if (horizontalPadding <= 0) {
            return 0D;
        }
        return horizontalPadding * lobeFactor(lobe, lobeFrequency, lobeStrength, x, z);
    }

    /**
     * Ceiling counterpart of {@link #lobedSideReach}, deliberately half as strong so the roof swells
     * with the same lobes without collapsing onto the pieces.
     */
    public static double lobedUpReach(CNG lobe, double lobeFrequency, double lobeStrength,
                                      int x, int z, double upReach) {
        if (upReach <= 0D) {
            return upReach;
        }
        return Math.max(1D, upReach
                * lobeFactor(lobe, lobeFrequency, lobeStrength * CEILING_LOBE_RATIO, x, z));
    }

    private static double lobeFactor(CNG lobe, double lobeFrequency, double lobeStrength,
                                     int x, int z) {
        double clampedStrength = Math.max(0D, Math.min(1D, lobeStrength));
        if (lobe == null || clampedStrength <= 0D) {
            return 1D;
        }
        double scaledFrequency = lobeFrequency * LOBE_FREQUENCY_SCALE;
        double sample = Math.max(0D, Math.min(1D, lobe.fitDouble(
                0D, 1D, x * scaledFrequency, z * scaledFrequency)));
        return 1D - clampedStrength * (1D - sample);
    }

    public static double erodedUpReach(CNG roll, double frequency, double strength, int x, int z, int head) {
        if (head <= 0) {
            return 0D;
        }
        double clampedStrength = Math.max(0D, Math.min(1D, strength));
        if (clampedStrength <= 0D) {
            return Math.max(1D, head);
        }
        double rollFrequency = frequency * CEILING_ROLL_FREQUENCY_RATIO;
        double sample = roll.fitDouble(0D, 1D, x * rollFrequency, z * rollFrequency) * 0.7D
                + roll.fitDouble(0D, 1D, x * rollFrequency * 3D, z * rollFrequency * 3D) * 0.3D;
        double contrast = Math.max(0D, Math.min(1D, (sample - 0.5D) * 2.6D + 0.5D));
        double roundedReach = Math.max(1D, head);
        double erodedReach = Math.max(1D, head * (0.4D + 1.4D * contrast));
        return roundedReach + clampedStrength * (erodedReach - roundedReach);
    }

    /**
     * Floor counterpart of {@link #erodedUpReach}. The amplitude is deliberately small and the result
     * never exceeds the configured floor padding, so a padding of zero leaves every column standing on
     * its own supporting block instead of undermining the structure.
     */
    public static double erodedDownReach(CNG roll, double frequency, double strength, int x, int z, int floorCut) {
        if (floorCut <= 0) {
            return 0D;
        }
        double clampedStrength = Math.max(0D, Math.min(1D, strength));
        if (clampedStrength <= 0D) {
            return floorCut;
        }
        double rollFrequency = frequency * FLOOR_ROLL_FREQUENCY_RATIO;
        double sample = roll.fitDouble(0D, 1D, x * rollFrequency, z * rollFrequency);
        return floorCut * (1D - clampedStrength * FLOOR_ROLL_AMPLITUDE * (1D - sample));
    }

    public static double normalizedVerticalDistance(int y, int sourceMinY, int sourceMaxY,
                                                    double upReach, double downReach) {
        if (y > sourceMaxY) {
            return (y - sourceMaxY) / upReach;
        }
        if (y < sourceMinY) {
            return (sourceMinY - y) / downReach;
        }
        return 0D;
    }

    public static double overboreBoundaryLimit(double noise, double strength) {
        double clampedNoise = Math.max(0.0, Math.min(1.0, noise));
        double clampedStrength = Math.max(0.0, Math.min(1.0, strength));
        return 1D - clampedStrength * (1D - clampedNoise);
    }

    public static boolean shouldCarveOverboreCell(IrisStructureCarveShape shape, double distanceSquared,
                                                  double noise, double strength) {
        if (distanceSquared <= 0.0) {
            return true;
        }
        IrisStructureCarveShape resolvedShape = shape == null ? IrisStructureCarveShape.ERODED : shape;
        return switch (resolvedShape) {
            case BOX -> true;
            case ROUNDED -> distanceSquared <= 1D;
            case ERODED -> {
                double limit = overboreBoundaryLimit(noise, strength);
                yield distanceSquared <= 1D && distanceSquared <= limit * limit;
            }
        };
    }
}
