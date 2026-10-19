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
import com.volmit.iris.core.gui.BlockModel.TextureMapping;
import com.volmit.iris.core.gui.BlockTextures.BlockTexture;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Draws an object mesh with an orthographic camera into a picture, without a graphics card: each quad is filled pixel
 * by pixel, and a depth buffer keeps the nearest one. A cut-away part's faint ghost only fills pixels the object
 * leaves empty, so it never hazes over what is shown. See-through quads come last, farthest first, each blended over
 * what is already drawn.
 */
final class ObjectRenderer {
    private static final Color GRID = new ThemeColor(0xE3E4E8, 0x34373C);
    private static final int GRID_LINES_PER_SIDE = 25;
    private static final double GRID_REACH = 0.7D;
    private static final double GRID_BELOW_OBJECT = 0.035D;
    // Shared quad edges land exactly on pixel centres now and then; a little overlap keeps them from leaving cracks.
    private static final double EDGE_TOLERANCE = 1e-6D;
    private static final double EDGE_ON_DETERMINANT = 1e-9D;
    // A face's own texels end a hair inside its UV rectangle, so rounding never picks the texel next to it.
    private static final double TEXEL_EDGE_MARGIN = 1e-6D;
    private static final double FULL_ALPHA = 255D;

    private final int width;
    private final int height;
    private final int[] pixels;
    private final double[] depths;
    private final boolean[] showsObject;
    private final Projection projection;
    private final int gridColor = GRID.getRGB() & 0xFFFFFF;

    private ObjectRenderer(ObjectMesh mesh, ObjectCamera camera, BufferedImage image, int coveredLeftWidth) {
        this.width = image.getWidth();
        this.height = image.getHeight();
        this.pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        this.depths = new double[pixels.length];
        this.showsObject = new boolean[pixels.length];
        this.projection = new Projection(mesh.bounds(), camera, width, height, coveredLeftWidth);
    }

    /**
     * @param coveredLeftWidth how much of the picture's left side something floats over, so the object is centred and
     *                         fitted in the rest
     */
    static BufferedImage render(ObjectMesh mesh, ObjectCamera camera, int width, int height, int coveredLeftWidth) {
        BufferedImage image = new BufferedImage(Math.max(1, width), Math.max(1, height), BufferedImage.TYPE_INT_RGB);
        ObjectRenderer renderer = new ObjectRenderer(mesh, camera, image, Math.max(0, Math.min(width - 1, coveredLeftWidth)));
        Arrays.fill(renderer.pixels, StudioTheme.EDITOR_BACKGROUND.getRGB() & 0xFFFFFF);
        Arrays.fill(renderer.depths, Double.NEGATIVE_INFINITY);
        renderer.drawGrid(mesh.bounds());
        for (ObjectMesh.Quad quad : mesh.solidQuads()) {
            renderer.drawQuad(quad, Pass.SOLID);
        }
        for (ObjectMesh.Quad quad : mesh.cutAwayQuads()) {
            renderer.drawQuad(quad, Pass.CUT_AWAY_GHOST);
        }
        for (ObjectMesh.Quad quad : renderer.farthestFirst(mesh.translucentQuads())) {
            renderer.drawQuad(quad, Pass.BLENDED);
        }
        return image;
    }

    private List<ObjectMesh.Quad> farthestFirst(List<ObjectMesh.Quad> quads) {
        List<ObjectMesh.Quad> sorted = new ArrayList<>(quads);
        sorted.sort(Comparator.comparingDouble(this::centerDepth));
        return sorted;
    }

    private double centerDepth(ObjectMesh.Quad quad) {
        Face face = quad.face();
        return projection.depth(
                quad.originX() + (face.edgeU().x() + face.edgeV().x()) / 2D,
                quad.originY() + (face.edgeU().y() + face.edgeV().y()) / 2D,
                quad.originZ() + (face.edgeU().z() + face.edgeV().z()) / 2D);
    }

    private void drawGrid(ObjectMesh.Bounds bounds) {
        int reach = (int) Math.ceil(bounds.largestSize() * GRID_REACH);
        int step = Math.max(1, (int) Math.ceil(reach / (double) GRID_LINES_PER_SIDE));
        double floor = bounds.minY() - GRID_BELOW_OBJECT;
        int centerX = Math.floorDiv(bounds.minX() + bounds.maxX(), 2);
        int centerZ = Math.floorDiv(bounds.minZ() + bounds.maxZ(), 2);
        for (int offset = -reach; offset <= reach; offset += step) {
            drawLine(centerX + offset, floor, centerZ - reach, centerX + offset, floor, centerZ + reach);
            drawLine(centerX - reach, floor, centerZ + offset, centerX + reach, floor, centerZ + offset);
        }
    }

