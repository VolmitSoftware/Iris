package com.volmit;

import com.volmit.resource.ResourceId;
import java.util.Objects;

/** Module identity. Stages a module defines must use the namespace of its id. */
public record ModuleInfo(ResourceId id, String version) {
    public ModuleInfo {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(version, "version");
        if (version.isBlank()) {
            throw new IllegalArgumentException("Blank module version: " + id);
        }
    }

    public static ModuleInfo of(String id, String version) {
        return new ModuleInfo(ResourceId.parse(id), version);
    }
}
