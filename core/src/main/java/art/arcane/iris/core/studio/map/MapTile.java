/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.core.studio.map;

import java.util.List;

/**
 * A square of the map: what each pixel shows, not how it looks, so the studio styles it (shading, highlights, borders)
 * without asking again. The grids hold {@code (size + 2)²} cells row by row: the tile's pixels inside a ring of the
 * neighbouring tiles' edge pixels, so edges and slopes continue across tiles.
 *
 * @param subjects the index in {@code labels} of each cell's biome, or its region in the region view;
 *                 {@link #NO_LABEL} where the stream had none
 * @param regions  the index in {@code labels} of each cell's region; the same array as {@code subjects} in the region view
 * @param heights   each cell's terrain height, or null when it was not asked for
 * @param pickNoise each cell's noise value that settled its pick, 0 to 1 and NaN where nothing picked it; null when it
 *                  was not asked for or the layer has none
 */
public record MapTile(int size, double blocksPerPixel, MapLabel.Kind subjectKind, List<MapLabel> labels,
                      short[] subjects, short[] regions, float[] heights, float[] pickNoise) {
    public static final short NO_LABEL = -1;

    /** What a tile can carry besides its labels. */
    public enum Extra {
        HEIGHTS,
        PICK_NOISE
    }

    public int gridSize() {
        return size + 2;
    }

    /** The grid index of a pixel; pixel coordinates run from -1 to {@code size}, the ring being the neighbours'. */
    public int gridIndex(int pixelX, int pixelZ) {
        return (pixelZ + 1) * gridSize() + pixelX + 1;
    }

    public MapLabel subjectAt(int pixelX, int pixelZ) {
        return label(subjects[gridIndex(pixelX, pixelZ)]);
    }

    public MapLabel regionAt(int pixelX, int pixelZ) {
        return label(regions[gridIndex(pixelX, pixelZ)]);
    }

    private MapLabel label(short index) {
        return index == NO_LABEL ? null : labels.get(index);
    }
}
