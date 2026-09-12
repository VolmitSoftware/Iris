package art.arcane.iris.integration.data;

import art.arcane.iris.integration.Identifier;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.block.IrisCustomData;
import art.arcane.volmlib.util.collection.KMap;
import dev.lone.itemsadder.api.CustomBlock;
import dev.lone.itemsadder.api.CustomStack;
import dev.lone.itemsadder.api.Events.ItemsAdderLoadDataEvent;
import dev.lone.itemsadder.api.ItemsAdder;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.MissingResourceException;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class ItemAdderDataProviderTest {
    @Test
    public void loadedEmptyRegistryIsReadyAndDisabledPluginIsNot() {
        ItemAdderDataProvider provider = spy(new ItemAdderDataProvider());
        Plugin plugin = mock(Plugin.class);
        doReturn(plugin).when(provider).getPlugin();
        when(plugin.isEnabled()).thenReturn(true);
        try (MockedStatic<ItemsAdder> api = mockStatic(ItemsAdder.class);
             MockedStatic<CustomStack> items = mockStatic(CustomStack.class);
             MockedStatic<CustomBlock> blocks = mockStatic(CustomBlock.class)) {
            api.when(ItemsAdder::areItemsLoaded).thenReturn(false);
            provider.init();
            assertFalse(provider.isReady());

            items.when(CustomStack::getNamespacedIdsInRegistry).thenReturn(Set.of());
            blocks.when(CustomBlock::getNamespacedIdsInRegistry).thenReturn(Set.of());
            provider.onLoadData(mock(ItemsAdderLoadDataEvent.class));

            assertTrue(provider.isReady());
            assertTrue(provider.getTypes(DataType.BLOCK).isEmpty());
            when(plugin.isEnabled()).thenReturn(false);
            assertFalse(provider.isReady());
        }
    }

    @Test
    public void startupWaitsForLoadEventBeforeReadingRegistries() {
        ItemAdderDataProvider provider = new ItemAdderDataProvider();
        try (MockedStatic<ItemsAdder> api = mockStatic(ItemsAdder.class);
             MockedStatic<CustomStack> items = mockStatic(CustomStack.class);
             MockedStatic<CustomBlock> blocks = mockStatic(CustomBlock.class)) {
            api.when(ItemsAdder::areItemsLoaded).thenReturn(false);

            provider.init();

            assertTrue(provider.getTypes(DataType.ITEM).isEmpty());
            assertTrue(provider.getTypes(DataType.BLOCK).isEmpty());
            assertFalse(provider.isValidProvider(new Identifier("rocks", "ruby_ore"), DataType.BLOCK));
            items.verifyNoInteractions();
            blocks.verifyNoInteractions();

            items.when(CustomStack::getNamespacedIdsInRegistry).thenReturn(Set.of("rocks:ruby_ore"));
            blocks.when(CustomBlock::getNamespacedIdsInRegistry).thenReturn(Set.of("rocks:ruby_ore"));
            provider.onLoadData(mock(ItemsAdderLoadDataEvent.class));

            assertEquals(Set.of(new Identifier("rocks", "ruby_ore")), provider.getTypes(DataType.ITEM));
            assertEquals(Set.of(new Identifier("rocks", "ruby_ore")), provider.getTypes(DataType.BLOCK));
            assertTrue(provider.isValidProvider(new Identifier("rocks", "ruby_ore"), DataType.BLOCK));
        }
    }

    @Test
    public void blockClaimsUseExactRegistryIds() {
        ItemAdderDataProvider provider = new ItemAdderDataProvider();
        try (MockedStatic<ItemsAdder> api = mockStatic(ItemsAdder.class);
             MockedStatic<CustomStack> items = mockStatic(CustomStack.class);
             MockedStatic<CustomBlock> blocks = mockStatic(CustomBlock.class)) {
            api.when(ItemsAdder::areItemsLoaded).thenReturn(true);
            items.when(CustomStack::getNamespacedIdsInRegistry).thenReturn(Set.of("rocks:ruby_ore", "rocks:ruby_sword"));
            blocks.when(CustomBlock::getNamespacedIdsInRegistry).thenReturn(Set.of("rocks:ruby_ore"));

            provider.init();

            assertTrue(provider.isValidProvider(new Identifier("rocks", "ruby_ore"), DataType.BLOCK));
            assertFalse(provider.isValidProvider(new Identifier("rocks", "ruby_sword"), DataType.BLOCK));
            assertFalse(provider.isValidProvider(new Identifier("rocks", "unknown"), DataType.BLOCK));
            assertFalse(provider.isValidProvider(new Identifier("other", "ruby_ore"), DataType.BLOCK));
            assertFalse(provider.isValidProvider(new Identifier("rocks", "ruby_ore"), DataType.ENTITY));
        }
    }

    @Test
    public void loadEventReplacesBlockClaimsAfterRegistryChanges() {
        ItemAdderDataProvider provider = new ItemAdderDataProvider();
        try (MockedStatic<ItemsAdder> api = mockStatic(ItemsAdder.class);
             MockedStatic<CustomStack> items = mockStatic(CustomStack.class);
             MockedStatic<CustomBlock> blocks = mockStatic(CustomBlock.class)) {
            api.when(ItemsAdder::areItemsLoaded).thenReturn(true);
            items.when(CustomStack::getNamespacedIdsInRegistry).thenReturn(Set.of("rocks:ruby_ore"));
            blocks.when(CustomBlock::getNamespacedIdsInRegistry).thenReturn(Set.of("rocks:ruby_ore"));
            provider.init();
            items.when(CustomStack::getNamespacedIdsInRegistry).thenReturn(Set.of("gems:sapphire_ore"));
            blocks.when(CustomBlock::getNamespacedIdsInRegistry).thenReturn(Set.of("rocks:sapphire_ore"));

            assertEquals(Set.of(new Identifier("rocks", "ruby_ore")), provider.getTypes(DataType.ITEM));
            assertEquals(Set.of(new Identifier("rocks", "ruby_ore")), provider.getTypes(DataType.BLOCK));
            items.verify(CustomStack::getNamespacedIdsInRegistry);
            blocks.verify(CustomBlock::getNamespacedIdsInRegistry);

            provider.onLoadData(mock(ItemsAdderLoadDataEvent.class));

            assertFalse(provider.isValidProvider(new Identifier("rocks", "ruby_ore"), DataType.BLOCK));
            assertTrue(provider.isValidProvider(new Identifier("rocks", "sapphire_ore"), DataType.BLOCK));
            assertFalse(provider.isValidProvider(new Identifier("rocks", "ruby_ore"), DataType.ITEM));
            assertTrue(provider.isValidProvider(new Identifier("gems", "sapphire_ore"), DataType.ITEM));
            assertEquals(Set.of(new Identifier("gems", "sapphire_ore")), provider.getTypes(DataType.ITEM));
            assertEquals(Set.of(new Identifier("rocks", "sapphire_ore")), provider.getTypes(DataType.BLOCK));
        }
    }

    @Test
    public void resolutionRetainsCarrierAndNativeIdentifier() {
        Identifier identifier = new Identifier("rocks", "ruby_ore");
        CustomBlock customBlock = mock(CustomBlock.class);
        BlockData carrier = mock(BlockData.class);
        when(customBlock.getBaseBlockData()).thenReturn(carrier);
        try (MockedStatic<CustomBlock> blocks = mockStatic(CustomBlock.class)) {
            blocks.when(() -> CustomBlock.getInstance("rocks:ruby_ore")).thenReturn(customBlock);

            BlockData result = new ItemAdderDataProvider().getBlockData(identifier, new KMap<>());

            assertTrue(result instanceof IrisCustomData);
            IrisCustomData custom = (IrisCustomData) result;
            assertSame(carrier, custom.getBase());
            assertEquals(identifier, custom.getCustom());
        }
    }

    @Test
    public void placementDoesNotOverwriteProviderInitializedBlock() {
        Block block = mock(Block.class);
        Location location = new Location(null, 7, 81, -6);
        CustomBlock placed = mock(CustomBlock.class);
        when(block.getLocation()).thenReturn(location);
        try (MockedStatic<CustomBlock> blocks = mockStatic(CustomBlock.class)) {
            blocks.when(() -> CustomBlock.place("rocks:ruby_ore", location)).thenReturn(placed);

            new ItemAdderDataProvider().processUpdate(mock(Engine.class), block, new Identifier("rocks", "ruby_ore"));

            blocks.verify(() -> CustomBlock.place("rocks:ruby_ore", location));
            verify(block).getLocation();
            verifyNoMoreInteractions(block, placed);
        }
    }

    @Test
    public void missingPlacementReportsFailure() {
        Block block = mock(Block.class);
        when(block.getLocation()).thenReturn(new Location(null, 7, 81, -6));
        try (MockedStatic<CustomBlock> blocks = mockStatic(CustomBlock.class)) {
            assertThrows(MissingResourceException.class, () -> new ItemAdderDataProvider().processUpdate(
                    mock(Engine.class), block, new Identifier("rocks", "unknown")));
        }
    }

    @Test
    public void unsupportedStateDoesNotResolveDefaultBlockSilently() {
        KMap<String, String> state = new KMap<>();
        state.put("facing", "north");
        try (MockedStatic<CustomBlock> blocks = mockStatic(CustomBlock.class)) {
            assertThrows(MissingResourceException.class, () -> new ItemAdderDataProvider().getBlockData(
                    new Identifier("rocks", "ruby_ore"), state));

            blocks.verifyNoInteractions();
        }
    }
}
