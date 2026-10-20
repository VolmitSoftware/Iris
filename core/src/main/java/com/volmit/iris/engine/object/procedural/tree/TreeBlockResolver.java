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
import com.volmit.iris.engine.object.IrisMaterialPalette;
import com.volmit.iris.engine.object.procedural.IrisProceduralBlocks;
import org.bukkit.block.data.BlockData;
import com.volmit.iris.util.math.RNG;

public final class TreeBlockResolver {
    private TreeBlockResolver() {
    }

    public static BlockData resolve(Tree tree, ObjectBuilder blocks, TreeBlockCanvas.Cell cell, TreeBlockCanvas.Vec pos) {
        RNG paletteRng = new RNG(tree.seed());
        RNG posRng = new RNG(tree.seed() ^ positionHash(pos));

        switch (cell.role()) {
            case TRUNK -> {
                BlockData state = resolveBlock(tree.trunk(), tree.trunkPalette().orElse(null), blocks, pos, paletteRng);
                return finishTrunk(state, cell, blocks);
            }
            case SECONDARY_TRUNK -> {
                BlockData state = resolveBlock(tree.secondaryTrunk().orElse(null), tree.secondaryTrunkPalette().orElse(null), blocks, pos, paletteRng);
                if (state == null) {
                    state = resolveBlock(tree.trunk(), tree.trunkPalette().orElse(null), blocks, pos, paletteRng);
                }
                return finishTrunk(state, cell, blocks);
            }
            case LEAF -> {
                return resolveBlock(tree.leaves(), tree.leavesPalette().orElse(null), blocks, pos, paletteRng);
            }
            case SECONDARY_LEAF -> {
                BlockData state = resolveSecondaryLeaf(tree, blocks, pos, paletteRng, posRng);
                if (state == null) {
                    state = resolveBlock(tree.leaves(), tree.leavesPalette().orElse(null), blocks, pos, paletteRng);
                }
                return state;
            }
            case DECORATOR -> {
                int index = cell.decoratorIndex();
                if (index < 0 || tree.decorators() == null || index >= tree.decorators().size()) {
                    return null;
                }
                TreeDecorator dec = tree.decorators().get(index);
                BlockData state = resolveBlock(dec.block(), dec.palette().orElse(null), blocks, pos, paletteRng);
                if (state != null && cell.facing() != null && IrisProceduralBlocks.hasProperty(state, "facing")) {
                    try {
                        state = blocks.withProperty(state, "facing", cell.facing().toLowerCase());
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                return state;
            }
            default -> {
                return null;
            }
        }
    }

    private static BlockData finishTrunk(BlockData state, TreeBlockCanvas.Cell cell, ObjectBuilder blocks) {
        if (state == null) {
            return null;
        }
        if (cell.axis() != TreeBlockCanvas.Axis.NONE && IrisProceduralBlocks.hasProperty(state, "axis")) {
            try {
                state = blocks.withProperty(state, "axis", cell.axis().name().toLowerCase());
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (cell.exposed()) {
            return woodCap(state, blocks);
        }
        return state;
    }

    private static BlockData resolveSecondaryLeaf(Tree tree, ObjectBuilder blocks, TreeBlockCanvas.Vec pos, RNG paletteRng, RNG posRng) {
        IrisMaterialPalette secondaryPalette = tree.secondaryLeavesPalette().orElse(null);
        if (IrisProceduralBlocks.paletteSet(secondaryPalette)) {
            return blocks.pick(secondaryPalette, paletteRng.getSeed(), pos.x(), pos.y(), pos.z());
        }
        if (tree.weightedSecondaryLeaves() != null && !tree.weightedSecondaryLeaves().isEmpty()) {
            String picked = pickWeighted(tree, posRng);
            return picked == null ? null : blocks.block(picked);
        }
        if (tree.secondaryLeaves().isPresent() && !tree.secondaryLeaves().get().isEmpty()) {
            return blocks.block(tree.secondaryLeaves().get());
        }
        return null;
    }

    private static String pickWeighted(Tree tree, RNG rng) {
        int total = 0;
        for (com.volmit.iris.engine.object.procedural.tree.TreeSecondaryLeaf s : tree.weightedSecondaryLeaves()) {
            total += Math.max(0, s.weight());
        }
        if (total <= 0) {
            return null;
        }
        double r = rng.nextDouble() * total;
        double cumulative = 0;
        for (com.volmit.iris.engine.object.procedural.tree.TreeSecondaryLeaf s : tree.weightedSecondaryLeaves()) {
            cumulative += Math.max(0, s.weight());
            if (r < cumulative) {
                return s.block();
            }
        }
        return tree.weightedSecondaryLeaves().get(tree.weightedSecondaryLeaves().size() - 1).block();
    }

    private static BlockData resolveBlock(String block, IrisMaterialPalette palette, ObjectBuilder blocks, TreeBlockCanvas.Vec pos, RNG paletteRng) {
        return IrisProceduralBlocks.resolve(block, palette, blocks, pos.x(), pos.y(), pos.z(), paletteRng);
    }

    private static BlockData woodCap(BlockData state, ObjectBuilder blocks) {
        String key = state.getAsString();
        int bracket = key.indexOf('[');
        String base = bracket < 0 ? key : key.substring(0, bracket);
        String woodKey = null;
        if (base.endsWith("_log")) {
            woodKey = base.substring(0, base.length() - 4) + "_wood";
        } else if (base.endsWith("_stem")) {
            woodKey = base.substring(0, base.length() - 5) + "_hyphae";
        }
        if (woodKey == null) {
            return state;
        }
        BlockData wood = blocks.block(woodKey);
        if (wood == null) {
            return state;
        }
        String axis = IrisProceduralBlocks.propertyValue(state, "axis");
        if (axis != null && IrisProceduralBlocks.hasProperty(wood, "axis")) {
            try {
                wood = blocks.withProperty(wood, "axis", axis);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return wood;
    }

    private static long positionHash(TreeBlockCanvas.Vec pos) {
        return pos.x() * 73856093L ^ pos.y() * 19349663L ^ pos.z() * 83492791L;
    }
}
