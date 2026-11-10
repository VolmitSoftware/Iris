package art.arcane.iris.platform.bukkit;

import com.volmit.resource.ContentKey;
import com.volmit.resource.ResourceId;
import org.bukkit.Material;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** Loot items arrive as namespaced keys, which {@code Material.valueOf} cannot read. */
public class BukkitItemStacksMaterialTest {
    @Test
    public void aNamespacedVanillaItemKeyNamesItsMaterial() {
        assertEquals(Material.COAL, BukkitItemStacks.materialOf(registered("minecraft:coal")));
    }

    @Test
    public void anItemKeyNoMaterialHasNamesNothing() {
        assertNull(BukkitItemStacks.materialOf(registered("minecraft:not_an_item")));
    }

    private static ContentKey registered(String key) {
        return new ContentKey.Registered(ResourceId.parse(key));
    }
}
