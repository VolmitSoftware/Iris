package art.arcane.iris.world.loot;

import art.arcane.iris.spi.IrisLogging;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootTable;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IrisVanillaLootTableTest {
    @Test
    public void registryReplacementRemovalAndRestorationApplyToTheNextContainer() {
        NamespacedKey key = new NamespacedKey("test", "reloadable_loot");
        World world = mock(World.class);
        LootTable before = mock(LootTable.class);
        LootTable after = mock(LootTable.class);
        ItemStack previousItem = mock(ItemStack.class);
        ItemStack currentItem = mock(ItemStack.class);
        when(before.populateLoot(any(), any())).thenReturn(List.of(previousItem));
        when(after.populateLoot(any(), any())).thenReturn(List.of(currentItem));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class)) {
            IrisVanillaLootTable table = new IrisVanillaLootTable(key.toString());
            bukkit.when(() -> Bukkit.getLootTable(key)).thenReturn(before);
            assertSame(previousItem, table.getLoot(false, 1L, InventorySlotType.STORAGE, world, 0, 64, 0).getFirst());

            bukkit.when(() -> Bukkit.getLootTable(key)).thenReturn(after);
            assertSame(currentItem, table.getLoot(false, 1L, InventorySlotType.STORAGE, world, 1, 64, 0).getFirst());

            bukkit.when(() -> Bukkit.getLootTable(key)).thenReturn(null);
            assertTrue(table.getLoot(false, 1L, InventorySlotType.STORAGE, world, 2, 64, 0).isEmpty());

            bukkit.when(() -> Bukkit.getLootTable(key)).thenReturn(after);
            assertSame(currentItem, table.getLoot(false, 1L, InventorySlotType.STORAGE, world, 3, 64, 0).getFirst());
            verify(before).populateLoot(any(), any());
            verify(after, times(2)).populateLoot(any(), any());
        }
    }

    @Test
    public void failedTableGenerationDoesNotRetainTheReplacedTable() {
        NamespacedKey key = new NamespacedKey("test", "repaired_loot");
        World world = mock(World.class);
        LootTable failed = mock(LootTable.class);
        LootTable repaired = mock(LootTable.class);
        ItemStack item = mock(ItemStack.class);
        IllegalStateException failure = new IllegalStateException("Loot generation failed");
        when(failed.populateLoot(any(), any())).thenThrow(failure);
        when(repaired.populateLoot(any(), any())).thenReturn(List.of(item));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            IrisVanillaLootTable table = new IrisVanillaLootTable(key.toString());
            bukkit.when(() -> Bukkit.getLootTable(key)).thenReturn(failed);
            assertSame(failure, assertThrows(IllegalStateException.class,
                    () -> table.getLoot(false, 1L, InventorySlotType.STORAGE, world, 0, 64, 0)));

            bukkit.when(() -> Bukkit.getLootTable(key)).thenReturn(repaired);
            assertSame(item, table.getLoot(false, 1L, InventorySlotType.STORAGE, world, 0, 64, 0).getFirst());
        }
    }
}
