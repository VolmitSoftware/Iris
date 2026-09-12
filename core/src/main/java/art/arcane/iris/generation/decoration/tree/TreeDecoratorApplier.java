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

import art.arcane.iris.generation.decoration.IrisProceduralBlocks;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.math.RNG;

import java.util.List;
import java.util.Map;

public final class TreeDecoratorApplier {
    private TreeDecoratorApplier() {
    }

    public static void apply(TreeBlockCanvas canvas, IrisProceduralTree tree, long baseSeed, List<int[]> branchEndpoints) {
        KList<IrisTreeDecorator> decorators = tree.getDecorators();
        if (decorators == null || decorators.isEmpty()) {
            return;
        }
        RNG rng = new RNG(baseSeed + 77777L);

        for (int idx = 0; idx < decorators.size(); idx++) {
            IrisTreeDecorator dec = decorators.get(idx);
            boolean hasBlock = (dec.getBlock() != null && !dec.getBlock().isEmpty()) || IrisProceduralBlocks.paletteSet(dec.getPalette());
            if (!hasBlock) {
                continue;
            }
            switch (dec.getTarget()) {
                case BRANCH_TIP -> branchTip(canvas, dec, idx, branchEndpoints, rng);
                case TRUNK_SURFACE -> trunkSurface(canvas, dec, idx, rng);
                case CANOPY_TOP -> canopyTop(canvas, dec, idx, rng);
                case CANOPY_BOTTOM -> canopyBottom(canvas, dec, idx, rng);
                case TRUNK_BASE -> trunkBase(canvas, dec, idx, rng);
                case LEAF_SURFACE -> leafSurface(canvas, dec, idx, rng);
                case CANOPY_HANG -> canopyHang(canvas, dec, idx, rng);
                case BRANCH_SURFACE -> branchSurface(canvas, dec, idx, rng);
                case TRUNK_TOP -> trunkTop(canvas, dec, idx, rng);
                case GROUND_SCATTER -> groundScatter(canvas, dec, idx, rng);
            }
        }
    }

    private static void branchTip(TreeBlockCanvas canvas, IrisTreeDecorator dec, int idx, List<int[]> branchEndpoints, RNG rng) {
        if (branchEndpoints == null) {
            return;
        }
        for (int[] e : branchEndpoints) {
            if (rng.nextDouble() > dec.getChance()) {
                continue;
            }
            int dx = e[0] - e[3];
            int dz = e[2] - e[4];
            boolean alongX = Math.abs(dx) >= Math.abs(dz);
            int stepX = alongX ? (dx >= 0 ? 1 : -1) : 0;
            int stepZ = alongX ? 0 : (dz >= 0 ? 1 : -1);
            int x = e[0] + stepX;
            int z = e[2] + stepZ;
            TreeBlockCanvas.Cell cell = canvas.get(x, e[1], z);
            while (cell != null && cell.role() != TreeBlockCanvas.Role.DECORATOR) {
                x += stepX;
                z += stepZ;
                cell = canvas.get(x, e[1], z);
            }
            if (cell != null) {
                continue;
            }
            String facing = dec.isAxisAware() ? facingAway(e[3], e[4], e[0], e[2]) : null;
            canvas.setDecor(x, e[1], z, idx, facing);
        }
    }

    private static void trunkSurface(TreeBlockCanvas canvas, IrisTreeDecorator dec, int idx, RNG rng) {
        int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        String[] facings = {"west", "east", "north", "south"};
        for (TreeBlockCanvas.Vec v : new KList<>(canvas.getTrunk())) {
            for (int i = 0; i < sides.length; i++) {
                int nx = v.x() + sides[i][0];
                int nz = v.z() + sides[i][1];
                if (canvas.has(nx, v.y(), nz) || rng.nextDouble() > dec.getChance()) {
                    continue;
                }
                canvas.setDecor(nx, v.y(), nz, idx, dec.isAxisAware() ? facings[i] : null);
            }
        }
    }

    private static void leafSurface(TreeBlockCanvas canvas, IrisTreeDecorator dec, int idx, RNG rng) {
        int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        String[] facings = {"west", "east", "north", "south"};
        for (TreeBlockCanvas.Vec v : new KList<>(canvas.getLeaf())) {
            for (int i = 0; i < sides.length; i++) {
                int nx = v.x() + sides[i][0];
                int nz = v.z() + sides[i][1];
                if (canvas.has(nx, v.y(), nz) || rng.nextDouble() > dec.getChance()) {
                    continue;
                }
                canvas.setDecor(nx, v.y(), nz, idx, dec.isAxisAware() ? facings[i] : null);
            }
        }
    }

    private static void branchSurface(TreeBlockCanvas canvas, IrisTreeDecorator dec, int idx, RNG rng) {
        for (TreeBlockCanvas.Vec v : new KList<>(canvas.getTrunk())) {
            int ay = v.y() + 1;
            if (canvas.has(v.x(), ay, v.z()) || rng.nextDouble() > dec.getChance()) {
                continue;
            }
            canvas.setDecor(v.x(), ay, v.z(), idx, null);
        }
    }

