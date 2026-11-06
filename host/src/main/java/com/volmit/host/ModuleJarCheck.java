package com.volmit.host;

import com.volmit.IrisModule;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
 *
 * <p>The jar's tooling parts ({@link ToolingPart}) are not generation code. Generation code may not use them, which is
 * always checked. The parts themselves are checked only for the platform {@code --platform=<name>:<path to the platform's
 * api jar>} names, such as {@code bukkit} with paper-api, against that jar and what a part may see; without the option,
 * or for other platforms, parts are skipped and the output lists them.
 */
public final class ModuleJarCheck {
    private static final int FAILED_EXIT_CODE = 1;
    private static final int MISSING_ARGUMENT_EXIT_CODE = 2;
    private static final String LEFT_OUT_OPTION = "--left-out=";
    private static final String PLATFORM_OPTION = "--platform=";
    private static final char PLATFORM_OPTION_SEPARATOR = ':';
    private static final String USAGE = "Usage: ModuleJarCheck <module jar> [--left-out=<file>] [--platform=<name>:<platform api jar>]"
            + " [exposed host package...]";

    private ModuleJarCheck() {}

    /** Returns the module classes the jar names, in the order it lists them, or throws a {@link ModuleLoadException}
     * naming the jar. The jar's tooling parts are skipped.
     */
    public static List<String> check(Path jar, Collection<String> exposedHostPackages) {
        return check(jar, exposedHostPackages, List.of());
    }

    /** Like {@link #check(Path, Collection)}, treating the classes {@code leftOutClasses} describes as missing from the host. */
    public static List<String> check(Path jar, Collection<String> exposedHostPackages, Collection<String> leftOutClasses) {
        return run(jar, exposedHostPackages, leftOutClasses, Optional.empty()).modules();
    }

    /** Like {@link #check(Path, Collection, Collection)}, also checking the jar's tooling parts of {@code platform} against
     * {@code platformApiJar}, the jar that holds the platform's api classes, such as paper-api, and what a part may see.
     * Parts of other platforms are skipped.
     */
    public static List<String> check(Path jar, Collection<String> exposedHostPackages, Collection<String> leftOutClasses,
                                     ToolingPlatform platform, Path platformApiJar) {
        Objects.requireNonNull(platform, "platform");
        return run(jar, exposedHostPackages, leftOutClasses, Optional.of(new CheckedPlatform(platform, platformApiJar))).modules();
    }

    /** The platform whose parts a check reads, with the jar of its api classes. */
    record CheckedPlatform(ToolingPlatform platform, Path apiJar) {
        CheckedPlatform {
            Objects.requireNonNull(platform, "platform");
            Objects.requireNonNull(apiJar, "apiJar");
        }

        /** Reads {@code <name>:<path to the platform api jar>}. */
        static CheckedPlatform parse(String option) {
            int separator = option.indexOf(PLATFORM_OPTION_SEPARATOR);
            if (separator <= 0 || separator == option.length() - 1) {
                throw new IllegalArgumentException("'" + PLATFORM_OPTION + option + "' is not " + PLATFORM_OPTION
                        + "<name>:<path to the platform api jar>, such as " + PLATFORM_OPTION + "bukkit:libs/paper-api.jar");
            }
            return new CheckedPlatform(ToolingPlatform.named(option.substring(0, separator)), Path.of(option.substring(separator + 1)));
        }
    }

    /** What a check found: the module classes, and the tooling parts it did not read. */
    record Outcome(Path jar, List<String> modules, List<ToolingPart> skippedParts, boolean checkedAPlatform) {
        String summary() {
            var passed = "Module jar " + jar + " passes the pack module checks: " + modules;
            if (skippedParts.isEmpty()) return passed;
            var reason = checkedAPlatform ? "of other platforms" : "(name a platform with " + PLATFORM_OPTION + "<name>:<platform api jar> to check them)";
            return passed + "\nSkipped tooling parts " + reason + ": " + skippedParts.stream().map(ToolingPart::declaration).toList();
        }
    }

    static Outcome run(Path jar, Collection<String> exposedHostPackages, Collection<String> leftOutClasses, Optional<CheckedPlatform> platform) {
        Objects.requireNonNull(jar, "jar");
        var host = new ExposedHostClassLoader(IrisModule.class.getClassLoader(), ExposedPackages.apiAnd(exposedHostPackages),
                HostClassesLeftOut.parse(leftOutClasses));
        var read = ModuleJar.read(new JarSnapshot.CopiedJar(jar.toString(), jar));
        requireEntryClassesInJar(read);
        requireLinkable(read, host, platform);
        return new Outcome(jar, read.entryClasses(), skippedParts(read, platform), platform.isPresent());
    }

    private static void requireLinkable(ModuleJar read, ExposedHostClassLoader host, Optional<CheckedPlatform> platform) {
        if (platform.isEmpty()) {
            PackClassCheck.requireLinkable(List.of(read), host, Optional.empty());
            return;
        }
        var apiJar = requireReadable(platform.get().apiJar());
        try (var platformClasses = new URLClassLoader("platform-api", new URL[] {urlOf(apiJar)}, null)) {
            PackClassCheck.requireLinkable(List.of(read), host, Optional.of(new PlatformApi(platform.get().platform(), platformClasses)));
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot close the platform api jar " + apiJar, exception);
        }
    }

    private static List<ToolingPart> skippedParts(ModuleJar read, Optional<CheckedPlatform> platform) {
        var checkedName = platform.map(checked -> checked.platform().name());
        return read.toolingParts().stream().filter(part -> checkedName.filter(part.platform()::equals).isEmpty()).toList();
    }

    private static Path requireReadable(Path apiJar) {
        if (Files.isRegularFile(apiJar)) return apiJar;
        throw new IllegalArgumentException("Cannot read the platform api jar " + apiJar + "; name the jar of the platform's api, such as paper-api");
    }

    private static URL urlOf(Path jar) {
        try {
            return jar.toUri().toURL();
        } catch (MalformedURLException exception) {
            throw new IllegalArgumentException("The platform api jar " + jar + " has no URL form", exception);
        }
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
        var leftOutOptions = optionValues(arguments, LEFT_OUT_OPTION);
        var platformOptions = optionValues(arguments, PLATFORM_OPTION);
        var positional = Arrays.stream(arguments)
                .filter(argument -> !argument.startsWith(LEFT_OUT_OPTION) && !argument.startsWith(PLATFORM_OPTION))
                .toList();
        if (positional.isEmpty() || platformOptions.size() > 1) {
            System.err.println(USAGE);
            System.exit(MISSING_ARGUMENT_EXIT_CODE);
        }
        try {
            var leftOut = leftOutOptions.stream().flatMap(option -> readLines(Path.of(option)).stream()).toList();
            var platform = platformOptions.stream().map(CheckedPlatform::parse).findFirst();
            System.out.println(run(Path.of(positional.getFirst()), positional.subList(1, positional.size()), leftOut, platform).summary());
        } catch (RuntimeException failure) {
            System.err.println(failure.getMessage());
            System.exit(FAILED_EXIT_CODE);
        }
    }

    private static List<String> optionValues(String[] arguments, String option) {
        return Arrays.stream(arguments).filter(argument -> argument.startsWith(option)).map(argument -> argument.substring(option.length())).toList();
    }

    private static List<String> readLines(Path file) {
        try {
            return Files.readAllLines(file);
        } catch (IOException unreadable) {
            throw new UncheckedIOException("Cannot read the left-out classes file " + file, unreadable);
        }
    }
}
