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

package art.arcane.iris.generation.decoration.fungus;

import art.arcane.iris.generation.decoration.tree.TreeFunctions;
import art.arcane.volmlib.util.math.Vector3i;
import art.arcane.volmlib.util.math.RNG;

import java.util.Map;

public final class FungusShelfBuilder {
    private FungusShelfBuilder() {
    }

    public static void build(Map<Vector3i, FungusCellRole> roles, IrisFungus fungus, RNG rng, long seed) {
        int radius = Math.max(1, fungus.getShelfRadius());
        double azimuth = rng.d(0.0, Math.PI * 2.0);
        double fanX = Math.sin(azimuth);
        double fanZ = Math.cos(azimuth);
        int stubHeight = Math.max(0, Math.min(2, fungus.getStemWidth()));

        for (int y = 0; y < stubHeight; y++) {
            roles.put(new Vector3i(0, y, 0), FungusCellRole.STEM);
        }

        int fanY = stubHeight;
        boolean hasGill = FungusCapBuilder.blockOrPaletteSet(fungus.getGillBlock(), fungus.getGillPalette());
        boolean hasSpot = FungusCapBuilder.blockOrPaletteSet(fungus.getSpotBlock(), fungus.getSpotPalette());

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > radius + 0.5) {
                    continue;
                }
                double forward = dx * fanX + dz * fanZ;
                if (forward < -0.5) {
                    continue;
                }
                double normalized = dist / Math.max(1.0, radius);
                int lift = (int) Math.round(radius * 0.25 * (1.0 - normalized));
                int y = fanY + lift;

                FungusCellRole role = FungusCellRole.CAP;
                if (hasSpot && spotNoise(dx, y, dz, seed + 9311L) <= fungus.getSpotChance()) {
                    role = FungusCellRole.SPOT;
                }
                roles.putIfAbsent(new Vector3i(dx, y, dz), role);

                if (hasGill && rollNoise(dx, y - 1, dz, seed + 4111L) <= fungus.getGillChance()) {
                    roles.putIfAbsent(new Vector3i(dx, y - 1, dz), FungusCellRole.GILL);
                }
            }
        }
    }

    private static double rollNoise(int x, int y, int z, long seed) {
        return TreeFunctions.valueNoise3D(x, y, z, seed);
    }

    private static double spotNoise(int x, int y, int z, long seed) {
        return TreeFunctions.valueNoise3D(Math.floorDiv(x, 2), y, Math.floorDiv(z, 2), seed);
    }
}
