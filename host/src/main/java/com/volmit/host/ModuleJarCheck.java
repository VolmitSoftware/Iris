package com.volmit.host;

import com.volmit.IrisModule;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** The checks a pack load makes on a module jar's classes, for a build to run on a jar it has just
 * written: the jar names at least one module, every module class is in the jar, and no class uses
 * what pack modules cannot see (Iris internals, JDK modules or packages packs may not use, missing
 * classes of exposed packages). Nothing is loaded or instantiated. A jar that passes can still fail
 * a pack load, for example on duplicate module ids, which only the pack's other modules reveal.
 *
 * <p>The host classes come from the class loader that defines the API, so run this with the API and the
 * host's classes on the class path. {@link #main} takes a jar path followed by the host packages that
 * pack modules may use, and exits with 1 and the message on stderr when the jar fails.
 *
 * <p>The class path the check runs on may hold classes of an exposed library that the real host's jar
 * leaves out. {@code --left-out=<file>} (or the {@code leftOutClasses} parameter) lists them, one entry per
 * line as described by {@code HostClassesLeftOut}, and a module that references one fails the check naming it.
 */
public final class ModuleJarCheck {
    private static final int FAILED_EXIT_CODE = 1;
    private static final int MISSING_ARGUMENT_EXIT_CODE = 2;
    private static final String LEFT_OUT_OPTION = "--left-out=";

    private ModuleJarCheck() {}

    /** Returns the module classes the jar names, in the order it lists them, or throws a {@link ModuleLoadException}
     * naming the jar.
     */
    public static List<String> check(Path jar, Collection<String> exposedHostPackages) {
        return check(jar, exposedHostPackages, List.of());
    }

    /** Like {@link #check(Path, Collection)}, treating the classes {@code leftOutClasses} describes as missing from the host. */
    public static List<String> check(Path jar, Collection<String> exposedHostPackages, Collection<String> leftOutClasses) {
        Objects.requireNonNull(jar, "jar");
        var host = new ExposedHostClassLoader(IrisModule.class.getClassLoader(), ExposedPackages.apiAnd(exposedHostPackages),
                HostClassesLeftOut.parse(leftOutClasses));
        var read = ModuleJar.read(new JarSnapshot.CopiedJar(jar.toString(), jar));
        requireEntryClassesInJar(read);
        PackClassCheck.requireLinkable(List.of(read), host);
        return read.entryClasses();
    }

    private static void requireEntryClassesInJar(ModuleJar jar) {
        var held = jar.classNames();
        for (var entryClass : jar.entryClasses()) {
            if (held.contains(entryClass)) continue;
            throw ModuleLoadException.ofJar(jar.origin(), "Module jar " + jar.origin() + " lists " + entryClass + " in "
                    + ModuleJar.ENTRY_POINT_FILE + ", but the jar holds no such class; a module class must be in the jar that lists it");
        }
    }

    public static void main(String[] arguments) {
        var options = Arrays.stream(arguments).filter(argument -> argument.startsWith(LEFT_OUT_OPTION)).toList();
        var positional = Arrays.stream(arguments).filter(argument -> !argument.startsWith(LEFT_OUT_OPTION)).toList();
        if (positional.isEmpty()) {
            System.err.println("Usage: ModuleJarCheck <module jar> [--left-out=<file>] [exposed host package...]");
            System.exit(MISSING_ARGUMENT_EXIT_CODE);
        }
        try {
            var leftOut = options.stream().flatMap(option -> readLines(Path.of(option.substring(LEFT_OUT_OPTION.length()))).stream()).toList();
            var modules = check(Path.of(positional.getFirst()), positional.subList(1, positional.size()), leftOut);
            System.out.println("Module jar " + positional.getFirst() + " passes the pack module checks: " + modules);
        } catch (RuntimeException failure) {
            System.err.println(failure.getMessage());
            System.exit(FAILED_EXIT_CODE);
        }
    }

    private static List<String> readLines(Path file) {
        try {
            return Files.readAllLines(file);
        } catch (IOException unreadable) {
            throw new UncheckedIOException("Cannot read the left-out classes file " + file, unreadable);
        }
    }
}
