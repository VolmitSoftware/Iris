package art.arcane.iris.world.history;

import com.google.gson.JsonObject;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

public class GenerationEpochTest {
    private static final String PACK_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String PACK_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @Test
    public void derivesStableIdentityFromEveryGenerationInput() {
        GenerationEpoch.DimensionContract contract = contract("iris:type_a", 1D);
        GenerationRegistryContract registry = registry(PACK_A);
        GenerationEpoch first = epoch(PACK_A, 1, 42L, 1, 1, 1, contract, registry);
        GenerationEpoch same = epoch(PACK_A, 1, 42L, 1, 1, 1, contract, registry);

        assertEquals(first, same);
        assertEquals(first.epochId(), same.epochId());
        assertNotEquals(first.epochId(), epoch(PACK_B, 1, 42L, 1, 1, 1, contract, registry).epochId());
        assertNotEquals(first.epochId(), epoch(PACK_A, 2, 42L, 1, 1, 1, contract, registry).epochId());
        assertNotEquals(first.epochId(), epoch(PACK_A, 1, 43L, 1, 1, 1, contract, registry).epochId());
        assertNotEquals(first.epochId(), epoch(PACK_A, 1, 42L, 2, 1, 1, contract, registry).epochId());
        assertNotEquals(first.epochId(), epoch(PACK_A, 1, 42L, 1, 2, 1, contract, registry).epochId());
        assertNotEquals(first.epochId(), epoch(PACK_A, 1, 42L, 1, 1, 2, contract, registry).epochId());
        GenerationEpoch changedKernelImplementation = GenerationEpoch.create(new GenerationEpoch.Spec(
                PACK_A,
                1,
                42L,
                1,
                1,
                1,
                "d".repeat(64),
                contract,
                registry
        ));
        assertNotEquals(first.epochId(), changedKernelImplementation.epochId());
        assertNotEquals(
                first.epochId(),
                epoch(PACK_A, 1, 42L, 1, 1, 1, contract("iris:type_b", 1D), registry).epochId()
        );
        assertNotEquals(
                first.epochId(),
                epoch(PACK_A, 1, 42L, 1, 1, 1, contract("iris:type_a", 8D), registry).epochId()
        );
        assertNotEquals(
                first.epochId(),
                epoch(PACK_A, 1, 42L, 1, 1, 1, contract, registry(PACK_B)).epochId()
        );
        assertNotEquals(first.epochId(), epoch(
                PACK_A,
                1,
                42L,
                1,
                1,
                1,
                new GenerationEpoch.DimensionContract(
                        "overworld",
                        "iris:type_a",
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
                        GenerationEpochContractFactory.DIMENSION_TYPE_FINGERPRINT_SCHEMA_VERSION_ONE,
                        PACK_B
                ),
                registry
        ).epochId());
    }

    @Test
    public void preservesExactImmutableInputs() {
        GenerationRegistryContract registry = registry(PACK_A);
        GenerationEpoch epoch = epoch(PACK_A, 3, Long.MIN_VALUE, 7, 5, 9, contract("iris:type_a", 1D), registry);

        assertEquals(PACK_A, epoch.packFingerprint());
        assertEquals(3, epoch.packFingerprintVersion());
        assertEquals(Long.MIN_VALUE, epoch.worldSeed());
        assertEquals(7, epoch.seedDerivationVersion());
        assertEquals(5, epoch.generatorAbi());
        assertEquals(9, epoch.rngVersion());
        assertEquals(GenerationKernelV1.IMPLEMENTATION_FINGERPRINT, epoch.kernelImplementationFingerprint());
        assertEquals(registry, epoch.registryContract());
    }

    @Test
    public void roundTripsStrictDeterministicJson() {
        GenerationEpoch epoch = epoch(
                PACK_A,
                1,
                Long.MAX_VALUE,
                1,
                3,
                7,
                contract("iris:type_a", 1D),
                registry(PACK_A)
        );
        JsonObject json = epoch.toJson();

        assertEquals(epoch, GenerationEpoch.fromJson(json.deepCopy()));
        assertEquals(json.toString(), epoch.toJson().toString());

        JsonObject unknownField = json.deepCopy();
        unknownField.addProperty("unexpected", true);
        assertThrows(IllegalArgumentException.class, () -> GenerationEpoch.fromJson(unknownField));

        JsonObject missingSeed = json.deepCopy();
        missingSeed.remove("worldSeed");
        assertThrows(IllegalArgumentException.class, () -> GenerationEpoch.fromJson(missingSeed));

        JsonObject missingKernelFingerprint = json.deepCopy();
        missingKernelFingerprint.remove("kernelImplementationFingerprint");
        assertThrows(IllegalArgumentException.class, () -> GenerationEpoch.fromJson(missingKernelFingerprint));
    }

