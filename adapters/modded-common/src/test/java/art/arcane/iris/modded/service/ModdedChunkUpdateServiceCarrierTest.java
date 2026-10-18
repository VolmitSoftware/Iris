package art.arcane.iris.modded.service;

import art.arcane.iris.generation.mantle.EngineMantle;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.EngineMetrics;
import art.arcane.iris.integration.Identifier;
import art.arcane.iris.modded.api.ModdedBlockData;
import art.arcane.iris.modded.api.ModdedBlockPlacementContext;
import art.arcane.iris.modded.api.ModdedCustomContentRegistry;
import art.arcane.iris.modded.api.ModdedDataProvider;
import art.arcane.iris.modded.api.ModdedDataType;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.world.IrisWorld;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.ModdedBlockState;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import art.arcane.volmlib.util.function.Consumer4;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.mantle.runtime.MantleDataAdapter;
import art.arcane.volmlib.util.mantle.runtime.MantleHooks;
import art.arcane.volmlib.util.matter.Matter;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.invocation.InvocationOnMock;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class ModdedChunkUpdateServiceCarrierTest {
    private static final String MOD_ID = "iris_carrier_guard_test";
    private static final String LAMP = MOD_ID + ":lamp";
    private static final String CHAIR = MOD_ID + ":chair";
    private static final String FIRE = MOD_ID + ":fire";
    private static final String SOUL_FIRE = MOD_ID + ":soul_fire";
    private static final List<BlockPos> PLACED = new CopyOnWriteArrayList<>();

    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ModdedCustomContentRegistry.register(new CarrierProvider());
    }

    @Before
    public void clearPlacements() {
        PLACED.clear();
    }

    @Test
    public void staleRecordsAreSkippedWhileIntactAndAirCarrierRecordsArePlaced() {
        RecordMantleChunk chunk = new RecordMantleChunk(List.of(
                new Placed(1, LAMP),
                new Placed(2, LAMP),
                new Placed(3, LAMP),
                new Placed(4, CHAIR)));
        NativeWorld level = level(Map.of(
                1, Blocks.NOTE_BLOCK.defaultBlockState().setValue(NoteBlock.NOTE, 7),
                2, Blocks.AIR.defaultBlockState(),
                3, Blocks.COBBLESTONE.defaultBlockState(),
                4, Blocks.COBBLESTONE.defaultBlockState()));
        List<String> debug = new ArrayList<>();
        Engine engine = engine(chunk);

        try (MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class)) {
            logging.when(() -> IrisLogging.debug(anyString(), any(Object[].class))).thenAnswer((InvocationOnMock invocation) -> {
                Object[] arguments = invocation.getArguments();
                debug.add(String.format((String) arguments[0], Arrays.copyOfRange(arguments, 1, arguments.length)));
                return null;
            });

            new ModdedChunkUpdateService().updateRegeneratedChunk(engine, level, engine.getMantle().getMantle(), 0, 0);
        }

        assertEquals(List.of(new BlockPos(1, 6, 1), new BlockPos(4, 6, 4)), PLACED);
        assertTrue(chunk.isFlagged(MantleFlag.ETCHED));
        assertTrue(chunk.customDeleted());
        assertEquals(1, debug.size());
        assertTrue(debug.getFirst(), debug.getFirst().contains("2 stale"));
    }

    @Test
    public void extinguishedFireCarriersArePlacedWhileCoveredCarriersAreSkipped() {
        RecordMantleChunk chunk = new RecordMantleChunk(List.of(
                new Placed(1, FIRE), new Placed(2, SOUL_FIRE),
                new Placed(3, FIRE), new Placed(4, SOUL_FIRE)));
        NativeWorld level = level(Map.of(
                1, Blocks.AIR.defaultBlockState(),
                2, Blocks.CAVE_AIR.defaultBlockState(),
                3, Blocks.COBBLESTONE.defaultBlockState(),
                4, Blocks.COBBLESTONE.defaultBlockState()));
        Engine engine = engine(chunk);

        new ModdedChunkUpdateService().updateRegeneratedChunk(engine, level, engine.getMantle().getMantle(), 0, 0);

        assertEquals(List.of(new BlockPos(1, 6, 1), new BlockPos(2, 6, 2)), PLACED);
        assertTrue(chunk.customDeleted());
    }

    private static NativeWorld level(Map<Integer, BlockState> live) {
        ServerLevel serverLevel = mock(ServerLevel.class);
        when(serverLevel.getBlockState(any(BlockPos.class))).thenReturn(Blocks.STONE.defaultBlockState());
        NativeWorld level = mock(NativeWorld.class);
        when(level.name()).thenReturn("example:carriers");
        when(level.minHeight()).thenReturn(-64);
        when(level.maxHeight()).thenReturn(320);
        when(level.nativeHandle()).thenReturn(serverLevel);
        for (Map.Entry<Integer, BlockState> entry : live.entrySet()) {
            int offset = entry.getKey();
            when(level.getBlock(offset, 6, offset)).thenReturn(ModdedBlockState.of(entry.getValue(), null));
        }
        return level;
    }

    private static Engine engine(RecordMantleChunk chunk) {
        @SuppressWarnings("unchecked")
        Mantle<Matter> mantle = mock(Mantle.class);
        when(mantle.isChunkLoaded(0, 0)).thenReturn(true);
        when(mantle.hasFlag(0, 0, MantleFlag.ETCHED)).thenAnswer((InvocationOnMock invocation) -> chunk.isFlagged(MantleFlag.ETCHED));
        when(mantle.useChunk(0, 0)).thenAnswer((InvocationOnMock invocation) -> chunk.use());
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

    private record Placed(int offset, String key) {
    }

    private static final class RecordMantleChunk extends MantleChunk<Matter> {
        private final List<Placed> records;
        private boolean customDeleted;

        private RecordMantleChunk(List<Placed> records) {
            super(1, 0, 0, emptyAdapter(), MantleHooks.NONE);
            this.records = records;
        }

        @Override
        public <T> void iterate(Class<T> type, Consumer4<Integer, Integer, Integer, T> iterator) {
            if (type != Identifier.class) {
                return;
            }
            for (Placed record : records) {
                iterator.accept(record.offset(), 70, record.offset(), type.cast(Identifier.fromString(record.key())));
            }
        }

        @Override
        public <T> T get(int x, int y, int z, Class<T> type) {
            if (type == Identifier.class && y == 70 && x == z) {
                for (Placed record : records) {
                    if (record.offset() == x) {
                        return type.cast(Identifier.fromString(record.key()));
                    }
                }
            }
            return null;
        }

        @Override
        public void deleteSlices(Class<?> type) {
            if (type == Identifier.class) {
                customDeleted = true;
            }
        }

        private boolean customDeleted() {
            return customDeleted;
        }
    }

    private static final class CarrierProvider implements ModdedDataProvider {
        @Override
        public String modId() {
            return MOD_ID;
        }

        @Override
        public Collection<String> getTypes(ModdedDataType type) {
            return type == ModdedDataType.BLOCK ? List.of(LAMP, CHAIR, FIRE, SOUL_FIRE) : List.of();
        }

        @Override
        public boolean isValidProvider(String id, ModdedDataType type) {
            return type == ModdedDataType.BLOCK && getTypes(type).contains(id);
        }

        @Override
        public ModdedBlockData getBlockData(String blockId, Map<String, String> state) {
            BlockState carrier = switch (blockId) {
                case CHAIR -> Blocks.AIR.defaultBlockState();
                case FIRE -> Blocks.FIRE.defaultBlockState();
                case SOUL_FIRE -> Blocks.SOUL_FIRE.defaultBlockState();
                default -> Blocks.NOTE_BLOCK.defaultBlockState();
            };
            return ModdedBlockData.deferred(ModdedBlockState.of(carrier, null));
        }

        @Override
        public String processBlockPlacement(ModdedBlockPlacementContext context) {
            PLACED.add(context.placement().position());
            return null;
        }
    }
}
