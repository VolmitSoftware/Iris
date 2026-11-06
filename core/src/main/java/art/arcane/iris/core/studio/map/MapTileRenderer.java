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
import art.arcane.iris.engine.framework.render.RenderType;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import art.arcane.iris.util.project.stream.ProceduralStream;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/** Draws {@link MapTile}s of one engine's world. Safe to use from several threads; each tile is drawn on its caller's. */
public final class MapTileRenderer {
    private final Engine engine;

    public MapTileRenderer(Engine engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    /**
     * The square of {@code size} pixels whose top left pixel samples ({@code startX}, {@code startZ}).
     *
     * @param extras what to sample besides the labels; the height view always has heights, and only a layer that
     *               {@linkplain MapLayer#hasPickNoise() has pick noise} gets it
     * @throws CancellationException as soon as {@code cancelled} answers true
     */
    public MapTile render(MapLayer layer, double startX, double startZ, double blocksPerPixel, int size,
                          Set<MapTile.Extra> extras, BooleanSupplier cancelled) {
        if (!Double.isFinite(startX) || !Double.isFinite(startZ) || !Double.isFinite(blocksPerPixel) || blocksPerPixel <= 0D) {
            throw new IllegalArgumentException("Map tile coordinates and scale must be finite");
        }
        if (size < 1) {
            throw new IllegalArgumentException("Map tile size must be positive");
        }
        Objects.requireNonNull(layer, "layer");
        Objects.requireNonNull(extras, "extras");
        Objects.requireNonNull(cancelled, "cancelled");
        checkCancelled(cancelled);

        TileGrid grid = new TileGrid(size + 2, startX - blocksPerPixel, startZ - blocksPerPixel, blocksPerPixel, cancelled);
        return layer.previews()
                ? renderPreview(layer, grid, blocksPerPixel, extras)
                : renderView(layer, grid, blocksPerPixel, extras);
    }

    private MapTile renderView(MapLayer layer, TileGrid grid, double blocksPerPixel, Set<MapTile.Extra> extras) {
        TileLabels labels = new TileLabels(engine, layer.colorType());
        IrisComplex complex = engine.getComplex();
        ProceduralStream<IrisRegion> regionStream = complex.getRegionStream();
        short[] regions = everyCell(grid, (x, z) -> labels.indexOf(regionStream.get(x, z)));
        ProceduralStream<IrisBiome> biomeStream = layer.view().biomeStream(complex);
        short[] subjects = layer.labelsRegions() ? regions : everyCell(grid, (x, z) -> labels.indexOf(biomeStream.get(x, z)));
        float[] heights = extras.contains(MapTile.Extra.HEIGHTS) || layer.view() == MapView.HEIGHT ? latticeHeights(grid) : null;
        float[] pickNoise = extras.contains(MapTile.Extra.PICK_NOISE) && layer.hasPickNoise()
                ? MapPickNoise.sample(engine, layer, grid)
                : null;
        MapLabel.Kind subjectKind = layer.labelsRegions() ? MapLabel.Kind.REGION : MapLabel.Kind.BIOME;
        return new MapTile(grid.size() - 2, blocksPerPixel, subjectKind, labels.labels(), subjects, regions, heights, pickNoise);
    }

    // Each spot's surface biome hangs on its height, which the lattice gives at a fraction of the exact heights' cost.
    // Only a coastline pixel whose smoothed height lands across the shore line can show another biome than its hover.
    private MapTile renderPreview(MapLayer layer, TileGrid grid, double blocksPerPixel, Set<MapTile.Extra> extras) {
        TileLabels labels = new TileLabels(engine, layer.colorType());
        IrisRegion region = layer.previewRegion();
        float[] heights = latticeHeights(grid);
        IrisComplex.BiomePick[] picks = previewPicks(grid, region, heights);
        short[] subjects = new short[picks.length];
        for (int cell = 0; cell < picks.length; cell++) {
            subjects[cell] = labels.indexOf(picks[cell].biome());
        }
        short[] regions = new short[picks.length];
        Arrays.fill(regions, labels.indexOf(region));
        float[] pickNoise = extras.contains(MapTile.Extra.PICK_NOISE)
                ? MapPickNoise.samplePreview(engine, region, grid, picks)
                : null;
        float[] requestedHeights = extras.contains(MapTile.Extra.HEIGHTS) ? heights : null;
        return new MapTile(grid.size() - 2, blocksPerPixel, MapLabel.Kind.BIOME, labels.labels(), subjects, regions, requestedHeights, pickNoise);
    }

    private IrisComplex.BiomePick[] previewPicks(TileGrid grid, IrisRegion region, float[] heights) {
        IrisComplex complex = engine.getComplex();
        int size = grid.size();
        IrisComplex.BiomePick[] picks = new IrisComplex.BiomePick[size * size];
        for (int gridZ = 0; gridZ < size; gridZ++) {
            checkCancelled(grid.cancelled());
            double worldZ = grid.worldZ(gridZ);
            for (int gridX = 0; gridX < size; gridX++) {
                int cell = gridZ * size + gridX;
                picks[cell] = complex.surfacePickAsIfIn(region, grid.worldX(gridX), worldZ, heights[cell]);
            }
        }
        return picks;
    }

    private float[] latticeHeights(TileGrid grid) {
        return new HeightLattice(grid, engine.getComplex().getHeightStream()).interpolatedHeights();
    }

    // A fractured child style speckles finer than any square a few samples could vouch for, so every cell is asked.
    private static short[] everyCell(TileGrid grid, CellLabeler labeler) {
        int size = grid.size();
        short[] cells = new short[size * size];
        for (int gridZ = 0; gridZ < size; gridZ++) {
            checkCancelled(grid.cancelled());
            double worldZ = grid.worldZ(gridZ);
            for (int gridX = 0; gridX < size; gridX++) {
                cells[gridZ * size + gridX] = labeler.indexAt(grid.worldX(gridX), worldZ);
            }
        }
        return cells;
    }

    static void checkCancelled(BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Map tile render cancelled");
        }
    }

    @FunctionalInterface
    private interface CellLabeler {
        short indexAt(double worldX, double worldZ);
    }

    /** The biomes and regions one tile meets, each labelled once, in the order the tile met them. */
    private static final class TileLabels {
        private final Engine engine;
        private final RenderType biomeColorType;
        private final Map<Object, Short> indices = new IdentityHashMap<>();
        private final List<MapLabel> labels = new ArrayList<>();

        private TileLabels(Engine engine, RenderType biomeColorType) {
            this.engine = engine;
            this.biomeColorType = biomeColorType;
        }

        short indexOf(IrisBiome biome) {
            return biome == null ? MapTile.NO_LABEL : indices.computeIfAbsent(biome, known -> add(MapLabel.of(engine, biome, biomeColorType)));
        }

        short indexOf(IrisRegion region) {
            return region == null ? MapTile.NO_LABEL : indices.computeIfAbsent(region, known -> add(MapLabel.of(engine, region)));
        }

        private short add(MapLabel label) {
            if (labels.size() == Short.MAX_VALUE) {
                return MapTile.NO_LABEL;
            }
            labels.add(label);
            return (short) (labels.size() - 1);
        }

        List<MapLabel> labels() {
            return List.copyOf(labels);
        }
    }
}
