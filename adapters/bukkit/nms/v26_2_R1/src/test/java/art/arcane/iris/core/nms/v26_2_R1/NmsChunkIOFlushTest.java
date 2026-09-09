package art.arcane.iris.core.nms.v26_2_R1;

import ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.RegionDataController;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import org.bukkit.craftbukkit.CraftWorld;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NmsChunkIOFlushTest {
    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void nativeDrainCanWaitOffOwnerUntilChunkPoiAndEntityIoFinish() throws Exception {
        Fixture fixture = new Fixture();
        AtomicBoolean chunkPending = new AtomicBoolean(true);
        AtomicBoolean poiPending = new AtomicBoolean(true);
        AtomicBoolean entityPending = new AtomicBoolean(true);
        CountDownLatch chunkDrain = new CountDownLatch(1);
        CountDownLatch poiDrain = new CountDownLatch(1);
        CountDownLatch entityDrain = new CountDownLatch(1);
        when(fixture.chunks.hasTasks()).thenAnswer(invocation -> {
            chunkDrain.countDown();
            return chunkPending.get();
        });
        when(fixture.pois.hasTasks()).thenAnswer(invocation -> {
            poiDrain.countDown();
            return poiPending.get();
        });
        when(fixture.entities.hasTasks()).thenAnswer(invocation -> {
            entityDrain.countDown();
            return entityPending.get();
        });
        ExecutorService io = Executors.newSingleThreadExecutor();
        try {
            Future<?> drain = io.submit(() -> fixture.binding.flushChunkIO(fixture.world));
            assertTrue(chunkDrain.await(5L, TimeUnit.SECONDS));
            assertFalse(drain.isDone());
            chunkPending.set(false);
            assertTrue(poiDrain.await(5L, TimeUnit.SECONDS));
            assertFalse(drain.isDone());
            poiPending.set(false);
            assertTrue(entityDrain.await(5L, TimeUnit.SECONDS));
            assertFalse(drain.isDone());
            entityPending.set(false);
            drain.get(5L, TimeUnit.SECONDS);
        } finally {
            chunkPending.set(false);
            poiPending.set(false);
            entityPending.set(false);
            io.shutdownNow();
            assertTrue(io.awaitTermination(5L, TimeUnit.SECONDS));
        }
    }

    @Test
    public void nativeDrainFailurePropagatesToItsCompletionReceipt() {
        Fixture fixture = new Fixture();
        IllegalStateException failure = new IllegalStateException("native IO failed");
        when(fixture.chunks.hasTasks()).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> fixture.binding.flushChunkIO(fixture.world)));
    }

    private static final class Fixture {
        private final NMSBinding binding = mock(NMSBinding.class, CALLS_REAL_METHODS);
        private final CraftWorld world = mock(CraftWorld.class);
        private final ServerLevel level = mock(ServerLevel.class);
        private final RegionDataController chunks = mock(RegionDataController.class);
        private final RegionDataController pois = mock(RegionDataController.class);
        private final RegionDataController entities = mock(RegionDataController.class);

        private Fixture() {
            when(world.getHandle()).thenReturn(level);
            when(level.moonrise$getChunkDataController()).thenReturn(chunks);
            when(level.moonrise$getPoiChunkDataController()).thenReturn(pois);
            when(level.moonrise$getEntityChunkDataController()).thenReturn(entities);
        }
    }
}
