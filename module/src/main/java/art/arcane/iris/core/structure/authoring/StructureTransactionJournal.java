/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.core.structure.authoring;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

record StructureTransactionJournal(
        int schemaVersion,
        UUID transactionId,
        Phase phase,
        List<Target> targets
) {
    static final int CURRENT_SCHEMA_VERSION = 1;
    static final String FILE_NAME = "transaction.json";
    static final String NEXT_FILE_NAME = "transaction.json.next";
    private static final int MAX_TARGETS = 100_000;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern MANIFEST_PATH = Pattern.compile(
            "\\.iris/structure-manifests/key-[a-f0-9]{64}\\.json"
    );

    StructureTransactionJournal {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported structure transaction schema: " + schemaVersion);
        }
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(targets, "targets");
        if (targets.isEmpty()) {
            throw new IllegalArgumentException("Structure transaction must contain at least one target");
        }
        if (targets.size() > MAX_TARGETS) {
            throw new IllegalArgumentException("Structure transaction contains too many targets");
        }
        ArrayList<Target> orderedTargets = new ArrayList<>(targets);
        orderedTargets.sort(Comparator.comparing(Target::relativePath));
        Set<String> portablePaths = new HashSet<>(orderedTargets.size());
        for (Target target : orderedTargets) {
            String portablePath = target.relativePath().toLowerCase(Locale.ROOT);
            if (!portablePaths.add(portablePath)) {
                throw new IllegalArgumentException("Structure transaction contains duplicate target "
                        + target.relativePath());
            }
        }
        targets = List.copyOf(orderedTargets);
    }

    static StructureTransactionJournal prepared(UUID transactionId, List<Target> targets) {
        return new StructureTransactionJournal(CURRENT_SCHEMA_VERSION, transactionId, Phase.PREPARED, targets);
    }

    static StructureTransactionJournal fromJson(byte[] content) {
        Objects.requireNonNull(content, "content");
        StructureTransactionJournal journal = GSON.fromJson(
                new String(content, StandardCharsets.UTF_8),
                StructureTransactionJournal.class
        );
        if (journal == null) {
            throw new IllegalArgumentException("Structure transaction journal is empty");
        }
        return journal;
    }

    StructureTransactionJournal committed() {
        return new StructureTransactionJournal(schemaVersion, transactionId, Phase.COMMITTED, targets);
    }

    byte[] toJson() {
        return GSON.toJson(this).getBytes(StandardCharsets.UTF_8);
    }

    enum Phase {
        PREPARED,
        COMMITTED
    }

    record Target(
            String relativePath,
            boolean hadOriginal,
            String originalHash,
            String replacementHash
    ) {
        Target {
            Objects.requireNonNull(relativePath, "relativePath");
            Objects.requireNonNull(originalHash, "originalHash");
            Objects.requireNonNull(replacementHash, "replacementHash");
            if (relativePath.startsWith(".iris/")) {
                StructureResourceBundle.validateRelativePath("transaction/" + relativePath);
                if (!MANIFEST_PATH.matcher(relativePath).matches()) {
                    throw new IllegalArgumentException("Invalid internal structure transaction target: "
                            + relativePath);
                }
            } else {
                relativePath = StructureResourceBundle.validateRelativePath(relativePath);
            }
            if (hadOriginal && !StructureHash.isSha256(originalHash)) {
                throw new IllegalArgumentException("Structure transaction original hash is invalid for "
                        + relativePath);
            }
            if (!hadOriginal && !originalHash.isEmpty()) {
                throw new IllegalArgumentException("Structure transaction has a hash for a new target "
                        + relativePath);
            }
            if (!replacementHash.isEmpty() && !StructureHash.isSha256(replacementHash)) {
                throw new IllegalArgumentException("Structure transaction replacement hash is invalid for "
                        + relativePath);
            }
            if (!hadOriginal && replacementHash.isEmpty()) {
                throw new IllegalArgumentException("Structure transaction new target has no replacement hash for "
                        + relativePath);
            }
        }
    }
}
