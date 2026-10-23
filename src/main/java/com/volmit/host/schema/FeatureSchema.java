package com.volmit.host.schema;

import com.volmit.ModuleInfo;
import com.volmit.config.Level;
import com.volmit.resource.ResourceId;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** A module's settings type and the JSON key it is written under at each of its targets.
 *
 * @param id the feature id from {@code @Feature}
 * @param name the feature's display name from {@code @Feature}
 * @param module the module that owns the feature id
 * @param key the JSON key the settings are written under, declared by the feature, the same at every target
 * @param root the option whose value the key holds, when the JSON is that value and not an object of options
 * @param targets the levels whose files hold the key, in {@link Level} order
 * @param settings the settings class; its description describes the value
 */
public record FeatureSchema(
        ResourceId id,
        String name,
        FeatureRole role,
        ModuleInfo module,
        String key,
        Optional<String> root,
        Set<Level> targets,
        ObjectType settings) {
    public FeatureSchema {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(settings, "settings");
        targets = Collections.unmodifiableSet(EnumSet.copyOf(targets));
        root.ifPresent(option -> requireOption(settings, option));
    }

    /** The option the key holds the value of, when the JSON under the key is one option's value. Then the
     * key takes exactly that value, such as a list, and not an object of options.
     */
    public Optional<SchemaOption> rootOption() {
        return root.map(option -> requireOption(settings, option));
    }

    private static SchemaOption requireOption(ObjectType settings, String option) {
        return settings.options().stream()
                .filter(candidate -> candidate.name().equals(option))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Root " + option + " is no option of " + settings.name()));
    }
}
