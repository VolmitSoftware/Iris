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

package com.volmit.iris.core.gui.components;

import com.volmit.iris.core.gui.GuiHost;

import java.awt.image.BufferedImage;

/**
 * One rendered square of the map. Besides its colours it remembers which biome or region and which region every pixel
 * shows, and optionally the terrain height, so highlighting, borders, shading and the legend never need a new render.
 * The label and height grids have a one pixel border taken from the neighbouring tiles, so edges and slopes line up
 * across tile boundaries.
 */
public final class MapTile {
    private static final double SHADING_EXAGGERATION = 1.2D;
    private static final double LIGHT_X = -1D;
    private static final double LIGHT_Y = 1.5D;
    private static final double LIGHT_Z = -1D;
    private static final double LIGHT_LENGTH = Math.sqrt(LIGHT_X * LIGHT_X + LIGHT_Y * LIGHT_Y + LIGHT_Z * LIGHT_Z);
    private static final double FLAT_LIGHT = LIGHT_Y / LIGHT_LENGTH;
    private static final double DARKEST_SHADE = 0.55D;
    private static final double BRIGHTEST_SHADE = 1.25D;
    private static final int DIM_TARGET = 0x1E1F22;
    private static final double DIM_AMOUNT = 0.72D;
    private static final double DIM_DESATURATION = 0.6D;
    private static final int HIGHLIGHT_EDGE = 0xFFFFFF;
    private static final int REGION_BORDER = 0x101114;
    private static final double REGION_BORDER_STRENGTH = 0.6D;

    private final int size;
    private final double blocksPerPixel;
    private final MapLabels labels;
    private final MapLabels.Kind subjectKind;
    private final int[] colors;
    private final short[] subjects;
    private final short[] regions;
    private final double[] heights;
    private MapStyle styledFor;
    private BufferedImage styledImage;

    MapTile(int size, double blocksPerPixel, MapLabels labels, MapLabels.Kind subjectKind, int[] colors, short[] subjects,
            short[] regions, double[] heights) {
        this.size = size;
        this.blocksPerPixel = blocksPerPixel;
        this.labels = labels;
        this.subjectKind = subjectKind;
        this.colors = colors;
        this.subjects = subjects;
        this.regions = regions;
        this.heights = heights;
    }

    public int size() {
        return size;
    }

    public MapLabels labels() {
        return labels;
    }

    /**
     * Whether the pixels name biomes or, in the region view, regions.
     */
    public MapLabels.Kind subjectKind() {
        return subjectKind;
    }

    public boolean hasHeights() {
        return heights != null;
    }

    public short subjectAt(int pixelX, int pixelZ) {
        return subjects[gridIndex(pixelX, pixelZ)];
    }

    public short regionAt(int pixelX, int pixelZ) {
        return regions[gridIndex(pixelX, pixelZ)];
    }

    public long bytes() {
        long styledBytes = (long) size * size * Integer.BYTES;
        long gridCells = (long) (size + 2) * (size + 2);
        long heightBytes = heights == null ? 0L : gridCells * Double.BYTES;
        return (long) colors.length * Integer.BYTES + gridCells * Short.BYTES * 2L + heightBytes + styledBytes;
    }

    /**
     * The tile as it looks in the given style; the last style's picture is kept, so redrawing an unchanged map is free.
     */
    public synchronized BufferedImage image(MapStyle style) {
        if (styledImage == null || !style.equals(styledFor)) {
            styledImage = GuiHost.uncachedImage(styledPixels(style), size, size);
            styledFor = style;
        }
        return styledImage;
    }

    private int[] styledPixels(MapStyle style) {
        int[] pixels = new int[size * size];
        boolean shading = style.shading() && heights != null;
        short[] highlightGrid = highlightGrid(style.highlight());
        short highlighted = highlightGrid == null
                ? MapLabels.NONE
                : labels.idOf(style.highlight().kind(), style.highlight().key());
        for (int pixelZ = 0; pixelZ < size; pixelZ++) {
            for (int pixelX = 0; pixelX < size; pixelX++) {
                int color = colors[pixelZ * size + pixelX];
                if (shading) {
                    color = shade(color, pixelX, pixelZ);
                }
                if (highlightGrid != null) {
                    color = highlight(color, highlightGrid, pixelX, pixelZ, highlighted);
                }
                if (style.regionBorders() && isRegionEdge(pixelX, pixelZ)) {
                    color = blend(color, REGION_BORDER, REGION_BORDER_STRENGTH);
                }
                pixels[pixelZ * size + pixelX] = color;
            }
        }
        return pixels;
    }

