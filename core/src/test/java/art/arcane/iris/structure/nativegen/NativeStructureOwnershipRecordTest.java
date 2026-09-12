package art.arcane.iris.structure.nativegen;

import art.arcane.iris.structure.placement.IrisStructureCarveShape;
import art.arcane.iris.structure.placement.IrisStructureStiltSettings;
import art.arcane.iris.structure.placement.IrisStructureTerrain;
import art.arcane.iris.structure.placement.IrisStructureTerrainMode;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class NativeStructureOwnershipRecordTest {
    private static final String FINGERPRINT = "12".repeat(32);
    private static final int SCHEMA_OFFSET = 8;

    @Test
    public void binaryRoundTripPreservesMultipleVersionedOwnershipRecords() throws Exception {
        NativeStructureOwnershipRecord tavern = record("nova_structures:tavern_oak", 4, -7, 71L);
        NativeStructureOwnershipRecord crypt = record("nova_structures:undead_crypt", 4, -7, 72L);
        NativeStructureOwnershipBundle bundle = NativeStructureOwnershipBundle.empty()
                .with(tavern)
                .with(crypt);
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        bundle.write(new DataOutputStream(encoded));
        NativeStructureOwnershipBundle restored = NativeStructureOwnershipBundle.read(
                new DataInputStream(new ByteArrayInputStream(encoded.toByteArray())));

        assertNotNull(restored);
        assertEquals(tavern, restored.find("NOVA_STRUCTURES:TAVERN_OAK", 4, -7));
        assertEquals(crypt, restored.find("nova_structures:undead_crypt", 4, -7));
    }

    @Test
    public void decisionSnapshotFreezesTerrainAndStiltSettings() {
        IrisStructureTerrain terrain = new IrisStructureTerrain()
                .setMode(IrisStructureTerrainMode.FORCE_CARVE)
                .setHorizontalPadding(9)
                .setCeilingPadding(3)
                .setFloorPadding(2)
                .setShape(IrisStructureCarveShape.ERODED)
                .setErosionStrength(0.42D)
                .setErosionFrequency(0.17D)
                .setLobeFrequency(0.03D)
                .setLobeStrength(0.61D);
        IrisStructureStiltSettings stilt = new IrisStructureStiltSettings()
                .setMaxDepth(91)
                .setSpacing(4)
                .setSupportNonOccluding(true);
        IrisNativeStructureDecision source = new IrisNativeStructureDecision(
                NativeStructureGenerationStatus.GENERATE_NATIVE,
                0,
                null,
                false,
                stilt,
                terrain
        );

        NativeStructureOwnershipRecord.DecisionSnapshot snapshot =
                NativeStructureOwnershipRecord.DecisionSnapshot.capture(source);
        terrain.setHorizontalPadding(1);
        stilt.setMaxDepth(2);
        IrisNativeStructureDecision restored = snapshot.restore();

        assertTrue(restored.generate());
        assertEquals(IrisStructureTerrainMode.FORCE_CARVE, restored.terrain().resolvedMode());
        assertEquals(IrisStructureCarveShape.ERODED, restored.terrain().resolvedShape());
        assertEquals(9, restored.terrain().getHorizontalPadding());
        assertEquals(0.42D, restored.terrain().getErosionStrength(), 0D);
        assertNotNull(restored.stilt());
        assertEquals(91, restored.stilt().getMaxDepth());
        assertEquals(4, restored.stilt().getSpacing());
        assertTrue(restored.stilt().isSupportNonOccluding());
    }

    @Test
    public void recordCarriesLocateAndReferenceEnvelopeState() {
        NativeStructureOwnershipRecord record = record("nova_structures:tavern_oak", -3, 9, 83L);

        assertEquals(47, record.baseY());
        assertEquals(43, record.contentMinY());
        assertEquals(78, record.contentMaxY());
        assertEquals(58, record.locatorY());
        assertTrue(record.covers(-5, 11));
        assertFalse(record.covers(-6, 11));
        assertEquals(83L, record.placementIdentity());
    }

    @Test
    public void referenceEnvelopeRefreshPreservesPersistedAuthority() {
        NativeStructureOwnershipRecord original = record(
                "nova_structures:tavern_oak", -3, 9, 83L);

        NativeStructureOwnershipRecord refreshed = original.withReferenceEnvelope(
                -6, 0, 6, 12);

        assertEquals(original.schema(), refreshed.schema());
        assertEquals(original.ownershipKey(), refreshed.ownershipKey());
        assertEquals(original.placementIdentity(), refreshed.placementIdentity());
        assertEquals(original.baseY(), refreshed.baseY());
        assertEquals(original.contentMinX(), refreshed.contentMinX());
        assertEquals(original.contentMinY(), refreshed.contentMinY());
        assertEquals(original.contentMinZ(), refreshed.contentMinZ());
        assertEquals(original.contentMaxX(), refreshed.contentMaxX());
        assertEquals(original.contentMaxY(), refreshed.contentMaxY());
        assertEquals(original.contentMaxZ(), refreshed.contentMaxZ());
        assertEquals(original.locatorY(), refreshed.locatorY());
        assertEquals(original.contentFingerprint(), refreshed.contentFingerprint());
        assertEquals(original.decision(), refreshed.decision());
        assertEquals(-6, refreshed.referenceMinChunkX());
        assertEquals(0, refreshed.referenceMaxChunkX());
        assertEquals(6, refreshed.referenceMinChunkZ());
        assertEquals(12, refreshed.referenceMaxChunkZ());
    }

    @Test
    public void ownershipRoundTripPreservesExactBoundsAndPriority() throws Exception {
        NativeStructureOwnershipRecord ownership = record(
                "nova_structures:tavern_oak", -3, 9, 83L);
        NativeStructureOwnershipBundle bundle = NativeStructureOwnershipBundle.empty().with(ownership);
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        bundle.write(new DataOutputStream(encoded));

        NativeStructureOwnershipRecord restored = NativeStructureOwnershipBundle.read(
                new DataInputStream(new ByteArrayInputStream(encoded.toByteArray())))
                .find(ownership.structureKey(), ownership.originChunkX(), ownership.originChunkZ());

        assertNotNull(restored);
        assertEquals(ownership.placementIdentity(), restored.placementIdentity());
        assertEquals(ownership.contentMinX(), restored.contentMinX());
        assertEquals(ownership.contentMinY(), restored.contentMinY());
        assertEquals(ownership.contentMinZ(), restored.contentMinZ());
        assertEquals(ownership.contentMaxX(), restored.contentMaxX());
        assertEquals(ownership.contentMaxY(), restored.contentMaxY());
        assertEquals(ownership.contentMaxZ(), restored.contentMaxZ());
    }

    @Test
    public void invalidOrUnboundedPayloadsFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> new NativeStructureOwnershipRecord(
                NativeStructureOwnershipRecord.CURRENT_SCHEMA,
                "nova_structures:tavern_oak",
                0,
                0,
                1L,
                47,
                0,
                43,
                0,
                15,
                78,
                15,
                58,
                -9,
                0,
                0,
                0,
                FINGERPRINT,
                decision()
        ));

        Map<NativeStructureOwnershipRecord.OwnershipKey, NativeStructureOwnershipRecord> records =
                new LinkedHashMap<>();
        for (int i = 0; i <= NativeStructureOwnershipBundle.MAX_RECORDS; i++) {
            NativeStructureOwnershipRecord record = record("test:structure_" + i, 0, 0, i);
            records.put(record.ownershipKey(), record);
        }
        assertThrows(IllegalArgumentException.class,
                () -> new NativeStructureOwnershipBundle(records));

        Map<NativeStructureOwnershipRecord.OwnershipKey, NativeStructureOwnershipRecord> oversized =
                new LinkedHashMap<>();
        NativeStructureOwnershipRecord.DecisionSnapshot largeDecision =
                new NativeStructureOwnershipRecord.DecisionSnapshot(
                        "null",
                        "{\"unused\":\"" + "x".repeat(65_000) + "\"}"
                );
        for (int i = 0; i < 33; i++) {
            NativeStructureOwnershipRecord record = new NativeStructureOwnershipRecord(
                    NativeStructureOwnershipRecord.CURRENT_SCHEMA,
                    "test:large_" + i,
                    0,
                    0,
                    i,
                    47,
                    0,
                    43,
                    0,
                    15,
                    78,
                    15,
                    58,
                    -1,
                    1,
                    -1,
                    1,
                    FINGERPRINT,
                    largeDecision
            );
            oversized.put(record.ownershipKey(), record);
        }
        assertThrows(IllegalArgumentException.class,
                () -> new NativeStructureOwnershipBundle(oversized));
    }

    @Test
    public void referenceDistanceChecksCannotBeBypassedByIntegerOverflow() {
        assertThrows(IllegalArgumentException.class, () -> new NativeStructureOwnershipRecord(
                NativeStructureOwnershipRecord.CURRENT_SCHEMA,
                "test:overflow",
                Integer.MAX_VALUE,
                0,
                1L,
                47,
                0,
                43,
                0,
                15,
                78,
                15,
                58,
                Integer.MIN_VALUE,
                Integer.MAX_VALUE,
                0,
                0,
                FINGERPRINT,
                decision()
        ));
    }

    @Test
    public void contentBoundsMustRemainInsideThePersistedReferenceEnvelope() {
        assertThrows(IllegalArgumentException.class, () -> new NativeStructureOwnershipRecord(
                NativeStructureOwnershipRecord.CURRENT_SCHEMA,
                "test:outside_envelope",
                0,
                0,
                1L,
                47,
                -16,
                43,
                -16,
                32,
                78,
                31,
                58,
                -1,
                1,
                -1,
                1,
                FINGERPRINT,
                decision()
        ));
    }

    @Test
    public void displacedReferenceEnvelopeMayExcludeTheStartChunk() {
        NativeStructureOwnershipRecord record = new NativeStructureOwnershipRecord(
                NativeStructureOwnershipRecord.CURRENT_SCHEMA,
                "test:displaced",
                0,
                0,
                1L,
                47,
                80,
                43,
                0,
                95,
                78,
                15,
                58,
                5,
                5,
                0,
                0,
                FINGERPRINT,
                decision()
        );

        assertFalse(record.covers(0, 0));
        assertTrue(record.covers(5, 0));
    }

    @Test
    public void nullableStiltRoundTripsAsNull() {
        NativeStructureOwnershipRecord record = record("test:no_stilt", 0, 0, 1L);
        assertNull(record.restoredDecision().stilt());
    }

    @Test
    public void bundlesWrittenUnderAnOlderSchemaRevisionFailClosed() throws Exception {
        NativeStructureOwnershipRecord record = record("nova_structures:tavern_oak", 4, -7, 71L);
        NativeStructureOwnershipBundle bundle = NativeStructureOwnershipBundle.empty().with(record);
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        bundle.write(new DataOutputStream(encoded));
        byte[] payload = encoded.toByteArray();
        ByteBuffer.wrap(payload).putInt(SCHEMA_OFFSET, NativeStructureOwnershipRecord.CURRENT_SCHEMA - 1);

        IOException failure = assertThrows(IOException.class, () -> NativeStructureOwnershipBundle.read(
                new DataInputStream(new ByteArrayInputStream(payload))));

        assertTrue(failure.getMessage(), failure.getMessage().contains("schema"));
    }

    @Test
    public void malformedButSizedBinaryRecordsFailAsIoErrors() throws Exception {
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(encoded);
        output.writeInt(NativeStructureOwnershipRecord.CURRENT_SCHEMA);
        NativeStructureOwnershipRecord.writeString(output, " ", 512, "structure key");
        output.writeInt(0);
        output.writeInt(0);
        output.writeLong(1L);
        output.writeInt(47);
        output.writeInt(0);
        output.writeInt(43);
        output.writeInt(0);
        output.writeInt(15);
        output.writeInt(78);
        output.writeInt(15);
        output.writeInt(58);
        output.writeInt(-1);
        output.writeInt(1);
        output.writeInt(-1);
        output.writeInt(1);
        NativeStructureOwnershipRecord.writeString(output, FINGERPRINT, 128, "content fingerprint");
        decision().write(output);

        assertThrows(IOException.class, () -> NativeStructureOwnershipRecord.read(
                new DataInputStream(new ByteArrayInputStream(encoded.toByteArray()))));
    }

    private static NativeStructureOwnershipRecord record(String key, int originX, int originZ,
                                                          long placementIdentity) {
        return new NativeStructureOwnershipRecord(
                NativeStructureOwnershipRecord.CURRENT_SCHEMA,
                key,
                originX,
                originZ,
                placementIdentity,
                47,
                originX << 4,
                43,
                originZ << 4,
                (originX << 4) + 31,
                78,
                (originZ << 4) + 31,
                58,
                originX - 2,
                originX + 2,
                originZ - 2,
                originZ + 2,
                FINGERPRINT,
                decision()
        );
    }

    private static NativeStructureOwnershipRecord.DecisionSnapshot decision() {
        return NativeStructureOwnershipRecord.DecisionSnapshot.capture(
                new IrisNativeStructureDecision(
                        NativeStructureGenerationStatus.GENERATE_NATIVE,
                        0,
                        null,
                        false,
                        null,
                        new IrisStructureTerrain()
                ));
    }
}
