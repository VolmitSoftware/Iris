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

/** Small vector maths on {@code double[3]}, with compass azimuths measured from south (+z) toward east (+x). */
final class Vectors {
    static final double[] UP = {0, 1, 0};

    private Vectors() {
    }

    /** The unit direction {@code azimuth} degrees around the compass and {@code pitch} degrees above the horizon. */
    static double[] direction(double azimuth, double pitch) {
        double a = Math.toRadians(azimuth);
        double p = Math.toRadians(pitch);
        return new double[]{Math.cos(p) * Math.sin(a), Math.sin(p), Math.cos(p) * Math.cos(a)};
    }

    static double azimuth(double[] direction) {
        return Math.toDegrees(Math.atan2(direction[0], direction[2]));
    }

    static double pitch(double[] direction) {
        return Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, direction[1]))));
    }

    static boolean isVertical(double[] direction) {
        return Math.abs(direction[1]) > 0.999;
    }

    static double[] add(double[] a, double[] b) {
        return new double[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]};
    }

    static double[] subtract(double[] a, double[] b) {
        return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    static double[] scale(double[] a, double factor) {
        return new double[]{a[0] * factor, a[1] * factor, a[2] * factor};
    }

    static double[] addScaled(double[] a, double[] b, double factor) {
        return new double[]{a[0] + b[0] * factor, a[1] + b[1] * factor, a[2] + b[2] * factor};
    }

    static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    static double length(double[] a) {
        return Math.sqrt(dot(a, a));
    }

    /** {@code a} scaled to length 1; {@code fallback} when it has no length. */
    static double[] normalize(double[] a, double[] fallback) {
        double length = length(a);
        return length < 1e-9 ? fallback.clone() : scale(a, 1 / length);
    }

    /** The unit vector across {@code axis} that points most toward {@code toward}; any across one when they line up. */
    static double[] across(double[] axis, double[] toward) {
        double[] flattened = subtract(toward, scale(axis, dot(toward, axis)));
        if (length(flattened) > 1e-6) {
            return normalize(flattened, UP);
        }
        double[] reference = Math.abs(axis[0]) < 0.9 ? new double[]{1, 0, 0} : new double[]{0, 0, 1};
        return normalize(subtract(reference, scale(axis, dot(reference, axis))), UP);
    }

    /** {@code direction} turned {@code degrees} toward {@code azimuth}, for leaning something away from where it points. */
    static double[] tilted(double[] direction, double azimuth, double degrees) {
        double[] toward = across(direction, direction(azimuth, 0));
        double angle = Math.toRadians(degrees);
        return normalize(add(scale(direction, Math.cos(angle)), scale(toward, Math.sin(angle))), direction);
    }
}