    private void drawLine(double fromX, double fromY, double fromZ, double toX, double toY, double toZ) {
        double startX = projection.screenX(fromX, fromY, fromZ);
        double startY = projection.screenY(fromX, fromY, fromZ);
        double startDepth = projection.depth(fromX, fromY, fromZ);
        double endX = projection.screenX(toX, toY, toZ);
        double endY = projection.screenY(toX, toY, toZ);
        double endDepth = projection.depth(toX, toY, toZ);
        int steps = (int) Math.ceil(Math.max(Math.abs(endX - startX), Math.abs(endY - startY)));
        for (int step = 0; step <= steps; step++) {
            double progress = steps == 0 ? 0D : step / (double) steps;
            int x = (int) (startX + (endX - startX) * progress);
            int y = (int) (startY + (endY - startY) * progress);
            if (x < 0 || y < 0 || x >= width || y >= height) {
                continue;
            }
            int index = y * width + x;
            double depth = startDepth + (endDepth - startDepth) * progress;
            if (depth > depths[index]) {
                depths[index] = depth;
                pixels[index] = gridColor;
            }
        }
    }

    // Later faces win ties, so an overlay drawn on the same box, like grass on a grass block's side, shows on top.
    private void drawQuad(ObjectMesh.Quad quad, Pass pass) {
        Face face = quad.face();
        if (!projection.facesCamera(face.normal().x(), face.normal().y(), face.normal().z())) {
            return;
        }
        Vec3 u = face.edgeU();
        Vec3 v = face.edgeV();
        double originX = projection.screenX(quad.originX(), quad.originY(), quad.originZ());
        double originY = projection.screenY(quad.originX(), quad.originY(), quad.originZ());
        double originDepth = projection.depth(quad.originX(), quad.originY(), quad.originZ());
        double edgeUX = projection.screenStepX(u.x(), u.z());
        double edgeUY = projection.screenStepY(u.x(), u.y(), u.z());
        double edgeUDepth = projection.depthStep(u.x(), u.y(), u.z());
        double edgeVX = projection.screenStepX(v.x(), v.z());
        double edgeVY = projection.screenStepY(v.x(), v.y(), v.z());
        double edgeVDepth = projection.depthStep(v.x(), v.y(), v.z());
        double determinant = edgeUX * edgeVY - edgeUY * edgeVX;
        if (Math.abs(determinant) < EDGE_ON_DETERMINANT) {
            return;
        }

        int left = Math.max(0, (int) Math.floor(min4(originX, originX + edgeUX, originX + edgeVX, originX + edgeUX + edgeVX)));
        int right = Math.min(width - 1, (int) Math.ceil(max4(originX, originX + edgeUX, originX + edgeVX, originX + edgeUX + edgeVX)));
        int top = Math.max(0, (int) Math.floor(min4(originY, originY + edgeUY, originY + edgeVY, originY + edgeUY + edgeVY)));
        int bottom = Math.min(height - 1, (int) Math.ceil(max4(originY, originY + edgeUY, originY + edgeVY, originY + edgeUY + edgeVY)));
        if (left > right || top > bottom) {
            return;
        }

        BlockTexture texture = face.texture();
        TextureMapping mapping = face.mapping();
        TexelRange columns = TexelRange.of(mapping.u(), mapping.uAlongU(), mapping.uAlongV(), texture.width());
        TexelRange rows = TexelRange.of(mapping.v(), mapping.vAlongU(), mapping.vAlongV(), texture.height());
        int[] texels = texture.pixels();
        boolean[] opaque = texture.opaque();
        int[] alphas = texture.alphas();
        double shade = face.shade();
        double alongUPerPixelX = edgeVY / determinant;
        double alongVPerPixelX = -edgeUY / determinant;
        for (int y = top; y <= bottom; y++) {
            double offsetX = left + 0.5D - originX;
            double offsetY = y + 0.5D - originY;
            double alongU = (offsetX * edgeVY - offsetY * edgeVX) / determinant;
            double alongV = (offsetY * edgeUX - offsetX * edgeUY) / determinant;
            int rowStart = y * width;
            for (int x = left; x <= right; x++, alongU += alongUPerPixelX, alongV += alongVPerPixelX) {
                if (alongU < -EDGE_TOLERANCE || alongU > 1D + EDGE_TOLERANCE || alongV < -EDGE_TOLERANCE || alongV > 1D + EDGE_TOLERANCE) {
                    continue;
                }
                int index = rowStart + x;
                double depth = originDepth + alongU * edgeUDepth + alongV * edgeVDepth;
                if (depth < depths[index] || (pass == Pass.CUT_AWAY_GHOST && showsObject[index])) {
                    continue;
                }
                int texelX = columns.texelAt(mapping.u() + alongU * mapping.uAlongU() + alongV * mapping.uAlongV());
                int texelY = rows.texelAt(mapping.v() + alongU * mapping.vAlongU() + alongV * mapping.vAlongV());
                int texel = texelY * texture.width() + texelX;
                if (!opaque[texel]) {
                    continue;
                }
                int color = BlockPreviewColors.shade(texels[texel], shade);
                if (pass != Pass.SOLID) {
                    pixels[index] = blend(color, pixels[index], (alphas == null ? 1D : alphas[texel] / FULL_ALPHA) * quad.opacity());
                    continue;
                }
                depths[index] = depth;
                pixels[index] = color;
                showsObject[index] = true;
            }
        }
    }

