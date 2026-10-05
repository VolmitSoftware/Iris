package com.volmit.host;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Checks the classes of all jars of one pack before its class loader exists, so a pack that
 * cannot link fails at load with the jar to fix, not later with a {@link NoClassDefFoundError}
 * during install or generation.
 */
final class PackClassCheck {
    private static final int MAX_REPORTED_REFERENCES_PER_JAR = 10;
    private static final String INDENT = "  ";

    private PackClassCheck() {}

    /** Fails when a class is in two jars, or when a class uses a class that is neither in the API,
     * the JDK nor a jar of the pack.
     */
    static void requireLinkable(List<ModuleJar> jars, ApiAndJdkClassLoader apiAndJdk) {
        requireDistinctClasses(jars);
        requireResolvableReferences(jars, apiAndJdk);
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

    private static void requireResolvableReferences(List<ModuleJar> jars, ApiAndJdkClassLoader apiAndJdk) {
        var resolver = new ReferenceResolver(jars, apiAndJdk);
        var unresolved = jars.stream()
                .map(jar -> new UnresolvedReferences(jar, resolver.unresolvedIn(jar)))
                .filter(UnresolvedReferences::any)
                .toList();
        if (!unresolved.isEmpty()) throw unresolvedReferences(unresolved);
    }

    private static IllegalArgumentException unresolvedReferences(List<UnresolvedReferences> jars) {
        var text = new StringBuilder();
        for (var jar : jars) jar.describeTo(text);
        text.append("A module can use only the Iris API, the JDK and the classes in its pack's module jars;"
                + " ship any library it needs inside a module jar of the pack.");
        if (jars.stream().anyMatch(UnresolvedReferences::missesApiClasses)) {
            text.append(" A missing ").append(ApiAndJdkClassLoader.API_PACKAGE_PREFIX)
                    .append("* class means the jar was built against a different Iris API.");
        }
        return new IllegalArgumentException(text.toString());
    }

    /** Decides per referenced class whether the pack loader will find it. Classes outside the pack
     * are looked up once per pack.
     */
    private static final class ReferenceResolver {
        private final Set<String> packClasses = new HashSet<>();
        private final Map<String, Boolean> outsidePack = new HashMap<>();
        private final ApiAndJdkClassLoader apiAndJdk;

        ReferenceResolver(List<ModuleJar> jars, ApiAndJdkClassLoader apiAndJdk) {
            this.apiAndJdk = apiAndJdk;
            jars.forEach(jar -> packClasses.addAll(jar.classNames()));
        }

        List<Reference> unresolvedIn(ModuleJar jar) {
            return jar.classes().stream()
                    .flatMap(type -> type.references().stream().map(target -> new Reference(type.name(), target)))
                    .filter(reference -> !resolves(reference.target()))
                    .toList();
        }

        private boolean resolves(String className) {
            return packClasses.contains(className) || outsidePack.computeIfAbsent(className, apiAndJdk::hasClass);
        }
    }

    private record Reference(String source, String target) {}

    private record UnresolvedReferences(ModuleJar jar, List<Reference> references) {
        boolean any() {
            return !references.isEmpty();
        }

        boolean missesApiClasses() {
            return references.stream().anyMatch(reference -> ApiAndJdkClassLoader.isApiClass(reference.target()));
        }

        void describeTo(StringBuilder text) {
            text.append("Module jar ").append(jar.origin())
                    .append(" uses classes that are not in the Iris API, the JDK or the pack's module jars:\n");
            references.stream().limit(MAX_REPORTED_REFERENCES_PER_JAR).forEach(reference -> text.append(INDENT)
                    .append(reference.source()).append(" -> ").append(reference.target()).append('\n'));
            int hidden = references.size() - MAX_REPORTED_REFERENCES_PER_JAR;
            if (hidden > 0) text.append(INDENT).append("and ").append(hidden).append(" more\n");
        }
    }
}
