package com.volmit.host;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.jar.Manifest;
import java.util.regex.Pattern;

/** One entry of a jar's {@value #MANIFEST_ATTRIBUTE} attribute: the tooling part of one platform that starts at
 * {@code entryClass}. The part is that class's package with its subpackages. Its classes are loaded by the platform's
 * own loader ({@link ToolingPartLoader}), never by the pack's, so they may use the platform; generation code may not
 * use them.
 *
 * <p>The attribute lists {@code <platform>=<entry class>} entries separated by spaces; a platform may appear more than
 * once. Entries of one platform may share a package. Otherwise parts must not overlap: two entries in one package, or
 * in nested packages, of different platforms or of nested packages would put a class in two parts.
 *
 * @param jar the load's copy of the jar that holds the part, see {@link JarSnapshot}
 * @param jarOrigin the original jar's name as messages show it
 */
public record ToolingPart(String platform, String entryClass, PackageName packageName, String jarOrigin, Path jar) {
    static final String MANIFEST_ATTRIBUTE = "Iris-Tooling-Parts";
    private static final Pattern PLATFORM_NAME = Pattern.compile("[a-z][a-z0-9]*");
    private static final String ENTRY_SEPARATORS = "\\s+";
    private static final char PLATFORM_SEPARATOR = '=';
    private static final char PACKAGE_SEPARATOR = '.';
    private static final String ENTRY_FORMAT_HINT = "write it like bukkit=com.example.tools.StudioPart, a platform name and"
            + " an entry class that is in a package";

    static boolean isPlatformName(String name) {
        return PLATFORM_NAME.matcher(name).matches();
    }

    /** The parts a jar's manifest declares, in the order it lists them. Fails on an entry that is not a platform and a
     * class in a package, and on parts that share classes.
     */
    static List<ToolingPart> readAll(String origin, Path jar, Manifest manifest) {
        var declared = manifest == null ? null : manifest.getMainAttributes().getValue(MANIFEST_ATTRIBUTE);
        if (declared == null || declared.isBlank()) return List.of();
        var parts = Arrays.stream(declared.trim().split(ENTRY_SEPARATORS))
                .distinct()
                .map(entry -> parse(origin, jar, entry))
                .toList();
        requireNoSharedClasses(origin, parts);
        return parts;
    }

    /** Fails when a part's entry class is not among {@code classNames}, the classes of its jar. */
    static void requireEntryClassesIn(String origin, List<ToolingPart> parts, Set<String> classNames) {
        for (var part : parts) {
            if (classNames.contains(part.entryClass)) continue;
            throw ModuleLoadException.ofJar(origin, "Module jar " + origin + " declares tooling part " + part.declaration()
                    + " in " + MANIFEST_ATTRIBUTE + ", but the jar holds no class " + part.entryClass);
        }
    }

    /** Whether the class is in this part's package or one of its subpackages. */
    boolean contains(String binaryName) {
        return packageName.contains(binaryName);
    }

    /** Whether both entries start the same part: the same platform and the same package. */
    boolean inSamePartAs(ToolingPart other) {
        return platform.equals(other.platform) && packageName.equals(other.packageName);
    }

    String declaration() {
        return platform + PLATFORM_SEPARATOR + entryClass;
    }

    private static ToolingPart parse(String origin, Path jar, String entry) {
        int separator = entry.indexOf(PLATFORM_SEPARATOR);
        var platform = separator < 0 ? "" : entry.substring(0, separator);
        var entryClass = separator < 0 ? "" : entry.substring(separator + 1);
        int classSeparator = entryClass.lastIndexOf(PACKAGE_SEPARATOR);
        boolean wellFormed = isPlatformName(platform) && classSeparator > 0 && !entryClass.endsWith(String.valueOf(PACKAGE_SEPARATOR))
                && PackageName.tryParse(entryClass).isPresent();
        if (!wellFormed) throw malformedEntry(origin, entry);
        return new ToolingPart(platform, entryClass, new PackageName(entryClass.substring(0, classSeparator)), origin, jar);
    }

    private static ModuleLoadException malformedEntry(String origin, String entry) {
        return ModuleLoadException.ofJar(origin, "Module jar " + origin + " declares tooling part '" + entry + "' in "
                + MANIFEST_ATTRIBUTE + ", which is malformed; " + ENTRY_FORMAT_HINT);
    }

    private static void requireNoSharedClasses(String origin, List<ToolingPart> parts) {
        for (int first = 0; first < parts.size(); first++) {
            for (int second = first + 1; second < parts.size(); second++) {
                if (parts.get(first).sharesClassesWithoutBeingOnePart(parts.get(second))) {
                    throw overlappingParts(origin, parts.get(first), parts.get(second));
                }
            }
        }
    }

    private boolean sharesClassesWithoutBeingOnePart(ToolingPart other) {
        boolean packagesOverlap = contains(other.entryClass) || other.contains(entryClass);
        return packagesOverlap && !inSamePartAs(other);
    }

    private static ModuleLoadException overlappingParts(String origin, ToolingPart first, ToolingPart second) {
        return ModuleLoadException.ofJar(origin, "Module jar " + origin + " declares overlapping tooling parts "
                + first.declaration() + " and " + second.declaration() + " in " + MANIFEST_ATTRIBUTE + "; a part is its entry"
                + " class's package with its subpackages, and no class may belong to two parts");
    }
}
