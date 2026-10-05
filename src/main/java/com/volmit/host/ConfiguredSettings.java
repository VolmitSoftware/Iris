package com.volmit.host;

import com.volmit.IrisFeature;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Feature settings configured for one invocation: the placement's settings, or the settings
 * the host resolved for a chunk. At most one value per type; merging and precedence between
 * dimension/region/biome/placement declarations stay host policy. Build once and reuse it for
 * every invocation with the same configuration.
 */
public final class ConfiguredSettings {
    private static final ConfiguredSettings NONE = new ConfiguredSettings(Map.of());

    private final Map<Class<?>, IrisFeature> values;

    private ConfiguredSettings(Map<Class<?>, IrisFeature> values) {
        this.values = Map.copyOf(values);
    }

    public static ConfiguredSettings none() {
        return NONE;
    }

    public static ConfiguredSettings of(IrisFeature... settings) {
        var values = new HashMap<Class<?>, IrisFeature>();
        for (var value : settings) {
            Objects.requireNonNull(value, "settings");
            if (values.putIfAbsent(value.getClass(), value) != null) {
                throw new IllegalArgumentException("Settings configured twice: " + value.getClass().getName());
            }
        }
        return new ConfiguredSettings(values);
    }

    public <S extends IrisFeature> Optional<S> find(Class<S> type) {
        return Optional.ofNullable(type.cast(values.get(type)));
    }
}
