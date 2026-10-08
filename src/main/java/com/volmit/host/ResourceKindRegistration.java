package com.volmit.host;

import com.volmit.resource.ResourceKind;
import java.util.Objects;

/** A resource kind and the module that registered it, which owns its id. */
public record ResourceKindRegistration(ResourceKind<?> kind, DiscoveredModule module) {
    public ResourceKindRegistration {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(module, "module");
    }

    @Override
    public String toString() {
        return "resource kind " + kind.id() + " of module " + module;
    }
}
