package art.arcane.iris.world;

import art.arcane.iris.pack.datapack.ServerConfigurator;
import art.arcane.iris.world.runtime.WorldRuntimeControlService;
import art.arcane.iris.platform.bukkit.plugin.VolmitSender;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class IrisCreatorTeleportTest {
    @Test
    public void createTeleportTarget_onlySelectsProductionPlayerSender() {
        Player player = mock(Player.class);
        VolmitSender playerSender = mock(VolmitSender.class);
        VolmitSender consoleSender = mock(VolmitSender.class);
        doReturn(true).when(playerSender).isPlayer();
        doReturn(player).when(playerSender).player();
        doReturn(false).when(consoleSender).isPlayer();

        assertSame(player, IrisCreator.createTeleportTarget(playerSender, false, false));
        assertNull(IrisCreator.createTeleportTarget(consoleSender, false, false));
        assertNull(IrisCreator.createTeleportTarget(playerSender, true, false));
        assertNull(IrisCreator.createTeleportTarget(playerSender, false, true));
    }

    @Test
    public void teleportSenderToCreatedWorld_loadsAndResolvesSafeEntryBeforeTeleport() {
        Player player = mock(Player.class);
        World world = mock(World.class);
        Chunk chunk = mock(Chunk.class);
        WorldRuntimeControlService runtimeControl = mock(WorldRuntimeControlService.class);
        Location anchor = new Location(world, 32.5D, 80D, -15.5D);
        Location safeEntry = new Location(world, 33.5D, 91D, -14.5D);
        doReturn(anchor).when(runtimeControl).resolveEntryAnchor(world);
        doReturn(CompletableFuture.completedFuture(chunk))
                .when(runtimeControl).requestChunkAsync(world, 2, -1, true);
        doReturn(CompletableFuture.completedFuture(safeEntry))
                .when(runtimeControl).resolveSafeEntry(world, anchor);
        doReturn(CompletableFuture.completedFuture(true))
                .when(runtimeControl).teleport(player, safeEntry);

        CompletableFuture<Boolean> result = IrisCreator.teleportSenderToCreatedWorld(player, world, runtimeControl);

        assertTrue(result.join());
        InOrder order = inOrder(runtimeControl);
        order.verify(runtimeControl).resolveEntryAnchor(world);
        order.verify(runtimeControl).requestChunkAsync(world, 2, -1, true);
        order.verify(runtimeControl).resolveSafeEntry(world, anchor);
        order.verify(runtimeControl).teleport(player, safeEntry);
        verifyNoMoreInteractions(runtimeControl);
    }

    @Test
    public void teleportSenderToCreatedWorld_preservesFalseTeleportResult() {
        Player player = mock(Player.class);
        World world = mock(World.class);
        WorldRuntimeControlService runtimeControl = mock(WorldRuntimeControlService.class);
        Location anchor = new Location(world, 0.5D, 80D, 0.5D);
        doReturn(anchor).when(runtimeControl).resolveEntryAnchor(world);
        doReturn(CompletableFuture.completedFuture(mock(Chunk.class)))
                .when(runtimeControl).requestChunkAsync(world, 0, 0, true);
        doReturn(CompletableFuture.completedFuture(anchor))
                .when(runtimeControl).resolveSafeEntry(world, anchor);
        doReturn(CompletableFuture.completedFuture(false))
                .when(runtimeControl).teleport(player, anchor);

        CompletableFuture<Boolean> result = IrisCreator.teleportSenderToCreatedWorld(player, world, runtimeControl);

        assertFalse(result.join());
    }

    @Test
    public void teleportSenderToCreatedWorld_failsWhenEntryAnchorCannotResolve() {
        Player player = mock(Player.class);
        World world = mock(World.class);
        WorldRuntimeControlService runtimeControl = mock(WorldRuntimeControlService.class);
        doReturn("irisworld").when(world).getName();
        doReturn(null).when(runtimeControl).resolveEntryAnchor(world);

        CompletableFuture<Boolean> result = IrisCreator.teleportSenderToCreatedWorld(player, world, runtimeControl);

        assertThrows(CompletionException.class, result::join);
        verify(runtimeControl).resolveEntryAnchor(world);
        verifyNoMoreInteractions(runtimeControl);
    }

    @Test
    public void teleportSenderToCreatedWorld_failsWhenNoSafeEntryExists() {
        Player player = mock(Player.class);
        World world = mock(World.class);
        WorldRuntimeControlService runtimeControl = mock(WorldRuntimeControlService.class);
        Location anchor = new Location(world, 0.5D, 80D, 0.5D);
        doReturn("irisworld").when(world).getName();
        doReturn(anchor).when(runtimeControl).resolveEntryAnchor(world);
        doReturn(CompletableFuture.completedFuture(mock(Chunk.class)))
                .when(runtimeControl).requestChunkAsync(world, 0, 0, true);
        doReturn(CompletableFuture.completedFuture(null))
                .when(runtimeControl).resolveSafeEntry(world, anchor);

        CompletableFuture<Boolean> result = IrisCreator.teleportSenderToCreatedWorld(player, world, runtimeControl);

        assertThrows(CompletionException.class, result::join);
        InOrder order = inOrder(runtimeControl);
        order.verify(runtimeControl).resolveEntryAnchor(world);
        order.verify(runtimeControl).requestChunkAsync(world, 0, 0, true);
        order.verify(runtimeControl).resolveSafeEntry(world, anchor);
        verifyNoMoreInteractions(runtimeControl);
    }

    @Test
    public void awaitTeleportFailure_returnsNullForSuccessfulTeleport() {
        CompletableFuture<Boolean> teleport = CompletableFuture.completedFuture(true);

        Throwable failure = IrisCreator.awaitTeleportFailure(
                teleport,
                "ParthOP69",
                1L,
                TimeUnit.SECONDS
        );

        assertNull(failure);
        assertFalse(teleport.isCancelled());
    }

    @Test
    public void awaitTeleportFailure_reportsFalseTeleportResult() {
        CompletableFuture<Boolean> teleport = CompletableFuture.completedFuture(false);

        Throwable failure = IrisCreator.awaitTeleportFailure(
                teleport,
                "ParthOP69",
                1L,
                TimeUnit.SECONDS
        );

        assertTrue(failure instanceof IllegalStateException);
        assertEquals(
                "The runtime teleport operation returned false for player \"ParthOP69\".",
                failure.getMessage()
        );
        assertFalse(teleport.isCancelled());
    }

    @Test
    public void awaitTeleportFailure_unwrapsExceptionalTeleportResult() {
        IllegalStateException expected = new IllegalStateException("teleport failed");
        CompletableFuture<Boolean> teleport = CompletableFuture.failedFuture(expected);

        Throwable failure = IrisCreator.awaitTeleportFailure(
                teleport,
                "ParthOP69",
                1L,
                TimeUnit.SECONDS
        );

        assertSame(expected, failure);
        assertFalse(teleport.isCancelled());
    }

    @Test
    public void awaitTeleportFailure_cancelsTimedOutTeleportWithoutRestartingServer() {
        CompletableFuture<Boolean> teleport = new CompletableFuture<>();

        try (MockedStatic<ServerConfigurator> serverConfigurator = mockStatic(ServerConfigurator.class)) {
            Throwable failure = IrisCreator.awaitTeleportFailure(
                    teleport,
                    "ParthOP69",
                    0L,
                    TimeUnit.MILLISECONDS
            );

            assertTrue(failure instanceof TimeoutException);
            assertTrue(teleport.isCancelled());
            serverConfigurator.verifyNoInteractions();
        }
    }
}
