/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package art.arcane.iris.pack.validation;

import java.util.Objects;

/**
 * One gating decision: {@code key} in {@code registry} is missing on this server, and the gate took {@code action} on
 * {@code subjectType}/{@code subjectKey} (a registrant type name and load key, or an object path) at {@code detail}
 * (a field path such as {@code layers[1].palette[2]}, or the fallback that was applied). Findings are deduplicated on
 * every field except {@code detail}.
 */
public record CompatFinding(CompatRegistry registry,
                            String key,
                            CompatAction action,
                            String subjectType,
                            String subjectKey,
                            String detail) {
    public CompatFinding {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(action, "action");
        subjectType = subjectType == null ? "" : subjectType;
        subjectKey = subjectKey == null ? "" : subjectKey;
        detail = detail == null ? "" : detail;
    }

    /** Grouping key shared by every finding about the same registry entry. */
    public String groupKey() {
        return registry.name() + ':' + key;
    }

    /** Deduplication key: one finding per registry, key, action and subject. */
    public String dedupKey() {
        return groupKey() + '|' + action.name() + '|' + subjectType + '|' + subjectKey;
    }

    /** {@code <key> (<registry>): <action> <subjectType> <subjectKey> at <detail>} */
    public String line() {
        StringBuilder sb = new StringBuilder(96);
        sb.append(key).append(" (").append(registry.label()).append("): ");
        return appendSubject(sb);
    }

    /** The part after the key, for lines already grouped under the key. */
    public String subjectLine() {
        return appendSubject(new StringBuilder(64));
    }

    private String appendSubject(StringBuilder sb) {
        sb.append(action.label());
        if (!subjectType.isEmpty()) {
            sb.append(' ').append(subjectType);
        }
        if (!subjectKey.isEmpty()) {
            sb.append(' ').append(subjectKey);
        }
        if (!detail.isEmpty()) {
            sb.append(" at ").append(detail);
        }
        return sb.toString();
    }
}
