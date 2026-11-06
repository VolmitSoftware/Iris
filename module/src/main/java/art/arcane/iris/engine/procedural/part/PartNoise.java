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

/** Seeded noise for parts: a hash per block for speckle, and smooth value noise for patches and wandering. */
final class PartNoise {
    private static final double PATCH_CONTRAST = 1.8;

    private PartNoise() {
    }

    /** A value from 0 to 1 that is the same for the same block and seed. */
    static double hash(int x, int y, int z, long seed) {
        long h = mix(seed);
        h = mix(h + x * 0x9E3779B97F4A7C15L);
        h = mix(h + y * 0xC2B2AE3D27D4EB4FL);
        h = mix(h + z * 0x165667B19E3779F9L);
        return unit(h);
    }

    /** Smooth value noise from 0 to 1 with features {@code scale} blocks across, stretched so thresholds split it evenly. */
    static double patches(double x, double y, double z, double scale, long seed) {
        double size = Math.max(1, scale);
        double value = smooth(x / size, y / size, z / size, seed);
        return Math.max(0, Math.min(1, (value - 0.5) * PATCH_CONTRAST + 0.5));
    }

    /** Smooth value noise from 0 to 1 along a line. */
    static double line(double t, long seed) {
        int cell = (int) Math.floor(t);
        double blend = fade(t - cell);
        double a = hash(cell, 0, 0, seed);
        double b = hash(cell + 1, 0, 0, seed);
        return a + (b - a) * blend;
    }

    /** Whether a block passes a chance under a pattern. */
    static boolean passes(PartPattern pattern, double chance, double scale, int x, int y, int z, long seed) {
        if (chance >= 1) {
            return true;
        }
        if (chance <= 0) {
            return false;
        }
        double roll = pattern == PartPattern.PATCHES ? patches(x, y, z, scale, seed) : hash(x, y, z, seed);
        return roll < chance;
    }

    private static double smooth(double x, double y, double z, long seed) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        int z0 = (int) Math.floor(z);
        double fx = fade(x - x0);
        double fy = fade(y - y0);
        double fz = fade(z - z0);
        double c00 = lerp(hash(x0, y0, z0, seed), hash(x0 + 1, y0, z0, seed), fx);
        double c10 = lerp(hash(x0, y0 + 1, z0, seed), hash(x0 + 1, y0 + 1, z0, seed), fx);
        double c01 = lerp(hash(x0, y0, z0 + 1, seed), hash(x0 + 1, y0, z0 + 1, seed), fx);
        double c11 = lerp(hash(x0, y0 + 1, z0 + 1, seed), hash(x0 + 1, y0 + 1, z0 + 1, seed), fx);
        return lerp(lerp(c00, c10, fy), lerp(c01, c11, fy), fz);
    }

    private static double fade(double t) {
        return t * t * (3 - 2 * t);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    static long mix(long a) {
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
