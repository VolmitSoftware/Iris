package com.volmit.host;

import com.volmit.IrisModule;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/** The host packages pack modules may use: always the Iris API, plus whatever the host adds, such
 * as the packages of built-in modules whose stages packs may require. A package is exposed with
 * all its subpackages, and only whole package names match: {@code com.volmit} does not expose
 * {@code com.volmitx}. The host side of IrisApi, {@code com.volmit.host}, lies inside the API's
 * root package but is never exposed: it loads and runs modules, and no module may reach it. The
 * platform packages of the tooling api, {@code com.volmit.tooling.<platform>}, are never exposed either:
 * only a tooling part may use them. The neutral {@code com.volmit.tooling} stays exposed.
 */
final class ExposedPackages {
    /** The root package of the Iris API. Module classes must not use it. */
    static final String API_PACKAGE = IrisModule.class.getPackageName();
    /** The host side of IrisApi, which packs never see, though it lies inside {@link #API_PACKAGE}. */
    static final String HOST_PACKAGE = ExposedPackages.class.getPackageName();
    /** The neutral tooling api. Each platform's own api is a subpackage of it, such as {@code com.volmit.tooling.bukkit}. */
    static final String TOOLING_PACKAGE = API_PACKAGE + ".tooling";
    private static final PackageName API = new PackageName(API_PACKAGE);
    private static final PackageName HOST = new PackageName(HOST_PACKAGE);
    private static final PackageName TOOLING = new PackageName(TOOLING_PACKAGE);
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
        hostPackages.forEach(name -> packages.add(requireExposable(requirePackageName(name))));
        return new ExposedPackages(List.copyOf(packages));
    }

    /** Whether the class lies in the API's root package, the hidden host side included; module classes must not. */
    static boolean isApiClass(String binaryName) {
        return API.contains(binaryName);
    }

    boolean exposes(String binaryName) {
        return reachesPacks(binaryName) && !isToolingPlatformPackage(packageNameOf(binaryName, PACKAGE_SEPARATOR));
    }

    /** Whether a resource path, such as {@code com/volmit/IrisModule.class}, lies in an exposed package. */
    boolean exposesResource(String path) {
        var directory = packageNameOf(path, PATH_SEPARATOR).replace(PATH_SEPARATOR, PACKAGE_SEPARATOR);
        return reachesPacks(path.replace(PATH_SEPARATOR, PACKAGE_SEPARATOR)) && !isToolingPlatformPackage(directory);
    }

    /** The exposed package a class is in; the most specific one when exposed packages nest. */
    String packageOf(String binaryName) {
        return packages.stream()
                .filter(name -> name.contains(binaryName))
                .map(PackageName::name)
                .max(Comparator.comparingInt(String::length))
                .orElseThrow(() -> new IllegalArgumentException(binaryName + " is not in an exposed package"));
    }

    private boolean reachesPacks(String dottedName) {
        return !HOST.contains(dottedName) && packages.stream().anyMatch(name -> name.contains(dottedName));
    }

    /** Whether the package lies below {@code com.volmit.tooling}, where each platform's api has its package. */
    private static boolean isToolingPlatformPackage(String packageName) {
        return TOOLING.contains(packageName);
    }

    /** What precedes the last separator: the package of a class name, or the directory of a resource path. */
    private static String packageNameOf(String name, char separator) {
        int lastSeparator = name.lastIndexOf(separator);
        return lastSeparator < 0 ? "" : name.substring(0, lastSeparator);
    }

    private static PackageName requireExposable(PackageName name) {
        if (name.equals(HOST) || HOST.contains(name.name())) {
            throw new IllegalArgumentException("Exposed package '" + name + "' is IrisApi's host side, which packs never see");
        }
        return name;
    }

    private static PackageName requirePackageName(String name) {
        Objects.requireNonNull(name, "exposed package");
        return PackageName.tryParse(name).orElseThrow(() -> new IllegalArgumentException("Exposed package '" + name
                + "' is not a package name; name a package such as art.arcane.iris.engine, which exposes that package"
                + " and its subpackages"));
    }
}
