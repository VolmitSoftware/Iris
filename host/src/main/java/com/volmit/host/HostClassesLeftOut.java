package com.volmit.host;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Classes that exist on the class path a build checks a module jar against, but that the real host leaves
 * out of its jar, such as the parts of a shared library Iris strips when it packages itself. A module that
 * uses one passes a check against the full library and fails on a server, so the check treats them as
 * absent.
 *
 * <p>Entries are class file paths relative to the class root, as Ant patterns: {@code **} crosses
 * packages, {@code *} does not. An entry that neither ends in {@code .class} nor contains {@code *} names a
 * class and takes its nested classes with it.
 */
final class HostClassesLeftOut {
    private static final HostClassesLeftOut NONE = new HostClassesLeftOut(List.of());
    private static final String CLASS_FILE_SUFFIX = ".class";
    private static final String COMMENT_START = "#";

    private final List<Pattern> patterns;

    private HostClassesLeftOut(List<Pattern> patterns) {
        this.patterns = List.copyOf(patterns);
    }

    static HostClassesLeftOut none() {
        return NONE;
    }

    /** The entries of a list file's lines; blank lines and text after {@code #} are ignored. */
    static HostClassesLeftOut parse(Collection<String> lines) {
        Objects.requireNonNull(lines, "lines");
        var patterns = lines.stream()
                .map(line -> line.contains(COMMENT_START) ? line.substring(0, line.indexOf(COMMENT_START)) : line)
                .map(String::strip)
                .filter(entry -> !entry.isEmpty())
                .flatMap(HostClassesLeftOut::classFilePatterns)
                .map(HostClassesLeftOut::compile)
                .toList();
        return patterns.isEmpty() ? NONE : new HostClassesLeftOut(patterns);
    }

    boolean leavesOut(String binaryName) {
        var classFile = binaryName.replace('.', '/') + CLASS_FILE_SUFFIX;
        return patterns.stream().anyMatch(pattern -> pattern.matcher(classFile).matches());
    }

    private static Stream<String> classFilePatterns(String entry) {
        if (entry.endsWith(CLASS_FILE_SUFFIX) || entry.contains("*")) return Stream.of(entry);
        return Stream.of(entry + CLASS_FILE_SUFFIX, entry + "$*" + CLASS_FILE_SUFFIX);
    }

    private static Pattern compile(String antPattern) {
        var regex = new StringBuilder();
        for (int at = 0; at < antPattern.length(); at++) {
            char c = antPattern.charAt(at);
            if (c != '*') {
                regex.append(Pattern.quote(String.valueOf(c)));
            } else if (at + 1 < antPattern.length() && antPattern.charAt(at + 1) == '*') {
                regex.append(".*");
                at++;
            } else {
                regex.append("[^/]*");
            }
        }
        return Pattern.compile(regex.toString());
    }
}
