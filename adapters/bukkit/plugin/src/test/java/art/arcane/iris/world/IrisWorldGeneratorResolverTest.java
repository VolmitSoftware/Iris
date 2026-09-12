package art.arcane.iris.world;

import art.arcane.iris.Iris;
import art.arcane.iris.world.lifecycle.WorldLifecycleStaging;
import art.arcane.iris.pack.validation.CompatAction;
import art.arcane.iris.pack.validation.CompatFinding;
import art.arcane.iris.pack.validation.CompatRegistry;
import art.arcane.iris.integration.ExternalDataSVC;
import art.arcane.iris.pack.BrokenPackException;
import art.arcane.iris.pack.PackValidationRegistry;
import art.arcane.iris.pack.PackValidationResult;
import art.arcane.iris.platform.bukkit.plugin.VolmitPlugin;
import art.arcane.iris.world.task.J;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.mockito.MockedStatic;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

public class IrisWorldGeneratorResolverTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void snapshotDeferralRequiresAnUnresolvedBlockWithAPendingProvider() {
        ExternalDataSVC external = mock(ExternalDataSVC.class);
        when(external.hasPendingBlockProvider("itemsadder:example/stone")).thenReturn(true);
        PackValidationResult unavailable = new PackValidationResult("pack", List.of("unavailable"), List.of(), 1L,
                List.of(new CompatFinding(CompatRegistry.BLOCK, "itemsadder:example/stone",
                        CompatAction.EXCLUDED, "biome", "main", "layers[0]")), "26.2");

