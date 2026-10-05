package com.volmit.host;

import com.volmit.IrisModule;
import java.util.Objects;

/** A module and where the host found it. Load errors name the origin so the author knows which
 * jar to fix.
 */
public record DiscoveredModule(IrisModule module, String origin) {
    static final String HOST_CLASSPATH = "host classpath";

    public DiscoveredModule {
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(origin, "origin");
        if (origin.isBlank()) {
            throw new IllegalArgumentException("Blank module origin: " + module.getClass().getName());
        }
    }

    /** A module that ships with the host instead of a pack. */
    public static DiscoveredModule builtIn(IrisModule module) {
        return new DiscoveredModule(module, HOST_CLASSPATH);
    }
}
