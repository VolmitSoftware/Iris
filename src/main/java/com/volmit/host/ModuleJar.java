package com.volmit.host;

import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipFile;

/** One jar in a pack's modules directory, the entry class it names and the classes it holds,
 * each with the classes it links against. {@code path} is the load's copy of the jar
 * ({@link JarSnapshot}); {@code origin} is the original's path relative to the pack, which every
 * message names.
 *
 * <p>The entry point is the jar's own {@code META-INF/services/com.volmit.IrisModule} file, in
 * the format {@link java.util.ServiceLoader} reads. The host reads that file from this jar only:
 * {@code ServiceLoader} would also return providers from parent class loaders and could not tell
 * which jar a broken entry came from.
 */
record ModuleJar(Path path, String origin, String entryClass, List<JarClass> classes) {
    static final String ENTRY_POINT_FILE = "META-INF/services/" + IrisModule.class.getName();
    private static final String COMMENT_START = "#";
    private static final String CLASS_FILE_SUFFIX = ".class";
    private static final String MODULE_DESCRIPTOR = "module-info" + CLASS_FILE_SUFFIX;
    private static final String META_INF = "META-INF/";

    ModuleJar {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(entryClass, "entryClass");
        classes = List.copyOf(classes);
    }

    /** A class in the jar, by binary name, and the binary names of the classes it links against. */
    record JarClass(String name, SortedSet<String> references) {
        JarClass {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(references, "references");
        }
    }

    /** Reads the single entry class a jar names and the classes it holds. Fails if a class file
     * cannot be parsed.
     */
    static ModuleJar read(JarSnapshot.CopiedJar copied) {
        var origin = copied.origin();
        var jar = copied.copy();
        try (var file = openAsClassLoaderSeesIt(jar)) {
            var entryClass = requireSingleEntryClass(origin, readEntryClasses(file));
            var classEntries = findClassEntries(file);
            return new ModuleJar(jar, origin, entryClass, readClasses(origin, file, classEntries));
        } catch (IOException exception) {
            throw new UncheckedIOException("Module jar " + origin + " is not a readable jar", exception);
        }
    }

    List<String> classNames() {
        return classes.stream().map(JarClass::name).toList();
    }

    URL url() {
        try {
            return path.toUri().toURL();
        } catch (MalformedURLException exception) {
            throw new IllegalArgumentException("Module jar " + origin + " has no URL form", exception);
        }
    }

    /** Loads and instantiates the entry class through the pack's class loader. The class must come
     * from this jar, so a copy on the host classpath cannot silently replace the jar's module.
     */
    DiscoveredModule createModule(ClassLoader packLoader, JarSnapshot snapshot) {
        var type = loadEntryClass(packLoader);
        requireDefinedHere(type, snapshot);
        var module = instantiate(type);
        return DiscoveredModule.fromJar(module, readIdentity(module), origin);
    }

    /** Opens the jar with the runtime's multi-release version, like the pack class loader does, so
     * class names below match the classes that loader can define.
     */
    private static JarFile openAsClassLoaderSeesIt(Path jar) throws IOException {
        return new JarFile(jar.toFile(), true, ZipFile.OPEN_READ, JarFile.runtimeVersion());
    }

    private static String requireSingleEntryClass(String origin, List<String> entryClasses) {
        if (entryClasses.isEmpty()) {
            throw new IllegalArgumentException("Module jar " + origin + " has no entry point; name its "
                    + IrisModule.class.getSimpleName() + " class in " + ENTRY_POINT_FILE);
        }
        if (entryClasses.size() > 1) {
            throw new IllegalArgumentException("Module jar " + origin + " names " + entryClasses.size()
                    + " entry points " + entryClasses + "; one jar holds exactly one module");
        }
        return entryClasses.getFirst();
    }

    private static List<String> readEntryClasses(JarFile file) throws IOException {
        var entry = file.getJarEntry(ENTRY_POINT_FILE);
        if (entry == null) return List.of();
        try (var reader = new BufferedReader(new InputStreamReader(file.getInputStream(entry), StandardCharsets.UTF_8))) {
            return reader.lines()
                    .map(ModuleJar::withoutComment)
                    .map(String::strip)
                    .filter(Predicate.not(String::isEmpty))
                    .distinct()
                    .toList();
        }
    }

