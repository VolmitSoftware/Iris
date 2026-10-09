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

import art.arcane.iris.engine.framework.LootResolver;
import art.arcane.iris.engine.object.entity.IrisAttributeModifier;
import art.arcane.iris.engine.object.loot.IrisEnchantment;
import art.arcane.iris.engine.object.loot.InventorySlotType;
import art.arcane.iris.engine.object.loot.IrisLoot;
import art.arcane.iris.engine.object.loot.IrisLootTable;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.item.ItemAttribute;
import com.volmit.item.ItemDrop;
import com.volmit.item.ItemEnchantment;
import com.volmit.item.ItemRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * Turns loot entries into the items they give: every random choice is made here, in the order Iris always made them
 * (amount, enchantments, attribute modifiers, damage), so that a container's loot stays what it was. What the platform
 * must do with the result is the host's.
 */
public final class LootItems {
    private static final String AIR = "air";
    private static final String MINECRAFT_PREFIX = "minecraft:";
    private static final int MIN_AMOUNT = 1;

    private final ItemRegistry items;
    private final boolean debug;

    /** @param debug whether the items say where they came from */
    public LootItems(ItemRegistry items, boolean debug) {
        this.items = items;
        this.debug = debug;
    }

    /**
     * The items {@code table} gives at the spot {@code x}, {@code y}, {@code z} (world coordinates): picks entries
     * from the table's own random stream until it has the number of items the table asks for or runs out of tries.
     */
    public List<ItemDrop> draw(IrisLootTable table, long lootSeed, int x, int y, int z) {
        List<ItemDrop> drops = new ArrayList<>();
        List<IrisLoot> entries = table.getLoot();
        if (entries.isEmpty() || table.getMaxTries() <= 0) {
            return drops;
        }

        RNG rng = LootTables.rng(lootSeed, table, x, y, z);
        int wanted = Math.max(0, LootResolver.inclusive(rng, table.getMinPicked(), table.getMaxPicked()));
        int tries = 0;
        while (drops.size() < wanted && tries++ < table.getMaxTries()) {
            int index = rng.nextInt(entries.size());
            IrisLoot entry = entries.get(index);
            if (entry == null || entry.getSlotTypes() != InventorySlotType.STORAGE) {
                continue;
            }
            ItemDrop drop = drawEntry(entry, table, rng, lootSeed, index, x, y, z);
            if (drop != null) {
                drops.add(drop);
            }
        }
        return drops;
    }

    /** The item {@code entry} gives when nothing else decides whether it does, such as a block's drop or a mob's equipment; null for no item. */
    public ItemDrop give(IrisLoot entry, RNG rng) {
        return resolve(entry, rng, null);
    }

    private ItemDrop drawEntry(IrisLoot entry, IrisLootTable table, RNG rng, long lootSeed, int index, int x, int y, int z) {
        long combinedRarity = LootResolver.combinedRarity(entry.getRarity(), table.getRarity());
        if (!LootTables.spatialOneIn(lootSeed, table, index, x, y, z, combinedRarity)) {
            return null;
        }
        return resolve(entry, rng, table);
    }

    private ItemDrop resolve(IrisLoot entry, RNG rng, IrisLootTable table) {
        String type = entry.getTypeKey();
        OptionalInt maxDurability = items.maxDurability(type);
        if (maxDurability.isEmpty() || isAir(type)) {
            return null;
        }

        int amount = Math.max(MIN_AMOUNT, LootResolver.inclusive(rng, entry.getMinAmount(), entry.getMaxAmount()));
        List<ItemEnchantment> enchantments = drawEnchantments(entry, rng);
        List<ItemAttribute> attributes = drawAttributes(entry, rng);
        Double damage = maxDurability.getAsInt() > 0 ? 1D - rng.d(entry.getMinDurability(), entry.getMaxDurability()) : null;
        return new ItemDrop(type, amount, entry.getDisplayName(), entry.getLore(), enchantments, attributes,
                entry.isUnbreakable(), entry.getItemFlags(), entry.getCustomModel(), damage, entry.getLeatherColor(),
                entry.getDyeColorKey(), entry.getCustomNbt(), debug ? sourceOf(entry, table) : null);
    }

    private static List<ItemEnchantment> drawEnchantments(IrisLoot entry, RNG rng) {
        List<ItemEnchantment> enchantments = new ArrayList<>();
        for (IrisEnchantment enchantment : entry.getEnchantments()) {
            String key = enchantment.getEnchantment();
            if (key == null || key.isBlank() || rng.nextDouble() >= enchantment.getChance()) {
                continue;
            }
            enchantments.add(new ItemEnchantment(key, Math.max(1, enchantment.getLevel(rng))));
        }
        return enchantments;
    }

    private static List<ItemAttribute> drawAttributes(IrisLoot entry, RNG rng) {
        List<ItemAttribute> attributes = new ArrayList<>();
        for (IrisAttributeModifier modifier : entry.getAttributes()) {
            if (rng.nextDouble() >= modifier.getChance()) {
                continue;
            }
            String attribute = modifier.getAttribute();
            if (attribute == null || attribute.isBlank()) {
                continue;
            }
            attributes.add(new ItemAttribute(attribute, modifier.getName(), modifier.getOperation(), modifier.getAmount(rng)));
        }
        return attributes;
    }

    private static ItemDrop.Source sourceOf(IrisLoot entry, IrisLootTable table) {
        if (table == null) {
            return new ItemDrop.Source(null, 0, entry.getRarity());
        }
        return new ItemDrop.Source(table.getName(), table.getRarity(), entry.getRarity());
    }

    private static boolean isAir(String type) {
        String key = type.startsWith(MINECRAFT_PREFIX) ? type.substring(MINECRAFT_PREFIX.length()) : type;
        return AIR.equalsIgnoreCase(key);
    }
}
