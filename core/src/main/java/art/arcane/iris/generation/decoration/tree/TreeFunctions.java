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

package art.arcane.iris.generation.decoration.tree;

import art.arcane.volmlib.util.math.RNG;

public final class TreeFunctions {
    private static final double GOLDEN_ANGLE = 137.50776405003785;

    private TreeFunctions() {
    }

    public static double trunkWidthMultiplier(IrisProceduralTree tree, double t) {
        double start = tree.getShapeStart();
        double end = tree.getShapeEnd();
        return switch (tree.getTrunkShape()) {
            case CONSTANT -> 1.0;
            case LINEAR -> start + (end - start) * t;
            case SIGMOID -> 1.0 / (1.0 + Math.exp(tree.getShapeSteepness() * (t - 0.5)));
            case LOG -> {
                double base = Math.max(1.0001, tree.getShapeBase());
                if (t <= 0.0) {
                    yield 1.0;
                }
                yield Math.max(0.0, 1.0 - Math.log(1.0 + t * (base - 1.0)) / Math.log(base));
            }
            case SINE -> {
                double period = tree.getShapePeriod() == 0 ? 1.0 : tree.getShapePeriod();
                yield 1.0 + tree.getShapeAmplitude() * Math.sin(2.0 * Math.PI * t / period);
            }
            case PARABOLIC -> {
                double peak = tree.getShapePeakOffset();
                double floor = tree.getShapeFloor();
                double denom = Math.pow(Math.max(peak, 1.0 - peak), 2);
                if (denom == 0) {
                    denom = 1;
                }
                yield floor + (1.0 - floor) * (Math.pow(t - peak, 2) / denom);
            }
            case EXPONENTIAL -> geometric(start, end, t);
            case SQRT -> start + (end - start) * Math.sqrt(t);
            case STEP -> t < tree.getShapePeakOffset() ? start : end;
            case BELL -> start + (end - start) * bell(t);
            case EASE_IN_OUT -> start + (end - start) * smoothstep(t);
        };
    }

    public static double curveProgress(IrisTreeFunction fn, double t, double steepness) {
        return switch (fn) {
            case LINEAR, CONSTANT, SINE -> t;
            case LOG -> Math.log(1.0 + t * (Math.E - 1.0));
            case SIGMOID -> 1.0 / (1.0 + Math.exp(-steepness * (t - 0.5)));
            case PARABOLIC, EASE_IN_OUT -> smoothstep(t);
            case EXPONENTIAL -> Math.pow(t, Math.max(1.0, steepness));
            case SQRT -> Math.sqrt(t);
            case STEP -> t < 0.5 ? 0.0 : 1.0;
            case BELL -> bell(t);
        };
    }

    public static double branchLength(IrisTreeBranches b, double t) {
        double base = b.getLengthBase();
        double crown = b.getLengthCrown();
        double max = b.getLengthMax();
        double v = switch (b.getLengthFunction()) {
            case CONSTANT -> b.getLengthConstant();
            case LINEAR, SINE -> base + (crown - base) * t;
            case SIGMOID -> max / (1.0 + Math.exp(-b.getLengthSteepness() * (t - 0.5)));
            case LOG -> max * Math.log(1.0 + t * (Math.E - 1.0));
            case PARABOLIC -> max * (1.0 - Math.pow(2.0 * t - 1.0, 2));
            case EXPONENTIAL -> geometric(base, crown, t);
            case SQRT -> base + (crown - base) * Math.sqrt(t);
            case STEP -> t < 0.5 ? base : crown;
            case BELL -> base + (max - base) * bell(t);
            case EASE_IN_OUT -> base + (crown - base) * smoothstep(t);
        };
        return Math.max(1.0, v);
    }

