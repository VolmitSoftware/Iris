package com.volmit.host;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

/** Decides for every class a pack's jars use whether the pack loader will find it, and why not
 * when it won't. Nothing is loaded: the host's classes are judged by their class files.
 *
 * <p>The JVM resolves a class only when code first needs it, so a class nobody has is not an
 * error: bundled libraries reference optional dependencies they only use when present. A class
 * that Iris has but hides, a class of a JDK module packs cannot see, or a class missing from a
 * package Iris exposes always fails the load.
 */
final class PackReferenceCheck {
    private static final int MAX_REPORTED_REFERENCES_PER_JAR = 10;
    private static final String INDENT = "  ";

    /** Where the pack loader would find a referenced class. */
    private enum Resolution {
        /** In the pack, an exposed host package, or the JDK. */
        PROVIDED,
        /** Nowhere: an optional dependency, which only fails when code that needs it runs. */
        ABSENT,
        HIDDEN_HOST_CLASS(
                "uses classes that exist in Iris but are hidden from modules:",
                "Modules cannot use Iris's own classes or its copies of libraries; ship the library in a module jar"
                        + " of the pack, or use the Iris API."),
        /** A JDK module outside the platform class loader, such as jdk.compiler. */
        UNAVAILABLE_JDK_CLASS(
                "uses classes of JDK modules that are not available to modules:",
                "Modules can use only the JDK modules of the platform class loader, such as java.base, java.sql and"
                        + " java.net.http."),
        MISSING_HOST_CLASS(
                "uses classes that this Iris does not have, in packages Iris provides to modules:",
                "The jar was built against a different Iris API or Iris version.");

        private final String header;
        private final String advice;

        Resolution() {
            this(null, null);
        }

        Resolution(String header, String advice) {
            this.header = header;
            this.advice = advice;
        }

        boolean failsLoad() {
            return header != null;
        }
    }

    private final Set<String> packClasses = new HashSet<>();
    private final Map<String, Resolution> outsidePack = new HashMap<>();
    private final ExposedHostClassLoader host;

    private PackReferenceCheck(List<ModuleJar> jars, ExposedHostClassLoader host) {
        this.host = host;
        jars.forEach(jar -> packClasses.addAll(jar.classNames()));
    }

    /** Fails when a jar uses a class Iris hides, a class of a JDK module packs cannot see, or a class
     * missing from a package Iris exposes.
     * Returns the classes nobody has, per jar, for jars that use any.
     */
    static List<MissingClasses> requireResolvable(List<ModuleJar> jars, ExposedHostClassLoader host) {
        var check = new PackReferenceCheck(jars, host);
        var references = jars.stream().map(check::resolve).toList();
        requireNoFailures(references);
        return references.stream()
                .filter(JarReferences::missesClasses)
                .map(JarReferences::missingClasses)
                .toList();
    }

    private JarReferences resolve(ModuleJar jar) {
        var references = jar.classes().stream()
                .flatMap(type -> type.references().stream().map(target -> new Reference(type.name(), target, resolve(target))))
                .filter(reference -> reference.resolution() != Resolution.PROVIDED)
                .toList();
        return new JarReferences(jar, references);
    }

    private Resolution resolve(String className) {
        if (packClasses.contains(className)) return Resolution.PROVIDED;
        return outsidePack.computeIfAbsent(className, this::resolveOutsidePack);
    }

    private Resolution resolveOutsidePack(String className) {
        if (host.hasClass(className)) return Resolution.PROVIDED;
        if (host.exposed().exposes(className)) return Resolution.MISSING_HOST_CLASS;
        if (UnavailableJdkModules.moduleOf(className).isPresent()) return Resolution.UNAVAILABLE_JDK_CLASS;
        if (host.hostHasClass(className)) return Resolution.HIDDEN_HOST_CLASS;
        return Resolution.ABSENT;
    }

    private static void requireNoFailures(List<JarReferences> jars) {
        var failures = Arrays.stream(Resolution.values())
                .filter(Resolution::failsLoad)
                .map(failure -> describeFailure(failure, jars))
                .filter(Predicate.not(String::isEmpty))
                .toList();
        if (failures.isEmpty()) return;
        throw new IllegalArgumentException(String.join("\n", failures));
    }

    private static String describeFailure(Resolution failure, List<JarReferences> jars) {
        var text = new StringBuilder();
        for (var jar : jars) jar.describeTo(failure, text);
        if (text.isEmpty()) return "";
        return text.append(failure.advice).toString();
    }

    private record Reference(String source, String target, Resolution resolution) {
        String describe() {
            var text = source + " -> " + target;
            if (resolution != Resolution.UNAVAILABLE_JDK_CLASS) return text;
            return text + " (JDK module " + UnavailableJdkModules.moduleOf(target).orElseThrow() + ")";
        }
    }

    /** The references of one jar the pack loader would not resolve. */
    private record JarReferences(ModuleJar jar, List<Reference> unresolved) {
        boolean missesClasses() {
            return unresolved.stream().anyMatch(reference -> reference.resolution() == Resolution.ABSENT);
        }

        MissingClasses missingClasses() {
            var classNames = new TreeSet<String>();
            with(Resolution.ABSENT).forEach(reference -> classNames.add(reference.target()));
            return new MissingClasses(jar.origin(), List.copyOf(classNames));
        }

        void describeTo(Resolution failure, StringBuilder text) {
            var references = with(failure);
            if (references.isEmpty()) return;
            text.append("Module jar ").append(jar.origin()).append(' ').append(failure.header).append('\n');
            references.stream().limit(MAX_REPORTED_REFERENCES_PER_JAR)
                    .forEach(reference -> text.append(INDENT).append(reference.describe()).append('\n'));
            int hidden = references.size() - MAX_REPORTED_REFERENCES_PER_JAR;
            if (hidden > 0) text.append(INDENT).append("and ").append(hidden).append(" more\n");
        }

        private List<Reference> with(Resolution resolution) {
            return unresolved.stream().filter(reference -> reference.resolution() == resolution).toList();
        }
    }
}
