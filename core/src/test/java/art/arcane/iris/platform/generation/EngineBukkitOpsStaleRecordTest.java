package art.arcane.iris.platform.generation;

import art.arcane.iris.generation.block.IrisCustomData;
import art.arcane.iris.generation.block.TileData;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.integration.ExternalDataSVC;
import art.arcane.iris.integration.Identifier;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.iris.world.IrisWorld;
import art.arcane.iris.world.storage.matter.TileWrapper;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.function.Consumer4;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.junit.ClassRule;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.invocation.InvocationOnMock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class EngineBukkitOpsStaleRecordTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    private static final Identifier ORE = Identifier.fromString("itemsadder:forest/amber_ore");
    private static final Identifier CHAIR = Identifier.fromString("craftengine:test/chair[variant=alpha]");

    @Test
    public void customRecordOverFlattenedAirIsNotPlaced() {
        Fixture fixture = new Fixture();
        fixture.carrier(ORE, Material.NOTE_BLOCK);
        Block flattened = fixture.block(1, 10, 1, Material.AIR);
        MantleChunk<Matter> records = customRecords(new Placed<>(1, 10, 1, ORE));

        fixture.materializeCustomBlocks(records);

        verify(fixture.service, never()).processUpdate(any(), eq(flattened), any());
        verify(records).deleteSlices(Identifier.class);
    }

    @Test
    public void customRecordOverReplacedBlockIsNotPlaced() {
        Fixture fixture = new Fixture();
        fixture.carrier(ORE, Material.NOTE_BLOCK);
        Block wall = fixture.block(2, 10, 2, Material.COBBLESTONE);
        MantleChunk<Matter> records = customRecords(new Placed<>(2, 10, 2, ORE));

        fixture.materializeCustomBlocks(records);

        verify(fixture.service, never()).processUpdate(any(), eq(wall), any());
        verify(records).deleteSlices(Identifier.class);
    }

    @Test
    public void customRecordOverIntactCarrierIsPlaced() {
        Fixture fixture = new Fixture();
        fixture.carrier(ORE, Material.NOTE_BLOCK);
        Block intact = fixture.block(3, 10, 3, Material.NOTE_BLOCK);

        fixture.materializeCustomBlocks(customRecords(new Placed<>(3, 10, 3, ORE)));

        verify(fixture.service).processUpdate(fixture.engine, intact, ORE);
    }

    @Test
    public void airCarrierFurnitureRecordIsPlacedWhateverTheLiveBlock() {
        Fixture fixture = new Fixture();
        fixture.carrier(CHAIR, Material.AIR);
        Block open = fixture.block(4, 10, 4, Material.AIR);
        Block covered = fixture.block(5, 10, 5, Material.COBBLESTONE);

        fixture.materializeCustomBlocks(customRecords(
                new Placed<>(4, 10, 4, CHAIR),
                new Placed<>(5, 10, 5, CHAIR)));

        verify(fixture.service).processUpdate(fixture.engine, open, CHAIR);
        verify(fixture.service).processUpdate(fixture.engine, covered, CHAIR);
    }

    @Test
    public void unresolvableCarrierLeavesPlacementToTheProvider() {
        Fixture fixture = new Fixture();
        when(fixture.service.getBlockData(ORE)).thenReturn(Optional.empty());
        Block block = fixture.block(6, 10, 6, Material.STONE);

        fixture.materializeCustomBlocks(customRecords(new Placed<>(6, 10, 6, ORE)));

        verify(fixture.service).processUpdate(fixture.engine, block, ORE);
    }

    @Test
    public void carrierResolvesOncePerIdentifierAndStaleRecordsLogOncePerChunk() {
        Fixture fixture = new Fixture();
        fixture.carrier(ORE, Material.NOTE_BLOCK);
        fixture.block(1, 10, 1, Material.AIR);
        fixture.block(2, 10, 2, Material.COBBLESTONE);
        Block intact = fixture.block(3, 10, 3, Material.NOTE_BLOCK);
        MantleChunk<Matter> records = customRecords(
                new Placed<>(1, 10, 1, ORE),
                new Placed<>(2, 10, 2, ORE),
                new Placed<>(3, 10, 3, ORE));

        List<String> debug = fixture.materializeCustomBlocksCapturingDebug(records);

        verify(fixture.service, times(1)).getBlockData(ORE);
        verify(fixture.service, times(1)).processUpdate(any(), any(), any());
        verify(fixture.service).processUpdate(fixture.engine, intact, ORE);
        assertEquals(1, debug.size());
        assertTrue(debug.getFirst(), debug.getFirst().contains("2 stale"));
    }

    @Test
    public void staleTileRecordIsSkippedWithoutWarning() {
        Fixture fixture = new Fixture();
        TileData chest = chestTile();
        Block replaced = fixture.block(7, 10, 7, Material.STONE);
        BlockData stone = blockData(Material.STONE);
        when(replaced.getBlockData()).thenReturn(stone);

        try (MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class)) {
            EngineBukkitOps.materializeTiles(fixture.engine, fixture.chunk, tileRecords(new Placed<>(7, 10, 7, chest)));

            logging.verify(() -> IrisLogging.warn(anyString(), any(Object[].class)), never());
            logging.verify(() -> IrisLogging.debug(anyString(), any(Object[].class)), times(1));
        }
        verify(chest, never()).toBukkitTry(any());
    }

    @Test
    public void intactTileRecordIsApplied() {
        Fixture fixture = new Fixture();
        TileData chest = chestTile();
        Block intact = fixture.block(8, 10, 8, Material.CHEST);
        BlockData chestData = blockData(Material.CHEST);
        when(intact.getBlockData()).thenReturn(chestData);
        when(intact.getState()).thenReturn(mock(TileState.class));
        doReturn(true).when(chest).toBukkitTry(intact);

        try (MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class)) {
            EngineBukkitOps.materializeTiles(fixture.engine, fixture.chunk, tileRecords(new Placed<>(8, 10, 8, chest)));

            logging.verify(() -> IrisLogging.warn(anyString(), any(Object[].class)), never());
        }
        verify(chest).toBukkitTry(intact);
    }

    private static TileData chestTile() {
        TileData tile = spy(new TileData("minecraft:chest", new KMap<>()));
        doReturn(Material.CHEST).when(tile).resolveMaterial();
        return tile;
    }

    private static BlockData blockData(Material material) {
        BlockData data = mock(BlockData.class);
        when(data.getMaterial()).thenReturn(material);
        return data;
    }

    @SafeVarargs
    private static MantleChunk<Matter> customRecords(Placed<Identifier>... records) {
        return records(Identifier.class, records);
    }

    @SafeVarargs
    private static MantleChunk<Matter> tileRecords(Placed<TileData>... records) {
        List<Placed<TileWrapper>> wrapped = new ArrayList<>(records.length);
        for (Placed<TileData> record : records) {
            wrapped.add(new Placed<>(record.x(), record.y(), record.z(), new TileWrapper(record.value())));
        }
        return records(TileWrapper.class, wrapped.toArray(Placed[]::new));
    }

    @SuppressWarnings("unchecked")
    private static <T> MantleChunk<Matter> records(Class<T> type, Placed<T>[] records) {
        MantleChunk<Matter> chunk = mock(MantleChunk.class);
        doAnswer((InvocationOnMock invocation) -> {
            Consumer4<Integer, Integer, Integer, T> iterator = invocation.getArgument(1);
            for (Placed<T> record : records) {
                iterator.accept(record.x(), record.y(), record.z(), record.value());
            }
            return null;
        }).when(chunk).iterate(eq(type), any());
        return chunk;
    }

    private record Placed<T>(int x, int y, int z, T value) {
    }

    private static final class Fixture {
        private final Engine engine = mock(Engine.class);
        private final Chunk chunk = mock(Chunk.class);
        private final ExternalDataSVC service = mock(ExternalDataSVC.class);

        private Fixture() {
            IrisWorld irisWorld = mock(IrisWorld.class);
            when(engine.getWorld()).thenReturn(irisWorld);
            World world = mock(World.class);
            when(world.getMinHeight()).thenReturn(0);
            when(world.getMaxHeight()).thenReturn(256);
            when(world.getName()).thenReturn("example");
            when(chunk.getWorld()).thenReturn(world);
        }

        private void carrier(Identifier identifier, Material material) {
            BlockData carrier = IrisCustomData.of(blockData(material), identifier);
            when(service.getBlockData(identifier)).thenReturn(Optional.of(carrier));
        }

        private Block block(int x, int y, int z, Material type) {
            Block block = mock(Block.class);
            when(block.getType()).thenReturn(type);
            when(chunk.getBlock(x, y, z)).thenReturn(block);
            return block;
        }

        private void materializeCustomBlocks(MantleChunk<Matter> records) {
            try (MockedStatic<IrisServices> services = mockStatic(IrisServices.class)) {
                services.when(() -> IrisServices.get(ExternalDataSVC.class)).thenReturn(service);
                EngineBukkitOps.materializeCustomBlocks(engine, chunk, records);
            }
        }

        private List<String> materializeCustomBlocksCapturingDebug(MantleChunk<Matter> records) {
            List<String> messages = new ArrayList<>();
            try (MockedStatic<IrisServices> services = mockStatic(IrisServices.class);
                 MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class)) {
                services.when(() -> IrisServices.get(ExternalDataSVC.class)).thenReturn(service);
                logging.when(() -> IrisLogging.debug(anyString(), any(Object[].class))).thenAnswer((InvocationOnMock invocation) -> {
                    Object[] arguments = invocation.getArguments();
                    messages.add(String.format((String) arguments[0], Arrays.copyOfRange(arguments, 1, arguments.length)));
                    return null;
                });
                EngineBukkitOps.materializeCustomBlocks(engine, chunk, records);
            }
            return messages;
        }
    }
}
