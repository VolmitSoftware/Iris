package com.volmit.host;

import com.volmit.IrisModule;
import com.volmit.PackageName;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/** The host packages pack modules may use: always the Iris API, plus whatever the host adds, such
 * as the packages of built-in modules whose stages packs may require. A package is exposed with
 * all its subpackages, and only whole package names match: {@code com.volmit} does not expose
 * {@code com.volmitx}.
 */
final class ExposedPackages {
    /** The root package of the Iris API. Module classes must not use it. */
    static final String API_PACKAGE = IrisModule.class.getPackageName();
    private static final PackageName API = new PackageName(API_PACKAGE);
    private static final char PACKAGE_SEPARATOR = '.';
    private static final char PATH_SEPARATOR = '/';

    private final List<PackageName> packages;

    private ExposedPackages(List<PackageName> packages) {
        this.packages = List.copyOf(packages);
    }

    static ExposedPackages apiOnly() {
        return apiAnd(List.of());
    }

    /** The API plus {@code hostPackages}. A trailing dot is accepted; anything that is not a package
     * name, such as a blank string or a wildcard, is rejected.
     */
    static ExposedPackages apiAnd(Collection<String> hostPackages) {
        Objects.requireNonNull(hostPackages, "hostPackages");
        var packages = new TreeSet<PackageName>();
        packages.add(API);
        hostPackages.forEach(name -> packages.add(requirePackageName(name)));
        return new ExposedPackages(List.copyOf(packages));
    }

    static boolean isApiClass(String binaryName) {
        return API.contains(binaryName);
    }

    boolean exposes(String binaryName) {
        return packages.stream().anyMatch(name -> name.contains(binaryName));
    }

    /** Whether a resource path, such as {@code com/volmit/IrisModule.class}, lies in an exposed package. */
    boolean exposesResource(String path) {
        return exposes(path.replace(PATH_SEPARATOR, PACKAGE_SEPARATOR));
    }

    /** The exposed package a class is in; the most specific one when exposed packages nest. */
    String packageOf(String binaryName) {
        return packages.stream()
                .filter(name -> name.contains(binaryName))
                .map(PackageName::name)
                .max(Comparator.comparingInt(String::length))
                .orElseThrow(() -> new IllegalArgumentException(binaryName + " is not in an exposed package"));
    }

    private static PackageName requirePackageName(String name) {
        Objects.requireNonNull(name, "exposed package");
        return PackageName.tryParse(name).orElseThrow(() -> new IllegalArgumentException("Exposed package '" + name
                + "' is not a package name; name a package such as art.arcane.iris.engine, which exposes that package"
                + " and its subpackages"));
    }
}
