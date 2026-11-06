package com.volmit.host;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Decides for every class a pack's jars use whether the pack loader will find it, and why not
 * when it won't. Nothing is loaded: the host's classes are judged by their class files.
 *
 * <p>The JVM resolves a class only when code first needs it, so a class nobody has is not an
 * error: bundled libraries reference optional dependencies they only use when present. A class
 * that Iris has but hides, a class of a JDK module packs cannot see or of a JDK package packs may
 * not use, or a class missing from a package Iris exposes always fails the load.
 *
 * <p>Tooling parts ({@link ToolingPart}) split the classes in two. Generation code is judged as above, plus it may not use
 * a part class. A part of the platform this host runs on is judged against what a part may see; the parts of other
 * platforms are not read.
 */
final class PackReferenceCheck {
    private static final int MAX_REPORTED_REFERENCES_PER_JAR = 10;
    private static final String INDENT = "  ";
    private static final String PLATFORM_PLACEHOLDER = "{platform}";
    private static final String NO_PLATFORM = "";

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
        /** A package of an available JDK module that it does not export to packs, such as jdk.internal.misc. */
        NOT_EXPORTED_JDK_CLASS(
                "uses classes of JDK packages that are not exported to modules:",
                "Use the supported alternative where there is one, such as sun.misc.Unsafe from jdk.unsupported instead"
                        + " of jdk.internal.misc.Unsafe. Otherwise the server must be started with"
                        + " --add-exports <module>/<package>=ALL-UNNAMED."),
        MISSING_HOST_CLASS(
                "uses classes that this Iris does not have, in packages Iris provides to modules:",
                "The jar was built against a different Iris API or Iris version."),
        /** A class of an exposed package that the library has but the host's own jar leaves out. */
        LEFT_OUT_HOST_CLASS(
                "uses classes of the libraries Iris provides to modules that Iris's jar leaves out:",
                "Iris strips classes it does not use itself from the libraries it ships. Use another class, or have"
                        + " Iris keep the class you need."),
        GENERATION_USES_PART(
                "has generation classes that use classes of the " + PLATFORM_PLACEHOLDER + " part:",
                "Generation code cannot use the " + PLATFORM_PLACEHOLDER + " part; move the call into the part."),
        PART_USES_OTHER_PART(
                "has " + PLATFORM_PLACEHOLDER + " part classes that use classes of another part:",
                "A part can use only its own packages; move what parts share into generation code or into one part."),
        PART_USES_DENIED_CLASS(
                "has " + PLATFORM_PLACEHOLDER + " part classes that use classes parts cannot see:",
                "A " + PLATFORM_PLACEHOLDER + " part can use its own packages, the " + PLATFORM_PLACEHOLDER + " API and what"
                        + " modules can use; it cannot use Iris internals, the server's own classes or the host side of IrisApi."),
        MISSING_PLATFORM_CLASS(
                "has " + PLATFORM_PLACEHOLDER + " part classes that use " + PLATFORM_PLACEHOLDER + " API classes this server does not have:",
                "The jar was built against a different " + PLATFORM_PLACEHOLDER + " API version.");

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

        /** Whether the failure is about one platform's part, so each platform gets its own group in the message. */
        boolean namesPlatform() {
            return failsLoad() && header.contains(PLATFORM_PLACEHOLDER);
        }

        String header(String platform) {
            return header.replace(PLATFORM_PLACEHOLDER, platform);
        }

