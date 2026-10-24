package com.volmit.host;

import java.util.List;
import java.util.Objects;

/** Classes a module jar uses that neither its pack, Iris nor the JDK has, usually optional
 * dependencies of a library the jar bundles, and that the jar's manifest does not declare optional
 * ({@value ModuleJar#OPTIONAL_PACKAGES_ATTRIBUTE}). The jar still loads; code that needs one of
 * these classes throws {@link NoClassDefFoundError} when it runs.
 *
 * @param origin the jar path relative to its pack
 * @param classNames the missing classes as sorted binary names
 */
public record MissingClasses(String origin, List<String> classNames) {
    public MissingClasses {
        Objects.requireNonNull(origin, "origin");
        classNames = List.copyOf(classNames);
    }

    /** These classes without those in any of {@code packages}. */
    MissingClasses outside(List<PackageName> packages) {
        var remaining = classNames.stream()
                .filter(className -> packages.stream().noneMatch(name -> name.contains(className)))
                .toList();
        return new MissingClasses(origin, remaining);
    }

    boolean isEmpty() {
        return classNames.isEmpty();
    }
}
