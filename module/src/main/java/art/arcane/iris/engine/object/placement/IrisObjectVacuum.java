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

package art.arcane.iris.engine.object.placement;

import art.arcane.iris.builtin.anchor.VacuumProfile;
import art.arcane.iris.builtin.vacuum.VacuumSettings;

public final class IrisObjectVacuum {
    private static final double DEFAULT_WAVE_AMPLITUDE = 3.0;
    private static final double DEFAULT_WAVE_SCALE = 5.0;
    /** The widest bend, as the settings allow it; placements prepare this much around every object. */
    public static final int MAX_RADIUS_BLOCKS = 128;

    private IrisObjectVacuum() {
    }

    public static int resolveRadius(VacuumProfile vacuum, VacuumSettings settings) {
        int radius = vacuum.defaultRadiusBlocks();
        if (settings != null && settings.radius() > 0) {
            radius = settings.radius();
        }
        return Math.max(0, Math.min(MAX_RADIUS_BLOCKS, radius));
    }

    public static double resolveFalloff(VacuumSettings settings) {
        return settings != null ? Math.max(0.25, settings.falloff()) : 2.0;
    }

    public static double resolveWaveAmplitude(VacuumSettings settings) {
        if (settings == null) {
            return DEFAULT_WAVE_AMPLITUDE;
        }
        return Math.max(0, settings.waveAmplitude());
    }

    public static double resolveWaveScale(VacuumSettings settings) {
        if (settings == null) {
            return DEFAULT_WAVE_SCALE;
        }
        double scale = settings.waveScale();
        return scale > 0 ? scale : DEFAULT_WAVE_SCALE;
    }

    public static int footprintLow(int dimension) {
        int size = Math.abs(dimension);
        return -(size / 2);
    }

    public static int footprintHigh(int dimension) {
        int size = Math.abs(dimension);
        if (size <= 0) {
            return 0;
        }
        return size - (size / 2) - 1;
    }

    public static int outset(int delta, int low, int high) {
        if (delta < low) {
            return low - delta;
        }
        if (delta > high) {
            return delta - high;
        }
        return 0;
    }

    /** The lowest height vacuum may target, one above the dimension's bottom (placer heights start at 0 there). */
    public static final int LOWEST_TARGET_Y = 1;

    public static int clampTargetY(int targetY, int lowestY, int highestY) {
        return Math.max(lowestY, Math.min(highestY, targetY));
    }

    public static int columnTargetY(int dx, int dz, int lowX, int highX, int lowZ, int highZ,
                                    double effectiveRadius, double falloff, int originalY, int meetY) {
        double factor = columnInfluence(
                dx, dz, lowX, highX, lowZ, highZ, effectiveRadius, falloff);
        return (int) Math.round(originalY + (((double) meetY - originalY) * factor));
    }

    public static double columnInfluence(int dx, int dz, int lowX, int highX, int lowZ, int highZ,
                                         double effectiveRadius, double falloff) {
        int outX = outset(dx, lowX, highX);
        int outZ = outset(dz, lowZ, highZ);
        double distance = Math.sqrt((double) outX * outX + (double) outZ * outZ);
        if (effectiveRadius <= 0) {
            return distance <= 0 ? 1.0 : 0.0;
        }
        if (distance >= effectiveRadius) {
            return 0.0;
        }
        double t = 1.0 - (distance / effectiveRadius);
        if (t <= 0) {
            return 0.0;
        }
        return Math.pow(t, Math.max(0.25, falloff));
    }

    public static int waveOffset(double distance, double effectiveRadius, double signedNoise, double amplitude) {
        if (amplitude <= 0 || effectiveRadius <= 0) {
            return 0;
        }
        if (distance <= 0 || distance >= effectiveRadius) {
            return 0;
        }
        double envelope = Math.sin(Math.PI * (distance / effectiveRadius));
        return (int) Math.round(signedNoise * amplitude * envelope);
    }

    public static int carveFloorY(int targetY, int objectTopY, boolean insideFootprint) {
        int floor = targetY + 1;
        if (insideFootprint) {
            floor = Math.max(floor, objectTopY + 1);
        }
        return floor;
    }
}
