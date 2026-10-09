package art.arcane.iris.engine.loot;

import art.arcane.iris.engine.object.entity.IrisAttributeModifier;
import art.arcane.iris.engine.object.loot.IrisEnchantment;
import art.arcane.iris.engine.object.loot.IrisLoot;
import art.arcane.iris.engine.object.loot.IrisLootTable;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.item.ItemDrop;
import com.volmit.item.ItemRegistry;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.OptionalInt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Loot entries turn into items with the random choices made in Iris's order: amount, enchantments, attributes, damage. */
public class LootItemsTest {
    private static final long LOOT_SEED = 77L;

    private ItemRegistry registry;

    @Before
    public void knowTheItems() {
        registry = mock(ItemRegistry.class);
        when(registry.maxDurability(anyString())).thenReturn(OptionalInt.empty());
        when(registry.maxDurability("diamond_sword")).thenReturn(OptionalInt.of(1561));
        when(registry.maxDurability("diamond")).thenReturn(OptionalInt.of(0));
    }

    @Test
    public void anEntryDrawsAmountEnchantmentsAttributesAndDamageFromOneStreamInThatOrder() {
        IrisLoot entry = new IrisLoot().setType("diamond_sword").setMinAmount(1).setMaxAmount(3)
                .setMinDurability(0.25).setMaxDurability(0.75)
                .setEnchantments(new KList<>(new IrisEnchantment("sharpness", 1, 5, 1D)))
                .setAttributes(new KList<>(new IrisAttributeModifier("max_health", "bonus", "ADD_NUMBER", 2, 6, 1D)));
        RNG expected = new RNG(5L);
        int amount = (int) (1 + expected.nextLong(3));
        expected.nextDouble();
        int level = (int) (1 + expected.nextLong(5));
        expected.nextDouble();
        double bonus = expected.d(2, 6);
        double damage = 1D - expected.d(0.25, 0.75);

        ItemDrop drop = new LootItems(registry, false).give(entry, new RNG(5L));

        assertEquals(amount, drop.amount());
        assertEquals(level, drop.enchantments().getFirst().level());
        assertEquals(bonus, drop.attributes().getFirst().amount(), 0D);
        assertEquals(damage, drop.damage(), 0D);
    }

    @Test
    public void anItemThatCannotBeDamagedDrawsNoDamageAndKeepsTheStreamAligned() {
        IrisLoot entry = new IrisLoot().setType("diamond").setMinAmount(2).setMaxAmount(2);
        RNG rng = new RNG(9L);
        RNG untouched = new RNG(9L);

        ItemDrop drop = new LootItems(registry, false).give(entry, rng);

        assertEquals(2, drop.amount());
        assertNull(drop.damage());
        assertEquals(untouched.nextLong(), rng.nextLong());
    }

    @Test
    public void anItemThePlatformDoesNotKnowIsNotGivenAndDrawsNothing() {
        RNG rng = new RNG(9L);
        RNG untouched = new RNG(9L);

        assertNull(new LootItems(registry, false).give(new IrisLoot().setType("not_an_item"), rng));
        assertNull(new LootItems(registry, false).give(new IrisLoot().setType("air"), rng));
        assertEquals(untouched.nextLong(), rng.nextLong());
    }

    @Test
    public void debugItemsSayWhereTheyCameFrom() {
        IrisLootTable table = new IrisLootTable().setName("dungeon").setRarity(4);
        table.setLoot(new KList<>(new IrisLoot().setType("diamond").setRarity(3)));
        table.setMinPicked(1).setMaxPicked(1);

        List<ItemDrop> drops = new LootItems(registry, true).draw(table, LOOT_SEED, 0, 64, 0);

        for (ItemDrop drop : drops) {
            assertEquals(new ItemDrop.Source("dungeon", 4, 3), drop.source());
        }
        assertTrue(new LootItems(registry, false).draw(table, LOOT_SEED, 0, 64, 0).stream().allMatch(drop -> drop.source() == null));
    }

    @Test
    public void aTableGivesTheSameItemsAtTheSameSpotAndSeed() {
        IrisLootTable table = new IrisLootTable().setName("tools").setMinPicked(2).setMaxPicked(4);
        table.setLoot(new KList<>(new IrisLoot().setType("diamond").setMaxAmount(5), new IrisLoot().setType("diamond_sword")));

        LootItems items = new LootItems(registry, false);

        assertEquals(items.draw(table, LOOT_SEED, 10, 64, -30), items.draw(table, LOOT_SEED, 10, 64, -30));
    }
}
