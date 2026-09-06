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

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.object.IrisObject;
import art.arcane.iris.engine.object.IrisProceduralTree;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.math.IrisBlockVector;
import art.arcane.volmlib.util.math.RNG;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ProceduralTreeGenerator {
    private ProceduralTreeGenerator() {
    }

    public static IrisObject generate(IrisProceduralTree tree, int height, RNG rng, IrisData data) {
        TreeBlockCanvas canvas = new TreeBlockCanvas();
        long baseSeed = rng.getSeed();

        List<TreeTrunkBuilder.Limb> limbs = TreeTrunkBuilder.build(canvas, tree, height);

        boolean wantEndpoints = tree.getDecorators() != null && !tree.getDecorators().isEmpty();
        List<int[]> branchEndpoints = wantEndpoints ? new ArrayList<>() : null;

        int limbIndex = 0;
        for (TreeTrunkBuilder.Limb limb : limbs) {
            TreeCanopyBuilder.build(canvas, tree, height, limb.offsets(), limb.branchStartY(), baseSeed + limbIndex * 131L, branchEndpoints);
            limbIndex++;
        }

        if (wantEndpoints) {
            TreeDecoratorApplier.apply(canvas, tree, baseSeed, branchEndpoints);
        }

        if (tree.isRoots()) {
            TreeRootBuilder.build(canvas, tree, height, baseSeed);
        }

        if (tree.isPlausible()) {
            TreeSupport.ensureLeavesSupported(canvas, 24);
        }

        Map<TreeBlockCanvas.Vec, PlatformBlockState> resolved = new HashMap<>();
        Set<TreeBlockCanvas.Vec> trunkPositions = new HashSet<>();
        Set<TreeBlockCanvas.Vec> leafPositions = new HashSet<>();
        RNG paletteRng = new RNG(tree.getSeed());
        for (Map.Entry<TreeBlockCanvas.Vec, TreeBlockCanvas.Cell> entry : canvas.getCells().entrySet()) {
            TreeBlockCanvas.Cell cell = entry.getValue();
            PlatformBlockState state = TreeBlockResolver.resolve(tree, data, cell, entry.getKey(), paletteRng);
            if (state == null) {
                continue;
            }
            resolved.put(entry.getKey(), state);
            if (cell.role() == TreeBlockCanvas.Role.TRUNK || cell.role() == TreeBlockCanvas.Role.SECONDARY_TRUNK) {
                trunkPositions.add(entry.getKey());
            } else if (cell.role() == TreeBlockCanvas.Role.LEAF || cell.role() == TreeBlockCanvas.Role.SECONDARY_LEAF) {
                leafPositions.add(entry.getKey());
            }
        }

        TreePlausibility.apply(resolved, trunkPositions, leafPositions, tree);

        return assemble(resolved);
    }

    private static IrisObject assemble(Map<TreeBlockCanvas.Vec, PlatformBlockState> resolved) {
        if (resolved.isEmpty()) {
            return null;
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (TreeBlockCanvas.Vec v : resolved.keySet()) {
            minX = Math.min(minX, v.x());
            minY = Math.min(minY, v.y());
            minZ = Math.min(minZ, v.z());
            maxX = Math.max(maxX, v.x());
            maxY = Math.max(maxY, v.y());
            maxZ = Math.max(maxZ, v.z());
        }

        int w = maxX - minX + 1;
        int h = maxY - minY + 1;
        int d = maxZ - minZ + 1;
        int cx = w / 2;
        int cy = h / 2;
        int cz = d / 2;

        IrisObject object = new IrisObject(w, h, d);
        for (Map.Entry<TreeBlockCanvas.Vec, PlatformBlockState> entry : resolved.entrySet()) {
            TreeBlockCanvas.Vec v = entry.getKey();
            int nx = v.x() - minX - cx;
            int ny = v.y() - cy + 1;
            int nz = v.z() - minZ - cz;
            object.getBlocks().put(new IrisBlockVector(nx, ny, nz), entry.getValue());
        }

        return object;
    }
}
