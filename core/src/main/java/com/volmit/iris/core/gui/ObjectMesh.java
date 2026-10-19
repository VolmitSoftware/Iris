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

import com.volmit.iris.core.gui.BlockTextures.BlockTexture;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.BlockVector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The sides of an object's blocks that can be seen from outside, as flat textured quads. Sides pressed against a solid
 * neighbour are left out, which is most of them in a large object.
 */
record ObjectMesh(List<Quad> quads, Bounds bounds) {
    private static final double CROSS_SHADE = 0.9D;

    static ObjectMesh build(Map<BlockVector, BlockData> blocks, Function<BlockData, BlockModel> modelOf) {
        List<Quad> quads = new ArrayList<>();
        for (Map.Entry<BlockVector, BlockData> entry : blocks.entrySet()) {
            BlockVector position = entry.getKey();
            BlockData block = entry.getValue();
            BlockModel model = modelOf.apply(block);
            if (model.isCross()) {
                addCross(quads, position, model.crossSprite());
                continue;
            }
            for (Map.Entry<CubeFace, BlockTexture> face : model.faces().entrySet()) {
                if (!isCoveredByNeighbour(blocks, modelOf, position, block, model, face.getKey())) {
                    quads.add(Quad.side(position, model.box(), face.getKey(), face.getValue()));
                }
            }
        }
        return new ObjectMesh(List.copyOf(quads), Bounds.of(blocks.keySet()));
    }

    boolean isEmpty() {
        return quads.isEmpty();
    }

    // Leaves next to leaves and glass next to glass hide the side between them too, as they do in the game.
    private static boolean isCoveredByNeighbour(Map<BlockVector, BlockData> blocks, Function<BlockData, BlockModel> modelOf,
                                                BlockVector position, BlockData block, BlockModel model, CubeFace face) {
        if (!touchesBlockEdge(model.box(), face)) {
            return false;
        }
        BlockData neighbour = blocks.get(new BlockVector(
                position.getBlockX() + face.stepX(),
                position.getBlockY() + face.stepY(),
                position.getBlockZ() + face.stepZ()));
        if (neighbour == null) {
            return false;
        }
        if (neighbour.equals(block) && model.box().isFull()) {
            return true;
        }
        return modelOf.apply(neighbour).hidesTouchingSides();
    }

    private static boolean touchesBlockEdge(BlockModel.Box box, CubeFace face) {
        return switch (face) {
            case UP -> box.maxY() >= 1D;
            case DOWN -> box.minY() <= 0D;
            case NORTH -> box.minZ() <= 0D;
            case SOUTH -> box.maxZ() >= 1D;
            case EAST -> box.maxX() >= 1D;
            case WEST -> box.minX() <= 0D;
        };
    }

    private static void addCross(List<Quad> quads, BlockVector position, BlockTexture sprite) {
        double x = position.getBlockX();
        double y = position.getBlockY();
        double z = position.getBlockZ();
        quads.add(new Quad(x, y + 1D, z, 1D, 0D, 1D, 0D, -1D, 0D, sprite, 0D, 0D, 1D, 1D, CROSS_SHADE, 0D, 0D, 0D));
        quads.add(new Quad(x, y + 1D, z + 1D, 1D, 0D, -1D, 0D, -1D, 0D, sprite, 0D, 0D, 1D, 1D, CROSS_SHADE, 0D, 0D, 0D));
    }

    /**
     * A parallelogram from its corner along two edges. Texture u runs along the first edge and v along the second,
     * starting at {@code textureU}, {@code textureV} and covering {@code textureWidth} by {@code textureHeight} of
     * the texture. A zero normal marks a quad seen from both sides, like a plant's sprite.
     */
    record Quad(double originX, double originY, double originZ,
                double edgeUX, double edgeUY, double edgeUZ,
                double edgeVX, double edgeVY, double edgeVZ,
                BlockTexture texture, double textureU, double textureV, double textureWidth, double textureHeight,
                double shade, double normalX, double normalY, double normalZ) {

        boolean isTwoSided() {
            return normalX == 0D && normalY == 0D && normalZ == 0D;
        }

        // Each side is laid out as seen from outside the block: u to the right, v downwards, matching Minecraft's UVs.
        static Quad side(BlockVector position, BlockModel.Box box, CubeFace face, BlockTexture texture) {
            double[] origin = switch (face) {
                case UP -> new double[]{box.minX(), box.maxY(), box.minZ()};
                case DOWN -> new double[]{box.minX(), box.minY(), box.maxZ()};
                case NORTH -> new double[]{box.maxX(), box.maxY(), box.minZ()};
                case SOUTH -> new double[]{box.minX(), box.maxY(), box.maxZ()};
                case EAST -> new double[]{box.maxX(), box.maxY(), box.maxZ()};
                case WEST -> new double[]{box.minX(), box.maxY(), box.minZ()};
            };
            Edge u = switch (face) {
                case UP, DOWN, SOUTH -> Edge.POSITIVE_X;
                case NORTH -> Edge.NEGATIVE_X;
                case EAST -> Edge.NEGATIVE_Z;
                case WEST -> Edge.POSITIVE_Z;
            };
            Edge v = switch (face) {
                case UP -> Edge.POSITIVE_Z;
                case DOWN -> Edge.NEGATIVE_Z;
                default -> Edge.NEGATIVE_Y;
            };
            double uLength = box.max(u.axis()) - box.min(u.axis());
            double vLength = box.max(v.axis()) - box.min(v.axis());
            return new Quad(
                    position.getBlockX() + origin[0], position.getBlockY() + origin[1], position.getBlockZ() + origin[2],
                    u.x() * uLength, u.y() * uLength, u.z() * uLength,
                    v.x() * vLength, v.y() * vLength, v.z() * vLength,
                    texture, u.textureStart(origin), v.textureStart(origin), uLength, vLength,
                    face.shade(), face.stepX(), face.stepY(), face.stepZ());
        }
    }

    private enum Edge {
        POSITIVE_X(0, 1),
        NEGATIVE_X(0, -1),
        NEGATIVE_Y(1, -1),
        POSITIVE_Z(2, 1),
        NEGATIVE_Z(2, -1);

        private final int axis;
        private final int sign;

        Edge(int axis, int sign) {
            this.axis = axis;
            this.sign = sign;
        }

        int axis() {
            return axis;
        }

        double x() {
            return axis == 0 ? sign : 0D;
        }

        double y() {
            return axis == 1 ? sign : 0D;
        }

        double z() {
            return axis == 2 ? sign : 0D;
        }

        // An edge running against its axis starts its texture at the far side, like Minecraft's default UVs.
        double textureStart(double[] origin) {
            return sign > 0 ? origin[axis] : 1D - origin[axis];
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
