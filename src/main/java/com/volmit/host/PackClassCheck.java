package com.volmit.host;

import java.util.HashMap;
import java.util.List;

/** Checks the classes of all jars of one pack before its class loader exists, so a pack that
 * cannot link fails at load with the jar to fix, not later with a {@link NoClassDefFoundError}
 * during install or generation.
 */
final class PackClassCheck {
    private PackClassCheck() {}

    /** Fails when a class is in two jars, or when a class uses a class that Iris hides from modules
     * or that is missing from the API. Returns the classes that nobody has, per jar; they fail only
     * when code that needs them runs.
     */
    static List<MissingClasses> requireLinkable(List<ModuleJar> jars, ApiAndJdkClassLoader apiAndJdk) {
        requireDistinctClasses(jars);
        return PackReferenceCheck.requireResolvable(jars, apiAndJdk);
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
