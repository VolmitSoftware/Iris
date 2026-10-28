package com.volmit.host;

import java.util.Objects;
import java.util.Set;

/** The type a pack's object placements are written as, its JSON field names and the module that declared it. */
public record PlacementTypeRegistration(Class<?> type, Set<String> jsonFields, DiscoveredModule module) {
    public PlacementTypeRegistration {
        Objects.requireNonNull(type, "type");
        jsonFields = Set.copyOf(jsonFields);
        Objects.requireNonNull(module, "module");
    }

    @Override
    public String toString() {
        return "placement type " + type.getName() + " from module " + module;
    }
}
