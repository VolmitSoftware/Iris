package art.arcane.iris.core.service;

import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioMode;
import art.arcane.iris.core.tools.IrisToolbelt;
import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.framework.BiomeEnvironment;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.GenerationSessionManager;
import art.arcane.iris.engine.framework.GenerationTransitionGate;
import art.arcane.iris.engine.history.SavedBiomeUnavailableException;
import art.arcane.iris.engine.mantle.EngineMantle;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.engine.object.IrisRegion;
import art.arcane.iris.engine.platform.PlatformChunkGenerator;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.iris.util.common.scheduling.J;
import art.arcane.volmlib.util.board.Board;
import art.arcane.volmlib.util.board.BoardSettings;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BoardSVCSavedBiomeTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    private static IrisPlatform previousPlatform;

    @BeforeClass
    public static void bindPlatform() {
        previousPlatform = IrisPlatforms.isBound() ? IrisPlatforms.get() : null;
        IrisPlatforms.unbind();
        IrisPlatform platform = mock(IrisPlatform.class);
        PlatformRegistries registries = mock(PlatformRegistries.class);
        when(platform.registries()).thenReturn(registries);
        when(registries.block(anyString())).thenReturn(mock(PlatformBlockState.class));
        IrisPlatforms.bind(platform);
    }

    @AfterClass
    public static void restorePlatform() {
        IrisPlatforms.unbind();
        if (previousPlatform != null) {
            IrisPlatforms.bind(previousPlatform);
        }
    }

    @Test
    public void worldEntryLoadingKeepsRefreshAliveAndRecoversWithoutMovement() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.biomeFailure = new SavedBiomeUnavailableException("Saved biome is loading", true);

            fixture.service.on(new PlayerChangedWorldEvent(fixture.player, mock(World.class)));

            assertTrue(fixture.lines().contains("Loading"));
            assertEquals(1, fixture.ticks.size());
            fixture.logging.verifyNoInteractions();

            fixture.biomeFailure = null;
            fixture.environment = environment("Saved region", "Saved biome");
            fixture.tick();

            assertTrue(fixture.lines().contains("Saved region"));
            assertTrue(fixture.lines().contains("Saved biome"));
            assertFalse(fixture.lines().contains("Loading"));
            assertEquals(1, fixture.ticks.size());
            verify(fixture.engine, times(2)).getBiomeOrMantleEnvironment(8, 160, 8);
            verify(fixture.engine, never()).getRegion(anyInt(), anyInt(), anyInt());
            verify(fixture.engine, never()).getBiomeOrMantle(anyInt(), anyInt(), anyInt());
        }
    }

    @Test
    public void unsavedBiomeUsesTheMantleOverride() throws Exception {
        try (Fixture fixture = new Fixture()) {
            IrisBiome natural = mock(IrisBiome.class);
            IrisBiome flooded = mock(IrisBiome.class);
            when(natural.getName()).thenReturn("Natural cave");
            when(flooded.getName()).thenReturn("Flooded cave");
            when(fixture.engine.getHeight(8, 8)).thenReturn(200);
            when(fixture.engine.getComplex()).thenReturn(mock(IrisComplex.class));
            when(fixture.engine.getCaveBiome(8, 160, 8)).thenReturn(natural);
            when(fixture.engine.getCaveOrMantleBiome(8, 160, 8)).thenReturn(flooded);
            when(fixture.engine.getDimension()).thenReturn(fixture.environment.dimension());
            when(fixture.engine.getData()).thenReturn(fixture.environment.data());
            doCallRealMethod().when(fixture.engine).getBiome(8, 160, 8);
            doCallRealMethod().when(fixture.engine).getBiomeOrMantle(8, 160, 8);
            doCallRealMethod().when(fixture.engine).getBiomeEnvironment(8, 160, 8);
            doCallRealMethod().when(fixture.engine).getBiomeOrMantleEnvironment(8, 160, 8);

            fixture.service.updatePlayer(fixture.player);

            assertTrue(fixture.lines().contains("Flooded cave"));
            assertFalse(fixture.lines().contains("Natural cave"));
            verify(fixture.engine, never()).getBiomeEnvironment(anyInt(), anyInt(), anyInt());
        }
    }

    @Test
    public void unavailableBiomeReportsOncePerContextAndKeepsRefreshing() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.biomeFailure = new SavedBiomeUnavailableException("Saved pack is missing", false);
            fixture.service.updatePlayer(fixture.player);

            assertTrue(fixture.lines().contains("Unavailable"));
            assertFalse(fixture.lines().contains("Loading"));
            fixture.biomeFailure = new SavedBiomeUnavailableException("Saved pack is missing", false);
            fixture.tick();
            fixture.logging.verify(() -> IrisLogging.reportError(anyString(),
                    any(SavedBiomeUnavailableException.class)), times(1));

            fixture.location = new Location(fixture.world, 24, 96, 8);
            fixture.tick();

            fixture.logging.verify(() -> IrisLogging.reportError(anyString(),
                    any(SavedBiomeUnavailableException.class)), times(2));
            assertEquals(1, fixture.ticks.size());
        }
    }

    @Test
    public void changingWorldClearsThePreviousBiomeWhileTheNewOneLoads() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.environment = environment("Previous region", "Previous biome");
            fixture.service.updatePlayer(fixture.player);
            assertTrue(fixture.lines().contains("Previous biome"));
            World previous = fixture.world;
            fixture.world = world("second-studio");
            fixture.location = new Location(fixture.world, 8, 96, 8);
            fixture.biomeFailure = new SavedBiomeUnavailableException("Saved biome is loading", true);

            fixture.service.on(new PlayerChangedWorldEvent(fixture.player, previous));

            assertTrue(fixture.lines().contains("Loading"));
            assertFalse(fixture.lines().contains("Previous region"));
            assertFalse(fixture.lines().contains("Previous biome"));
            assertEquals(1, fixture.ticks.size());
        }
    }

    @Test
    public void jigsawViewDoesNotQueryBiomesAndOrdinaryViewStillRetries() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.biomeFailure = new SavedBiomeUnavailableException("Saved biome is loading", true);
            JigsawStudioBoardContext context = new JigsawStudioBoardContext(
                    fixture.world.getUID(), UUID.randomUUID(), "village", JigsawStudioMode.PLANAR_JIGSAW,
                    "Corner", "Corner", "mossy", JigsawStudioBoardState.SAVED, "Open the chest");

            fixture.service.applyJigsawContext(fixture.player, context);

            assertEquals(BoardSVC.jigsawLines(context), fixture.service.getLines(fixture.player));
            verify(fixture.engine, never()).getBiomeOrMantleEnvironment(anyInt(), anyInt(), anyInt());
            assertTrue(fixture.ticks.isEmpty());

            fixture.service.clearJigsawContext(fixture.player);

            assertTrue(fixture.lines().contains("Loading"));
            assertFalse(fixture.lines().contains("Jigsaw Studio"));
            assertEquals(1, fixture.ticks.size());
        }
    }

    @Test
    public void ordinaryRefreshLeavesOwnerThreadAvailableForStudioCheckpoint() throws Exception {
        GenerationSessionManager sessions = new GenerationSessionManager(true);
        AtomicInteger height = new AtomicInteger(70);
        AtomicInteger checkpoints = new AtomicInteger();
        ExecutorService owner = Executors.newSingleThreadExecutor();
        ExecutorService reloader = Executors.newSingleThreadExecutor();
        Fixture fixture = null;
        try {
            fixture = owner.submit(() -> {
                Fixture created = new Fixture();
                when(created.engine.getGenerationSessions()).thenReturn(sessions);
                when(created.engine.getHeight(8, 8)).thenAnswer(invocation -> {
                    try (GenerationTransitionGate.Participation ignored = sessions.transitionGate().enter()) {
                        return height.get();
                    }
                });
                created.service.updatePlayer(created.player);
                return created;
            }).get(3L, TimeUnit.SECONDS);
            Fixture board = fixture;
            List<String> previous = board.service.getLines(board.player);
            assertTrue(board.lines().contains("70"));

            CompletableFuture<Void> cutover = CompletableFuture.runAsync(() -> {
                try (GenerationTransitionGate.Transition ignored = sessions.transitionGate().beginTransition(2_000L)) {
                    sessions.sealAndAwait("Studio generation cutover", 2_000L);
                    Future<?> refresh = owner.submit(board::tick);
                    owner.submit(checkpoints::incrementAndGet).get(2L, TimeUnit.SECONDS);
                    refresh.get(2L, TimeUnit.SECONDS);
                    assertEquals(previous, board.service.getLines(board.player));
                    verify(board.engine, times(2)).getGeneratedPerSecond();
                    verify(board.engine, times(1)).getBiomeOrMantleEnvironment(8, 160, 8);
                    verify(board.engine, times(1)).getHeight(8, 8);
                    height.set(85);
                    sessions.activateNextSession();
                } catch (Exception failure) {
                    throw new IllegalStateException(failure);
                }
            }, reloader);
            cutover.get(3L, TimeUnit.SECONDS);

            assertEquals(1, checkpoints.get());
            owner.submit(board::tick).get(2L, TimeUnit.SECONDS);
            assertTrue(board.lines().contains("85"));
            assertEquals(1, board.ticks.size());
            assertEquals(0, sessions.activeLeases());
            verify(board.engine, times(2)).getBiomeOrMantleEnvironment(8, 160, 8);
            verify(board.engine, times(2)).getHeight(8, 8);
        } finally {
            try {
                if (fixture != null) {
                    Fixture board = fixture;
                    owner.submit(board::close).get(2L, TimeUnit.SECONDS);
                }
            } finally {
                owner.shutdownNow();
                reloader.shutdownNow();
                assertTrue(owner.awaitTermination(2L, TimeUnit.SECONDS));
                assertTrue(reloader.awaitTermination(2L, TimeUnit.SECONDS));
            }
        }
    }

    private static BiomeEnvironment environment(String regionName, String biomeName) {
        IrisRegion region = mock(IrisRegion.class);
        IrisBiome biome = mock(IrisBiome.class);
        when(region.getName()).thenReturn(regionName);
        when(biome.getName()).thenReturn(biomeName);
        return new BiomeEnvironment(1L, biome, region, mock(IrisDimension.class), mock(IrisData.class));
    }

    private static World world(String name) {
        World world = mock(World.class);
        when(world.getUID()).thenReturn(UUID.randomUUID());
        when(world.getName()).thenReturn(name);
        when(world.getMinHeight()).thenReturn(-64);
        return world;
    }

    private static void setField(BoardSVC service, String name, Object value) throws ReflectiveOperationException {
        Field field = BoardSVC.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(service, value);
    }

    private static final class Fixture implements AutoCloseable {
        private final MockedStatic<J> scheduling = mockStatic(J.class);
        private final MockedStatic<IrisToolbelt> toolbelt = mockStatic(IrisToolbelt.class);
        private final MockedStatic<IrisSettings> settings = mockStatic(IrisSettings.class);
        private final MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class);
        private final MockedConstruction<Board> boards = mockConstruction(Board.class,
                (board, context) -> when(board.ownsScoreboardAssignment()).thenReturn(true));
        private final BoardSVC service = new BoardSVC();
        private final Player player = mock(Player.class);
        private final Engine engine = mock(Engine.class);
        private final ArrayDeque<Runnable> ticks = new ArrayDeque<>();
        private World world = world("first-studio");
        private Location location = new Location(world, 8, 96, 8);
        private BiomeEnvironment environment = environment("Ready region", "Ready biome");
        private SavedBiomeUnavailableException biomeFailure;

        private Fixture() throws ReflectiveOperationException {
            setField(service, "boardEnabled", true);
            setField(service, "settings", BoardSettings.builder().boardProvider(service).build());
            settings.when(IrisSettings::get).thenReturn(new IrisSettings());
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            when(player.isOnline()).thenReturn(true);
            when(player.getWorld()).thenAnswer(invocation -> world);
            when(player.getLocation()).thenAnswer(invocation -> location);
            scheduling.when(() -> J.isOwnedByCurrentRegion(player)).thenReturn(true);
            scheduling.when(() -> J.runEntity(same(player), any(Runnable.class))).thenAnswer(invocation -> {
                invocation.getArgument(1, Runnable.class).run();
                return true;
            });
            scheduling.when(() -> J.runEntity(same(player), any(Runnable.class), eq(20), any(Runnable.class)))
                    .thenAnswer(invocation -> ticks.add(invocation.getArgument(1, Runnable.class)));
            PlatformChunkGenerator generator = mock(PlatformChunkGenerator.class);
            when(generator.isStudio()).thenReturn(true);
            when(generator.getEngine()).thenReturn(engine);
            toolbelt.when(() -> IrisToolbelt.access(any(World.class))).thenReturn(generator);
            when(engine.getGeneratedPerSecond()).thenReturn(10D);
            EngineMantle mantle = mock(EngineMantle.class);
            when(engine.getMantle()).thenReturn(mantle);
            doAnswer(invocation -> sampledEnvironment()).when(engine).getBiomeEnvironment(anyInt(), anyInt(), anyInt());
            doAnswer(invocation -> sampledEnvironment()).when(engine).getBiomeOrMantleEnvironment(anyInt(), anyInt(), anyInt());
            doAnswer(invocation -> sampledEnvironment().region()).when(engine).getRegion(anyInt(), anyInt(), anyInt());
            doAnswer(invocation -> sampledEnvironment().biome()).when(engine).getBiomeOrMantle(anyInt(), anyInt(), anyInt());
        }

        private BiomeEnvironment sampledEnvironment() {
            if (biomeFailure != null) {
                throw biomeFailure;
            }
            return environment;
        }

        private String lines() {
            return String.join("\n", service.getLines(player));
        }

        private void tick() {
            assertFalse("The ordinary scoreboard must schedule its next refresh", ticks.isEmpty());
            ticks.remove().run();
        }

        @Override
        public void close() {
            boards.close();
            logging.close();
            settings.close();
            toolbelt.close();
            scheduling.close();
        }
    }
}
