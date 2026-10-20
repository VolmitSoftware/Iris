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

package com.volmit.iris.engine.object.procedural.tree;

import com.volmit.iris.engine.object.procedural.IrisProceduralBlocks;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.math.RNG;

import java.util.List;

public final class TreeCanopyBuilder {
    private TreeCanopyBuilder() {
    }

    public static void build(TreeBlockCanvas canvas, Tree tree, int height, double[][] offsets,
                             int branchStartY, long baseSeed, List<int[]> branchEndpoints) {
        TreeCanopy canopy = tree.canopy();
        if (canopy == null) {
            canopy = new TreeCanopy();
        }
        TreeBranches branches = canopy.branches().orElse(null);

        double[][] layers = resolveLayers(canopy, tree.profile(), height, branches != null);

        if (branches != null) {
            if (layers.length > 0) {
                double[][] crown = new double[][]{layers[layers.length - 1]};
                volumeCanopy(canvas, tree, crown, canopy, baseSeed, offsets);
            }
            placeApexCap(canvas, tree, height, layers, offsets, baseSeed);
            branchCanopy(canvas, tree, height, branches, branchStartY, baseSeed, offsets, branchEndpoints);
        } else {
            volumeCanopy(canvas, tree, layers, canopy, baseSeed, offsets);
        }
    }

    private static void placeApexCap(TreeBlockCanvas canvas, Tree tree, int height, double[][] layers,
                                     double[][] offsets, long baseSeed) {
        int ocx = 0;
        int ocz = 0;
        if (offsets != null && offsets.length > 0) {
            ocx = (int) Math.round(offsets[offsets.length - 1][0]);
            ocz = (int) Math.round(offsets[offsets.length - 1][1]);
        }
        double maxR = 2;
        for (double[] layer : layers) {
            maxR = Math.max(maxR, layer[1]);
        }
        int capRadius = Math.max(2, Math.min(3, (int) Math.round(maxR * 0.35)));
        TreeCanopy canopy = tree.canopy();
        TreeLeafMode mode = canopy != null ? canopy.mode() : TreeLeafMode.TRIMMED;
        double density = canopy != null ? canopy.leafDensity() : 1.0;
        placeLeafCluster(canvas, tree, ocx, height - 1, ocz, capRadius, mode, density, baseSeed + 5555L);
    }

    private static double[][] resolveLayers(TreeCanopy canopy, TreeProfile profile, int height, boolean branchDriven) {
        KList<TreeLayer> explicit = new KList<>(canopy.layers());
        if (explicit != null && !explicit.isEmpty()) {
            double[][] out = new double[explicit.size()][2];
            for (int i = 0; i < explicit.size(); i++) {
                out[i][0] = explicit.get(i).yOffset();
                out[i][1] = explicit.get(i).radius();
            }
            return out;
        }
        return TreeProfiles.presetLayers(profile, height, branchDriven);
    }

    private static void volumeCanopy(TreeBlockCanvas canvas, Tree tree, double[][] layers,
                                     TreeCanopy canopy, long baseSeed, double[][] offsets) {
        int ocx = 0;
        int ocz = 0;
        if (offsets != null && offsets.length > 0) {
            ocx = (int) Math.round(offsets[offsets.length - 1][0]);
            ocz = (int) Math.round(offsets[offsets.length - 1][1]);
        }

        double startAngle = canopy.startAngle();
        double squish = canopy.squish();
        TreeLeafMode mode = canopy.mode();
        double leafDensity = canopy.leafDensity();

        for (double[] layer : layers) {
            int yCenter = (int) Math.round(layer[0]);
            double radius = layer[1];
            int halfH = layerHalfHeight(radius, startAngle, squish);
            int downH = startAngle < 90.0
                    ? (int) Math.ceil(radius * Math.cos(Math.toRadians(startAngle)) * squish)
                    : 0;
            int skirt = downH;

            for (int y = yCenter - skirt; y <= yCenter + halfH; y++) {
                int dy = y - yCenter;
                double layerR;
                if (halfH > 0 && dy >= 0) {
                    layerR = radius * Math.sqrt(Math.max(0.0, 1.0 - Math.pow(dy / (double) halfH, 2)));
                } else if (skirt > 0 && dy < 0) {
                    layerR = radius * Math.sqrt(Math.max(0.0, 1.0 - Math.pow(dy / (double) skirt, 2)));
                } else {
                    layerR = radius;
                }
                if (layerR < 0.5) {
                    continue;
                }
                placeLeafDisc(canvas, tree, ocx, y, ocz, layerR, mode, leafDensity, baseSeed + y);
            }
        }
    }

    private static int layerHalfHeight(double radius, double startAngle, double squish) {
        if (startAngle >= 180.0) {
            return 0;
        }
        return (int) Math.ceil(radius * squish);
    }

