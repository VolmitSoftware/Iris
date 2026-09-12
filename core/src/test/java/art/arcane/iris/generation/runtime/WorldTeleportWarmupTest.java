package art.arcane.iris.generation.runtime;

import art.arcane.iris.platform.bukkit.BukkitPlatform;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class WorldTeleportWarmupTest {
    @Test
    public void cancelledTeleportUsesNativeAsyncPathWithOriginalCause() {
        World world = mock(World.class);
        Player player = mock(Player.class);
        PlayerTeleportEvent event = mock(PlayerTeleportEvent.class);
        Location destination = new Location(world, 400.5D, 96D, 2.5D, 45F, 10F);
        PlayerTeleportEvent.TeleportCause cause = PlayerTeleportEvent.TeleportCause.COMMAND;
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        AtomicReference<Location> capturedDestination = new AtomicReference<>();

        when(event.getPlayer()).thenReturn(player);
        when(event.getTo()).thenReturn(destination);
        when(event.getCause()).thenReturn(cause);

        try (MockedStatic<BukkitPlatform> platform = mockStatic(BukkitPlatform.class)) {
            platform.when(() -> BukkitPlatform.teleportAsync(same(player), any(Location.class), eq(cause)))
                    .thenAnswer(invocation -> {
                        capturedDestination.set(invocation.getArgument(1, Location.class));
                        return result;
                    });

            new WorldTeleportWarmup().teleportAsync(event);

            verify(event).setCancelled(true);
            assertEquals(destination, capturedDestination.get());
            assertNotSame(destination, capturedDestination.get());
        }
    }

    @Test
    public void missingDestinationLeavesTeleportUntouched() {
        PlayerTeleportEvent event = mock(PlayerTeleportEvent.class);
        when(event.getTo()).thenReturn(null);

        try (MockedStatic<BukkitPlatform> platform = mockStatic(BukkitPlatform.class)) {
            new WorldTeleportWarmup().teleportAsync(event);

            verify(event, never()).setCancelled(true);
            platform.verifyNoInteractions();
        }
    }

    @Test
    public void falseNativeSettlementDoesNotThrowFromCompletion() {
        World world = mock(World.class);
        Player player = mock(Player.class);
        PlayerTeleportEvent event = mock(PlayerTeleportEvent.class);
        Location destination = new Location(world, 0.5D, 80D, 0.5D);
        when(event.getPlayer()).thenReturn(player);
        when(event.getTo()).thenReturn(destination);
        when(event.getCause()).thenReturn(PlayerTeleportEvent.TeleportCause.COMMAND);
        when(player.getName()).thenReturn("Player");

        try (MockedStatic<BukkitPlatform> platform = mockStatic(BukkitPlatform.class)) {
            platform.when(() -> BukkitPlatform.teleportAsync(
                            same(player),
                            any(Location.class),
                            eq(PlayerTeleportEvent.TeleportCause.COMMAND)))
                    .thenReturn(CompletableFuture.completedFuture(false));

            new WorldTeleportWarmup().teleportAsync(event);

            verify(event).setCancelled(true);
        }
    }

    @Test
    public void missingNativeFutureDoesNotThrowAfterCancellation() {
        World world = mock(World.class);
        Player player = mock(Player.class);
        PlayerTeleportEvent event = mock(PlayerTeleportEvent.class);
        Location destination = new Location(world, 0.5D, 80D, 0.5D);
        when(event.getPlayer()).thenReturn(player);
        when(event.getTo()).thenReturn(destination);
        when(event.getCause()).thenReturn(PlayerTeleportEvent.TeleportCause.COMMAND);
        when(player.getName()).thenReturn("Player");

        try (MockedStatic<BukkitPlatform> platform = mockStatic(BukkitPlatform.class)) {
            platform.when(() -> BukkitPlatform.teleportAsync(
                            same(player),
                            any(Location.class),
                            eq(PlayerTeleportEvent.TeleportCause.COMMAND)))
                    .thenReturn(null);

            new WorldTeleportWarmup().teleportAsync(event);

            verify(event).setCancelled(true);
        }
    }
}
