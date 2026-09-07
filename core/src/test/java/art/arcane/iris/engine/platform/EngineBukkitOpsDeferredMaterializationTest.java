package art.arcane.iris.engine.platform;

import art.arcane.iris.core.link.Identifier;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.IrisEngineMantle;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineMetrics;
import art.arcane.iris.engine.history.SavedBiomeUnavailableException;
import art.arcane.iris.engine.object.IrisWorld;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.util.project.matter.TileWrapper;
import art.arcane.iris.util.project.matter.slices.PreObjectMatterTest;
import art.arcane.volmlib.util.function.Consumer4;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.mantle.runtime.MantleDataAdapter;
import art.arcane.volmlib.util.mantle.runtime.MantleHooks;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterUpdate;
import art.arcane.volmlib.util.matter.slices.UpdateMatter;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.junit.Test;
import org.mockito.InOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class EngineBukkitOpsDeferredMaterializationTest {
    @Test(timeout = 5000)
    public void loadingRetryPreservesPendingUpdatesWithoutRepeatingCompletedUpdates() {
        MantleChunk<Matter> mantleChunk = pendingUpdates();
        IrisWorld irisWorld = mock(IrisWorld.class);
        when(irisWorld.minHeight()).thenReturn(-64);
        Engine engine = mock(Engine.class);
        when(engine.getWorld()).thenReturn(irisWorld);
        when(engine.getMetrics()).thenReturn(new EngineMetrics(8));
        Chunk chunk = mock(Chunk.class);
        AtomicInteger tileRuns = new AtomicInteger();
        AtomicInteger customRuns = new AtomicInteger();
        AtomicInteger attempts = new AtomicInteger();
        List<Integer> completed = new ArrayList<>();
        SavedBiomeUnavailableException loading = new SavedBiomeUnavailableException("Loading", true);
        Runnable updates = () -> EngineBukkitOps.materializeUpdates(engine, chunk, mantleChunk, (x, y, z) -> {
            if (y == -43 && attempts.incrementAndGet() <= 2) {
                throw loading;
            }
            completed.add(y);
        });
        Runnable pass = () -> EngineBukkitOps.runMaterializationPasses(mantleChunk,
                tileRuns::incrementAndGet, customRuns::incrementAndGet, updates);

        for (int retry = 0; retry < 2; retry++) {
            assertSame(loading, assertThrows(SavedBiomeUnavailableException.class, pass::run));
            assertEquals(List.of(-59), completed);
            assertNull(mantleChunk.get(0).getSlice(MatterUpdate.class).get(3, 5, 7));
            assertTrue(mantleChunk.get(1).getSlice(MatterUpdate.class).get(3, 5, 7).isUpdate());
            assertTrue(mantleChunk.get(2).getSlice(MatterUpdate.class).get(3, 5, 7).isUpdate());
            assertTrue(mantleChunk.isFlagged(MantleFlag.TILE));
            assertTrue(mantleChunk.isFlagged(MantleFlag.CUSTOM));
            assertFalse(mantleChunk.isFlagged(MantleFlag.UPDATE));
            assertFalse(mantleChunk.isFlagged(MantleFlag.ETCHED));
        }

        pass.run();

        assertEquals(List.of(-59, -43, -27), completed);
        assertEquals(1, tileRuns.get());
        assertEquals(1, customRuns.get());
        assertTrue(mantleChunk.isFlagged(MantleFlag.UPDATE));
        assertTrue(mantleChunk.isFlagged(MantleFlag.ETCHED));
        for (int section = 0; section < 3; section++) {
            assertFalse(mantleChunk.get(section).hasSlice(MatterUpdate.class));
        }
    }

    @Test
    public void permanentAndUnrelatedFailuresKeepTheirCauseAndPendingMarkers() {
        for (RuntimeException failure : List.of(
                new SavedBiomeUnavailableException("Saved biome is missing", false),
                new IllegalStateException("Broken loot"))) {
            MantleChunk<Matter> mantleChunk = pendingUpdates();
            IrisWorld irisWorld = mock(IrisWorld.class);
            when(irisWorld.minHeight()).thenReturn(-64);
            Engine engine = mock(Engine.class);
            when(engine.getWorld()).thenReturn(irisWorld);
            List<Integer> completed = new ArrayList<>();
            Runnable updates = () -> EngineBukkitOps.materializeUpdates(engine, mock(Chunk.class), mantleChunk,
                    (x, y, z) -> {
                        if (y == -43) {
                            throw failure;
                        }
                        completed.add(y);
                    });

            assertSame(failure, assertThrows(RuntimeException.class,
                    () -> EngineBukkitOps.runMaterializationPasses(mantleChunk, () -> {}, () -> {}, updates)));

            assertEquals(List.of(-59), completed);
            assertNull(mantleChunk.get(0).getSlice(MatterUpdate.class).get(3, 5, 7));
            assertSame(UpdateMatter.ON, mantleChunk.get(1).getSlice(MatterUpdate.class).get(3, 5, 7));
            assertSame(UpdateMatter.ON, mantleChunk.get(2).getSlice(MatterUpdate.class).get(3, 5, 7));
            assertTrue(mantleChunk.isFlagged(MantleFlag.TILE));
            assertTrue(mantleChunk.isFlagged(MantleFlag.CUSTOM));
            assertFalse(mantleChunk.isFlagged(MantleFlag.UPDATE));
            assertFalse(mantleChunk.isFlagged(MantleFlag.ETCHED));
        }
    }

    @Test
    public void cleanupFailurePreservesLoadingCauseAndUnfinishedFlags() {
        MantleChunk<Matter> mantleChunk = spy(pendingUpdates());
        SavedBiomeUnavailableException loading = new SavedBiomeUnavailableException("Loading", true);
        IllegalStateException cleanup = new IllegalStateException("Marker cleanup failed");
        AtomicBoolean failed = new AtomicBoolean();
        doAnswer(invocation -> {
            if (failed.get()) {
                throw cleanup;
            }
            return invocation.callRealMethod();
        }).when(mantleChunk).get(0);
        IrisWorld irisWorld = mock(IrisWorld.class);
        when(irisWorld.minHeight()).thenReturn(-64);
        Engine engine = mock(Engine.class);
        when(engine.getWorld()).thenReturn(irisWorld);
        List<Integer> completed = new ArrayList<>();
        Runnable updates = () -> EngineBukkitOps.materializeUpdates(engine, mock(Chunk.class), mantleChunk,
                (x, y, z) -> {
                    if (y == -43) {
                        failed.set(true);
                        throw loading;
                    }
                    completed.add(y);
                });

        assertSame(loading, assertThrows(SavedBiomeUnavailableException.class,
                () -> EngineBukkitOps.runMaterializationPasses(mantleChunk, () -> {}, () -> {}, updates)));

        assertEquals(List.of(-59), completed);
        assertEquals(1, loading.getSuppressed().length);
        assertSame(cleanup, loading.getSuppressed()[0]);
        assertTrue(mantleChunk.isFlagged(MantleFlag.TILE));
        assertTrue(mantleChunk.isFlagged(MantleFlag.CUSTOM));
        assertFalse(mantleChunk.isFlagged(MantleFlag.UPDATE));
        assertFalse(mantleChunk.isFlagged(MantleFlag.ETCHED));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void updateMarkerUsesWorldHeightAndDeletesSliceAfterDispatch() {
        MantleChunk<Matter> mantleChunk = mock(MantleChunk.class);
        doAnswer(invocation -> {
            Consumer4<Integer, Integer, Integer, MatterUpdate> iterator = invocation.getArgument(1);
            iterator.accept(-1, 5, -2, UpdateMatter.ON);
            return null;
        }).when(mantleChunk).iterate(eq(MatterUpdate.class), any());
        IrisWorld irisWorld = mock(IrisWorld.class);
        when(irisWorld.minHeight()).thenReturn(-64);
        Engine engine = mock(Engine.class);
        when(engine.getWorld()).thenReturn(irisWorld);
        when(engine.getMetrics()).thenReturn(new EngineMetrics(8));
        Chunk chunk = mock(Chunk.class);
        ArrayList<String> updates = new ArrayList<>();

        EngineBukkitOps.materializeUpdates(
                engine,
                chunk,
                mantleChunk,
                (x, y, z) -> updates.add(x + "," + y + "," + z)
        );

        assertEquals(List.of("-1,-59,-2"), updates);
        verify(mantleChunk).deleteSlices(MatterUpdate.class);
    }

    @Test
    public void fallingFluidUpdateRetainsLevelEightStateAndRequestsPhysics() {
        Block block = mock(Block.class);
        BlockData falling = mock(BlockData.class);

        EngineBukkitOps.applyPhysicsUpdate(block, falling);

        InOrder order = inOrder(block);
        order.verify(block).setType(Material.AIR, false);
        order.verify(block).setBlockData(falling, true);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void tileSliceIsDeletedAfterIteration() {
        MantleChunk<Matter> mantleChunk = mock(MantleChunk.class);

        EngineBukkitOps.materializeTiles(mock(Engine.class), mock(Chunk.class), mantleChunk);

        InOrder order = inOrder(mantleChunk);
        order.verify(mantleChunk).iterate(eq(TileWrapper.class), any());
        order.verify(mantleChunk).deleteSlices(TileWrapper.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void failedTileIterationRetainsSlice() {
        MantleChunk<Matter> mantleChunk = mock(MantleChunk.class);
        IllegalStateException failure = new IllegalStateException("tile failure");
        doThrow(failure).when(mantleChunk).iterate(eq(TileWrapper.class), any());

        assertThrows(IllegalStateException.class,
                () -> EngineBukkitOps.materializeTiles(mock(Engine.class), mock(Chunk.class), mantleChunk));

        verify(mantleChunk, never()).deleteSlices(TileWrapper.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void customSliceIsDeletedAfterIteration() {
        MantleChunk<Matter> mantleChunk = mock(MantleChunk.class);

        EngineBukkitOps.materializeCustomBlocks(mock(Engine.class), mock(Chunk.class), mantleChunk);

        InOrder order = inOrder(mantleChunk);
        order.verify(mantleChunk).iterate(eq(Identifier.class), any());
        order.verify(mantleChunk).deleteSlices(Identifier.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void failedCustomPassRetrySkipsCompletedTilePass() {
        MantleDataAdapter<Matter> adapter = mock(MantleDataAdapter.class);
        MantleChunk<Matter> chunk = new MantleChunk<>(1, 0, 0, adapter, MantleHooks.NONE);
        AtomicInteger tileRuns = new AtomicInteger();
        AtomicInteger customRuns = new AtomicInteger();
        AtomicInteger updateRuns = new AtomicInteger();
        Runnable tileTask = tileRuns::incrementAndGet;
        Runnable customTask = () -> {
            if (customRuns.incrementAndGet() == 1) {
                throw new IllegalStateException("custom failure");
            }
        };
        Runnable updateTask = updateRuns::incrementAndGet;

        assertThrows(IllegalStateException.class,
                () -> EngineBukkitOps.runMaterializationPasses(chunk, tileTask, customTask, updateTask));

        assertTrue(chunk.isFlagged(MantleFlag.TILE));
        assertFalse(chunk.isFlagged(MantleFlag.CUSTOM));
        assertFalse(chunk.isFlagged(MantleFlag.UPDATE));
        assertFalse(chunk.isFlagged(MantleFlag.ETCHED));

        EngineBukkitOps.runMaterializationPasses(chunk, tileTask, customTask, updateTask);

        assertTrue(chunk.isFlagged(MantleFlag.TILE));
        assertTrue(chunk.isFlagged(MantleFlag.CUSTOM));
        assertTrue(chunk.isFlagged(MantleFlag.UPDATE));
        assertTrue(chunk.isFlagged(MantleFlag.ETCHED));
        assertEquals(1, tileRuns.get());
        assertEquals(2, customRuns.get());
        assertEquals(1, updateRuns.get());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void failedCustomIterationRetainsSlice() {
        MantleChunk<Matter> mantleChunk = mock(MantleChunk.class);
        doThrow(new IllegalStateException("custom failure"))
                .when(mantleChunk).iterate(eq(Identifier.class), any());

        assertThrows(IllegalStateException.class,
                () -> EngineBukkitOps.materializeCustomBlocks(
                        mock(Engine.class),
                        mock(Chunk.class),
                        mantleChunk
                ));

        verify(mantleChunk, never()).deleteSlices(Identifier.class);
    }

    private static MantleChunk<Matter> pendingUpdates() {
        PreObjectMatterTest.setUpBukkit();
        IrisPlatform previous = IrisPlatforms.isBound() ? IrisPlatforms.get() : null;
        IrisPlatform platform = mock(IrisPlatform.class);
        PlatformRegistries registries = mock(PlatformRegistries.class);
        when(registries.block("AIR")).thenReturn(mock(PlatformBlockState.class));
        when(platform.registries()).thenReturn(registries);
        IrisPlatforms.unbind();
        IrisPlatforms.bind(platform);
        try {
            MantleChunk<Matter> chunk = new MantleChunk<>(4, 2, -1,
                    IrisEngineMantle.createRuntimeDataAdapter(mock(IrisData.class)), MantleHooks.NONE);
            for (int section = 0; section < 3; section++) {
                chunk.getOrCreate(section).slice(MatterUpdate.class).set(3, 5, 7, UpdateMatter.ON);
            }
            return chunk;
        } finally {
            IrisPlatforms.unbind();
            if (previous != null) {
                IrisPlatforms.bind(previous);
            }
        }
    }
}
