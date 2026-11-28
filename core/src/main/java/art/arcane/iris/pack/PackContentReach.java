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

package art.arcane.iris.pack;

import art.arcane.iris.pack.validation.CompatFinding;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Which pack files a dimension can reach, found by following every string in its file that names another pack file,
 * then every string in those files, and so on. It over-reaches on purpose: a string that matches a key in any folder
 * counts, so a finding is only treated as unused when nothing the dimension reaches mentions its subject.
 */
final class PackContentReach {
    private static final String SNIPPET_PREFIX = "snippet/";
    private static final String PLACEMENT_SUBJECT = "placement";
    private static final String PLACEMENT_KEY_SEPARATOR = ", ";
    private static final int KEY_LENGTH_LIMIT = 512;

    private PackContentReach() {
    }

    /** Every string reachable from {@code dimensionFile}, plus the dimension's own key. */
    static Set<String> referencedFrom(File packFolder, File dimensionFile, String dimensionKey, Collection<String> folders) {
        Set<String> reached = new HashSet<>();
        Set<File> visited = new HashSet<>();
        ArrayDeque<File> pending = new ArrayDeque<>();
        pending.add(dimensionFile);
        while (!pending.isEmpty()) {
            File file = pending.poll();
            if (!visited.add(file.getAbsoluteFile())) {
                continue;
            }
            String text;
            try {
                text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            } catch (IOException | RuntimeException unreadable) {
                continue;
            }
            for (String value : PackValidationIo.listReferencedKeysFromCorpus(text)) {
                if (value.isBlank() || value.length() > KEY_LENGTH_LIMIT || value.contains("..") || !reached.add(value)) {
                    continue;
                }
                for (String folder : folders) {
                    queueIfFile(pending, new File(packFolder, folder + "/" + value + ".json"));
                }
                if (value.startsWith(SNIPPET_PREFIX)) {
                    queueIfFile(pending, new File(packFolder, value + ".json"));
                }
            }
        }
        reached.add(dimensionKey);
        return reached;
    }

    /** Findings without a subject count as reached, so nothing is skipped without a reason. */
    static boolean reaches(Set<String> reached, CompatFinding finding) {
        String subjectKey = finding.subjectKey();
        if (subjectKey.isEmpty() || reached.contains(subjectKey)) {
            return true;
        }
        if (PLACEMENT_SUBJECT.equals(finding.subjectType())) {
            for (String objectKey : subjectKey.split(PLACEMENT_KEY_SEPARATOR)) {
                if (reached.contains(objectKey)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void queueIfFile(ArrayDeque<File> pending, File candidate) {
        if (candidate.isFile()) {
            pending.add(candidate);
        }
    }
}
