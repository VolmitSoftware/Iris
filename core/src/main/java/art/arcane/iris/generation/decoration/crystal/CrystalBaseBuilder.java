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

import art.arcane.iris.generation.decoration.tree.TreeFunctions;

public final class CrystalBaseBuilder {
    private CrystalBaseBuilder() {
    }

    public static void build(CrystalCanvas canvas, IrisCrystal crystal, long seed) {
        double radius = crystal.getBaseRadius();
        if (radius <= 0.0) {
            canvas.set(0, 0, 0, CrystalRole.BASE);
            return;
        }

        double noiseStrength = crystal.getBaseNoise();
        int reach = (int) Math.ceil(radius + 1.0);
        for (int x = -reach; x <= reach; x++) {
            for (int y = -reach; y <= reach; y++) {
                for (int z = -reach; z <= reach; z++) {
                    double distance = Math.sqrt((double) x * x + (double) y * y + (double) z * z);
                    double wobble = (TreeFunctions.valueNoise3D(x, y, z, seed) - 0.5) * 2.0 * noiseStrength * radius;
                    if (distance <= radius + wobble) {
                        canvas.set(x, y, z, CrystalRole.BASE);
                    }
                }
            }
        }
    }
}
