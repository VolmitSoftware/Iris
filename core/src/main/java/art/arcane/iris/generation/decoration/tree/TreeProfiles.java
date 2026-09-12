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


public final class TreeProfiles {
    private TreeProfiles() {
    }

    static double radiusScale(IrisTreeProfile profile) {
        return switch (profile) {
            case OAK, DARK_OAK, CHERRY -> 1.0;
            case BIRCH -> 0.7;
            case SPRUCE -> 0.55;
            case JUNGLE -> 0.45;
            case ACACIA -> 0.6;
            case DARK_OAK_FLAT -> 1.25;
            case DARK_OAK_FLAT_WIDE -> 1.8;
            case PALM -> 0.5;
            case WILLOW -> 1.1;
            case COLUMNAR -> 0.35;
            case BUSH -> 1.3;
            case MEGA_SPRUCE -> 0.7;
        };
    }

    static double[][] fractions(IrisTreeProfile profile) {
        return switch (profile) {
            case OAK -> new double[][]{{0.55, 0.6}, {0.65, 0.9}, {0.75, 1.0}, {0.85, 0.9}, {0.92, 0.65}, {0.98, 0.35}};
            case BIRCH -> new double[][]{{0.6, 0.45}, {0.7, 0.75}, {0.8, 0.85}, {0.88, 0.75}, {0.94, 0.45}, {0.99, 0.2}};
            case SPRUCE, MEGA_SPRUCE -> new double[][]{{0.3, 1.0}, {0.42, 0.85}, {0.54, 0.7}, {0.65, 0.55}, {0.75, 0.4}, {0.84, 0.25}, {0.91, 0.15}, {0.97, 0.05}};
            case JUNGLE -> new double[][]{{0.82, 0.4}, {0.88, 0.8}, {0.93, 1.0}, {0.97, 0.7}, {1.0, 0.3}};
            case ACACIA -> new double[][]{{0.9, 0.5}, {0.95, 0.8}, {0.99, 0.4}};
            case DARK_OAK -> new double[][]{{0.5, 0.7}, {0.62, 1.0}, {0.72, 1.0}, {0.82, 0.9}, {0.91, 0.6}, {0.97, 0.3}};
            case DARK_OAK_FLAT -> new double[][]{{0.74, 0.62}, {0.83, 1.0}, {0.92, 1.0}, {1.0, 0.6}};
            case DARK_OAK_FLAT_WIDE -> new double[][]{{0.74, 0.62}, {0.83, 1.0}, {0.92, 1.0}, {1.0, 0.62}};
            case CHERRY -> new double[][]{{0.5, 0.5}, {0.62, 0.9}, {0.74, 1.0}, {0.84, 1.0}, {0.92, 0.7}, {0.98, 0.4}};
            case PALM -> new double[][]{{0.92, 0.45}, {0.97, 0.9}, {1.0, 1.0}, {1.03, 0.6}};
            case WILLOW -> new double[][]{{0.55, 0.7}, {0.68, 1.0}, {0.8, 1.0}, {0.9, 0.85}, {0.97, 0.6}};
            case COLUMNAR -> new double[][]{{0.4, 0.6}, {0.55, 0.9}, {0.7, 1.0}, {0.82, 0.9}, {0.92, 0.7}, {0.98, 0.4}};
            case BUSH -> new double[][]{{0.25, 0.7}, {0.45, 1.0}, {0.65, 1.0}, {0.82, 0.8}, {0.95, 0.5}};
        };
    }

    static double[][] presetLayers(IrisTreeProfile profile, int height, boolean branchDriven) {
        double scale = radiusScale(profile);
        int baseRadius = Math.max(3, (int) Math.round(height / 2.0 * scale));
        double[][] fractions = fractions(profile);

        int crownExtra = branchDriven ? 0 : Math.max(2, height / 4);
        int yTop = (height - 1) + crownExtra;

        double[][] layers = new double[fractions.length][2];
        for (int i = 0; i < fractions.length; i++) {
            int yOff = (int) Math.round(fractions[i][0] * yTop);
            double radius = Math.max(1.5, baseRadius * fractions[i][1]);
            layers[i][0] = yOff;
            layers[i][1] = radius;
        }
        return layers;
    }
}
