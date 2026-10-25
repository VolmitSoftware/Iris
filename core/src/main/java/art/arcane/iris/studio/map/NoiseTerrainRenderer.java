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

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Draws noise terrain without a graphics card. Block columns get Minecraft's face shading and an outline around every
 * block, until blocks get too small on screen to outline. Noise measured in blocks gets see-through water up to its
 * level.
 * <p>
 * Each frame is split into horizontal bands drawn side by side, every band drawing only the pixels in its rows. Block
 * faces are found once per terrain; each frame only works out which rows they cover, so bands skip the rest cheaply.
 */
final class NoiseTerrainRenderer {
    private static final double TOP_SHADE = 1D;
    private static final double NORTH_SOUTH_SHADE = 0.8D;
    private static final double EAST_WEST_SHADE = 0.6D;
    private static final double LEAST_SIDE_CONTRAST = 0.35D;
    // The graphics card draws with these too, so both look alike.
    static final int WATER_COLOR = 0x3F76E4;
    static final double WATER_OPACITY = 0.55D;
    static final double OUTLINE_SHADE = 0.72D;
    static final double OUTLINE_LIGHTENING = 0.25D;
    static final double DARK_LUMINANCE = 70D;
    static final double LEAST_OUTLINE_SPACING_PIXELS = 4D;
    static final double FULL_OUTLINE_SPACING_PIXELS = 12D;
    static final double OUTLINE_HALF_WIDTH_PIXELS = 0.5D;
    // A few slices per worker even out slices that take longer, like bands crossing the busiest part of the terrain.
    private static final int SLICES_PER_WORKER = 2;
    // Shared edges land exactly on pixel centres now and then; a little overlap keeps them from leaving cracks.
    private static final double EDGE_TOLERANCE = 1e-6D;
    private static final double EDGE_ON_DETERMINANT = 1e-9D;

    private final TerrainLayer layer;
    private final NoiseTerrain terrain;
    private final OrbitProjection projection;
    private final int width;
    private final int[] pixels;
    private final double[] depths;
    private final FaceRows faceRows;
    private final int bandTop;
    private final int bandBottom;
    private final int[] runColors;

    private NoiseTerrainRenderer(Frame frame, TerrainLayer layer, FaceRows faceRows, int bandTop, int bandBottom) {
        this.layer = layer;
        this.terrain = layer.terrain();
        this.projection = frame.projection();
        this.width = frame.width();
        this.pixels = frame.pixels();
        this.depths = frame.depths();
        this.faceRows = faceRows;
        this.bandTop = bandTop;
        this.bandBottom = bandBottom;
        this.runColors = new int[Math.max(terrain.columns(), terrain.rows())];
    }

    static OrbitPicture render(NoiseTerrainScene scene, OrbitCamera camera, int width, int height, int coveredLeftWidth) {
        BufferedImage image = new BufferedImage(Math.max(1, width), Math.max(1, height), BufferedImage.TYPE_INT_RGB);
        Frame frame = Frame.of(image, scene, camera, coveredLeftWidth);
        List<FaceRows> faceRows = new ArrayList<>(scene.layers().size());
        for (HoledLayer layer : scene.layers()) {
            faceRows.add(FaceRows.of(frame, layer.layer(), layer.holeOfAllFillers(), scene));
        }
        inSlices(image.getHeight(), scene, (top, bottom) -> drawBand(frame, scene, faceRows, top, bottom - 1));
        return OrbitPicture.of(image, frame.depths(), frame.projection());
    }

    // Every layer draws what it has in the band's rows; the water goes over all of them last.
    private static void drawBand(Frame frame, NoiseTerrainScene scene, List<FaceRows> faceRows, int bandTop, int bandBottom) {
        frame.clearRows(bandTop, bandBottom);
        for (int layer = 0; layer < scene.layers().size(); layer++) {
            new NoiseTerrainRenderer(frame, scene.layers().get(layer).layer(), faceRows.get(layer), bandTop, bandBottom).drawBlocks();
        }
        if (scene.hasWater() && !scene.layers().isEmpty()) {
            NoiseTerrainScene.Extent extent = scene.extent();
            new NoiseTerrainRenderer(frame, scene.layers().get(0).layer(), FaceRows.NONE, bandTop, bandBottom)
                    .drawWater(extent.minX(), extent.minZ(), extent.sizeX(), extent.sizeZ(), BlockFaces.blockTop(scene.waterLevel()));
        }
    }