    // Lit from the north west like a relief map, so slopes read as hills and valleys at a glance.
    private int shade(int color, int pixelX, int pixelZ) {
        double slopeX = (height(pixelX + 1, pixelZ) - height(pixelX - 1, pixelZ)) / (2D * blocksPerPixel);
        double slopeZ = (height(pixelX, pixelZ + 1) - height(pixelX, pixelZ - 1)) / (2D * blocksPerPixel);
        double normalX = -slopeX * SHADING_EXAGGERATION;
        double normalZ = -slopeZ * SHADING_EXAGGERATION;
        double normalLength = Math.sqrt(normalX * normalX + 1D + normalZ * normalZ);
        double light = (normalX * LIGHT_X + LIGHT_Y + normalZ * LIGHT_Z) / (normalLength * LIGHT_LENGTH);
        double factor = Math.max(DARKEST_SHADE, Math.min(BRIGHTEST_SHADE, light / FLAT_LIGHT));
        return scale(color, factor);
    }

    // Every view knows its regions, so a region can be highlighted in a biome view too.
    private short[] highlightGrid(MapStyle.Highlight highlight) {
        if (highlight == null) {
            return null;
        }
        if (highlight.kind() == subjectKind) {
            return subjects;
        }
        return highlight.kind() == MapLabels.Kind.REGION ? regions : null;
    }

    private int highlight(int color, short[] grid, int pixelX, int pixelZ, short highlighted) {
        if (grid[gridIndex(pixelX, pixelZ)] != highlighted) {
            return dim(color);
        }
        boolean edge = grid[gridIndex(pixelX + 1, pixelZ)] != highlighted
                || grid[gridIndex(pixelX - 1, pixelZ)] != highlighted
                || grid[gridIndex(pixelX, pixelZ + 1)] != highlighted
                || grid[gridIndex(pixelX, pixelZ - 1)] != highlighted;
        return edge ? HIGHLIGHT_EDGE : color;
    }

    private boolean isRegionEdge(int pixelX, int pixelZ) {
        short region = regionAt(pixelX, pixelZ);
        return regions[gridIndex(pixelX + 1, pixelZ)] != region || regions[gridIndex(pixelX, pixelZ + 1)] != region;
    }

    private double height(int pixelX, int pixelZ) {
        return heights[gridIndex(pixelX, pixelZ)];
    }

    // Pixel coordinates run from -1 to size, the outer ring being the neighbouring tiles' edge pixels.
    private int gridIndex(int pixelX, int pixelZ) {
        return (pixelZ + 1) * (size + 2) + pixelX + 1;
    }

    private static int dim(int color) {
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;
        int gray = (red + green + blue) / 3;
        int desaturated = blend(color, (gray << 16) | (gray << 8) | gray, DIM_DESATURATION);
        return blend(desaturated, DIM_TARGET, DIM_AMOUNT);
    }

    private static int scale(int color, double factor) {
        int red = Math.min(255, (int) (((color >> 16) & 0xFF) * factor));
        int green = Math.min(255, (int) (((color >> 8) & 0xFF) * factor));
        int blue = Math.min(255, (int) ((color & 0xFF) * factor));
        return (red << 16) | (green << 8) | blue;
    }

    static int blend(int first, int second, double amount) {
        int red = (int) Math.round(((first >> 16) & 0xFF) * (1D - amount) + ((second >> 16) & 0xFF) * amount);
        int green = (int) Math.round(((first >> 8) & 0xFF) * (1D - amount) + ((second >> 8) & 0xFF) * amount);
        int blue = (int) Math.round((first & 0xFF) * (1D - amount) + (second & 0xFF) * amount);
        return (red << 16) | (green << 8) | blue;
    }
}
