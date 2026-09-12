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

package art.arcane.iris.structure.object;

import art.arcane.iris.generation.decoration.IrisVacuumSettings;

public final class IrisObjectVacuum {
    private static final double DEFAULT_WAVE_AMPLITUDE = 3.0;
    private static final double DEFAULT_WAVE_SCALE = 5.0;

    private IrisObjectVacuum() {
    }

    public static boolean isVacuumMode(ObjectPlaceMode mode) {
        return mode == ObjectPlaceMode.VACUUM
                || mode == ObjectPlaceMode.VACUUM_HIGH
                || mode == ObjectPlaceMode.VACUUM_FAST
                || mode == ObjectPlaceMode.VACUUM_ORGANIC
                || mode == ObjectPlaceMode.VACUUM_WAVY;
    }

    public static int resolveRadius(ObjectPlaceMode mode, IrisVacuumSettings settings) {
        int radius = switch (mode) {
            case VACUUM_HIGH -> 20;
            case VACUUM_FAST -> 8;
            default -> 12;
        };
        if (settings != null && settings.getRadius() > 0) {
            radius = settings.getRadius();
        }
        return Math.max(0, radius);
    }

    public static int resolveStep(ObjectPlaceMode mode) {
        return mode == ObjectPlaceMode.VACUUM_FAST ? 2 : 1;
    }

    public static double resolveFalloff(IrisVacuumSettings settings) {
        return settings != null ? Math.max(0.25, settings.getFalloff()) : 2.0;
    }

    public static double resolveWaveAmplitude(IrisVacuumSettings settings) {
        if (settings == null) {
            return DEFAULT_WAVE_AMPLITUDE;
        }
        return Math.max(0, settings.getWaveAmplitude());
    }

    public static double resolveWaveScale(IrisVacuumSettings settings) {
        if (settings == null) {
            return DEFAULT_WAVE_SCALE;
        }
        double scale = settings.getWaveScale();
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
