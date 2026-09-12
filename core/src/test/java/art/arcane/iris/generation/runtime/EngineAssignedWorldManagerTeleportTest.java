package art.arcane.iris.generation.runtime;

import art.arcane.iris.world.IrisWorld;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.platform.bukkit.BukkitWorldBinding;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class EngineAssignedWorldManagerTeleportTest {
    @Test
    public void unloadedIrisDestinationDelegatesToAsyncTeleport() {
        TeleportHarness harness = new TeleportHarness(false, true);

        try (harness) {
            harness.manager.on(harness.event);

            assertSame(harness.event, harness.manager.teleportEvent);
        }
    }

    @Test
    public void commandTeleportStillConvertsDuringSealedStudioCutover() throws Exception {
        ExecutorService reloader = Executors.newSingleThreadExecutor();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (TeleportHarness harness = new TeleportHarness(false, true)) {
            when(harness.engine.isClosing()).thenReturn(true);
            when(harness.engine.isShuttingDown()).thenReturn(false);
            Location destination = harness.event.getTo();
            Future<?> cutover = reloader.submit(() -> {
                try (GenerationTransitionGate.Transition ignored = harness.sessions.transitionGate().beginTransition(2_000L)) {
                    harness.sessions.sealAndAwait("Studio generation cutover", 2_000L);
                    entered.countDown();
                    assertTrue(release.await(2L, TimeUnit.SECONDS));
                    harness.sessions.activateNextSession();
                } catch (Exception failure) {
                    throw new IllegalStateException(failure);
                }
            });
            assertTrue(entered.await(2L, TimeUnit.SECONDS));

            harness.manager.on(harness.event);

            assertSame(harness.event, harness.manager.teleportEvent);
            assertSame(destination, harness.manager.teleportEvent.getTo());
            assertEquals(PlayerTeleportEvent.TeleportCause.COMMAND, harness.manager.teleportEvent.getCause());
            assertEquals(0, harness.sessions.activeLeases());
            release.countDown();
            cutover.get(2L, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            reloader.shutdownNow();
            assertTrue(reloader.awaitTermination(2L, TimeUnit.SECONDS));
        }
    }

    @Test
    public void closingStudioCancelsCommandInsteadOfFallingThroughToVanilla() {
        try (TeleportHarness harness = new TeleportHarness(false, true)) {
            when(harness.engine.isClosing()).thenReturn(true);
            when(harness.engine.isShuttingDown()).thenReturn(true);

            harness.manager.on(harness.event);

            verify(harness.event).setCancelled(true);
            assertNull(harness.manager.teleportEvent);
        }
    }

    @Test
    public void closedStudioCancelsEvenAnAlreadyLoadedDestination() {
        try (TeleportHarness harness = new TeleportHarness(true, true)) {
            when(harness.engine.isClosed()).thenReturn(true);
            when(harness.engine.isShuttingDown()).thenReturn(true);

            harness.manager.on(harness.event);

            verify(harness.event).setCancelled(true);
            assertNull(harness.manager.teleportEvent);
        }
    }

    @Test
    public void loadedIrisDestinationContinuesWithoutInterception() {
        TeleportHarness harness = new TeleportHarness(true, true);

        try (harness) {
            harness.manager.on(harness.event);

            assertNull(harness.manager.teleportEvent);
        }
    }

    @Test
    public void classicBukkitDestinationContinuesWithoutInterception() {
        TeleportHarness harness = new TeleportHarness(false, false);

        try (harness) {
            harness.manager.on(harness.event);

            assertNull(harness.manager.teleportEvent);
        }
    }

    @Test
    public void pluginTeleportContinuesWithoutInterception() {
        TeleportHarness harness = new TeleportHarness(false, true);
        when(harness.event.getCause()).thenReturn(PlayerTeleportEvent.TeleportCause.PLUGIN);

        try (harness) {
            harness.manager.on(harness.event);

            assertNull(harness.manager.teleportEvent);
        }
    }

    @Test
    public void productionWorldCommandContinuesWithoutInterception() {
        TeleportHarness harness = new TeleportHarness(false, true);
        when(harness.engine.isStudio()).thenReturn(false);

        try (harness) {
            harness.manager.on(harness.event);

            assertNull(harness.manager.teleportEvent);
        }
    }

    @Test
    public void otherWorldDestinationContinuesWithoutInterception() {
        TeleportHarness harness = new TeleportHarness(false, true);
        World otherWorld = mock(World.class);
        when(harness.engine.isClosing()).thenReturn(true);
        when(harness.engine.isShuttingDown()).thenReturn(true);
        when(harness.event.getTo()).thenReturn(new Location(otherWorld, 400.5D, 96D, 2.5D));

        try (harness) {
            harness.manager.on(harness.event);

            assertNull(harness.manager.teleportEvent);
            verify(harness.event, never()).setCancelled(true);
        }
    }

    private static final class TeleportHarness implements AutoCloseable {
        private final GenerationSessionManager sessions = new GenerationSessionManager(true);
        private final Engine engine;
        private final TestWorldManager manager;
        private final PlayerTeleportEvent event;
        private final MockedStatic<BukkitPlatform> platform;
        private final MockedStatic<BukkitWorldBinding> binding;

        private TeleportHarness(boolean loaded, boolean paper) {
            engine = mock(Engine.class);
            EngineTarget target = mock(EngineTarget.class);
            IrisWorld irisWorld = mock(IrisWorld.class);
            World world = mock(World.class);
            event = mock(PlayerTeleportEvent.class);
            Location destination = new Location(world, 400.5D, 96D, 2.5D);

            when(engine.getTarget()).thenReturn(target);
            when(engine.isStudio()).thenReturn(true);
            when(engine.getGenerationSessions()).thenReturn(sessions);
            when(target.getWorld()).thenReturn(irisWorld);
            when(event.getTo()).thenReturn(destination);
            when(event.getCause()).thenReturn(PlayerTeleportEvent.TeleportCause.COMMAND);
            when(world.isChunkLoaded(25, 0)).thenReturn(loaded);

            platform = mockStatic(BukkitPlatform.class);
            platform.when(BukkitPlatform::isPaperServer).thenReturn(paper);
            binding = mockStatic(BukkitWorldBinding.class);
            binding.when(() -> BukkitWorldBinding.world(irisWorld)).thenReturn(world);
            manager = new TestWorldManager(engine);
            manager.start();
        }

        @Override
        public void close() {
            manager.close();
            binding.close();
            platform.close();
        }
    }

    private static final class TestWorldManager extends EngineAssignedWorldManager {
        private PlayerTeleportEvent teleportEvent;

        private TestWorldManager(Engine engine) {
            super(engine);
        }

        @Override
        protected void registerManagerListener() {
        }

        @Override
        protected int scheduleManagerTick(Runnable tick) {
            return 0;
        }

        @Override
        protected void unregisterManagerListener() {
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
            teleportEvent = event;
        }
    }
}
