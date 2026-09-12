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
import art.arcane.iris.generation.terrain.IrisMaterialPalette;
import art.arcane.iris.generation.decoration.IrisProceduralBlocks;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.generation.block.B;
import art.arcane.volmlib.util.math.RNG;

public final class TreeBlockResolver {
    private TreeBlockResolver() {
    }

    public static PlatformBlockState resolve(IrisProceduralTree tree, IrisData data, TreeBlockCanvas.Cell cell, TreeBlockCanvas.Vec pos, RNG paletteRng) {
        return switch (cell.role()) {
            case TRUNK -> finishTrunk(resolveBlock(tree.getTrunk(), tree.getTrunkPalette(), data, pos, paletteRng), cell);
            case SECONDARY_TRUNK -> {
                PlatformBlockState state = resolveBlock(tree.getSecondaryTrunk(), tree.getSecondaryTrunkPalette(), data, pos, paletteRng);
                if (state == null) {
                    state = resolveBlock(tree.getTrunk(), tree.getTrunkPalette(), data, pos, paletteRng);
                }
                yield finishTrunk(state, cell);
            }
            case LEAF -> resolveBlock(tree.getLeaves(), tree.getLeavesPalette(), data, pos, paletteRng);
            case SECONDARY_LEAF -> {
                PlatformBlockState state = resolveSecondaryLeaf(tree, data, pos, paletteRng);
                if (state == null) {
                    state = resolveBlock(tree.getLeaves(), tree.getLeavesPalette(), data, pos, paletteRng);
                }
                yield state;
            }
            case DECORATOR -> {
                int index = cell.decoratorIndex();
                if (index < 0 || tree.getDecorators() == null || index >= tree.getDecorators().size()) {
                    yield null;
                }
                IrisTreeDecorator dec = tree.getDecorators().get(index);
                PlatformBlockState state = resolveBlock(dec.getBlock(), dec.getPalette(), data, pos, paletteRng);
                if (state != null && cell.facing() != null && IrisProceduralBlocks.hasProperty(state, "facing")) {
                    try {
                        state = state.withProperty("facing", cell.facing().toLowerCase());
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                yield state;
            }
            default -> null;
        };
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

    private static PlatformBlockState resolveSecondaryLeaf(IrisProceduralTree tree, IrisData data, TreeBlockCanvas.Vec pos, RNG paletteRng) {
        if (IrisProceduralBlocks.paletteSet(tree.getSecondaryLeavesPalette())) {
            return tree.getSecondaryLeavesPalette().get(paletteRng, pos.x(), pos.y(), pos.z(), data);
        }
        if (tree.getWeightedSecondaryLeaves() != null && !tree.getWeightedSecondaryLeaves().isEmpty()) {
            String picked = pickWeighted(tree, new RNG(tree.getSeed() ^ positionHash(pos)));
            return picked == null ? null : B.getStateOrNull(picked, false);
        }
        if (tree.getSecondaryLeaves() != null && !tree.getSecondaryLeaves().isEmpty()) {
            return B.getStateOrNull(tree.getSecondaryLeaves(), false);
        }
        return null;
    }

    private static String pickWeighted(IrisProceduralTree tree, RNG rng) {
        long total = 0;
        for (IrisTreeSecondaryLeaf s : tree.getWeightedSecondaryLeaves()) {
            total += Math.max(0, s.getWeight());
        }
        if (total <= 0) {
            return null;
        }
        double r = rng.nextDouble() * total;
        long cumulative = 0;
        for (IrisTreeSecondaryLeaf s : tree.getWeightedSecondaryLeaves()) {
            cumulative += Math.max(0, s.getWeight());
            if (r < cumulative) {
                return s.getBlock();
            }
        }
        return tree.getWeightedSecondaryLeaves().get(tree.getWeightedSecondaryLeaves().size() - 1).getBlock();
    }

    private static PlatformBlockState resolveBlock(String block, IrisMaterialPalette palette, IrisData data, TreeBlockCanvas.Vec pos, RNG paletteRng) {
        if (IrisProceduralBlocks.paletteSet(palette)) {
            return palette.get(paletteRng, pos.x(), pos.y(), pos.z(), data);
        }
        if (block != null && !block.isEmpty()) {
            return B.getStateOrNull(block, false);
        }
        return null;
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
