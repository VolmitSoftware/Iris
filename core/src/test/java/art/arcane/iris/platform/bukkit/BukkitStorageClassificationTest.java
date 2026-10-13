package art.arcane.iris.platform.bukkit;

import art.arcane.volmlib.util.data.BSupport;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.junit.Test;
import org.mockito.MockedStatic;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class BukkitStorageClassificationTest {
    @Test
    public void copperChestsAndExistingLootContainersReceiveStorageUpdates() {
        BlockData air = blockData(Material.AIR);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.createBlockData(Material.AIR)).thenReturn(air);
            BSupport<Object> support = new BSupport<Object>() {
            };
            for (Material material : new Material[]{Material.COPPER_CHEST, Material.EXPOSED_COPPER_CHEST,
                    Material.WEATHERED_COPPER_CHEST, Material.OXIDIZED_COPPER_CHEST,
                    Material.WAXED_COPPER_CHEST, Material.WAXED_EXPOSED_COPPER_CHEST,
                    Material.WAXED_WEATHERED_COPPER_CHEST, Material.WAXED_OXIDIZED_COPPER_CHEST,
                    Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL, Material.SHULKER_BOX,
                    Material.BLUE_SHULKER_BOX, Material.DISPENSER, Material.DROPPER, Material.HOPPER}) {
                BlockData state = blockData(material);
                assertTrue(material.name(), support.isStorage(state));
                assertTrue(material.name(), support.isStorageChest(state));
                assertTrue(material.name(), support.isUpdatable(state));
            }
            for (Material material : new Material[]{Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER}) {
                BlockData state = blockData(material);
                assertTrue(material.name(), support.isStorage(state));
                assertFalse(material.name(), support.isStorageChest(state));
            }
            for (Material material : new Material[]{Material.STONE, Material.AIR, Material.ENDER_CHEST,
                    Material.COPPER_BLOCK, Material.COPPER_CHESTPLATE}) {
                BlockData state = blockData(material);
                assertFalse(material.name(), support.isStorage(state));
                assertFalse(material.name(), support.isStorageChest(state));
            }
        }
    }

    private static BlockData blockData(Material material) {
        BlockData state = mock(BlockData.class);
        when(state.getMaterial()).thenReturn(material);
        return state;
    }
}
