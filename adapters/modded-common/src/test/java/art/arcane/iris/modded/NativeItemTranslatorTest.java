package art.arcane.iris.modded;

import art.arcane.iris.world.loot.IrisLoot;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeItemContext;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeItemStack;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeItemTranslator;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.math.RNG;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class NativeItemTranslatorTest {
    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        if (!Items.LEATHER_HELMET.builtInRegistryHolder().areComponentsBound()) {
            Items.LEATHER_HELMET.builtInRegistryHolder().bindComponents(DataComponentMap.builder()
                    .set(DataComponents.MAX_STACK_SIZE, 1)
                    .set(DataComponents.MAX_DAMAGE, 55)
                    .set(DataComponents.DAMAGE, 0)
                    .build());
        }
    }

    @Test
    public void componentsPreserveAuthoredValuesAndDurability() {
        IrisLoot loot = new IrisLoot().setType("leather_helmet").setMinAmount(2).setMaxAmount(2)
                .setMinDurability(0.5).setMaxDurability(0.5).setUnbreakable(true)
                .setCustomModel(73).setLeatherColor("0x12ab34").setDisplayName("&aExplorer")
                .setLore(new KList<>("&bFirst line", "Second line"))
                .setCustomNbt(new KMap<String, Object>().qput("marker", "terrain"));
        List<String> warnings = new ArrayList<>();
        NativeItemContext context = new NativeItemContext(null,
                new NativeItemContext.Options("example", (key, message) -> warnings.add(message)));

        NativeItemStack result = NativeItemTranslator.stack(new IrisItemRecipe(loot, new RNG(41)), context);

        assertNotNull(result);
        ItemStack stack = result.stack();
        assertTrue(stack.is(Items.LEATHER_HELMET));
        assertEquals(2, stack.getCount());
        assertEquals((int) Math.round(stack.getMaxDamage() * 0.5), stack.getDamageValue());
        assertNotNull(stack.get(DataComponents.UNBREAKABLE));
        assertEquals(73F, stack.get(DataComponents.CUSTOM_MODEL_DATA).floats().getFirst(), 0F);
        assertEquals(0x12ab34, stack.get(DataComponents.DYED_COLOR).rgb());
        assertEquals("§aExplorer", stack.get(DataComponents.CUSTOM_NAME).getString());
        assertEquals("§bFirst line", stack.get(DataComponents.LORE).lines().getFirst().getString());
        assertEquals("terrain", stack.get(DataComponents.CUSTOM_DATA).copyTag().getString("marker").orElseThrow());
        assertTrue(warnings.isEmpty());
    }

    @Test
    public void unknownItemDoesNotConsumeLootRandomness() {
        RNG random = new RNG(83);
        RNG expected = new RNG(83);
        List<String> warnings = new ArrayList<>();
        NativeItemContext context = new NativeItemContext(null,
                new NativeItemContext.Options("example", (key, message) -> warnings.add(key)));

        NativeItemStack result = NativeItemTranslator.stack(new IrisItemRecipe(
                new IrisLoot().setType("example:unknown_item").setMinAmount(1).setMaxAmount(64), random), context);

        assertNull(result);
        assertEquals(expected.nextLong(), random.nextLong());
        assertEquals(List.of("item:example:unknown_item"), warnings);
    }
}
