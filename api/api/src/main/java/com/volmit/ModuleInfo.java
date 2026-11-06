package com.volmit;

import com.volmit.resource.ResourceId;
import java.util.Objects;

/** Module identity. Every stage a module defines has an id that starts with the module id and
 * {@code /}, such as {@code iris:stilt/fill} for module {@code iris:stilt}, so a module id has no
 * {@code /} of its own: {@code iris:stilt/extra} would own stages under {@code iris:stilt}'s path.
 * Settings ids, object feature ids and mantle keys the module owns are its id or start with it and
 * {@code /}, such as {@code iris:stilt} or {@code iris:stilt/depth}. The name is for
 * people, for example in load logs; the id is what the host and other modules match on. Name and
 * version must fit on one log line, so they reject control characters such as line breaks.
 *
 * <p>What the module's jar needs at load, such as packages it expects may be missing, is the jar's, declared in its
 * manifest (see README, "Optional packages"), not the module's identity.
 */
public record ModuleInfo(ResourceId id, String name, String version) {
    private static final String STAGE_SEPARATOR = "/";

    public ModuleInfo {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(version, "version");
        requireSingleSegment(id);
        requireOneLineText(id, "name", name);
        requireOneLineText(id, "version", version);
    }

    public static ModuleInfo of(String id, String name, String version) {
        return new ModuleInfo(ResourceId.parse(id), name, version);
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
