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

package art.arcane.iris.studio.bridge;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.studio.map.RiverKey;
import art.arcane.iris.studio.map.components.MapLabels;
import art.arcane.iris.studio.map.components.MapLayer;
import art.arcane.iris.studio.map.components.MapTile;
import art.arcane.iris.studio.map.components.MapTileRenderer;
import art.arcane.iris.studio.map.components.PickDisplay;
import art.arcane.iris.studio.map.components.TileType;
import art.arcane.iris.studio.render.IrisRenderer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/** Draws the studio app's map views with the renderer the Swing map uses, one tile at a time. */
final class StudioTiles {
    private static final int RGB = 0xFFFFFF;
    private static final short DRY = 0;
    private static final String DRY_KEY = "dry";
    private static final int DRY_COLOR = 0x1C1F26;
    private static final int MAX_FINISH_ATTEMPTS = 200;
    private static final long FINISH_WAIT_MILLIS = 50L;

    private final Engine engine;
    private final MapTileRenderer renderer;
    private final IrisRenderer waterRenderer;
    private final List<StudioTile.Label> waterLabels;
    private final Map<Integer, Short> waterLabelByColor;

    StudioTiles(Engine engine, MapTileRenderer renderer) {
        this.engine = engine;
        this.renderer = renderer;
        this.waterRenderer = new IrisRenderer(engine);
        List<StudioTile.Label> labels = new ArrayList<>();
        labels.add(new StudioTile.Label("WATER", DRY_KEY, "No water", DRY_COLOR));
        Map<Integer, Short> byColor = new HashMap<>();
        for (RiverKey.Row row : RiverKey.rows()) {
            byColor.put(row.color() & RGB, (short) labels.size());
            labels.add(new StudioTile.Label("WATER", row.label(), row.label(), row.color() & RGB));
        }
        this.waterLabels = List.copyOf(labels);
        this.waterLabelByColor = Map.copyOf(byColor);
    }

    boolean hydrologyEnabled() {
        var runtime = engine.getComplex().getHydrologyRuntime();
        return runtime != null && runtime.enabled();
    }

    StudioTile biomesOrRegions(String view, String previewRegion, double startX, double startZ, double blocksPerPixel, int size,
                               boolean withHeights, BooleanSupplier cancelled) {
        MapLayer layer = layerOf(view, previewRegion);
        MapTileRenderer.TileRender render = renderer.prepareTile(startX, startZ, blocksPerPixel, size, layer, withHeights);
        MapTile tile = render.render(cancelled);
        for (int attempt = 0; !render.complete() && attempt < MAX_FINISH_ATTEMPTS; attempt++) {
            pause(cancelled);
            tile = render.render(cancelled);
        }
        return wire(tile, blocksPerPixel, withHeights && tile.hasHeights());
    }

    StudioTile rivers(double startX, double startZ, double blocksPerPixel, int size, BooleanSupplier cancelled) {
        int grid = size + 2;
        short[] subjects = new short[grid * grid];
        Arrays.fill(subjects, DRY);
        if (hydrologyEnabled()) {
            IrisRenderer.WaterTile water = waterRenderer.prepareStudioWater(startX - blocksPerPixel, startZ - blocksPerPixel, blocksPerPixel, grid);
            int[] colors = water.render(null, false, cancelled);
            for (int attempt = 0; !water.complete() && attempt < MAX_FINISH_ATTEMPTS; attempt++) {
                pause(cancelled);
                colors = water.render(null, false, cancelled);
            }
            for (int cell = 0; cell < colors.length; cell++) {
                subjects[cell] = waterLabelByColor.getOrDefault(colors[cell] & RGB, DRY);
            }
        }
        return new StudioTile(size, blocksPerPixel, "WATER", waterLabels, subjects, subjects, null);
    }

    private static MapLayer layerOf(String view, String previewRegion) {
        if ("regions".equals(view)) {
            return previewRegion == null ? MapLayer.regions(PickDisplay.PICKS) : MapLayer.regionPreview(previewRegion, PickDisplay.PICKS);
        }
        return MapLayer.biomes(PickDisplay.PICKS, false);
    }

    private StudioTile wire(MapTile tile, double blocksPerPixel, boolean withHeights) {
        int size = tile.size();
        int grid = size + 2;
        Labelling labelling = new Labelling();
        short[] subjects = new short[grid * grid];
        short[] regions = new short[grid * grid];
        float[] heights = withHeights ? new float[grid * grid] : null;
        for (int z = -1; z <= size; z++) {
            for (int x = -1; x <= size; x++) {
                int cell = (z + 1) * grid + x + 1;
                subjects[cell] = labelling.indexOf(tile.labels(), tile.subjectAt(x, z));
                regions[cell] = labelling.indexOf(tile.labels(), tile.regionAt(x, z));
                if (heights != null) {
                    heights[cell] = (float) tile.heightAt(x, z);
                }
            }
        }
        List<StudioTile.Label> labels = labelling.labels(renderer);
        return new StudioTile(size, blocksPerPixel, tile.subjectKind().name(), labels, subjects, regions, heights);
    }

    private static void pause(BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean()) {
            throw new CancellationException("Tile dropped");
        }
        try {
            Thread.sleep(FINISH_WAIT_MILLIS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Tile interrupted");
        }
    }

    /** Renumbers the labels a tile uses from 0, so its header lists only those. */
    private static final class Labelling {
        private final Map<Short, Short> indexByLabelId = new HashMap<>();
        private final List<Short> usedLabelIds = new ArrayList<>();

        short indexOf(MapLabels labels, short labelId) {
            if (labelId == MapLabels.NONE || labels.label(labelId) == null) {
                return StudioTile.NO_LABEL;
            }
            return indexByLabelId.computeIfAbsent(labelId, id -> {
                usedLabelIds.add(id);
                return (short) (usedLabelIds.size() - 1);
            });
        }

        List<StudioTile.Label> labels(MapTileRenderer renderer) {
            List<StudioTile.Label> labels = new ArrayList<>();
            for (short id : usedLabelIds) {
                MapLabels.Label label = renderer.labels().label(id);
                labels.add(new StudioTile.Label(label.kind().name(), label.key(), label.name(),
                        renderer.legendColor(label, TileType.BIOME) & RGB));
            }
            return labels;
        }
    }
}
