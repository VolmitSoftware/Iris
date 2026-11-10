package art.arcane.iris.modded.service;

import art.arcane.iris.core.link.Identifier;
import art.arcane.iris.engine.IrisEngine;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.world.IrisWorld;
import art.arcane.iris.modded.api.ModdedBlockData;
import art.arcane.iris.modded.api.ModdedBlockPlacementContext;
import art.arcane.iris.modded.api.ModdedCustomContentRegistry;
import art.arcane.iris.modded.api.ModdedDataProvider;
import art.arcane.iris.modded.api.ModdedDataType;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.function.Consumer4;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.mantle.runtime.MantleDataAdapter;
import art.arcane.volmlib.util.mantle.runtime.MantleHooks;
import art.arcane.volmlib.util.matter.Matter;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.invocation.InvocationOnMock;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
    private static final net.minecraft.resources.Identifier LAMP =
            net.minecraft.resources.Identifier.parse(MOD_ID + ":lamp");
    private static final net.minecraft.resources.Identifier CHAIR =
            net.minecraft.resources.Identifier.parse(MOD_ID + ":chair");
    private static final int MIN_HEIGHT = -64;
    private static final int RECORD_Y = 70;
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
    public void staleRecordsAreSkippedWhileIntactAndAirCarrierRecordsArePlaced() throws Exception {
        RecordMantleChunk chunk = new RecordMantleChunk(List.of(
                new Placed(1, LAMP.toString()),
                new Placed(2, LAMP.toString()),
                new Placed(3, LAMP.toString()),
                new Placed(4, CHAIR.toString())));
        ServerLevel level = level(Map.of(
                1, Blocks.NOTE_BLOCK.defaultBlockState().setValue(NoteBlock.NOTE, 7),
                2, Blocks.AIR.defaultBlockState(),
                3, Blocks.COBBLESTONE.defaultBlockState(),
                4, Blocks.COBBLESTONE.defaultBlockState()));
        List<String> debug = new ArrayList<>();

        try (MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class)) {
            logging.when(() -> IrisLogging.debug(anyString(), any(Object[].class))).thenAnswer((InvocationOnMock invocation) -> {
                Object[] arguments = invocation.getArguments();
                debug.add(String.format((String) arguments[0], Arrays.copyOfRange(arguments, 1, arguments.length)));
                return null;
            });

            new ModdedChunkUpdateService().runCustomPass(engine(), level, 0, 0, chunk);
        }

        assertEquals(List.of(new BlockPos(1, RECORD_Y, 1), new BlockPos(4, RECORD_Y, 4)), PLACED);
        assertTrue(chunk.customDeleted());
        assertEquals(1, debug.size());
        assertTrue(debug.getFirst(), debug.getFirst().contains("2 stale"));
    }

    private static ServerLevel level(Map<Integer, BlockState> live) {
        ServerLevel level = mock(ServerLevel.class);
        when(level.getMinY()).thenReturn(MIN_HEIGHT);
        when(level.getMaxY()).thenReturn(320);
        when(level.dimension()).thenReturn(Level.OVERWORLD);
        when(level.getBlockState(any(BlockPos.class))).thenAnswer((InvocationOnMock invocation) -> {
            BlockPos position = invocation.getArgument(0);
            return live.getOrDefault(position.getX(), Blocks.STONE.defaultBlockState());
        });
        return level;
    }

    private static Engine engine() throws Exception {
        Field unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Object unsafe = unsafeField.get(null);
        Method allocateInstance = unsafe.getClass().getMethod("allocateInstance", Class.class);
        WorldEngine engine = (WorldEngine) allocateInstance.invoke(unsafe, WorldEngine.class);
        engine.world = IrisWorld.builder().minHeight(MIN_HEIGHT).build();
        return engine;
    }

    private static final class WorldEngine extends IrisEngine {
        private IrisWorld world;

        private WorldEngine() {
            super(null, InitializationMode.RUNTIME);
        }

        @Override
        public IrisWorld getWorld() {
            return world;
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
                iterator.accept(record.offset(), RECORD_Y - MIN_HEIGHT, record.offset(),
                        type.cast(Identifier.fromString(record.key())));
            }
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
        public Collection<net.minecraft.resources.Identifier> getTypes(ModdedDataType type) {
            return type == ModdedDataType.BLOCK ? List.of(LAMP, CHAIR) : List.of();
        }

        @Override
        public boolean isValidProvider(net.minecraft.resources.Identifier id, ModdedDataType type) {
            return type == ModdedDataType.BLOCK && (LAMP.equals(id) || CHAIR.equals(id));
        }

        @Override
        public ModdedBlockData getBlockData(net.minecraft.resources.Identifier blockId, Map<String, String> state) {
            BlockState carrier = CHAIR.equals(blockId) ? Blocks.AIR.defaultBlockState() : Blocks.NOTE_BLOCK.defaultBlockState();
            return ModdedBlockData.deferred(carrier);
        }

        @Override
        public void processBlockPlacement(ModdedBlockPlacementContext context) {
            PLACED.add(context.position());
        }
    }
}
