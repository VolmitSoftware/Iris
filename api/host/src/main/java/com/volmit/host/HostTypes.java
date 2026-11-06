package com.volmit.host;

import java.util.Collection;
import java.util.List;

/** Classes the host describes in studio itself, such as Iris's palettes and noise styles, which built-in
 * settings may hold. Given as packages, each with its subpackages. Pack modules cannot use them.
 */
final class HostTypes {
    static final HostTypes NONE = new HostTypes(List.of());

    private final List<PackageName> packages;

    private HostTypes(List<PackageName> packages) {
        this.packages = packages;
    }

    static HostTypes of(Collection<String> packages) {
        return new HostTypes(packages.stream().map(HostTypes::parse).toList());
    }

    boolean contains(Class<?> type) {
        var name = type.getName();
        return packages.stream().anyMatch(packageName -> packageName.contains(name));
    }

    private static PackageName parse(String name) {
        return PackageName.tryParse(name).orElseThrow(() -> new IllegalArgumentException(
                "Host type package '" + name + "' is not a package name, such as art.arcane.iris.engine.object"));
    }
}
