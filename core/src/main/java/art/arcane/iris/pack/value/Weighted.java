package art.arcane.iris.pack.value;

import java.util.List;
import java.util.Objects;

public record Weighted<T>(T value, int weight) {
    public static final int MIN_WEIGHT = 1;
    public static final int MAX_WEIGHT = 1000;

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
