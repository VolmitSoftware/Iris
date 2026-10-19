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
import com.volmit.iris.core.gui.components.MapTile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * Counts how much of the visible map each biome or region covers, from the tiles already drawn.
 */
final class MapCensus {
    private static final long MAXIMUM_COUNTED_PIXELS = 250_000L;

    private MapCensus() {
    }

    static List<Share> count(VisionRenderController.Frame frame, double minimumX, double minimumZ, double maximumX,
                             double maximumZ, ToIntFunction<MapLabels.Label> colorOf) {
        if (frame == null) {
            return List.of();
        }
        double blocksPerPixel = frame.renderBlocksPerPixel();
        int stride = countingStride(maximumX - minimumX, maximumZ - minimumZ, blocksPerPixel);
        double tileSpan = VisionRenderController.TILE_PIXELS * blocksPerPixel;
        Map<Short, long[]> counts = new HashMap<>();
        MapLabels labels = null;
        long counted = 0L;
        for (VisionRenderController.VisibleTile visible : frame.tiles()) {
            MapTile tile = visible.tile();
            if (tile == null) {
                continue;
            }
            labels = tile.labels();
            double originX = visible.tileX() * tileSpan;
            double originZ = visible.tileZ() * tileSpan;
            int firstX = firstPixel(minimumX - originX, blocksPerPixel, stride, visible.tileX());
            int firstZ = firstPixel(minimumZ - originZ, blocksPerPixel, stride, visible.tileZ());
            int endX = endPixel(maximumX - originX, blocksPerPixel, tile.size());
            int endZ = endPixel(maximumZ - originZ, blocksPerPixel, tile.size());
            for (int pixelZ = firstZ; pixelZ < endZ; pixelZ += stride) {
                for (int pixelX = firstX; pixelX < endX; pixelX += stride) {
                    counts.computeIfAbsent(tile.subjectAt(pixelX, pixelZ), id -> new long[1])[0]++;
                    counted++;
                }
            }
        }
        return shares(counts, labels, counted, colorOf);
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
}
