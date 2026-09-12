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

import java.util.Map;

public final class TreeSupport {
    private static final int LEGAL_DISTANCE = 6;
    private static final int REACH_GAP = 4;

    private TreeSupport() {
    }

    public static void ensureLeavesSupported(TreeBlockCanvas canvas, int maxTendrils) {
        for (int iter = 0; iter < maxTendrils; iter++) {
            Map<TreeBlockCanvas.Vec, Integer> dist = TreePlausibility.computeDistances(canvas.getTrunk(), canvas.getLeaf());

            TreeBlockCanvas.Vec worstLeaf = null;
            TreeBlockCanvas.Vec worstWood = null;
            int worstGap = -1;

            for (TreeBlockCanvas.Vec leaf : canvas.getLeaf()) {
                Integer d = dist.get(leaf);
                if (d != null && d <= LEGAL_DISTANCE) {
                    continue;
                }
                TreeBlockCanvas.Vec nearest = null;
                int best = Integer.MAX_VALUE;
                for (TreeBlockCanvas.Vec wood : canvas.getTrunk()) {
                    int cd = chebyshev(leaf, wood);
                    if (cd < best) {
                        best = cd;
                        nearest = wood;
                        if (cd <= 1) {
                            break;
                        }
                    }
                }
                if (nearest != null && best > worstGap) {
                    worstGap = best;
                    worstLeaf = leaf;
                    worstWood = nearest;
                }
            }

            if (worstLeaf == null) {
                return;
            }
            growTendril(canvas, worstWood, worstLeaf);
        }
    }

    private static void growTendril(TreeBlockCanvas canvas, TreeBlockCanvas.Vec from, TreeBlockCanvas.Vec to) {
        int dx = to.x() - from.x();
        int dy = to.y() - from.y();
        int dz = to.z() - from.z();
        int length = Math.max(Math.max(Math.abs(dx), Math.abs(dy)), Math.max(Math.abs(dz), 1));
        int steps = Math.max(1, length - REACH_GAP);
        TreeBlockCanvas.Axis axis = TreeFunctions.logAxis(dx, dy, dz);

        for (int i = 1; i <= steps; i++) {
            double t = i / (double) length;
            int x = from.x() + (int) Math.round(dx * t);
            int y = from.y() + (int) Math.round(dy * t);
            int z = from.z() + (int) Math.round(dz * t);
            canvas.setTrunk(x, y, z, TreeBlockCanvas.Role.TRUNK, axis);
        }
    }

    private static int chebyshev(TreeBlockCanvas.Vec a, TreeBlockCanvas.Vec b) {
        return Math.max(Math.max(Math.abs(a.x() - b.x()), Math.abs(a.y() - b.y())), Math.abs(a.z() - b.z()));
    }
}
