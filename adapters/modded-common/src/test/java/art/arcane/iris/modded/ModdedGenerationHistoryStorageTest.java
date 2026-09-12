package art.arcane.iris.modded;

import art.arcane.iris.world.history.GenerationEpoch;
import art.arcane.iris.world.history.GenerationEpochContractFactory;
import art.arcane.iris.world.history.GenerationHistory;
import art.arcane.iris.world.history.GenerationPackFingerprint;
import art.arcane.iris.world.history.GenerationRegistryContract;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ModdedGenerationHistoryStorageTest {
    private static final String DIMENSION_FINGERPRINT = "11".repeat(32);
    private static final String DEFINITION_FINGERPRINT = "22".repeat(32);
    private static final int TRANSITION_WIDTH_BLOCKS = 256;

    @Test
    public void newDimensionPublishesAnImmutableWorldLocalPack() throws IOException {
        Path root = Files.createTempDirectory("iris-modded-history-create");
        try {
            Path source = pack(root.resolve("global-pack"), "first");
            ModdedGenerationHistoryStorage.Candidate candidate = candidate(source, registryContract());
            GenerationHistory history = ModdedGenerationHistoryStorage.createOrStage(
                    root.resolve("world-dimension"),
                    source,
                    41L,
                    candidate,
                    ModdedGenerationHistoryStorageTest::union,
                    TRANSITION_WIDTH_BLOCKS
            );

            Path activePack = history.activePackRoot();
            assertEquals("first", Files.readString(activePack.resolve("content.txt"), StandardCharsets.UTF_8));
            Files.writeString(source.resolve("content.txt"), "mutated", StandardCharsets.UTF_8);
            assertEquals("first", Files.readString(activePack.resolve("content.txt"), StandardCharsets.UTF_8));
            assertEquals(candidate.packFingerprint(), history.activeEpoch().packFingerprint());
        } finally {
            deleteTree(root);
        }
    }

    @Test
    public void existingDimensionAdoptsItsSelectedPackOnlyOnce() throws IOException {
        Path root = Files.createTempDirectory("iris-modded-history-adopt");
        try {
            Path source = pack(root.resolve("global-pack"), "adopted");
            Path dimensionRoot = root.resolve("world-dimension");
            AtomicInteger sourceReads = new AtomicInteger();
            ModdedGenerationHistoryStorage.AdoptionSourceSupplier supplier = () -> {
                sourceReads.incrementAndGet();
                return new ModdedGenerationHistoryStorage.AdoptionSource(
                        source,
                        candidate(source, registryContract())
                );
            };

            GenerationHistory adopted = ModdedGenerationHistoryStorage.restoreOrAdopt(
                    dimensionRoot,
                    91L,
                    supplier,
                    ModdedGenerationHistoryStorageTest::union
            );
            String epochId = adopted.activeEpoch().epochId();
            Files.writeString(source.resolve("content.txt"), "changed", StandardCharsets.UTF_8);
            GenerationHistory restored = ModdedGenerationHistoryStorage.restoreOrAdopt(
                    dimensionRoot,
                    91L,
                    supplier,
                    ModdedGenerationHistoryStorageTest::union
            );

            assertEquals(1, sourceReads.get());
            assertEquals(epochId, restored.activeEpoch().epochId());
            assertEquals("adopted", Files.readString(
                    restored.activePackRoot().resolve("content.txt"),
                    StandardCharsets.UTF_8
            ));
        } finally {
            deleteTree(root);
        }
    }

    @Test
    public void selectedUpdateStagesWithoutReplacingTheActivePack() throws IOException {
        Path root = Files.createTempDirectory("iris-modded-history-stage");
        try {
            Path dimensionRoot = root.resolve("world-dimension");
            Path first = pack(root.resolve("first-pack"), "first");
            GenerationHistory created = ModdedGenerationHistoryStorage.createOrStage(
                    dimensionRoot,
                    first,
                    5L,
                    candidate(first, registryContract()),
                    ModdedGenerationHistoryStorageTest::union,
                    TRANSITION_WIDTH_BLOCKS
            );
            String activeEpoch = created.activeEpoch().epochId();
            Path second = pack(root.resolve("second-pack"), "second");
            GenerationHistory staged = ModdedGenerationHistoryStorage.createOrStage(
                    dimensionRoot,
                    second,
                    5L,
                    candidate(second, registryContract()),
                    ModdedGenerationHistoryStorageTest::union,
                    TRANSITION_WIDTH_BLOCKS
            );

            assertEquals(activeEpoch, staged.activeEpoch().epochId());
            assertTrue(staged.pendingActivation().isPresent());
            assertNotEquals(activeEpoch, staged.pendingActivation().orElseThrow().epochId());
            assertEquals("first", Files.readString(
                    staged.activePackRoot().resolve("content.txt"),
                    StandardCharsets.UTF_8
            ));
            assertEquals("second", Files.readString(
                    staged.packRoot(staged.pendingActivation().orElseThrow().activationId()).resolve("content.txt"),
                    StandardCharsets.UTF_8
            ));
        } finally {
            deleteTree(root);
        }
    }

    @Test
    public void unavailableUpdateRegistryDoesNotCreatePendingActivation() throws IOException {
        Path root = Files.createTempDirectory("iris-modded-history-stage-registry");
        try {
            Path dimensionRoot = root.resolve("world-dimension");
            Path first = pack(root.resolve("first-pack"), "first");
            GenerationHistory history = ModdedGenerationHistoryStorage.createOrStage(
                    dimensionRoot,
                    first,
                    63L,
                    candidate(first, registryContract()),
                    ModdedGenerationHistoryStorageTest::union,
                    TRANSITION_WIDTH_BLOCKS
            );
            Path second = pack(root.resolve("second-pack"), "second");

            assertThrows(IOException.class, () -> ModdedGenerationHistoryStorage.createOrStage(
                    dimensionRoot,
                    second,
                    63L,
                    candidate(second, registryContract()),
                    contracts -> GenerationRegistryContract.empty(),
                    TRANSITION_WIDTH_BLOCKS
            ));

            assertTrue(history.pendingActivation().isEmpty());
            assertEquals("first", Files.readString(
                    history.activePackRoot().resolve("content.txt"),
                    StandardCharsets.UTF_8
            ));
        } finally {
            deleteTree(root);
        }
    }

    @Test
    public void updateWithANewCustomBiomeKeyStagesBeforeThatKeyIsRegistered() throws IOException {
        Path root = Files.createTempDirectory("iris-modded-history-new-biome");
        try {
            Path dimensionRoot = root.resolve("world-dimension");
            Path first = pack(root.resolve("first-pack"), "first");
            GenerationRegistryContract activeContract = registryContract("minecraft:plains");
            ModdedGenerationHistoryStorage.createOrStage(
                    dimensionRoot,
                    first,
                    71L,
                    candidate(first, activeContract),
                    ModdedGenerationHistoryStorageTest::union,
                    TRANSITION_WIDTH_BLOCKS
            );
            Path second = pack(root.resolve("second-pack"), "second");
            GenerationRegistryContract candidateContract = registryContract(
                    "iris:biomes/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
            );
            AtomicInteger validationCalls = new AtomicInteger();

            GenerationHistory staged = ModdedGenerationHistoryStorage.createOrStage(
                    dimensionRoot,
                    second,
                    71L,
                    candidate(second, candidateContract),
                    contracts -> {
                        validationCalls.incrementAndGet();
                        assertEquals(1, contracts.size());
                        assertEquals(activeContract, contracts.iterator().next());
                        return union(contracts);
                    },
                    TRANSITION_WIDTH_BLOCKS
            );

            assertEquals(1, validationCalls.get());
            assertTrue(staged.pendingActivation().isPresent());
            assertEquals(
                    candidateContract,
                    staged.manifest()
                            .epoch(staged.pendingActivation().orElseThrow().epochId())
                            .orElseThrow()
                            .registryContract()
            );
        } finally {
            deleteTree(root);
        }
    }

    @Test
    public void restoreRejectsASeedMismatchBeforeConsultingTheGlobalPack() throws IOException {
        Path root = Files.createTempDirectory("iris-modded-history-seed");
        try {
            Path source = pack(root.resolve("global-pack"), "seeded");
            Path dimensionRoot = root.resolve("world-dimension");
            ModdedGenerationHistoryStorage.createOrStage(
                    dimensionRoot,
                    source,
                    123L,
                    candidate(source, registryContract()),
                    ModdedGenerationHistoryStorageTest::union,
                    TRANSITION_WIDTH_BLOCKS
            );
            AtomicInteger sourceReads = new AtomicInteger();

            assertThrows(IOException.class, () -> ModdedGenerationHistoryStorage.restoreOrAdopt(
                    dimensionRoot,
                    124L,
                    () -> {
                        sourceReads.incrementAndGet();
                        return new ModdedGenerationHistoryStorage.AdoptionSource(
                                source,
                                candidate(source, registryContract())
                        );
                    },
                    ModdedGenerationHistoryStorageTest::union
            ));
            assertEquals(0, sourceReads.get());
        } finally {
            deleteTree(root);
        }
    }

    @Test
    public void restoreRejectsUnavailableRetainedRegistryDefinitions() throws IOException {
        Path root = Files.createTempDirectory("iris-modded-history-registry");
        try {
            Path source = pack(root.resolve("global-pack"), "registry");
            Path dimensionRoot = root.resolve("world-dimension");
            ModdedGenerationHistoryStorage.createOrStage(
                    dimensionRoot,
                    source,
                    12L,
                    candidate(source, registryContract()),
                    ModdedGenerationHistoryStorageTest::union,
                    TRANSITION_WIDTH_BLOCKS
            );

            assertThrows(IOException.class, () -> ModdedGenerationHistoryStorage.restoreOrAdopt(
                    dimensionRoot,
                    12L,
                    () -> {
                        throw new AssertionError("Existing history consulted the global pack");
                    },
                    contracts -> GenerationRegistryContract.empty()
            ));
        } finally {
            deleteTree(root);
        }
    }

    private static ModdedGenerationHistoryStorage.Candidate candidate(
            Path source,
            GenerationRegistryContract registryContract
    ) throws IOException {
        return new ModdedGenerationHistoryStorage.Candidate(
                GenerationPackFingerprint.compute(source, GenerationPackFingerprint.CURRENT_VERSION),
                dimensionContract(),
                registryContract
        );
    }

    private static GenerationEpoch.DimensionContract dimensionContract() {
        return new GenerationEpoch.DimensionContract(
                "surface",
                "irisworldgen:packs/7061636b/dimensions/73757266616365/dimension_type",
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
                DIMENSION_FINGERPRINT
        );
    }

    private static GenerationRegistryContract registryContract() {
        return registryContract("minecraft:stone");
    }

    private static GenerationRegistryContract registryContract(String resourceKey) {
        return GenerationRegistryContract.fromDefinitions(Map.of(
                new GenerationRegistryContract.PhysicalResourceKey(
                        resourceKey.startsWith("iris:biomes/")
                                ? "minecraft:worldgen/biome"
                                : "minecraft:block",
                        resourceKey
                ),
                DEFINITION_FINGERPRINT
        ));
    }

    private static GenerationRegistryContract union(Collection<GenerationRegistryContract> contracts)
            throws IOException {
        TreeMap<GenerationRegistryContract.PhysicalResourceKey, String> definitions = new TreeMap<>();
        for (GenerationRegistryContract contract : contracts) {
            for (Map.Entry<GenerationRegistryContract.PhysicalResourceKey, String> entry
                    : contract.definitions().entrySet()) {
                String previous = definitions.putIfAbsent(entry.getKey(), entry.getValue());
                if (previous != null && !previous.equals(entry.getValue())) {
                    throw new IOException("Conflicting registry definition");
                }
            }
        }
        return GenerationRegistryContract.fromDefinitions(definitions);
    }

    private static Path pack(Path root, String content) throws IOException {
        Files.createDirectories(root);
        Files.writeString(root.resolve("content.txt"), content, StandardCharsets.UTF_8);
        return root;
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
