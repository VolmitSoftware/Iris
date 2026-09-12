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

package art.arcane.iris.generation.decoration.ruin;

import art.arcane.volmlib.util.math.RNG;

final class RuinShapes {
    private RuinShapes() {
    }

    static void pillar(RuinBlockCanvas canvas, int width, int height, RNG rng) {
        int half = (width - 1) / 2;
        int broken = Math.max(2, height - rng.i(0, Math.max(1, height / 3)));
        for (int y = 0; y < broken; y++) {
            boolean bottom = y == 0;
            int jitter = y >= broken - 1 ? rng.i(0, 2) : 0;
            for (int x = -half; x <= half; x++) {
                for (int z = -half; z <= half; z++) {
                    if (y == broken - 1 && jitter > 0 && (Math.abs(x) == half || Math.abs(z) == half) && rng.chance(0.5)) {
                        continue;
                    }
                    canvas.set(x, y, z, RuinBlockCanvas.Role.STRUCTURE, bottom);
                }
            }
        }

        int stub = rng.i(0, 3);
        if (stub > 0) {
            int dir = rng.i(0, 4);
            int dx = dir == 0 ? 1 : (dir == 1 ? -1 : 0);
            int dz = dir == 2 ? 1 : (dir == 3 ? -1 : 0);
            for (int s = 1; s <= stub; s++) {
                canvas.set((half + s) * dx, 0, (half + s) * dz, RuinBlockCanvas.Role.STRUCTURE, true);
            }
        }
    }

    static void wall(RuinBlockCanvas canvas, int thickness, int length, int height, RNG rng) {
        int half = (length - 1) / 2;
        int tHalf = (thickness - 1) / 2;
        long edgeSeed = rng.lmax();
        for (int z = -half; z <= half; z++) {
            int top = height - jaggedDrop(z, height, edgeSeed);
            for (int y = 0; y < top; y++) {
                if (y > 0 && rng.chance(0.10)) {
                    continue;
                }
                for (int x = -tHalf; x <= thickness - 1 - tHalf; x++) {
                    canvas.set(x, y, z, RuinBlockCanvas.Role.STRUCTURE, y == 0);
                }
            }
        }
    }

    static void arch(RuinBlockCanvas canvas, int thickness, int span, int height, RNG rng) {
        int legSpacing = Math.max(2, span);
        int legGap = (legSpacing - 1) / 2;
        int tHalf = (thickness - 1) / 2;
        int legTop = Math.max(2, height - Math.max(2, (legSpacing / 2)));

        for (int side = -1; side <= 1; side += 2) {
            int legZ = side * legGap;
            for (int y = 0; y < legTop; y++) {
                for (int x = -tHalf; x <= thickness - 1 - tHalf; x++) {
                    canvas.set(x, y, legZ, RuinBlockCanvas.Role.STRUCTURE, y <= 1);
                }
            }
        }

        double radius = legGap;
        int crown = legTop + (int) Math.round(radius);
        for (int z = -legGap; z <= legGap; z++) {
            double inside = radius * radius - (z * z);
            if (inside < 0) {
                continue;
            }
            int y = legTop + (int) Math.round(Math.sqrt(inside));
            for (int yy = legTop; yy <= y && yy <= crown; yy++) {
                boolean keystone = Math.abs(z) <= 1;
                for (int x = -tHalf; x <= thickness - 1 - tHalf; x++) {
                    canvas.set(x, yy, z, RuinBlockCanvas.Role.STRUCTURE, keystone);
                }
            }
        }
    }

    static void floorSlab(RuinBlockCanvas canvas, int width, int length, int thickness) {
        int xHalf = (width - 1) / 2;
        int zHalf = (length - 1) / 2;
        for (int y = 0; y < thickness; y++) {
            for (int x = -xHalf; x <= width - 1 - xHalf; x++) {
                for (int z = -zHalf; z <= length - 1 - zHalf; z++) {
                    canvas.set(x, y, z, RuinBlockCanvas.Role.STRUCTURE, y == 0);
                }
            }
        }
    }

    static void rubble(RuinBlockCanvas canvas, int width, int length, int peak, RNG rng) {
        int xHalf = (width - 1) / 2;
        int zHalf = (length - 1) / 2;
        long blobSeed = rng.lmax();
        double rx = Math.max(1.0, width / 2.0);
        double rz = Math.max(1.0, length / 2.0);

        for (int x = -xHalf; x <= width - 1 - xHalf; x++) {
            for (int z = -zHalf; z <= length - 1 - zHalf; z++) {
                double radial = (x * x) / (rx * rx) + (z * z) / (rz * rz);
                if (radial > 1.2) {
                    continue;
                }
                double falloff = Math.max(0.0, 1.0 - radial);
                double noise = art.arcane.iris.generation.decoration.tree.TreeFunctions.valueNoise3D(x, 0, z, blobSeed);
                int columnHeight = (int) Math.round(peak * falloff * (0.6 + 0.4 * noise));
                for (int y = 0; y <= columnHeight; y++) {
                    if (y > 0 && rng.chance(0.25)) {
                        continue;
                    }
                    canvas.set(x, y, z, RuinBlockCanvas.Role.STRUCTURE, y == 0 && radial < 0.5);
                }
            }
        }
    }

    private static int jaggedDrop(int z, int height, long seed) {
        double n = art.arcane.iris.generation.decoration.tree.TreeFunctions.valueNoise1D(z * 0.9, seed);
        return (int) Math.round(n * Math.max(1, height - 1) * 0.6);
    }
}
