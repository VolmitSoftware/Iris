package art.arcane.iris.pack.value;

import art.arcane.volmlib.util.math.Rarity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class IrisRaritySelection<T extends Rarity> {
    private final List<T> choices;
    private final long[] cumulativeCounts;

    private IrisRaritySelection(List<T> choices, long[] cumulativeCounts) {
        this.choices = choices;
        this.cumulativeCounts = cumulativeCounts;
    }

    public static <T extends Rarity> IrisRaritySelection<T> create(List<T> options) {
        ArrayList<T> choices = new ArrayList<>(options.size());
        long maximumRarity = 1L;
        for (T option : options) {
            if (option != null) {
                choices.add(option);
                maximumRarity = Math.max(maximumRarity, option.getRarity());
            }
        }
        long[] cumulativeCounts = new long[choices.size()];
        long total = 0L;
        for (int index = 0; index < choices.size(); index++) {
            total += maximumRarity + 1L - Math.max(1, choices.get(index).getRarity());
            cumulativeCounts[index] = total;
        }
        return new IrisRaritySelection<>(List.copyOf(choices), cumulativeCounts);
    }

    public long size() {
        return cumulativeCounts.length == 0 ? 0L : cumulativeCounts[cumulativeCounts.length - 1];
    }

    public T select(double noiseValue) {
        long maximumIndex = size() - 1L;
        long selectedIndex = Math.round(noiseValue * maximumIndex);
        return get(maximumIndex <= Integer.MAX_VALUE ? (int) selectedIndex : selectedIndex);
    }

    public T get(long index) {
        long size = size();
        if (size == 0L) {
            return null;
        }
        if (choices.size() == 1) {
            return choices.getFirst();
        }
        long selectedIndex = Math.max(0L, Math.min(size - 1L, index));
        long frontSize = size / 2L;
        long insertionIndex = selectedIndex < frontSize
                ? 2L * (frontSize - selectedIndex) - 1L
                : 2L * (selectedIndex - frontSize);
        int choiceIndex = Arrays.binarySearch(cumulativeCounts, insertionIndex + 1L);
        return choices.get(choiceIndex < 0 ? -choiceIndex - 1 : choiceIndex);
    }
}
