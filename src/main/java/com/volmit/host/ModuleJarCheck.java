package com.volmit.host;

import com.volmit.IrisModule;
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
 */
public final class ModuleJarCheck {
    private static final int FAILED_EXIT_CODE = 1;
    private static final int MISSING_ARGUMENT_EXIT_CODE = 2;

    private ModuleJarCheck() {}

    /** Returns the module classes the jar names, in the order it lists them, or fails naming the jar. */
    public static List<String> check(Path jar, Collection<String> exposedHostPackages) {
        Objects.requireNonNull(jar, "jar");
        var host = new ExposedHostClassLoader(IrisModule.class.getClassLoader(), ExposedPackages.apiAnd(exposedHostPackages));
        var read = ModuleJar.read(new JarSnapshot.CopiedJar(jar.toString(), jar));
        requireEntryClassesInJar(read);
        PackClassCheck.requireLinkable(List.of(read), host);
        return read.entryClasses();
    }

    private static void requireEntryClassesInJar(ModuleJar jar) {
        var held = jar.classNames();
        for (var entryClass : jar.entryClasses()) {
            if (held.contains(entryClass)) continue;
            throw new IllegalArgumentException("Module jar " + jar.origin() + " lists " + entryClass + " in "
                    + ModuleJar.ENTRY_POINT_FILE + ", but the jar holds no such class; a module class must be in the jar that lists it");
        }
    }

    public static void main(String[] arguments) {
        if (arguments.length == 0) {
            System.err.println("Usage: ModuleJarCheck <module jar> [exposed host package...]");
            System.exit(MISSING_ARGUMENT_EXIT_CODE);
        }
        var exposed = Arrays.asList(arguments).subList(1, arguments.length);
        try {
            var modules = check(Path.of(arguments[0]), exposed);
            System.out.println("Module jar " + arguments[0] + " passes the pack module checks: " + modules);
        } catch (RuntimeException failure) {
            System.err.println(failure.getMessage());
            System.exit(FAILED_EXIT_CODE);
        }
    }
}
