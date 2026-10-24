package com.volmit.host;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** The packages of available JDK modules that pack modules can load classes from but not use. The
 * platform class loader finds every class of its modules, but a module lets other code use only the
 * packages it exports to it, so a class such as {@code jdk.internal.misc.Unsafe} fails with an
 * {@link IllegalAccessError} once code touches it. Pack classes live in their loader's unnamed
 * module, so a package counts as exported only when every unnamed module may use it: exported
 * unqualified, or opened up with {@code --add-exports <module>/<package>=ALL-UNNAMED} on the server's
 * command line. Exports to named modules only do not count. Judged from the boot layer's modules, so
 * nothing is loaded.
 *
 * <p>Modules the platform loader does not provide are {@link UnavailableJdkModules}, not listed here.
 */
final class NonExportedJdkPackages {
    private static final char PACKAGE_SEPARATOR = '.';

    private NonExportedJdkPackages() {}

    /** A JDK package and the module that holds it. */
    record JdkPackage(String name, String module) {}

    /** The class's package, if an available JDK module holds it without exporting it to packs. */
    static Optional<JdkPackage> packageOf(String binaryName) {
        int separator = binaryName.lastIndexOf(PACKAGE_SEPARATOR);
        if (separator < 0) return Optional.empty();
        return Optional.ofNullable(PackagesByName.NOT_EXPORTED.get(binaryName.substring(0, separator)));
    }

    private static Stream<JdkPackage> packagesNotExportedToPacks(Module module) {
        return module.getPackages().stream()
                .filter(name -> !module.isExported(name, PackagesByName.UNNAMED_MODULE_LIKE_A_PACK))
                .map(name -> new JdkPackage(name, module.getName()));
    }

    /** Built on first use, once per runtime: the boot layer's exports are fixed at startup. */
    private static final class PackagesByName {
        /** The unnamed module of a loader that defines nothing and that no export names, so it may use
         * exactly what every unnamed module may use, like a new pack loader's.
         */
        static final Module UNNAMED_MODULE_LIKE_A_PACK = new ClassLoader("iris-jdk-export-probe", null) {}.getUnnamedModule();

        static final Map<String, JdkPackage> NOT_EXPORTED = ModuleLayer.boot().modules().stream()
                .filter(UnavailableJdkModules::platformLoaderProvides)
                .flatMap(NonExportedJdkPackages::packagesNotExportedToPacks)
                .collect(Collectors.toUnmodifiableMap(JdkPackage::name, Function.identity()));

        private PackagesByName() {}
    }
}
