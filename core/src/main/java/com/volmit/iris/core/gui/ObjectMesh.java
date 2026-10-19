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


package com.volmit.iris.core.gui;

import com.volmit.iris.core.gui.BlockModel.Face;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.BlockVector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The faces of an object's blocks that can be seen from outside. A face is left out when the neighbour on its side
 * covers that side, which is most of them in a large object. See-through faces, like water and stained glass, are
 * kept apart because they are drawn last, blended over everything behind them. Top faces covered only by the block
 * above are kept aside too, so the top layers can be cut away without building the mesh again; what is cut away stays
 * as a faint ghost of the object's shape.
 */
record ObjectMesh(List<Quad> solidQuads, List<Quad> translucentQuads, List<Quad> cutAwayQuads, List<Quad> topsUnderBlockAbove,
                  Bounds bounds) {
    private static final double SEE_THROUGH_OPACITY = 0.22D;
    private static final double FULL_OPACITY = 1D;
    private static final double CUT_AWAY_OPACITY = 0.1D;

    static ObjectMesh build(Map<BlockVector, BlockData> blocks, Function<BlockData, BlockModel> modelOf) {
        return build(blocks, modelOf, Bounds.of(blocks.keySet()), block -> false);
    }

    /**
     * @param bounds       the whole object's extent, kept when only part of it is drawn so the view does not jump
     * @param isSeeThrough blocks drawn faintly, which hide nothing behind them, to look past them into the object
     */
    static ObjectMesh build(Map<BlockVector, BlockData> blocks, Function<BlockData, BlockModel> modelOf, Bounds bounds,
                            Predicate<BlockData> isSeeThrough) {
        List<Quad> solid = new ArrayList<>();
        List<Quad> translucent = new ArrayList<>();
        List<Quad> topsUnderBlockAbove = new ArrayList<>();
        for (Map.Entry<BlockVector, BlockData> entry : blocks.entrySet()) {
            BlockVector position = entry.getKey();
            BlockData block = entry.getValue();
            BlockModel model = modelOf.apply(block);
            boolean seeThrough = isSeeThrough.test(block);
            for (Face face : model.faces()) {
                Quad quad = new Quad(face, position.getBlockX(), position.getBlockY(), position.getBlockZ(),
                        seeThrough ? SEE_THROUGH_OPACITY : FULL_OPACITY);
                if (!isHiddenByNeighbour(blocks, modelOf, isSeeThrough, position, block, model, face)) {
                    (quad.isBlended() ? translucent : solid).add(quad);
                } else if (face.cullFace() == CubeFace.UP) {
                    topsUnderBlockAbove.add(quad);
                }
            }
        }
        return new ObjectMesh(List.copyOf(solid), List.copyOf(translucent), List.of(), List.copyOf(topsUnderBlockAbove), bounds);
    }

    /**
     * This mesh with the blocks above the given layer faded to a ghost of the object's shape, and the tops of that
     * layer's blocks showing again. Quick enough to follow a slider as it moves.
     */
    ObjectMesh cutAbove(int highestShownY) {
        List<Quad> solid = new ArrayList<>();
        List<Quad> translucent = new ArrayList<>();
        List<Quad> cutAway = new ArrayList<>();
        for (Quad quad : solidQuads) {
            keepOrCutAway(quad, highestShownY, solid, cutAway);
        }
        for (Quad quad : translucentQuads) {
            keepOrCutAway(quad, highestShownY, translucent, cutAway);
        }
        for (Quad quad : topsUnderBlockAbove) {
            if (quad.y() == highestShownY) {
                (quad.isBlended() ? translucent : solid).add(quad);
            }
        }
        return new ObjectMesh(solid, translucent, cutAway, List.of(), bounds);
    }

    private static void keepOrCutAway(Quad quad, int highestShownY, List<Quad> kept, List<Quad> cutAway) {
        if (quad.y() <= highestShownY) {
            kept.add(quad);
        } else {
            cutAway.add(quad.faded(CUT_AWAY_OPACITY));
        }
    }

    boolean isEmpty() {
        return solidQuads.isEmpty() && translucentQuads.isEmpty() && cutAwayQuads.isEmpty();
    }

    int faceCount() {
        return solidQuads.size() + translucentQuads.size();
    }

    // Glass next to glass and water next to water hide the side between them too, as they do in the game. A see-through
    // block only hides sides of other see-through blocks, so whatever it covers shows through it.
    private static boolean isHiddenByNeighbour(Map<BlockVector, BlockData> blocks, Function<BlockData, BlockModel> modelOf,
                                               Predicate<BlockData> isSeeThrough, BlockVector position, BlockData block,
                                               BlockModel model, Face face) {
        CubeFace side = face.cullFace();
        if (side == null) {
            return false;
        }
        BlockData neighbour = blocks.get(new BlockVector(
                position.getBlockX() + side.stepX(),
                position.getBlockY() + side.stepY(),
                position.getBlockZ() + side.stepZ()));
        if (neighbour == null || (isSeeThrough.test(neighbour) && !isSeeThrough.test(block))) {
            return false;
        }
        BlockModel neighbourModel = modelOf.apply(neighbour);
        if (neighbourModel.coveredSides().contains(side.opposite())) {
            return true;
        }
        return neighbour.getMaterial() == block.getMaterial() && model.hidesSameBlockSides() && neighbourModel.hidesSameBlockSides();
    }

    /**
     * A model face placed at a block in the object, drawn at the given opacity on top of its texture's own.
     */
    record Quad(Face face, int x, int y, int z, double opacity) {
        boolean isBlended() {
            return opacity < FULL_OPACITY || face.texture().isTranslucent();
        }

        Quad faded(double factor) {
            return new Quad(face, x, y, z, opacity * factor);
        }

        double originX() {
            return x + face.origin().x();
        }

        double originY() {
            return y + face.origin().y();
        }

        double originZ() {
            return z + face.origin().z();
        }
    }

    /**
     * The blocks' extent, with the maximum one past the last block so a single block spans one unit.
     */
    record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        private static final Bounds EMPTY = new Bounds(0, 0, 0, 1, 1, 1);

        static Bounds of(Iterable<BlockVector> positions) {
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (BlockVector position : positions) {
                minX = Math.min(minX, position.getBlockX());
                minY = Math.min(minY, position.getBlockY());
                minZ = Math.min(minZ, position.getBlockZ());
                maxX = Math.max(maxX, position.getBlockX() + 1);
                maxY = Math.max(maxY, position.getBlockY() + 1);
                maxZ = Math.max(maxZ, position.getBlockZ() + 1);
            }
            return minX == Integer.MAX_VALUE ? EMPTY : new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
        }

        int sizeX() {
            return maxX - minX;
        }

        int sizeY() {
            return maxY - minY;
        }

        int sizeZ() {
            return maxZ - minZ;
        }

        int largestSize() {
            return Math.max(sizeX(), Math.max(sizeY(), sizeZ()));
        }
    }
}
