package com.volmit.host;

import com.volmit.resource.ResourceId;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Namespaces that only modules from the host's classpath may use, such as {@code iris}. A pack
 * module's id must use another namespace. Its stages, settings ids and mantle keys follow its
 * namespace, so none of them can be in a reserved namespace either.
 */
final class ReservedNamespaces {
    private final Set<String> namespaces;

    private ReservedNamespaces(Set<String> namespaces) {
        this.namespaces = namespaces;
    }

    /** Fails on anything that is not a namespace, such as a blank string or a full id. */
    static ReservedNamespaces of(Collection<String> namespaces) {
        Objects.requireNonNull(namespaces, "reservedNamespaces");
        var valid = new TreeSet<String>();
        for (var namespace : namespaces) {
            valid.add(requireNamespace(namespace));
        }
        return new ReservedNamespaces(Set.copyOf(valid));
    }

    void requireAllowed(DiscoveredModule discovered) {
        if (discovered.source() == DiscoveredModule.Source.HOST) return;
        var namespace = discovered.info().id().namespace();
        if (!namespaces.contains(namespace)) return;
        throw new IllegalArgumentException("Module " + discovered.info().id() + " from " + discovered.origin()
                + " uses reserved namespace '" + namespace + "', which only modules that ship with the host may use;"
                + " give the module a namespace of its own, which its stages, settings ids and mantle keys then use too");
    }

    private static String requireNamespace(String namespace) {
        Objects.requireNonNull(namespace, "reserved namespace");
        if (!ResourceId.isNamespace(namespace)) {
            throw new IllegalArgumentException("Reserved namespace '" + namespace
                    + "' is not a namespace; name one such as iris, without ':' or a path");
        }
        return namespace;
    }
}
