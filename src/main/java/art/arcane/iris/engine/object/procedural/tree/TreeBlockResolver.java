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

package art.arcane.iris.engine.object.procedural.tree;

import art.arcane.iris.builtin.objects.tree.TreeSecondaryLeaf;
import art.arcane.iris.builtin.objects.tree.TreeDecorator;
import art.arcane.iris.core.loader.IrisData;
import com.volmit.block.PaletteSpec;
import art.arcane.iris.engine.object.procedural.IrisProceduralBlocks;
import art.arcane.iris.builtin.objects.tree.Tree;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.data.B;
import art.arcane.volmlib.util.math.RNG;

public final class TreeBlockResolver {
    private TreeBlockResolver() {
    }

    public static PlatformBlockState resolve(Tree tree, IrisData data, TreeBlockCanvas.Cell cell, TreeBlockCanvas.Vec pos) {
        RNG paletteRng = new RNG(tree.seed());
        RNG posRng = new RNG(tree.seed() ^ positionHash(pos));

        switch (cell.role()) {
            case TRUNK -> {
                PlatformBlockState state = resolveBlock(tree.trunk(), tree.trunkPalette().orElse(null), data, pos, paletteRng);
                return finishTrunk(state, cell);
            }
            case SECONDARY_TRUNK -> {
                PlatformBlockState state = resolveBlock(tree.secondaryTrunk().orElse(null), tree.secondaryTrunkPalette().orElse(null), data, pos, paletteRng);
                if (state == null) {
                    state = resolveBlock(tree.trunk(), tree.trunkPalette().orElse(null), data, pos, paletteRng);
                }
                return finishTrunk(state, cell);
            }
            case LEAF -> {
                return resolveBlock(tree.leaves(), tree.leavesPalette().orElse(null), data, pos, paletteRng);
            }
            case SECONDARY_LEAF -> {
                PlatformBlockState state = resolveSecondaryLeaf(tree, data, pos, paletteRng, posRng);
                if (state == null) {
                    state = resolveBlock(tree.leaves(), tree.leavesPalette().orElse(null), data, pos, paletteRng);
                }
                return state;
            }
            case DECORATOR -> {
                int index = cell.decoratorIndex();
                if (index < 0 || tree.decorators() == null || index >= tree.decorators().size()) {
                    return null;
                }
                TreeDecorator dec = tree.decorators().get(index);
                PlatformBlockState state = resolveBlock(dec.block(), dec.palette().orElse(null), data, pos, paletteRng);
                if (state != null && cell.facing() != null && IrisProceduralBlocks.hasProperty(state, "facing")) {
                    try {
                        state = state.withProperty("facing", cell.facing().toLowerCase());
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

    private static PlatformBlockState finishTrunk(PlatformBlockState state, TreeBlockCanvas.Cell cell) {
        if (state == null) {
            return null;
        }
        if (cell.axis() != TreeBlockCanvas.Axis.NONE && IrisProceduralBlocks.hasProperty(state, "axis")) {
            try {
                state = state.withProperty("axis", cell.axis().name().toLowerCase());
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (cell.exposed()) {
            return woodCap(state);
        }
        return state;
    }

    private static PlatformBlockState resolveSecondaryLeaf(Tree tree, IrisData data, TreeBlockCanvas.Vec pos, RNG paletteRng, RNG posRng) {
        PaletteSpec secondaryPalette = tree.secondaryLeavesPalette().orElse(null);
        if (IrisProceduralBlocks.paletteSet(secondaryPalette)) {
            return IrisProceduralBlocks.pick(secondaryPalette, data, paletteRng.getSeed(), pos.x(), pos.y(), pos.z());
        }
        if (tree.weightedSecondaryLeaves() != null && !tree.weightedSecondaryLeaves().isEmpty()) {
            String picked = pickWeighted(tree, posRng);
            return picked == null ? null : B.getStateOrNull(picked, false);
        }
        if (tree.secondaryLeaves().isPresent() && !tree.secondaryLeaves().get().isEmpty()) {
            return B.getStateOrNull(tree.secondaryLeaves().get(), false);
        }
        return null;
    }

    private static String pickWeighted(Tree tree, RNG rng) {
        int total = 0;
        for (art.arcane.iris.builtin.objects.tree.TreeSecondaryLeaf s : tree.weightedSecondaryLeaves()) {
            total += Math.max(0, s.weight());
        }
        if (total <= 0) {
            return null;
        }
        double r = rng.nextDouble() * total;
        double cumulative = 0;
        for (art.arcane.iris.builtin.objects.tree.TreeSecondaryLeaf s : tree.weightedSecondaryLeaves()) {
            cumulative += Math.max(0, s.weight());
            if (r < cumulative) {
                return s.block();
            }
        }
        return tree.weightedSecondaryLeaves().get(tree.weightedSecondaryLeaves().size() - 1).block();
    }

    private static PlatformBlockState resolveBlock(String block, PaletteSpec palette, IrisData data, TreeBlockCanvas.Vec pos, RNG paletteRng) {
        return IrisProceduralBlocks.resolve(block, palette, data, pos.x(), pos.y(), pos.z(), paletteRng);
    }

    private static PlatformBlockState woodCap(PlatformBlockState state) {
        String key = state.key();
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
        PlatformBlockState wood = B.getStateOrNull(woodKey, false);
        if (wood == null) {
            return state;
        }
        String axis = IrisProceduralBlocks.propertyValue(state, "axis");
        if (axis != null && IrisProceduralBlocks.hasProperty(wood, "axis")) {
            try {
                wood = wood.withProperty("axis", axis);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return wood;
    }

    private static long positionHash(TreeBlockCanvas.Vec pos) {
        return pos.x() * 73856093L ^ pos.y() * 19349663L ^ pos.z() * 83492791L;
    }
}
