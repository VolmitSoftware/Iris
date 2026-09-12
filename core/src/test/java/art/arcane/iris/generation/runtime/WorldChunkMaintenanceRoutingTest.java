package art.arcane.iris.generation.runtime;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.world.history.GenerationHistoryRuntimeRouter;
import art.arcane.iris.world.history.GenerationHistoryRuntimeRouter.SavedChunkMantle;
import art.arcane.iris.generation.mantle.EngineMantle;
import art.arcane.iris.platform.generation.EngineBukkitOps;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.testsupport.Await;
import art.arcane.iris.testsupport.PlatformBinding;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.junit.ClassRule;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class WorldChunkMaintenanceRoutingTest {
    @ClassRule
    public static final PlatformBinding PLATFORM = PlatformBinding.mockPlatform();


    @Test
    public void materializesTheSavedOwnerAndKeepsSpawningAfterCompletion() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.schedule();
            verify(fixture.currentMantle, never()).getChunk(2, -1);
            fixture.prepare();

            verify(fixture.savedChunk).use();
            verify(fixture.saved).detachThread();
            verify(fixture.saved, never()).close();
            verify(fixture.savedChunk, never()).release();

            fixture.apply();
            fixture.awaitRelease();

            fixture.updates.verify(() -> EngineBukkitOps.updateChunk(fixture.engine, fixture.chunk, fixture.savedMantle));
            InOrder lifetime = inOrder(fixture.savedChunk, fixture.saved);
            lifetime.verify(fixture.savedChunk).use();
            lifetime.verify(fixture.saved).detachThread();
            lifetime.verify(fixture.savedChunk).release();
            lifetime.verify(fixture.saved).close();
            assertNotSame(Thread.currentThread(), fixture.releaseThread.get());
            assertFalse(fixture.scoped.get());

            fixture.schedule();
            assertTrue(fixture.async.isEmpty());
            verify(fixture.router).openSavedChunkMantle(2, -1);
            verify(fixture.spawner, times(2)).spawnInitially(fixture.chunk);
        }
    }

    @Test
    public void managerCloseReleasesQueuedStorageWithoutRunningItsRegionCallback() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.schedule();
            fixture.prepare();

            fixture.maintenance.close();
            fixture.awaitRelease();
            fixture.apply();
            fixture.schedule();

            fixture.updates.verifyNoInteractions();
            verify(fixture.saved).close();
            verify(fixture.spawner, never()).spawnInitially(fixture.chunk);
            assertTrue(fixture.async.isEmpty());
        }
    }

    @Test
    public void managerCloseDuringPreparationReleasesLocallyHeldStorage() throws Exception {
        try (Fixture fixture = new Fixture()) {
            when(fixture.savedMantle.getChunk(2, -1)).thenAnswer(invocation -> {
                fixture.maintenance.close();
                return fixture.savedChunk;
            });
            fixture.schedule();

            fixture.prepare();
            fixture.awaitRelease();

            fixture.updates.verifyNoInteractions();
            assertTrue(fixture.regions.isEmpty());
            verify(fixture.savedChunk).release();
            verify(fixture.saved).close();
        }
    }

    @Test
    public void rejectedRegionSchedulingReleasesStorageAndAllowsRetry() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.acceptRegion.set(false);
            fixture.schedule();
            fixture.prepare();
            fixture.awaitRelease();

            fixture.acceptRegion.set(true);
            fixture.schedule();
            assertEquals(1, fixture.async.size());
            fixture.updates.verifyNoInteractions();
            verify(fixture.savedChunk).release();
        }
    }

    @Test
    public void rejectedLifecycleAdmissionReleasesPreparedStorage() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.schedule();
            fixture.prepare();
            fixture.admitted.set(false);

            fixture.apply();
            fixture.awaitRelease();

            fixture.updates.verifyNoInteractions();
            verify(fixture.saved).close();
            verify(fixture.savedChunk).release();
        }
    }

    @Test
    public void unloadedChunkDoesNotBecomeCompleted() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.schedule();
            fixture.prepare();
            when(fixture.world.isChunkLoaded(2, -1)).thenReturn(false);

            fixture.apply();
            fixture.awaitRelease();

            when(fixture.world.isChunkLoaded(2, -1)).thenReturn(true);
            fixture.schedule();
            assertEquals(1, fixture.async.size());
            fixture.updates.verifyNoInteractions();
        }
    }

    @Test
    public void failedMaterializationRetainsRetryAndReportsTheFailure() throws Exception {
        try (Fixture fixture = new Fixture()) {
            IllegalStateException failure = new IllegalStateException("Provider placement failed");
            fixture.updates.when(() -> EngineBukkitOps.updateChunk(fixture.engine, fixture.chunk, fixture.savedMantle)).thenThrow(failure);
            fixture.schedule();
            fixture.prepare();

            fixture.apply();
            fixture.awaitRelease();

            fixture.logging.verify(() -> IrisLogging.reportError(anyString(), eq(failure)));
            fixture.schedule();
            assertEquals(1, fixture.async.size());
            verify(fixture.spawner, never()).spawnInitially(fixture.chunk);
        }
    }

    @Test
    public void skippedMaterializationRetainsRetryWithoutSuppressingSpawning() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.updates.when(() -> EngineBukkitOps.updateChunk(fixture.engine, fixture.chunk, fixture.savedMantle))
                    .thenAnswer(invocation -> null);
            fixture.schedule();
            fixture.prepare();

            fixture.apply();
            fixture.awaitRelease();

            fixture.schedule();
            assertEquals(1, fixture.async.size());
            verify(fixture.spawner).spawnInitially(fixture.chunk);
        }
    }

    @Test
    public void regeneratedChunkInvalidatesCompletedMaterialization() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.schedule();
            fixture.prepare();
            fixture.apply();
            fixture.awaitRelease();

            fixture.maintenance.invalidateMaterialization(2, -1);
            fixture.schedule();

            assertEquals(1, fixture.async.size());
        }
    }

    @Test
    public void pendingMaterializationIsBoundedAndCancelledBeforePreparation() throws Exception {
        try (Fixture fixture = new Fixture()) {
            for (int x = 0; x < 256; x++) {
                fixture.maintenance.updateChunkRegion(fixture.world, x, 0);
            }
            assertEquals(128, fixture.async.size());

            fixture.maintenance.close();
            while (!fixture.async.isEmpty()) {
                fixture.prepare();
            }

            verify(fixture.router, never()).openSavedChunkMantle(anyInt(), anyInt());
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final IrisEngine engine = mock(IrisEngine.class);
        private final IrisWorldManager manager = mock(IrisWorldManager.class);
        private final GenerationHistoryRuntimeRouter router = mock(GenerationHistoryRuntimeRouter.class);
        private final SavedChunkMantle saved = mock(SavedChunkMantle.class);
        private final SavedChunkMantle.Scope scope = mock(SavedChunkMantle.Scope.class);
        private final EngineMantle currentEngineMantle = mock(EngineMantle.class);
        private final Mantle<Matter> currentMantle;
        private final Mantle<Matter> savedMantle;
        private final MantleChunk<Matter> savedChunk;
        private final WorldEntitySpawner spawner = mock(WorldEntitySpawner.class);
        private final World world = mock(World.class);
        private final Chunk chunk = mock(Chunk.class);
        private final WorldChunkMaintenance maintenance = new WorldChunkMaintenance(manager);
        private final Map<?, ?> pending;
        private final Queue<Runnable> async = new ArrayDeque<>();
        private final Queue<Runnable> regions = new ArrayDeque<>();
        private final AtomicBoolean etched = new AtomicBoolean();
        private final AtomicBoolean scoped = new AtomicBoolean();
        private final AtomicBoolean acceptRegion = new AtomicBoolean(true);
        private final AtomicBoolean admitted = new AtomicBoolean(true);
        private final AtomicReference<Thread> releaseThread = new AtomicReference<>();
        private final Semaphore released = new Semaphore(0);
        private final MockedStatic<IrisSettings> configured = mockStatic(IrisSettings.class);
        private final MockedStatic<J> scheduling = mockStatic(J.class);
        private final MockedStatic<EngineBukkitOps> updates = mockStatic(EngineBukkitOps.class);
        private final MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class);

        @SuppressWarnings("unchecked")
        private Fixture() throws Exception {
            currentMantle = mock(Mantle.class);
            savedMantle = mock(Mantle.class);
            savedChunk = mock(MantleChunk.class);
            Field pendingField = WorldChunkMaintenance.class.getDeclaredField("materializations");
            pendingField.setAccessible(true);
            pending = (Map<?, ?>) pendingField.get(maintenance);
            Field field = IrisWorldManager.class.getDeclaredField("entitySpawner");
            field.setAccessible(true);
            field.set(manager, spawner);
            when(manager.getEngine()).thenReturn(engine);
            when(engine.getGenerationHistoryRuntimeRouter()).thenReturn(Optional.of(router));
            when(manager.getMantle()).thenReturn(currentMantle);
            when(engine.getMantle()).thenReturn(currentEngineMantle);
            when(currentEngineMantle.getMantle()).thenReturn(currentMantle);
            when(saved.mantle()).thenReturn(savedMantle);
            when(savedMantle.getChunk(2, -1)).thenReturn(savedChunk);
            when(savedChunk.use()).thenReturn(savedChunk);
            when(savedMantle.isChunkLoaded(2, -1)).thenReturn(true);
            when(savedMantle.hasFlag(2, -1, MantleFlag.ETCHED)).thenAnswer(invocation -> etched.get());
            when(router.openSavedChunkMantle(2, -1)).thenReturn(saved);
            when(saved.openScope()).thenAnswer(invocation -> {
                assertFalse(scoped.getAndSet(true));
                return scope;
            });
            doAnswer(invocation -> {
                assertTrue(scoped.getAndSet(false));
                return null;
            }).when(scope).close();
            doAnswer(invocation -> {
                releaseThread.set(Thread.currentThread());
                released.release();
                return null;
            }).when(saved).close();
            when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
            when(world.getChunkAt(anyInt(), anyInt())).thenReturn(chunk);
            when(spawner.isEntitySpawningEnabledForCurrentWorld()).thenReturn(true);
            when(manager.managedTask(anyString(), any(Runnable.class), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        Runnable task = invocation.getArgument(1);
                        Runnable rejected = invocation.getArgument(2);
                        return (Runnable) () -> {
                            if (admitted.get()) {
                                task.run();
                            } else {
                                rejected.run();
                            }
                        };
                    });
            IrisSettings settings = new IrisSettings();
            settings.getWorld().setPostLoadBlockUpdates(true);
            configured.when(IrisSettings::get).thenReturn(settings);
            scheduling.when(() -> J.a(any(Runnable.class))).thenAnswer(invocation -> {
                async.add(invocation.getArgument(0));
                return null;
            });
            scheduling.when(() -> J.runRegion(eq(world), eq(2), eq(-1), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        if (!acceptRegion.get()) {
                            return false;
                        }
                        regions.add(invocation.getArgument(3));
                        return true;
                    });
            updates.when(() -> EngineBukkitOps.updateChunk(engine, chunk, savedMantle)).thenAnswer(invocation -> {
                assertTrue(scoped.get());
                assertSame(currentMantle, engine.getMantle().getMantle());
                etched.set(true);
                return null;
            });
        }

        private void schedule() {
            maintenance.updateChunkRegion(world, 2, -1);
        }

        private void prepare() {
            async.remove().run();
        }

        private void apply() {
            regions.remove().run();
        }

        private void awaitRelease() throws InterruptedException {
            assertTrue("Saved mantle handle was not released", released.tryAcquire(5L, TimeUnit.SECONDS));
            Await.reached("the saved mantle cleanup to finish", Duration.ofSeconds(5L), pending::isEmpty);
            assertTrue("Saved mantle cleanup did not finish", pending.isEmpty());
        }

        @Override
        public void close() {
            maintenance.close();
            logging.close();
            updates.close();
            scheduling.close();
            configured.close();
        }
    }
}
