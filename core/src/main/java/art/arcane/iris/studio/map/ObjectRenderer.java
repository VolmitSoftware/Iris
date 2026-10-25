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

package art.arcane.iris.studio.map;

import art.arcane.iris.studio.map.BlockModel.Face;
import art.arcane.iris.studio.map.BlockModel.TextureMapping;
import art.arcane.iris.studio.map.BlockTextures.BlockTexture;

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
    // Zoom 2 fills the picture with the object's bounding box; thumbnails keep a hair of margin around it.
    private static final OrbitCamera THUMBNAIL_CAMERA = new OrbitCamera(OrbitCamera.START.yaw(), OrbitCamera.START.pitch(), 1.9D, Vec3.ZERO);

    private final int width;
    private final int height;
    private final int[] pixels;
    private final double[] depths;
    private final boolean[] showsObject;
    private final OrbitProjection projection;
    private final int gridColor = GRID.getRGB() & 0xFFFFFF;

    private ObjectRenderer(BufferedImage image, OrbitProjection projection) {
        this.width = image.getWidth();
        this.height = image.getHeight();
        this.pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        this.depths = new double[pixels.length];
        this.showsObject = new boolean[pixels.length];
        this.projection = projection;
        Arrays.fill(pixels, StudioTheme.EDITOR_BACKGROUND.getRGB() & 0xFFFFFF);
        Arrays.fill(depths, Double.NEGATIVE_INFINITY);
    }

    static OrbitPicture render(ObjectMesh mesh, OrbitCamera camera, int width, int height, int coveredLeftWidth) {
        BufferedImage image = blankImage(width, height);
        ObjectRenderer renderer = new ObjectRenderer(image, projection(mesh, camera, image.getWidth(), image.getHeight(), coveredLeftWidth));
        renderer.drawGrid(mesh.bounds());
        renderer.drawMesh(mesh);
        return OrbitPicture.of(image, renderer.depths, renderer.projection);
    }

    static OrbitProjection projection(ObjectMesh mesh, OrbitCamera camera, int width, int height, int coveredLeftWidth) {
        int covered = Math.max(0, Math.min(Math.max(1, width) - 1, coveredLeftWidth));
        return projection(mesh.bounds(), mesh.bounds(), camera, Math.max(1, width), Math.max(1, height), covered);
    }

    /**
     * Draws {@code mesh} from the starting angle, without the floor grid, at the scale that fits an object of
     * {@code sizeToFit}. Objects drawn with the same size compare at their true sizes, standing on the same ground.
     */
    static BufferedImage renderThumbnail(ObjectMesh mesh, ObjectMesh.Bounds sizeToFit, int width, int height) {
        BufferedImage image = blankImage(width, height);
        new ObjectRenderer(image, projection(mesh.bounds(), sizeToFit, THUMBNAIL_CAMERA, image.getWidth(), image.getHeight(), 0)).drawMesh(mesh);
        return image;
    }

    /**
     * @param shown    the object drawn, centred across and standing where an object of {@code scaledAs} would
     * @param scaledAs the size that is fitted to the view
     */
    private static OrbitProjection projection(ObjectMesh.Bounds shown, ObjectMesh.Bounds scaledAs, OrbitCamera camera,
                                              int width, int height, int coveredLeftWidth) {
        Vec3 center = new Vec3((shown.minX() + shown.maxX()) / 2D, shown.minY() + scaledAs.sizeY() / 2D, (shown.minZ() + shown.maxZ()) / 2D);
        Vec3 fittedSize = new Vec3(scaledAs.sizeX(), scaledAs.sizeY(), scaledAs.sizeZ());
        return new OrbitProjection(center, fittedSize, camera, OrbitCamera.START, width, height, coveredLeftWidth);
    }

    private static BufferedImage blankImage(int width, int height) {
        return new BufferedImage(Math.max(1, width), Math.max(1, height), BufferedImage.TYPE_INT_RGB);
    }

    private void drawMesh(ObjectMesh mesh) {
        for (ObjectMesh.Quad quad : mesh.solidQuads()) {
            drawQuad(quad, Pass.SOLID);
        }
        for (ObjectMesh.Quad quad : mesh.cutAwayQuads()) {
            drawQuad(quad, Pass.CUT_AWAY_GHOST);
        }
        for (ObjectMesh.Quad quad : farthestFirst(mesh.translucentQuads())) {
            drawQuad(quad, Pass.BLENDED);
        }
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
        FloorGrid grid = FloorGrid.under(bounds);
        int reach = grid.reach();
        for (int offset = -reach; offset <= reach; offset += grid.step()) {
            drawLine(grid.centerX() + offset, grid.floorY(), grid.centerZ() - reach, grid.centerX() + offset, grid.floorY(), grid.centerZ() + reach);
            drawLine(grid.centerX() - reach, grid.floorY(), grid.centerZ() + offset, grid.centerX() + reach, grid.floorY(), grid.centerZ() + offset);
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
    record TexelRange(int first, int last, int size) {
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
     * The grid on the ground under an object, a square of lines reaching past it on every side.
     */
    record FloorGrid(int centerX, double floorY, int centerZ, int reach, int step) {
        static FloorGrid under(ObjectMesh.Bounds bounds) {
            int reach = (int) Math.ceil(bounds.largestSize() * GRID_REACH);
            int step = Math.max(1, (int) Math.ceil(reach / (double) GRID_LINES_PER_SIDE));
            return new FloorGrid(Math.floorDiv(bounds.minX() + bounds.maxX(), 2), bounds.groundY() - GRID_BELOW_OBJECT,
                    Math.floorDiv(bounds.minZ() + bounds.maxZ(), 2), reach, step);
        }

        int linesPerDirection() {
            return 2 * reach / step + 1;
        }

        int color() {
            return GRID.getRGB();
        }
    }

    private enum Pass {
        SOLID,
        CUT_AWAY_GHOST,
        BLENDED
    }
}
