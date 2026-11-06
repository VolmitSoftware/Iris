package com.volmit.host;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** A platform that tooling parts can target, described by what its parts may use: the packages of the platform's api,
 * which its loader provides, and the packages parts must never see. The description is the same on every server; the
 * classes behind it come from the {@link PlatformApi} the host runs on.
 *
 * <p>A part may use its own packages, the platform's api packages and what pack modules may use. A denied package wins
 * over an api package it lies in, and applies unless the host exposes the class to pack modules, so
 * {@code art.arcane.iris.builtin} stays usable while the rest of {@code art.arcane.iris} does not.
 *
 * @param name lower case letters and digits, such as {@code bukkit}; the platform's api package is
 *     {@code com.volmit.tooling.<name>}
 */
public record ToolingPlatform(String name, List<PackageName> apiPackages, List<PackageName> deniedPackages) {
    /** Paper and the servers built on it: Bukkit, Paper and Adventure through the Iris plugin's loader; Iris internals, the
     * server's own classes and the host side of IrisApi stay out of reach.
     */
    public static final ToolingPlatform BUKKIT = new ToolingPlatform("bukkit",
            packages("com.volmit.tooling.bukkit", "org.bukkit", "io.papermc.paper", "com.destroystokyo.paper", "net.kyori"),
            packages("art.arcane.iris", "net.minecraft", "org.bukkit.craftbukkit", "com.mojang", ExposedPackages.HOST_PACKAGE));

    public ToolingPlatform {
        Objects.requireNonNull(name, "name");
        if (!ToolingPart.isPlatformName(name)) {
            throw new IllegalArgumentException("Tooling platform '" + name + "' is not a platform name; use lower case letters and digits");
        }
        apiPackages = List.copyOf(apiPackages);
        deniedPackages = List.copyOf(deniedPackages);
    }

    /** The platform of the given name that this host describes. */
    static ToolingPlatform named(String name) {
        if (BUKKIT.name.equals(name)) return BUKKIT;
        throw new IllegalArgumentException("Unknown tooling platform '" + name + "'; this host knows " + BUKKIT.name);
    }

    boolean hasApiPackageOf(String binaryName) {
        return apiPackages.stream().anyMatch(name -> name.contains(binaryName));
    }

    boolean deniesPackageOf(String binaryName) {
        return deniedPackages.stream().anyMatch(name -> name.contains(binaryName));
    }

    private static List<PackageName> packages(String... names) {
        return Arrays.stream(names).map(PackageName::new).toList();
    }
}
