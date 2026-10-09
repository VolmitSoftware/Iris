package com.volmit.host;

import com.volmit.ModuleLifecycle;
import java.util.Objects;

/** Lifecycle hooks and the module that registered them, which the host names when a hook fails. */
public record LifecycleRegistration(ModuleLifecycle lifecycle, DiscoveredModule module) {
    public LifecycleRegistration {
        Objects.requireNonNull(lifecycle, "lifecycle");
        Objects.requireNonNull(module, "module");
    }
}
