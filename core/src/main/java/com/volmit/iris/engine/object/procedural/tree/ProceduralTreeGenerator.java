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

import com.volmit.iris.engine.object.procedural.ObjectBuilder;
import com.volmit.iris.engine.object.procedural.ProceduralShape;
import com.volmit.iris.util.math.RNG;
import org.bukkit.block.data.BlockData;
import com.volmit.iris.engine.object.procedural.ObjectBlock;
import com.volmit.iris.engine.object.procedural.BlockPosition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ProceduralTreeGenerator {
    private ProceduralTreeGenerator() {
    }

    public static ProceduralShape generate(Tree tree, int height, RNG rng, ObjectBuilder blocks) {
        TreeBlockCanvas canvas = new TreeBlockCanvas();
        long baseSeed = rng.getSeed();

        List<TreeTrunkBuilder.Limb> limbs = TreeTrunkBuilder.build(canvas, tree, height);

        boolean wantEndpoints = tree.decorators() != null && !tree.decorators().isEmpty();
        List<int[]> branchEndpoints = wantEndpoints ? new ArrayList<>() : null;

        int limbIndex = 0;
        for (TreeTrunkBuilder.Limb limb : limbs) {
            TreeCanopyBuilder.build(canvas, tree, height, limb.offsets(), limb.branchStartY(), baseSeed + limbIndex * 131L, branchEndpoints);
            limbIndex++;
        }

        if (wantEndpoints) {
            TreeDecoratorApplier.apply(canvas, tree, baseSeed, branchEndpoints);
        }

        if (tree.roots()) {
            TreeRootBuilder.build(canvas, tree, height, baseSeed);
        }

        if (tree.plausible()) {
            TreeSupport.ensureLeavesSupported(canvas, 24);
        }

        Map<TreeBlockCanvas.Vec, BlockData> resolved = new HashMap<>();
        Set<TreeBlockCanvas.Vec> trunkPositions = new HashSet<>();
        Set<TreeBlockCanvas.Vec> leafPositions = new HashSet<>();
        for (Map.Entry<TreeBlockCanvas.Vec, TreeBlockCanvas.Cell> entry : canvas.getCells().entrySet()) {
            TreeBlockCanvas.Cell cell = entry.getValue();
            BlockData state = TreeBlockResolver.resolve(tree, blocks, cell, entry.getKey());
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

        TreePlausibility.apply(resolved, trunkPositions, leafPositions, tree, blocks);

        return assemble(resolved);
    }

    private static ProceduralShape assemble(Map<TreeBlockCanvas.Vec, BlockData> resolved) {
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

        List<ObjectBlock> placed = new ArrayList<>(resolved.size());
        for (Map.Entry<TreeBlockCanvas.Vec, BlockData> entry : resolved.entrySet()) {
            TreeBlockCanvas.Vec v = entry.getKey();
            int nx = v.x() - minX - cx;
            int ny = v.y() - cy + 1;
            int nz = v.z() - minZ - cz;
            placed.add(new ObjectBlock(new BlockPosition(nx, ny, nz), entry.getValue()));
        }

        return new ProceduralShape(w, h, d, placed);
    }
}