    /** The class file entries of the jar, sorted by name. A multi-release jar contributes each class
     * once, under its base name, in the version this runtime would load. Anything left below
     * {@code META-INF/}, such as versioned entries of a jar that is not multi-release, cannot be
     * loaded as a class: {@code META-INF} is not a valid package name.
     */
    private static List<JarEntry> findClassEntries(JarFile file) {
        return file.versionedStream()
                .filter(Predicate.not(JarEntry::isDirectory))
                .filter(entry -> isClassFile(entry.getName()))
                .sorted(Comparator.comparing(entry -> toBinaryName(entry.getName())))
                .toList();
    }

    private static boolean isClassFile(String entryName) {
        return entryName.endsWith(CLASS_FILE_SUFFIX)
                && !entryName.equals(MODULE_DESCRIPTOR)
                && !entryName.startsWith(META_INF);
    }

    private static List<JarClass> readClasses(String origin, JarFile file, List<JarEntry> classEntries) throws IOException {
        var classes = new ArrayList<JarClass>(classEntries.size());
        for (var entry : classEntries) {
            classes.add(new JarClass(toBinaryName(entry.getName()), readReferences(origin, file, entry)));
        }
        return classes;
    }

    private static SortedSet<String> readReferences(String origin, JarFile file, JarEntry entry) throws IOException {
        try (var input = file.getInputStream(entry)) {
            return ClassReferences.read(input.readAllBytes());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Module jar " + origin + " holds a class file this runtime cannot read, "
                    + entry.getRealName() + ": " + exception.getMessage(), exception);
        }
    }

    private static String toBinaryName(String entryName) {
        return entryName.substring(0, entryName.length() - CLASS_FILE_SUFFIX.length()).replace('/', '.');
    }

    private static String withoutComment(String line) {
        int comment = line.indexOf(COMMENT_START);
        return comment < 0 ? line : line.substring(0, comment);
    }

    private Class<? extends IrisModule> loadEntryClass(ClassLoader packLoader) {
        Class<?> type;
        try {
            type = Class.forName(entryClass, false, packLoader);
        } catch (ClassNotFoundException | LinkageError exception) {
            throw brokenEntryPoint("class cannot be loaded: " + exception, exception);
        }
        if (!IrisModule.class.isAssignableFrom(type)) {
            throw brokenEntryPoint("class does not implement " + IrisModule.class.getName(), null);
        }
        return type.asSubclass(IrisModule.class);
    }

    private void requireDefinedHere(Class<?> type, JarSnapshot snapshot) {
        var source = type.getProtectionDomain().getCodeSource();
        var location = source == null ? null : source.getLocation();
        if (location != null && location.toExternalForm().equals(url().toExternalForm())) return;
        throw brokenEntryPoint("class is loaded from " + describeLocation(location, snapshot) + ", not from this jar", null);
    }

    /** Another jar of the pack by its origin, never by the path of its copy. */
    private static String describeLocation(URL location, JarSnapshot snapshot) {
        if (location == null) return "an unknown location";
        try {
            var origin = snapshot.originOf(Path.of(location.toURI()));
            return origin == null ? location.toString() : origin;
        } catch (URISyntaxException | IllegalArgumentException | java.nio.file.FileSystemNotFoundException exception) {
            return location.toString();
        }
    }

    private IrisModule instantiate(Class<? extends IrisModule> type) {
        try {
            return type.getConstructor().newInstance();
        } catch (InvocationTargetException exception) {
            throw brokenEntryPoint("constructor threw " + exception.getCause(), exception.getCause());
        } catch (ReflectiveOperationException exception) {
            throw brokenEntryPoint("class needs to be public and concrete with a public no-argument constructor", exception);
        } catch (LinkageError exception) {
            throw brokenEntryPoint("class cannot be initialized: " + exception, exception);
        }
    }

    private ModuleInfo readIdentity(IrisModule module) {
        try {
            return Objects.requireNonNull(module.info(), "info() returned null");
        } catch (RuntimeException | LinkageError exception) {
            throw brokenEntryPoint("info() failed: " + exception, exception);
        }
    }

    private IllegalArgumentException brokenEntryPoint(String problem, Throwable cause) {
        return new IllegalArgumentException("Module jar " + origin + " has a broken entry point "
                + entryClass + ": " + problem, cause);
    }
}
