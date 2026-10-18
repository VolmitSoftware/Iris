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

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.iris.generation.geometry.IrisBlockVector;
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
        Set<TreeBlockCanvas.Vec> supportPositions = new HashSet<>();
        for (TreeBlockCanvas.Vec position : canvas.getTrunk()) {
            if (position.y() == 0) {
                supportPositions.add(position);
            }
        }

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
            Set<TreeBlockCanvas.Vec> existingWood = new HashSet<>(canvas.getTrunk());
            TreeRootBuilder.build(canvas, tree, height, baseSeed);
            for (TreeBlockCanvas.Vec position : canvas.getTrunk()) {
                if (position.y() <= 0 && !existingWood.contains(position)) {
                    supportPositions.add(position);
                }
            }
        }

        if (tree.isPlausible()) {
            TreeSupport.ensureLeavesSupported(canvas, 24);
        }

        Map<TreeBlockCanvas.Vec, NativeBlockState> resolved = new HashMap<>();
        Set<TreeBlockCanvas.Vec> trunkPositions = new HashSet<>();
        Set<TreeBlockCanvas.Vec> leafPositions = new HashSet<>();
        RNG paletteRng = new RNG(tree.getSeed());
        for (Map.Entry<TreeBlockCanvas.Vec, TreeBlockCanvas.Cell> entry : canvas.getCells().entrySet()) {
            TreeBlockCanvas.Cell cell = entry.getValue();
            NativeBlockState state = TreeBlockResolver.resolve(tree, data, cell, entry.getKey(), paletteRng);
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

        int rootReach = tree.isRoots() ? tree.getRootDepth() > 0 ? tree.getRootDepth()
                : Math.max(2, (int) Math.round(height * 0.18)) : 0;
        return assemble(resolved, supportPositions, leafPositions, rootReach);
    }

    private static IrisObject assemble(Map<TreeBlockCanvas.Vec, NativeBlockState> resolved,
                                       Set<TreeBlockCanvas.Vec> supportPositions,
                                       Set<TreeBlockCanvas.Vec> leafPositions, int rootReach) {
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

        int w = 2 * Math.max(Math.abs(minX), Math.abs(maxX)) + 1;
        int h = maxY - minY + 1;
        int d = 2 * Math.max(Math.abs(minZ), Math.abs(maxZ)) + 1;
        int cy = h / 2;

        IrisObject object = new IrisObject(w, h, d);
        List<TreeGrounding.Root> roots = new ArrayList<>(supportPositions.size());
        Set<TreeBlockCanvas.Vec> canopy = new HashSet<>(leafPositions.size());
        for (Map.Entry<TreeBlockCanvas.Vec, NativeBlockState> entry : resolved.entrySet()) {
            TreeBlockCanvas.Vec v = entry.getKey();
            int ny = v.y() - cy + 1;
            object.getBlocks().put(new IrisBlockVector(v.x(), ny, v.z()), entry.getValue());
            if (supportPositions.contains(v)) {
                roots.add(new TreeGrounding.Root(v.x(), ny, v.z(), entry.getValue()));
            }
            if (leafPositions.contains(v)) {
                canopy.add(new TreeBlockCanvas.Vec(v.x(), ny, v.z()));
            }
        }
        object.setTreeGrounding(TreeGrounding.create(roots, rootReach, canopy));

        return object;
    }
}
