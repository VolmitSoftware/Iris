package com.volmit.host.schema;

import com.volmit.FeatureAttachment;
import com.volmit.ModuleInfo;
import com.volmit.resource.ResourceId;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** A module's settings type and the JSON field it is attached to at each of its targets.
 *
 * @param id the feature id from {@code @Feature}
 * @param name the feature's display name from {@code @Feature}
 * @param module the module that owns the feature id
 * @param field the JSON field the settings are written under, the same at every target
 * @param targets where the field appears, in {@link FeatureAttachment.Target} order
 * @param settings the settings class; its description describes the field
 */
public record FeatureSchema(
        ResourceId id,
        String name,
        FeatureRole role,
        ModuleInfo module,
        String field,
        Set<FeatureAttachment.Target> targets,
        ObjectType settings) {
    public FeatureSchema {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(settings, "settings");
        targets = Collections.unmodifiableSet(EnumSet.copyOf(targets));
    }
}
