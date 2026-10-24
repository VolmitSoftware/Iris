package com.volmit.config;

import com.volmit.IrisFeature;
import com.volmit.config.FeatureSettings.Entry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** The entries of one settings type that apply somewhere, in {@link Level} order: what a column stacks
 * ({@link FeatureSettings#at}), what a chunk or a placement configures ({@link com.volmit.stage.StageContext#configured})
 * or what a pack declares ({@link PackSettings#declared}). Levels stack and nothing is merged, so a module picks what it
 * needs: every entry, the dimension's, the most specific one. Several entries may share a level, such as the biomes of
 * a chunk or of a pack; they keep the order they were found in.
 *
 * <p>An immutable value; equal when the entries are equal. Hosts build it with {@link #of}.
 */
public final class SettingsStack<F extends IrisFeature> implements Iterable<Entry<F>> {
    private static final SettingsStack<?> EMPTY = new SettingsStack<>(List.of());
    private static final Comparator<Entry<?>> LEVEL_ORDER = Comparator.comparing(Entry::level);

    private final List<Entry<F>> entries;
    private final List<F> values;

    private SettingsStack(List<Entry<F>> entries) {
        this.entries = entries;
        this.values = entries.stream().map(Entry::value).toList();
    }

    @SuppressWarnings("unchecked") // The empty stack holds no entry of any type.
    public static <F extends IrisFeature> SettingsStack<F> empty() {
        return (SettingsStack<F>) EMPTY;
    }

    /** These entries in level order; entries of one level keep the order given. */
    public static <F extends IrisFeature> SettingsStack<F> of(List<? extends Entry<F>> entries) {
        Objects.requireNonNull(entries, "entries");
        if (entries.isEmpty()) {
            return empty();
        }
        var sorted = new ArrayList<Entry<F>>(entries);
        sorted.forEach(entry -> Objects.requireNonNull(entry, "entry"));
        sorted.sort(LEVEL_ORDER);
        return new SettingsStack<>(List.copyOf(sorted));
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** Every entry, in level order. */
    public List<Entry<F>> entries() {
        return entries;
    }

    /** Every entry's value, in level order. */
    public List<F> values() {
        return values;
    }

    /** The dimension's value; empty when the dimension declares none. */
    public Optional<F> dimension() {
        return level(Level.DIMENSION);
    }

    /** The first value declared at {@code level}; empty when that level declares none. */
    public Optional<F> level(Level level) {
        Objects.requireNonNull(level, "level");
        for (var entry : entries) {
            if (entry.level() == level) {
                return Optional.of(entry.value());
            }
        }
        return Optional.empty();
    }

    /** The value of the last entry, the most specific level's (a placement before a biome before a region before the
     * dimension); empty when the stack is.
     */
    public Optional<F> mostSpecific() {
        return entries.isEmpty() ? Optional.empty() : Optional.of(entries.getLast().value());
    }

    @Override
    public Iterator<Entry<F>> iterator() {
        return entries.iterator();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SettingsStack<?> stack && entries.equals(stack.entries);
    }

    @Override
    public int hashCode() {
        return entries.hashCode();
    }

    @Override
    public String toString() {
        return "SettingsStack" + entries;
    }
}
