package art.arcane.iris.modded.service;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.ModdedPlatformWorld;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.EngineMetrics;
import art.arcane.iris.modded.ModdedLootApplier;
import art.arcane.iris.world.IrisWorld;
import art.arcane.iris.world.history.SavedBiomeUnavailableException;
import art.arcane.iris.world.storage.matter.TileWrapper;
import art.arcane.volmlib.nativelib.terrain.NativeBlockPoint;
import art.arcane.volmlib.util.function.Consumer4;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.mantle.runtime.MantleDataAdapter;
import art.arcane.volmlib.util.mantle.runtime.MantleHooks;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterSlice;
import art.arcane.volmlib.util.matter.MatterUpdate;
import art.arcane.volmlib.util.matter.slices.UpdateMatter;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ModdedChunkUpdateServiceTest {
    @Test
    public void fallingFluidUpdateRetainsLevelEightStateAndRequestsPhysics() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        IrisWorld world = mock(IrisWorld.class);
        when(world.minHeight()).thenReturn(-64);
        Engine engine = mock(Engine.class);
        when(engine.getWorld()).thenReturn(world);
        when(engine.getMetrics()).thenReturn(new EngineMetrics(8));
        ServerLevel level = mock(ServerLevel.class);
        when(level.getMinY()).thenReturn(-64);
        when(level.getMaxY()).thenReturn(319);
        when(level.getHeight()).thenReturn(384);
        BlockPos position = new BlockPos(15, -59, 14);
        BlockState falling = Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 8);
        when(level.getBlockState(position)).thenReturn(falling);
        UpdateRecordingMantleChunk chunk = new UpdateRecordingMantleChunk();

        new ModdedChunkUpdateService().runUpdatePass(engine, new ModdedPlatformWorld(level), 0, 0, chunk);

        assertEquals(8, falling.getValue(LiquidBlock.LEVEL).intValue());
        if (falling.getFluidState().hasProperty(FlowingFluid.FALLING)) {
            assertTrue(falling.getFluidState().getValue(FlowingFluid.FALLING));
        }
        InOrder order = inOrder(level);
        order.verify(level).setBlock(eq(position), eq(Blocks.AIR.defaultBlockState()), anyInt());
        order.verify(level).setBlock(position, falling, Block.UPDATE_ALL);
        assertTrue(chunk.deleted());
    }

    @Test
    public void loadingSavedBiomeDefersThePassAndForgetsOnlyCompletedLoot() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        IrisWorld world = mock(IrisWorld.class);
        when(world.minHeight()).thenReturn(-64);
        Engine engine = mock(Engine.class);
        when(engine.getWorld()).thenReturn(world);
        when(engine.getMetrics()).thenReturn(new EngineMetrics(8));
        ServerLevel level = mock(ServerLevel.class);
        when(level.getMinY()).thenReturn(-64);
        when(level.getMaxY()).thenReturn(319);
        when(level.getHeight()).thenReturn(384);
        when(level.getBlockState(any(BlockPos.class))).thenReturn(Blocks.CHEST.defaultBlockState());
        ChestUpdateMantleChunk chunk = new ChestUpdateMantleChunk();
        SavedBiomeUnavailableException loading = new SavedBiomeUnavailableException(
                "Saved biome information is loading at chunk 0,0. Try again shortly.", true);

        try (MockedStatic<ModdedLootApplier> loot = mockStatic(ModdedLootApplier.class)) {
            loot.when(() -> ModdedLootApplier.apply(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
                NativeBlockPoint position = invocation.getArgument(2);
                if (position.x() == 2) {
                    throw loading;
                }
                return null;
            });

            SavedBiomeUnavailableException thrown = assertThrows(SavedBiomeUnavailableException.class,
                    () -> new ModdedChunkUpdateService().runUpdatePass(
                            engine, new ModdedPlatformWorld(level), 0, 0, chunk));

            assertSame(loading, thrown);
        }
        assertFalse(chunk.deleted());
        verify(chunk.slice).set(1, 5, 1, null);
        verify(chunk.slice, never()).set(2, 5, 2, null);
    }

    @Test
    public void scansWhenPlayersArePresent() {
        assertTrue(ModdedChunkUpdateService.hasUpdateTargets(true, false));
    }

    @Test
    public void scansHeadlessForceLoadedChunks() {
        assertTrue(ModdedChunkUpdateService.hasUpdateTargets(false, true));
    }

    @Test
    public void skipsLevelsWithoutPlayersOrForcedChunks() {
        assertFalse(ModdedChunkUpdateService.hasUpdateTargets(false, false));
    }

    @Test
    public void deferredSliceIsDeletedAfterMaterialization() {
        RecordingMantleChunk chunk = new RecordingMantleChunk();

        ModdedChunkUpdateService.materializeDeferredSlice(
                chunk,
                TileWrapper.class,
                () -> chunk.record("materialize")
        );

        assertEquals(List.of("materialize", "delete:" + TileWrapper.class.getName()),
                chunk.operations());
    }

    @Test
    public void failedMaterializationRetainsDeferredSlice() {
        RecordingMantleChunk chunk = new RecordingMantleChunk();

        assertThrows(IllegalStateException.class, () ->
                ModdedChunkUpdateService.materializeDeferredSlice(
                        chunk,
                        TileWrapper.class,
                        () -> {
                            chunk.record("materialize");
                            throw new IllegalStateException("materialization failure");
                        }
                ));

        assertEquals(List.of("materialize"), chunk.operations());
    }

    private static final class RecordingMantleChunk extends MantleChunk<Matter> {
        private final ArrayList<String> operations = new ArrayList<>();

        private RecordingMantleChunk() {
            super(1, 0, 0, emptyAdapter(), MantleHooks.NONE);
        }

        @Override
        public void deleteSlices(Class<?> type) {
            operations.add("delete:" + type.getName());
        }

        private void record(String operation) {
            operations.add(operation);
        }

        private List<String> operations() {
            return List.copyOf(operations);
        }
    }

    private static final class UpdateRecordingMantleChunk extends MantleChunk<Matter> {
        private boolean deleted;

        private UpdateRecordingMantleChunk() {
            super(1, 0, 0, emptyAdapter(), MantleHooks.NONE);
        }

        @Override
        public <T> void iterate(
                Class<T> type,
                Consumer4<Integer, Integer, Integer, T> iterator
        ) {
            if (type == MatterUpdate.class) {
                iterator.accept(-1, 5, -2, type.cast(UpdateMatter.ON));
            }
        }

        @Override
        public void deleteSlices(Class<?> type) {
            if (type == MatterUpdate.class) {
                deleted = true;
            }
        }

        private boolean deleted() {
            return deleted;
        }
    }

    private static final class ChestUpdateMantleChunk extends MantleChunk<Matter> {
        @SuppressWarnings("unchecked")
        private final MatterSlice<MatterUpdate> slice = mock(MatterSlice.class);
        private final Matter section = mock(Matter.class);
        private boolean deleted;

        private ChestUpdateMantleChunk() {
            super(1, 0, 0, emptyAdapter(), MantleHooks.NONE);
            when(section.hasSlice(MatterUpdate.class)).thenReturn(true);
            when(section.getSlice(MatterUpdate.class)).thenReturn(slice);
        }

        @Override
        public Matter get(int index) {
            return section;
        }

        @Override
        public <T> void iterate(
                Class<T> type,
                Consumer4<Integer, Integer, Integer, T> iterator
        ) {
            if (type == MatterUpdate.class) {
                iterator.accept(1, 5, 1, type.cast(UpdateMatter.ON));
                iterator.accept(2, 5, 2, type.cast(UpdateMatter.ON));
            }
        }

        @Override
        public void deleteSlices(Class<?> type) {
            if (type == MatterUpdate.class) {
                deleted = true;
            }
        }

        private boolean deleted() {
            return deleted;
        }
    }

    @SuppressWarnings("unchecked")
    private static MantleDataAdapter<Matter> emptyAdapter() {
        return (MantleDataAdapter<Matter>) Proxy.newProxyInstance(
                MantleDataAdapter.class.getClassLoader(),
                new Class<?>[]{MantleDataAdapter.class},
                (proxy, method, arguments) -> {
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }
}
