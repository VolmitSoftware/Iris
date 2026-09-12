package art.arcane.iris.integration.data;

import art.arcane.iris.integration.Identifier;
import art.arcane.iris.integration.ExternalDataSVC;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.SeedManager;
import art.arcane.iris.platform.bukkit.BukkitBlockResolution;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.testsupport.BukkitTestServer;
import art.arcane.iris.generation.block.IrisCustomData;
import art.arcane.iris.platform.bukkit.registry.RegistryUtil;
import art.arcane.volmlib.util.collection.KMap;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.api.CraftEngineFurniture;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.bukkit.api.event.CraftEngineReloadEvent;
import net.momirealms.craftengine.bukkit.item.BukkitItemDefinition;
import net.momirealms.craftengine.bukkit.plugin.BukkitCraftEngine;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.property.BooleanProperty;
import net.momirealms.craftengine.core.entity.furniture.FurnitureDefinition;
import net.momirealms.craftengine.core.entity.furniture.FurnitureVariant;
import net.momirealms.craftengine.core.registry.Holder;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CraftEngineDataProviderTest {
    @Test
    public void startupWaitsForReloadEventAndEmptyLoadedRegistriesAreReady() {
        CraftEngineDataProvider provider = spy(new CraftEngineDataProvider());
        Plugin plugin = mock(Plugin.class);
        doReturn(plugin).when(provider).getPlugin();
        when(plugin.isEnabled()).thenReturn(true);
        try (MockedStatic<BukkitCraftEngine> engine = mockStatic(BukkitCraftEngine.class);
             MockedStatic<CraftEngineBlocks> blocks = mockStatic(CraftEngineBlocks.class);
             MockedStatic<CraftEngineFurniture> furniture = mockStatic(CraftEngineFurniture.class);
             MockedStatic<CraftEngineItems> items = mockStatic(CraftEngineItems.class)) {
            provider.init();

            assertFalse(provider.isReady());
            assertTrue(provider.getTypes(DataType.BLOCK).isEmpty());
            assertFalse(provider.isValidProvider(new Identifier("test", "lamp"), DataType.BLOCK));
            blocks.verifyNoInteractions();
            furniture.verifyNoInteractions();
            items.verifyNoInteractions();

            provider.onReload(mock(CraftEngineReloadEvent.class));

            assertTrue(provider.isReady());
            when(plugin.isEnabled()).thenReturn(false);
            assertFalse(provider.isReady());
        }
    }

    @Test
    public void lateInitializationUsesFullyLoadedFlagAndRefreshesExactClaims() {
        CraftEngineDataProvider provider = new CraftEngineDataProvider();
        BukkitCraftEngine plugin = mock(BukkitCraftEngine.class);
        when(plugin.isFullyLoaded()).thenReturn(true);
        Key oldKey = Key.of("test:old");
        Key newKey = Key.of("test:new");
        try (MockedStatic<BukkitCraftEngine> engine = mockStatic(BukkitCraftEngine.class);
             MockedStatic<CraftEngineBlocks> blocks = mockStatic(CraftEngineBlocks.class);
             MockedStatic<CraftEngineFurniture> furniture = mockStatic(CraftEngineFurniture.class);
             MockedStatic<CraftEngineItems> items = mockStatic(CraftEngineItems.class);
             MockedStatic<IrisServices> services = mockStatic(IrisServices.class)) {
            ExternalDataSVC service = mock(ExternalDataSVC.class);
            services.when(() -> IrisServices.getOrNull(ExternalDataSVC.class)).thenReturn(service);
            engine.when(BukkitCraftEngine::instance).thenReturn(plugin);
            blocks.when(CraftEngineBlocks::loadedBlocks).thenReturn(Map.of(oldKey, mock(BlockDefinition.class)));
            items.when(CraftEngineItems::loadedItems).thenReturn(Map.of(oldKey, mock(BukkitItemDefinition.class)));
            provider.init();

            assertTrue(provider.isValidProvider(new Identifier("test", "old"), DataType.BLOCK));
            assertTrue(provider.isValidProvider(new Identifier("test", "old"), DataType.ITEM));
            assertFalse(provider.isValidProvider(new Identifier("test", "missing"), DataType.ITEM));
            assertFalse(provider.isValidProvider(new Identifier("test", "old"), DataType.ENTITY));

            blocks.when(CraftEngineBlocks::loadedBlocks).thenReturn(Map.of());
            items.when(CraftEngineItems::loadedItems).thenReturn(Map.of(newKey, mock(BukkitItemDefinition.class)));
            furniture.when(CraftEngineFurniture::loadedFurniture).thenReturn(Map.of(newKey, mock(FurnitureDefinition.class)));
            when(plugin.isFullyLoaded()).thenReturn(false);
            when(plugin.isReloading()).thenReturn(true);
            provider.onReload(mock(CraftEngineReloadEvent.class));

            assertFalse(provider.isValidProvider(new Identifier("test", "old"), DataType.BLOCK));
            assertFalse(provider.isValidProvider(new Identifier("test", "old"), DataType.ITEM));
            assertEquals(Set.of(new Identifier("test", "new")), provider.getTypes(DataType.BLOCK));
            assertEquals(Set.of(new Identifier("test", "new")), provider.getTypes(DataType.ITEM));
            verify(service).notifyContentChanged();
        }
    }

    @Test
    public void blockPropertiesProduceNativeCarrierAndCanonicalIdentifier() {
        Identifier id = new Identifier("test", "lamp");
        BooleanProperty property = BooleanProperty.create("lit", false);
        BlockDefinition definition = mock(BlockDefinition.class);
        ImmutableBlockState initial = blockState(definition, "test:lamp[lit=false]");
        ImmutableBlockState lit = blockState(definition, "test:lamp[lit=true]");
        BlockData data = mock(BlockData.class);
        when(definition.defaultState()).thenReturn(initial);
        doReturn(property).when(definition).getProperty("lit");
        when(initial.with(property, true)).thenReturn(lit);
        KMap<String, String> properties = new KMap<>();
        properties.put("lit", "true");
        try (MockedStatic<CraftEngineBlocks> blocks = mockStatic(CraftEngineBlocks.class)) {
            blocks.when(() -> CraftEngineBlocks.byId(Key.of("test:lamp"))).thenReturn(definition);
            blocks.when(() -> CraftEngineBlocks.getBukkitBlockData(lit)).thenReturn(data);

            IrisCustomData result = (IrisCustomData) new CraftEngineDataProvider().getBlockData(id, properties);

            assertSame(data, result.getBase());
            assertEquals(new Identifier("test", "lamp[lit=true]"), result.getCustom());
            verify(initial).with(property, true);
        }
    }

    @Test
    public void unknownOrInvalidBlockPropertiesFailBeforeNativeConversion() {
        Identifier id = new Identifier("test", "lamp");
        BlockDefinition definition = mock(BlockDefinition.class);
        ImmutableBlockState initial = blockState(definition, "test:lamp[lit=false]");
        when(definition.defaultState()).thenReturn(initial);
        doReturn(BooleanProperty.create("lit", false)).when(definition).getProperty("lit");
        try (MockedStatic<CraftEngineBlocks> blocks = mockStatic(CraftEngineBlocks.class)) {
            blocks.when(() -> CraftEngineBlocks.byId(Key.of("test:lamp"))).thenReturn(definition);
            for (Map.Entry<String, String> entry : List.of(Map.entry("missing", "true"), Map.entry("lit", "yes"))) {
                KMap<String, String> properties = new KMap<>();
                properties.put(entry.getKey(), entry.getValue());
                assertThrows(MissingResourceException.class, () -> new CraftEngineDataProvider().getBlockData(id, properties));
            }
            blocks.verify(() -> CraftEngineBlocks.getBukkitBlockData(any()), times(0));
        }
    }

    @Test
    public void reverseCaptureUsesSemanticStateInsteadOfSyntheticNativeName() {
        BlockData nativeData = mock(BlockData.class);
        when(nativeData.getAsString()).thenReturn("craftengine:custom_12");
        ImmutableBlockState state = blockState(mock(BlockDefinition.class), "test:lamp[lit=true]");
        try (MockedStatic<CraftEngineBlocks> blocks = mockStatic(CraftEngineBlocks.class)) {
            blocks.when(() -> CraftEngineBlocks.getCustomBlockState(nativeData)).thenReturn(state);
            CraftEngineDataProvider provider = new CraftEngineDataProvider();

            assertEquals(new Identifier("test", "lamp[lit=true]"), provider.identifyBlock(nativeData).orElseThrow());
            assertTrue(provider.identifyBlock(mock(BlockData.class)).isEmpty());
        }
    }

    @Test
    public void currentSyntheticSlotResolvesOnlyWhenBoundToCustomContent() {
        CraftEngineDataProvider provider = new CraftEngineDataProvider();
        Identifier id = new Identifier("craftengine", "custom_12");
        BlockData data = mock(BlockData.class);
        ImmutableBlockState state = blockState(mock(BlockDefinition.class), "test:lamp[lit=true]");
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<CraftEngineBlocks> blocks = mockStatic(CraftEngineBlocks.class);
             MockedStatic<CraftEngineFurniture> furniture = mockStatic(CraftEngineFurniture.class);
             MockedStatic<CraftEngineItems> items = mockStatic(CraftEngineItems.class)) {
            bukkit.when(() -> Bukkit.createBlockData("craftengine:custom_12")).thenReturn(data);
            blocks.when(() -> CraftEngineBlocks.getCustomBlockState(data)).thenReturn(state);
            blocks.when(() -> CraftEngineBlocks.getBukkitBlockData(state)).thenReturn(data);
            provider.onReload(mock(CraftEngineReloadEvent.class));

            assertTrue(provider.isValidProvider(id, DataType.BLOCK));
            IrisCustomData result = (IrisCustomData) provider.getBlockData(id, new KMap<>());
            assertEquals(new Identifier("test", "lamp[lit=true]"), result.getCustom());

            blocks.when(() -> CraftEngineBlocks.getCustomBlockState(data)).thenReturn(null);
            assertFalse(provider.isValidProvider(id, DataType.BLOCK));
            assertThrows(MissingResourceException.class, () -> provider.getBlockData(id, new KMap<>()));
            bukkit.when(() -> Bukkit.createBlockData("craftengine:custom_12")).thenThrow(new IllegalArgumentException("Unknown block"));
            assertFalse(provider.isValidProvider(id, DataType.BLOCK));
        }
    }

    @Test
    public void deferredPlacementUsesCraftEngineAndAcceptsAlreadyMatchingState() {
        Identifier id = new Identifier("test", "lamp");
        BlockDefinition definition = mock(BlockDefinition.class);
        ImmutableBlockState state = blockState(definition, "test:lamp");
        when(definition.defaultState()).thenReturn(state);
        Block block = mock(Block.class);
        Location location = new Location(null, 17, 80, -12);
        when(block.getLocation()).thenReturn(location);
        try (MockedStatic<CraftEngineBlocks> blocks = mockStatic(CraftEngineBlocks.class)) {
            blocks.when(() -> CraftEngineBlocks.byId(Key.of("test:lamp"))).thenReturn(definition);
            blocks.when(() -> CraftEngineBlocks.getCustomBlockState(block)).thenReturn(state);

            new CraftEngineDataProvider().processUpdate(mock(Engine.class), block, id);

            blocks.verify(() -> CraftEngineBlocks.place(location, state, false));
            blocks.when(() -> CraftEngineBlocks.getCustomBlockState(block)).thenReturn(null);
            assertThrows(MissingResourceException.class, () -> new CraftEngineDataProvider().processUpdate(mock(Engine.class), block, id));
        }
    }

    @Test
    public void missingBlockAndFurnitureCannotBePlaced() {
        try (MockedStatic<CraftEngineBlocks> blocks = mockStatic(CraftEngineBlocks.class);
             MockedStatic<CraftEngineFurniture> furniture = mockStatic(CraftEngineFurniture.class)) {
            assertThrows(MissingResourceException.class, () -> new CraftEngineDataProvider().processUpdate(
                    mock(Engine.class), mock(Block.class), new Identifier("test", "missing")));
        }
    }

    @Test
    public void furnitureValidatesPropertiesAndStoresDeterministicDefaultVariant() {
        BukkitTestServer.install();
        FurnitureDefinition definition = furnitureDefinition();
        Identifier id = new Identifier("test", "chair");
        BlockData air = mock(BlockData.class);
        try (MockedStatic<CraftEngineBlocks> blocks = mockStatic(CraftEngineBlocks.class);
             MockedStatic<CraftEngineFurniture> furniture = mockStatic(CraftEngineFurniture.class);
             MockedStatic<RegistryUtil> registry = materialRegistry();
             MockedStatic<BukkitBlockResolution> resolution = mockStatic(BukkitBlockResolution.class)) {
            furniture.when(() -> CraftEngineFurniture.byId(Key.of("test:chair"))).thenReturn(definition);
            resolution.when(BukkitBlockResolution::getAir).thenReturn(air);
            CraftEngineDataProvider provider = new CraftEngineDataProvider();
            IrisCustomData result = (IrisCustomData) provider.getBlockData(id, new KMap<>());

            assertSame(air, result.getBase());
            assertEquals("test:chair[variant=alpha]", result.getCustom().toString());
            assertEquals("alpha", provider.getBlockProperties(id).stream()
                    .filter(property -> property.name().equals("variant")).findFirst().orElseThrow().defaultValue());
            for (Map.Entry<String, String> invalid : List.of(Map.entry("variant", "missing"), Map.entry("randomYaw", "yes"),
                    Map.entry("randomPitch", "1"), Map.entry("yaw", "NaN"), Map.entry("yaw", "Infinity"),
                    Map.entry("pitch", "360"), Map.entry("pitch", "-1"), Map.entry("yaw", "word"), Map.entry("unknown", "true"))) {
                KMap<String, String> properties = new KMap<>();
                properties.put(invalid.getKey(), invalid.getValue());
                assertThrows(MissingResourceException.class, () -> provider.getBlockData(id, properties));
            }
        }
    }

    @Test
    public void furniturePlacementUsesRepeatablePositionSeededRotation() {
        FurnitureDefinition definition = furnitureDefinition();
        Engine engine = mock(Engine.class);
        SeedManager seeds = mock(SeedManager.class);
        when(engine.getSeedManager()).thenReturn(seeds);
        when(seeds.getSeed()).thenReturn(1234L);
        Block block = mock(Block.class);
        when(block.getX()).thenReturn(7);
        when(block.getY()).thenReturn(80);
        when(block.getZ()).thenReturn(-9);
        when(block.getLocation()).thenAnswer(invocation -> new Location(null, 7, 80, -9));
        Identifier id = new Identifier("test", "chair[randomYaw=true,randomPitch=true,variant=beta]");
        try (MockedStatic<CraftEngineBlocks> blocks = mockStatic(CraftEngineBlocks.class);
             MockedStatic<CraftEngineFurniture> furniture = mockStatic(CraftEngineFurniture.class)) {
            furniture.when(() -> CraftEngineFurniture.byId(Key.of("test:chair"))).thenReturn(definition);
            Location[] observed = new Location[2];
            int[] count = new int[1];
            furniture.when(() -> CraftEngineFurniture.place(any(Location.class), eq(definition), eq("beta"), eq(false)))
                    .thenAnswer(invocation -> {
                        observed[count[0]++] = invocation.getArgument(0, Location.class).clone();
                        return null;
                    });
            CraftEngineDataProvider provider = new CraftEngineDataProvider();
            assertThrows(MissingResourceException.class, () -> provider.processUpdate(engine, block, id));
            assertThrows(MissingResourceException.class, () -> provider.processUpdate(engine, block, id));

            assertEquals(observed[0], observed[1]);
            assertTrue(observed[0].getYaw() >= 0 && observed[0].getYaw() < 360);
            assertTrue(observed[0].getPitch() >= 0 && observed[0].getPitch() < 360);
        }
    }

    @Test
    public void itemsAreBuiltThroughCraftEngineAndMissingItemsFail() {
        BukkitItemDefinition definition = mock(BukkitItemDefinition.class);
        ItemStack stack = mock(ItemStack.class);
        when(definition.buildBukkitItem()).thenReturn(stack);
        try (MockedStatic<CraftEngineItems> items = mockStatic(CraftEngineItems.class)) {
            items.when(() -> CraftEngineItems.byId(Key.of("test:lamp"))).thenReturn(definition);
            CraftEngineDataProvider provider = new CraftEngineDataProvider();

            assertSame(stack, provider.getItemStack(new Identifier("test", "lamp"), new KMap<>()));
            assertThrows(MissingResourceException.class, () -> provider.getItemStack(new Identifier("test", "missing"), new KMap<>()));
        }
    }

    @Test
    public void directPlacementUsesCustomBlockPropertiesWithoutAnEngine() {
        BlockDefinition definition = mock(BlockDefinition.class);
        BooleanProperty property = BooleanProperty.create("lit", false);
        ImmutableBlockState initial = blockState(definition, "test:lamp[lit=false]");
        ImmutableBlockState lit = blockState(definition, "test:lamp[lit=true]");
        when(definition.defaultState()).thenReturn(initial);
        doReturn(property).when(definition).getProperty("lit");
        when(initial.with(property, true)).thenReturn(lit);
        Block block = mock(Block.class);
        Location location = new Location(null, 7, 80, -9);
        when(block.getLocation()).thenReturn(location);
        try (MockedStatic<CraftEngineBlocks> blocks = mockStatic(CraftEngineBlocks.class)) {
            blocks.when(() -> CraftEngineBlocks.byId(Key.of("test:lamp"))).thenReturn(definition);
            blocks.when(() -> CraftEngineBlocks.place(location, lit, false)).thenReturn(true);

            assertTrue(new CraftEngineDataProvider().placeBlock(block, new Identifier("test", "lamp[lit=true]")));

            blocks.verify(() -> CraftEngineBlocks.place(location, lit, false));
        }
    }

    @Test
    public void directFurniturePlacementDoesNotSpawnEntities() {
        FurnitureDefinition definition = furnitureDefinition();
        Block block = mock(Block.class);
        try (MockedStatic<CraftEngineBlocks> blocks = mockStatic(CraftEngineBlocks.class);
             MockedStatic<CraftEngineFurniture> furniture = mockStatic(CraftEngineFurniture.class)) {
            furniture.when(() -> CraftEngineFurniture.byId(Key.of("test:chair"))).thenReturn(definition);

            assertFalse(new CraftEngineDataProvider().placeBlock(block, new Identifier("test", "chair[variant=alpha]")));
            assertThrows(MissingResourceException.class, () -> new CraftEngineDataProvider().placeBlock(
                    block, new Identifier("test", "chair[variant=missing]")));

            furniture.verify(() -> CraftEngineFurniture.place(any(Location.class), any(FurnitureDefinition.class), any(String.class), eq(false)), times(0));
            verify(block, times(0)).getLocation();
        }
    }

    private static MockedStatic<RegistryUtil> materialRegistry() {
        MockedStatic<RegistryUtil> registry = mockStatic(RegistryUtil.class);
        registry.when(() -> RegistryUtil.find(Material.class, "grass", "short_grass")).thenReturn(Material.SHORT_GRASS);
        return registry;
    }

    private static ImmutableBlockState blockState(BlockDefinition definition, String semanticId) {
        ImmutableBlockState state = mock(ImmutableBlockState.class);
        when(state.owner()).thenReturn(Holder.direct(definition));
        when(state.toString()).thenReturn(semanticId);
        return state;
    }

    private static FurnitureDefinition furnitureDefinition() {
        FurnitureDefinition definition = mock(FurnitureDefinition.class);
        when(definition.id()).thenReturn(Key.of("test:chair"));
        when(definition.variants()).thenReturn(Map.of("beta", mock(FurnitureVariant.class), "alpha", mock(FurnitureVariant.class)));
        return definition;
    }
}
