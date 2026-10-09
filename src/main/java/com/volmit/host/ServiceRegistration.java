package com.volmit.host;

import java.util.Objects;

/** One provider of a service and the module that registered it. */
public record ServiceRegistration(Class<?> service, Object provider, DiscoveredModule module) {
    public ServiceRegistration {
        Objects.requireNonNull(service, "service");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(module, "module");
    }

    @Override
    public String toString() {
        return "provider of " + service.getName() + " from module " + module;
    }
}
