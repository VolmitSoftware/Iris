package com.volmit.world;

import java.util.List;
import java.util.Objects;

/** A list entry that counts as {@code weight} copies of itself in a row, as biome, region and child lists write it.
 * {@link #pick} walks such a list with a noise from 0 to 1, so a module that picks among a pack's weighted lists
 * (a region's biomes, a biome's children) lands on the same entry the host does.
 */
public record Weighted<T>(T value, int weight) {
    public static final int MIN_WEIGHT = 1;
    public static final int MAX_WEIGHT = 1000;

    /** Which entry gets noise lying exactly on the edge between two. Cellular noise sits on such edges over whole cells,
     * so this decides real patches of terrain. A region's biome list gives edges to the earlier entry and children to the
     * later one.
     */
    public enum Edge {
        EARLIER,
        LATER
    }

    public Weighted {
        Objects.requireNonNull(value, "value");
        if (weight < MIN_WEIGHT || weight > MAX_WEIGHT) {
            throw new IllegalArgumentException("Weight must be from " + MIN_WEIGHT + " to " + MAX_WEIGHT + ": " + weight);
        }
    }

    /** Picks along the list as if every entry were written out {@code weight} times: the noise from 0 to 1 is split into
     * equal slots, one per copy, in list order. Noise below 0 picks the first entry, noise above 1 the last; an empty list
     * picks nothing.
     */
    public static <T> T pick(List<Weighted<T>> entries, double noise, Edge edge) {
        if (entries.isEmpty()) {
            return null;
        }

        double slotPosition = noise * totalWeight(entries);
        int slotsUpToHere = 0;
        for (Weighted<T> entry : entries) {
            slotsUpToHere += entry.weight();
            boolean inThisEntry = edge == Edge.EARLIER ? slotPosition <= slotsUpToHere : slotPosition < slotsUpToHere;
            if (inThisEntry) {
                return entry.value();
            }
        }

        return entries.getLast().value();
    }

    public static int totalWeight(List<? extends Weighted<?>> entries) {
        int total = 0;
        for (Weighted<?> entry : entries) {
            total += entry.weight();
        }
        return total;
    }
}