        String advice(String platform) {
            return advice.replace(PLATFORM_PLACEHOLDER, platform);
        }
    }

    private final Set<String> packClasses = new HashSet<>();
    private final Map<String, Resolution> outsidePack = new HashMap<>();
    private final ExposedHostClassLoader host;
    private final ToolingPartition partition;

    private PackReferenceCheck(List<ModuleJar> jars, ExposedHostClassLoader host, ToolingPartition partition) {
        this.host = host;
        this.partition = partition;
        jars.forEach(jar -> packClasses.addAll(jar.classNames()));
    }

    /** Fails when a jar uses a class Iris hides, a class of a JDK module packs cannot see or of a JDK
     * package packs may not use, or a class missing from a package Iris exposes, or when generation code uses a tooling
     * part or a part of {@code runningPlatform} uses what parts cannot see.
     * Returns the classes nobody has, per jar, for jars that use any.
     */
    static List<MissingClasses> requireResolvable(List<ModuleJar> jars, ExposedHostClassLoader host, Optional<PlatformApi> runningPlatform) {
        var check = new PackReferenceCheck(jars, host, new ToolingPartition(jars, runningPlatform));
        var references = jars.stream().map(check::resolve).toList();
        requireNoFailures(references);
        return references.stream()
                .filter(JarReferences::missesClasses)
                .map(JarReferences::missingClasses)
                .toList();
    }

    private JarReferences resolve(ModuleJar jar) {
        var references = jar.classes().stream()
                .filter(type -> partition.isChecked(type.name()))
                .flatMap(type -> type.references().stream().map(target -> referenceFrom(type.name(), target)))
                .filter(reference -> reference.resolution() != Resolution.PROVIDED)
                .toList();
        return new JarReferences(jar, references);
    }

    private Reference referenceFrom(String source, String target) {
        var resolution = partition.partOf(source)
                .map(sourcePart -> resolveFromPart(sourcePart, target))
                .orElseGet(() -> resolveFromGeneration(target));
        var platform = resolution.namesPlatform() ? partition.platformInvolvedIn(source, target) : NO_PLATFORM;
        return new Reference(source, target, resolution, platform);
    }

    private Resolution resolveFromGeneration(String className) {
        if (partition.partOf(className).isPresent()) return Resolution.GENERATION_USES_PART;
        return resolve(className);
    }

    /** What a part may use: itself, generation classes, the platform's api, and what modules may use, but never a denied class. */
    private Resolution resolveFromPart(ToolingPart sourcePart, String className) {
        var targetPart = partition.partOf(className);
        if (targetPart.isPresent()) return sourcePart.inSamePartAs(targetPart.get()) ? Resolution.PROVIDED : Resolution.PART_USES_OTHER_PART;
        if (packClasses.contains(className)) return Resolution.PROVIDED;
        var platform = partition.runningPlatform();
        if (isDeniedToParts(platform.platform(), className)) return Resolution.PART_USES_DENIED_CLASS;
        if (platform.platform().hasApiPackageOf(className)) return platform.hasClass(className) ? Resolution.PROVIDED : Resolution.MISSING_PLATFORM_CLASS;
        return resolve(className);
    }

    /** A class modules may use is not denied: {@code art.arcane.iris.builtin} lies in the denied {@code art.arcane.iris}. */
    private boolean isDeniedToParts(ToolingPlatform platform, String className) {
        return platform.deniesPackageOf(className) && !host.exposed().exposes(className);
    }

    private Resolution resolve(String className) {
        if (packClasses.contains(className)) return Resolution.PROVIDED;
        return outsidePack.computeIfAbsent(className, this::resolveOutsidePack);
    }

    /** Access comes first: whichever loader finds a JDK class, its module still decides who may use it. */
    private Resolution resolveOutsidePack(String className) {
        if (NonExportedJdkPackages.packageOf(className).isPresent()) return Resolution.NOT_EXPORTED_JDK_CLASS;
        if (host.leavesOut(className)) return Resolution.LEFT_OUT_HOST_CLASS;
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
        throw ModuleLoadException.ofPack(String.join("\n", failures));
    }

    /** One group per platform for the failures that name one; the others have a single group without a platform. */
    private static String describeFailure(Resolution failure, List<JarReferences> jars) {
        var platforms = jars.stream()
                .flatMap(jar -> jar.with(failure).stream())
                .map(Reference::platform)
                .collect(Collectors.toCollection(TreeSet::new));
        return platforms.stream()
                .map(platform -> describeFailure(failure, platform, jars))
                .collect(Collectors.joining("\n"));
    }

    private static String describeFailure(Resolution failure, String platform, List<JarReferences> jars) {
        var text = new StringBuilder();
        for (var jar : jars) jar.describeTo(failure, platform, text);
        return text.append(failure.advice(platform)).toString();
    }

    private record Reference(String source, String target, Resolution resolution, String platform) {
        String describe() {
            var text = source + " -> " + target;
            return switch (resolution) {
                case UNAVAILABLE_JDK_CLASS -> text + " (JDK module " + UnavailableJdkModules.moduleOf(target).orElseThrow() + ")";
                case NOT_EXPORTED_JDK_CLASS -> text + " (" + describeNotExported(NonExportedJdkPackages.packageOf(target).orElseThrow()) + ")";
                default -> text;
            };
        }

        private static String describeNotExported(NonExportedJdkPackages.JdkPackage jdkPackage) {
            return "package " + jdkPackage.name() + " of " + jdkPackage.module() + " is not exported";
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

        void describeTo(Resolution failure, String platform, StringBuilder text) {
            var references = with(failure).stream().filter(reference -> reference.platform().equals(platform)).toList();
            if (references.isEmpty()) return;
            text.append("Module jar ").append(jar.origin()).append(' ').append(failure.header(platform)).append('\n');
            references.stream().limit(MAX_REPORTED_REFERENCES_PER_JAR)
                    .forEach(reference -> text.append(INDENT).append(reference.describe()).append('\n'));
            int hidden = references.size() - MAX_REPORTED_REFERENCES_PER_JAR;
            if (hidden > 0) text.append(INDENT).append("and ").append(hidden).append(" more\n");
        }

        List<Reference> with(Resolution resolution) {
            return unresolved.stream().filter(reference -> reference.resolution() == resolution).toList();
        }
    }
}
