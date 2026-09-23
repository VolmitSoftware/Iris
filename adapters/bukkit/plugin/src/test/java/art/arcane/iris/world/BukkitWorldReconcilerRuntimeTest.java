package art.arcane.iris.world;

import art.arcane.iris.Iris;
import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.platform.generation.BukkitChunkGenerator;
import art.arcane.iris.world.history.GenerationActivation;
import art.arcane.iris.world.history.GenerationEpoch;
import art.arcane.iris.world.history.GenerationHistory;
import art.arcane.iris.world.history.GenerationHistoryPaths;
import art.arcane.iris.world.history.GenerationHistoryRuntimeRouter;
import art.arcane.iris.world.history.GenerationManifest;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class BukkitWorldReconcilerRuntimeTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void incompleteOrDetachedRuntimeRequiresFullValidation() throws Exception {
        RuntimeFixture fixture = fixture();
        when(fixture.generator().getStartupReady()).thenReturn(new CompletableFuture<>());
        assertEquals(BukkitWorldReconciler.LoadedRuntimeStatus.UNAVAILABLE,
                fixture.backend().validateLoadedRuntime(fixture.world(), fixture.key(), "overworld", 1337L));
        when(fixture.generator().getStartupReady()).thenReturn(CompletableFuture.completedFuture(null));
        when(fixture.engine().getGenerationHistoryRuntimeRouter()).thenReturn(Optional.empty());
        assertEquals(BukkitWorldReconciler.LoadedRuntimeStatus.UNAVAILABLE,
                fixture.backend().validateLoadedRuntime(fixture.world(), fixture.key(), "overworld", 1337L));
    }

    @Test
    public void configuredDimensionAndSeedMustMatchLiveRuntime() throws Exception {
        RuntimeFixture fixture = fixture();
        assertThrows(IOException.class, () -> fixture.backend().validateLoadedRuntime(
                fixture.world(), fixture.key(), "other", 1337L));
        assertThrows(IOException.class, () -> fixture.backend().validateLoadedRuntime(
                fixture.world(), fixture.key(), "overworld", 7L));
    }

    @Test
    public void validatedLiveRuntimeStillRequiresMatchingSavedStorage() throws Exception {
        RuntimeFixture fixture = fixture();
        Path levelRoot = fixture.root().getParent().getParent().getParent();
        Path container = levelRoot.getParent();
        GenerationManifest manifest = mock(GenerationManifest.class);
        GenerationEpoch activeEpoch = fixture.history().activeEpoch();
        GenerationActivation activeActivation = fixture.history().activeActivation();
        when(manifest.activeEpoch()).thenReturn(activeEpoch);
        when(manifest.activeActivation()).thenReturn(activeActivation);
        when(manifest.pendingActivation()).thenReturn(Optional.empty());
        GenerationHistory.PackInspection inspection = new GenerationHistory.PackInspection(
                fixture.history().paths(), manifest, fixture.pack(), Optional.empty());
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<WorldCreatorCompat> creator = mockStatic(WorldCreatorCompat.class);
             MockedStatic<IrisWorldStorage> storage = mockStatic(IrisWorldStorage.class);
             MockedStatic<GenerationHistory> history = mockStatic(GenerationHistory.class)) {
            bukkit.when(Bukkit::getWorldContainer).thenReturn(container.toFile());
            creator.when(() -> WorldCreatorCompat.persistentDimensionRoot(fixture.key())).thenReturn(fixture.root().toFile());
            storage.when(IrisWorldStorage::levelRoot).thenReturn(levelRoot.toFile());
            storage.when(() -> IrisWorldStorage.configuredWorldName(fixture.key(), "world")).thenReturn("world_iris_probe");
            storage.when(() -> IrisWorldStorage.requireFrozenDimensionRoot(
                    container.toFile(), levelRoot.toFile(), "world_iris_probe", fixture.key())).thenReturn(fixture.root().toFile());
            history.when(() -> GenerationHistory.inspectPacks(fixture.root())).thenReturn(inspection);

            assertEquals(BukkitWorldReconciler.LoadedRuntimeStatus.READY, fixture.backend().validateLoadedRuntime(
                    fixture.world(), fixture.key(), "overworld", 1337L));

            GenerationActivation differentActivation = mock(GenerationActivation.class);
            when(manifest.activeActivation()).thenReturn(differentActivation);
            assertThrows(IOException.class, () -> fixture.backend().validateLoadedRuntime(
                    fixture.world(), fixture.key(), "overworld", 1337L));
            when(manifest.activeActivation()).thenReturn(activeActivation);
            Files.delete(fixture.root().resolve("iris/generation/semantics/index.isix"));
            assertThrows(IOException.class, () -> fixture.backend().validateLoadedRuntime(
                    fixture.world(), fixture.key(), "overworld", 1337L));
        }
    }

    private RuntimeFixture fixture() throws Exception {
        Path container = temporaryFolder.newFolder().toPath();
        Path root = container.resolve("world/dimensions/iris/probe");
        Path pack = root.resolve("iris/generation/epochs/" + "a".repeat(64) + "/pack");
        Files.createDirectories(pack);
        Path semantics = root.resolve("iris/generation/semantics");
        Files.createDirectories(semantics);
        Files.createFile(semantics.resolve("index.isix"));
        NamespacedKey key = new NamespacedKey("iris", "probe");
        World world = mock(World.class);
        BukkitChunkGenerator generator = mock(BukkitChunkGenerator.class);
        IrisEngine engine = mock(IrisEngine.class);
        IrisDimension dimension = mock(IrisDimension.class);
        IrisData data = mock(IrisData.class);
        GenerationHistory history = mock(GenerationHistory.class);
        GenerationHistoryRuntimeRouter router = mock(GenerationHistoryRuntimeRouter.class);
        GenerationEpoch epoch = mock(GenerationEpoch.class);
        GenerationActivation activation = mock(GenerationActivation.class);
        GenerationEpoch.DimensionContract contract = mock(GenerationEpoch.DimensionContract.class);
        when(world.getGenerator()).thenReturn(generator);
        when(world.getKey()).thenReturn(key);
        when(generator.getStartupReady()).thenReturn(CompletableFuture.completedFuture(null));
        when(generator.getEngine()).thenReturn(engine);
        when(generator.getGenerationHistory()).thenReturn(history);
        when(generator.getDimensionKey()).thenReturn("overworld");
        when(engine.getGenerationHistoryRuntimeRouter()).thenReturn(Optional.of(router));
        when(router.history()).thenReturn(history);
        when(history.paths()).thenReturn(GenerationHistoryPaths.forDimension(root));
        when(history.activeEpoch()).thenReturn(epoch);
        when(history.activeActivation()).thenReturn(activation);
        when(history.pendingActivation()).thenReturn(Optional.empty());
        when(epoch.worldSeed()).thenReturn(1337L);
        when(epoch.dimensionContract()).thenReturn(contract);
        when(contract.dimensionKey()).thenReturn("overworld");
        when(engine.getWorld()).thenReturn(IrisWorld.builder().platformIdentity(key.toString()).worldFolder(root.toFile()).seed(1337L).build());
        when(engine.getDimension()).thenReturn(dimension);
        when(dimension.getLoadKey()).thenReturn("overworld");
        when(engine.getData()).thenReturn(data);
        when(data.getDataFolder()).thenReturn(pack.toFile());
        Class<?> backendClass = Class.forName(BukkitWorldReconciler.class.getName() + "$BukkitBackend");
        Constructor<?> constructor = backendClass.getDeclaredConstructor(Iris.class);
        constructor.setAccessible(true);
        BukkitWorldReconciler.Backend backend = (BukkitWorldReconciler.Backend) constructor.newInstance(mock(Iris.class));
        return new RuntimeFixture(backend, world, key, generator, engine, history, root, pack);
    }

    private record RuntimeFixture(BukkitWorldReconciler.Backend backend, World world, NamespacedKey key,
                                  BukkitChunkGenerator generator, IrisEngine engine, GenerationHistory history,
                                  Path root, Path pack) {
    }
}