    // Splits {@code total} items into a few slices per worker and works on them side by side.
    private static void inSlices(int total, NoiseTerrainScene scene, SliceWork work) {
        int sliceCount = Math.min(total, scene.workerCount() * SLICES_PER_WORKER);
        List<Callable<Void>> slices = new ArrayList<>(sliceCount);
        for (int slice = 0; slice < sliceCount; slice++) {
            int from = total * slice / sliceCount;
            int to = total * (slice + 1) / sliceCount;
            slices.add(() -> {
                work.doSlice(from, to);
                return null;
            });
        }
        try {
            for (Future<Void> slice : scene.workers().invokeAll(slices)) {
                slice.get();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException error) {
            throw new IllegalStateException("Drawing the terrain failed", error.getCause());
        }
    }

    private void drawBlocks() {
        BlockFaces faces = layer.faces();
        for (int face = 0; face < faces.count(); face++) {
            if (faceRows.crosses(face, bandTop, bandBottom)) {
                drawFace(faces, face);
            }
        }
    }

    // A face stretching over several columns is still coloured and outlined column by column.
    private void drawFace(BlockFaces faces, int face) {
        BlockFaces.Facing facing = faces.facing(face);
        double shade = faceShadeAt(facing, projection.pixelsPerBlock());
        int columns = faces.columns(face);
        for (int along = 0; along < columns; along++) {
            int column = faces.firstColumn(face) + (facing.runsAlongX() ? along : 0);
            int row = faces.firstRow(face) + (facing.runsAlongX() ? 0 : along);
            runColors[along] = shade(terrain.color(column, row), shade);
        }
        fillQuad(faces.originX(face), faces.originY(face), faces.originZ(face), faces.runX(face), 0D, faces.runZ(face),
                0D, faces.reachY(face), faces.reachZ(face), facing.normalX, facing.normalY, facing.normalZ,
                runColors, columns, faces.height(face));
    }

    // How much a face's colour is darkened, with blocks drawn {@code pixelsPerBlock} wide.
    static double faceShadeAt(BlockFaces.Facing facing, double pixelsPerBlock) {
        return withContrast(faceShade(facing), blockSideContrast(pixelsPerBlock));
    }

    private static double faceShade(BlockFaces.Facing facing) {
        return switch (facing) {
            case TOP -> TOP_SHADE;
            case NORTH, SOUTH -> NORTH_SOUTH_SHADE;
            case WEST, EAST -> EAST_WEST_SHADE;
        };
    }

    // Blocks a few pixels wide show their sides as one-pixel slivers on every slope; at full contrast those read as noise.
    private static double blockSideContrast(double pixelsPerBlock) {
        return LEAST_SIDE_CONTRAST + (1D - LEAST_SIDE_CONTRAST) * outlineStrength(pixelsPerBlock);
    }

    private static double withContrast(double faceShade, double contrast) {
        return 1D - (1D - faceShade) * contrast;
    }

    // Water is drawn last, blended over whatever lies below its surface, without hiding what is behind it.
    private void drawWater(double x, double z, double sizeX, double sizeZ, double level) {
        Footprint water = new Footprint(x, level, z, sizeX, 0, 0, 0, 0, sizeZ);
        if (water.isEmpty()) {
            return;
        }
        for (int y = water.top; y <= water.bottom; y++) {
            water.spanRow(y);
            for (int pixelX = water.rowStart; pixelX <= water.rowEnd; pixelX++) {
                double alongU = water.alongU(pixelX, y);
                double alongV = water.alongV(pixelX, y);
                int index = y * width + pixelX;
                if (isInside(alongU, alongV) && water.depthAt(alongU, alongV) > depths[index]) {
                    pixels[index] = blend(WATER_COLOR, pixels[index], WATER_OPACITY);
                }
            }
        }
    }

    /**
     * Fills the parallelogram from {@code origin} along both edges, with lines {@code 1 / units} of the way along
     * each edge; no lines when {@code units} is 0. Each of the {@code unitsU} blocks along the first edge takes its
     * colour from {@code colorsAlongU}; with no units, the whole face takes the first.
     */
    private void fillQuad(double originX, double originY, double originZ, double uX, double uY, double uZ,
                          double vX, double vY, double vZ, int normalX, int normalY, int normalZ,
                          int[] colorsAlongU, int unitsU, int unitsV) {
        if (!projection.facesCamera(normalX, normalY, normalZ)) {
            return;
        }
        Footprint quad = new Footprint(originX, originY, originZ, uX, uY, uZ, vX, vY, vZ);
        if (quad.isEmpty()) {
            return;
        }
        double pixelsPerUnitU = unitsU == 0 ? 0D : quad.lengthU() / unitsU;
        double pixelsPerUnitV = unitsV == 0 ? 0D : quad.lengthV() / unitsV;
        double outlineStrengthU = outlineStrength(pixelsPerUnitU);
        double outlineStrengthV = outlineStrength(pixelsPerUnitV);
        int lastUnitU = Math.max(0, unitsU - 1);
        for (int y = quad.top; y <= quad.bottom; y++) {
            quad.spanRow(y);
            for (int x = quad.rowStart; x <= quad.rowEnd; x++) {
                double alongU = quad.alongU(x, y);
                double alongV = quad.alongV(x, y);
                if (!isInside(alongU, alongV)) {
                    continue;
                }
                int index = y * width + x;
                double depth = quad.depthAt(alongU, alongV);
                if (depth < depths[index]) {
                    continue;
                }
                depths[index] = depth;
                int color = colorsAlongU[Math.max(0, Math.min(lastUnitU, (int) (alongU * unitsU)))];
                if (isOnLine(alongU, unitsU, pixelsPerUnitU)) {
                    pixels[index] = blend(outlineOf(color), color, outlineStrengthU);
                } else if (isOnLine(alongV, unitsV, pixelsPerUnitV)) {
                    pixels[index] = blend(outlineOf(color), color, outlineStrengthV);
                } else {
                    pixels[index] = color;
                }
            }
        }
    }

    private static boolean isInside(double alongU, double alongV) {
        return alongU >= -EDGE_TOLERANCE && alongU <= 1D + EDGE_TOLERANCE && alongV >= -EDGE_TOLERANCE && alongV <= 1D + EDGE_TOLERANCE;
    }

    // Lines fade in as blocks grow on screen; dense lines at full strength would only speckle the face.
    private static double outlineStrength(double pixelsPerUnit) {
        double fadeIn = (pixelsPerUnit - LEAST_OUTLINE_SPACING_PIXELS) / (FULL_OUTLINE_SPACING_PIXELS - LEAST_OUTLINE_SPACING_PIXELS);
        return Math.max(0D, Math.min(1D, fadeIn));
    }

    // Lines closer together than a few pixels would only grey the face out, so they are left out.
    private static boolean isOnLine(double along, int units, double pixelsPerUnit) {
        if (pixelsPerUnit < LEAST_OUTLINE_SPACING_PIXELS) {
            return false;
        }
        double unitsAlong = along * units;
        return Math.abs(unitsAlong - Math.rint(unitsAlong)) * pixelsPerUnit < OUTLINE_HALF_WIDTH_PIXELS;
    }

    // Dark faces get lighter outlines, so the blocks of a black low still show.
    private static int outlineOf(int color) {
        double luminance = 0.3D * ((color >>> 16) & 0xFF) + 0.59D * ((color >>> 8) & 0xFF) + 0.11D * (color & 0xFF);
        return luminance < DARK_LUMINANCE ? blend(0xFFFFFF, color, OUTLINE_LIGHTENING) : shade(color, OUTLINE_SHADE);
    }

    private static int shade(int color, double shade) {
        return BlockPreviewColors.shade(color, shade);
    }

    private static int blend(int over, int under, double coverage) {
        return blendChannel(over, under, coverage, 16) << 16 | blendChannel(over, under, coverage, 8) << 8 | blendChannel(over, under, coverage, 0);
    }

    private static int blendChannel(int over, int under, double coverage, int shift) {
        return (int) Math.round(((over >>> shift) & 0xFF) * coverage + ((under >>> shift) & 0xFF) * (1D - coverage));
    }

    /**
     * What every band of one frame shares: the image's pixels and depths, and where the terrain lands on it.
     */
    private record Frame(OrbitProjection projection, int width, int[] pixels, double[] depths) {
        static Frame of(BufferedImage image, NoiseTerrainScene scene, OrbitCamera camera, int coveredLeftWidth) {
            OrbitProjection projection = scene.projection(camera, image.getWidth(), image.getHeight(), coveredLeftWidth);
            int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
            return new Frame(projection, image.getWidth(), pixels, new double[pixels.length]);
        }

        void clearRows(int top, int bottom) {
            int from = top * width;
            int to = (bottom + 1) * width;
            Arrays.fill(pixels, from, to, StudioTheme.EDITOR_BACKGROUND.getRGB() & 0xFFFFFF);
            Arrays.fill(depths, from, to, Double.NEGATIVE_INFINITY);
        }
    }

    /**
     * The screen rows each block face of a layer spans this frame, worked out once so every band skips the faces it
     * doesn't cross without projecting them again.
     */
    private record FaceRows(int[] tops, int[] bottoms) {
        static final FaceRows NONE = new FaceRows(new int[0], new int[0]);

        static FaceRows of(Frame frame, TerrainLayer layer, TerrainHole hole, NoiseTerrainScene scene) {
            BlockFaces faces = layer.faces();
            FaceRows rows = new FaceRows(new int[faces.count()], new int[faces.count()]);
            inSlices(faces.count(), scene, (from, to) -> rows.measure(faces, hole, frame.projection(), from, to));
            return rows;
        }

        // Faces looking away from the camera, or not showing with the hole, are never drawn, so they span no rows.
        private void measure(BlockFaces faces, TerrainHole hole, OrbitProjection projection, int fromFace, int toFace) {
            for (int face = fromFace; face < toFace; face++) {
                BlockFaces.Facing facing = faces.facing(face);
                if (!projection.facesCamera(facing.normalX, facing.normalY, facing.normalZ) || !hole.shows(faces, face)) {
                    tops[face] = Integer.MAX_VALUE;
                    bottoms[face] = Integer.MIN_VALUE;
                    continue;
                }
                double originY = projection.screenY(faces.originX(face), faces.originY(face), faces.originZ(face));
                double runY = projection.screenStepY(faces.runX(face), 0D, faces.runZ(face));
                double reachY = projection.screenStepY(0D, faces.reachY(face), faces.reachZ(face));
                tops[face] = (int) Math.floor(originY + Math.min(0D, runY) + Math.min(0D, reachY));
                bottoms[face] = (int) Math.ceil(originY + Math.max(0D, runY) + Math.max(0D, reachY));
            }
        }

        boolean crosses(int face, int top, int bottom) {
            return tops[face] <= bottom && bottoms[face] >= top;
        }
    }

    @FunctionalInterface
    private interface SliceWork {
        void doSlice(int from, int to);
    }

    /**
     * Where a parallelogram, from an origin along two edges, lands in this band: the pixels around it, and how far
     * along each edge, and how near, any pixel inside it is.
     */
    private final class Footprint {
        private final double originX;
        private final double originY;
        private final double originDepth;
        private final double edgeUX;
        private final double edgeUY;
        private final double edgeUDepth;
        private final double edgeVX;
        private final double edgeVY;
        private final double edgeVDepth;
        private final double determinant;
        private final int left;
        private final int right;
        private final int top;
        private final int bottom;
        private int rowStart;
        private int rowEnd;

        private Footprint(double x, double y, double z, double uX, double uY, double uZ, double vX, double vY, double vZ) {
            originX = projection.screenX(x, y, z);
            originY = projection.screenY(x, y, z);
            originDepth = projection.depth(x, y, z);
            edgeUX = projection.screenStepX(uX, uZ);
            edgeUY = projection.screenStepY(uX, uY, uZ);
            edgeUDepth = projection.depthStep(uX, uY, uZ);
            edgeVX = projection.screenStepX(vX, vZ);
            edgeVY = projection.screenStepY(vX, vY, vZ);
            edgeVDepth = projection.depthStep(vX, vY, vZ);
            determinant = edgeUX * edgeVY - edgeUY * edgeVX;
            left = Math.max(0, (int) Math.floor(min(originX, originX + edgeUX, originX + edgeVX, originX + edgeUX + edgeVX)));
            right = Math.min(width - 1, (int) Math.ceil(max(originX, originX + edgeUX, originX + edgeVX, originX + edgeUX + edgeVX)));
            top = Math.max(bandTop, (int) Math.floor(min(originY, originY + edgeUY, originY + edgeVY, originY + edgeUY + edgeVY)));
            bottom = Math.min(bandBottom, (int) Math.ceil(max(originY, originY + edgeUY, originY + edgeVY, originY + edgeUY + edgeVY)));
        }

        // Edge-on faces cover no pixels, and faces outside the band are another band's work.
        private boolean isEmpty() {
            return Math.abs(determinant) < EDGE_ON_DETERMINANT || top > bottom || left > right;
        }

        /**
         * Narrows {@link #rowStart} to {@link #rowEnd} to the pixels of row {@code y} the footprint can cover, so long
         * thin faces lying across the screen don't cost their whole bounding box. A pixel or so either side is kept;
         * the caller still tests every pixel.
         */
        private void spanRow(int y) {
            double fromOriginY = y + 0.5D - originY;
            double slopeU = edgeVY / determinant;
            double startU = -fromOriginY * edgeVX / determinant;
            double slopeV = -edgeUY / determinant;
            double startV = fromOriginY * edgeUX / determinant;
            double[] range = {Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY};
            keepWhere(range, slopeU, startU, 0D, 1D);
            keepWhere(range, slopeV, startV, 0D, 1D);
            rowStart = Math.max(left, (int) Math.floor(range[0] + originX - 0.5D));
            rowEnd = Math.min(right, (int) Math.ceil(range[1] + originX - 0.5D));
        }

        // Narrows the range of offsets from the origin to where {@code start + slope * offset} stays within the limits.
        private static void keepWhere(double[] range, double slope, double start, double lowest, double highest) {
            if (Math.abs(slope) < EDGE_ON_DETERMINANT) {
                if (start < lowest - EDGE_TOLERANCE || start > highest + EDGE_TOLERANCE) {
                    range[0] = Double.POSITIVE_INFINITY;
                }
                return;
            }
            double atLowest = (lowest - EDGE_TOLERANCE - start) / slope;
            double atHighest = (highest + EDGE_TOLERANCE - start) / slope;
            range[0] = Math.max(range[0], Math.min(atLowest, atHighest));
            range[1] = Math.min(range[1], Math.max(atLowest, atHighest));
        }

        private double alongU(int x, int y) {
            return ((x + 0.5D - originX) * edgeVY - (y + 0.5D - originY) * edgeVX) / determinant;
        }

        private double alongV(int x, int y) {
            return ((y + 0.5D - originY) * edgeUX - (x + 0.5D - originX) * edgeUY) / determinant;
        }

        private double depthAt(double alongU, double alongV) {
            return originDepth + alongU * edgeUDepth + alongV * edgeVDepth;
        }

        private double lengthU() {
            return Math.hypot(edgeUX, edgeUY);
        }

        private double lengthV() {
            return Math.hypot(edgeVX, edgeVY);
        }

        private static double min(double a, double b, double c, double d) {
            return Math.min(Math.min(a, b), Math.min(c, d));
        }

        private static double max(double a, double b, double c, double d) {
            return Math.max(Math.max(a, b), Math.max(c, d));
        }
    }
}
