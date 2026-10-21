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

import com.volmit.iris.engine.object.InferredType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Which of a region's biomes allow which ground height in HYBRID terrain, measured against the ground heights the map
 * shows, so the region view can point out heights no biome covers and heights several biomes share.
 * Heights are in blocks above the fluid height, negative below it.
 */
final class RegionHeightCoverage {
    static final List<InferredType> MEASURED_TYPES = List.of(InferredType.SEA, InferredType.LAND);
    private static final int MAXIMUM_NOTES = 8;

    private final List<BiomeRange> ranges;
    private final int lowestHeight;
    private final int highestHeight;
    private final int[] countsFromLowest;
    private final int highestCount;

    private RegionHeightCoverage(List<BiomeRange> ranges, int lowestHeight, int[] countsFromLowest) {
        this.ranges = List.copyOf(ranges);
        this.lowestHeight = lowestHeight;
        this.highestHeight = lowestHeight + countsFromLowest.length - 1;
        this.countsFromLowest = countsFromLowest;
        int highest = 0;
        for (int count : countsFromLowest) {
            highest = Math.max(highest, count);
        }
        this.highestCount = highest;
    }

    /**
     * @param groundHeights the ground height of every sampled spot, rounded to whole blocks
     */
    static RegionHeightCoverage measure(List<BiomeRange> ranges, int[] groundHeights) {
        if (groundHeights.length == 0) {
            return new RegionHeightCoverage(ranges, 0, new int[1]);
        }
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        for (int height : groundHeights) {
            lowest = Math.min(lowest, height);
            highest = Math.max(highest, height);
        }
        int[] counts = new int[highest - lowest + 1];
        for (int height : groundHeights) {
            counts[height - lowest]++;
        }
        return new RegionHeightCoverage(ranges, lowest, counts);
    }

    List<BiomeRange> ranges(InferredType type) {
        return ranges.stream().filter(range -> range.type() == type).toList();
    }

    int lowestHeight() {
        return lowestHeight;
    }

    int highestHeight() {
        return highestHeight;
    }

    int count(int height) {
        return height < lowestHeight || height > highestHeight ? 0 : countsFromLowest[height - lowestHeight];
    }

    int highestCount() {
        return highestCount;
    }

    /**
     * The ground heights of one type split into runs that the same biomes allow, from low to high. Land is the ground
     * at or above the fluid height, sea the ground below it.
     */
    List<HeightBand> bands(InferredType type) {
        int from = type == InferredType.SEA ? lowestHeight : Math.max(0, lowestHeight);
        int to = type == InferredType.SEA ? Math.min(-1, highestHeight) : highestHeight;
        int total = countBetween(from, to);
        List<HeightBand> bands = new ArrayList<>();
        if (total == 0) {
            return bands;
        }
        List<BiomeRange> typeRanges = ranges(type);
        int bandStart = from;
        List<BiomeRange> bandBiomes = coveringBiomes(typeRanges, from);
        for (int height = from + 1; height <= to + 1; height++) {
            List<BiomeRange> biomes = height <= to ? coveringBiomes(typeRanges, height) : null;
            if (bandBiomes.equals(biomes)) {
                continue;
            }
            bands.add(new HeightBand(type, bandStart, height - 1, bandBiomes, countBetween(bandStart, height - 1) / (double) total));
            bandStart = height;
            bandBiomes = biomes;
        }
        return bands;
    }

    List<String> notes() {
        List<String> notes = new ArrayList<>();
        noteBiomesWithoutRange(notes);
        for (InferredType type : MEASURED_TYPES) {
            for (HeightBand band : bands(type)) {
                if (band.share() == 0D) {
                    continue;
                }
                if (band.isGap()) {
                    notes.add(gapNote(band));
                } else if (band.isShared() && band.biomes().stream().allMatch(BiomeRange::isBounded)) {
                    notes.add(names(band.biomes()) + " share " + heightText(band.fromHeight(), band.toHeight())
                            + ": their noise decides.");
                }
            }
        }
        return notes.size() > MAXIMUM_NOTES ? notes.subList(0, MAXIMUM_NOTES) : notes;
    }

