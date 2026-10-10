package art.arcane.iris.engine.framework;

import art.arcane.iris.builtin.structure.StructureCarveShape;
import art.arcane.iris.builtin.structure.StructureTerrainMode;
import art.arcane.iris.engine.framework.structure.NativeStructureFit;
import com.volmit.vanilla.StartMemo;
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

    /** The stilts and terrain fit JSON records written before memos hold, as the old Iris classes wrote them. */
    @Test
    public void recordsWrittenBeforeMemosReadAsTheStructureModulesMemo() throws Exception {
        String stilts = "{\"maxDepth\":91,\"palette\":{\"style\":{\"style\":\"STATIC\",\"cellularFrequency\":0.0,"
                + "\"cellularZoom\":1.0,\"zoom\":1.0,\"multiplier\":1.0,\"exponent\":1.0,\"cacheSize\":0},\"zoom\":5.0,"
                + "\"palette\":[{\"block\":\"minecraft:stone_bricks\",\"debug\":false,\"weight\":2,"
                + "\"data\":{\"waterlogged\":false},\"tileData\":{}}]},\"spacing\":4,\"supportNonOccluding\":true}";
        String terrain = "{\"mode\":\"FORCE_CARVE\",\"horizontalPadding\":9,\"ceilingPadding\":3,\"floorPadding\":2,"
                + "\"shape\":\"ERODED\",\"erosionStrength\":0.42,\"erosionFrequency\":0.17,\"lobeFrequency\":0.03,"
                + "\"lobeStrength\":0.61}";
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        new NativeStructureOwnershipRecord.MemoSnapshot(stilts, terrain).write(new DataOutputStream(encoded));

        NativeStartMemo memo = NativeStructureOwnershipRecord.MemoSnapshot.read(
                new DataInputStream(new ByteArrayInputStream(encoded.toByteArray()))).restore();
        NativeStructureFit fit = NativeStructureFit.of(new StartMemo(memo.terrain(), memo.foundation()));

        assertEquals(StructureTerrainMode.FORCE_CARVE, fit.terrain().resolvedMode());
        assertEquals(StructureCarveShape.ERODED, fit.terrain().resolvedShape());
        assertEquals(9, fit.terrain().horizontalPadding());
        assertEquals(0.42D, fit.terrain().resolvedErosionStrength(), 0D);
        assertEquals(0.61D, fit.terrain().resolvedLobeStrength(), 0D);
        assertEquals(91, fit.stilts().maxDepth());
        assertEquals(4, fit.stilts().spacing());
        assertTrue(fit.stilts().supportNonOccluding());
        assertEquals("minecraft:stone_bricks", fit.stilts().palette().orElseThrow().palette().getFirst().block());
        assertEquals(2, fit.stilts().palette().orElseThrow().palette().getFirst().weight());
    }

    @Test
    public void aStartWithoutAMemoRoundTripsWithoutOne() throws Exception {
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        NativeStructureOwnershipRecord.MemoSnapshot.of(null).write(new DataOutputStream(encoded));

        assertNull(NativeStructureOwnershipRecord.MemoSnapshot.read(
                new DataInputStream(new ByteArrayInputStream(encoded.toByteArray()))).restore());
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
        assertEquals(original.memo(), refreshed.memo());
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
        NativeStructureOwnershipRecord.MemoSnapshot largeDecision =
                new NativeStructureOwnershipRecord.MemoSnapshot(
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
        NativeStartMemo memo = record.restoredMemo();
        assertNull(NativeStructureFit.of(new StartMemo(memo.terrain(), memo.foundation())).stilts());
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

    private static NativeStructureOwnershipRecord.MemoSnapshot decision() {
        StartMemo memo = NativeStructureFit.NONE.memo();
        return NativeStructureOwnershipRecord.MemoSnapshot.of(new NativeStartMemo(memo.terrain(), memo.foundation()));
    }
}
