package art.arcane.iris.engine.history;

import art.arcane.iris.testsupport.DurabilityMode;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Stream;

public abstract class GenerationHistorySupport {
    @ClassRule
    public static final DurabilityMode DURABILITY = DurabilityMode.relaxed();

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    static GenerationKernelRegistry singleKernel(GenerationKernelRegistry.Version version, String fingerprint) {
        return new GenerationKernelRegistry(version, List.of(new GenerationKernelRegistry.Kernel(
                version.generatorAbi(), fingerprint.repeat(64),
                Map.of(new GenerationKernelRegistry.AlgorithmVersion(version.rngVersion(), version.seedDerivationVersion()),
                        (engine, transition, detached) -> {
                            throw new AssertionError("Saved terrain capture invoked a generator factory");
                        }))));
    }
    void initializeRawHistory(
            Path world,
            Path pack,
            int generatorAbi,
            int rngVersion
    ) throws IOException {
        String fingerprint = fingerprint(pack);
        GenerationEpoch epoch = GenerationEpoch.create(new GenerationEpoch.Spec(
                fingerprint,
                GenerationPackFingerprint.CURRENT_VERSION,
                42L,
                GenerationEpoch.CURRENT_SEED_DERIVATION_VERSION,
                generatorAbi,
                rngVersion,
                GenerationKernelV1.IMPLEMENTATION_FINGERPRINT,
                contract(),
                GenerationRegistryContract.empty()
        ));
        GenerationPackRepository repository = new GenerationPackRepository(world);
        repository.publish(
                epoch.epochId(),
                fingerprint,
                GenerationPackFingerprint.CURRENT_VERSION,
                pack
        );
        GenerationSemanticIndex.initialize(world);
        GenerationHistoryStore.initialize(repository.generationRoot(), epoch);
    }
    Path createPack(String name, String content) throws IOException {
        Path pack = temporaryFolder.newFolder(name).toPath();
        Files.createDirectories(pack.resolve("dimensions"));
        Files.writeString(pack.resolve("dimensions/main.json"), content);
        return pack;
    }
    static String fingerprint(Path pack) throws IOException {
        return GenerationPackFingerprint.compute(pack, GenerationPackFingerprint.CURRENT_VERSION);
    }
    static GenerationEpoch.DimensionContract upperContract(String packFingerprint) {
        GenerationEpoch.DimensionContract base = contract();
        return new GenerationEpoch.DimensionContract(
                base.dimensionKey(), base.dimensionTypeKey(), base.environment(), base.generationMode(),
                base.internalFluidHeight(), base.minHeight(), base.height(), base.logicalHeight(),
                base.coordinateScale(), true, "ceiling", 32, packFingerprint,
                base.dimensionTypeFingerprintSchema(), base.dimensionTypeFingerprint());
    }
    static GenerationEpoch.DimensionContract contract() {
        return new GenerationEpoch.DimensionContract(
                "overworld",
                "iris:overworld_type",
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
    static GenerationHistory createHistory(Path world, Path pack) throws IOException {
        return GenerationHistory.create(
                world,
                pack,
                fingerprint(pack),
                42L,
                contract(),
                GenerationRegistryContract.empty()
        );
    }
    static GenerationActivation stage(GenerationHistory history, Path pack) throws IOException {
        return history.stageUpdate(
                pack,
                fingerprint(pack),
                contract(),
                GenerationRegistryContract.empty(),
                256
        );
    }
    static List<TerrainBoundarySignature> signaturesForChunks(int[][] chunks) {
        ArrayList<GenerationBoundary.ChunkCoordinate> coordinates = new ArrayList<>(chunks.length);
        for (int[] chunk : chunks) {
            coordinates.add(new GenerationBoundary.ChunkCoordinate(chunk[0], chunk[1]));
        }
        GenerationBoundary boundary = GenerationBoundary.freeze("test-boundary", coordinates);
        ArrayList<TerrainBoundarySignature> signatures = new ArrayList<>(boundary.exposedBlockColumns().size());
        for (GenerationBoundary.BlockColumn column : boundary.exposedBlockColumns()) {
            signatures.add(signature(column.blockX(), column.blockZ()));
        }
        return List.copyOf(signatures);
    }
    static TerrainBoundarySignature signature(int blockX, int blockZ) {
        return new TerrainBoundarySignature(
                new TerrainBoundarySignature.Column(blockX, blockZ, 64, 63, OptionalInt.empty(), OptionalInt.empty()),
                new TerrainBoundarySignature.Samples(
                        new TerrainBoundarySignature.VerticalLayout(-64, 64, 2),
                        new TerrainBoundarySignature.BiomeEncoding(
                                List.of("iris:test"),
                                new short[]{0, 0}
                        )
                )
        , BoundaryColumnGeometry.empty());
    }
    static void writeRegion(Path file, int[][] chunks) throws IOException {
        SavedTerrainTestRegion.write(file, chunks);
    }
}
