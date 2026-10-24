package com.volmit.host;

import com.volmit.world.PackWorld;
import java.util.Objects;

/** One provider of a service and the module that registered it. */
public record ServiceRegistration(Class<?> service, Object provider, DiscoveredModule module) {
    public ServiceRegistration {
        Objects.requireNonNull(service, "service");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(module, "module");
    }

    /** {@code world} as this provider's module sees it, which the host hands to the provider's methods so that
     * {@link PackWorld#log} is the module's own.
     */
    public PackWorld scope(PackWorld world) {
        return ModuleWorld.of(world, module.log());
    }

    @Override
    public String toString() {
        return "provider of " + service.getName() + " from module " + module;
    }
}
