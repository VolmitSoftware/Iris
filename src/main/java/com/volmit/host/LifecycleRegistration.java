package com.volmit.host;

import com.volmit.ModuleLifecycle;
import com.volmit.ModuleLog;
import com.volmit.PackContext;
import com.volmit.config.PackSettings;
import com.volmit.resource.PackResources;
import com.volmit.world.PackWorld;
import java.util.Objects;

/** Lifecycle hooks and the module that registered them, which the host names when a hook fails. */
public record LifecycleRegistration(ModuleLifecycle lifecycle, DiscoveredModule module) {
    public LifecycleRegistration {
        Objects.requireNonNull(lifecycle, "lifecycle");
        Objects.requireNonNull(module, "module");
    }

    /** {@code world} as this hook's module sees it, which the host hands to the hook so that {@link PackWorld#log} is the
     * module's own.
     */
    PackWorld scope(PackWorld world) {
        return world.withLog(module.log());
    }

    /** The pack snapshot as this hook's module sees it, whose {@link PackContext#log} is the module's own. */
    PackContext pack(PackResources resources, PackSettings settings) {
        return new LoadedPackContext(resources, settings, module.log());
    }

    private record LoadedPackContext(PackResources resources, PackSettings settings, ModuleLog log) implements PackContext {}
}
