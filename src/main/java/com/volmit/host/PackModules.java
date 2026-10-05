package com.volmit.host;

import com.volmit.IrisModule;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** The module jars of one pack: every {@code <pack>/modules/*.jar}, one module per jar, ordered
 * by file name so the result never depends on directory listing order.
 *
 * <p>All jars of a pack share one class loader whose parent is the loader of the API. A pack jar
 * can therefore name another pack jar's stage constants in {@code requires(...)}, as well as
 * those of built-in modules visible to the API. Packs do not see each other's classes.
 *
 * <p>Close this when the pack unloads, after dropping everything built from its modules, such as
 * {@link LoadedModules}. Classes not loaded before then can no longer be loaded.
 */
public final class PackModules implements AutoCloseable {
    public static final String MODULES_DIRECTORY = "modules";
    static final String JAR_EXTENSION = ".jar";

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
        var loader = newPackClassLoader(packRoot, jars);
        try {
            return new PackModules(createModules(jars, loader), loader);
        } catch (RuntimeException | Error failure) {
            closeAfterFailure(loader, failure);
            throw failure;
        }
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

    private static URLClassLoader newPackClassLoader(Path packRoot, List<ModuleJar> jars) {
        var urls = jars.stream().map(ModuleJar::url).toArray(URL[]::new);
        return new URLClassLoader("pack:" + packRoot.getFileName(), urls, IrisModule.class.getClassLoader());
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
