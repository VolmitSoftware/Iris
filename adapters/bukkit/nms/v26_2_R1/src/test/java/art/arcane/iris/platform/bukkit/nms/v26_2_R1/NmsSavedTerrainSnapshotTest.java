package art.arcane.iris.platform.bukkit.nms.v26_2_R1;

import art.arcane.iris.world.task.J;
import ca.spottedleaf.concurrentutil.completable.Completable;
import ca.spottedleaf.concurrentutil.executor.PrioritisedExecutor;
import ca.spottedleaf.concurrentutil.executor.thread.BalancedPrioritisedThreadPool;
import ca.spottedleaf.concurrentutil.util.Priority;
import ca.spottedleaf.moonrise.common.PlatformHooks;
import ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import org.bukkit.World;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NmsSavedTerrainSnapshotTest {
    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void ownerReservesSaveOrderBeforeOffOwnerSerializationAndLaterAutosave() throws Exception {
        try (SaveFixture fixture = new SaveFixture();
             ExecutorService checkpoint = Executors.newSingleThreadExecutor();
             MockedStatic<SerializableChunkData> snapshots = mockStatic(SerializableChunkData.class);
             MockedStatic<PlatformHooks> hooks = mockStatic(PlatformHooks.class);
             MockedStatic<MoonriseRegionFileIO> io = mockStatic(MoonriseRegionFileIO.class)) {
            Thread owner = Thread.currentThread();
            ChunkAccess chunk = mock(ChunkAccess.class);
            SerializableChunkData snapshot = mock(SerializableChunkData.class);
            PlatformHooks platform = mock(PlatformHooks.class);
            CompoundTag capturedData = new CompoundTag();
            CompoundTag autosave = new CompoundTag();
            List<String> submissions = new ArrayList<>();
            when(chunk.getPos()).thenReturn(new ChunkPos(4, -9));
            snapshots.when(() -> SerializableChunkData.copyOf(fixture.level, chunk)).thenAnswer(invocation -> {
                assertSame(owner, Thread.currentThread());
                return snapshot;
            });
            hooks.when(PlatformHooks::get).thenReturn(platform);
            doAnswer(invocation -> {
                assertSame(owner, Thread.currentThread());
                return null;
            }).when(platform).chunkSyncSave(fixture.level, chunk, snapshot);
            when(snapshot.write()).thenAnswer(invocation -> {
                assertNotSame(owner, Thread.currentThread());
                assertEquals(List.of("checkpoint", "autosave"), submissions);
                return capturedData;
            });
            io.when(() -> MoonriseRegionFileIO.scheduleSave(eq(fixture.level), eq(4), eq(-9),
                    any(Completable.class), any(PrioritisedExecutor.PrioritisedTask.class),
                    eq(MoonriseRegionFileIO.RegionFileType.CHUNK_DATA), eq(Priority.NORMAL)))
                    .thenAnswer(invocation -> {
                        assertSame(owner, Thread.currentThread());
                        submissions.add("checkpoint");
                        return null;
                    });
            io.when(() -> MoonriseRegionFileIO.scheduleSave(fixture.level, 4, -9, autosave,
                    MoonriseRegionFileIO.RegionFileType.CHUNK_DATA)).thenAnswer(invocation -> {
                submissions.add("autosave");
                return null;
            });

            NmsSavedTerrainCapture.CapturedChunk captured = NmsSavedTerrainCapture.scheduleSnapshot(fixture.level, chunk);

            assertEquals(List.of("checkpoint"), submissions);
            assertFalse(captured.data().isDone());
            verify(snapshot, never()).write();
            verify(platform).chunkSyncSave(fixture.level, chunk, snapshot);
            MoonriseRegionFileIO.scheduleSave(fixture.level, 4, -9, autosave,
                    MoonriseRegionFileIO.RegionFileType.CHUNK_DATA);
            Future<CompoundTag> result = checkpoint.submit(() -> NmsSavedTerrainCapture.awaitSnapshot(
                    fixture.level, captured, System.nanoTime() + TimeUnit.SECONDS.toNanos(3)));

            assertSame(capturedData, result.get(5, TimeUnit.SECONDS));
            verify(snapshot).write();
        }
    }

    @Test
    public void failedOffOwnerSerializationCannotSucceedAsACheckpoint() throws Exception {
        try (SaveFixture fixture = new SaveFixture();
             ExecutorService checkpoint = Executors.newSingleThreadExecutor();
             MockedStatic<SerializableChunkData> snapshots = mockStatic(SerializableChunkData.class);
             MockedStatic<PlatformHooks> hooks = mockStatic(PlatformHooks.class);
             MockedStatic<MoonriseRegionFileIO> io = mockStatic(MoonriseRegionFileIO.class)) {
            ChunkAccess chunk = mock(ChunkAccess.class);
            SerializableChunkData snapshot = mock(SerializableChunkData.class);
            IllegalStateException failure = new IllegalStateException("serialization failed");
            when(chunk.getPos()).thenReturn(new ChunkPos(4, -9));
            snapshots.when(() -> SerializableChunkData.copyOf(fixture.level, chunk)).thenReturn(snapshot);
            hooks.when(PlatformHooks::get).thenReturn(mock(PlatformHooks.class));
            when(snapshot.write()).thenThrow(failure);
            NmsSavedTerrainCapture.CapturedChunk captured = NmsSavedTerrainCapture.scheduleSnapshot(fixture.level, chunk);

            Future<IOException> result = checkpoint.submit(() -> assertThrows(IOException.class,
                    () -> NmsSavedTerrainCapture.awaitSnapshot(fixture.level, captured,
                            System.nanoTime() + TimeUnit.SECONDS.toNanos(3))));

            assertSame(failure, result.get(5, TimeUnit.SECONDS).getCause());
            assertTrue(captured.data().isCompletedExceptionally());
        }
    }

    @Test
    public void checkpointBoundsOwnerRequestsUntilPriorNativeWritesFinish() throws Exception {
        try (SaveFixture fixture = new SaveFixture();
             ExecutorService owner = Executors.newSingleThreadExecutor();
             ExecutorService checkpoint = Executors.newSingleThreadExecutor()) {
            World world = mock(World.class);
            when(world.getWorldFolder()).thenReturn(new File("checkpoint-world"));
            List<NewChunkHolder> holders = new ArrayList<>(129);
            for (int index = 0; index < 129; index++) {
                NewChunkHolder holder = mock(NewChunkHolder.class);
                setField(holder, "chunkX", index);
                holders.add(holder);
            }
            AtomicInteger requested = new AtomicInteger();
            AtomicBoolean writesBlocked = new AtomicBoolean(true);
            CountDownLatch firstBatchWaiting = new CountDownLatch(1);
            Future<?> result = checkpoint.submit(() -> {
                try (MockedStatic<J> scheduling = mockStatic(J.class);
                     MockedStatic<MoonriseRegionFileIO> io = mockStatic(MoonriseRegionFileIO.class)) {
                    scheduling.when(() -> J.runRegionFuture(eq(world), anyInt(), anyInt(), any(Runnable.class)))
                            .thenAnswer(invocation -> {
                                requested.incrementAndGet();
                                Runnable action = invocation.getArgument(3);
                                return CompletableFuture.runAsync(action, owner);
                            });
                    io.when(() -> MoonriseRegionFileIO.getPriority(eq(fixture.level), anyInt(), anyInt(),
                            eq(MoonriseRegionFileIO.RegionFileType.CHUNK_DATA))).thenAnswer(invocation -> {
                        firstBatchWaiting.countDown();
                        return writesBlocked.get() ? Priority.NORMAL : Priority.COMPLETING;
                    });
                    NmsSavedTerrainCapture.flushSnapshots(world, fixture.level, holders);
                    io.verify(() -> MoonriseRegionFileIO.getPriority(fixture.level, 128, 0,
                            MoonriseRegionFileIO.RegionFileType.CHUNK_DATA), times(2));
                    return null;
                }
            });
            try {
                assertTrue(firstBatchWaiting.await(5, TimeUnit.SECONDS));
                assertEquals(64, requested.get());
                assertTrue(owner.submit(() -> true).get(5, TimeUnit.SECONDS));
                assertFalse(result.isDone());
                writesBlocked.set(false);
                result.get(5, TimeUnit.SECONDS);
                assertEquals(129, requested.get());
            } finally {
                writesBlocked.set(false);
            }
        }
    }

    private static void setField(Object instance, String name, Object value) throws Exception {
        Field field = instance.getClass().getField(name);
        field.setAccessible(true);
        field.set(instance, value);
    }

    private static final class SaveFixture implements AutoCloseable {
        private final BalancedPrioritisedThreadPool pool = new BalancedPrioritisedThreadPool(
                TimeUnit.MILLISECONDS.toNanos(1), Executors.defaultThreadFactory());
        private final ServerLevel level = mock(ServerLevel.class);

        private SaveFixture() throws Exception {
            BalancedPrioritisedThreadPool.OrderedStreamGroup group = pool.createOrderedStreamGroup();
            ChunkTaskScheduler scheduler = mock(ChunkTaskScheduler.class);
            setField(scheduler, "saveExecutor", group.createExecutor());
            setField(scheduler, "compressionExecutor", group.createExecutor());
            setField(scheduler, "chunkHolderManager", mock(ChunkHolderManager.class));
            when(level.moonrise$getChunkTaskScheduler()).thenReturn(scheduler);
        }

        @Override
        public void close() throws Exception {
            pool.halt(true);
            assertTrue(pool.joinInterruptable(5_000));
        }
    }
}
