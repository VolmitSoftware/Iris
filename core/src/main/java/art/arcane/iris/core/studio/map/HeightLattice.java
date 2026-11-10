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

import art.arcane.iris.core.studio.noise.CatmullRom;
import art.arcane.iris.util.project.stream.ProceduralStream;

/**
 * Terrain heights from samples on a coarse lattice, smoothly interpolated in between. Iris heights are costly to
 * sample, and shading turns the steps of cheaper shortcuts into visible creases, so a smooth curve through fewer exact
 * samples looks better and draws an order of magnitude faster than sampling every cell. The lattice lines up across
 * tiles, so slopes continue seamlessly from one tile into the next.
 */
final class HeightLattice {
    static final int LATTICE_PIXELS = 4;
    private static final int LATTICE_MARGIN = 2;
    private static final int CURVE_POINTS = 4;

    private final TileGrid grid;
    private final ProceduralStream<Double> stream;
    private final int pointsPerSide;
    private final double[] points;

    HeightLattice(TileGrid grid, ProceduralStream<Double> stream) {
        this.grid = grid;
        this.stream = stream;
        this.pointsPerSide = (grid.size() - 2) / LATTICE_PIXELS + LATTICE_MARGIN * 2 + 1;
        this.points = new double[pointsPerSide * pointsPerSide];
    }

    float[] interpolatedHeights() {
        samplePoints();
        int size = grid.size();
        float[] heights = new float[size * size];
        for (int gridZ = 0; gridZ < size; gridZ++) {
            for (int gridX = 0; gridX < size; gridX++) {
                heights[gridZ * size + gridX] = (float) heightAt(gridX - 1, gridZ - 1);
            }
        }
        return heights;
    }

    // Lattice point 0 sits on the tile's first pixel; the margin covers the ring and the curve's reach.
    private void samplePoints() {
        double latticeStep = grid.step() * LATTICE_PIXELS;
        double firstX = grid.worldX(1) - LATTICE_MARGIN * latticeStep;
        double firstZ = grid.worldZ(1) - LATTICE_MARGIN * latticeStep;
        for (int pointZ = 0; pointZ < pointsPerSide; pointZ++) {
            MapTileRenderer.checkCancelled(grid.cancelled());
            for (int pointX = 0; pointX < pointsPerSide; pointX++) {
                points[pointZ * pointsPerSide + pointX] = stream.getDouble(firstX + pointX * latticeStep, firstZ + pointZ * latticeStep);
            }
        }
    }

    private double heightAt(int pixelX, int pixelZ) {
        int cellX = Math.floorDiv(pixelX, LATTICE_PIXELS);
        int cellZ = Math.floorDiv(pixelZ, LATTICE_PIXELS);
        double fractionX = (pixelX - cellX * LATTICE_PIXELS) / (double) LATTICE_PIXELS;
        double fractionZ = (pixelZ - cellZ * LATTICE_PIXELS) / (double) LATTICE_PIXELS;
        int baseX = cellX + LATTICE_MARGIN;
        int baseZ = cellZ + LATTICE_MARGIN;
        double[] rows = new double[CURVE_POINTS];
        for (int row = 0; row < CURVE_POINTS; row++) {
            int offset = (baseZ - 1 + row) * pointsPerSide + baseX - 1;
            rows[row] = CatmullRom.between(points[offset], points[offset + 1], points[offset + 2], points[offset + 3], fractionX);
        }
        return CatmullRom.between(rows[0], rows[1], rows[2], rows[3], fractionZ);
    }
}
