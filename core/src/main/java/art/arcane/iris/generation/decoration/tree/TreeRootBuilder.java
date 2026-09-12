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

import art.arcane.volmlib.util.math.RNG;

import java.util.ArrayList;
import java.util.List;

public final class TreeRootBuilder {
    private TreeRootBuilder() {
    }

    public static void build(TreeBlockCanvas canvas, IrisProceduralTree tree, int height, long baseSeed) {
        List<int[]> cells = new ArrayList<>();
        for (TreeBlockCanvas.Vec v : canvas.getTrunk()) {
            if (v.y() == 0) {
                cells.add(new int[]{v.x(), v.z()});
            }
        }
        if (cells.isEmpty()) {
            return;
        }

        int depth = tree.getRootDepth() > 0 ? tree.getRootDepth() : autoDepth(height);
        RNG rng = new RNG(baseSeed ^ 0x5009L);

        double cx = 0;
        double cz = 0;
        for (int[] c : cells) {
            cx += c[0];
            cz += c[1];
        }
        cx /= cells.size();
        cz /= cells.size();

        double baseRadius = 1.0;
        for (int[] c : cells) {
            baseRadius = Math.max(baseRadius, Math.hypot(c[0] - cx, c[1] - cz) + 0.5);
        }
        double flare = tree.getRootFlare() > 0 ? tree.getRootFlare() : baseRadius + Math.max(2.0, depth * 0.6);

        IrisTreeRootStyle style = tree.getRootStyle();
        switch (style) {
            case TAPROOT -> taproot(canvas, cells, cx, cz, baseRadius, depth);
            case BUTTRESS -> {
                taproot(canvas, cells, cx, cz, baseRadius, depth);
                buttress(canvas, cells, cx, cz, baseRadius, depth, flare, rng);
            }
            case STILT -> {
                taproot(canvas, cells, cx, cz, baseRadius, Math.min(depth, 3));
                stilts(canvas, cx, cz, baseRadius, flare, height, cells.size(), rng);
            }
        }
    }

    private static void taproot(TreeBlockCanvas canvas, List<int[]> cells, double cx, double cz, double baseRadius, int depth) {
        int icx = (int) Math.round(cx);
        int icz = (int) Math.round(cz);
        for (int k = 1; k <= depth; k++) {
            double frac = k / (double) depth;
            double keepR = baseRadius * (1.0 - 0.6 * frac);
            for (int[] c : cells) {
                if (Math.hypot(c[0] - cx, c[1] - cz) <= keepR + 1e-6) {
                    placeRoot(canvas, c[0], -k, c[1], TreeBlockCanvas.Axis.Y);
                }
            }
            placeRoot(canvas, icx, -k, icz, TreeBlockCanvas.Axis.Y);
        }
    }

    private static void buttress(TreeBlockCanvas canvas, List<int[]> cells, double cx, double cz, double baseRadius,
                                 int depth, double flare, RNG rng) {
        int nLegs = Math.max(4, Math.min(12, cells.size() + 2));
        for (int li = 0; li < nLegs; li++) {
            double ang = 2.0 * Math.PI * li / nLegs + rng.d(-0.25, 0.25);
            double ca = Math.cos(ang);
            double sa = Math.sin(ang);
            int prevX = (int) Math.round(cx + baseRadius * ca);
            int prevZ = (int) Math.round(cz + baseRadius * sa);
            for (int k = 1; k <= depth; k++) {
                double frac = k / (double) depth;
                double r = baseRadius + (flare - baseRadius) * frac;
                int x = (int) Math.round(cx + r * ca);
                int z = (int) Math.round(cz + r * sa);
                placeRoot(canvas, x, -k, z, TreeBlockCanvas.Axis.Y);
                if (x != prevX || z != prevZ) {
                    placeRoot(canvas, prevX, -k, prevZ, TreeBlockCanvas.Axis.Y);
                }
                prevX = x;
                prevZ = z;
            }
        }
    }

    private static void stilts(TreeBlockCanvas canvas, double cx, double cz, double baseRadius, double flare,
                               int height, int cellCount, RNG rng) {
        int stiltTop = Math.max(2, Math.min(7, (int) Math.round(0.25 * height)));
        int nProps = Math.max(4, Math.min(10, cellCount + 2));
        for (int p = 0; p < nProps; p++) {
            double ang = 2.0 * Math.PI * p / nProps + rng.d(-0.25, 0.25);
            double ca = Math.cos(ang);
            double sa = Math.sin(ang);
            int sx = (int) Math.round(cx + baseRadius * ca);
            int sz = (int) Math.round(cz + baseRadius * sa);
            int fx = (int) Math.round(cx + flare * ca);
            int fz = (int) Math.round(cz + flare * sa);
            rasterizeRoot(canvas, sx, stiltTop, sz, fx, -2, fz);
        }
    }

    private static void rasterizeRoot(TreeBlockCanvas canvas, int ox, int oy, int oz, int ex, int ey, int ez) {
        int dx = ex - ox;
        int dy = ey - oy;
        int dz = ez - oz;
        int steps = Math.max(Math.max(Math.abs(dx), Math.abs(dy)), Math.max(Math.abs(dz), 1));
        TreeBlockCanvas.Axis axis = TreeFunctions.logAxis(dx, dy, dz);
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            int x = (int) Math.round(ox + dx * t);
            int y = (int) Math.round(oy + dy * t);
            int z = (int) Math.round(oz + dz * t);
            placeRoot(canvas, x, y, z, axis);
        }
    }

    private static void placeRoot(TreeBlockCanvas canvas, int x, int y, int z, TreeBlockCanvas.Axis axis) {
        if (!canvas.has(x, y, z)) {
            canvas.setTrunk(x, y, z, TreeBlockCanvas.Role.TRUNK, axis);
        }
    }

    private static int autoDepth(int height) {
        return Math.max(2, Math.min(16, (int) Math.round(0.18 * height)));
    }
}
