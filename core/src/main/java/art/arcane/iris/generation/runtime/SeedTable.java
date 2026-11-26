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

package art.arcane.iris.generation.runtime;

import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.function.LongFunction;

/**
 * The values one runtime object builds per seed, for pack objects that callers sample with seeds they derive
 * themselves. Bounded by least-recent use; an evicted value is rebuilt from the same seed, so eviction never changes
 * output. The most recent value is kept aside because callers usually ask for the same seed many times in a row.
 */
public final class SeedTable<T> {
    private final int capacity;
    private final LongFunction<T> builder;
    private final LinkedHashMap<Long, T> entries;
    private volatile Recent<T> recent;

    public SeedTable(int capacity, LongFunction<T> builder) {
        if (capacity < 1) {
            throw new IllegalArgumentException("Seed table capacity must be positive.");
        }
        this.capacity = capacity;
        this.builder = Objects.requireNonNull(builder, "seed builder");
        this.entries = new LinkedHashMap<>(capacity * 2, 0.75F, true);
    }

    public T get(long seed) {
        Recent<T> last = recent;
        if (last != null && last.seed == seed) {
            return last.value;
        }
        T value = stored(seed);
        if (value == null) {
            value = publish(seed, Objects.requireNonNull(builder.apply(seed), "seeded runtime value"));
        }
        recent = new Recent<>(seed, value);
        return value;
    }

    private T stored(long seed) {
        synchronized (entries) {
            return entries.get(seed);
        }
    }

    private T publish(long seed, T built) {
        synchronized (entries) {
            T raced = entries.putIfAbsent(seed, built);
            if (raced != null) {
                return raced;
            }
            evictBeyondCapacity();
            return built;
        }
    }

    private void evictBeyondCapacity() {
        while (entries.size() > capacity) {
            entries.sequencedKeySet().removeFirst();
        }
    }

    private record Recent<T>(long seed, T value) {
    }
}
