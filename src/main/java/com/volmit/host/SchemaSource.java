package com.volmit.host;

/** The settings, and their module, that first reached a class while describing a pack; load errors name them. */
record SchemaSource(FeatureDescriptor<?> feature, DiscoveredModule module) {
    @Override
    public String toString() {
        return "settings " + feature.id() + " (module " + module + ")";
    }
}
