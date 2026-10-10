/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.engine.loot;

import art.arcane.iris.engine.object.loot.IrisLootTable;
import com.google.gson.Gson;
import com.volmit.item.ItemDrop;
import com.volmit.item.ItemRegistry;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * What a loot table gives at fixed spots of a fixed world, as the whole drawn items, hashed. The numbers were taken
 * before the loot classes became settings of the loot module; a change in them changes the loot of existing worlds.
 */
public class LootDrawPinTest {
    private static final long LOOT_SEED = 8123456789L;
    private static final String TABLE = """
            {"name": "Pin table", "rarity": 1, "minPicked": 2, "maxPicked": 6, "maxTries": 20, "loot": [
              {"type": "diamond_sword", "rarity": 2, "minAmount": 1, "maxAmount": 3, "minDurability": 0.25, "maxDurability": 0.75,
               "displayName": "Pin blade", "lore": ["one", "two"], "unbreakable": true, "itemFlags": ["HIDE_ENCHANTS"],
               "enchantments": [{"enchantment": "sharpness", "minLevel": 1, "maxLevel": 5, "chance": 0.6},
                                {"enchantment": "unbreaking", "minLevel": 2, "maxLevel": 3}],
               "attributes": [{"attribute": "max_health", "name": "bonus", "minAmount": 2, "maxAmount": 6, "chance": 0.7}]},
              {"type": "diamond", "minAmount": 1, "maxAmount": 5},
              {"type": "bread", "rarity": 2, "minAmount": 2, "maxAmount": 8, "customModel": 4, "dyeColor": "red", "leatherColor": "#ff0000"},
              {"type": "stick", "slotTypes": "FUEL"},
              {"type": "unknown_item"}
            ]}
            """;
    private static final int[][] SPOTS = {{0, 64, 0}, {10, 64, -30}, {-512, 12, 777}, {4096, 200, -4096}, {7, -50, 7}};

    private ItemRegistry registry;

    @Before
    public void knowTheItems() {
        registry = mock(ItemRegistry.class);
        when(registry.maxDurability(anyString())).thenReturn(OptionalInt.empty());
        when(registry.maxDurability("diamond_sword")).thenReturn(OptionalInt.of(1561));
        when(registry.maxDurability("diamond")).thenReturn(OptionalInt.of(0));
        when(registry.maxDurability("bread")).thenReturn(OptionalInt.of(0));
        when(registry.maxDurability("stick")).thenReturn(OptionalInt.of(0));
    }

    @Test
    public void aTableGivesThePinnedItemsAtEachSpot() {
        IrisLootTable table = new Gson().fromJson(TABLE, IrisLootTable.class);
        LootItems items = new LootItems(registry, false);

        List<Integer> hashes = new ArrayList<>();
        for (int[] spot : SPOTS) {
            List<ItemDrop> drops = items.draw(table, LOOT_SEED, spot[0], spot[1], spot[2]);
            hashes.add(drops.toString().hashCode());
        }

        assertEquals(List.of(-492887340, -92432338, -1096436977, -1436497629, -1714085578), hashes);
    }
}
