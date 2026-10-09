package com.volmit.host;

import com.volmit.vanilla.VanillaStructureHook;
import java.util.Objects;

/** A vanilla structure hook and the module that registered it, which the host names when the hook fails. */
public record VanillaStructureHookRegistration(VanillaStructureHook hook, DiscoveredModule module) {
    public VanillaStructureHookRegistration {
        Objects.requireNonNull(hook, "hook");
        Objects.requireNonNull(module, "module");
    }
}
