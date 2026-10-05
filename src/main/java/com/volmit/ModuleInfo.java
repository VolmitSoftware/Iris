package com.volmit;

import com.volmit.resource.ResourceId;
import java.util.Objects;

/** Module identity. Stages a module defines must use the namespace of its id. The name is for
 * people, for example in load logs; the id is what the host and other modules match on. Name and
 * version must fit on one log line, so they reject control characters such as line breaks.
 */
public record ModuleInfo(ResourceId id, String name, String version) {
    public ModuleInfo {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(version, "version");
        requireOneLineText(id, "name", name);
        requireOneLineText(id, "version", version);
    }

    public static ModuleInfo of(String id, String name, String version) {
        return new ModuleInfo(ResourceId.parse(id), name, version);
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
