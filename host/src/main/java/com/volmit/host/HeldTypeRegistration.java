package com.volmit.host;

import java.util.Objects;

/** A class of a built-in module that Iris's own classes hold, and the module that registered it.
 *
 * @deprecated Transitional, until the cut-over; see {@link com.volmit.FeatureRegistry#registerHeldType}.
 */
@Deprecated(forRemoval = true)
public record HeldTypeRegistration(Class<?> type, DiscoveredModule module) {
    public HeldTypeRegistration {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(module, "module");
    }

    @Override
    public String toString() {
        return "held type " + type.getName() + " of module " + module;
    }
}
