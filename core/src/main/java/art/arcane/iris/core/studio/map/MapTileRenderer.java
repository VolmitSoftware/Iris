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
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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
    private final GeneratedChunks generated;

    public MapTileRenderer(Engine engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.generated = new GeneratedChunks(engine);
    }

    /**
     * The square of {@code size} pixels whose top left pixel samples ({@code startX}, {@code startZ}).
     *
     * @param extras what to sample besides the labels; the height view always has heights
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

    /**
     * {@code tile}, drawn from ({@code startX}, {@code startZ}), with the block on top of each of its columns from
     * {@code source}, and the solid blocks raised above it. Where the tile carries heights they become the heights of
     * that ground: the terrain's top as generated, under what stands on it.
     *
     * @param withShape whether the tile also carries the ground's shape in 3D, where the source knows it: every block
     *                  seen from the open air in a tile of a block a pixel, the runs of solid blocks of each cell's area
     *                  in a coarser one
     * @throws IllegalArgumentException when the tile is coarser than the source is drawn at
     * @throws CancellationException    as soon as {@code cancelled} answers true
     */
    public MapTile withBlocks(MapTile tile, double startX, double startZ, BlockSource source, boolean withShape, BooleanSupplier cancelled) {
        double step = tile.blocksPerPixel();
        if (step > source.largestBlocksPerPixel()) {
            throw new IllegalArgumentException("Blocks from " + source.id() + " are drawn at " + source.largestBlocksPerPixel()
                    + " blocks per pixel or finer");
        }
        TileGrid grid = new TileGrid(tile.gridSize(), startX - step, startZ - step, step, cancelled);
        ColumnTops tops = topsOf(source).prepared(grid);
        int cells = grid.size() * grid.size();
        short[] ground = new short[cells];
        short[] cover = new short[cells];
        short[] raisedCounts = new short[cells];
        ShortArrayList raisedY = new ShortArrayList();
        ShortArrayList raised = new ShortArrayList();
        float[] heights = tile.heights() == null ? null : new float[cells];
        BlockNames names = new BlockNames();
        for (int gridZ = 0; gridZ < grid.size(); gridZ++) {
            checkCancelled(cancelled);
            int worldZ = (int) Math.floor(grid.worldZ(gridZ));
            for (int gridX = 0; gridX < grid.size(); gridX++) {
                int cell = gridZ * grid.size() + gridX;
                ColumnTops.ColumnTop top = tops.at((int) Math.floor(grid.worldX(gridX)), worldZ);
                ground[cell] = names.indexOf(top.ground());
                cover[cell] = names.indexOf(top.cover());
                ColumnTops.Raised raisedHere = top.raised();
                raisedCounts[cell] = (short) raisedHere.count();
                raisedY.addElements(raisedY.size(), raisedHere.ys());
                for (String block : raisedHere.blocks()) {
                    raised.add(names.indexOf(block));
                }
                if (heights != null) {
                    heights[cell] = top.groundY();
                }
            }
        }
        MapTile.Voxels voxels = withShape && step == 1D ? voxelsOf(tops, startX, startZ, tile.size(), names) : null;
        MapTile.Runs runs = withShape && step > 1D ? runsOf(tops, grid, names) : null;
        MapTile.Blocks blocks = new MapTile.Blocks(names.names(), ground, cover, raisedCounts, raisedY.toShortArray(), raised.toShortArray(),
                voxels, runs);
        return tile.withBlocks(blocks, heights);
    }

    private static MapTile.Runs runsOf(ColumnTops tops, TileGrid grid, BlockNames names) {
        short[] counts = new short[grid.size() * grid.size()];
        ShortArrayList bottoms = new ShortArrayList();
        ShortArrayList runTops = new ShortArrayList();
        ShortArrayList blocks = new ShortArrayList();
        ShortArrayList covers = new ShortArrayList();
        ByteArrayList coverShares = new ByteArrayList();
        for (int gridZ = 0; gridZ < grid.size(); gridZ++) {
            int worldZ = (int) Math.floor(grid.worldZ(gridZ));
            for (int gridX = 0; gridX < grid.size(); gridX++) {
                ColumnTops.AreaRuns area = tops.runsAt((int) Math.floor(grid.worldX(gridX)), worldZ);
                if (area == null) {
                    return null;
                }
                counts[gridZ * grid.size() + gridX] = (short) area.tops().length;
                bottoms.addElements(bottoms.size(), area.bottoms());
                runTops.addElements(runTops.size(), area.tops());
                for (int run = 0; run < area.tops().length; run++) {
                    blocks.add(names.indexOf(area.blocks()[run]));
                    covers.add(names.indexOf(area.covers()[run]));
                }
                coverShares.addElements(coverShares.size(), area.coverShares());
            }
        }
        return new MapTile.Runs(counts, bottoms.toShortArray(), runTops.toShortArray(), blocks.toShortArray(), covers.toShortArray(),
                coverShares.toByteArray());
    }

    // Only a tile of a block a pixel shows its blocks one by one; coarser ones show the tops of their columns.
    private static MapTile.Voxels voxelsOf(ColumnTops tops, double startX, double startZ, int size, BlockNames names) {
        ColumnTops.TileVoxels found = tops.voxelsIn((int) Math.floor(startX), (int) Math.floor(startZ), size);
        if (found == null) {
            return null;
        }
        short[] blocks = new short[found.blocks().length];
        for (int at = 0; at < blocks.length; at++) {
            blocks[at] = names.indexOf(found.blocks()[at]);
        }
        return new MapTile.Voxels(found.x(), found.z(), found.y(), blocks, found.faces());
    }

    private ColumnTops topsOf(BlockSource source) {
        return switch (source) {
            case GENERATED -> generated;
        };
    }

    private MapTile renderView(MapLayer layer, TileGrid grid, double blocksPerPixel, Set<MapTile.Extra> extras) {
        TileLabels labels = new TileLabels(engine, layer.colorType());
        IrisComplex complex = engine.getComplex();
        ProceduralStream<IrisRegion> regionStream = complex.getRegionStream();
        short[] regions = everyCell(grid, (x, z) -> labels.indexOf(regionStream.get(x, z)));
        ProceduralStream<IrisBiome> baseBiomes = complex.getBaseBiomeStream();
        short[] subjects = layer.labelsRegions() ? regions : everyCell(grid, (x, z) -> labels.indexOf(baseBiomes.get(x, z)));
        float[] heights = extras.contains(MapTile.Extra.HEIGHTS) ? latticeHeights(grid) : null;
        float[] pickNoise = extras.contains(MapTile.Extra.PICK_NOISE) ? MapPickNoise.sample(engine, layer, grid) : null;
        MapLabel.Kind subjectKind = layer.labelsRegions() ? MapLabel.Kind.REGION : MapLabel.Kind.BIOME;
        return new MapTile(grid.size() - 2, blocksPerPixel, subjectKind, labels.labels(), subjects, regions, heights, pickNoise, null);
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
        return new MapTile(grid.size() - 2, blocksPerPixel, MapLabel.Kind.BIOME, labels.labels(), subjects, regions, requestedHeights, pickNoise, null);
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

    /** The blocks one tile meets, each named once, in the order the tile met them. */
    private static final class BlockNames {
        private final Map<String, Short> indices = new HashMap<>();
        private final List<String> names = new ArrayList<>();

        short indexOf(String block) {
            return block == null ? MapTile.Blocks.NO_BLOCK : indices.computeIfAbsent(block, this::add);
        }

        private short add(String block) {
            names.add(block);
            return (short) (names.size() - 1);
        }

        List<String> names() {
            return List.copyOf(names);
        }
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
