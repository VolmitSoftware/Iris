package art.arcane.iris.platform.bukkit.nms.v26_2_R1;

import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.craftbukkit.CraftWorld;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class NmsChunkTaskPollingTest {
    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void serverOwnerPumpsOnlyTheTargetWorldQueue() {
        Server bukkitServer = mock(Server.class);
        CraftWorld world = mock(CraftWorld.class);
        ServerLevel level = mock(ServerLevel.class);
        MinecraftServer server = mock(MinecraftServer.class);
        ServerChunkCache chunks = mock(ServerChunkCache.class);
        NMSBinding binding = mock(NMSBinding.class, CALLS_REAL_METHODS);
        CompletableFuture<Void> completed = new CompletableFuture<>();
        when(world.getHandle()).thenReturn(level);
        when(level.getServer()).thenReturn(server);
        when(server.getRunningThread()).thenReturn(Thread.currentThread());
        when(level.getChunkSource()).thenReturn(chunks);
        when(chunks.pollTask()).thenAnswer(invocation -> completed.complete(null)).thenReturn(false);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<FoliaScheduler> folia = mockStatic(FoliaScheduler.class)) {
            bukkit.when(Bukkit::getServer).thenReturn(bukkitServer);
            folia.when(() -> FoliaScheduler.isRegionizedRuntime(bukkitServer)).thenReturn(false);
            assertTrue(binding.pollChunkTask(world));
            assertTrue(completed.isDone());
            assertFalse(binding.pollChunkTask(world));
        }
        verify(chunks, times(2)).pollTask();
        verify(server, never()).executeAllRecentInternalTasks();
    }

    @Test
    public void nonOwnerIsRejectedBeforeAccessingTheChunkQueue() {
        Server bukkitServer = mock(Server.class);
        CraftWorld world = mock(CraftWorld.class);
        ServerLevel level = mock(ServerLevel.class);
        MinecraftServer server = mock(MinecraftServer.class);
        NMSBinding binding = mock(NMSBinding.class, CALLS_REAL_METHODS);
        when(world.getHandle()).thenReturn(level);
        when(level.getServer()).thenReturn(server);
        when(server.getRunningThread()).thenReturn(new Thread("Other server owner"));
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<FoliaScheduler> folia = mockStatic(FoliaScheduler.class)) {
            bukkit.when(Bukkit::getServer).thenReturn(bukkitServer);
            folia.when(() -> FoliaScheduler.isRegionizedRuntime(bukkitServer)).thenReturn(false);
            assertThrows(IllegalStateException.class, () -> binding.pollChunkTask(world));
        }
        verify(level, never()).getChunkSource();
    }

    @Test
    public void foliaNeverUsesTheWorldWideTaskPump() {
        Server bukkitServer = mock(Server.class);
        CraftWorld world = mock(CraftWorld.class);
        NMSBinding binding = mock(NMSBinding.class, CALLS_REAL_METHODS);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<FoliaScheduler> folia = mockStatic(FoliaScheduler.class)) {
            bukkit.when(Bukkit::getServer).thenReturn(bukkitServer);
            folia.when(() -> FoliaScheduler.isRegionizedRuntime(bukkitServer)).thenReturn(true);
            assertFalse(binding.pollChunkTask(world));
        }
        verifyNoInteractions(world);
    }
}
