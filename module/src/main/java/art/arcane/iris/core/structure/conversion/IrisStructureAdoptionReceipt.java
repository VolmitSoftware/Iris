package art.arcane.iris.core.structure.conversion;

import art.arcane.iris.core.structure.authoring.StructureHash;
import art.arcane.iris.core.structure.authoring.StructureKey;
import art.arcane.iris.core.structure.authoring.StructureOwnershipManifest;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

public record IrisStructureAdoptionReceipt(
        UUID receiptId,
        UUID planId,
        StructureOwnershipManifest.Origin origin,
        Instant appliedAt,
        StructureKey sourceStructure,
        StructureKey targetStructure,
        String sourceClosureHash,
        String planHash,
        Map<String, String> sourceResourceHashes,
        Map<String, String> targetResourceHashes,
        Map<String, String> sourceToTargetPaths,
        StructureOwnershipManifest.RollbackDisposition rollbackDisposition
) {
    public IrisStructureAdoptionReceipt {
        Objects.requireNonNull(receiptId, "receiptId");
        Objects.requireNonNull(planId, "planId");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(appliedAt, "appliedAt");
        Objects.requireNonNull(sourceStructure, "sourceStructure");
        Objects.requireNonNull(targetStructure, "targetStructure");
        requireHash(sourceClosureHash, "source closure");
        requireHash(planHash, "plan");
        sourceResourceHashes = immutableMap(sourceResourceHashes, true);
        targetResourceHashes = immutableMap(targetResourceHashes, true);
        sourceToTargetPaths = immutableMap(sourceToTargetPaths, false);
        rollbackDisposition = Objects.requireNonNull(rollbackDisposition, "rollbackDisposition");
        if (sourceResourceHashes.isEmpty() || targetResourceHashes.isEmpty() || sourceToTargetPaths.isEmpty()) {
            throw new IllegalArgumentException("Adoption receipt requires source, target, and mapping entries");
        }
    }

    public StructureOwnershipManifest.Provenance provenance() {
        return new StructureOwnershipManifest.Provenance(
                origin,
                receiptId.toString(),
                planHash,
                sourceClosureHash,
                appliedAt.toEpochMilli(),
                sourceResourceHashes,
                sourceToTargetPaths,
                rollbackDisposition
        );
    }

    public boolean rollbackAvailable() {
        return rollbackDisposition != StructureOwnershipManifest.RollbackDisposition.NONE;
    }

    private static Map<String, String> immutableMap(Map<String, String> values, boolean hashes) {
        Objects.requireNonNull(values, "receipt values");
        TreeMap<String, String> ordered = new TreeMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = Objects.requireNonNull(entry.getKey(), "receipt map key");
            String value = Objects.requireNonNull(entry.getValue(), "receipt map value");
            if (hashes) {
                requireHash(value, "resource");
            }
            ordered.put(key, value);
        }
        return Collections.unmodifiableMap(ordered);
    }

    private static void requireHash(String value, String kind) {
        if (!StructureHash.isSha256(value)) {
            throw new IllegalArgumentException("Adoption receipt " + kind + " hash must be SHA-256");
        }
    }
}
