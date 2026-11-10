package art.arcane.iris.core.service;

import art.arcane.iris.Iris;
import art.arcane.iris.core.link.WorldEditLink;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import art.arcane.volmlib.util.data.Cuboid;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class WandSVCTest {
    private Iris previousPlugin;
    private Player player;
    private PlayerInventory inventory;

    @Before
    public void installPlugin() {
        previousPlugin = Iris.instance;
        Iris plugin = mock(Iris.class);
        when(plugin.namespace()).thenReturn("iris");
        Iris.instance = plugin;
        player = mock(Player.class);
        inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
    }

    @After
    public void restorePlugin() {
        Iris.instance = previousPlugin;
    }

    @Test
    public void ignoresWorldEditSelectionWhileHoldingAnOrdinaryAxe() {
        ItemStack axe = wandItem(false);
        when(inventory.getItemInMainHand()).thenReturn(axe);
        Cuboid selection = mock(Cuboid.class);

        try (MockedStatic<WorldEditLink> worldEdit = mockStatic(WorldEditLink.class)) {
            worldEdit.when(() -> WorldEditLink.getSelection(player)).thenReturn(selection);

            assertFalse(WandSVC.isHoldingWand(player));
            assertNull(WandSVC.getCuboid(player));
        }
    }

    @Test
    public void ignoresWorldEditSelectionWithAnEmptyHand() {
        Cuboid selection = mock(Cuboid.class);

        try (MockedStatic<WorldEditLink> worldEdit = mockStatic(WorldEditLink.class)) {
            worldEdit.when(() -> WorldEditLink.getSelection(player)).thenReturn(selection);

            assertFalse(WandSVC.isHoldingWand(player));
            assertNull(WandSVC.getCuboid(player));
        }
    }

    @Test
    public void readsSelectionFromAnIrisMarkedWand() {
        ItemStack wand = wandItem(true);
        when(inventory.getItemInMainHand()).thenReturn(wand);
        World world = mock(World.class);

        try (MockedStatic<WorldIdentity> worlds = mockStatic(WorldIdentity.class)) {
            worlds.when(() -> WorldIdentity.resolve("minecraft:overworld")).thenReturn(Optional.of(world));

            assertTrue(WandSVC.isHoldingWand(player));
            assertArrayEquals(new Location[]{new Location(world, 1D, 64D, 2D), new Location(world, 3D, 68D, 4D)},
                    WandSVC.getCuboid(player));
        }
    }

    private ItemStack wandItem(boolean marked) {
        ItemStack item = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        when(item.getType()).thenReturn(marked ? Material.BLAZE_ROD : Material.WOODEN_AXE);
        when(item.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(data);
        when(meta.getLore()).thenReturn(List.of("1,64,2 in minecraft:overworld", "3,68,4 in minecraft:overworld"));
        if (marked) {
            when(data.get(new NamespacedKey("iris", "wand"), PersistentDataType.BYTE)).thenReturn((byte) 1);
        }
        return item;
    }
}
