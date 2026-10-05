package com.volmit.host;

import com.volmit.IrisModule;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.System.Logger.Level;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

/** The module jars of one pack: every {@code <pack>/modules/*.jar}, one module per jar, ordered
 * by file name so the result never depends on directory listing order.
 *
 * <p>All jars of a pack share one class loader whose parent is the loader of the API. A pack jar
 * can therefore name another pack jar's stage constants in {@code requires(...)}, as well as
 * those of built-in modules visible to the API. Packs do not see each other's classes, and no class
 * may appear in two jars of one pack.
 *
 * <p>A successful load logs one INFO summary of the pack's modules through {@link System.Logger},
 * which the host can route to its own logging.
 *
 * <p>Close this when the pack unloads, after dropping everything built from its modules, such as
 * {@link LoadedModules}. Classes not loaded before then can no longer be loaded.
 */
public final class PackModules implements AutoCloseable {
    public static final String MODULES_DIRECTORY = "modules";
    static final String JAR_EXTENSION = ".jar";
    private static final System.Logger LOGGER = System.getLogger(PackModules.class.getName());

    private final List<DiscoveredModule> modules;
    private final URLClassLoader loader;

    private PackModules(List<DiscoveredModule> modules, URLClassLoader loader) {
        this.modules = List.copyOf(modules);
        this.loader = loader;
    }

    /** Loads the modules of the pack at {@code packRoot}. A pack without a modules directory has
     * no modules. Errors name the offending jar relative to the pack root.
     */
    public static PackModules load(Path packRoot) {
        Objects.requireNonNull(packRoot, "packRoot");
        var jars = findModuleJars(packRoot).stream()
                .map(jar -> ModuleJar.read(packRoot, jar))
                .toList();
        requireDistinctClasses(jars);
        var packModules = defineModules(packRoot, jars);
        LOGGER.log(Level.INFO, () -> PackModulesSummary.describe(packName(packRoot), packModules.modules()));
        return packModules;
    }

    /** One module per jar, in jar file name order. */
    public List<DiscoveredModule> modules() {
        return modules;
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

    /** Fails before any class is defined when two jars hold the same class. The shared loader would
     * otherwise silently use whichever copy comes first in file name order.
     */
    private static void requireDistinctClasses(List<ModuleJar> jars) {
        var owners = new HashMap<String, ModuleJar>();
        for (var jar : jars) {
            for (var className : jar.classNames()) {
                var owner = owners.putIfAbsent(className, jar);
                if (owner != null) throw duplicateClass(className, owner, jar);
            }
        }
    }

    private static IllegalArgumentException duplicateClass(String className, ModuleJar first, ModuleJar second) {
        return new IllegalArgumentException("Class " + className + " is in both module jars " + first.origin()
                + " and " + second.origin() + "; every class of a pack must come from one jar, so relocate"
                + " shaded libraries or keep shared classes in one module");
    }

    private static PackModules defineModules(Path packRoot, List<ModuleJar> jars) {
        var loader = newPackClassLoader(packRoot, jars);
        try {
            return new PackModules(createModules(jars, loader), loader);
        } catch (RuntimeException | Error failure) {
            closeAfterFailure(loader, failure);
            throw failure;
        }
    }

    private static URLClassLoader newPackClassLoader(Path packRoot, List<ModuleJar> jars) {
        var urls = jars.stream().map(ModuleJar::url).toArray(URL[]::new);
        return new URLClassLoader("pack:" + packName(packRoot), urls, IrisModule.class.getClassLoader());
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
