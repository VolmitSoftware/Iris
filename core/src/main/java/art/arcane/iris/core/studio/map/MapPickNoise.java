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

import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.world.biome.InferredType;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.region.IrisRegion;

/**
 * The noise value that settled each spot's pick, from 0 to 1: the region noise in the region view; in the biome view
 * and a region preview the last noise that picked the biome, its list's or its last child's. NaN where nothing was
 * picked, as on an image map.
 */
final class MapPickNoise {
    private MapPickNoise() {
    }

    /** The pick noise of a layer that previews nothing. */
    static float[] sample(Engine engine, MapLayer layer, TileGrid grid) {
        NoiseAt noise = noiseOf(engine, layer);
        return everyCell(grid, (cell, x, z) -> noise.at(x, z));
    }

    /** The pick noise of a region preview, from the surface picks the tile already made for each of its cells. */
    static float[] samplePreview(Engine engine, IrisRegion region, TileGrid grid, IrisComplex.BiomePick[] picks) {
        IrisComplex complex = engine.getComplex();
        IrisBiome focus = complex.getFocusBiome();
        if (focus != null) {
            return everyCell(grid, (cell, x, z) -> ChildPicks.settlingNoise(complex, focus, x, z, Double.NaN));
        }
        return everyCell(grid, (cell, x, z) -> listNoise(complex, region, picks[cell].list(), x, z));
    }

    private static float[] everyCell(TileGrid grid, CellNoise noise) {
        int size = grid.size();
        float[] cells = new float[size * size];
        for (int gridZ = 0; gridZ < size; gridZ++) {
            MapTileRenderer.checkCancelled(grid.cancelled());
            double worldZ = grid.worldZ(gridZ);
            for (int gridX = 0; gridX < size; gridX++) {
                int cell = gridZ * size + gridX;
                cells[cell] = (float) noise.at(cell, grid.worldX(gridX), worldZ);
            }
        }
        return cells;
    }

    private static NoiseAt noiseOf(Engine engine, MapLayer layer) {
        IrisComplex complex = engine.getComplex();
        if (layer.view() == MapView.REGIONS) {
            return (x, z) -> complex.getFocusRegion() != null || complex.getImageMapRuntime().sampleRegion(x, z) != null
                    ? Double.NaN
                    : complex.getRegionStyleStream().get(x, z);
        }
        return (x, z) -> baseBiomeNoise(complex, x, z);
    }

    private static double baseBiomeNoise(IrisComplex complex, double x, double z) {
        IrisBiome focus = complex.getFocusBiome();
        if (focus != null) {
            return ChildPicks.settlingNoise(complex, focus, x, z, Double.NaN);
        }
        if (complex.getImageMapRuntime().sampleBiome(x, z) != null) {
            return Double.NaN;
        }
        IrisRegion region = complex.getRegionStream().get(x, z);
        return region == null ? Double.NaN : listNoise(complex, region, complex.getBridgeStream().get(x, z), x, z);
    }

    private static double listNoise(IrisComplex complex, IrisRegion region, InferredType list, double x, double z) {
        IrisBiome picked = complex.biomePicks(region, list).get(x, z);
        double noise = complex.biomePickNoise(region, list).get(x, z);
        return picked == null ? Double.NaN : ChildPicks.settlingNoise(complex, picked, x, z, noise);
    }

    @FunctionalInterface
    private interface NoiseAt {
        double at(double x, double z);
    }

    @FunctionalInterface
    private interface CellNoise {
        double at(int cell, double x, double z);
    }
}
