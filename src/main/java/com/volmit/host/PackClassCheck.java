package com.volmit.host;

import java.util.HashMap;
import java.util.List;

/** Checks the classes of all jars of one pack before its class loader exists, so a pack that
 * cannot link fails at load with the jar to fix, not later with a {@link NoClassDefFoundError}
 * during setup or generation.
 */
final class PackClassCheck {
    private PackClassCheck() {}

    /** Fails when a jar holds a class of an exposed host package, when a class is in two jars, or
     * when a class uses a class that Iris hides from modules or that is missing from an exposed
     * package. Returns the classes that nobody has, per jar; they fail only when code that needs them
     * runs.
     */
    static List<MissingClasses> requireLinkable(List<ModuleJar> jars, ExposedHostClassLoader host) {
        jars.forEach(jar -> requireNoHostClasses(jar, host.exposed()));
        requireDistinctClasses(jars);
        return PackReferenceCheck.requireResolvable(jars, host);
    }

    /** Exposed packages belong to the host. The pack loader asks the host for their classes first, so
     * a packaged copy would be silently replaced by the host's, and a module's own class there could be
     * replaced by a later host class of that name.
     */
    private static void requireNoHostClasses(ModuleJar jar, ExposedPackages exposed) {
        var hostClasses = jar.classNames().stream().filter(exposed::exposes).toList();
        if (hostClasses.isEmpty()) return;
        var apiClasses = hostClasses.stream().filter(ExposedPackages::isApiClass).toList();
        throw apiClasses.isEmpty() ? shippedHostClasses(jar, hostClasses, exposed) : shippedApiClasses(jar, apiClasses);
    }

    private static IllegalArgumentException shippedApiClasses(ModuleJar jar, List<String> apiClasses) {
        return new IllegalArgumentException("Module jar " + jar.origin() + " contains Iris API class "
                + firstAndCount(apiClasses) + "; Iris provides the API at runtime, so mark the Iris API dependency"
                + " compileOnly (Maven: provided) and keep module classes out of " + ExposedPackages.API_PACKAGE + ".*");
    }

    private static IllegalArgumentException shippedHostClasses(ModuleJar jar, List<String> hostClasses, ExposedPackages exposed) {
        var hostPackage = exposed.packageOf(hostClasses.getFirst());
        return new IllegalArgumentException("Module jar " + jar.origin() + " contains class " + firstAndCount(hostClasses)
                + " of package " + hostPackage + ", which Iris provides to modules at runtime; compile against it"
                + " compileOnly (Maven: provided) instead of shipping it, and keep module classes out of " + hostPackage + ".*");
    }

    private static String firstAndCount(List<String> classNames) {
        var others = classNames.size() == 1 ? "" : " and " + (classNames.size() - 1) + " more";
        return classNames.getFirst() + others;
    }

    /** The shared pack loader would otherwise silently use whichever copy comes first in file name order. */
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
}
