package art.arcane.iris.studio.view;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.world.IrisWorld;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.platform.bukkit.BukkitWorldBinding;
import art.arcane.iris.world.task.J;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BukkitVisionOverlayFoliaContractTest {
    @Test
    public void teleportDelegatesImmediatelyToTheNativeAsyncPath() {
        VisionHarness harness = new VisionHarness();

        try (harness) {
            harness.overlay.teleport(33.5D, 33.5D);

            assertEquals(1, harness.destinations.size());
            Location destination = harness.destinations.get(0);
            assertEquals(33.5D, destination.getX(), 0D);
            assertEquals(76D, destination.getY(), 0D);
            assertEquals(33.5D, destination.getZ(), 0D);
            verify(harness.engine).getHeight(33, 33, false);
        }
    }

    @Test
    public void teleportFloorsNegativeCoordinatesBeforeCenteringTheDestination() {
        VisionHarness harness = new VisionHarness();

        try (harness) {
            harness.overlay.teleport(-0.25D, -16.01D);

            assertEquals(1, harness.destinations.size());
            Location destination = harness.destinations.get(0);
            assertEquals(-0.5D, destination.getX(), 0D);
            assertEquals(-16.5D, destination.getZ(), 0D);
            verify(harness.engine).getHeight(-1, -17, false);
        }
    }

    @Test
    public void latestRequestRunsAfterAnOlderNativeTeleportSettles() {
        VisionHarness harness = new VisionHarness();
        CompletableFuture<Boolean> firstTeleport = new CompletableFuture<>();
        CompletableFuture<Boolean> secondTeleport = new CompletableFuture<>();
        harness.nativeTeleports.add(firstTeleport);
        harness.nativeTeleports.add(secondTeleport);

        try (harness) {
            harness.overlay.teleport(1.5D, 1.5D);
            harness.overlay.teleport(33.5D, 33.5D);
            assertEquals(1, harness.destinations.size());

            firstTeleport.complete(true);
            assertEquals(2, harness.destinations.size());
            assertEquals(33, harness.destinations.get(1).getBlockX());
            assertEquals(33, harness.destinations.get(1).getBlockZ());
            secondTeleport.complete(true);
        }
    }

    @Test
    public void missingOpenerDoesNotTeleportAnotherPlayer() {
        VisionHarness harness = new VisionHarness();
        harness.binding.when(() -> BukkitWorldBinding.players(harness.target))
                .thenReturn(List.of(harness.otherPlayer));

        try (harness) {
            harness.overlay.teleport(1.5D, 1.5D);

            assertEquals(0, harness.destinations.size());
        }
    }

    private static final class VisionHarness implements AutoCloseable {
        private final Engine engine;
        private final IrisWorld target;
        private final World world;
        private final Player player;
        private final Player otherPlayer;
        private final UUID openerId;
        private final MockedStatic<J> scheduling;
        private final MockedStatic<BukkitWorldBinding> binding;
        private final MockedStatic<BukkitPlatform> platform;
        private final List<Location> destinations;
        private final List<CompletableFuture<Boolean>> nativeTeleports;
        private final AtomicInteger nativeTeleportIndex;
        private final BukkitVisionOverlay overlay;

        private VisionHarness() {
            engine = mock(Engine.class);
            target = mock(IrisWorld.class);
            world = mock(World.class);
            player = mock(Player.class);
            otherPlayer = mock(Player.class);
            openerId = UUID.randomUUID();
            destinations = new ArrayList<>();
            nativeTeleports = new ArrayList<>();
            nativeTeleportIndex = new AtomicInteger();

            when(engine.getWorld()).thenReturn(target);
            when(engine.getMinHeight()).thenReturn(-64);
            when(engine.getHeight(anyInt(), anyInt(), eq(false))).thenReturn(138);
            when(target.hasPlatformWorld()).thenReturn(true);
            when(player.isOnline()).thenReturn(true);
            when(player.getWorld()).thenReturn(world);
            when(player.getUniqueId()).thenReturn(openerId);
            when(otherPlayer.getUniqueId()).thenReturn(UUID.randomUUID());

            scheduling = mockStatic(J.class);
            scheduling.when(() -> J.runGlobal(any(Runnable.class))).thenAnswer(invocation -> {
                invocation.getArgument(0, Runnable.class).run();
                return true;
            });
            scheduling.when(() -> J.runEntity(same(player), any(Runnable.class))).thenAnswer(invocation -> {
                invocation.getArgument(1, Runnable.class).run();
                return true;
            });

            binding = mockStatic(BukkitWorldBinding.class);
            binding.when(() -> BukkitWorldBinding.world(target)).thenReturn(world);
            binding.when(() -> BukkitWorldBinding.players(target)).thenReturn(List.of(otherPlayer, player));

            platform = mockStatic(BukkitPlatform.class);
            platform.when(() -> BukkitPlatform.teleportAsync(same(player), any(Location.class)))
                    .thenAnswer(invocation -> {
                        destinations.add(invocation.getArgument(1, Location.class));
                        int index = nativeTeleportIndex.getAndIncrement();
                        return index < nativeTeleports.size()
                                ? nativeTeleports.get(index)
                                : CompletableFuture.completedFuture(true);
                    });
            overlay = new BukkitVisionOverlay(engine, openerId);
        }

        @Override
        public void close() {
            platform.close();
            binding.close();
            scheduling.close();
        }
    }
}
