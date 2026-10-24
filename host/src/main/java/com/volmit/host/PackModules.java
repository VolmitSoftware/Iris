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
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** The module jars of one pack: every {@code <pack>/modules/*.jar}, each with the modules it lists,
 * ordered by file name so the result never depends on directory listing order. Hosts normally load packs
 * through {@link LoadedPack}, which uses this.
 *
 * <p>The load first copies the jars into a fresh temporary directory ({@link JarSnapshot}) and works
 * only on the copies, so the originals stay free to be replaced, even on Windows, and the load sees one
 * consistent set of jars. Messages and origins name the originals ({@code modules/moss.jar}). Copies
 * are deleted when the pack closes or its load fails; copies a crashed process left behind are removed
 * by a later load ({@link StaleJarSnapshots}).
 *
 * <p>All jars of a pack share one class loader. A pack jar can therefore name another pack jar's
 * stage constants in {@code requires(...)}. Besides the pack's own classes, that loader sees only
 * the exposed host packages and the JDK ({@link ExposedHostClassLoader}); packs do not see each
 * other or Iris internals. The Iris API is always exposed; the host may expose more packages, such
 * as those of built-in modules whose stages packs may require. Before the loader exists, the load
 * fails if a jar holds classes of an exposed package, if a class is in two jars, or if a class uses
 * a class that Iris hides from modules or that is missing from an exposed package.
 * Classes that nobody has are not an error, because the JVM only needs them when code that uses
 * them runs; they are reported as {@link #missingClasses()}, except those in the optional packages
 * the jar's module declares in its {@link com.volmit.ModuleInfo}.
 *
 * <p>A successful load logs one INFO summary of the pack's modules through a {@link System.Logger}, by
 * default the one named after this class, or the host's from {@link ModuleHost#loggingTo}, and one
 * WARNING listing the missing classes, if any.
 *
 * <p>Close this when the pack unloads, after dropping everything built from its modules, such as
 * {@link LoadedModules}. Classes not loaded before then can no longer be loaded.
 */
public final class PackModules implements AutoCloseable {
    public static final String MODULES_DIRECTORY = "modules";
    static final String JAR_EXTENSION = ".jar";
    static final String BUNDLED_ORIGIN_SUFFIX = " (bundled with the host)";
    private static final String TEMPORARY_DIRECTORY_PROPERTY = "java.io.tmpdir";
    static final System.Logger DEFAULT_LOGGER = System.getLogger(PackModules.class.getName());

    private final List<DiscoveredModule> modules;
    private final List<MissingClasses> missingClasses;
    private final URLClassLoader loader;
    private final JarSnapshot snapshot;

    private PackModules(
            List<DiscoveredModule> modules, List<MissingClasses> missingClasses, URLClassLoader loader, JarSnapshot snapshot) {
        this.modules = List.copyOf(modules);
        this.missingClasses = List.copyOf(missingClasses);
        this.loader = loader;
        this.snapshot = snapshot;
    }

    /** Loads the modules of the pack at {@code packRoot}, exposing only the Iris API. */
    public static PackModules load(Path packRoot) {
        return load(packRoot, List.of());
    }

    /** Loads the modules of the pack at {@code packRoot}. A pack without a modules directory has
     * no modules. A load error is a {@link ModuleLoadException} that names the offending jar relative to the pack root.
     *
     * @param exposedHostPackages host packages pack modules may use besides the Iris API, which is
     *     always exposed, such as the packages of built-in modules whose stages packs may require.
     *     Each exposes the package and its subpackages from the loader that defines the API. A
     *     trailing dot is accepted; blank names and wildcards are rejected.
     */
    public static PackModules load(Path packRoot, Collection<String> exposedHostPackages) {
        return load(packRoot, exposedHostPackages, systemTemporaryDirectory());
    }

    /** Like {@link #load(Path, Collection)}, copying the jars below {@code temporaryRoot}. */
    static PackModules load(Path packRoot, Collection<String> exposedHostPackages, Path temporaryRoot) {
        return load(packRoot, exposedHostPackages, temporaryRoot, DEFAULT_LOGGER);
    }

    /** Like {@link #load(Path, Collection)}, copying the jars below {@code temporaryRoot} and logging to {@code logger}. */
    static PackModules load(Path packRoot, Collection<String> exposedHostPackages, Path temporaryRoot, System.Logger logger) {
        return load(packRoot, exposedHostPackages, List.of(), temporaryRoot, logger);
    }

    /** Like {@link #load(Path, Collection, Path, System.Logger)}, also loading the host's {@code bundledJars} as if they were
     * in the pack, before its own jars and in the given order.
     */
    static PackModules load(Path packRoot, Collection<String> exposedHostPackages, List<Path> bundledJars, Path temporaryRoot,
                            System.Logger logger) {
        Objects.requireNonNull(packRoot, "packRoot");
        Objects.requireNonNull(logger, "logger");
        var host = new ExposedHostClassLoader(IrisModule.class.getClassLoader(), ExposedPackages.apiAnd(exposedHostPackages));
        StaleJarSnapshots.remove(temporaryRoot);
        var snapshot = JarSnapshot.take(sourceJars(packRoot, bundledJars, findModuleJars(packRoot)), temporaryRoot);
        try {
            var packModules = loadSnapshot(packRoot, snapshot, host, logger);
            logLoad(logger, packName(packRoot), packModules);
            return packModules;
        } catch (RuntimeException failure) {
            snapshot.deleteAfterFailure(failure);
            throw ModuleLoadException.of(failure);
        } catch (Error failure) {
            snapshot.deleteAfterFailure(failure);
            throw failure;
        }
    }

    /** The modules of all jars, in jar file name order and, within a jar, in the order it lists them. */
    public List<DiscoveredModule> modules() {
        return modules;
    }

    /** Per jar that uses any, in jar file name order, the classes that neither the pack, Iris nor the
     * JDK has and that are outside the optional packages the jar's module declares. Empty when every
     * class the jars use exists or is declared optional.
     */
    public List<MissingClasses> missingClasses() {
        return missingClasses;
    }

    /** Closes the class loader, then deletes the jar copies, which an open loader would keep locked on Windows. */
    @Override
    public void close() {
        try {
            loader.close();
        } catch (IOException exception) {
            var failure = new UncheckedIOException("Cannot close module class loader " + loader.getName(), exception);
            snapshot.deleteAfterFailure(failure);
            throw failure;
        }
        snapshot.delete();
    }

    private static Path systemTemporaryDirectory() {
        return Path.of(System.getProperty(TEMPORARY_DIRECTORY_PROPERTY));
    }

    private static PackModules loadSnapshot(Path packRoot, JarSnapshot snapshot, ExposedHostClassLoader host, System.Logger logger) {
        var jars = snapshot.jars().stream().map(ModuleJar::read).toList();
        var missingClasses = PackClassCheck.requireLinkable(jars, host);
        return defineModules(packRoot, jars, missingClasses, host, snapshot, logger);
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
            throw new ModuleLoadException(null, null, "Cannot list module jars in " + directory, exception);
        }
    }

    private static List<JarSnapshot.SourceJar> sourceJars(Path packRoot, List<Path> bundledJars, List<Path> packJars) {
        var bundled = bundledJars.stream().map(jar -> new JarSnapshot.SourceJar(bundledOrigin(jar), jar));
        var own = packJars.stream().map(jar -> new JarSnapshot.SourceJar(packRoot.relativize(jar).toString(), jar));
        return Stream.concat(bundled, own).toList();
    }

    /** How messages name a bundled jar: its file name, marked as the host's. */
    static String bundledOrigin(Path bundledJar) {
        return fileName(bundledJar) + BUNDLED_ORIGIN_SUFFIX;
    }

    private static String fileName(Path jar) {
        return jar.getFileName().toString();
    }

    private static boolean isJar(Path path) {
        return Files.isRegularFile(path) && path.getFileName().toString().endsWith(JAR_EXTENSION);
    }

    private static PackModules defineModules(
            Path packRoot, List<ModuleJar> jars, List<MissingClasses> missingClasses, ClassLoader host, JarSnapshot snapshot,
            System.Logger logger) {
        var loader = newPackClassLoader(packRoot, jars, host);
        try {
            var modules = createModules(jars, loader, snapshot, logger);
            return new PackModules(modules, withoutDeclaredOptional(missingClasses, modules), loader, snapshot);
        } catch (RuntimeException | Error failure) {
            closeAfterFailure(loader, failure);
            throw failure;
        }
    }

    private static URLClassLoader newPackClassLoader(Path packRoot, List<ModuleJar> jars, ClassLoader host) {
        var urls = jars.stream().map(ModuleJar::url).toArray(URL[]::new);
        return new URLClassLoader("pack:" + packName(packRoot), urls, host);
    }

    private static void logLoad(System.Logger logger, String packName, PackModules packModules) {
        logger.log(Level.INFO, () -> PackModulesSummary.describe(packName, packModules.modules()));
        if (packModules.missingClasses().isEmpty()) return;
        logger.log(Level.WARNING, () -> MissingClassesWarning.describe(packName, packModules.missingClasses()));
    }

    private static String packName(Path packRoot) {
        var name = packRoot.getFileName();
        return name == null ? packRoot.toString() : name.toString();
    }

    private static List<DiscoveredModule> createModules(
            List<ModuleJar> jars, ClassLoader loader, JarSnapshot snapshot, System.Logger logger) {
        return jars.stream()
                .flatMap(jar -> jar.createModules(loader, snapshot).stream())
                .map(module -> module.loggingTo(logger))
                .toList();
    }

    /** A module's optional packages excuse only its own jar's missing classes; a jar with several
     * modules is excused by the optional packages of all of them. They are read from {@code info()},
     * so this runs after the entry classes exist; errors were decided before that and never depend on them.
     */
    private static List<MissingClasses> withoutDeclaredOptional(List<MissingClasses> missingClasses, List<DiscoveredModule> modules) {
        var optionalPackages = modules.stream()
                .collect(Collectors.groupingBy(DiscoveredModule::origin,
                        Collectors.flatMapping(found -> found.info().optionalPackages().stream(), Collectors.toList())));
        return missingClasses.stream()
                .map(jar -> jar.outside(optionalPackages.get(jar.origin())))
                .filter(Predicate.not(MissingClasses::isEmpty))
                .toList();
    }

    private static void closeAfterFailure(URLClassLoader loader, Throwable failure) {
        try {
            loader.close();
        } catch (IOException exception) {
            failure.addSuppressed(exception);
        }
    }
}
