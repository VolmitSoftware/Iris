package art.arcane.iris.modded.service;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.ModdedPlatformWorld;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.generation.mantle.EngineMantle;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.EngineMetrics;
import art.arcane.iris.modded.IrisModdedChunkGenerator;
import art.arcane.iris.modded.ModdedLootApplier;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.world.IrisWorld;
import art.arcane.iris.world.history.SavedBiomeUnavailableException;
import art.arcane.iris.world.storage.matter.TileWrapper;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedServer;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeWorldGenerators;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeWorldMaintenance;
import art.arcane.volmlib.nativelib.terrain.NativeBlockPoint;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import art.arcane.volmlib.util.function.Consumer4;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
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
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.invocation.InvocationOnMock;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongConsumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
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
            loot.when(() -> ModdedLootApplier.apply(any(), any(), any(), any(), any())).thenAnswer((InvocationOnMock invocation) -> {
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
    public void loadingSavedBiomeLeavesTheChunkUnetchedWithoutAnErrorAndRetriesOnTheNextPass() {
        ChestUpdateMantleChunk chunk = new ChestUpdateMantleChunk();
        Engine engine = updateEngine(chunk);
        NativeWorld level = chestLevel();
        AtomicInteger loadingAttempts = new AtomicInteger();
        ModdedChunkUpdateService service = new ModdedChunkUpdateService();

        try (MockedStatic<ModdedLootApplier> loot = mockStatic(ModdedLootApplier.class);
             MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class)) {
            loot.when(() -> ModdedLootApplier.apply(any(), any(), any(), any(), any())).thenAnswer((InvocationOnMock invocation) -> {
                NativeBlockPoint position = invocation.getArgument(2);
                if (position.x() == 2 && loadingAttempts.getAndIncrement() == 0) {
                    throw new SavedBiomeUnavailableException("Saved biome information is loading at chunk 0,0.", true);
                }
                return null;
            });

            service.updateRegeneratedChunk(engine, level, 0, 0);

            assertFalse(chunk.isFlagged(MantleFlag.ETCHED));
            assertFalse(chunk.isFlagged(MantleFlag.UPDATE));
            assertFalse(chunk.deleted());
            logging.verify(() -> IrisLogging.reportError(any(Throwable.class)), never());
            logging.verify(() -> IrisLogging.reportError(anyString(), any(Throwable.class)), never());

            service.updateRegeneratedChunk(engine, level, 0, 0);

            assertTrue(chunk.isFlagged(MantleFlag.ETCHED));
            assertTrue(chunk.deleted());
        }
    }

    @Test
    public void permanentSavedBiomeUnavailabilityCompletesThePassWithAWarning() {
        ChestUpdateMantleChunk chunk = new ChestUpdateMantleChunk();
        Engine engine = updateEngine(chunk);
        NativeWorld level = chestLevel();
        String reason = "This chunk has no exact saved Iris biome assignment for generation 3.";

        try (MockedStatic<ModdedLootApplier> loot = mockStatic(ModdedLootApplier.class);
             MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class)) {
            loot.when(() -> ModdedLootApplier.apply(any(), any(), any(), any(), any())).thenAnswer((InvocationOnMock invocation) -> {
                NativeBlockPoint position = invocation.getArgument(2);
                if (position.x() == 2) {
                    throw new SavedBiomeUnavailableException(reason, false);
                }
                return null;
            });

            new ModdedChunkUpdateService().updateRegeneratedChunk(engine, level, 0, 0);

            assertTrue(chunk.isFlagged(MantleFlag.ETCHED));
            assertTrue(chunk.deleted());
            logging.verify(() -> IrisLogging.warnOnce(anyString(), contains(reason)));
            logging.verify(() -> IrisLogging.reportError(any(Throwable.class)), never());
            logging.verify(() -> IrisLogging.reportError(anyString(), any(Throwable.class)), never());
        }
    }

    @Test
    public void failedChunkDoesNotStopTheLevelPassForItsOtherChunks() {
        IrisSettings previousSettings = IrisSettings.settings;
        IrisSettings.settings = new IrisSettings();
        FailingMantleChunk failing = new FailingMantleChunk();
        ChestUpdateMantleChunk healthy = new ChestUpdateMantleChunk();
        @SuppressWarnings("unchecked")
        Mantle<Matter> mantle = mock(Mantle.class);
        when(mantle.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(mantle.useChunk(0, 0)).thenAnswer((InvocationOnMock invocation) -> failing.use());
        when(mantle.useChunk(4, 0)).thenAnswer((InvocationOnMock invocation) -> healthy.use());
        Engine engine = engineWith(mantle);
        NativeWorld level = chestLevel();
        NativeModdedServer server = mock(NativeModdedServer.class);
        when(server.worlds()).thenReturn(List.of(level));
        IrisModdedChunkGenerator generator = mock(IrisModdedChunkGenerator.class);
        when(generator.engineIfBound()).thenReturn(engine);

        try (MockedStatic<NativeWorldGenerators> generators = mockStatic(NativeWorldGenerators.class);
             MockedStatic<ModdedLootApplier> loot = mockStatic(ModdedLootApplier.class);
             MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class);
             MockedConstruction<NativeWorldMaintenance> maintenance = mockConstruction(NativeWorldMaintenance.class,
                     (NativeWorldMaintenance mocked, MockedConstruction.Context context) -> {
                         when(mocked.hasForcedChunks()).thenReturn(true);
                         doAnswer((InvocationOnMock invocation) -> {
                             LongConsumer action = invocation.getArgument(0);
                             action.accept(0L);
                             action.accept(4L);
                             return null;
                         }).when(mocked).forEachForcedChunk(any(LongConsumer.class));
                     })) {
            generators.when(() -> NativeWorldGenerators.find(level, IrisModdedChunkGenerator.class)).thenReturn(generator);

            new ModdedChunkUpdateService().onServerTick(server);

            assertFalse(failing.isFlagged(MantleFlag.ETCHED));
            assertTrue(healthy.isFlagged(MantleFlag.ETCHED));
            assertTrue(healthy.deleted());
            logging.verify(() -> IrisLogging.errorOnce(anyString(), anyString()));
        } finally {
            IrisSettings.settings = previousSettings;
        }
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

    private static Engine updateEngine(ChestUpdateMantleChunk chunk) {
        @SuppressWarnings("unchecked")
        Mantle<Matter> mantle = mock(Mantle.class);
        when(mantle.isChunkLoaded(0, 0)).thenReturn(true);
        when(mantle.hasFlag(0, 0, MantleFlag.ETCHED)).thenAnswer((InvocationOnMock invocation) -> chunk.isFlagged(MantleFlag.ETCHED));
        when(mantle.useChunk(0, 0)).thenAnswer((InvocationOnMock invocation) -> chunk.use());
        return engineWith(mantle);
    }

    private static Engine engineWith(Mantle<Matter> mantle) {
        IrisWorld world = mock(IrisWorld.class);
        when(world.minHeight()).thenReturn(-64);
        EngineMantle engineMantle = mock(EngineMantle.class);
        when(engineMantle.getMantle()).thenReturn(mantle);
        Engine engine = mock(Engine.class);
        when(engine.getWorld()).thenReturn(world);
        when(engine.getMetrics()).thenReturn(new EngineMetrics(8));
        when(engine.getMantle()).thenReturn(engineMantle);
        return engine;
    }

    private static NativeWorld chestLevel() {
        NativeBlockState chest = mock(NativeBlockState.class);
        when(chest.isStorage()).thenReturn(true);
        when(chest.isStorageChest()).thenReturn(true);
        NativeWorld level = mock(NativeWorld.class);
        when(level.name()).thenReturn("example:updates");
        when(level.minHeight()).thenReturn(-64);
        when(level.maxHeight()).thenReturn(320);
        when(level.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(level.getBlock(anyInt(), anyInt(), anyInt())).thenReturn(chest);
        return level;
    }

    private static final class FailingMantleChunk extends MantleChunk<Matter> {
        private FailingMantleChunk() {
            super(1, 0, 0, emptyAdapter(), MantleHooks.NONE);
        }

        @Override
        public <T> void iterate(
                Class<T> type,
                Consumer4<Integer, Integer, Integer, T> iterator
        ) {
            throw new IllegalStateException("corrupt " + type.getSimpleName() + " slice");
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
