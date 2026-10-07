package art.arcane.iris.world.runtime;

import art.arcane.iris.world.task.J;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class WorldRuntimeControlServiceTeleportTest {
    @Test
    public void scheduledTeleportKeepsTheRequestedDestination() {
        Player player = mock(Player.class);
        Location destination = new Location(null, 12.5D, 70D, -30.5D);
        Location expected = destination.clone();
        AtomicReference<Runnable> task = new AtomicReference<>();
        AtomicReference<Location> received = new AtomicReference<>();
        try (MockedStatic<J> scheduling = mockStatic(J.class)) {
            scheduling.when(() -> J.runEntity(any(Player.class), any(Runnable.class), eq(0), any(Runnable.class))).thenAnswer(invocation -> {
                task.set(invocation.getArgument(1));
                return true;
            });
            CompletableFuture<Boolean> result = WorldRuntimeControlService.scheduleTeleport(player, destination,
                    null, (target, location) -> {
                        received.set(location);
                        return CompletableFuture.completedFuture(false);
                    });
            destination.setX(900D);
            destination.setY(-64D);
            task.get().run();
            assertFalse(result.join());
            assertEquals(expected, received.get());
        }
    }

    @Test
    public void retiredPlayerCompletesTeleportWithoutApplyingGameMode() {
        Player player = mock(Player.class);
        AtomicReference<Runnable> retired = new AtomicReference<>();
        try (MockedStatic<J> scheduling = mockStatic(J.class)) {
            scheduling.when(() -> J.runEntity(any(Player.class), any(Runnable.class), eq(0), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        retired.set(invocation.getArgument(3));
                        return true;
                    });
            CompletableFuture<Boolean> result = WorldRuntimeControlService.scheduleTeleport(player,
                    new Location(null, 1D, 70D, 2D), GameMode.SPECTATOR,
                    (target, location) -> { throw new AssertionError("Retired player was teleported"); });
            retired.get().run();
            assertFalse(result.join());
            verify(player, never()).setGameMode(any());
        }
    }

    @Test
    public void failedModeTeleportRestoresThePreviousGameMode() {
        Player player = mock(Player.class);
        Location destination = mock(Location.class);
        CompletableFuture<Boolean> nativeTeleport = new CompletableFuture<>();
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);

        try (MockedStatic<J> scheduling = immediateEntityScheduling()) {
            CompletableFuture<Boolean> result = WorldRuntimeControlService.scheduleTeleport(
                    player,
                    destination,
                    GameMode.SPECTATOR,
                    (target, location) -> nativeTeleport);
            nativeTeleport.complete(false);

            assertFalse(result.join());
            InOrder gameModes = inOrder(player);
            gameModes.verify(player).setGameMode(GameMode.SPECTATOR);
            gameModes.verify(player).setGameMode(GameMode.SURVIVAL);
        }
    }

    @Test
    public void exceptionalModeTeleportRestoresThePreviousGameMode() {
        Player player = mock(Player.class);
        Location destination = mock(Location.class);
        CompletableFuture<Boolean> nativeTeleport = new CompletableFuture<>();
        when(player.getGameMode()).thenReturn(GameMode.ADVENTURE);

        try (MockedStatic<J> scheduling = immediateEntityScheduling()) {
            CompletableFuture<Boolean> result = WorldRuntimeControlService.scheduleTeleport(
                    player,
                    destination,
                    GameMode.SPECTATOR,
                    (target, location) -> nativeTeleport);
            nativeTeleport.completeExceptionally(new IllegalStateException("teleport failed"));

            assertThrows(CompletionException.class, result::join);
            InOrder gameModes = inOrder(player);
            gameModes.verify(player).setGameMode(GameMode.SPECTATOR);
            gameModes.verify(player).setGameMode(GameMode.ADVENTURE);
        }
    }

    @Test
    public void timedOutModeTeleportCancelsNativeWorkAndRestoresThePreviousGameMode() {
        Player player = mock(Player.class);
        Location destination = mock(Location.class);
        CompletableFuture<Boolean> nativeTeleport = new CompletableFuture<>();
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);

        try (MockedStatic<J> scheduling = immediateEntityScheduling()) {
            CompletableFuture<Boolean> result = WorldRuntimeControlService.scheduleTeleport(
                    player,
                    destination,
                    GameMode.SPECTATOR,
                    (target, location) -> nativeTeleport);
            assertTrue(result.completeExceptionally(new TimeoutException("timed out")));

            assertThrows(CompletionException.class, result::join);
            assertTrue(nativeTeleport.isCancelled());
            InOrder gameModes = inOrder(player);
            gameModes.verify(player).setGameMode(GameMode.SPECTATOR);
            gameModes.verify(player).setGameMode(GameMode.CREATIVE);
        }
    }

    @Test
    public void teleportNeverTouchesThePlayersViewDistance() {
        Player player = mock(Player.class);
        Location destination = mock(Location.class);
        CompletableFuture<Boolean> nativeTeleport = new CompletableFuture<>();
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);

        try (MockedStatic<J> scheduling = immediateEntityScheduling()) {
            CompletableFuture<Boolean> result = WorldRuntimeControlService.scheduleTeleport(
                    player,
                    destination,
                    GameMode.SPECTATOR,
                    (target, location) -> nativeTeleport);
            nativeTeleport.complete(true);

            assertTrue(result.join());
            verify(player).setGameMode(GameMode.SPECTATOR);
            verify(player, never()).setViewDistance(anyInt());
            verify(player, never()).getViewDistance();
        }
    }

    private static MockedStatic<J> immediateEntityScheduling() {
        MockedStatic<J> scheduling = mockStatic(J.class);
        scheduling.when(() -> J.runEntity(any(Player.class), any(Runnable.class), eq(0), any(Runnable.class))).thenAnswer(invocation -> {
            invocation.getArgument(1, Runnable.class).run();
            return true;
        });
        scheduling.when(() -> J.runEntity(any(Player.class), any(Runnable.class))).thenAnswer(invocation -> {
            invocation.getArgument(1, Runnable.class).run();
            return true;
        });
        return scheduling;
    }
}
