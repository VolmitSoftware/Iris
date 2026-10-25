package art.arcane.iris.modded.command;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.studio.view.OpenerPosition;
import art.arcane.iris.world.IrisWorld;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedServer;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeProtocolPlayer;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import art.arcane.volmlib.nativelib.view.WorldView;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.invocation.InvocationOnMock;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class ModdedVisionOverlayTest {
    @Test
    public void rejectedServerTaskCompletesThePositionRequestAsOffline() {
        WorldView world = mock(WorldView.class);
        doThrow(new RejectedExecutionException("Server stopped")).when(world).execute(any(Runnable.class));
        ModdedVisionOverlay overlay = new ModdedVisionOverlay(new ModdedVisionOverlay.Context(world,
                mock(Engine.class), UUID.randomUUID()));
        AtomicReference<OpenerPosition> position = new AtomicReference<>();

        overlay.requestOpenerPosition(position::set);

        assertEquals(OpenerPosition.OFFLINE, position.get());
    }

    @Test
    public void openerPositionRunsOnTheServerAndTracksDisconnectAndWorldChanges() {
        WorldView world = mock(WorldView.class);
        AtomicReference<Runnable> scheduled = new AtomicReference<>();
        doAnswer(invocation -> {
            scheduled.set(invocation.getArgument(0));
            return null;
        }).when(world).execute(any(Runnable.class));
        Engine engine = mock(Engine.class);
        IrisWorld irisWorld = mock(IrisWorld.class);
        NativeWorld level = mock(NativeWorld.class);
        NativeModdedServer server = mock(NativeModdedServer.class);
        NativeProtocolPlayer player = mock(NativeProtocolPlayer.class);
        UUID opener = UUID.randomUUID();
        when(engine.getWorld()).thenReturn(irisWorld);
        when(irisWorld.platformWorld()).thenReturn(level);
        when(server.player(opener)).thenReturn(player);
        when(player.connected()).thenReturn(true);
        when(player.inWorld(level)).thenReturn(true);
        when(player.x()).thenReturn(21.5D);
        when(player.y()).thenReturn(85.25D);
        when(player.z()).thenReturn(-39.5D);
        ModdedVisionOverlay overlay = new ModdedVisionOverlay(new ModdedVisionOverlay.Context(world, engine, opener));
        AtomicReference<OpenerPosition> position = new AtomicReference<>();
        try (MockedStatic<NativeModdedServer> nativeServer = mockStatic(NativeModdedServer.class)) {
            nativeServer.when(() -> NativeModdedServer.forWorld(level)).thenReturn(server);

            overlay.requestOpenerPosition(position::set);
            assertNull("position reads must wait for the server task", position.get());
            scheduled.get().run();
            assertEquals(OpenerPosition.inWorld(21.5D, 85.25D, -39.5D), position.get());

            overlay.requestOpenerPosition(position::set);
            when(player.connected()).thenReturn(false);
            scheduled.get().run();
            assertEquals(OpenerPosition.OFFLINE, position.get());

            when(player.connected()).thenReturn(true);
            when(player.inWorld(level)).thenReturn(false);
            overlay.requestOpenerPosition(position::set);
            scheduled.get().run();
            assertEquals(OpenerPosition.IN_OTHER_WORLD, position.get());

            when(player.inWorld(level)).thenReturn(true);
            overlay.requestOpenerPosition(position::set);
            scheduled.get().run();
            assertEquals(OpenerPosition.inWorld(21.5D, 85.25D, -39.5D), position.get());
        }
    }

    /**
     * A Vision click can land anywhere in the world, so the destination chunk is usually cold and gets the same
     * bounded deadline as /iris tp.
     */
    @Test
    public void teleportWaitsForAColdDestinationWithinTheTeleportDeadline() {
        WorldView world = mock(WorldView.class);
        doAnswer((InvocationOnMock invocation) -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(world).execute(any());
        UUID opener = UUID.randomUUID();
        AtomicReference<WorldView.Destination> requested = new AtomicReference<>();
        when(world.teleport(eq(opener), any())).thenAnswer((InvocationOnMock invocation) -> {
            requested.set(invocation.getArgument(1));
            return Optional.empty();
        });
        Engine engine = mock(Engine.class);
        when(engine.getMinHeight()).thenReturn(-64);
        when(engine.getHeight(anyInt(), anyInt(), eq(false))).thenReturn(130);

        long before = System.nanoTime();
        new ModdedVisionOverlay(new ModdedVisionOverlay.Context(world, engine, opener)).teleport(-3.5D, 17.25D);
        long after = System.nanoTime();

        WorldView.Destination destination = requested.get();
        assertEquals(-3.5D, destination.x(), 0D);
        assertEquals(68D, destination.y(), 0D);
        assertEquals(17.5D, destination.z(), 0D);
        assertTrue("deadline must outlast a cold first chunk",
                destination.deadlineNanos() - before >= TimeUnit.SECONDS.toNanos(60L));
        assertTrue("deadline must be bounded", destination.deadlineNanos() - after <= TimeUnit.SECONDS.toNanos(120L));
    }
}
