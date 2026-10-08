package com.volmit.host;

import com.volmit.FeatureAttachment;
import com.volmit.IrisFeature;
import com.volmit.config.FeatureSettings.Entry;
import com.volmit.resource.ResourceId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** The settings entries configured for one invocation, per settings type, in level order. A stage
 * conditioned on a type is active when the type has at least one entry, and its body receives them
 * all. For a placement these are the placement's own declarations; for a chunk the runners collect
 * every distinct entry of the chunk's columns themselves. Build once and reuse it for every invocation
 * with the same configuration.
 */
public final class ConfiguredSettings {
    private static final ConfiguredSettings NONE = new ConfiguredSettings(Map.of());
    private static final Comparator<Entry<?>> LEVEL_ORDER = Comparator.comparing(Entry::level);

    private final Map<Class<?>, List<Entry<?>>> entries;

    private ConfiguredSettings(Map<Class<?>, List<Entry<?>>> entries) {
        this.entries = Map.copyOf(entries);
    }

    public static ConfiguredSettings none() {
        return NONE;
    }

    /** These entries, grouped by settings type, each group in level order and otherwise as given. */
    public static ConfiguredSettings of(List<? extends Entry<?>> entries) {
        var grouped = new HashMap<Class<?>, List<Entry<?>>>();
        for (var entry : entries) {
            Objects.requireNonNull(entry, "entry");
            grouped.computeIfAbsent(entry.value().getClass(), ignored -> new ArrayList<>()).add(entry);
        }
        grouped.replaceAll((type, group) -> group.stream().sorted(LEVEL_ORDER).toList());
        return grouped.isEmpty() ? NONE : new ConfiguredSettings(grouped);
    }

    /** One placement's own settings, at most one value per type, declared by {@code source}. */
    public static ConfiguredSettings placement(ResourceId source, IrisFeature... settings) {
        Objects.requireNonNull(source, "source");
        var entries = new ArrayList<Entry<?>>();
        var types = new HashMap<Class<?>, IrisFeature>();
        for (var value : settings) {
            Objects.requireNonNull(value, "settings");
            if (types.putIfAbsent(value.getClass(), value) != null) {
                throw new IllegalArgumentException("Settings configured twice: " + value.getClass().getName());
            }
            entries.add(new Entry<>(FeatureAttachment.Target.OBJECT_PLACEMENT, source, value));
        }
        return of(entries);
    }

    /** The entries of {@code type}, in level order; empty when nothing configures it. */
    @SuppressWarnings("unchecked") // Entries are grouped by the class of their value.
    public <S extends IrisFeature> List<Entry<S>> find(Class<S> type) {
        var found = entries.get(type);
        return found == null ? List.of() : (List<Entry<S>>) (List<?>) found;
    }
}
