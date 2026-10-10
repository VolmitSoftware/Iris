package art.arcane.iris.engine.loot;

import art.arcane.iris.builtin.loot.LootEntry;
import art.arcane.iris.builtin.loot.LootTable;
import art.arcane.iris.engine.object.TestSettings;
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
        LootEntry entry = entry("""
                {"type": "diamond_sword", "minAmount": 1, "maxAmount": 3, "minDurability": 0.25, "maxDurability": 0.75,
                 "enchantments": [{"enchantment": "sharpness", "minLevel": 1, "maxLevel": 5, "chance": 1}],
                 "attributes": [{"attribute": "max_health", "name": "bonus", "operation": "ADD_NUMBER", "minAmount": 2, "maxAmount": 6, "chance": 1}]}""");
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
        LootEntry entry = entry("{\"type\": \"diamond\", \"minAmount\": 2, \"maxAmount\": 2}");
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

        assertNull(new LootItems(registry, false).give(entry("{\"type\": \"not_an_item\"}"), rng));
        assertNull(new LootItems(registry, false).give(entry("{\"type\": \"air\"}"), rng));
        assertEquals(untouched.nextLong(), rng.nextLong());
    }

    @Test
    public void debugItemsSayWhereTheyCameFrom() {
        LootTable table = table("{\"name\": \"dungeon\", \"rarity\": 4, \"minPicked\": 1, \"maxPicked\": 1, \"loot\": [{\"type\": \"diamond\", \"rarity\": 3}]}");

        List<ItemDrop> drops = new LootItems(registry, true).draw("dungeon", table, LOOT_SEED, 0, 64, 0);

        for (ItemDrop drop : drops) {
            assertEquals(new ItemDrop.Source("dungeon", 4, 3), drop.source());
        }
        assertTrue(new LootItems(registry, false).draw("dungeon", table, LOOT_SEED, 0, 64, 0).stream().allMatch(drop -> drop.source() == null));
    }

    @Test
    public void aTableGivesTheSameItemsAtTheSameSpotAndSeed() {
        LootTable table = table("{\"name\": \"tools\", \"minPicked\": 2, \"maxPicked\": 4, \"loot\": [{\"type\": \"diamond\", \"maxAmount\": 5}, {\"type\": \"diamond_sword\"}]}");

        LootItems items = new LootItems(registry, false);

        assertEquals(items.draw("tools", table, LOOT_SEED, 10, 64, -30), items.draw("tools", table, LOOT_SEED, 10, 64, -30));
    }

    private static LootEntry entry(String json) {
        return TestSettings.of(LootEntry.class, json);
    }

    private static LootTable table(String json) {
        return TestSettings.of(LootTable.class, json);
    }
}