        assertTrue(IrisWorldGeneratorResolver.hasPendingExternalContent(unavailable, external));
        when(external.hasPendingBlockProvider("itemsadder:example/stone")).thenReturn(false);
        assertFalse(IrisWorldGeneratorResolver.hasPendingExternalContent(unavailable, external));
        assertFalse(IrisWorldGeneratorResolver.hasPendingExternalContent(unavailable, null));
        assertFalse(IrisWorldGeneratorResolver.hasPendingExternalContent(
                new PackValidationResult("vanilla", List.of(), List.of(), 1L), external));
    }

    @After
    public void clearValidationState() {
        PackValidationRegistry.clear();
        IrisStartupValidation.disable();
        WorldLifecycleStaging.clearAll("world_nether");
    }

    @Test
    public void snapshotValidationIsLazyAndExactRootScoped() throws Exception {
        File packRoot = temporaryFolder.newFolder("world", "iris", "pack");
        writeValidPack(packRoot.toPath());
        PackValidationResult unrelatedNamedFailure = new PackValidationResult(
                "pack", List.of("unrelated basename failure"), List.of(), 1L);
        PackValidationRegistry.publish(unrelatedNamedFailure);

        PackValidationResult result = IrisWorldGeneratorResolver.requireSnapshotLoadable(packRoot);

        assertTrue(result.isLoadable());
        assertEquals(result, PackValidationRegistry.get(packRoot.toPath()));
        assertEquals(unrelatedNamedFailure, PackValidationRegistry.get("pack"));
    }

    @Test
    public void invalidatedSnapshotIsValidatedAgainBeforeAuthorization() throws Exception {
        File packRoot = temporaryFolder.newFolder("replace", "iris", "pack");
        writeValidPack(packRoot.toPath());
        assertTrue(IrisWorldGeneratorResolver.requireSnapshotLoadable(packRoot).isLoadable());

        Files.writeString(
                packRoot.toPath().resolve("dimensions/main.json"),
                "{",
                StandardCharsets.UTF_8);
        PackValidationRegistry.remove(packRoot.toPath());

        assertThrows(BrokenPackException.class,
                () -> IrisWorldGeneratorResolver.requireSnapshotLoadable(packRoot));
        PackValidationResult invalid = PackValidationRegistry.get(packRoot.toPath());
        assertNotNull(invalid);
        assertFalse(invalid.getBlockingErrors().toString(), invalid.isLoadable());
    }

    @Test
    public void providerEventsCoalesceAndRefreshAgainAfterAnInFlightChange() {
        IrisWorldGeneratorResolver resolver = spy(new IrisWorldGeneratorResolver(mock(VolmitPlugin.class)));
        List<Runnable> tasks = new ArrayList<>();
        AtomicInteger passes = new AtomicInteger();
        doAnswer(invocation -> {
            if (passes.incrementAndGet() == 1) {
                resolver.requestExternalContentRefresh();
                assertTrue(tasks.isEmpty());
            }
            return null;
        }).when(resolver).refreshExternalContent();
        try (MockedStatic<J> scheduler = mockStatic(J.class)) {
            scheduler.when(() -> J.a(any(Runnable.class))).thenAnswer(invocation -> {
                tasks.add(invocation.getArgument(0, Runnable.class));
                return null;
            });

            resolver.requestExternalContentRefresh();
            resolver.requestExternalContentRefresh();
            assertEquals(1, tasks.size());
            tasks.removeFirst().run();
            assertEquals(1, passes.get());
            assertEquals(1, tasks.size());
            tasks.removeFirst().run();
            assertEquals(2, passes.get());
            assertTrue(tasks.isEmpty());
        }
    }

    @Test
    public void paperStartupAliasResolvesToCanonicalRuntimeKey() throws Exception {
        File levelRoot = ownedLevelRoot("startup-alias", "moon");

        assertEquals(
                new NamespacedKey("iris", "moon"),
                IrisWorldGeneratorResolver.configuredWorldKey("world_iris_moon", "world", levelRoot)
        );
        assertEquals(
                new NamespacedKey("iris", "moon"),
                IrisWorldGeneratorResolver.configuredWorldKey("moon", "world", levelRoot)
        );
        assertEquals(
                NamespacedKey.minecraft("overworld"),
                IrisWorldGeneratorResolver.configuredWorldKey("world", "world", levelRoot)
        );
    }

    @Test
    public void multiverseRuntimeKeyedNameResolvesToTheStoredIrisWorld() throws Exception {
        File levelRoot = ownedLevelRoot("runtime-keyed", "moon");

        assertEquals(
                "mv load names an Iris world iris_<key> because its keyed creator refuses the namespace",
                new NamespacedKey("iris", "moon"),
                IrisWorldGeneratorResolver.configuredWorldKey("iris_moon", "world", levelRoot)
        );
    }

    @Test
    public void runtimeKeyedNameWithoutStorageKeepsItsLiteralKey() throws Exception {
        File levelRoot = ownedLevelRoot("runtime-keyed-missing", "elsewhere");

        assertEquals(
                new NamespacedKey("iris", "iris_moon"),
                IrisWorldGeneratorResolver.configuredWorldKey("iris_moon", "world", levelRoot)
        );
    }

    @Test
    public void literalWorldStorageWinsOverTheRuntimeKeyedReading() throws Exception {
        File levelRoot = ownedLevelRoot("runtime-keyed-collision", "moon");
        assertTrue(new File(levelRoot, "dimensions/iris/iris_moon").mkdirs());

        assertEquals(
                "a world genuinely created as iris_moon keeps its own identity",
                new NamespacedKey("iris", "iris_moon"),
                IrisWorldGeneratorResolver.configuredWorldKey("iris_moon", "world", levelRoot)
        );
    }

    @Test
    public void levelNamedIrisStillResolvesItsOwnStartupNames() throws Exception {
        File levelRoot = temporaryFolder.newFolder("level-named-iris", "iris");
        assertTrue(new File(levelRoot, "dimensions/iris/moon").mkdirs());

        assertEquals(
                new NamespacedKey("iris", "moon"),
                IrisWorldGeneratorResolver.configuredWorldKey("iris_iris_moon", "iris", levelRoot)
        );
    }

    /**
     * A refusal has to name the world the admin typed. The generation guard keeps the storage-existence
     * check that stops a genuine iris_moon world being mis-mapped; the message must not, or an orphan is
     * described as iris:iris_orphan1 and the remedy names a world that never existed.
     */
    @Test
    public void refusalMessagesNameTheRuntimeKeyedWorldWithoutItsStorage() {
        assertEquals(
                new NamespacedKey("iris", "orphan1"),
                IrisWorldGeneratorResolver.messageWorldKey("iris_orphan1", "world")
        );
        assertEquals(
                new NamespacedKey("iris", "moon"),
                IrisWorldGeneratorResolver.messageWorldKey("world_iris_moon", "world")
        );
        assertEquals(
                "a world genuinely created as iris_moon keeps its own identity",
                new NamespacedKey("iris", "iris_moon"),
                IrisWorldGeneratorResolver.messageWorldKey("world_iris_iris_moon", "world")
        );
        assertEquals(
                NamespacedKey.minecraft("overworld"),
                IrisWorldGeneratorResolver.messageWorldKey("world", "world")
        );
    }

    private File ownedLevelRoot(String scope, String worldKey) throws Exception {
        File levelRoot = temporaryFolder.newFolder(scope, "world");
        assertTrue(new File(levelRoot, "dimensions/iris/" + worldKey).mkdirs());
        return levelRoot;
    }

    @Test
    public void plotSquaredDiscoveryProbeIsIgnoredQuietly() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<Iris> iris = mockStatic(Iris.class)) {
            bukkit.when(() -> Bukkit.getWorld("CheckingPlotSquaredGenerator")).thenReturn(null);

            ChunkGenerator probe = new IrisWorldGeneratorResolver(null)
                    .resolveDefaultWorldGenerator("CheckingPlotSquaredGenerator", "");

            assertNull("Iris cannot be used as a PlotSquared base generator", probe);
            bukkit.verify(Bukkit::shutdown, never());
        }
    }

    @Test
    public void plotSquaredSentinelWithDimensionIdUsesNormalOwnershipChecks() throws Exception {
        File worldContainer = temporaryFolder.newFolder("plotsquared-non-probe");
        File levelRoot = new File(worldContainer, "world");
        assertTrue(levelRoot.mkdirs());

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<Iris> iris = mockStatic(Iris.class)) {
            Server server = mock(Server.class);
            when(server.getLevelDirectory()).thenReturn(levelRoot.toPath());
            bukkit.when(Bukkit::getServer).thenReturn(server);
            bukkit.when(Bukkit::getWorldContainer).thenReturn(worldContainer);
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of());

            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
                    () -> new IrisWorldGeneratorResolver(null)
                            .resolveDefaultWorldGenerator("CheckingPlotSquaredGenerator", "overworld"));

            assertTrue(failure.getMessage(), failure.getMessage().contains("CheckingPlotSquaredGenerator"));
            bukkit.verify(Bukkit::shutdown, never());
        }
    }

    @Test
    public void discoveryProbeOfLoadedWorldReturnsInertGeneratorQuietly() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<Iris> iris = mockStatic(Iris.class)) {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(mock(World.class));
            IrisStartupValidation.begin();
            IrisStartupValidation.requireRestart("restart boundary");

            ChunkGenerator probe = new IrisWorldGeneratorResolver(null)
                    .resolveDefaultWorldGenerator("world", "");

            assertNotNull("Multiverse drops Iris from /mv generators when the probe returns null", probe);
            bukkit.verify(Bukkit::shutdown, never());
            IllegalStateException refusal = assertThrows(
                    IllegalStateException.class,
                    () -> probe.generateNoise(mock(WorldInfo.class), new Random(), 0, 0, null));
            assertTrue(refusal.getMessage(), refusal.getMessage().contains("'world'"));
            assertTrue(refusal.getMessage(), refusal.getMessage().contains("discovery probe"));
        }
    }

    @Test
    public void restartRequiredDefaultWorldCannotFallBackToVanilla() {
        ChunkGenerator stagedGenerator = mock(ChunkGenerator.class);
        ChunkGenerator vanillaFallback = mock(ChunkGenerator.class);
        WorldLifecycleStaging.stageGenerator("world_nether", stagedGenerator, null);
        IrisStartupValidation.begin();
        IrisStartupValidation.requireRestart("updated external datapacks require restart");

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<Iris> iris = mockStatic(Iris.class)) {
            bukkit.when(() -> Bukkit.getWorld("world_nether")).thenReturn(null);

            ChunkGenerator selected = craftBukkitGeneratorOrFallback(
                    () -> new IrisWorldGeneratorResolver(null)
                            .resolveDefaultWorldGenerator("world_nether", "underworld"),
                    vanillaFallback
            );

            assertNotSame(vanillaFallback, selected);
            assertNotSame(stagedGenerator, selected);
            assertSame(stagedGenerator, WorldLifecycleStaging.consumeGenerator("world_nether"));
            WorldInfo worldInfo = mock(WorldInfo.class);
            Random random = new Random();
            assertFalse(selected.shouldGenerateNoise());
            assertFalse(selected.shouldGenerateNoise(worldInfo, random, 0, 0));
            assertFalse(selected.shouldGenerateSurface());
            assertFalse(selected.shouldGenerateSurface(worldInfo, random, 0, 0));
            assertFalse(selected.shouldGenerateBedrock());
            assertFalse(selected.shouldGenerateCaves());
            assertFalse(selected.shouldGenerateCaves(worldInfo, random, 0, 0));
            assertFalse(selected.shouldGenerateDecorations());
            assertFalse(selected.shouldGenerateDecorations(worldInfo, random, 0, 0));
            assertFalse(selected.shouldGenerateMobs());
            assertFalse(selected.shouldGenerateMobs(worldInfo, random, 0, 0));
            assertFalse(selected.shouldGenerateStructures());
            assertFalse(selected.shouldGenerateStructures(worldInfo, random, 0, 0));
            IllegalStateException refusal = assertThrows(
                    IllegalStateException.class,
                    () -> selected.generateNoise(
                            worldInfo,
                            random,
                            0,
                            0,
                            mock(ChunkGenerator.ChunkData.class)
                    )
            );
            assertTrue(refusal.getMessage(), refusal.getMessage().contains("world_nether"));
            assertTrue(refusal.getMessage(), refusal.getMessage().contains("updated external datapacks require restart"));
        }
    }

    @Test
    public void everyBlockingStartupStateReturnsFailClosedGenerator() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<Iris> iris = mockStatic(Iris.class)) {
            IrisStartupValidation.begin();
            assertStartupStateFailsClosed("world_pending", "external datapacks");

            IrisStartupValidation.begin();
            IrisStartupValidation.markDatapacksInvalid("invalid external datapack state");
            assertStartupStateFailsClosed("world_datapack_invalid", "invalid external datapack state");

            IrisStartupValidation.begin();
            IrisStartupValidation.markDatapacksReady();
            IrisStartupValidation.markPacksInvalid(List.of("invalid dimension pack state"));
            assertStartupStateFailsClosed("world_pack_invalid", "invalid dimension pack state");
        }
    }

    @Test
    public void readyStartupStillConsumesStagedGenerator() {
        ChunkGenerator stagedGenerator = mock(ChunkGenerator.class);
        WorldLifecycleStaging.stageGenerator("world_nether", stagedGenerator, null);
        IrisStartupValidation.begin();
        IrisStartupValidation.markDatapacksReady();
        IrisStartupValidation.markPacksReady();

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<Iris> iris = mockStatic(Iris.class)) {
            bukkit.when(() -> Bukkit.getWorld("world_nether")).thenReturn(null);

            ChunkGenerator selected = new IrisWorldGeneratorResolver(null)
                    .resolveDefaultWorldGenerator("world_nether", "underworld");

            assertSame(stagedGenerator, selected);
        }
    }

    @Test
    public void externalCreateWithoutIrisStorageThrowsWithoutShutdown() throws Exception {
        File worldContainer = temporaryFolder.newFolder("external-create");
        File levelRoot = new File(worldContainer, "world");
        assertTrue(levelRoot.mkdirs());

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<Iris> iris = mockStatic(Iris.class)) {
            Server server = mock(Server.class);
            when(server.getLevelDirectory()).thenReturn(levelRoot.toPath());
            bukkit.when(Bukkit::getServer).thenReturn(server);
            bukkit.when(Bukkit::getWorldContainer).thenReturn(worldContainer);
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of());

            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
                    () -> new IrisWorldGeneratorResolver(null)
                            .resolveDefaultWorldGenerator("mvtest", "overworld"));

            assertTrue(failure.getMessage(), failure.getMessage().contains("mvtest"));
            assertTrue(failure.getMessage(), failure.getMessage().contains("/iris create"));
            bukkit.verify(Bukkit::shutdown, never());
        }
    }

    @Test
    public void ownedIrisWorldWithUnusableSnapshotStillStopsTheServer() throws Exception {
        File worldContainer = temporaryFolder.newFolder("owned-broken");
        File levelRoot = new File(worldContainer, "world");
        assertTrue(levelRoot.mkdirs());
        assertTrue(new File(worldContainer, "world_iris_moon/dimensions/iris/moon").mkdirs());

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<Iris> iris = mockStatic(Iris.class)) {
            Server server = mock(Server.class);
            when(server.getLevelDirectory()).thenReturn(levelRoot.toPath());
            bukkit.when(Bukkit::getServer).thenReturn(server);
            bukkit.when(Bukkit::getWorldContainer).thenReturn(worldContainer);
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of());

            assertThrows(
                    IllegalStateException.class,
                    () -> new IrisWorldGeneratorResolver(null)
                            .resolveDefaultWorldGenerator("world_iris_moon", "overworld"));

            bukkit.verify(Bukkit::shutdown);
        }
    }

    @Test
    public void vanillaDimensionSlotWithoutFrozenPackIsNotOwned() throws Exception {
        File worldContainer = temporaryFolder.newFolder("vanilla-slot");
        File levelRoot = new File(worldContainer, "world");
        assertTrue(new File(levelRoot, "dimensions/minecraft/the_nether").mkdirs());

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<Iris> iris = mockStatic(Iris.class)) {
            Server server = mock(Server.class);
            when(server.getLevelDirectory()).thenReturn(levelRoot.toPath());
            bukkit.when(Bukkit::getServer).thenReturn(server);
            bukkit.when(Bukkit::getWorldContainer).thenReturn(worldContainer);
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of());

            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
                    () -> new IrisWorldGeneratorResolver(null)
                            .resolveDefaultWorldGenerator("world_nether", "overworld"));

            assertTrue(failure.getMessage(), failure.getMessage().contains("minecraft:the_nether"));
            assertTrue(failure.getMessage(), failure.getMessage().contains("/iris create"));
            bukkit.verify(Bukkit::shutdown, never());
        }
    }

    @Test
    public void alreadyLoadedIrisKeyIsRefusedBeforeAnyEngineCanStart() throws Exception {
        File worldContainer = temporaryFolder.newFolder("duplicate-key");
        File levelRoot = new File(worldContainer, "world");
        assertTrue(levelRoot.mkdirs());
        writeValidPack(new File(levelRoot, "dimensions/iris/irisworld/iris/pack").toPath());

        World loaded = mock(World.class);
        when(loaded.getKey()).thenReturn(new NamespacedKey("iris", "irisworld"));
        when(loaded.getName()).thenReturn("world_iris_irisworld");

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<Iris> iris = mockStatic(Iris.class)) {
            Server server = mock(Server.class);
            when(server.getLevelDirectory()).thenReturn(levelRoot.toPath());
            bukkit.when(Bukkit::getServer).thenReturn(server);
            bukkit.when(Bukkit::getWorldContainer).thenReturn(worldContainer);
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of(loaded));

            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
                    () -> new IrisWorldGeneratorResolver(null)
                            .resolveDefaultWorldGenerator("irisworld", "overworld"));

            assertTrue(failure.getMessage(), failure.getMessage().contains("iris:irisworld"));
            assertTrue(failure.getMessage(), failure.getMessage().contains("world_iris_irisworld"));
            bukkit.verify(Bukkit::shutdown, never());
        }
    }

    private static void writeValidPack(Path packRoot) throws Exception {
        Files.createDirectories(packRoot.resolve("dimensions"));
        Files.createDirectories(packRoot.resolve("regions"));
        Files.createDirectories(packRoot.resolve("biomes"));
        Files.writeString(
                packRoot.resolve("dimensions/main.json"),
                "{\"regions\":[\"region\"]}",
                StandardCharsets.UTF_8);
        Files.writeString(
                packRoot.resolve("regions/region.json"),
                "{\"landBiomes\":[\"biome\"]}",
                StandardCharsets.UTF_8);
        Files.writeString(
                packRoot.resolve("biomes/biome.json"),
                "{\"name\":\"Biome\"}",
                StandardCharsets.UTF_8);
    }

    private static void assertStartupStateFailsClosed(String worldName, String expectedReason) {
        ChunkGenerator vanillaFallback = mock(ChunkGenerator.class);
        ChunkGenerator selected = craftBukkitGeneratorOrFallback(
                () -> new IrisWorldGeneratorResolver(null)
                        .resolveDefaultWorldGenerator(worldName, "overworld"),
                vanillaFallback
        );
        assertNotSame(vanillaFallback, selected);
        IllegalStateException refusal = assertThrows(
                IllegalStateException.class,
                () -> selected.generateNoise(
                        mock(WorldInfo.class),
                        new Random(),
                        0,
                        0,
                        mock(ChunkGenerator.ChunkData.class)
                )
        );
        assertTrue(refusal.getMessage(), refusal.getMessage().contains(worldName));
        assertTrue(refusal.getMessage(), refusal.getMessage().contains(expectedReason));
    }

    private static ChunkGenerator craftBukkitGeneratorOrFallback(
            Supplier<ChunkGenerator> resolution,
            ChunkGenerator fallback
    ) {
        try {
            ChunkGenerator selected = resolution.get();
            return selected == null ? fallback : selected;
        } catch (Throwable ignoredGeneratorFailure) {
            return fallback;
        }
    }
}
