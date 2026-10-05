package com.volmit.host;

import com.volmit.IrisModule;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.System.Logger.Level;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** The module jars of one pack: every {@code <pack>/modules/*.jar}, one module per jar, ordered
 * by file name so the result never depends on directory listing order.
 *
 * <p>All jars of a pack share one class loader. A pack jar can therefore name another pack jar's
 * stage constants in {@code requires(...)}. Besides the pack's own classes, that loader sees only
 * the exposed host packages and the JDK ({@link ExposedHostClassLoader}); packs do not see each
 * other or Iris internals. The Iris API is always exposed; the host may expose more packages, such
 * as those of built-in modules whose stages packs may require. Before the loader exists, the load
 * fails if a jar holds classes of an exposed package, if a class is in two jars, or if a class uses
 * a class that Iris hides from modules or that is missing from an exposed package.
 * Classes that nobody has are not an error, because the JVM only needs them when code that uses
 * them runs; they are reported as {@link #missingClasses()}.
 *
 * <p>A successful load logs one INFO summary of the pack's modules through {@link System.Logger},
 * which the host can route to its own logging, and one WARNING listing the missing classes, if any.
 *
 * <p>Close this when the pack unloads, after dropping everything built from its modules, such as
 * {@link LoadedModules}. Classes not loaded before then can no longer be loaded.
 */
public final class PackModules implements AutoCloseable {
    public static final String MODULES_DIRECTORY = "modules";
    static final String JAR_EXTENSION = ".jar";
    private static final System.Logger LOGGER = System.getLogger(PackModules.class.getName());

    private final List<DiscoveredModule> modules;
    private final List<MissingClasses> missingClasses;
    private final URLClassLoader loader;

    private PackModules(List<DiscoveredModule> modules, List<MissingClasses> missingClasses, URLClassLoader loader) {
        this.modules = List.copyOf(modules);
        this.missingClasses = List.copyOf(missingClasses);
        this.loader = loader;
    }

    /** Loads the modules of the pack at {@code packRoot}, exposing only the Iris API. */
    public static PackModules load(Path packRoot) {
        return load(packRoot, List.of());
    }

    /** Loads the modules of the pack at {@code packRoot}. A pack without a modules directory has
     * no modules. Errors name the offending jar relative to the pack root.
     *
     * @param exposedHostPackages host packages pack modules may use besides the Iris API, which is
     *     always exposed, such as the packages of built-in modules whose stages packs may require.
     *     Each exposes the package and its subpackages from the loader that defines the API. A
     *     trailing dot is accepted; blank names and wildcards are rejected.
     */
    public static PackModules load(Path packRoot, Collection<String> exposedHostPackages) {
        Objects.requireNonNull(packRoot, "packRoot");
        var host = new ExposedHostClassLoader(IrisModule.class.getClassLoader(), ExposedPackages.apiAnd(exposedHostPackages));
        var jars = findModuleJars(packRoot).stream()
                .map(jar -> ModuleJar.read(packRoot, jar))
                .toList();
        var missingClasses = PackClassCheck.requireLinkable(jars, host);
        var packModules = defineModules(packRoot, jars, missingClasses, host);
        logLoad(packName(packRoot), packModules);
        return packModules;
    }

    /** One module per jar, in jar file name order. */
    public List<DiscoveredModule> modules() {
        return modules;
    }

    /** Per jar that uses any, in jar file name order, the classes that neither the pack, Iris nor the
     * JDK has. Empty when every class the jars use exists.
     */
    public List<MissingClasses> missingClasses() {
        return missingClasses;
    }

    @Override
    public void close() {
        try {
            loader.close();
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot close module class loader " + loader.getName(), exception);
        }
    }

    private static List<Path> findModuleJars(Path packRoot) {
        var directory = packRoot.resolve(MODULES_DIRECTORY);
        if (!Files.isDirectory(directory)) return List.of();
        try (var entries = Files.list(directory)) {
            return entries
                    .filter(PackModules::isJar)
                    .sorted(Comparator.comparing(jar -> jar.getFileName().toString()))
                    .toList();
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot list module jars in " + directory, exception);
        }
    }

    private static boolean isJar(Path path) {
        return Files.isRegularFile(path) && path.getFileName().toString().endsWith(JAR_EXTENSION);
    }

    private static PackModules defineModules(
            Path packRoot, List<ModuleJar> jars, List<MissingClasses> missingClasses, ClassLoader host) {
        var loader = newPackClassLoader(packRoot, jars, host);
        try {
            return new PackModules(createModules(jars, loader), missingClasses, loader);
        } catch (RuntimeException | Error failure) {
            closeAfterFailure(loader, failure);
            throw failure;
        }
    }

    private static URLClassLoader newPackClassLoader(Path packRoot, List<ModuleJar> jars, ClassLoader host) {
        var urls = jars.stream().map(ModuleJar::url).toArray(URL[]::new);
        return new URLClassLoader("pack:" + packName(packRoot), urls, host);
    }

    private static void logLoad(String packName, PackModules packModules) {
        LOGGER.log(Level.INFO, () -> PackModulesSummary.describe(packName, packModules.modules()));
        if (packModules.missingClasses().isEmpty()) return;
        LOGGER.log(Level.WARNING, () -> MissingClassesWarning.describe(packName, packModules.missingClasses()));
    }

    private static String packName(Path packRoot) {
        var name = packRoot.getFileName();
        return name == null ? packRoot.toString() : name.toString();
    }

    private static List<DiscoveredModule> createModules(List<ModuleJar> jars, ClassLoader loader) {
        return jars.stream().map(jar -> jar.createModule(loader)).toList();
    }

    private static void closeAfterFailure(URLClassLoader loader, Throwable failure) {
        try {
            loader.close();
        } catch (IOException exception) {
            failure.addSuppressed(exception);
        }
    }
}
