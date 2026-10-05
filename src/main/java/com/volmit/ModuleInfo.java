package com.volmit;

import com.volmit.resource.ResourceId;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Module identity. Every stage a module defines has an id that starts with the module id and
 * {@code /}, such as {@code iris:stilt/fill} for module {@code iris:stilt}, so a module id has no
 * {@code /} of its own: {@code iris:stilt/extra} would own stages under {@code iris:stilt}'s path.
 * Settings ids, object feature ids and mantle keys the module owns are its id or start with it and
 * {@code /}, such as {@code iris:stilt} or {@code iris:stilt/depth}. The name is for
 * people, for example in load logs; the id is what the host and other modules match on. Name and
 * version must fit on one log line, so they reject control characters such as line breaks.
 *
 * <p>Optional packages are packages, with their subpackages, whose classes the module's jar uses
 * but expects may be absent at runtime, typically optional integrations of a library it bundles.
 * The pack load does not warn about missing classes in them. They never excuse a class that Iris
 * hides from modules or one missing from a package Iris exposes; those still fail the load.
 */
public record ModuleInfo(ResourceId id, String name, String version, List<PackageName> optionalPackages) {
    private static final String STAGE_SEPARATOR = "/";

    public ModuleInfo {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(version, "version");
        requireSingleSegment(id);
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

    /** Whether {@code stageId} may name a stage of this module: it starts with this module's id and '/'. */
    public boolean ownsStage(ResourceId stageId) {
        return stageId.namespace().equals(id.namespace()) && stageId.path().startsWith(childPathPrefix());
    }

    /** Whether {@code featureId} may name settings, an object generator or a mantle layer of this
     * module: it is this module's id or starts with it and '/'.
     */
    public boolean ownsId(ResourceId featureId) {
        return featureId.equals(id) || ownsStage(featureId);
    }

    /** What every stage id of this module, and every other id below it, starts with, such as {@code iris:stilt/}. */
    public String idPrefix() {
        return id.namespace() + ":" + childPathPrefix();
    }

    private String childPathPrefix() {
        return id.path() + STAGE_SEPARATOR;
    }

    private static void requireSingleSegment(ResourceId id) {
        if (!id.path().contains(STAGE_SEPARATOR)) return;
        throw new IllegalArgumentException("Module id " + id + " must not contain '" + STAGE_SEPARATOR
                + "': stage ids are a module id, '" + STAGE_SEPARATOR + "' and a name, so " + id
                + " would name stages under another module's id");
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
