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

package art.arcane.iris.generation.decoration.crystal;


public final class CrystalGeometry {
    private CrystalGeometry() {
    }

    public static double[] surfaceNormal(IrisCrystalSurface surface) {
        return switch (surface) {
            case FLOOR -> new double[]{0.0, 1.0, 0.0};
            case CEILING -> new double[]{0.0, -1.0, 0.0};
            case WALL -> {
                double len = Math.sqrt(1.0 + 0.5 * 0.5);
                yield new double[]{1.0 / len, 0.5 / len, 0.0};
            }
        };
    }

    public static double[] orthonormalBasisU(double[] normal) {
        double[] reference = Math.abs(normal[1]) > 0.99 ? new double[]{1.0, 0.0, 0.0} : new double[]{0.0, 1.0, 0.0};
        double[] u = cross(reference, normal);
        normalize(u);
        return u;
    }

    public static double[] orthonormalBasisV(double[] normal, double[] u) {
        double[] v = cross(normal, u);
        normalize(v);
        return v;
    }

    public static double[] coneDirection(double[] normal, double[] u, double[] v, double coneAngleRadians, double azimuthRadians) {
        double sin = Math.sin(coneAngleRadians);
        double cos = Math.cos(coneAngleRadians);
        double ca = Math.cos(azimuthRadians);
        double sa = Math.sin(azimuthRadians);
        double[] dir = new double[3];
        for (int i = 0; i < 3; i++) {
            dir[i] = cos * normal[i] + sin * (ca * u[i] + sa * v[i]);
        }
        normalize(dir);
        return dir;
    }

    public static double[] cross(double[] a, double[] b) {
        return new double[]{
                a[1] * b[2] - a[2] * b[1],
                a[2] * b[0] - a[0] * b[2],
                a[0] * b[1] - a[1] * b[0]
        };
    }

    public static void normalize(double[] vec) {
        double len = Math.sqrt(vec[0] * vec[0] + vec[1] * vec[1] + vec[2] * vec[2]);
        if (len < 1e-9) {
            vec[0] = 0.0;
            vec[1] = 1.0;
            vec[2] = 0.0;
            return;
        }
        vec[0] /= len;
        vec[1] /= len;
        vec[2] /= len;
    }
}