    private static void canopyTop(TreeBlockCanvas canvas, IrisTreeDecorator dec, int idx, RNG rng) {
        KMap<Long, Integer> colTop = new KMap<>();
        for (TreeBlockCanvas.Vec v : canvas.getCells().keySet()) {
            long key = pack(v.x(), v.z());
            Integer cur = colTop.get(key);
            if (cur == null || v.y() > cur) {
                colTop.put(key, v.y());
            }
        }
        for (Map.Entry<Long, Integer> e : colTop.entrySet()) {
            int x = unpackX(e.getKey());
            int z = unpackZ(e.getKey());
            int aboveY = e.getValue() + 1;
            if (canvas.has(x, aboveY, z) || rng.nextDouble() > dec.getChance()) {
                continue;
            }
            canvas.setDecor(x, aboveY, z, idx, null);
        }
    }

    private static void canopyBottom(TreeBlockCanvas canvas, IrisTreeDecorator dec, int idx, RNG rng) {
        KMap<Long, Integer> colBottom = columnLeafBottoms(canvas);
        for (Map.Entry<Long, Integer> e : colBottom.entrySet()) {
            int x = unpackX(e.getKey());
            int z = unpackZ(e.getKey());
            int belowY = e.getValue() - 1;
            if (canvas.has(x, belowY, z) || rng.nextDouble() > dec.getChance()) {
                continue;
            }
            canvas.setDecor(x, belowY, z, idx, null);
        }
    }

    private static void canopyHang(TreeBlockCanvas canvas, IrisTreeDecorator dec, int idx, RNG rng) {
        int maxLen = Math.max(1, dec.getLength());
        KMap<Long, Integer> colBottom = columnLeafBottoms(canvas);
        for (Map.Entry<Long, Integer> e : colBottom.entrySet()) {
            if (rng.nextDouble() > dec.getChance()) {
                continue;
            }
            int x = unpackX(e.getKey());
            int z = unpackZ(e.getKey());
            int strand = rng.i(1, maxLen + 1);
            for (int k = 1; k <= strand; k++) {
                int yy = e.getValue() - k;
                if (canvas.has(x, yy, z)) {
                    break;
                }
                canvas.setDecor(x, yy, z, idx, null);
            }
        }
    }

    private static void trunkBase(TreeBlockCanvas canvas, IrisTreeDecorator dec, int idx, RNG rng) {
        int[][] sides = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (TreeBlockCanvas.Vec v : new KList<>(canvas.getTrunk())) {
            if (v.y() != 0) {
                continue;
            }
            for (int[] side : sides) {
                int nx = v.x() + side[0];
                int nz = v.z() + side[1];
                if (canvas.has(nx, 0, nz) || rng.nextDouble() > dec.getChance()) {
                    continue;
                }
                canvas.setDecor(nx, 0, nz, idx, null);
            }
        }
    }

    private static void trunkTop(TreeBlockCanvas canvas, IrisTreeDecorator dec, int idx, RNG rng) {
        int maxY = Integer.MIN_VALUE;
        for (TreeBlockCanvas.Vec v : canvas.getTrunk()) {
            maxY = Math.max(maxY, v.y());
        }
        if (maxY == Integer.MIN_VALUE) {
            return;
        }
        for (TreeBlockCanvas.Vec v : new KList<>(canvas.getTrunk())) {
            if (v.y() != maxY) {
                continue;
            }
            int aboveY = v.y() + 1;
            if (canvas.has(v.x(), aboveY, v.z()) || rng.nextDouble() > dec.getChance()) {
                continue;
            }
            canvas.setDecor(v.x(), aboveY, v.z(), idx, null);
        }
    }

    private static void groundScatter(TreeBlockCanvas canvas, IrisTreeDecorator dec, int idx, RNG rng) {
        double cx = 0;
        double cz = 0;
        int count = 0;
        double footprint = 1.0;
        for (TreeBlockCanvas.Vec v : canvas.getTrunk()) {
            if (v.y() == 0) {
                cx += v.x();
                cz += v.z();
                count++;
            }
        }
        if (count == 0) {
            return;
        }
        cx /= count;
        cz /= count;
        for (TreeBlockCanvas.Vec v : canvas.getTrunk()) {
            if (v.y() == 0) {
                footprint = Math.max(footprint, Math.hypot(v.x() - cx, v.z() - cz));
            }
        }
        int radius = (int) Math.ceil(footprint + 3.0);
        int icx = (int) Math.round(cx);
        int icz = (int) Math.round(cz);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (Math.hypot(dx, dz) > radius) {
                    continue;
                }
                int x = icx + dx;
                int z = icz + dz;
                if (canvas.has(x, 0, z) || rng.nextDouble() > dec.getChance()) {
                    continue;
                }
                canvas.setDecor(x, 0, z, idx, null);
            }
        }
    }

    private static KMap<Long, Integer> columnLeafBottoms(TreeBlockCanvas canvas) {
        KMap<Long, Integer> colBottom = new KMap<>();
        for (TreeBlockCanvas.Vec v : canvas.getLeaf()) {
            long key = pack(v.x(), v.z());
            Integer cur = colBottom.get(key);
            if (cur == null || v.y() < cur) {
                colBottom.put(key, v.y());
            }
        }
        return colBottom;
    }

    private static String facingAway(int cx, int cz, int x, int z) {
        int dx = x - cx;
        int dz = z - cz;
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? "east" : "west";
        }
        return dz >= 0 ? "south" : "north";
    }

    private static long pack(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static int unpackX(long key) {
        return (int) (key >> 32);
    }

    private static int unpackZ(long key) {
        return (int) (key & 0xFFFFFFFFL);
    }
}
