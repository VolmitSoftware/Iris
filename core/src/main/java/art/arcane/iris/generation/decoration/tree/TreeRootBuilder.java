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
        List<TreeBlockCanvas.Vec> cells = new ArrayList<>();
        for (TreeBlockCanvas.Vec v : canvas.getTrunk()) {
            if (v.y() == 0) {
                cells.add(v);
            }
        }
        if (cells.isEmpty()) {
            return;
        }
        cells.sort(TreeBlockCanvas.Vec::compareTo);

        int depth = tree.getRootDepth() > 0 ? tree.getRootDepth() : autoDepth(height);
        RNG rng = new RNG(baseSeed ^ 0x5009L);

        double cx = 0;
        double cz = 0;
        for (TreeBlockCanvas.Vec cell : cells) {
            cx += cell.x();
            cz += cell.z();
        }
        cx /= cells.size();
        cz /= cells.size();

        double baseRadius = 1.0;
        for (TreeBlockCanvas.Vec cell : cells) {
            baseRadius = Math.max(baseRadius, Math.hypot(cell.x() - cx, cell.z() - cz) + 0.5);
        }
        double flare = tree.getRootFlare() > 0 ? tree.getRootFlare() : baseRadius + Math.max(0.75, baseRadius * 0.35);
        RootShape shape = new RootShape(cells, cx, cz, baseRadius, depth, flare);

        IrisTreeRootStyle style = tree.getRootStyle();
        switch (style) {
            case TAPROOT -> taproot(canvas, shape);
            case BUTTRESS -> {
                taproot(canvas, shape);
                buttress(canvas, shape, rng);
            }
            case STILT -> {
                stilts(canvas, shape, height, rng);
                taproot(canvas, new RootShape(cells, cx, cz, baseRadius, Math.min(depth, 3), flare));
            }
        }
    }

    private static void taproot(TreeBlockCanvas canvas, RootShape shape) {
        TreeBlockCanvas.Vec center = nearestCell(shape.cells(), shape.centerX(), shape.centerZ());
        for (int k = 1; k <= shape.depth(); k++) {
            double fraction = k / (double) shape.depth();
            double keepRadius = shape.radius() * (1.0 - fraction);
            for (TreeBlockCanvas.Vec cell : shape.cells()) {
                if (Math.hypot(cell.x() - shape.centerX(), cell.z() - shape.centerZ()) <= keepRadius) {
                    placeRoot(canvas, cell.x(), -k, cell.z(), TreeBlockCanvas.Axis.Y);
                }
            }
            placeRoot(canvas, center.x(), -k, center.z(), TreeBlockCanvas.Axis.Y);
        }
    }

    private static void buttress(TreeBlockCanvas canvas, RootShape shape, RNG rng) {
        int legs = Math.max(3, Math.min(8, shape.cells().size() + 2));
        for (int leg = 0; leg < legs; leg++) {
            double angle = 2.0 * Math.PI * leg / legs + rng.d(-0.25, 0.25);
            double reach = shape.flare() * rng.d(0.8, 1.0);
            int x = (int) Math.round(shape.centerX() + reach * Math.cos(angle));
            int z = (int) Math.round(shape.centerZ() + reach * Math.sin(angle));
            int depth = legDepth(shape.depth(), leg, legs);
            TreeBlockCanvas.Vec start = nearestCell(shape.cells(), x, z);
            rasterizeRoot(canvas, start, new TreeBlockCanvas.Vec(x, -depth, z));
        }
    }

    private static void stilts(TreeBlockCanvas canvas, RootShape shape, int height, RNG rng) {
        int stiltTop = Math.min(height - 1, Math.max(2, Math.min(7, (int) Math.round(0.25 * height))));
        List<TreeBlockCanvas.Vec> attachments = new ArrayList<>();
        for (TreeBlockCanvas.Vec cell : canvas.getTrunk()) {
            if (cell.y() == stiltTop) {
                attachments.add(cell);
            }
        }
        if (attachments.isEmpty()) {
            attachments.addAll(shape.cells());
        }
        attachments.sort(TreeBlockCanvas.Vec::compareTo);
        int props = Math.max(3, Math.min(8, shape.cells().size() + 2));
        for (int prop = 0; prop < props; prop++) {
            double angle = 2.0 * Math.PI * prop / props + rng.d(-0.25, 0.25);
            double reach = shape.flare() * rng.d(0.8, 1.0);
            int x = (int) Math.round(shape.centerX() + reach * Math.cos(angle));
            int z = (int) Math.round(shape.centerZ() + reach * Math.sin(angle));
            int depth = legDepth(Math.min(shape.depth(), 3), prop, props);
            TreeBlockCanvas.Vec start = nearestCell(attachments, x, z);
            rasterizeRoot(canvas, start, new TreeBlockCanvas.Vec(x, -depth, z));
        }
    }

    private static int legDepth(int depth, int leg, int count) {
        return Math.max(1, (int) Math.round(depth * (0.55 + 0.45 * leg / Math.max(1, count - 1))));
    }

    private static TreeBlockCanvas.Vec nearestCell(List<TreeBlockCanvas.Vec> cells, double x, double z) {
        TreeBlockCanvas.Vec nearest = cells.getFirst();
        double distance = Double.POSITIVE_INFINITY;
        for (TreeBlockCanvas.Vec cell : cells) {
            double dx = cell.x() - x;
            double dz = cell.z() - z;
            double candidateDistance = dx * dx + dz * dz;
            if (candidateDistance < distance) {
                nearest = cell;
                distance = candidateDistance;
            }
        }
        return nearest;
    }

    private static void rasterizeRoot(TreeBlockCanvas canvas, TreeBlockCanvas.Vec start, TreeBlockCanvas.Vec end) {
        int dx = end.x() - start.x();
        int dy = end.y() - start.y();
        int dz = end.z() - start.z();
        int steps = Math.max(Math.max(Math.abs(dx), Math.abs(dy)), Math.max(Math.abs(dz), 1));
        TreeBlockCanvas.Axis axis = TreeFunctions.logAxis(dx, dy, dz);
        int x = start.x();
        int y = start.y();
        int z = start.z();
        for (int i = 1; i <= steps; i++) {
            double t = i / (double) steps;
            int nextX = (int) Math.round(start.x() + dx * t);
            int nextY = (int) Math.round(start.y() + dy * t);
            int nextZ = (int) Math.round(start.z() + dz * t);
            while (y != nextY) {
                y += Integer.signum(nextY - y);
                placeRoot(canvas, x, y, z, axis);
            }
            while (x != nextX) {
                x += Integer.signum(nextX - x);
                placeRoot(canvas, x, y, z, axis);
            }
            while (z != nextZ) {
                z += Integer.signum(nextZ - z);
                placeRoot(canvas, x, y, z, axis);
            }
        }
    }

    private static void placeRoot(TreeBlockCanvas canvas, int x, int y, int z, TreeBlockCanvas.Axis axis) {
        TreeBlockCanvas.Cell cell = canvas.get(x, y, z);
        if (cell == null || cell.role() != TreeBlockCanvas.Role.TRUNK && cell.role() != TreeBlockCanvas.Role.SECONDARY_TRUNK) {
            canvas.setTrunk(x, y, z, TreeBlockCanvas.Role.TRUNK, axis);
        }
    }

    private static int autoDepth(int height) {
        return Math.max(2, Math.min(16, (int) Math.round(0.18 * height)));
    }

    private record RootShape(List<TreeBlockCanvas.Vec> cells, double centerX, double centerZ,
                             double radius, int depth, double flare) {
    }
}