    public static double branchProbability(IrisTreeBranches b, double t, long seed) {
        double std = Math.max(b.getProbabilityStd(), 1e-6);
        return switch (b.getProbabilityFunction()) {
            case CONSTANT -> b.getProbabilityConstant();
            case LINEAR -> b.getProbabilityBase() + (b.getProbabilityCrown() - b.getProbabilityBase()) * t;
            case SIGMOID -> 1.0 / (1.0 + Math.exp(-b.getProbabilitySteepness() * (t - b.getProbabilityMidpoint())));
            case TOP_HEAVY -> Math.pow(t, b.getProbabilityExponent());
            case GAUSSIAN -> Math.exp(-0.5 * Math.pow((t - b.getProbabilityMean()) / std, 2));
            case NOISE -> valueNoise1D(t * b.getProbabilityScale(), seed);
            case BOTTOM_HEAVY -> Math.pow(1.0 - t, b.getProbabilityExponent());
            case PERIODIC -> b.getProbabilityConstant() * Math.pow(Math.max(0.0, Math.sin(2.0 * Math.PI * t * b.getProbabilityPeriods())), 8);
            case BAND -> Math.abs(t - b.getProbabilityMean()) <= b.getProbabilityStd() ? b.getProbabilityConstant() : 0.0;
            case INVERSE_GAUSSIAN -> b.getProbabilityConstant() * (1.0 - Math.exp(-0.5 * Math.pow((t - b.getProbabilityMean()) / std, 2)));
            case EXPONENTIAL_DECAY -> b.getProbabilityConstant() * Math.exp(-b.getProbabilityExponent() * t);
        };
    }

    public static double azimuthDegrees(IrisTreeAzimuthMode mode, double t, int index, IrisProceduralTree tree, double constantBase, RNG rng) {
        return switch (mode) {
            case CONSTANT -> constantBase;
            case LINEAR -> tree.getAzimuthStart() + (tree.getAzimuthEnd() - tree.getAzimuthStart()) * t;
            case SPIRAL -> tree.getAzimuthStart() + tree.getAzimuthTurns() * 360.0 * t;
            case SINE -> {
                double period = tree.getAzimuthPeriod() == 0 ? 1.0 : tree.getAzimuthPeriod();
                yield tree.getAzimuthOffset() + tree.getAzimuthAmplitude() * Math.sin(2.0 * Math.PI * t / period);
            }
            case NOISE -> noiseAzimuth(t, tree.getAzimuthScale(), tree.getSeed());
            case RANDOM -> rng != null ? rng.d(0.0, 360.0) : constantBase;
            case GOLDEN_ANGLE -> index >= 0 ? constantBase + index * GOLDEN_ANGLE : constantBase;
            case ALTERNATING -> index >= 0 ? constantBase + (index % 2) * 180.0 : constantBase;
            case WHORL -> {
                int count = Math.max(1, tree.getAzimuthWhorlCount());
                yield index >= 0 ? constantBase + (index % count) * (360.0 / count) : constantBase;
            }
            case ZIGZAG -> index >= 0 ? constantBase + ((index % 2 == 0) ? -tree.getAzimuthAmplitude() : tree.getAzimuthAmplitude()) : constantBase;
        };
    }

    public static TreeBlockCanvas.Axis logAxis(double dx, double dy, double dz) {
        double ax = Math.abs(dx);
        double ay = Math.abs(dy) * 1.8;
        double az = Math.abs(dz);
        if (ay >= ax && ay >= az) {
            return TreeBlockCanvas.Axis.Y;
        }
        if (ax >= az) {
            return TreeBlockCanvas.Axis.X;
        }
        return TreeBlockCanvas.Axis.Z;
    }

    private static double geometric(double a, double b, double t) {
        if (a <= 0.0 || b <= 0.0) {
            return a + (b - a) * t;
        }
        return a * Math.pow(b / a, t);
    }

    private static double bell(double t) {
        return Math.exp(-Math.pow(t - 0.5, 2) / (2.0 * 0.15 * 0.15));
    }

    private static double smoothstep(double t) {
        return 3.0 * t * t - 2.0 * t * t * t;
    }

    public static double noiseAzimuth(double t, double scale, long seed) {
        double a = valueNoise1D(t * scale, seed);
        double b = valueNoise1D(t * scale * 0.5, seed + 1);
        return ((a + b) / 2.0) * 360.0;
    }

    public static double valueNoise1D(double x, long seed) {
        long h = mix(seed * 0x9E3779B97F4A7C15L + Math.round(x * 1000.0));
        return unit(h);
    }

    public static double valueNoise3D(int x, int y, int z, long seed) {
        long h = mix(seed);
        h = mix(h + x * 0x9E3779B97F4A7C15L);
        h = mix(h + y * 0xC2B2AE3D27D4EB4FL);
        h = mix(h + z * 0x165667B19E3779F9L);
        return unit(h);
    }

    private static long mix(long a) {
        a ^= (a >>> 33);
        a *= 0xff51afd7ed558ccdL;
        a ^= (a >>> 33);
        a *= 0xc4ceb9fe1a85ec53L;
        a ^= (a >>> 33);
        return a;
    }

    private static double unit(long h) {
        return (h >>> 11) * 0x1.0p-53;
    }
}
