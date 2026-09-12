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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every gating decision made for one pack instance ({@code IrisData}). Thread-safe; findings are deduplicated on
 * {@link CompatFinding#dedupKey()} and kept in first-recorded order. Console rendering lives here so Bukkit and modded
 * print the same text.
 */
public final class PackCompatReport {
    private static final Comparator<CompatFinding> BY_ACTION = Comparator.comparingInt(finding -> finding.action().ordinal());

    private final Map<String, CompatFinding> findings = new LinkedHashMap<>();
    private String incompleteReason;

    /** A report rebuilt from persisted findings, for rendering a boot listing or a command listing again. */
    public static PackCompatReport of(Iterable<CompatFinding> findings) {
        PackCompatReport report = new PackCompatReport();
        report.recordAll(findings);
        return report;
    }

    public synchronized void record(CompatFinding finding) {
        if (finding == null) {
            return;
        }
        findings.putIfAbsent(finding.dedupKey(), finding);
    }

    public synchronized void recordAll(Iterable<CompatFinding> all) {
        if (all == null) {
            return;
        }
        for (CompatFinding finding : all) {
            record(finding);
        }
    }

    /** Forget every finding and any incomplete marker; a studio hotload rebuilds the pack's report from scratch. */
    public synchronized void clear() {
        findings.clear();
        incompleteReason = null;
    }

    /** The registry could not be consulted for part of the pack; the report may be missing findings. */
    public synchronized void markIncomplete(String reason) {
        if (incompleteReason == null) {
            incompleteReason = reason == null ? "registry not ready" : reason;
        }
    }

    public synchronized boolean isIncomplete() {
        return incompleteReason != null;
    }

    public synchronized String incompleteReason() {
        return incompleteReason;
    }

    public synchronized boolean isEmpty() {
        return findings.isEmpty();
    }

    public synchronized int size() {
        return findings.size();
    }

    public synchronized List<CompatFinding> findings() {
        return List.copyOf(findings.values());
    }

    /**
     * Findings grouped by {@link CompatFinding#groupKey()}, keys in first-seen order; inside a key the exclusions come
     * first, then the drops, then the substitutions (first-seen within each action), so a capped listing shows the
     * decisions that removed content before the ones that kept it.
     */
    public synchronized Map<String, List<CompatFinding>> byKey() {
        Map<String, List<CompatFinding>> grouped = new LinkedHashMap<>();
        for (CompatFinding finding : findings.values()) {
            grouped.computeIfAbsent(finding.groupKey(), ignored -> new ArrayList<>()).add(finding);
        }
        for (List<CompatFinding> group : grouped.values()) {
            group.sort(BY_ACTION);
        }
        return grouped;
    }

    public synchronized Map<CompatAction, Integer> countsByAction() {
        Map<CompatAction, Integer> counts = new EnumMap<>(CompatAction.class);
        for (CompatFinding finding : findings.values()) {
            counts.merge(finding.action(), 1, Integer::sum);
        }
        return counts;
    }

    public synchronized int distinctKeys() {
        return byKey().size();
    }

    /**
     * {@code 3 content keys unavailable on Minecraft 26.1.2: 2 excluded, 5 dropped, 1 substituted.} Empty string when
     * there is nothing to say.
     */
    public synchronized String summaryLine(String minecraftVersion) {
        if (findings.isEmpty()) {
            return "";
        }
        Map<CompatAction, Integer> counts = countsByAction();
        int keys = distinctKeys();
        StringBuilder sb = new StringBuilder(96);
        sb.append(keys).append(keys == 1 ? " content key" : " content keys")
                .append(" unavailable on Minecraft ").append(versionOrUnknown(minecraftVersion)).append(": ");
        boolean first = true;
        for (CompatAction action : CompatAction.values()) {
            int count = counts.getOrDefault(action, 0);
            if (count == 0) {
                continue;
            }
            if (!first) {
                sb.append(", ");
            }
            sb.append(count).append(' ').append(action.label());
            first = false;
        }
        sb.append('.');
        return sb.toString();
    }

    /**
     * Boot listing: header, one line per key with up to {@code perKeyCap} subjects, then the remediation hint. Empty
     * list when there are no findings.
     */
    public synchronized List<String> bootLines(String packName, String minecraftVersion, int perKeyCap) {
        List<String> lines = new ArrayList<>();
        if (findings.isEmpty()) {
            return lines;
        }
        lines.add("Pack '" + packName + "': content unavailable on Minecraft " + versionOrUnknown(minecraftVersion));
        for (String line : keyLines(perKeyCap)) {
            lines.add("  " + line);
        }
        lines.add("  Update the server to a newer Minecraft to restore this content, or declare fallbacks"
                + " (dimension blockFallbacks, block backup). /iris pack compat " + packName + " lists everything.");
        return lines;
    }

    /**
     * One line per key, with up to {@code perKeyCap} subjects each and a {@code +N more} tail beyond that; pass 0 for
     * every subject. No header and no remediation hint - the command wraps these in localized text.
     */
    public synchronized List<String> keyLines(int perKeyCap) {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, List<CompatFinding>> entry : byKey().entrySet()) {
            lines.add(renderKeyLine(entry.getValue(), perKeyCap));
        }
        if (incompleteReason != null) {
            lines.add("(incomplete: " + incompleteReason + ")");
        }
        return lines;
    }

    /** One line per finding, grouped under a key header line. Empty list when there are no findings. */
    public synchronized List<String> detailLines() {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, List<CompatFinding>> entry : byKey().entrySet()) {
            CompatFinding head = entry.getValue().getFirst();
            lines.add(head.key() + " (" + head.registry().label() + ")");
            for (CompatFinding finding : entry.getValue()) {
                lines.add("  " + finding.subjectLine());
            }
        }
        if (incompleteReason != null) {
            lines.add("(incomplete: " + incompleteReason + ")");
        }
        return lines;
    }

    private static String renderKeyLine(List<CompatFinding> group, int perKeyCap) {
        CompatFinding head = group.getFirst();
        StringBuilder sb = new StringBuilder(128);
        sb.append(head.key()).append(" (").append(head.registry().label()).append("): ");
        int cap = perKeyCap <= 0 ? Integer.MAX_VALUE : perKeyCap;
        int shown = 0;
        for (CompatFinding finding : group) {
            if (shown == cap) {
                break;
            }
            if (shown > 0) {
                sb.append("; ");
            }
            sb.append(finding.subjectLine());
            shown++;
        }
        int rest = group.size() - shown;
        if (rest > 0) {
            sb.append("; +").append(rest).append(" more");
        }
        return sb.toString();
    }

    private static String versionOrUnknown(String minecraftVersion) {
        return minecraftVersion == null || minecraftVersion.isBlank() ? "unknown" : minecraftVersion;
    }
}