    @Test
    public void rejectsMalformedGenerationInputs() {
        GenerationEpoch.DimensionContract contract = contract("iris:type_a", 1D);
        GenerationRegistryContract registry = registry(PACK_A);

        assertThrows(
                IllegalArgumentException.class,
                () -> epoch(
                        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                        1,
                        42L,
                        1,
                        1,
                        1,
                        contract,
                        registry
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> epoch(PACK_A, 0, 42L, 1, 1, 1, contract, registry)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> epoch(PACK_A, 1, 42L, 0, 1, 1, contract, registry)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> epoch(PACK_A, 1, 42L, 1, 0, 1, contract, registry)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> epoch(PACK_A, 1, 42L, 1, 1, 0, contract, registry)
        );
        GenerationEpoch valid = epoch(PACK_A, 1, 42L, 1, 1, 1, contract, registry);
        JsonObject malformedKernelFingerprint = valid.toJson();
        malformedKernelFingerprint.addProperty("kernelImplementationFingerprint", "not-a-sha256");
        assertThrows(IllegalArgumentException.class, () -> GenerationEpoch.fromJson(malformedKernelFingerprint));
        assertThrows(
                NullPointerException.class,
                () -> epoch(PACK_A, 1, 42L, 1, 1, 1, contract, null)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new GenerationEpoch.DimensionContract(
                        "overworld",
                        "iris:type_a",
                        "NORMAL",
                        "OVERWORLD",
                        127,
                        -64,
                        0,
                        0,
                        1D,
                        false,
                        "none",
                        0,
                        "0".repeat(64),
                        GenerationEpochContractFactory.DIMENSION_TYPE_FINGERPRINT_SCHEMA_VERSION_ONE,
                        PACK_A
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new GenerationEpoch.DimensionContract(
                        "overworld",
                        "iris:type_a",
                        "NORMAL",
                        "OVERWORLD",
                        127,
                        -64,
                        384,
                        385,
                        1D,
                        false,
                        "none",
                        0,
                        "0".repeat(64),
                        GenerationEpochContractFactory.DIMENSION_TYPE_FINGERPRINT_SCHEMA_VERSION_ONE,
                        PACK_A
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new GenerationEpoch.DimensionContract(
                        "overworld",
                        "iris:type_a",
                        "NORMAL",
                        "OVERWORLD",
                        127,
                        -64,
                        384,
                        384,
                        Double.NaN,
                        false,
                        "none",
                        0,
                        "0".repeat(64),
                        GenerationEpochContractFactory.DIMENSION_TYPE_FINGERPRINT_SCHEMA_VERSION_ONE,
                        PACK_A
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new GenerationEpoch.DimensionContract(
                        "overworld",
                        "iris:type_a",
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
                        99,
                        PACK_A
                )
        );
    }

    @Test
    public void rejectsForgedContentAddress() {
        GenerationEpoch epoch = epoch(
                PACK_A,
                1,
                42L,
                1,
                1,
                1,
                contract("iris:type_a", 1D),
                registry(PACK_A)
        );
        JsonObject json = epoch.toJson();
        json.addProperty("epochId", PACK_B);

        assertThrows(IllegalArgumentException.class, () -> GenerationEpoch.fromJson(json));
    }

    private static GenerationEpoch epoch(
            String packFingerprint,
            int packFingerprintVersion,
            long worldSeed,
            int seedDerivationVersion,
            int generatorAbi,
            int rngVersion,
            GenerationEpoch.DimensionContract contract,
            GenerationRegistryContract registryContract
    ) {
        return GenerationEpoch.create(new GenerationEpoch.Spec(
                packFingerprint,
                packFingerprintVersion,
                worldSeed,
                seedDerivationVersion,
                generatorAbi,
                rngVersion,
                GenerationKernelV1.IMPLEMENTATION_FINGERPRINT,
                contract,
                registryContract
        ));
    }

    private static GenerationEpoch.DimensionContract contract(String typeKey, double coordinateScale) {
        return new GenerationEpoch.DimensionContract(
                "overworld",
                typeKey,
                "NORMAL",
                "OVERWORLD",
                127,
                -64,
                384,
                384,
                coordinateScale,
                false,
                "none",
                0,
                "0".repeat(64),
                GenerationEpochContractFactory.DIMENSION_TYPE_FINGERPRINT_SCHEMA_VERSION_ONE,
                PACK_A
        );
    }

    private static GenerationRegistryContract registry(String definitionSha256) {
        GenerationRegistryContract.PhysicalResourceKey key = new GenerationRegistryContract.PhysicalResourceKey(
                "minecraft:worldgen/biome",
                "minecraft:plains"
        );
        return GenerationRegistryContract.fromDefinitions(Map.of(key, definitionSha256));
    }
}
