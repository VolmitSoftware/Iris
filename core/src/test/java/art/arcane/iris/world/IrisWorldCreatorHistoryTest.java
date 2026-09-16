package art.arcane.iris.world;

import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.platform.generation.BukkitChunkGenerator;
import art.arcane.iris.studio.StudioSVC;
import art.arcane.iris.world.history.GenerationEpoch;
import art.arcane.iris.world.history.GenerationEpochContractFactory;
import art.arcane.iris.world.history.GenerationHistory;
import art.arcane.iris.world.history.GenerationPackFingerprint;
import art.arcane.iris.world.history.GenerationRegistryContract;
import org.bukkit.NamespacedKey;
import org.bukkit.WorldCreator;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class IrisWorldCreatorHistoryTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void normalCreationHandsThePublishedHistoryToItsGeneratorWithoutReopening() throws Exception {
        assertGeneratorHistory(false, true);
    }

    @Test
    public void studioCreationHandsThePublishedHistoryToItsGeneratorWithoutReopening() throws Exception {
        assertGeneratorHistory(true, true);
    }

    @Test
    public void creationWithoutAHandoffOpensAndValidatesThePackOnce() throws Exception {
        assertGeneratorHistory(false, false);
    }

    @Test
    public void suppliedHistoryMustMatchTheDimensionRootAndSeed() throws Exception {
        GenerationHistory history = createHistory();
        IrisWorldCreator creator = new IrisWorldCreator().persistent(true).seed(42L).generationHistory(history);

        assertThrows(IllegalArgumentException.class, () -> creator.resolveGenerationHistory(
                temporaryFolder.newFolder("different-world")));
        assertThrows(IllegalStateException.class, () -> creator.seed(43L).resolveGenerationHistory(
                history.paths().dimensionRoot().toFile()));
    }

    @Test
    public void transientWorldsDoNotAcquireGenerationHistory() throws Exception {
        GenerationHistory history = createHistory();
        IrisWorldCreator creator = new IrisWorldCreator();

        assertNull(creator.resolveGenerationHistory(history.paths().dimensionRoot().toFile()));
        assertThrows(IllegalStateException.class, () -> creator.generationHistory(history)
                .resolveGenerationHistory(history.paths().dimensionRoot().toFile()));
    }

    @Test
    public void finalGeneratorPreparationRejectsTamperingAfterTheHandoff() throws Exception {
        GenerationHistory history = createHistory();
        Path pack = history.paths().packRoot(history.activeEpoch().epochId());
        IrisWorldCreator creator = new IrisWorldCreator().persistent(true).seed(42L).generationHistory(history);
        GenerationHistory resolved = creator.resolveGenerationHistory(history.paths().dimensionRoot().toFile());
        Files.writeString(pack.resolve("dimensions/overworld.json"), "changed after publication");

        assertSame(history, resolved);
        assertThrows(IOException.class, () -> resolved.prepareCurrentGenerator(128));
        assertThrows(IllegalStateException.class, () -> new IrisWorldCreator().persistent(true).seed(42L)
                .resolveGenerationHistory(history.paths().dimensionRoot().toFile()));
    }

    private void assertGeneratorHistory(boolean studio, boolean handoff) throws Exception {
        GenerationHistory history = createHistory();
        File root = history.paths().dimensionRoot().toFile();
        File pack = history.paths().packRoot(history.activeEpoch().epochId()).toFile();
        File authoring = temporaryFolder.newFolder("authoring");
        IrisDimension dimension = mock(IrisDimension.class);
        when(dimension.getLoadKey()).thenReturn("overworld");
        when(dimension.getMinHeight()).thenReturn(-64);
        when(dimension.getMaxHeight()).thenReturn(320);
        StudioSVC.GenerationPublication publication = new StudioSVC.GenerationPublication(dimension, history);
        IrisWorldCreator creator = new IrisWorldCreator().name("created").dimension(publication.dimension())
                .studio(studio).persistent(!studio).studioPackSource(authoring).seed(42L);
        if (handoff) {
            creator.generationHistory(publication.history());
        }
        NamespacedKey key = new NamespacedKey("iris", "created");
        WorldCreator bukkitCreator = mock(WorldCreator.class, RETURNS_SELF);
        when(bukkitCreator.name()).thenReturn("world_iris_created");
        List<GenerationHistory> histories = new ArrayList<>();
        List<File> packs = new ArrayList<>();
        AtomicInteger hashes = new AtomicInteger();
        try (MockedStatic<IrisWorldStorage> storage = mockStatic(IrisWorldStorage.class);
             MockedStatic<WorldCreatorCompat> compatibility = mockStatic(WorldCreatorCompat.class);
             MockedStatic<GenerationPackFingerprint> fingerprints = mockStatic(GenerationPackFingerprint.class, invocation -> {
                 if (invocation.getMethod().getName().equals("compute")) {
                     hashes.incrementAndGet();
                 }
                 return invocation.callRealMethod();
             });
             MockedConstruction<BukkitChunkGenerator> generators = mockConstruction(BukkitChunkGenerator.class,
                     (generator, context) -> {
                         packs.add((File) context.arguments().get(2));
                         histories.add((GenerationHistory) context.arguments().get(4));
                     })) {
            storage.when(() -> IrisWorldStorage.keyFromName("created")).thenReturn(key);
            storage.when(() -> IrisWorldStorage.dimensionRoot(key)).thenReturn(root);
            compatibility.when(() -> WorldCreatorCompat.ofKey(key)).thenReturn(bukkitCreator);
            compatibility.when(() -> WorldCreatorCompat.ofPersistentKey(key)).thenReturn(bukkitCreator);
            compatibility.when(() -> WorldCreatorCompat.persistentDimensionRoot(key)).thenReturn(root);

            assertSame(bukkitCreator, creator.create());

            assertEquals(1, generators.constructed().size());
            assertEquals(List.of(studio ? authoring : pack), packs);
            assertEquals(handoff ? 0 : 1, hashes.get());
            if (handoff) {
                assertSame(history, histories.getFirst());
            } else {
                assertNotSame(history, histories.getFirst());
                assertEquals(history.activeEpoch(), histories.getFirst().activeEpoch());
            }
        }
    }

    private GenerationHistory createHistory() throws IOException {
        Path world = temporaryFolder.newFolder("world").toPath();
        Path source = temporaryFolder.newFolder("source").toPath();
        Files.createDirectories(source.resolve("dimensions"));
        Files.writeString(source.resolve("dimensions/overworld.json"), "{}");
        GenerationEpoch.DimensionContract contract = new GenerationEpoch.DimensionContract(
                "overworld", "iris:overworld_type", "NORMAL", "OVERWORLD", 127,
                -64, 384, 384, 1D, false, "none", 0, "0".repeat(64),
                GenerationEpochContractFactory.CURRENT_DIMENSION_TYPE_FINGERPRINT_SCHEMA, "c".repeat(64));
        return GenerationHistory.create(world, source,
                GenerationPackFingerprint.compute(source, GenerationPackFingerprint.CURRENT_VERSION),
                42L, contract, GenerationRegistryContract.empty());
    }
}
