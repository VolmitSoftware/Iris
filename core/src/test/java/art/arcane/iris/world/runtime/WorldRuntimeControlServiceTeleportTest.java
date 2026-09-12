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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class WorldRuntimeControlServiceTeleportTest {
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
        scheduling.when(() -> J.runEntity(any(Player.class), any(Runnable.class))).thenAnswer(invocation -> {
            invocation.getArgument(1, Runnable.class).run();
            return true;
        });
        return scheduling;
    }
}
