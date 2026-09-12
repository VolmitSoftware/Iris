package art.arcane.iris.platform.bukkit.nms.v26_2_R1;

import ca.spottedleaf.concurrentutil.executor.thread.BalancedPrioritisedThreadPool;
import ca.spottedleaf.concurrentutil.util.Priority;
import ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class NmsSavedTerrainWriteBarrierTest {
    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void checkpointHelpsSaveAndCompressionWhileNativeGenerationOccupiesTheWorker() throws Exception {
        BalancedPrioritisedThreadPool pool = new BalancedPrioritisedThreadPool(
                TimeUnit.MILLISECONDS.toNanos(1), Executors.defaultThreadFactory());
        BalancedPrioritisedThreadPool.OrderedStreamGroup group = pool.createOrderedStreamGroup();
        BalancedPrioritisedThreadPool.OrderedStreamGroup.Queue generation = group.createExecutor();
        BalancedPrioritisedThreadPool.OrderedStreamGroup.Queue saves = group.createExecutor();
        BalancedPrioritisedThreadPool.OrderedStreamGroup.Queue compression = group.createExecutor();
        ServerLevel level = level(saves, compression);
        ChunkPos chunk = new ChunkPos(4, -9);
        CountDownLatch generationEntered = new CountDownLatch(1);
        CountDownLatch releaseGeneration = new CountDownLatch(1);
        AtomicBoolean pendingWrite = new AtomicBoolean(true);
        AtomicInteger serialized = new AtomicInteger();
        AtomicInteger compressed = new AtomicInteger();
        AtomicReference<Thread> saveThread = new AtomicReference<>();
        AtomicReference<Thread> compressionThread = new AtomicReference<>();
        try (MockedStatic<MoonriseRegionFileIO> io = mockStatic(MoonriseRegionFileIO.class)) {
            io.when(() -> MoonriseRegionFileIO.getPriority(level, chunk.x(), chunk.z(),
                    MoonriseRegionFileIO.RegionFileType.CHUNK_DATA))
                    .thenAnswer(invocation -> pendingWrite.get() ? Priority.NORMAL : Priority.COMPLETING);
            generation.queueTask(() -> {
                generationEntered.countDown();
                try {
                    assertTrue(releaseGeneration.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException interruption) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(interruption);
                }
            });
            pool.adjustThreadCount(1);
            assertTrue(generationEntered.await(5, TimeUnit.SECONDS));
            saves.queueTask(() -> {
                saveThread.set(Thread.currentThread());
                serialized.incrementAndGet();
                compression.queueTask(() -> {
                    compressionThread.set(Thread.currentThread());
                    compressed.incrementAndGet();
                    pendingWrite.set(false);
                });
            });

            NmsSavedTerrainCapture.awaitWrite(level, chunk, System.nanoTime() + TimeUnit.SECONDS.toNanos(3));

            assertEquals(1L, releaseGeneration.getCount());
            assertEquals(1, serialized.get());
            assertEquals(1, compressed.get());
            assertFalse(pendingWrite.get());
            assertSame(Thread.currentThread(), saveThread.get());
            assertSame(Thread.currentThread(), compressionThread.get());
        } finally {
            releaseGeneration.countDown();
            pool.halt(true);
            assertTrue(pool.joinInterruptable(5_000));
        }
    }

    @Test
    public void waitingForNativeWriteHonorsTheSharedDeadline() throws Exception {
        ServerLevel level = mock(ServerLevel.class);
        ChunkPos chunk = new ChunkPos(2, 3);
        IOException failure = assertThrows(IOException.class,
                () -> NmsSavedTerrainCapture.awaitWrite(level, chunk, System.nanoTime() - 1L));
        assertTrue(failure.getMessage().contains("Timed out"));
    }

    @Test
    public void interruptedNativeWriteWaitPreservesInterruption() throws Exception {
        ServerLevel level = mock(ServerLevel.class);
        ChunkPos chunk = new ChunkPos(2, 3);
        Thread.currentThread().interrupt();
        try {
            IOException failure = assertThrows(IOException.class,
                    () -> NmsSavedTerrainCapture.awaitWrite(level, chunk,
                            System.nanoTime() + TimeUnit.SECONDS.toNanos(3)));
            assertTrue(failure.getMessage().contains("Interrupted"));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    private static ServerLevel level(BalancedPrioritisedThreadPool.OrderedStreamGroup.Queue saves,
                                     BalancedPrioritisedThreadPool.OrderedStreamGroup.Queue compression) throws Exception {
        ServerLevel level = mock(ServerLevel.class);
        ChunkTaskScheduler scheduler = mock(ChunkTaskScheduler.class);
        Field savesField = ChunkTaskScheduler.class.getField("saveExecutor");
        Field compressionField = ChunkTaskScheduler.class.getField("compressionExecutor");
        savesField.setAccessible(true);
        compressionField.setAccessible(true);
        savesField.set(scheduler, saves);
        compressionField.set(scheduler, compression);
        when(level.moonrise$getChunkTaskScheduler()).thenReturn(scheduler);
        return level;
    }
}
