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


package art.arcane.iris.studio.map;

/**
 * An affine transform built up like Minecraft's pose stack: each step applies to points before the steps added
 * earlier. Rotations follow the right-hand rule, the way Minecraft turns model parts.
 */
final class Transform {
    static final Transform IDENTITY = new Transform(new double[]{1D, 0D, 0D, 0D, 1D, 0D, 0D, 0D, 1D}, Vec3.ZERO);

    private final double[] matrix;
    private final Vec3 translation;

    private Transform(double[] matrix, Vec3 translation) {
        this.matrix = matrix;
        this.translation = translation;
    }

    Transform translate(double x, double y, double z) {
        return then(new Transform(IDENTITY.matrix, new Vec3(x, y, z)));
    }

    Transform rotateX(double degrees) {
        double cos = cosine(degrees);
        double sin = sine(degrees);
        return then(new Transform(new double[]{1D, 0D, 0D, 0D, cos, -sin, 0D, sin, cos}, Vec3.ZERO));
    }

    Transform rotateY(double degrees) {
        double cos = cosine(degrees);
        double sin = sine(degrees);
        return then(new Transform(new double[]{cos, 0D, sin, 0D, 1D, 0D, -sin, 0D, cos}, Vec3.ZERO));
    }

    Transform rotateZ(double degrees) {
        double cos = cosine(degrees);
        double sin = sine(degrees);
        return then(new Transform(new double[]{cos, -sin, 0D, sin, cos, 0D, 0D, 0D, 1D}, Vec3.ZERO));
    }

    Transform scale(double x, double y, double z) {
        return then(new Transform(new double[]{x, 0D, 0D, 0D, y, 0D, 0D, 0D, z}, Vec3.ZERO));
    }

    /**
     * This transform applied after {@code next}.
     */
    Transform then(Transform next) {
        double[] product = new double[9];
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                product[row * 3 + column] = matrix[row * 3] * next.matrix[column]
                        + matrix[row * 3 + 1] * next.matrix[3 + column]
                        + matrix[row * 3 + 2] * next.matrix[6 + column];
            }
        }
        return new Transform(product, direction(next.translation).plus(translation));
    }

    Vec3 point(Vec3 point) {
        return direction(point).plus(translation);
    }

    Vec3 direction(Vec3 direction) {
        return new Vec3(
                matrix[0] * direction.x() + matrix[1] * direction.y() + matrix[2] * direction.z(),
                matrix[3] * direction.x() + matrix[4] * direction.y() + matrix[5] * direction.z(),
                matrix[6] * direction.x() + matrix[7] * direction.y() + matrix[8] * direction.z());
    }

    // Quarter turns stay exact, so faces turned by a blockstate still line up with the block's sides.
    private static double cosine(double degrees) {
        return sine(degrees + 90D);
    }

    private static double sine(double degrees) {
        double turned = ((degrees % 360D) + 360D) % 360D;
        if (turned == 0D || turned == 180D) {
            return 0D;
        }
        if (turned == 90D) {
            return 1D;
        }
        if (turned == 270D) {
            return -1D;
        }
        return Math.sin(Math.toRadians(turned));
    }
}
