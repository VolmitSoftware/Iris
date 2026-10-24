package art.arcane.iris.engine.framework;


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
        MemoSnapshot memo
) {
    public static final int CURRENT_SCHEMA = 3;
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
        memo = Objects.requireNonNull(memo, "Native structure memo snapshot must not be null");
    }

    public static NativeStructureOwnershipRecord create(String structureKey,
                                                         int originChunkX,
                                                         int originChunkZ,
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
                                                         NativeStartMemo memo) {
        return new NativeStructureOwnershipRecord(
                CURRENT_SCHEMA,
                structureKey,
                originChunkX,
                originChunkZ,
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
                MemoSnapshot.of(memo)
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
                memo
        );
    }

    public OwnershipKey ownershipKey() {
        return new OwnershipKey(structureKey, originChunkX, originChunkZ);
    }

    /** The memo the module gave the start when it placed it; null when it gave none. */
    public NativeStartMemo restoredMemo() {
        return memo.restore();
    }

    void write(DataOutputStream output) throws IOException {
        output.writeInt(schema);
        writeString(output, structureKey, MAX_KEY_BYTES, "structure key");
        output.writeInt(originChunkX);
        output.writeInt(originChunkZ);
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
        memo.write(output);
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
                    MemoSnapshot.read(input)
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

    /** The start's memo as the record stores it: the module's text, empty for a start without a memo. */
    public record MemoSnapshot(String text) {
        private static final int MAX_MEMO_BYTES = 131_072;
        private static final MemoSnapshot NONE = new MemoSnapshot("");

        public MemoSnapshot {
            text = Objects.requireNonNull(text, "Native structure memo must not be null");
            requireUtf8Limit(text, MAX_MEMO_BYTES, "memo");
        }

        public static MemoSnapshot of(NativeStartMemo memo) {
            return memo == null ? NONE : new MemoSnapshot(memo.text());
        }

        public NativeStartMemo restore() {
            return equals(NONE) ? null : new NativeStartMemo(text);
        }

        void write(DataOutputStream output) throws IOException {
            writeString(output, text, MAX_MEMO_BYTES, "memo");
        }

        static MemoSnapshot read(DataInputStream input) throws IOException {
            return new MemoSnapshot(readString(input, MAX_MEMO_BYTES, "memo"));
        }
    }
}
