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
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
     * @param withHeights also sample terrain heights, for shading; the height view always has them
     * @throws CancellationException as soon as {@code cancelled} answers true
     */
    public MapTile render(MapView view, double startX, double startZ, double blocksPerPixel, int size,
                          boolean withHeights, BooleanSupplier cancelled) {
        if (!Double.isFinite(startX) || !Double.isFinite(startZ) || !Double.isFinite(blocksPerPixel) || blocksPerPixel <= 0D) {
            throw new IllegalArgumentException("Map tile coordinates and scale must be finite");
        }
        if (size < 1) {
            throw new IllegalArgumentException("Map tile size must be positive");
        }
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(cancelled, "cancelled");
        checkCancelled(cancelled);

        TileGrid grid = new TileGrid(size + 2, startX - blocksPerPixel, startZ - blocksPerPixel, blocksPerPixel, cancelled);
        TileLabels labels = new TileLabels(engine, view.colorType());
        short[] regions = sampleRegions(grid, labels);
        short[] subjects = view.labelsRegions() ? regions : sampleBiomes(grid, labels, view.biomeStream(engine.getComplex()));
        float[] heights = withHeights || view == MapView.HEIGHT
                ? new HeightLattice(grid, engine.getComplex().getHeightStream()).interpolatedHeights()
                : null;
        MapLabel.Kind subjectKind = view.labelsRegions() ? MapLabel.Kind.REGION : MapLabel.Kind.BIOME;
        return new MapTile(size, blocksPerPixel, subjectKind, labels.labels(), subjects, regions, heights);
    }

    private static short[] sampleRegions(TileGrid grid, TileLabels labels) {
        ProceduralStream<IrisRegion> stream = labels.engine.getComplex().getRegionStream();
        return everyCell(grid, (x, z) -> labels.indexOf(stream.get(x, z)));
    }

    private static short[] sampleBiomes(TileGrid grid, TileLabels labels, ProceduralStream<IrisBiome> stream) {
        return everyCell(grid, (x, z) -> labels.indexOf(stream.get(x, z)));
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
