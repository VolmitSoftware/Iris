package art.arcane.iris.structure.nativegen;

import art.arcane.iris.structure.nativegen.NativeStructureOwnershipRecord.OwnershipKey;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record NativeStructureOwnershipBundle(Map<OwnershipKey, NativeStructureOwnershipRecord> records) {
    public static final int MAX_RECORDS = 16_384;
    public static final int MAX_ENCODED_BYTES = 2 * 1024 * 1024;
    private static final Comparator<NativeStructureOwnershipRecord> RECORD_ORDER = Comparator
            .comparing(NativeStructureOwnershipRecord::structureKey)
            .thenComparingInt(NativeStructureOwnershipRecord::originChunkX)
            .thenComparingInt(NativeStructureOwnershipRecord::originChunkZ);

    public NativeStructureOwnershipBundle {
        records = Map.copyOf(Objects.requireNonNull(records,
                "Native structure ownership records must not be null"));
        if (records.size() > MAX_RECORDS) {
            throw new IllegalArgumentException("Native structure ownership bundle exceeds "
                    + MAX_RECORDS + " records");
        }
        for (Map.Entry<OwnershipKey, NativeStructureOwnershipRecord> entry : records.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().ownershipKey())) {
                throw new IllegalArgumentException("Native structure ownership key does not match its record");
            }
        }
        try {
            encodePayload(records);
        } catch (IOException error) {
            throw new IllegalArgumentException("Native structure ownership bundle is not encodable", error);
        }
    }

    public static NativeStructureOwnershipBundle empty() {
        return new NativeStructureOwnershipBundle(Map.of());
    }

    public NativeStructureOwnershipRecord find(String structureKey, int originChunkX, int originChunkZ) {
        return records.get(new OwnershipKey(structureKey, originChunkX, originChunkZ));
    }

    public NativeStructureOwnershipBundle with(NativeStructureOwnershipRecord record) {
        NativeStructureOwnershipRecord resolved = Objects.requireNonNull(
                record, "Native structure ownership record must not be null");
        Map<OwnershipKey, NativeStructureOwnershipRecord> updated = new LinkedHashMap<>(records);
        updated.put(resolved.ownershipKey(), resolved);
        return new NativeStructureOwnershipBundle(updated);
    }

    public NativeStructureOwnershipBundle without(String structureKey, int originChunkX, int originChunkZ) {
        Map<OwnershipKey, NativeStructureOwnershipRecord> updated = new LinkedHashMap<>(records);
        updated.remove(new OwnershipKey(structureKey, originChunkX, originChunkZ));
        return new NativeStructureOwnershipBundle(updated);
    }

    public void write(DataOutputStream output) throws IOException {
        byte[] encoded = encodePayload(records);
        output.writeInt(encoded.length);
        output.write(encoded);
    }

    public static NativeStructureOwnershipBundle read(DataInputStream input) throws IOException {
        int byteCount = input.readInt();
        if (byteCount < 0 || byteCount > MAX_ENCODED_BYTES) {
            throw new IOException("Native structure ownership bundle has invalid length " + byteCount);
        }
        byte[] encoded = input.readNBytes(byteCount);
        if (encoded.length != byteCount) {
            throw new IOException("Native structure ownership bundle ended early");
        }
        try (DataInputStream payload = new DataInputStream(new ByteArrayInputStream(encoded))) {
            int count = payload.readInt();
            if (count < 0 || count > MAX_RECORDS) {
                throw new IOException("Native structure ownership bundle has invalid record count " + count);
            }
            Map<OwnershipKey, NativeStructureOwnershipRecord> records = new LinkedHashMap<>(count);
            for (int i = 0; i < count; i++) {
                NativeStructureOwnershipRecord record = NativeStructureOwnershipRecord.read(payload);
                if (records.put(record.ownershipKey(), record) != null) {
                    throw new IOException("Native structure ownership bundle contains a duplicate record");
                }
            }
            if (payload.available() != 0) {
                throw new IOException("Native structure ownership bundle contains trailing data");
            }
            return new NativeStructureOwnershipBundle(records);
        }
    }

    private static byte[] encodePayload(
            Map<OwnershipKey, NativeStructureOwnershipRecord> records) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream payload = new DataOutputStream(bytes)) {
            payload.writeInt(records.size());
            List<NativeStructureOwnershipRecord> ordered = new ArrayList<>(records.values());
            ordered.sort(RECORD_ORDER);
            for (NativeStructureOwnershipRecord record : ordered) {
                record.write(payload);
            }
        }
        if (bytes.size() > MAX_ENCODED_BYTES) {
            throw new IOException("Native structure ownership bundle exceeds "
                    + MAX_ENCODED_BYTES + " bytes");
        }
        return bytes.toByteArray();
    }
}
