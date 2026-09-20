package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import art.arcane.volmlib.nativelib.terrain.NativePregenRuntime;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

public class NativePregenRuntimeTest {
    @BeforeClass
    public static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void chunkTicketAdmissionAndReleaseRunThroughTheServerExecutor() {
        MinecraftServer server = mock(MinecraftServer.class);
        ServerLevel level = mock(ServerLevel.class);
        ServerChunkCache chunks = mock(ServerChunkCache.class);
        when(level.getServer()).thenReturn(server);
        when(level.getChunkSource()).thenReturn(chunks);
        CompletableFuture<Object> loaded = new CompletableFuture<>();
        doReturn(loaded).when(chunks).addTicketAndLoadWithRadius(any(), eq(new ChunkPos(-2, 7)), eq(0));
        NativePregenRuntime runtime = NativeModdedPregenRuntime.from(new ModdedPlatformWorld(level));

        CompletableFuture<NativePregenRuntime.ChunkLoad> result = runtime.loadChunk(-2, 7);
        verify(chunks, never()).addTicketAndLoadWithRadius(any(), any(), eq(0));
        ArgumentCaptor<Runnable> requests = ArgumentCaptor.forClass(Runnable.class);
        verify(server).execute(requests.capture());
        requests.getValue().run();
        assertFalse(result.isDone());
        loaded.complete(new Object());
        assertTrue(result.join().successful());
        ArgumentCaptor<TicketType> ticket = ArgumentCaptor.forClass(TicketType.class);
        verify(chunks).addTicketAndLoadWithRadius(ticket.capture(), eq(new ChunkPos(-2, 7)), eq(0));

        runtime.releaseChunk(-2, 7);
        verify(chunks, never()).removeTicketWithRadius(any(), any(), eq(0));
        verify(server, times(2)).execute(requests.capture());
        requests.getAllValues().getLast().run();
        ArgumentCaptor<TicketType> released = ArgumentCaptor.forClass(TicketType.class);
        verify(chunks).removeTicketWithRadius(released.capture(), eq(new ChunkPos(-2, 7)), eq(0));
        assertSame(ticket.getValue(), released.getValue());
    }
}
