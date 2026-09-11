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

package art.arcane.iris.engine.object.tree;

import art.arcane.iris.engine.object.IrisProceduralBlocks;
import art.arcane.iris.engine.object.IrisProceduralTree;
import art.arcane.iris.engine.object.IrisTreeBranches;
import art.arcane.iris.engine.object.IrisTreeCanopy;
import art.arcane.iris.engine.object.IrisTreeLayer;
import art.arcane.iris.engine.object.IrisTreeLeafMode;
import art.arcane.iris.engine.object.IrisTreeProfile;
import art.arcane.iris.engine.object.IrisTreeSubBranches;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;

import java.util.List;

public final class TreeCanopyBuilder {
    private TreeCanopyBuilder() {
    }

    public static void build(TreeBlockCanvas canvas, IrisProceduralTree tree, int height, double[][] offsets,
                             int branchStartY, long baseSeed, List<int[]> branchEndpoints) {
        IrisTreeCanopy canopy = tree.getCanopy();
        if (canopy == null) {
            canopy = new IrisTreeCanopy();
        }
        IrisTreeBranches branches = canopy.getBranches();

        double[][] layers = resolveLayers(canopy, tree.getProfile(), height, branches != null);

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

    private static void placeApexCap(TreeBlockCanvas canvas, IrisProceduralTree tree, int height, double[][] layers,
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
        IrisTreeCanopy canopy = tree.getCanopy();
        IrisTreeLeafMode mode = canopy != null ? canopy.getMode() : IrisTreeLeafMode.TRIMMED;
        double density = canopy != null ? canopy.getLeafDensity() : 1.0;
        placeLeafCluster(canvas, tree, ocx, height - 1, ocz, capRadius, mode, density, baseSeed + 5555L);
    }

    private static double[][] resolveLayers(IrisTreeCanopy canopy, IrisTreeProfile profile, int height, boolean branchDriven) {
        KList<IrisTreeLayer> explicit = canopy.getLayers();
        if (explicit != null && !explicit.isEmpty()) {
            double[][] out = new double[explicit.size()][2];
            for (int i = 0; i < explicit.size(); i++) {
                out[i][0] = explicit.get(i).getYOffset();
                out[i][1] = explicit.get(i).getRadius();
            }
            return out;
        }
        return TreeProfiles.presetLayers(profile, height, branchDriven);
    }

    private static void volumeCanopy(TreeBlockCanvas canvas, IrisProceduralTree tree, double[][] layers,
                                     IrisTreeCanopy canopy, long baseSeed, double[][] offsets) {
        int ocx = 0;
        int ocz = 0;
        if (offsets != null && offsets.length > 0) {
            ocx = (int) Math.round(offsets[offsets.length - 1][0]);
            ocz = (int) Math.round(offsets[offsets.length - 1][1]);
        }

        double startAngle = canopy.getStartAngle();
        double squish = canopy.getSquish();
        IrisTreeLeafMode mode = canopy.getMode();
        double leafDensity = canopy.getLeafDensity();

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

    private static void placeLeafDisc(TreeBlockCanvas canvas, IrisProceduralTree tree, int cx, int cy, int cz,
                                      double radius, IrisTreeLeafMode mode, double density, long seed) {
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

    private static void placeLeafCluster(TreeBlockCanvas canvas, IrisProceduralTree tree, int cx, int cy, int cz,
                                         int radius, IrisTreeLeafMode mode, double density, long seed) {
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

    private static double stretchX(IrisProceduralTree tree) {
        IrisTreeCanopy c = tree.getCanopy();
        return c == null ? 1.0 : Math.max(0.1, c.getCrownStretchX());
    }

    private static double stretchZ(IrisProceduralTree tree) {
        IrisTreeCanopy c = tree.getCanopy();
        return c == null ? 1.0 : Math.max(0.1, c.getCrownStretchZ());
    }

    private static boolean passesLeafTest(IrisTreeLeafMode mode, double dist, double radius, double density,
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

    private static TreeBlockCanvas.Role resolveLeaf(IrisProceduralTree tree, RNG rng) {
        boolean hasSecondary = IrisProceduralBlocks.paletteSet(tree.getSecondaryLeavesPalette())
                || (tree.getWeightedSecondaryLeaves() != null && !tree.getWeightedSecondaryLeaves().isEmpty())
                || (tree.getSecondaryLeaves() != null && !tree.getSecondaryLeaves().isEmpty());
        if (!hasSecondary || tree.getSecondaryLeafFraction() <= 0.0) {
            return TreeBlockCanvas.Role.LEAF;
        }
        if (rng.nextDouble() >= tree.getSecondaryLeafFraction()) {
            return TreeBlockCanvas.Role.LEAF;
        }
        return TreeBlockCanvas.Role.SECONDARY_LEAF;
    }

    private static void branchCanopy(TreeBlockCanvas canvas, IrisProceduralTree tree, int height,
                                     IrisTreeBranches branches, int branchStartY, long baseSeed, double[][] offsets,
                                     List<int[]> branchEndpoints) {
        long branchSeed = baseSeed + 9999;
        RNG branchRng = new RNG(branchSeed);
        IrisTreeSubBranches sub = branches.getSubBranches();
        int depth = branches.getBranchDepth();
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

            double az = TreeFunctions.azimuthDegrees(branches.getAzimuthMode(), t, branchIndex, tree, branches.getAzimuth(), branchRng);
            branchIndex++;
            double branchLen = TreeFunctions.branchLength(branches, t);
            double effElevation = branches.isLeafStartUp() ? Math.max(0.0, branches.getElevation()) : branches.getElevation();
            int[] end = branchEndpoint(ox, y, oz, az, effElevation, branchLen);
            int[] tip = rasterizeBranch(canvas, ox, y, oz, end[0], end[1], end[2], branches.getSag());

            if (branchEndpoints != null) {
                branchEndpoints.add(new int[]{tip[0], tip[1], tip[2], ox, oz});
            }
            placeLeafCluster(canvas, tree, tip[0], tip[1], tip[2], branches.getClusterRadius(),
                    branches.getClusterMode(), branches.getClusterDensity(), branchSeed + y);

            growSubBranches(canvas, tree, tip[0], tip[1], tip[2], az, effElevation, branchLen, sub, depth, branchSeed + y + 1000L);
        }
    }

    private static void growSubBranches(TreeBlockCanvas canvas, IrisProceduralTree tree, int ox, int oy, int oz,
                                        double az, double el, double len, IrisTreeSubBranches sub, int depth, long seed) {
        if (sub == null || depth <= 0) {
            return;
        }
        int count = Math.max(1, sub.getCount());
        for (int si = 0; si < count; si++) {
            double yawOffset = sub.getYawDelta() * (si - (count - 1) / 2.0);
            double subAz = az + yawOffset;
            double subEl = el + sub.getPitchDelta();
            double subLen = len * sub.getLengthScale();
            int[] end = branchEndpoint(ox, oy, oz, subAz, subEl, subLen);
            int[] tip = rasterizeBranch(canvas, ox, oy, oz, end[0], end[1], end[2], sub.getSag());
            placeLeafCluster(canvas, tree, tip[0], tip[1], tip[2], sub.getClusterRadius(),
                    sub.getClusterMode(), sub.getClusterDensity(), seed + si * 131L);
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
