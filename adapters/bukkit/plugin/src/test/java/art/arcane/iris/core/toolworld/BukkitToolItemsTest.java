package art.arcane.iris.core.toolworld;

import com.volmit.resource.ResourceId;
import com.volmit.tooling.world.ToolItem;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BukkitToolItemsTest {
    private ItemStack stackWith(PersistentDataContainer data, ItemMeta meta) {
        ItemStack stack = mock(ItemStack.class);
        when(stack.getType()).thenReturn(Material.STICK);
        when(stack.hasItemMeta()).thenReturn(true);
        when(stack.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(data);
        return stack;
    }

    @Test
    public void aStackWithThePayloadReadsBackAsTheToolItemItWasMadeFrom() {
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        ItemMeta meta = mock(ItemMeta.class);
        when(data.get(BukkitToolItems.PAYLOAD_KEY, PersistentDataType.STRING)).thenReturn("select:bay-1");
        when(meta.hasDisplayName()).thenReturn(true);
        when(meta.displayName()).thenReturn(Component.text("Select bay"));
        when(meta.hasLore()).thenReturn(true);
        when(meta.lore()).thenReturn(List.of(Component.text("Right-click to use")));
        when(meta.getEnchantmentGlintOverride()).thenReturn(true);

        Optional<ToolItem> item = BukkitToolItems.read(stackWith(data, meta));

        assertEquals(Optional.of(new ToolItem(ResourceId.parse("minecraft:stick"), "Select bay",
                List.of("Right-click to use"), true, "select:bay-1")), item);
    }

    @Test
    public void anEmptyPayloadStillMakesAToolItemAndAMissingOneDoesNot() {
        PersistentDataContainer empty = mock(PersistentDataContainer.class);
        when(empty.get(BukkitToolItems.PAYLOAD_KEY, PersistentDataType.STRING)).thenReturn("");
        PersistentDataContainer missing = mock(PersistentDataContainer.class);

        ToolItem tagged = BukkitToolItems.read(stackWith(empty, mock(ItemMeta.class))).orElseThrow();

        assertEquals("", tagged.payload());
        assertEquals("", tagged.name());
        assertEquals(List.of(), tagged.lore());
        assertTrue(BukkitToolItems.read(stackWith(missing, mock(ItemMeta.class))).isEmpty());
    }

    @Test
    public void nothingAirOrAStackWithoutMetadataIsNotAToolItem() {
        ItemStack air = mock(ItemStack.class);
        when(air.getType()).thenReturn(Material.AIR);
        ItemStack plain = mock(ItemStack.class);
        when(plain.getType()).thenReturn(Material.STICK);
        when(plain.hasItemMeta()).thenReturn(false);

        assertTrue(BukkitToolItems.read(null).isEmpty());
        assertTrue(BukkitToolItems.read(air).isEmpty());
        assertTrue(BukkitToolItems.read(plain).isEmpty());
    }

    @Test
    public void thePayloadLivesUnderTheIrisToolPayloadKey() {
        assertEquals("iris:tool_payload", BukkitToolItems.PAYLOAD_KEY.toString());
    }
}
