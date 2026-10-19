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

import java.util.EnumMap;
import java.util.Map;

/**
 * A block as the object viewer draws it: a box with a texture on each side, or two crossed sprites for plants.
 * Models made of several boxes, like stairs, are drawn as a full block.
 *
 * @param box         the box in block units, from 0 to 1 on every axis for a full block
 * @param faces       the texture on each side of the box; a side the model leaves open is missing
 * @param crossSprite the sprite drawn on two crossed planes, or null for a box
 */
record BlockModel(Box box, Map<CubeFace, BlockTexture> faces, BlockTexture crossSprite) {
    static BlockModel box(Box box, Map<CubeFace, BlockTexture> faces) {
        return new BlockModel(box, Map.copyOf(faces), null);
    }

    static BlockModel cross(BlockTexture sprite) {
        return new BlockModel(Box.FULL, Map.of(), sprite);
    }

    static BlockModel solidColor(int color) {
        BlockTexture texture = new BlockTexture(1, new int[]{color}, new boolean[]{true}, color, null);
        Map<CubeFace, BlockTexture> faces = new EnumMap<>(CubeFace.class);
        for (CubeFace face : CubeFace.values()) {
            faces.put(face, texture);
        }
        return box(Box.FULL, faces);
    }

    boolean isCross() {
        return crossSprite != null;
    }

    /**
     * Whether a neighbour's side touching this block can never be seen.
     */
    boolean hidesTouchingSides() {
        if (isCross() || !box.isFull() || faces.size() < CubeFace.values().length) {
            return false;
        }
        for (BlockTexture texture : faces.values()) {
            for (boolean opaque : texture.opaque()) {
                if (!opaque) {
                    return false;
                }
            }
        }
        return true;
    }

    record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        static final Box FULL = new Box(0D, 0D, 0D, 1D, 1D, 1D);
        private static final double BLOCK_CENTER = 0.5D;

        static Box ofModelElement(double[] from, double[] to) {
            double pixels = 16D;
            return new Box(from[0] / pixels, from[1] / pixels, from[2] / pixels, to[0] / pixels, to[1] / pixels, to[2] / pixels);
        }

        boolean isFull() {
            return equals(FULL);
        }

        double min(int axis) {
            return switch (axis) {
                case 0 -> minX;
                case 1 -> minY;
                default -> minZ;
            };
        }

        double max(int axis) {
            return switch (axis) {
                case 0 -> maxX;
                case 1 -> maxY;
                default -> maxZ;
            };
        }

        // Blockstates turn a model around the block's centre, the x axis first.
        Box rotated(int rotationX, int rotationY) {
            if (rotationX % 360 == 0 && rotationY % 360 == 0) {
                return this;
            }
            double[] first = turned(minX, minY, minZ, rotationX, rotationY);
            double[] second = turned(maxX, maxY, maxZ, rotationX, rotationY);
            return new Box(Math.min(first[0], second[0]), Math.min(first[1], second[1]), Math.min(first[2], second[2]),
                    Math.max(first[0], second[0]), Math.max(first[1], second[1]), Math.max(first[2], second[2]));
        }

        private static double[] turned(double x, double y, double z, int rotationX, int rotationY) {
            int[] axisX = CubeFace.turnAroundY(CubeFace.turnAroundX(new int[]{1, 0, 0}, rotationX), rotationY);
            int[] axisY = CubeFace.turnAroundY(CubeFace.turnAroundX(new int[]{0, 1, 0}, rotationX), rotationY);
            int[] axisZ = CubeFace.turnAroundY(CubeFace.turnAroundX(new int[]{0, 0, 1}, rotationX), rotationY);
            double offsetX = x - BLOCK_CENTER;
            double offsetY = y - BLOCK_CENTER;
            double offsetZ = z - BLOCK_CENTER;
            return new double[]{
                    BLOCK_CENTER + axisX[0] * offsetX + axisY[0] * offsetY + axisZ[0] * offsetZ,
                    BLOCK_CENTER + axisX[1] * offsetX + axisY[1] * offsetY + axisZ[1] * offsetZ,
                    BLOCK_CENTER + axisX[2] * offsetX + axisY[2] * offsetY + axisZ[2] * offsetZ
            };
        }
    }
}
