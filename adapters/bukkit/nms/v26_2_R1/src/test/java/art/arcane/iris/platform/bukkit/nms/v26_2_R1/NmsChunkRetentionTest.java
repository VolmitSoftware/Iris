package art.arcane.iris.platform.bukkit.nms.v26_2_R1;

import art.arcane.volmlib.nativelib.v26_2_R1.terrain.NativeTerrainAccessImpl;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.bukkit.craftbukkit.CraftWorld;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NmsChunkRetentionTest {
    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void retentionRequestsNothingBeyondTheOuterRingOfAFullRequest() {
        NativeTerrainAccessImpl binding = mock(NativeTerrainAccessImpl.class, CALLS_REAL_METHODS);
        assertEquals(ChunkLevel.RADIUS_AROUND_FULL_CHUNK, binding.fullChunkDependencyRadius());
        assertEquals(ChunkLevel.MAX_LEVEL, ChunkHolderManager.MAX_TICKET_LEVEL);
        assertEquals(ChunkLevel.generationStatus(ChunkLevel.FULL_CHUNK_LEVEL + binding.fullChunkDependencyRadius()),
                ChunkLevel.generationStatus(ChunkHolderManager.MAX_TICKET_LEVEL));
        assertEquals(ChunkStatus.STRUCTURE_STARTS, ChunkLevel.generationStatus(ChunkHolderManager.MAX_TICKET_LEVEL));
    }

    @Test
    public void retainAndReleaseUseOnePermanentNonPersistentTicketAtTheMaximumLevel() throws Exception {
        NativeTerrainAccessImpl binding = mock(NativeTerrainAccessImpl.class, CALLS_REAL_METHODS);
        CraftWorld world = mock(CraftWorld.class);
        ServerLevel level = mock(ServerLevel.class);
        ChunkTaskScheduler scheduler = mock(ChunkTaskScheduler.class);
        ChunkHolderManager manager = mock(ChunkHolderManager.class);
        Field managerField = ChunkTaskScheduler.class.getField("chunkHolderManager");
        managerField.setAccessible(true);
        managerField.set(scheduler, manager);
        when(world.getHandle()).thenReturn(level);
        when(level.moonrise$getChunkTaskScheduler()).thenReturn(scheduler);
        when(manager.addTicketAtLevel(any(TicketType.class), eq(3), eq(-4), eq(ChunkHolderManager.MAX_TICKET_LEVEL), isNull()))
                .thenReturn(true);
        when(manager.removeTicketAtLevel(any(TicketType.class), eq(3), eq(-4), eq(ChunkHolderManager.MAX_TICKET_LEVEL), isNull()))
                .thenReturn(true);

        assertTrue(binding.retainChunk(world, 3, -4));
        ArgumentCaptor<TicketType> type = ArgumentCaptor.forClass(TicketType.class);
        verify(manager).addTicketAtLevel(type.capture(), eq(3), eq(-4), eq(ChunkHolderManager.MAX_TICKET_LEVEL), isNull());
        assertEquals(TicketType.NO_TIMEOUT, type.getValue().timeout());
        assertTrue(type.getValue().doesLoad());
        assertFalse(type.getValue().doesSimulate());
        assertFalse(type.getValue().persist());

        assertTrue(binding.releaseChunk(world, 3, -4));
        verify(manager).removeTicketAtLevel(same(type.getValue()), eq(3), eq(-4), eq(ChunkHolderManager.MAX_TICKET_LEVEL), isNull());

        binding.retainChunk(world, 3, -4);
        ArgumentCaptor<TicketType> again = ArgumentCaptor.forClass(TicketType.class);
        verify(manager, times(2)).addTicketAtLevel(again.capture(), eq(3), eq(-4),
                eq(ChunkHolderManager.MAX_TICKET_LEVEL), isNull());
        assertSame(type.getValue(), again.getValue());
    }
}
