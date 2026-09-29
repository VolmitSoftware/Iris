package art.arcane.iris.modded.command;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.volmlib.nativelib.view.WorldView;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ModdedVisionOverlayTest {
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
