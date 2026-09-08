package art.arcane.iris.engine;

import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineAssignedWorldManager;
import art.arcane.iris.engine.framework.EngineEffects;
import art.arcane.iris.engine.framework.EngineEffectsProvider;
import art.arcane.iris.engine.framework.EngineTarget;
import art.arcane.iris.engine.framework.EngineWorldManagerProvider;
import art.arcane.iris.engine.framework.GenerationSessionManager;
import art.arcane.iris.engine.framework.GenerationTransitionGate;
import art.arcane.iris.engine.object.IrisWorld;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.platform.bukkit.BukkitWorldBinding;
import art.arcane.iris.spi.IrisServices;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class EngineRuntimeBuilderStudioServiceTest {
    @Test
    public void replacementInterceptsCommandsWhileOldManagerRetires() throws Exception {
        ExecutorService owner = Executors.newSingleThreadExecutor();
        try (Fixture fixture = new Fixture()) {
            CompletableFuture<Boolean> teleport = new CompletableFuture<>();
            AtomicInteger requests = new AtomicInteger();
            fixture.previous.afterUnregister = () -> {
                try {
                    owner.submit(() -> fixture.teleportDuringRetirement(teleport, requests))
                            .get(2L, TimeUnit.SECONDS);
                } catch (Exception failure) {
                    throw new IllegalStateException(failure);
                }
            };

            try (GenerationTransitionGate.Transition ignored = fixture.sessions.transitionGate().beginTransition(2_000L)) {
                fixture.sessions.sealAndAwait("Studio generation cutover", 2_000L);
                fixture.builder.refreshStudioRuntimeServices();
            }

            assertSame(fixture.replacement, fixture.engine.runtime.worldManager());
            assertEquals(List.of(fixture.replacement), fixture.registered);
            assertEquals(1, requests.get());
            assertFalse(teleport.isDone());
            assertTrue(teleport.complete(true));
            fixture.previous.tick.run();
            fixture.replacement.tick.run();
            assertEquals(0, fixture.previous.updates);
            assertEquals(1, fixture.replacement.updates);
            verify(fixture.previousEffects).close();
            verify(fixture.nextEffects, never()).close();
        } finally {
            owner.shutdownNow();
            assertTrue(owner.awaitTermination(2L, TimeUnit.SECONDS));
        }
    }

    @Test
    public void failedReplacementStartKeepsThePreviousListenerRegistered() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.replacement.failRegistration = true;
            EngineRuntime previous = fixture.engine.runtime;
            try (GenerationTransitionGate.Transition ignored = fixture.sessions.transitionGate().beginTransition(2_000L)) {
                fixture.sessions.sealAndAwait("Studio generation cutover", 2_000L);

                assertThrows(IllegalStateException.class, fixture.builder::refreshStudioRuntimeServices);
            }

            assertSame(previous, fixture.engine.runtime);
            assertEquals(List.of(fixture.previous), fixture.registered);
            assertEquals(0, fixture.previous.closes);
            assertEquals(1, fixture.replacement.closes);
            assertEquals(1L, fixture.sessions.currentSessionId());
            verify(fixture.previousEffects, never()).close();
            verify(fixture.nextEffects).close();
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final IrisEngine engine = mock(IrisEngine.class);
        private final GenerationSessionManager sessions = new GenerationSessionManager(true);
        private final AtomicBoolean closing = new AtomicBoolean(true);
        private final List<TestWorldManager> registered = new CopyOnWriteArrayList<>();
        private final EngineEffects previousEffects = mock(EngineEffects.class);
        private final EngineEffects nextEffects = mock(EngineEffects.class);
        private final IrisWorld irisWorld = mock(IrisWorld.class);
        private final World world = mock(World.class);
        private final TestWorldManager previous;
        private final TestWorldManager replacement;
        private final EngineRuntimeBuilder builder;
        private final MockedStatic<IrisServices> services;

        private Fixture() throws Exception {
            EngineTarget target = mock(EngineTarget.class);
            when(engine.getTarget()).thenReturn(target);
            when(target.getWorld()).thenReturn(irisWorld);
            when(engine.isStudio()).thenReturn(true);
            when(engine.isClosing()).thenAnswer(invocation -> closing.get());
            when(engine.isShuttingDown()).thenCallRealMethod();
            when(engine.getClosing()).thenReturn(closing);
            when(engine.getGenerationSessions()).thenReturn(sessions);
            engine.lifecycleState = IrisEngine.LifecycleState.HOTLOADING;
            EngineBackgroundTasks background = mock(EngineBackgroundTasks.class);
            when(background.scheduleTrackedTask(any(Runnable.class))).thenReturn(true);
            Field backgroundField = IrisEngine.class.getDeclaredField("backgroundTasks");
            backgroundField.setAccessible(true);
            backgroundField.set(engine, background);
            previous = new TestWorldManager(engine, registered);
            replacement = new TestWorldManager(engine, registered);
            engine.runtime = new EngineRuntime(mock(GenerationRuntime.class), previousEffects, previous);
            previous.start();
            builder = new EngineRuntimeBuilder(engine);
            EngineEffectsProvider effectsProvider = mock(EngineEffectsProvider.class);
            EngineWorldManagerProvider managerProvider = mock(EngineWorldManagerProvider.class);
            when(effectsProvider.create(engine)).thenReturn(nextEffects);
            when(managerProvider.create(engine)).thenReturn(replacement);
            services = mockStatic(IrisServices.class);
            services.when(() -> IrisServices.get(EngineEffectsProvider.class)).thenReturn(effectsProvider);
            services.when(() -> IrisServices.get(EngineWorldManagerProvider.class)).thenReturn(managerProvider);
        }

        private void teleportDuringRetirement(CompletableFuture<Boolean> teleport, AtomicInteger requests) {
            Player player = mock(Player.class);
            Location destination = new Location(world, 1024.5D, 309D, 0.5D, 45F, 10F);
            PlayerTeleportEvent event = new PlayerTeleportEvent(player,
                    new Location(world, 0.5D, 100D, 0.5D), destination,
                    PlayerTeleportEvent.TeleportCause.COMMAND);
            try (MockedStatic<BukkitPlatform> platform = mockStatic(BukkitPlatform.class);
                 MockedStatic<BukkitWorldBinding> binding = mockStatic(BukkitWorldBinding.class)) {
                platform.when(BukkitPlatform::isPaperServer).thenReturn(true);
                binding.when(() -> BukkitWorldBinding.world(irisWorld)).thenReturn(world);
                platform.when(() -> BukkitPlatform.teleportAsync(same(player), any(Location.class),
                                eq(PlayerTeleportEvent.TeleportCause.COMMAND)))
                        .thenAnswer(invocation -> {
                            assertTrue(event.isCancelled());
                            Location requested = invocation.getArgument(1, Location.class);
                            assertEquals(destination, requested);
                            assertNotSame(destination, requested);
                            requests.incrementAndGet();
                            return teleport;
                        });
                assertEquals(List.of(replacement), registered);
                for (TestWorldManager manager : registered) {
                    if (!event.isCancelled()) {
                        manager.on(event);
                    }
                }
                assertTrue(event.isCancelled());
                previous.tick.run();
                replacement.tick.run();
                assertEquals(0, previous.updates);
                assertEquals(0, replacement.updates);
                assertEquals(0, sessions.activeLeases());
            }
        }

        @Override
        public void close() {
            previous.afterUnregister = null;
            previous.close();
            replacement.close();
            services.close();
        }
    }

    private static final class TestWorldManager extends EngineAssignedWorldManager {
        private final List<TestWorldManager> registered;
        private final WorldTeleportWarmup warmup = new WorldTeleportWarmup();
        private Runnable tick;
        private Runnable afterUnregister;
        private boolean failRegistration;
        private int updates;
        private int closes;

        private TestWorldManager(Engine engine, List<TestWorldManager> registered) {
            super(engine);
            this.registered = registered;
        }

        @Override
        public void close() {
            closes++;
            super.close();
        }

        @Override
        protected void registerManagerListener() {
            registered.add(this);
            if (failRegistration) {
                throw new IllegalStateException("listener registration failed");
            }
        }

        @Override
        protected void unregisterManagerListener() {
            registered.remove(this);
            if (afterUnregister != null) {
                afterUnregister.run();
            }
        }

        @Override
        protected int scheduleManagerTick(Runnable tick) {
            this.tick = tick;
            return 42;
        }

        @Override
        protected void cancelManagerTick(int scheduledTaskId) {
        }

        @Override
        public int getEntityCount() {
            return 0;
        }

        @Override
        public int getChunkCount() {
            return 0;
        }

        @Override
        public double getEntitySaturation() {
            return 0D;
        }

        @Override
        public void onTick() {
            updates++;
        }

        @Override
        public void onSave() {
        }

        @Override
        public void onBlockBreak(BlockBreakEvent event) {
        }

        @Override
        public void onBlockPlace(BlockPlaceEvent event) {
        }

        @Override
        public void onChunkLoad(Chunk chunk, boolean generated) {
        }

        @Override
        public void onChunkUnload(Chunk chunk) {
        }

        @Override
        public void teleportAsync(PlayerTeleportEvent event) {
            warmup.teleportAsync(event);
        }
    }
}
