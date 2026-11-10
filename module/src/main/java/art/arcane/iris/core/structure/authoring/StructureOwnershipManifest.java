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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

public record StructureOwnershipManifest(
        int schemaVersion,
        StructureKey structure,
        StructureSource source,
        StructureBackend backend,
        List<StructureCapability> capabilities,
        List<StructureLoss> losses,
        Map<String, String> resourceHashes,
        Provenance provenance
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public StructureOwnershipManifest {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported structure ownership manifest schema: " + schemaVersion);
        }
        Objects.requireNonNull(structure, "structure");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(backend, "backend");
        Objects.requireNonNull(capabilities, "capabilities");
        Objects.requireNonNull(losses, "losses");
        Objects.requireNonNull(resourceHashes, "resourceHashes");
        provenance = provenance == null ? Provenance.created() : provenance;
        ArrayList<StructureCapability> orderedCapabilities = new ArrayList<>(capabilities);
        orderedCapabilities.sort(Comparator.naturalOrder());
        capabilities = List.copyOf(orderedCapabilities);
        losses = List.copyOf(losses);
        TreeMap<String, String> orderedHashes = new TreeMap<>();
        TreeMap<String, String> portablePaths = new TreeMap<>();
        for (Map.Entry<String, String> entry : resourceHashes.entrySet()) {
            String relativePath = StructureResourceBundle.validateRelativePath(entry.getKey());
            String portablePath = relativePath.toLowerCase(Locale.ROOT);
            String previousPath = portablePaths.putIfAbsent(portablePath, relativePath);
            if (previousPath != null) {
                throw new IllegalArgumentException(
                        "Ownership manifest contains case-colliding resources: " + previousPath + " and " + relativePath
                );
            }
            String contentHash = Objects.requireNonNull(entry.getValue(), "contentHash");
            if (!StructureHash.isSha256(contentHash)) {
                throw new IllegalArgumentException("Invalid SHA-256 hash for resource " + relativePath);
            }
            orderedHashes.put(relativePath, contentHash);
        }
        resourceHashes = Collections.unmodifiableMap(orderedHashes);
    }

    public StructureOwnershipManifest(
            int schemaVersion,
            StructureKey structure,
            StructureSource source,
            StructureBackend backend,
            List<StructureCapability> capabilities,
            List<StructureLoss> losses,
            Map<String, String> resourceHashes
    ) {
        this(schemaVersion, structure, source, backend, capabilities, losses, resourceHashes, Provenance.created());
    }

    public static StructureOwnershipManifest from(StructureResourceBundle bundle) {
        return from(bundle, Provenance.created());
    }

    public static StructureOwnershipManifest from(StructureResourceBundle bundle, Provenance provenance) {
        Objects.requireNonNull(bundle, "bundle");
        Objects.requireNonNull(provenance, "provenance");
        TreeMap<String, String> hashes = new TreeMap<>();
        for (StructureResourceBundle.Resource resource : bundle.resources().values()) {
            hashes.put(resource.relativePath(), resource.contentHash());
        }
        return new StructureOwnershipManifest(
                CURRENT_SCHEMA_VERSION,
                bundle.key(),
                bundle.source(),
                bundle.backend(),
                new ArrayList<>(bundle.capabilities()),
                bundle.losses(),
                hashes,
                provenance
        );
    }

    public static StructureOwnershipManifest fromJson(byte[] content) {
        Objects.requireNonNull(content, "content");
        StructureOwnershipManifest manifest = GSON.fromJson(
                new String(content, StandardCharsets.UTF_8),
                StructureOwnershipManifest.class
        );
        if (manifest == null) {
            throw new IllegalArgumentException("Structure ownership manifest is empty");
        }
        return manifest;
    }

    public byte[] toJson() {
        return GSON.toJson(this).getBytes(StandardCharsets.UTF_8);
    }

    public String relativePath() {
        return relativePath(structure);
    }

    public static String relativePath(StructureKey structure) {
        Objects.requireNonNull(structure, "structure");
        String identityHash = StructureHash.sha256(structure.value().getBytes(StandardCharsets.UTF_8));
        return ".iris/structure-manifests/key-" + identityHash + ".json";
    }

    public record Provenance(
            Origin origin,
            String receiptId,
            String planHash,
            String sourceClosureHash,
            long appliedAtEpochMilli,
            Map<String, String> sourceResourceHashes,
            Map<String, String> sourceToTargetPaths,
            RollbackDisposition rollbackDisposition
    ) {
        public Provenance {
            origin = origin == null ? Origin.CREATED : origin;
            receiptId = normalize(receiptId);
            planHash = normalize(planHash);
            sourceClosureHash = normalize(sourceClosureHash);
            sourceResourceHashes = immutableHashes(sourceResourceHashes);
            sourceToTargetPaths = immutableMappings(sourceToTargetPaths);
            rollbackDisposition = rollbackDisposition == null
                    ? RollbackDisposition.NONE
                    : rollbackDisposition;
            if (origin == Origin.CREATED) {
                if (!receiptId.isEmpty() || !planHash.isEmpty() || !sourceClosureHash.isEmpty()
                        || appliedAtEpochMilli != 0L || !sourceResourceHashes.isEmpty()
                        || !sourceToTargetPaths.isEmpty() || rollbackDisposition != RollbackDisposition.NONE) {
                    throw new IllegalArgumentException("Created structure provenance cannot declare adoption metadata");
                }
            } else {
                requireUuid(receiptId);
                requireHash(planHash, "plan");
                requireHash(sourceClosureHash, "source closure");
                if (appliedAtEpochMilli <= 0L) {
                    throw new IllegalArgumentException("Adoption provenance requires a positive application time");
                }
                if (sourceResourceHashes.isEmpty() || sourceToTargetPaths.isEmpty()) {
                    throw new IllegalArgumentException("Adoption provenance requires source hashes and path mappings");
                }
            }
        }

        public static Provenance created() {
            return new Provenance(
                    Origin.CREATED,
                    "",
                    "",
                    "",
                    0L,
                    Map.of(),
                    Map.of(),
                    RollbackDisposition.NONE
            );
        }

        public boolean adopted() {
            return origin != Origin.CREATED;
        }

        private static Map<String, String> immutableHashes(Map<String, String> hashes) {
            if (hashes == null || hashes.isEmpty()) {
                return Map.of();
            }
            TreeMap<String, String> ordered = new TreeMap<>();
            for (Map.Entry<String, String> entry : hashes.entrySet()) {
                String relativePath = StructureResourceBundle.validateRelativePath(entry.getKey());
                String hash = Objects.requireNonNull(entry.getValue(), "source resource hash");
                requireHash(hash, "source resource");
                ordered.put(relativePath, hash);
            }
            return Collections.unmodifiableMap(ordered);
        }

        private static Map<String, String> immutableMappings(Map<String, String> mappings) {
            if (mappings == null || mappings.isEmpty()) {
                return Map.of();
            }
            TreeMap<String, String> ordered = new TreeMap<>();
            for (Map.Entry<String, String> entry : mappings.entrySet()) {
                String sourcePath = StructureResourceBundle.validateRelativePath(entry.getKey());
                String targetPath = StructureResourceBundle.validateRelativePath(
                        Objects.requireNonNull(entry.getValue(), "target resource path"));
                ordered.put(sourcePath, targetPath);
            }
            return Collections.unmodifiableMap(ordered);
        }

        private static void requireUuid(String value) {
            try {
                UUID.fromString(value);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Adoption receipt ID must be a UUID", exception);
            }
        }

        private static void requireHash(String value, String kind) {
            if (!StructureHash.isSha256(value)) {
                throw new IllegalArgumentException("Adoption " + kind + " hash must be SHA-256");
            }
        }

        private static String normalize(String value) {
            return value == null ? "" : value.trim();
        }
    }

    public enum Origin {
        CREATED,
        ADOPTED_EXISTING,
        ADOPTED_CLONE,
        ADOPTED_MANAGED_CLONE,
        CONVERTED,
        MANAGED_DATAPACK
    }

    public enum RollbackDisposition {
        NONE,
        DELETE_CREATED_IF_UNCHANGED
    }
}