    private void noteBiomesWithoutRange(List<String> notes) {
        List<BiomeRange> unbounded = ranges.stream()
                .filter(range -> range.type() != InferredType.SHORE && !range.isBounded())
                .toList();
        if (unbounded.isEmpty()) {
            return;
        }
        boolean single = unbounded.size() == 1;
        notes.add(names(unbounded) + (single ? " has no height range, so it" : " have no height range, so they")
                + " can show up at any height.");
    }

    private String gapNote(HeightBand gap) {
        String ground = gap.type() == InferredType.SEA ? "sea floor" : "land";
        String note = "Nothing covers " + heightText(gap.fromHeight(), gap.toHeight()) + " (" + percent(gap.share())
                + " of the " + ground + " in view)";
        BiomeRange below = closestBelow(gap);
        BiomeRange above = closestAbove(gap);
        if (below != null && above != null) {
            return note + ": " + below.name() + " and " + above.name() + ", the closest ranges, take it over.";
        }
        BiomeRange closest = below != null ? below : above;
        return closest == null ? note + "." : note + ": " + closest.name() + ", the closest range, takes it over.";
    }

    private BiomeRange closestBelow(HeightBand gap) {
        return ranges(gap.type()).stream()
                .filter(range -> range.maxHeight() < gap.fromHeight())
                .max(Comparator.comparingInt(BiomeRange::maxHeight))
                .orElse(null);
    }

    private BiomeRange closestAbove(HeightBand gap) {
        return ranges(gap.type()).stream()
                .filter(range -> range.minHeight() > gap.toHeight())
                .min(Comparator.comparingInt(BiomeRange::minHeight))
                .orElse(null);
    }

    private static List<BiomeRange> coveringBiomes(List<BiomeRange> typeRanges, int height) {
        return typeRanges.stream().filter(range -> range.covers(height)).toList();
    }

    private int countBetween(int from, int to) {
        int total = 0;
        for (int height = from; height <= to; height++) {
            total += count(height);
        }
        return total;
    }

    static String heightText(int from, int to) {
        if (from >= 0) {
            return from == to ? from + " above sea" : from + " to " + to + " above sea";
        }
        if (to < 0) {
            return from == to ? -from + " below sea" : -to + " to " + -from + " below sea";
        }
        return -from + " below to " + to + " above sea";
    }

    private static String names(List<BiomeRange> biomes) {
        List<String> names = biomes.stream().map(BiomeRange::name).toList();
        if (names.size() == 1) {
            return names.get(0);
        }
        return names.subList(0, names.size() - 1).stream().collect(Collectors.joining(", ")) + " and " + names.get(names.size() - 1);
    }

    private static String percent(double share) {
        double percent = share * 100D;
        return percent < 1D ? "<1%" : String.format(Locale.ROOT, "%.0f%%", percent);
    }

    /**
     * The heights one of the region's biomes allows, from its placement.
     */
    record BiomeRange(String key, String name, int color, InferredType type, int minHeight, int maxHeight) {
        private static final int UNBOUNDED_LOW = -2032;
        private static final int UNBOUNDED_HIGH = 2032;

        boolean covers(int height) {
            return height >= minHeight && height <= maxHeight;
        }

        boolean isBounded() {
            return hasLowerBound() || hasUpperBound();
        }

        boolean hasLowerBound() {
            return minHeight > UNBOUNDED_LOW;
        }

        boolean hasUpperBound() {
            return maxHeight < UNBOUNDED_HIGH;
        }
    }

    /**
     * A run of ground heights that the same biomes allow; no biomes makes it a gap.
     *
     * @param share how much of this type's ground in view lies in the run
     */
    record HeightBand(InferredType type, int fromHeight, int toHeight, List<BiomeRange> biomes, double share) {
        boolean isGap() {
            return biomes.isEmpty();
        }

        boolean isShared() {
            return biomes.size() > 1;
        }
    }
}
