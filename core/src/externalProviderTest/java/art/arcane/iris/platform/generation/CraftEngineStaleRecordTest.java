package art.arcane.iris.platform.generation;

import art.arcane.iris.generation.block.IrisCustomData;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.SeedManager;
import art.arcane.iris.integration.ExternalDataProvider;
import art.arcane.iris.integration.ExternalDataSVC;
import art.arcane.iris.integration.Identifier;
import art.arcane.iris.integration.data.CraftEngineDataProvider;
import art.arcane.iris.platform.bukkit.BukkitBlockResolution;
import art.arcane.iris.platform.registry.RegistryUtil;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.testsupport.BukkitTestServer;
import art.arcane.iris.world.IrisWorld;
import art.arcane.volmlib.util.function.Consumer4;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.api.CraftEngineFurniture;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.bukkit.api.event.CraftEngineReloadEvent;
import net.momirealms.craftengine.bukkit.entity.furniture.BukkitFurniture;
import net.momirealms.craftengine.bukkit.plugin.BukkitCraftEngine;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.entity.furniture.FurnitureDefinition;
import net.momirealms.craftengine.core.entity.furniture.FurnitureVariant;
import net.momirealms.craftengine.core.registry.Holder;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.invocation.InvocationOnMock;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CraftEngineStaleRecordTest {
    @Test
    public void stalePlacementRecordsAreSkippedAndIntactOnesPlaced() throws Exception {
        BukkitTestServer.install();
        BlockDefinition lampDefinition = mock(BlockDefinition.class);
        ImmutableBlockState lamp = mock(ImmutableBlockState.class);
        when(lamp.owner()).thenReturn(Holder.direct(lampDefinition));
        when(lamp.toString()).thenReturn("test:lamp");
        when(lampDefinition.defaultState()).thenReturn(lamp);
        FurnitureDefinition chairDefinition = mock(FurnitureDefinition.class);
        when(chairDefinition.id()).thenReturn(Key.of("test:chair"));
        when(chairDefinition.variants()).thenReturn(Map.of("alpha", mock(FurnitureVariant.class)));
        BlockData noteBlock = blockData(Material.NOTE_BLOCK);
        BlockData air = blockData(Material.AIR);
        CraftEngineDataProvider provider = new CraftEngineDataProvider();
        ExternalDataSVC service = new ExternalDataSVC();
        activate(service, provider);

        Engine engine = mock(Engine.class);
        when(engine.getWorld()).thenReturn(mock(IrisWorld.class));
        SeedManager seeds = mock(SeedManager.class);
        when(engine.getSeedManager()).thenReturn(seeds);
        World world = mock(World.class);
        when(world.getMinHeight()).thenReturn(0);
        when(world.getMaxHeight()).thenReturn(256);
        Chunk chunk = mock(Chunk.class);
        when(chunk.getWorld()).thenReturn(world);
        block(chunk, 1, Material.NOTE_BLOCK);
        block(chunk, 2, Material.AIR);
        block(chunk, 3, Material.COBBLESTONE);
        block(chunk, 4, Material.COBBLESTONE);

        try (MockedStatic<BukkitCraftEngine> craftEngine = mockStatic(BukkitCraftEngine.class);
             MockedStatic<CraftEngineBlocks> blocks = mockStatic(CraftEngineBlocks.class);
             MockedStatic<CraftEngineFurniture> furniture = mockStatic(CraftEngineFurniture.class);
             MockedStatic<CraftEngineItems> items = mockStatic(CraftEngineItems.class);
             MockedStatic<RegistryUtil> registry = materialRegistry();
             MockedStatic<BukkitBlockResolution> resolution = mockStatic(BukkitBlockResolution.class);
             MockedStatic<IrisServices> services = mockStatic(IrisServices.class)) {
            resolution.when(BukkitBlockResolution::getAir).thenReturn(air);
            services.when(() -> IrisServices.get(ExternalDataSVC.class)).thenReturn(service);
            blocks.when(CraftEngineBlocks::loadedBlocks).thenReturn(Map.of(Key.of("test:lamp"), lampDefinition));
            furniture.when(CraftEngineFurniture::loadedFurniture).thenReturn(Map.of(Key.of("test:chair"), chairDefinition));
            items.when(CraftEngineItems::loadedItems).thenReturn(Map.of());
            blocks.when(() -> CraftEngineBlocks.byId(Key.of("test:lamp"))).thenReturn(lampDefinition);
            blocks.when(() -> CraftEngineBlocks.getBukkitBlockData(lamp)).thenReturn(noteBlock);
            blocks.when(() -> CraftEngineBlocks.place(any(Location.class), eq(lamp), eq(false))).thenReturn(true);
            furniture.when(() -> CraftEngineFurniture.byId(Key.of("test:chair"))).thenReturn(chairDefinition);
            furniture.when(() -> CraftEngineFurniture.place(any(Location.class), eq(chairDefinition), eq("alpha"), eq(false)))
                    .thenReturn(mock(BukkitFurniture.class));
            provider.onReload(mock(CraftEngineReloadEvent.class));
            Identifier lampRecord = generatedRecord(service, "test:lamp");
            Identifier chairRecord = generatedRecord(service, "test:chair");
            assertEquals("craftengine:test/lamp", lampRecord.toString());
            assertEquals("craftengine:test/chair[variant=alpha]", chairRecord.toString());

            EngineBukkitOps.materializeCustomBlocks(engine, chunk, records(
                    new Placed(1, lampRecord),
                    new Placed(2, lampRecord),
                    new Placed(3, lampRecord),
                    new Placed(4, chairRecord)));

            blocks.verify(() -> CraftEngineBlocks.place(any(Location.class), eq(lamp), eq(false)), times(1));
            blocks.verify(() -> CraftEngineBlocks.place(location(1), lamp, false));
            blocks.verify(() -> CraftEngineBlocks.place(location(2), lamp, false), never());
            blocks.verify(() -> CraftEngineBlocks.place(location(3), lamp, false), never());
            furniture.verify(() -> CraftEngineFurniture.place(location(4), chairDefinition, "alpha", false));
        }
    }

    private static MockedStatic<RegistryUtil> materialRegistry() {
        MockedStatic<RegistryUtil> registry = mockStatic(RegistryUtil.class);
        registry.when(() -> RegistryUtil.find(Material.class, "grass", "short_grass")).thenReturn(Material.SHORT_GRASS);
        return registry;
    }

    private static Identifier generatedRecord(ExternalDataSVC service, String key) {
        BlockData resolved = service.getBlockData(Identifier.fromString(key)).orElseThrow();
        return ((IrisCustomData) resolved).getCustom();
    }

    private static BlockData blockData(Material material) {
        BlockData data = mock(BlockData.class);
        when(data.getMaterial()).thenReturn(material);
        return data;
    }

    private static Block block(Chunk chunk, int offset, Material type) {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(type);
        when(block.getX()).thenReturn(offset);
        when(block.getY()).thenReturn(10);
        when(block.getZ()).thenReturn(offset);
        when(block.getLocation()).thenAnswer((InvocationOnMock invocation) -> location(offset));
        when(chunk.getBlock(offset, 10, offset)).thenReturn(block);
        return block;
    }

    private static Location location(int offset) {
        return new Location(null, offset, 10, offset);
    }

    @SuppressWarnings("unchecked")
    private static MantleChunk<Matter> records(Placed... records) {
        MantleChunk<Matter> chunk = mock(MantleChunk.class);
        doAnswer((InvocationOnMock invocation) -> {
            Consumer4<Integer, Integer, Integer, Identifier> iterator = invocation.getArgument(1);
            for (Placed record : records) {
                iterator.accept(record.offset(), 10, record.offset(), record.identifier());
            }
            return null;
        }).when(chunk).iterate(eq(Identifier.class), any());
        return chunk;
    }

    @SuppressWarnings("unchecked")
    private static void activate(ExternalDataSVC service, ExternalDataProvider provider) throws Exception {
        Field field = ExternalDataSVC.class.getDeclaredField("activeProviders");
        field.setAccessible(true);
        ((List<ExternalDataProvider>) field.get(service)).add(provider);
    }

    private record Placed(int offset, Identifier identifier) {
    }
}
