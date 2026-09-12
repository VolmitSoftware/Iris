package art.arcane.iris.world.history;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class GenerationManifestTest {
    private static final String PACK_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String PACK_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @Test
    public void keepsEpochsSeparateFromMonotonicActivations() {
        GenerationEpoch epochA = epoch(PACK_A, contract("iris:type_a"));
        GenerationEpoch epochB = epoch(PACK_B, contract("iris:type_a"));
        GenerationManifest initial = GenerationManifest.initial(epochA, 10L);
        GenerationManifest pendingB = initial.preparePending(epochB, 20L, 256);
        GenerationActivation activationB = pendingB.pendingActivation().orElseThrow();
        GenerationManifest activeB = complete(pendingB, activationB.activationId()).activatePending(
                activationB.activationId()
        );
        GenerationManifest pendingA = activeB.preparePending(epochA, 30L, 256);
        GenerationActivation activationA2 = pendingA.pendingActivation().orElseThrow();

        assertEquals(3L, activationA2.activationId());
        assertEquals(Long.valueOf(2L), activationA2.parentActivationId());
        assertEquals(epochA.epochId(), activationA2.epochId());
        assertEquals(2, pendingA.epochs().size());
        assertEquals(List.of(1L, 2L, 3L), pendingA.activations().stream()
                .map(GenerationActivation::activationId)
                .toList());
        assertEquals(epochB, pendingA.activeEpoch());
    }

    @Test
    public void repeatedPendingPreparationIsIdempotent() {
        GenerationEpoch epochA = epoch(PACK_A, contract("iris:type_a"));
        GenerationEpoch epochB = epoch(PACK_B, contract("iris:type_a"));
        GenerationManifest pending = GenerationManifest.initial(epochA, 10L).preparePending(epochB, 20L, 256);

        GenerationManifest repeated = pending.preparePending(epochB, 99L, 256);

        assertSame(pending, repeated);
        assertEquals(2, repeated.activations().size());
        assertEquals(20L, repeated.pendingActivation().orElseThrow().createdAtEpochMillis());
    }

    @Test
    public void refusesCompetingPendingActivation() {
        GenerationEpoch epochA = epoch(PACK_A, contract("iris:type_a"));
        GenerationEpoch epochB = epoch(PACK_B, contract("iris:type_a"));
        GenerationEpoch epochC = epoch(
                "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
                contract("iris:type_a")
        );
        GenerationManifest pending = GenerationManifest.initial(epochA, 10L).preparePending(epochB, 20L, 256);

        assertThrows(IllegalStateException.class, () -> pending.preparePending(epochC, 30L, 256));
    }

    @Test
    public void refusesImmutableDimensionContractChange() {
        GenerationEpoch epochA = epoch(PACK_A, contract("iris:type_a"));
        GenerationEpoch changedType = epoch(PACK_B, contract("iris:type_b"));
        GenerationManifest manifest = GenerationManifest.initial(epochA, 10L);

        assertThrows(IllegalArgumentException.class, () -> manifest.preparePending(changedType, 20L, 256));
        assertFalse(manifest.pendingActivation().isPresent());
        assertEquals(epochA, manifest.activeEpoch());
    }

    @Test
    public void activatesOnlyTheExpectedPendingRecord() {
        GenerationEpoch epochA = epoch(PACK_A, contract("iris:type_a"));
        GenerationEpoch epochB = epoch(PACK_B, contract("iris:type_a"));
        GenerationManifest pending = GenerationManifest.initial(epochA, 10L).preparePending(epochB, 20L, 256);

        assertThrows(IllegalStateException.class, () -> pending.activatePending(3L));
        assertThrows(IllegalArgumentException.class, () -> pending.activatePending(2L));

        GenerationManifest active = complete(pending, 2L).activatePending(2L);
        assertEquals(2L, active.activeActivation().activationId());
        assertEquals(epochB, active.activeEpoch());
        assertTrue(active.pendingActivation().isEmpty());
    }

    private static GenerationEpoch epoch(
            String packFingerprint,
            GenerationEpoch.DimensionContract contract
    ) {
        return GenerationEpoch.create(new GenerationEpoch.Spec(
                packFingerprint,
                GenerationPackFingerprint.CURRENT_VERSION,
                42L,
                GenerationEpoch.CURRENT_SEED_DERIVATION_VERSION,
                1,
                1,
                GenerationKernelV1.IMPLEMENTATION_FINGERPRINT,
                contract,
                GenerationRegistryContract.empty()
        ));
    }

    private static GenerationEpoch.DimensionContract contract(String typeKey) {
        return new GenerationEpoch.DimensionContract(
                "overworld",
                typeKey,
                "NORMAL",
                "OVERWORLD",
                127,
                -64,
                384,
                384,
                1D,
                false,
                "none",
                0,
                "0".repeat(64),
                GenerationEpochContractFactory.CURRENT_DIMENSION_TYPE_FINGERPRINT_SCHEMA,
                "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
        );
    }

    private static GenerationManifest complete(GenerationManifest manifest, long activationId) {
        return manifest.completePendingTransition(
                activationId,
                "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd",
                "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"
        );
    }
}
