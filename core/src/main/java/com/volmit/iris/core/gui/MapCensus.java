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

package com.volmit.iris.core.gui;

import com.volmit.iris.core.gui.components.MapLabels;
import com.volmit.iris.core.gui.components.MapStyle;
import com.volmit.iris.core.gui.components.MapTile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * Measures the visible part of the map from the tiles already drawn: how much each biome or region covers, and which
 * heights it spans.
 */
final class MapCensus {
    private static final long MAXIMUM_COUNTED_PIXELS = 250_000L;
    private static final int MINIMUM_HEIGHT_SAMPLES = 64;
    private static final double LOW_HEIGHT_PERCENTILE = 0.01D;
    private static final double HIGH_HEIGHT_PERCENTILE = 0.99D;
    private static final double HEIGHT_RANGE_STEP_BLOCKS = 8D;
    private static final double MINIMUM_WATER_DEPTH_BLOCKS = 8D;
    private static final double MINIMUM_LAND_HEIGHT_BLOCKS = 16D;

    private MapCensus() {
    }

    static List<Share> count(VisionRenderController.Frame frame, ViewArea area, ToIntFunction<MapLabels.Label> colorOf) {
        Map<Short, long[]> counts = new HashMap<>();
        MapLabels[] labels = new MapLabels[1];
        long counted = visitVisiblePixels(frame, area, (tile, pixelX, pixelZ) -> {
            labels[0] = tile.labels();
            counts.computeIfAbsent(tile.subjectAt(pixelX, pixelZ), id -> new long[1])[0]++;
        });
        return shares(counts, labels[0], counted, colorOf);
    }

    /**
     * The heights on screen, ignoring the highest and lowest few percent so one spike doesn't wash out every colour.
     * Rounded to whole steps, so panning around doesn't recolour the map for every small change.
     *
     * @return the range, or null until enough of the map is drawn to tell
     */
    static MapStyle.HeightRange heightRange(VisionRenderController.Frame frame, ViewArea area, double seaLevel) {
        DoubleList heights = new DoubleList();
        visitVisiblePixels(frame, area, (tile, pixelX, pixelZ) -> {
            if (tile.hasHeights()) {
                heights.add(tile.heightAt(pixelX, pixelZ));
            }
        });
        if (heights.size() < MINIMUM_HEIGHT_SAMPLES) {
            return null;
        }
        double[] sorted = heights.sorted();
        double low = sorted[(int) ((sorted.length - 1) * LOW_HEIGHT_PERCENTILE)];
        double high = sorted[(int) ((sorted.length - 1) * HIGH_HEIGHT_PERCENTILE)];
        double lowest = Math.floor(Math.min(low, seaLevel - MINIMUM_WATER_DEPTH_BLOCKS) / HEIGHT_RANGE_STEP_BLOCKS)
                * HEIGHT_RANGE_STEP_BLOCKS;
        double highest = Math.ceil(Math.max(high, seaLevel + MINIMUM_LAND_HEIGHT_BLOCKS) / HEIGHT_RANGE_STEP_BLOCKS)
                * HEIGHT_RANGE_STEP_BLOCKS;
        return new MapStyle.HeightRange(lowest, seaLevel, highest);
    }

    private static long visitVisiblePixels(VisionRenderController.Frame frame, ViewArea area, PixelVisitor visitor) {
        if (frame == null) {
            return 0L;
        }
        double blocksPerPixel = frame.renderBlocksPerPixel();
        int stride = countingStride(area.maximumX() - area.minimumX(), area.maximumZ() - area.minimumZ(), blocksPerPixel);
        double tileSpan = VisionRenderController.TILE_PIXELS * blocksPerPixel;
        long visited = 0L;
        for (VisionRenderController.VisibleTile visible : frame.tiles()) {
            MapTile tile = visible.tile();
            if (tile == null) {
                continue;
            }
            double originX = visible.tileX() * tileSpan;
            double originZ = visible.tileZ() * tileSpan;
            int firstX = firstPixel(area.minimumX() - originX, blocksPerPixel, stride, visible.tileX());
            int firstZ = firstPixel(area.minimumZ() - originZ, blocksPerPixel, stride, visible.tileZ());
            int endX = endPixel(area.maximumX() - originX, blocksPerPixel, tile.size());
            int endZ = endPixel(area.maximumZ() - originZ, blocksPerPixel, tile.size());
            for (int pixelZ = firstZ; pixelZ < endZ; pixelZ += stride) {
                for (int pixelX = firstX; pixelX < endX; pixelX += stride) {
                    visitor.visit(tile, pixelX, pixelZ);
                    visited++;
                }
            }
        }
        return visited;
    }

    private static List<Share> shares(Map<Short, long[]> counts, MapLabels labels, long counted,
                                      ToIntFunction<MapLabels.Label> colorOf) {
        if (labels == null || counted == 0L) {
            return List.of();
        }
        List<Share> shares = new ArrayList<>(counts.size());
        for (Map.Entry<Short, long[]> entry : counts.entrySet()) {
            MapLabels.Label label = labels.label(entry.getKey());
            if (label != null) {
                shares.add(new Share(label, colorOf.applyAsInt(label), entry.getValue()[0] / (double) counted));
            }
        }
        shares.sort(Comparator.comparingDouble(Share::fraction).reversed().thenComparing(share -> share.label().name()));
        return List.copyOf(shares);
    }

    // Counting every pixel of a big screen is wasted work; an evenly spread sample gives the same percentages.
    private static int countingStride(double widthBlocks, double heightBlocks, double blocksPerPixel) {
        double pixels = (widthBlocks / blocksPerPixel) * (heightBlocks / blocksPerPixel);
        return Math.max(1, (int) Math.ceil(Math.sqrt(pixels / MAXIMUM_COUNTED_PIXELS)));
    }

    // Starting on the global stride grid keeps neighbouring tiles from sampling the same columns twice as densely.
    private static int firstPixel(double offsetBlocks, double blocksPerPixel, int stride, long tileIndex) {
        int first = Math.max(0, (int) Math.ceil(offsetBlocks / blocksPerPixel));
        long globalPixel = tileIndex * VisionRenderController.TILE_PIXELS + first;
        int misalignment = (int) Math.floorMod(globalPixel, (long) stride);
        return misalignment == 0 ? first : first + stride - misalignment;
    }

    private static int endPixel(double offsetBlocks, double blocksPerPixel, int size) {
        return Math.min(size, (int) Math.ceil(offsetBlocks / blocksPerPixel));
    }

    record Share(MapLabels.Label label, int color, double fraction) {
    }

    /**
     * The world area the screen shows, in blocks.
     */
    record ViewArea(double minimumX, double minimumZ, double maximumX, double maximumZ) {
    }

    @FunctionalInterface
    private interface PixelVisitor {
        void visit(MapTile tile, int pixelX, int pixelZ);
    }

    private static final class DoubleList {
        private double[] values = new double[1024];
        private int size;

        void add(double value) {
            if (size == values.length) {
                values = Arrays.copyOf(values, size * 2);
            }
            values[size++] = value;
        }

        int size() {
            return size;
        }

        double[] sorted() {
            double[] sorted = Arrays.copyOf(values, size);
            Arrays.sort(sorted);
            return sorted;
        }
    }
}
