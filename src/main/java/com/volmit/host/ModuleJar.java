package com.volmit.host;

import com.volmit.IrisModule;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.jar.JarFile;

/** One jar in a pack's modules directory and the entry class it names.
 *
 * <p>The entry point is the jar's own {@code META-INF/services/com.volmit.IrisModule} file, in
 * the format {@link java.util.ServiceLoader} reads. The host reads that file from this jar only:
 * {@code ServiceLoader} would also return providers from parent class loaders and could not tell
 * which jar a broken entry came from.
 */
record ModuleJar(Path path, String origin, String entryClass) {
    static final String ENTRY_POINT_FILE = "META-INF/services/" + IrisModule.class.getName();
    private static final String COMMENT_START = "#";

    ModuleJar {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(entryClass, "entryClass");
    }

    /** Reads the single entry class a jar names. The origin is the jar path relative to its pack. */
    static ModuleJar read(Path packRoot, Path jar) {
        var origin = packRoot.relativize(jar).toString();
        var entryClasses = readEntryClasses(jar, origin);
        if (entryClasses.isEmpty()) {
            throw new IllegalArgumentException("Module jar " + origin + " has no entry point; name its "
                    + IrisModule.class.getSimpleName() + " class in " + ENTRY_POINT_FILE);
        }
        if (entryClasses.size() > 1) {
            throw new IllegalArgumentException("Module jar " + origin + " names " + entryClasses.size()
                    + " entry points " + entryClasses + "; one jar holds exactly one module");
        }
        return new ModuleJar(jar, origin, entryClasses.getFirst());
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
    DiscoveredModule createModule(ClassLoader packLoader) {
        var type = loadEntryClass(packLoader);
        requireDefinedHere(type);
        var module = instantiate(type);
        requireIdentity(module);
        return new DiscoveredModule(module, origin);
    }

    private static List<String> readEntryClasses(Path jar, String origin) {
        try (var file = new JarFile(jar.toFile())) {
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
        } catch (IOException exception) {
            throw new UncheckedIOException("Module jar " + origin + " is not a readable jar", exception);
        }
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

    private void requireDefinedHere(Class<?> type) {
        var source = type.getProtectionDomain().getCodeSource();
        var location = source == null ? null : source.getLocation();
        if (location == null || !location.toExternalForm().equals(url().toExternalForm())) {
            throw brokenEntryPoint("class is loaded from " + location + ", not from this jar", null);
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

    private void requireIdentity(IrisModule module) {
        try {
            Objects.requireNonNull(module.info(), "info() returned null");
        } catch (RuntimeException | LinkageError exception) {
            throw brokenEntryPoint("info() failed: " + exception, exception);
        }
    }

    private IllegalArgumentException brokenEntryPoint(String problem, Throwable cause) {
        return new IllegalArgumentException("Module jar " + origin + " has a broken entry point "
                + entryClass + ": " + problem, cause);
    }
}
