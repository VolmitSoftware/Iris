package com.volmit;

import com.volmit.resource.ResourceId;
import java.util.Objects;

/** Module identity. Stages a module defines must use the namespace of its id. The name is for
 * people, for example in load logs; the id is what the host and other modules match on.
 */
public record ModuleInfo(ResourceId id, String name, String version) {
    public ModuleInfo {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(version, "version");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Blank module name: " + id);
        }
        if (version.isBlank()) {
            throw new IllegalArgumentException("Blank module version: " + id);
        }
    }

    public static ModuleInfo of(String id, String name, String version) {
        return new ModuleInfo(ResourceId.parse(id), name, version);
    }
}
