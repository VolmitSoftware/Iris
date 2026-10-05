package com.volmit.host;

import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** The JDK modules whose classes pack modules cannot use, by package. Packs see the JDK through the
 * platform class loader, which provides the modules defined to it and to the boot loader. Other JDK
 * modules, such as {@code jdk.compiler} or {@code jdk.jshell}, are defined to the application class
 * loader, or not resolved at all, so a pack never sees them. Judged from module descriptors, so
 * nothing is loaded.
 */
final class UnavailableJdkModules {
    private static final char PACKAGE_SEPARATOR = '.';

    private UnavailableJdkModules() {}

    /** The JDK module that holds the class's package, if packs cannot use that module. */
    static Optional<String> moduleOf(String binaryName) {
        int separator = binaryName.lastIndexOf(PACKAGE_SEPARATOR);
        if (separator < 0) return Optional.empty();
        return Optional.ofNullable(ModulesByPackage.UNAVAILABLE.get(binaryName.substring(0, separator)));
    }

    private static boolean platformLoaderProvides(String moduleName) {
        return ModuleLayer.boot().findModule(moduleName)
                .filter(module -> isPlatformOrBootLoader(module.getClassLoader()))
                .isPresent();
    }

    /** The boot loader is {@code null}; the platform loader delegates to it. */
    private static boolean isPlatformOrBootLoader(ClassLoader loader) {
        return loader == null || loader == ClassLoader.getPlatformClassLoader();
    }

    /** Built on first use, once per runtime: the JDK's modules do not change while it runs. */
    private static final class ModulesByPackage {
        static final Map<String, String> UNAVAILABLE = ModuleFinder.ofSystem().findAll().stream()
                .map(ModuleReference::descriptor)
                .filter(descriptor -> !platformLoaderProvides(descriptor.name()))
                .flatMap(descriptor -> descriptor.packages().stream().map(name -> Map.entry(name, descriptor.name())))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));

        private ModulesByPackage() {}
    }
}
