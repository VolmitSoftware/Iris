package com.volmit.host;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/** A Java package name that stands for the package and all its subpackages. Only whole package
 * names match: {@code com.volmit} contains {@code com.volmit.stage.Stage} but not
 * {@code com.volmitx.Fake}.
 */
record PackageName(String name) implements Comparable<PackageName> {
    private static final char SEPARATOR = '.';
    private static final String SEPARATOR_PATTERN = "\\.";

    public PackageName {
        Objects.requireNonNull(name, "name");
        if (!isPackageName(name)) throw new IllegalArgumentException("'" + name + "' is not a package name");
    }

    /** The package {@code name} denotes, if it is a package name. A trailing dot is accepted; blank
     * names, wildcards and anything else that is not a package name give an empty result.
     */
    public static Optional<PackageName> tryParse(String name) {
        Objects.requireNonNull(name, "name");
        var withoutTrailingDot = name.endsWith(String.valueOf(SEPARATOR)) ? name.substring(0, name.length() - 1) : name;
        return isPackageName(withoutTrailingDot) ? Optional.of(new PackageName(withoutTrailingDot)) : Optional.empty();
    }

    /** Whether the class with this binary name is in this package or one of its subpackages. */
    public boolean contains(String binaryName) {
        return binaryName.startsWith(name)
                && binaryName.length() > name.length()
                && binaryName.charAt(name.length()) == SEPARATOR;
    }

    @Override
    public String toString() { return name; }

    @Override
    public int compareTo(PackageName other) { return name.compareTo(other.name); }

    private static boolean isPackageName(String name) {
        return Arrays.stream(name.split(SEPARATOR_PATTERN, -1)).allMatch(PackageName::isIdentifier);
    }

    private static boolean isIdentifier(String segment) {
        return !segment.isEmpty()
                && Character.isJavaIdentifierStart(segment.codePointAt(0))
                && segment.codePoints().skip(1).allMatch(Character::isJavaIdentifierPart);
    }
}
