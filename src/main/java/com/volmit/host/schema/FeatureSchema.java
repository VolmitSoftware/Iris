package com.volmit.host.schema;

import com.volmit.FeatureAttachment;
import com.volmit.ModuleInfo;
import com.volmit.resource.ResourceId;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** A module's settings type and the JSON keys it is written under at each of its targets.
 *
 * @param id the feature id from {@code @Feature}
 * @param name the feature's display name from {@code @Feature}
 * @param module the module that owns the feature id
 * @param key the JSON key the settings are written under, the feature id, the same at every target
 * @param alias the short JSON key a built-in module accepts instead of {@code key}; pack modules
 *     have none. A JSON object may use one of the two, not both.
 * @param targets where the keys appear, in {@link FeatureAttachment.Target} order
 * @param settings the settings class; its description describes the value
 */
public record FeatureSchema(
        ResourceId id,
        String name,
        FeatureRole role,
        ModuleInfo module,
        String key,
        Optional<String> alias,
        Set<FeatureAttachment.Target> targets,
        ObjectType settings) {
    public FeatureSchema {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(alias, "alias");
        Objects.requireNonNull(settings, "settings");
        targets = Collections.unmodifiableSet(EnumSet.copyOf(targets));
    }
}
