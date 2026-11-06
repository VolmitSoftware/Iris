package com.volmit.host;

import com.volmit.resource.ResourceKind;

/** The settings or resource kind, and its module, that first reached a class while describing a pack; load errors name them.
 *
 * @param subject what reached the class, such as {@code settings example:flora}
 * @param type the class the subject is read into
 */
record SchemaSource(String subject, Class<?> type, DiscoveredModule module) {
    static SchemaSource settings(FeatureDescriptor<?> feature, DiscoveredModule module) {
        return new SchemaSource("settings " + feature.id(), feature.type(), module);
    }

    static SchemaSource resourceKind(ResourceKind<?> kind, DiscoveredModule module) {
        return new SchemaSource("resource kind " + kind.id(), kind.type(), module);
    }

    @Override
    public String toString() {
        return subject + " (module " + module + ")";
    }
}
