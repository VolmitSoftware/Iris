/*
 * Iris is a World Generator for Minecraft Bukkit Servers
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

package art.arcane.iris.engine.framework;

import art.arcane.volmlib.util.math.RNG;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

public final class LootResolver {
    private static final int MAX_SCALED_SOURCE_COUNT = 256;
    private static final long CONTAINER_SALT = 0x632BE59BD9B4E019L;
    private static final long TABLE_SALT = 0x94D049BB133111EBL;
    private static final long ENTRY_SALT = 0x9E3779B97F4A7C15L;

    private LootResolver() {
    }

    public static RNG containerRng(long lootSeed, int x, int y, int z) {
        return new RNG(containerSeed(lootSeed, x, y, z));
    }

    public static long containerSeed(long lootSeed, int x, int y, int z) {
        long seed = mix(lootSeed ^ CONTAINER_SALT);
        seed = mix(seed ^ ((long) x * 0xD6E8FEB86659FD93L));
        seed = mix(seed ^ ((long) y * 0xA5A3564E27F886A7L));
        return mix(seed ^ ((long) z * 0x8CB92BA72F3D8DD7L));
    }

    /** The random stream of one loot table at one position; {@code tableIdentity} names the table (its key, else its name). */
    public static RNG tableRng(long lootSeed, String tableIdentity, int x, int y, int z) {
        return new RNG(tableSeed(lootSeed, tableIdentity, x, y, z));
    }

    public static long tableSeed(long lootSeed, String tableIdentity, int x, int y, int z) {
        return mix(containerSeed(lootSeed, x, y, z) ^ stableHash(tableIdentity) ^ TABLE_SALT);
    }

    public static boolean spatialOneIn(
            long lootSeed,
            String tableIdentity,
            int entryIndex,
            int x,
            int y,
            int z,
            long rarity
    ) {
        if (rarity <= 1L) {
            return true;
        }
        long roll = mix(tableSeed(lootSeed, tableIdentity, x, y, z) ^ ((long) entryIndex * ENTRY_SALT));
        return new RNG(roll).nextLong(rarity) == 0L;
    }

    public static long combinedRarity(int tableRarity, int entryRarity) {
        long table = Math.max(1, tableRarity);
        long entry = Math.max(1, entryRarity);
        if (table > Long.MAX_VALUE / entry) {
            return Long.MAX_VALUE;
        }
        return table * entry;
    }

    public static boolean oneIn(RNG rng, int rarity) {
        int bound = Math.max(1, rarity);
        return bound == 1 || rng.nextInt(bound) == 0;
    }

    public static int inclusive(RNG rng, int first, int second) {
        int lower = Math.min(first, second);
        int upper = Math.max(first, second);
        if (lower == upper) {
            return lower;
        }
        long span = (long) upper - lower + 1L;
        return (int) (lower + rng.nextLong(span));
    }

    public static <T> T pickWeighted(List<T> choices, ToIntFunction<T> weight, RNG rng) {
        long total = 0L;
        for (T choice : choices) {
            int value = Math.max(0, weight.applyAsInt(choice));
            total = Long.MAX_VALUE - total < value ? Long.MAX_VALUE : total + value;
        }
        if (total <= 0L) {
            return null;
        }
        long pull = rng.nextLong(total);
        for (T choice : choices) {
            pull -= Math.max(0, weight.applyAsInt(choice));
            if (pull < 0L) {
                return choice;
            }
        }
        return null;
    }

    public static <T> void scaleSources(List<T> sources, double multiplier, RNG rng) {
        if (!Double.isFinite(multiplier) || multiplier < 0D) {
            throw new IllegalArgumentException("Effective loot source multiplier must be finite and non-negative, got "
                    + multiplier + ".");
        }
        if (sources.isEmpty()) {
            return;
        }
        if (sources.size() > MAX_SCALED_SOURCE_COUNT) {
            throw new IllegalArgumentException("Loot source scaling starts above the maximum of "
                    + MAX_SCALED_SOURCE_COUNT + " sources: " + sources.size() + ".");
        }
        double scaledSize = sources.size() * multiplier;
        if (!Double.isFinite(scaledSize) || scaledSize > MAX_SCALED_SOURCE_COUNT) {
            throw new IllegalArgumentException("Loot source scaling exceeds the maximum of "
                    + MAX_SCALED_SOURCE_COUNT + " sources: " + sources.size() + " * " + multiplier + ".");
        }
        int target = (int) Math.round(scaledSize);
        if (target == sources.size()) {
            return;
        }
        List<T> original = new ArrayList<>(sources);
        int[] order = new int[original.size()];
        if (target < original.size()) {
            shrinkSources(sources, original, order, target, rng);
            return;
        }
        expandSources(sources, original, order, target, rng);
    }

    private static <T> void shrinkSources(List<T> sources, List<T> original, int[] order, int target, RNG rng) {
        shuffleOrder(order, rng);
        boolean[] selected = new boolean[original.size()];
        for (int index = 0; index < target; index++) {
            selected[order[index]] = true;
        }
        sources.clear();
        for (int index = 0; index < original.size(); index++) {
            if (selected[index]) {
                sources.add(original.get(index));
            }
        }
    }

    private static <T> void expandSources(List<T> sources, List<T> original, int[] order, int target, RNG rng) {
        while (sources.size() < target) {
            shuffleOrder(order, rng);
            int additions = Math.min(target - sources.size(), original.size());
            for (int index = 0; index < additions; index++) {
                sources.add(original.get(order[index]));
            }
        }
    }

    private static void shuffleOrder(int[] order, RNG rng) {
        for (int index = 0; index < order.length; index++) {
            order[index] = index;
        }
        for (int index = 0; index < order.length - 1; index++) {
            int selected = index + rng.nextInt(order.length - index);
            int previous = order[index];
            order[index] = order[selected];
            order[selected] = previous;
        }
    }

    private static long stableHash(String value) {
        long hash = 0xCBF29CE484222325L;
        for (int i = 0; i < value.length(); i++) {
            hash ^= value.charAt(i);
            hash *= 0x100000001B3L;
        }
        return mix(hash);
    }

    private static long mix(long value) {
        long mixed = value;
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        return mixed ^ (mixed >>> 31);
    }
}