    private static void placeLeafDisc(TreeBlockCanvas canvas, Tree tree, int cx, int cy, int cz,
                                      double radius, TreeLeafMode mode, double density, long seed) {
        double sx = stretchX(tree);
        double sz = stretchZ(tree);
        RNG rng = new RNG(seed);
        int irx = (int) Math.ceil(radius * sx);
        int irz = (int) Math.ceil(radius * sz);
        for (int dx = -irx; dx <= irx; dx++) {
            for (int dz = -irz; dz <= irz; dz++) {
                double ddx = dx / sx;
                double ddz = dz / sz;
                double dist = Math.sqrt(ddx * ddx + ddz * ddz);
                boolean corner = Math.abs(dx) == irx && Math.abs(dz) == irz;
                if (!passesLeafTest(mode, dist, radius, density, rng, cx + dx, cy, cz + dz, seed, corner)) {
                    continue;
                }
                canvas.setLeaf(cx + dx, cy, cz + dz, resolveLeaf(tree, rng));
            }
        }
    }

    private static void placeLeafCluster(TreeBlockCanvas canvas, Tree tree, int cx, int cy, int cz,
                                         int radius, TreeLeafMode mode, double density, long seed) {
        double sx = stretchX(tree);
        double sz = stretchZ(tree);
        RNG rng = new RNG(seed);
        int irx = (int) Math.ceil(radius * sx);
        int irz = (int) Math.ceil(radius * sz);
        for (int dx = -irx; dx <= irx; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -irz; dz <= irz; dz++) {
                    double ddx = dx / sx;
                    double ddz = dz / sz;
                    double dist = Math.sqrt(ddx * ddx + dy * dy + ddz * ddz);
                    boolean corner = Math.abs(dx) == irx && Math.abs(dz) == irz;
                    if (!passesLeafTest(mode, dist, radius, density, rng, cx + dx, cy + dy, cz + dz, seed, corner)) {
                        continue;
                    }
                    canvas.setLeaf(cx + dx, cy + dy, cz + dz, resolveLeaf(tree, rng));
                }
            }
        }
    }

    private static double stretchX(Tree tree) {
        TreeCanopy c = tree.canopy();
        return c == null ? 1.0 : Math.max(0.1, c.crownStretchX());
    }

    private static double stretchZ(Tree tree) {
        TreeCanopy c = tree.canopy();
        return c == null ? 1.0 : Math.max(0.1, c.crownStretchZ());
    }

    private static boolean passesLeafTest(TreeLeafMode mode, double dist, double radius, double density,
                                          RNG rng, int wx, int wy, int wz, long seed, boolean corner) {
        switch (mode) {
            case TRIMMED -> {
                return !(dist > radius || corner);
            }
            case FILLED -> {
                return dist <= radius;
            }
            case DENSITY -> {
                if (dist > radius) {
                    return false;
                }
                return rng.nextDouble() <= density * (1.0 - (dist / (radius + 1.0)));
            }
            case NOISE -> {
                if (dist > radius) {
                    return false;
                }
                return TreeFunctions.valueNoise3D(wx, wy, wz, seed) <= density;
            }
            case HOLLOW -> {
                return dist <= radius && dist > radius - 1.0;
            }
            case GRADIENT -> {
                if (dist > radius) {
                    return false;
                }
                return rng.nextDouble() <= density * Math.pow(1.0 - (dist / (radius + 1.0)), 2);
            }
            case CLUMPED -> {
                if (dist > radius) {
                    return false;
                }
                return TreeFunctions.valueNoise3D(Math.floorDiv(wx, 2), Math.floorDiv(wy, 2), Math.floorDiv(wz, 2), seed) <= density;
            }
            case TATTERED -> {
                if (dist > radius || corner) {
                    return false;
                }
                return !(dist > radius - 1.5 && rng.nextDouble() < 0.6);
            }
            case SPARSE -> {
                if (dist > radius) {
                    return false;
                }
                return rng.nextDouble() <= density * 0.4;
            }
            default -> {
                return dist <= radius;
            }
        }
    }

    private static TreeBlockCanvas.Role resolveLeaf(Tree tree, RNG rng) {
        boolean hasSecondary = IrisProceduralBlocks.paletteSet(tree.secondaryLeavesPalette())
                || (tree.weightedSecondaryLeaves() != null && !tree.weightedSecondaryLeaves().isEmpty())
                || tree.secondaryLeaves().filter(leaves -> !leaves.isEmpty()).isPresent();
        if (!hasSecondary || tree.secondaryLeafFraction() <= 0.0) {
            return TreeBlockCanvas.Role.LEAF;
        }
        if (rng.nextDouble() >= tree.secondaryLeafFraction()) {
            return TreeBlockCanvas.Role.LEAF;
        }
        return TreeBlockCanvas.Role.SECONDARY_LEAF;
    }

    private static void branchCanopy(TreeBlockCanvas canvas, Tree tree, int height,
                                     TreeBranches branches, int branchStartY, long baseSeed, double[][] offsets,
                                     List<int[]> branchEndpoints) {
        long branchSeed = baseSeed + 9999;
        RNG branchRng = new RNG(branchSeed);
        TreeSubBranches sub = branches.subBranches().orElse(null);
        int depth = branches.branchDepth();
        int branchIndex = 0;

        for (int y = Math.max(0, branchStartY); y < height; y++) {
            double t = y / (double) Math.max(height - 1, 1);
            double p = TreeFunctions.branchProbability(branches, t, branchSeed);
            if (branchRng.nextDouble() > p) {
                continue;
            }

            int ox = 0;
            int oz = 0;
            if (offsets != null && y < offsets.length) {
                ox = (int) Math.round(offsets[y][0]);
                oz = (int) Math.round(offsets[y][1]);
            }

            double az = TreeFunctions.azimuthDegrees(branches.azimuthMode(), t, branchIndex, tree, branches.azimuth(), branchRng);
            branchIndex++;
            double branchLen = TreeFunctions.branchLength(branches, t);
            double effElevation = branches.leafStartUp() ? Math.max(0.0, branches.elevation()) : branches.elevation();
            int[] end = branchEndpoint(ox, y, oz, az, effElevation, branchLen);
            int[] tip = rasterizeBranch(canvas, ox, y, oz, end[0], end[1], end[2], branches.sag());

            if (branchEndpoints != null) {
                branchEndpoints.add(new int[]{tip[0], tip[1], tip[2], ox, oz});
            }
            placeLeafCluster(canvas, tree, tip[0], tip[1], tip[2], branches.clusterRadius(),
                    branches.clusterMode(), branches.clusterDensity(), branchSeed + y);

            growSubBranches(canvas, tree, tip[0], tip[1], tip[2], az, effElevation, branchLen, sub, depth, branchSeed + y + 1000L);
        }
    }

    private static void growSubBranches(TreeBlockCanvas canvas, Tree tree, int ox, int oy, int oz,
                                        double az, double el, double len, TreeSubBranches sub, int depth, long seed) {
        if (sub == null || depth <= 0) {
            return;
        }
        int count = Math.max(1, sub.count());
        for (int si = 0; si < count; si++) {
            double yawOffset = sub.yawDelta() * (si - (count - 1) / 2.0);
            double subAz = az + yawOffset;
            double subEl = el + sub.pitchDelta();
            double subLen = len * sub.lengthScale();
            int[] end = branchEndpoint(ox, oy, oz, subAz, subEl, subLen);
            int[] tip = rasterizeBranch(canvas, ox, oy, oz, end[0], end[1], end[2], sub.sag());
            placeLeafCluster(canvas, tree, tip[0], tip[1], tip[2], sub.clusterRadius(),
                    sub.clusterMode(), sub.clusterDensity(), seed + si * 131L);
            growSubBranches(canvas, tree, tip[0], tip[1], tip[2], subAz, subEl, subLen, sub, depth - 1, seed + si * 131L + 17L);
        }
    }

    private static int[] branchEndpoint(int ox, int oy, int oz, double azimuthDeg, double elevationDeg, double length) {
        double azRad = Math.toRadians(azimuthDeg);
        double elRad = Math.toRadians(elevationDeg);
        double dx = length * Math.cos(elRad) * Math.sin(azRad);
        double dy = length * Math.sin(elRad);
        double dz = length * Math.cos(elRad) * Math.cos(azRad);
        return new int[]{ox + (int) Math.round(dx), oy + (int) Math.round(dy), oz + (int) Math.round(dz)};
    }

    private static int[] rasterizeBranch(TreeBlockCanvas canvas, int ox, int oy, int oz, int ex, int ey, int ez, double sag) {
        int dx = ex - ox;
        int dy = ey - oy;
        int dz = ez - oz;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double sagBlocks = sag * horizontal;
        int steps = Math.max(Math.max(Math.abs(dx), Math.abs(dz)),
                Math.max((int) (Math.abs(dy) + Math.ceil(sagBlocks)), 1));
        TreeBlockCanvas.Axis axis = TreeFunctions.logAxis(dx, dy, dz);

        int[] tip = new int[]{ox, oy, oz};
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            int x = (int) Math.round(ox + dx * t);
            int y = (int) Math.round(oy + dy * t - sagBlocks * t * t);
            int z = (int) Math.round(oz + dz * t);
            if (!canvas.has(x, y, z)) {
                canvas.setTrunk(x, y, z, TreeBlockCanvas.Role.TRUNK, axis);
            }
            tip = new int[]{x, y, z};
        }
        return tip;
    }
}
