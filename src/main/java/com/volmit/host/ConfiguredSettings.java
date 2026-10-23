package com.volmit.host;

import com.volmit.IrisFeature;
import com.volmit.config.FeatureSettings.Entry;
import com.volmit.config.Level;
import com.volmit.config.SettingsStack;
import com.volmit.resource.ResourceId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** The settings configured for one invocation, one {@link SettingsStack} per settings type. A stage
 * conditioned on a type is active when its stack is not empty, and reads it with
 * {@link com.volmit.stage.StageContext#configured}. For a placement these are the placement's own declarations; for
 * a chunk the runners collect every distinct entry of the chunk's columns themselves. Entries are grouped by their
 * declared settings type ({@link FeatureDescriptor#declaredType}), so a value of a subclass counts as the type it is
 * declared as. Build once and reuse it for every invocation with the same configuration.
 */
public final class ConfiguredSettings {
    private static final ConfiguredSettings NONE = new ConfiguredSettings(Map.of());

    private final Map<Class<?>, SettingsStack<?>> stacks;

    private ConfiguredSettings(Map<Class<?>, SettingsStack<?>> stacks) {
        this.stacks = Map.copyOf(stacks);
    }

    public static ConfiguredSettings none() {
        return NONE;
    }

    /** These entries, grouped by declared settings type, each group in level order and otherwise as given. */
    public static ConfiguredSettings of(List<? extends Entry<?>> entries) {
        var grouped = new HashMap<Class<?>, List<Entry<?>>>();
        for (var entry : entries) {
            Objects.requireNonNull(entry, "entry");
            grouped.computeIfAbsent(FeatureDescriptor.declaredType(entry.value().getClass()), ignored -> new ArrayList<>()).add(entry);
        }
        if (grouped.isEmpty()) {
            return NONE;
        }
        var stacks = new HashMap<Class<?>, SettingsStack<?>>();
        grouped.forEach((type, group) -> stacks.put(type, stackOf(group)));
        return new ConfiguredSettings(stacks);
    }

    /** The stacks of these types, each kept under its own type; empty stacks are left out. */
    static ConfiguredSettings ofStacks(Map<Class<?>, SettingsStack<?>> stacks) {
        var configured = new HashMap<Class<?>, SettingsStack<?>>();
        stacks.forEach((type, stack) -> {
            if (!stack.isEmpty()) {
                configured.put(type, stack);
            }
        });
        return configured.isEmpty() ? NONE : new ConfiguredSettings(configured);
    }

    /** One placement's own settings, at most one value per declared type, declared by {@code source}. */
    public static ConfiguredSettings placement(ResourceId source, IrisFeature... settings) {
        Objects.requireNonNull(source, "source");
        var entries = new ArrayList<Entry<?>>();
        var types = new HashMap<Class<?>, IrisFeature>();
        for (var value : settings) {
            Objects.requireNonNull(value, "settings");
            var declared = FeatureDescriptor.declaredType(value.getClass());
            if (types.putIfAbsent(declared, value) != null) {
                throw new IllegalArgumentException("Settings configured twice: " + declared.getName());
            }
            entries.add(new Entry<>(Level.PLACEMENT, source, value));
        }
        return of(entries);
    }

    /** The stack of {@code type}; empty when nothing configures it. */
    @SuppressWarnings("unchecked") // Stacks are grouped by the declared type of their values.
    public <S extends IrisFeature> SettingsStack<S> find(Class<S> type) {
        var found = stacks.get(type);
        return found == null ? SettingsStack.empty() : (SettingsStack<S>) found;
    }

    @SuppressWarnings({"unchecked", "rawtypes"}) // A group holds entries of one declared type.
    private static SettingsStack<?> stackOf(List<Entry<?>> group) {
        return SettingsStack.of((List) group);
    }
}
