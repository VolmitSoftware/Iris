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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * A block as the object viewer draws it: the textured faces of its model, in block units, already turned the way the
 * blockstate faces.
 *
 * @param faces               every face of every box in the model
 * @param coveredSides        the block's sides that a solid face fills completely, which hide whatever touches them
 * @param hidesSameBlockSides whether the model fills the whole block, so like glass it hides the sides between two of
 *                            the same blocks
 */
record BlockModel(List<Face> faces, Set<CubeFace> coveredSides, boolean hidesSameBlockSides) {
    static final BlockModel EMPTY = new BlockModel(List.of(), Set.of(), false);
    private static final int SIDE_CELLS = 16;
    private static final double ON_SIDE_TOLERANCE = 1e-4D;

    static BlockModel of(List<Face> faces) {
        Set<CubeFace> coveredSides = EnumSet.noneOf(CubeFace.class);
        boolean fillsEverySide = true;
        for (CubeFace side : CubeFace.values()) {
            if (sideIsFilled(faces, side, true)) {
                coveredSides.add(side);
            }
            fillsEverySide &= sideIsFilled(faces, side, false);
        }
        return new BlockModel(List.copyOf(faces), Set.copyOf(coveredSides), fillsEverySide);
    }

    static BlockModel solidColor(int color) {
        BlockTexture texture = BlockTexture.solid(color);
        List<Face> faces = new ArrayList<>();
        for (CubeFace side : CubeFace.values()) {
            faces.add(Face.boxSide(Vec3.ZERO, new Vec3(1D, 1D, 1D), side, texture, side));
        }
        return of(faces);
    }

    boolean isEmpty() {
        return faces.isEmpty();
    }

    BlockModel withTextures(UnaryOperator<BlockTexture> change) {
        Map<BlockTexture, BlockTexture> changed = new IdentityHashMap<>();
        List<Face> changedFaces = new ArrayList<>(faces.size());
        for (Face face : faces) {
            changedFaces.add(face.withTexture(changed.computeIfAbsent(face.texture(), change)));
        }
        return new BlockModel(List.copyOf(changedFaces), coveredSides, hidesSameBlockSides);
    }

    // The side is split into a grid, and every cell has to lie under a face pressed flat against it.
    private static boolean sideIsFilled(List<Face> faces, CubeFace side, boolean onlySolidFaces) {
        boolean[] filled = new boolean[SIDE_CELLS * SIDE_CELLS];
        for (Face face : faces) {
            if (face.liesOn(side) && (!onlySolidFaces || face.texture().isFullyOpaque())) {
                face.fillCells(side, filled, SIDE_CELLS);
            }
        }
        for (boolean cell : filled) {
            if (!cell) {
                return false;
            }
        }
        return true;
    }

