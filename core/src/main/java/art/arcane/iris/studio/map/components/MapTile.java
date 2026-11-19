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

package art.arcane.iris.studio.map.components;

/**
 * One rendered square of the map. Besides its colours it remembers which biome or region and which region every pixel
 * shows, and optionally the terrain height, so the studio can highlight, shade and count without a new render.
 * The label and height grids have a one pixel border taken from the neighbouring tiles, so edges and slopes line up
 * across tile boundaries.
 */
public final class MapTile {
    private final int size;
    private final double blocksPerPixel;
    private final MapLabels labels;
    private final MapLabels.Kind subjectKind;
    private final int[] colors;
    private final MapStyle.HeightRange defaultHeightRange;
    private final short[] subjects;
    private final short[] regions;
    private final double[] heights;

    /**
     * @param colors the colour of every pixel, or null to colour the pixels by their height
     * @param defaultHeightRange the height colours to use until the map has measured the heights on screen
     */
    MapTile(int size, double blocksPerPixel, MapLabels labels, MapLabels.Kind subjectKind, int[] colors,
            MapStyle.HeightRange defaultHeightRange, short[] subjects, short[] regions, double[] heights) {
        this.size = size;
        this.blocksPerPixel = blocksPerPixel;
        this.labels = labels;
        this.subjectKind = subjectKind;
        this.colors = colors;
        this.defaultHeightRange = defaultHeightRange;
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

    public double heightAt(int pixelX, int pixelZ) {
        return heights[gridIndex(pixelX, pixelZ)];
    }

    /** The pixel's plain colour as 0xRRGGBB, before any shading or highlight. */
    public int colorAt(int pixelX, int pixelZ) {
        if (colors == null) {
            return MapTileRenderer.heightColor(heightAt(pixelX, pixelZ), defaultHeightRange);
        }
        return colors[pixelZ * size + pixelX];
    }

    public long bytes() {
        long gridCells = (long) (size + 2) * (size + 2);
        long heightBytes = heights == null ? 0L : gridCells * Double.BYTES;
        long colorBytes = colors == null ? 0L : (long) colors.length * Integer.BYTES;
        return colorBytes + gridCells * Short.BYTES * 2L + heightBytes;
    }

    // Pixel coordinates run from -1 to size, the outer ring being the neighbouring tiles' edge pixels.
    private int gridIndex(int pixelX, int pixelZ) {
        return (pixelZ + 1) * (size + 2) + pixelX + 1;
    }
}
