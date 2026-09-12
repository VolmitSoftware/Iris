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

import art.arcane.iris.generation.terrain.IrisMaterialPalette;
import art.arcane.iris.generation.decoration.IrisProceduralBlocks;
import art.arcane.iris.generation.decoration.tree.TreeFunctions;
import art.arcane.volmlib.util.math.Vector3i;

import java.util.Map;

public final class FungusCapBuilder {
    private FungusCapBuilder() {
    }

    public static void build(Map<Vector3i, FungusCellRole> roles, IrisFungus fungus, int baseRadius, double cx, int baseY, double cz, long seed) {
        int radius = (int) Math.round(baseRadius + Math.max(0.0, fungus.getCapOverhang()));
        int thickness = Math.max(1, Math.min(3, fungus.getCapThickness()));
        double squish = Math.max(0.0, Math.min(1.0, fungus.getCapSquish()));
        double droop = Math.max(0.0, fungus.getCapDroop());
        IrisFungusCapShape shape = fungus.getCapShape() == null ? IrisFungusCapShape.DOME : fungus.getCapShape();
        int icx = (int) Math.round(cx);
        int icz = (int) Math.round(cz);

        boolean hasGill = blockOrPaletteSet(fungus.getGillBlock(), fungus.getGillPalette());
        boolean hasSpot = blockOrPaletteSet(fungus.getSpotBlock(), fungus.getSpotPalette());

        double apexRise = apexRise(shape, radius) * (1.0 - 0.6 * squish);
        double droopReach = Math.tan(Math.toRadians(droop)) * radius;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > radius + 0.5) {
                    continue;
                }
                double normalized = dist / Math.max(1.0, radius);
                double surfaceTop = baseY + capSurface(shape, normalized, apexRise);
                surfaceTop -= droopReach * Math.pow(normalized, 3);

                int topYi = (int) Math.round(surfaceTop);
                for (int layer = 0; layer < thickness; layer++) {
                    int y = topYi - layer;
                    if (y < baseY - thickness) {
                        continue;
                    }
                    Vector3i pos = new Vector3i(icx + dx, y, icz + dz);
                    FungusCellRole role = FungusCellRole.CAP;

                    boolean underside = layer == thickness - 1;
                    boolean topFace = layer == 0;

                    if (underside && hasGill && rollNoise(icx + dx, y, icz + dz, seed + 4111L) <= fungus.getGillChance()) {
                        role = FungusCellRole.GILL;
                    } else if (topFace && hasSpot && spotNoise(icx + dx, y, icz + dz, seed + 9311L) <= fungus.getSpotChance()) {
                        role = FungusCellRole.SPOT;
                    }

                    roles.putIfAbsent(pos, role);
                }
            }
        }
    }

    private static double apexRise(IrisFungusCapShape shape, int radius) {
        return switch (shape) {
            case DOME -> radius * 0.85;
            case CONICAL -> radius * 1.4;
            case FUNNEL -> radius * 0.5;
            case FLAT -> radius * 0.35;
            case FLAT_WIDE -> radius * 0.2;
        };
    }

    private static double capSurface(IrisFungusCapShape shape, double normalized, double apexRise) {
        double n = Math.max(0.0, Math.min(1.0, normalized));
        return switch (shape) {
            case DOME -> apexRise * Math.sqrt(Math.max(0.0, 1.0 - n * n));
            case CONICAL -> apexRise * (1.0 - n);
            case FUNNEL -> apexRise * (n * n);
            case FLAT -> apexRise * Math.max(0.0, 1.0 - Math.pow(n, 4));
            case FLAT_WIDE -> apexRise * Math.max(0.0, 1.0 - Math.pow(n, 6));
        };
    }

    private static double rollNoise(int x, int y, int z, long seed) {
        return TreeFunctions.valueNoise3D(x, y, z, seed);
    }

    private static double spotNoise(int x, int y, int z, long seed) {
        return TreeFunctions.valueNoise3D(Math.floorDiv(x, 2), y, Math.floorDiv(z, 2), seed);
    }

    static boolean blockOrPaletteSet(String block, IrisMaterialPalette palette) {
        if (IrisProceduralBlocks.paletteSet(palette)) {
            return true;
        }
        return block != null && !block.isEmpty();
    }
}