    /**
     * A parallelogram from {@code origin} along {@code edgeU} and {@code edgeV}. The texture point at the origin and
     * how it moves along each edge are given as fractions of the texture.
     *
     * @param normal   the way the face looks out; it is hidden when seen from behind
     * @param cullFace the block side whose neighbour hides this face when it covers that side, or null
     */
    record Face(Vec3 origin, Vec3 edgeU, Vec3 edgeV, Vec3 normal, BlockTexture texture, TextureMapping mapping,
                double shade, CubeFace cullFace) {

        // Laid out as seen from outside: u to the right and v downwards, matching Minecraft's UVs for a whole texture.
        static Face boxSide(Vec3 from, Vec3 to, CubeFace side, BlockTexture texture, CubeFace cullFace) {
            Vec3[] corners = BoxCorners.of(from, to, side);
            return new Face(corners[0], corners[3].minus(corners[0]), corners[1].minus(corners[0]), side.direction(),
                    texture, TextureMapping.WHOLE, side.shade(), cullFace);
        }

        Face withTexture(BlockTexture changedTexture) {
            return new Face(origin, edgeU, edgeV, normal, changedTexture, mapping, shade, cullFace);
        }

        Face transformed(Transform transform) {
            Vec3 turnedNormal = transform.direction(normal);
            double length = Math.sqrt(turnedNormal.x() * turnedNormal.x() + turnedNormal.y() * turnedNormal.y() + turnedNormal.z() * turnedNormal.z());
            return new Face(transform.point(origin), transform.direction(edgeU), transform.direction(edgeV),
                    length == 0D ? turnedNormal : turnedNormal.times(1D / length), texture, mapping, shade,
                    cullFace == null ? null : CubeFace.nearest(transform.direction(cullFace.direction())));
        }

        Face withCullFace(CubeFace side) {
            return new Face(origin, edgeU, edgeV, normal, texture, mapping, shade, side);
        }

        Face withShade(double changedShade) {
            return new Face(origin, edgeU, edgeV, normal, texture, mapping, changedShade, cullFace);
        }

        private boolean liesOn(CubeFace side) {
            int axis = side.axis();
            double plane = side.pointsPositive() ? 1D : 0D;
            return CubeFace.nearest(normal) == side
                    && Math.abs(normal.component(axis)) > 1D - ON_SIDE_TOLERANCE
                    && Math.abs(origin.component(axis) - plane) < ON_SIDE_TOLERANCE
                    && Math.abs(edgeU.component(axis)) < ON_SIDE_TOLERANCE
                    && Math.abs(edgeV.component(axis)) < ON_SIDE_TOLERANCE;
        }

        private void fillCells(CubeFace side, boolean[] filled, int cells) {
            int firstAxis = side.axis() == 0 ? 1 : 0;
            int secondAxis = side.axis() == 2 ? 1 : 2;
            double[] firstRange = range(firstAxis);
            double[] secondRange = range(secondAxis);
            for (int first = 0; first < cells; first++) {
                double firstCenter = (first + 0.5D) / cells;
                if (firstCenter < firstRange[0] || firstCenter > firstRange[1]) {
                    continue;
                }
                for (int second = 0; second < cells; second++) {
                    double secondCenter = (second + 0.5D) / cells;
                    if (secondCenter >= secondRange[0] && secondCenter <= secondRange[1]) {
                        filled[first * cells + second] = true;
                    }
                }
            }
        }

        private double[] range(int axis) {
            double start = origin.component(axis);
            double alongU = edgeU.component(axis);
            double alongV = edgeV.component(axis);
            double low = start + Math.min(0D, alongU) + Math.min(0D, alongV);
            double high = start + Math.max(0D, alongU) + Math.max(0D, alongV);
            return new double[]{low, high};
        }
    }

    /**
     * Where a face's corner sits in its texture and how the texture point moves along the face's two edges, all as
     * fractions of the texture's width and height.
     */
    record TextureMapping(double u, double v, double uAlongU, double vAlongU, double uAlongV, double vAlongV) {
        static final TextureMapping WHOLE = new TextureMapping(0D, 0D, 1D, 0D, 0D, 1D);
    }

    /**
     * A box side's corners in Minecraft's order: top left, bottom left, bottom right and top right, as seen from
     * outside the box.
     */
    static final class BoxCorners {
        private BoxCorners() {
        }

        static Vec3[] of(Vec3 from, Vec3 to, CubeFace side) {
            double minX = from.x();
            double minY = from.y();
            double minZ = from.z();
            double maxX = to.x();
            double maxY = to.y();
            double maxZ = to.z();
            return switch (side) {
                case DOWN -> corners(minX, minY, maxZ, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ);
                case UP -> corners(minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ);
                case NORTH -> corners(maxX, maxY, minZ, maxX, minY, minZ, minX, minY, minZ, minX, maxY, minZ);
                case SOUTH -> corners(minX, maxY, maxZ, minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ);
                case WEST -> corners(minX, maxY, minZ, minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ);
                case EAST -> corners(maxX, maxY, maxZ, maxX, minY, maxZ, maxX, minY, minZ, maxX, maxY, minZ);
            };
        }

        private static Vec3[] corners(double... coordinates) {
            Vec3[] corners = new Vec3[4];
            for (int corner = 0; corner < 4; corner++) {
                corners[corner] = new Vec3(coordinates[corner * 3], coordinates[corner * 3 + 1], coordinates[corner * 3 + 2]);
            }
            return corners;
        }
    }
}
