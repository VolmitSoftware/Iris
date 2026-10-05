package com.volmit;

import com.volmit.resource.ResourceId;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Module identity. Stages a module defines must use the namespace of its id. The name is for
 * people, for example in load logs; the id is what the host and other modules match on. Name and
 * version must fit on one log line, so they reject control characters such as line breaks.
 *
 * <p>Optional packages are packages, with their subpackages, whose classes the module's jar uses
 * but expects may be absent at runtime, typically optional integrations of a library it bundles.
 * The pack load does not warn about missing classes in them. They never excuse a class that Iris
 * hides from modules or one missing from a package Iris exposes; those still fail the load.
 */
public record ModuleInfo(ResourceId id, String name, String version, List<PackageName> optionalPackages) {
    public ModuleInfo {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(version, "version");
        requireOneLineText(id, "name", name);
        requireOneLineText(id, "version", version);
        optionalPackages = List.copyOf(optionalPackages);
    }

    public static ModuleInfo of(String id, String name, String version) {
        return new ModuleInfo(ResourceId.parse(id), name, version, List.of());
    }

    /** This identity with {@code packages} as its optional packages, replacing any declared before.
     * A trailing dot is accepted; anything that is not a package name, such as a wildcard, is rejected.
     */
    public ModuleInfo withOptionalPackages(String... packages) {
        var parsed = Arrays.stream(packages).map(this::requireOptionalPackage).toList();
        return new ModuleInfo(id, name, version, parsed);
    }

    private PackageName requireOptionalPackage(String packageName) {
        Objects.requireNonNull(packageName, "optional package");
        return PackageName.tryParse(packageName).orElseThrow(() -> new IllegalArgumentException("Optional package '"
                + packageName + "' of module " + id + " is not a package name; name a package such as org.slf4j,"
                + " which covers that package and its subpackages"));
    }

    private static void requireOneLineText(ResourceId id, String field, String value) {
        if (value.isBlank()) {
            throw new IllegalArgumentException("Blank module " + field + ": " + id);
        }
        if (value.chars().anyMatch(ModuleInfo::breaksLogLine)) {
            throw new IllegalArgumentException("Module " + field + " of " + id
                    + " contains a line break or other control character");
        }
    }

    private static boolean breaksLogLine(int character) {
        var type = Character.getType(character);
        return Character.isISOControl(character)
                || type == Character.LINE_SEPARATOR
                || type == Character.PARAGRAPH_SEPARATOR;
    }
}
