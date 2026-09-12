package art.arcane.iris.structure.nativegen;

import art.arcane.iris.structure.placement.IrisStructureStiltSettings;
import art.arcane.iris.structure.placement.IrisStructureTerrain;
import com.google.gson.Gson;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;

public record NativeStructureOwnershipRecord(
        int schema,
        String structureKey,
        int originChunkX,
        int originChunkZ,
        long placementIdentity,
        int baseY,
        int contentMinX,
        int contentMinY,
        int contentMinZ,
        int contentMaxX,
        int contentMaxY,
        int contentMaxZ,
        int locatorY,
        int referenceMinChunkX,
        int referenceMaxChunkX,
        int referenceMinChunkZ,
        int referenceMaxChunkZ,
        String contentFingerprint,
        DecisionSnapshot decision
) {
    public static final int CURRENT_SCHEMA = 2;
    public static final int MAX_REFERENCE_DISTANCE_CHUNKS = 8;
    private static final int MAX_KEY_BYTES = 512;
    private static final int MAX_FINGERPRINT_BYTES = 128;

    public NativeStructureOwnershipRecord {
        if (schema != CURRENT_SCHEMA) {
            throw new IllegalArgumentException("Unsupported native structure ownership schema " + schema);
        }
        structureKey = normalizeKey(structureKey);
        requireUtf8Limit(structureKey, MAX_KEY_BYTES, "structure key");
        if (contentMinX > contentMaxX || contentMinY > contentMaxY || contentMinZ > contentMaxZ) {
            throw new IllegalArgumentException("Native structure content bounds are inverted");
        }
        if ((long) originChunkX - referenceMinChunkX > MAX_REFERENCE_DISTANCE_CHUNKS
                || (long) referenceMaxChunkX - originChunkX > MAX_REFERENCE_DISTANCE_CHUNKS
                || (long) originChunkZ - referenceMinChunkZ > MAX_REFERENCE_DISTANCE_CHUNKS
                || (long) referenceMaxChunkZ - originChunkZ > MAX_REFERENCE_DISTANCE_CHUNKS) {
            throw new IllegalArgumentException("Native structure ownership exceeds Minecraft's reference range");
        }
        long referenceMinBlockX = (long) referenceMinChunkX << 4;
        long referenceMaxBlockX = ((long) referenceMaxChunkX << 4) + 15L;
        long referenceMinBlockZ = (long) referenceMinChunkZ << 4;
        long referenceMaxBlockZ = ((long) referenceMaxChunkZ << 4) + 15L;
        if (contentMinX < referenceMinBlockX || contentMaxX > referenceMaxBlockX
                || contentMinZ < referenceMinBlockZ || contentMaxZ > referenceMaxBlockZ) {
            throw new IllegalArgumentException(
                    "Native structure content bounds exceed their reference envelope");
        }
        contentFingerprint = Objects.requireNonNull(contentFingerprint,
                "Native structure content fingerprint must not be null").toLowerCase(Locale.ROOT);
        requireUtf8Limit(contentFingerprint, MAX_FINGERPRINT_BYTES, "content fingerprint");
        if (!contentFingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Native structure content fingerprint must be SHA-256 hex");
        }
        decision = Objects.requireNonNull(decision, "Native structure decision snapshot must not be null");
    }

    public static NativeStructureOwnershipRecord create(String structureKey,
                                                         int originChunkX,
                                                         int originChunkZ,
                                                         long placementIdentity,
                                                         int baseY,
                                                         int contentMinX,
                                                         int contentMinY,
                                                         int contentMinZ,
                                                         int contentMaxX,
                                                         int contentMaxY,
                                                         int contentMaxZ,
                                                         int locatorY,
                                                         int referenceMinChunkX,
                                                         int referenceMaxChunkX,
                                                         int referenceMinChunkZ,
                                                         int referenceMaxChunkZ,
                                                         String contentFingerprint,
                                                         IrisNativeStructureDecision decision) {
        return new NativeStructureOwnershipRecord(
                CURRENT_SCHEMA,
                structureKey,
                originChunkX,
                originChunkZ,
                placementIdentity,
                baseY,
                contentMinX,
                contentMinY,
                contentMinZ,
                contentMaxX,
                contentMaxY,
                contentMaxZ,
                locatorY,
                referenceMinChunkX,
                referenceMaxChunkX,
                referenceMinChunkZ,
                referenceMaxChunkZ,
                contentFingerprint,
                DecisionSnapshot.capture(decision)
        );
    }

    public boolean covers(int chunkX, int chunkZ) {
        return chunkX >= referenceMinChunkX && chunkX <= referenceMaxChunkX
                && chunkZ >= referenceMinChunkZ && chunkZ <= referenceMaxChunkZ;
    }

    public NativeStructureOwnershipRecord withReferenceEnvelope(
            int referenceMinChunkX, int referenceMaxChunkX,
            int referenceMinChunkZ, int referenceMaxChunkZ) {
        return new NativeStructureOwnershipRecord(
                schema,
                structureKey,
                originChunkX,
                originChunkZ,
                placementIdentity,
                baseY,
                contentMinX,
                contentMinY,
                contentMinZ,
                contentMaxX,
                contentMaxY,
                contentMaxZ,
                locatorY,
                referenceMinChunkX,
                referenceMaxChunkX,
                referenceMinChunkZ,
                referenceMaxChunkZ,
                contentFingerprint,
                decision
        );
    }

    public OwnershipKey ownershipKey() {
        return new OwnershipKey(structureKey, originChunkX, originChunkZ);
    }

    public IrisNativeStructureDecision restoredDecision() {
        return decision.restore();
    }

    void write(DataOutputStream output) throws IOException {
        output.writeInt(schema);
        writeString(output, structureKey, MAX_KEY_BYTES, "structure key");
        output.writeInt(originChunkX);
        output.writeInt(originChunkZ);
        output.writeLong(placementIdentity);
        output.writeInt(baseY);
        output.writeInt(contentMinX);
        output.writeInt(contentMinY);
        output.writeInt(contentMinZ);
        output.writeInt(contentMaxX);
        output.writeInt(contentMaxY);
        output.writeInt(contentMaxZ);
        output.writeInt(locatorY);
        output.writeInt(referenceMinChunkX);
        output.writeInt(referenceMaxChunkX);
        output.writeInt(referenceMinChunkZ);
        output.writeInt(referenceMaxChunkZ);
        writeString(output, contentFingerprint, MAX_FINGERPRINT_BYTES, "content fingerprint");
        decision.write(output);
    }

    static NativeStructureOwnershipRecord read(DataInputStream input) throws IOException {
        int schema = input.readInt();
        if (schema != CURRENT_SCHEMA) {
            throw new IOException("Unsupported native structure ownership schema " + schema);
        }
        try {
            return new NativeStructureOwnershipRecord(
                    schema,
                    readString(input, MAX_KEY_BYTES, "structure key"),
                    input.readInt(),
                    input.readInt(),
                    input.readLong(),
                    input.readInt(),
                    input.readInt(),
                    input.readInt(),
                    input.readInt(),
                    input.readInt(),
                    input.readInt(),
                    input.readInt(),
                    input.readInt(),
                    input.readInt(),
                    input.readInt(),
                    input.readInt(),
                    input.readInt(),
                    readString(input, MAX_FINGERPRINT_BYTES, "content fingerprint"),
                    DecisionSnapshot.read(input)
            );
        } catch (IllegalArgumentException exception) {
            throw new IOException("Native structure ownership record is invalid", exception);
        }
    }

    private static String normalizeKey(String key) {
        String normalized = Objects.requireNonNull(key,
                "Native structure key must not be null").trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Native structure key must not be blank");
        }
        return normalized;
    }

    static void writeString(DataOutputStream output, String value, int maximumBytes,
                            String fieldName) throws IOException {
        byte[] encoded = Objects.requireNonNull(value, fieldName + " must not be null")
                .getBytes(StandardCharsets.UTF_8);
        if (encoded.length > maximumBytes) {
            throw new IOException("Native structure " + fieldName + " exceeds " + maximumBytes + " bytes");
        }
        output.writeInt(encoded.length);
        output.write(encoded);
    }

    static String readString(DataInputStream input, int maximumBytes, String fieldName) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > maximumBytes) {
            throw new IOException("Native structure " + fieldName + " has invalid length " + length);
        }
        byte[] encoded = input.readNBytes(length);
        if (encoded.length != length) {
            throw new IOException("Native structure " + fieldName + " ended early");
        }
        return new String(encoded, StandardCharsets.UTF_8);
    }

    private static void requireUtf8Limit(String value, int maximumBytes, String fieldName) {
        if (value.getBytes(StandardCharsets.UTF_8).length > maximumBytes) {
            throw new IllegalArgumentException("Native structure " + fieldName
                    + " exceeds " + maximumBytes + " bytes");
        }
    }

    public record OwnershipKey(String structureKey, int originChunkX, int originChunkZ) {
        public OwnershipKey {
            structureKey = normalizeKey(structureKey);
        }
    }

    public record DecisionSnapshot(String stiltJson, String terrainJson) {
        private static final Gson GSON = new Gson();
        private static final int MAX_JSON_BYTES = 65_536;

        public DecisionSnapshot {
            stiltJson = Objects.requireNonNull(stiltJson, "Native structure stilt snapshot must not be null");
            terrainJson = Objects.requireNonNull(terrainJson, "Native structure terrain snapshot must not be null");
            requireUtf8Limit(stiltJson, MAX_JSON_BYTES, "stilt snapshot");
            requireUtf8Limit(terrainJson, MAX_JSON_BYTES, "terrain snapshot");
        }

        public static DecisionSnapshot capture(IrisNativeStructureDecision decision) {
            IrisNativeStructureDecision resolved = Objects.requireNonNull(
                    decision, "Native structure decision must not be null");
            if (!resolved.generate()) {
                throw new IllegalArgumentException("Only generated native structure decisions can be persisted");
            }
            return new DecisionSnapshot(
                    GSON.toJson(resolved.stilt()),
                    GSON.toJson(Objects.requireNonNullElseGet(
                            resolved.terrain(), IrisStructureTerrain::new))
            );
        }

        public IrisNativeStructureDecision restore() {
            IrisStructureStiltSettings stilt = "null".equals(stiltJson)
                    ? null : GSON.fromJson(stiltJson, IrisStructureStiltSettings.class);
            IrisStructureTerrain terrain = GSON.fromJson(terrainJson, IrisStructureTerrain.class);
            if (terrain == null) {
                throw new IllegalStateException("Persisted native structure terrain snapshot is null");
            }
            return new IrisNativeStructureDecision(
                    NativeStructureGenerationStatus.GENERATE_NATIVE,
                    0,
                    null,
                    false,
                    stilt,
                    terrain
            );
        }

        void write(DataOutputStream output) throws IOException {
            writeString(output, stiltJson, MAX_JSON_BYTES, "stilt snapshot");
            writeString(output, terrainJson, MAX_JSON_BYTES, "terrain snapshot");
        }

        static DecisionSnapshot read(DataInputStream input) throws IOException {
            return new DecisionSnapshot(
                    readString(input, MAX_JSON_BYTES, "stilt snapshot"),
                    readString(input, MAX_JSON_BYTES, "terrain snapshot")
            );
        }
    }
}
