package art.arcane.iris.generation.runtime;

import art.arcane.iris.platform.bukkit.plugin.Chunks;
import art.arcane.iris.generation.context.IrisContext;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.matter.Matter;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.junit.Test;
import org.junit.BeforeClass;
import org.mockito.MockedStatic;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class WorldChunkMaintenanceInitialSpawnTest {
    @BeforeClass
    public static void initializeGameRules() throws Exception {
        WorldEntitySpawnerAdmissionTest.initializeGameRules();
    }

    @Test
    public void completionIsWrittenAfterOwningRegionWorkAndDuplicateRequestsAreSkipped() {
        try (Fixture fixture = new Fixture()) {
            AtomicInteger attempts = new AtomicInteger();
            fixture.start(attempts::incrementAndGet);
            fixture.start(attempts::incrementAndGet);
            assertEquals(1, fixture.async.size());
            fixture.async.remove().run();
            verify(fixture.world, never()).isChunkLoaded(2, -1);
            verify(fixture.mantle, never()).flag(2, -1, MantleFlag.INITIAL_SPAWNED_MARKER, true);

            fixture.regions.remove().run();

            assertEquals(1, attempts.get());
            assertTrue(fixture.maintenance.isInitialSpawnComplete(2, -1));
            verify(fixture.mantle, never()).flag(2, -1, MantleFlag.INITIAL_SPAWNED_MARKER, true);
            fixture.start(attempts::incrementAndGet);
            assertTrue(fixture.regions.isEmpty());
            fixture.async.remove().run();
            verify(fixture.mantle).flag(2, -1, MantleFlag.INITIAL_SPAWNED_MARKER, true);
        }
    }

    @Test
    public void unloadAndDisabledMobSpawningRemainEligibleForRetry() {
        for (boolean unloaded : new boolean[]{true, false}) {
            try (Fixture fixture = new Fixture()) {
                AtomicInteger attempts = new AtomicInteger();
                when(fixture.world.isChunkLoaded(2, -1)).thenReturn(!unloaded);
                when(fixture.world.getGameRuleValue(GameRules.SPAWN_MOBS)).thenReturn(unloaded);
                fixture.start(attempts::incrementAndGet);
                fixture.async.remove().run();
                fixture.regions.remove().run();
                assertFalse(fixture.maintenance.isInitialSpawnComplete(2, -1));
                assertEquals(0, attempts.get());
                verify(fixture.mantle, never()).flag(2, -1, MantleFlag.INITIAL_SPAWNED_MARKER, true);

                when(fixture.world.isChunkLoaded(2, -1)).thenReturn(true);
                when(fixture.world.getGameRuleValue(GameRules.SPAWN_MOBS)).thenReturn(true);
                fixture.start(attempts::incrementAndGet);
                fixture.async.remove().run();
                fixture.regions.remove().run();
                assertEquals(1, attempts.get());
            }
        }
    }

    @Test
    public void persistedCompletionAndRegionRejectionDoNotRunInitialWork() {
        try (Fixture fixture = new Fixture()) {
            AtomicInteger attempts = new AtomicInteger();
            when(fixture.mantle.hasFlag(2, -1, MantleFlag.INITIAL_SPAWNED_MARKER)).thenReturn(true);
            fixture.start(attempts::incrementAndGet);
            fixture.async.remove().run();
            assertTrue(fixture.maintenance.isInitialSpawnComplete(2, -1));
            assertTrue(fixture.regions.isEmpty());
            assertEquals(0, attempts.get());
        }
        try (Fixture fixture = new Fixture()) {
            AtomicInteger attempts = new AtomicInteger();
            fixture.acceptRegions = false;
            fixture.start(attempts::incrementAndGet);
            fixture.async.remove().run();
            fixture.start(attempts::incrementAndGet);
            assertEquals(1, fixture.async.size());
            assertFalse(fixture.maintenance.isInitialSpawnComplete(2, -1));
            assertEquals(0, attempts.get());
        }
    }

    @Test
    public void shutdownDrainWaitsForTheReservedCompletionWriteAfterInitialEntitiesWereEmitted() throws Exception {
        try (Fixture fixture = new Fixture()) {
            AtomicInteger spawned = new AtomicInteger();
            fixture.start(spawned::incrementAndGet);
            fixture.async.remove().run();
            try (GenerationSessionLease owner = fixture.sessions.acquire("initial_spawn_owner");
                 IrisContext.Scope ignored = IrisContext.open(fixture.engine, owner.sessionId(), null)) {
                fixture.regions.remove().run();
            }
            assertEquals(1, spawned.get());
            assertEquals(1, fixture.sessions.activeLeases());
            when(fixture.engine.isClosing()).thenReturn(true);
            ExecutorService closer = Executors.newSingleThreadExecutor();
            try {
                CountDownLatch sealing = new CountDownLatch(1);
                Future<?> drained = closer.submit(() -> {
                    sealing.countDown();
                    fixture.sessions.sealAndAwait("shutdown", 2_000L, true);
                    return null;
                });
                assertTrue(sealing.await(1L, TimeUnit.SECONDS));
                assertFalse(drained.isDone());
                fixture.async.remove().run();
                drained.get(1L, TimeUnit.SECONDS);

                verify(fixture.mantle).flag(2, -1, MantleFlag.INITIAL_SPAWNED_MARKER, true);
                assertEquals(0, fixture.sessions.activeLeases());
            } finally {
                closer.shutdownNow();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static final class Fixture implements AutoCloseable {
        private final Engine engine = mock(Engine.class);
        private final GenerationSessionManager sessions = new GenerationSessionManager();
        private final IrisWorldManager manager = mock(IrisWorldManager.class);
        private final World world = mock(World.class);
        private final Mantle<Matter> mantle = mock(Mantle.class);
        private final WorldChunkMaintenance maintenance = new WorldChunkMaintenance(manager);
        private final Queue<Runnable> async = new ArrayDeque<>();
        private final Queue<Runnable> regions = new ArrayDeque<>();
        private final MockedStatic<J> scheduler;
        private final MockedStatic<Chunks> chunks;
        private boolean acceptRegions = true;

        private Fixture() {
            scheduler = mockStatic(J.class);
            try {
                chunks = mockStatic(Chunks.class);
            } catch (RuntimeException | Error failure) {
                scheduler.close();
                throw failure;
            }
            try {
                configure();
            } catch (RuntimeException | Error failure) {
                close();
                throw failure;
            }
        }

        private void configure() {
            when(manager.getEngine()).thenReturn(engine);
            when(engine.getGenerationSessions()).thenReturn(sessions);
            when(manager.getMantle()).thenReturn(mantle);
            when(manager.managedTask(anyString(), any(Runnable.class), any(Runnable.class)))
                    .thenAnswer(invocation -> invocation.getArgument(1));
            when(world.isChunkLoaded(2, -1)).thenReturn(true);
            when(world.getGameRuleValue(GameRules.SPAWN_MOBS)).thenReturn(true);
            chunks.when(() -> Chunks.isSafe(world, 2, -1)).thenReturn(true);
            scheduler.when(() -> J.a(any(Runnable.class))).thenAnswer(invocation -> {
                async.add(invocation.getArgument(0));
                return null;
            });
            scheduler.when(() -> J.runRegion(eq(world), eq(2), eq(-1), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        if (acceptRegions) {
                            regions.add(invocation.getArgument(3));
                        }
                        return acceptRegions;
                    });
        }

        private void start(Runnable work) {
            maintenance.runInitialSpawn(world, 2, -1, work);
        }

        @Override
        public void close() {
            while (!async.isEmpty()) {
                async.remove().run();
            }
            chunks.close();
            scheduler.close();
        }
    }
}