    private static int blend(int over, int under, double coverage) {
        return blendChannel(over, under, coverage, 16) << 16 | blendChannel(over, under, coverage, 8) << 8 | blendChannel(over, under, coverage, 0);
    }

    private static int blendChannel(int over, int under, double coverage, int shift) {
        return (int) Math.round(((over >>> shift) & 0xFF) * coverage + ((under >>> shift) & 0xFF) * (1D - coverage));
    }

    private static double min4(double a, double b, double c, double d) {
        return Math.min(Math.min(a, b), Math.min(c, d));
    }

    private static double max4(double a, double b, double c, double d) {
        return Math.max(Math.max(a, b), Math.max(c, d));
    }

    /**
     * The texels a face covers along one texture axis, so a pixel on the face's edge never takes its neighbour's.
     */
    private record TexelRange(int first, int last, int size) {
        static TexelRange of(double start, double alongU, double alongV, int size) {
            double low = start + Math.min(0D, alongU) + Math.min(0D, alongV);
            double high = start + Math.max(0D, alongU) + Math.max(0D, alongV);
            int first = clamp((int) Math.floor(low * size + TEXEL_EDGE_MARGIN), size);
            int last = Math.max(first, clamp((int) Math.ceil(high * size - TEXEL_EDGE_MARGIN) - 1, size));
            return new TexelRange(first, last, size);
        }

        int texelAt(double fraction) {
            int texel = (int) Math.floor(fraction * size);
            return texel < first ? first : Math.min(texel, last);
        }

        private static int clamp(int texel, int size) {
            return Math.max(0, Math.min(size - 1, texel));
        }
    }

    /**
     * Turns block positions into pixels. Larger depth is nearer the camera.
     */
    private static final class Projection {
        private final double centerX;
        private final double centerY;
        private final double centerZ;
        private final double cosYaw;
        private final double sinYaw;
        private final double cosPitch;
        private final double sinPitch;
        private final double pixelsPerBlock;
        private final double screenCenterX;
        private final double screenCenterY;

        private Projection(ObjectMesh.Bounds bounds, ObjectCamera camera, int width, int height, int coveredLeftWidth) {
            this.centerX = (bounds.minX() + bounds.maxX()) / 2D;
            this.centerY = (bounds.minY() + bounds.maxY()) / 2D;
            this.centerZ = (bounds.minZ() + bounds.maxZ()) / 2D;
            this.cosYaw = Math.cos(camera.yaw());
            this.sinYaw = Math.sin(camera.yaw());
            this.cosPitch = Math.cos(camera.pitch());
            this.sinPitch = Math.sin(camera.pitch());
            int visibleWidth = width - coveredLeftWidth;
            double fit = fitAtStartingAngle(bounds, visibleWidth / (double) height);
            this.pixelsPerBlock = camera.zoom() * fit * height / 2D / bounds.largestSize();
            this.screenCenterX = coveredLeftWidth + visibleWidth / 2D + camera.panX() * visibleWidth / 2D;
            this.screenCenterY = height / 2D - camera.panY() * height / 2D;
        }

        // How far to scale the object down so it fits the view at the starting angle; orbiting keeps the scale.
        private static double fitAtStartingAngle(ObjectMesh.Bounds bounds, double aspect) {
            double size = bounds.largestSize();
            double width = bounds.sizeX() / size;
            double height = bounds.sizeY() / size;
            double depth = bounds.sizeZ() / size;
            double yaw = ObjectCamera.START.yaw();
            double pitch = ObjectCamera.START.pitch();
            double projectedWidth = Math.abs(Math.cos(yaw)) * width + Math.abs(Math.sin(yaw)) * depth;
            double projectedDepth = Math.abs(Math.sin(yaw)) * width + Math.abs(Math.cos(yaw)) * depth;
            double projectedHeight = Math.abs(Math.cos(pitch)) * height + Math.abs(Math.sin(pitch)) * projectedDepth;
            return Math.min(1D, Math.min(aspect / projectedWidth, 1D / projectedHeight));
        }

        double screenX(double x, double y, double z) {
            return screenCenterX + screenStepX(x - centerX, z - centerZ);
        }

        double screenY(double x, double y, double z) {
            return screenCenterY + screenStepY(x - centerX, y - centerY, z - centerZ);
        }

        double depth(double x, double y, double z) {
            return depthStep(x - centerX, y - centerY, z - centerZ);
        }

        double screenStepX(double x, double z) {
            return (cosYaw * x - sinYaw * z) * pixelsPerBlock;
        }

        double screenStepY(double x, double y, double z) {
            double towardCamera = sinYaw * x + cosYaw * z;
            return -(cosPitch * y - sinPitch * towardCamera) * pixelsPerBlock;
        }

        double depthStep(double x, double y, double z) {
            double towardCamera = sinYaw * x + cosYaw * z;
            return sinPitch * y + cosPitch * towardCamera;
        }

        boolean facesCamera(double normalX, double normalY, double normalZ) {
            return depthStep(normalX, normalY, normalZ) > 0D;
        }
    }

    private enum Pass {
        SOLID,
        CUT_AWAY_GHOST,
        BLENDED
    }
}
