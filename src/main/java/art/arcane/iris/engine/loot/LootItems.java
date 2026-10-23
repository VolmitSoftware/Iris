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

import art.arcane.iris.builtin.loot.ItemAttributeModifier;
import art.arcane.iris.builtin.loot.LootEnchantment;
import art.arcane.iris.builtin.loot.LootEntry;
import art.arcane.iris.builtin.loot.LootSlot;
import art.arcane.iris.builtin.loot.LootTable;
import art.arcane.iris.builtin.loot.LootResolver;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.item.ItemAttribute;
import com.volmit.item.ItemDrop;
import com.volmit.item.ItemEnchantment;
import com.volmit.item.ItemRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
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
     * The items {@code table}, which the pack names {@code tableKey}, gives at the spot {@code x}, {@code y}, {@code z}
     * (world coordinates): picks entries from the table's own random stream until it has the number of items the table
     * asks for or runs out of tries.
     */
    public List<ItemDrop> draw(String tableKey, LootTable table, long lootSeed, int x, int y, int z) {
        List<ItemDrop> drops = new ArrayList<>();
        List<LootEntry> entries = table.loot();
        if (entries.isEmpty() || table.maxTries() <= 0) {
            return drops;
        }

        String identity = LootTables.identity(tableKey);
        RNG rng = LootTables.rng(lootSeed, identity, x, y, z);
        int wanted = Math.max(0, LootResolver.inclusive(rng, table.minPicked(), table.maxPicked()));
        int tries = 0;
        while (drops.size() < wanted && tries++ < table.maxTries()) {
            int index = rng.nextInt(entries.size());
            LootEntry entry = entries.get(index);
            if (entry == null || entry.slotTypes() != LootSlot.STORAGE) {
                continue;
            }
            ItemDrop drop = drawEntry(entry, table, identity, rng, lootSeed, index, x, y, z);
            if (drop != null) {
                drops.add(drop);
            }
        }
        return drops;
    }

    /** The item {@code entry} gives when nothing else decides whether it does, such as a block's drop or a mob's equipment; null for no item. */
    public ItemDrop give(LootEntry entry, RNG rng) {
        return resolve(entry, rng, null);
    }

    private ItemDrop drawEntry(LootEntry entry, LootTable table, String identity, RNG rng, long lootSeed, int index, int x, int y, int z) {
        long combinedRarity = LootResolver.combinedRarity(entry.rarity(), table.rarity());
        if (!LootTables.spatialOneIn(lootSeed, identity, index, x, y, z, combinedRarity)) {
            return null;
        }
        return resolve(entry, rng, table);
    }

    private ItemDrop resolve(LootEntry entry, RNG rng, LootTable table) {
        String type = entry.type();
        OptionalInt maxDurability = items.maxDurability(type);
        if (maxDurability.isEmpty() || isAir(type)) {
            return null;
        }

        int amount = Math.max(MIN_AMOUNT, LootResolver.inclusive(rng, entry.minAmount(), entry.maxAmount()));
        List<ItemEnchantment> enchantments = drawEnchantments(entry, rng);
        List<ItemAttribute> attributes = drawAttributes(entry, rng);
        OptionalDouble damage = maxDurability.getAsInt() > 0
                ? OptionalDouble.of(1D - rng.d(entry.minDurability(), entry.maxDurability()))
                : OptionalDouble.empty();
        return new ItemDrop(type, amount, Optional.ofNullable(entry.displayName()), entry.lore(), enchantments, attributes,
                entry.unbreakable(), entry.itemFlags(), customModelOf(entry), damage, Optional.ofNullable(entry.leatherColor()),
                Optional.ofNullable(entry.dyeColor()), Objects.requireNonNullElse(entry.customNbt(), Map.of()),
                debug ? Optional.of(sourceOf(entry, table)) : Optional.empty());
    }

    private static OptionalInt customModelOf(LootEntry entry) {
        Integer customModel = entry.customModel();
        return customModel == null ? OptionalInt.empty() : OptionalInt.of(customModel);
    }

    private static List<ItemEnchantment> drawEnchantments(LootEntry entry, RNG rng) {
        List<ItemEnchantment> enchantments = new ArrayList<>();
        for (LootEnchantment enchantment : entry.enchantments()) {
            String key = enchantment.enchantment();
            if (key == null || key.isBlank() || rng.nextDouble() >= enchantment.chance()) {
                continue;
            }
            int level = LootResolver.inclusive(rng, enchantment.minLevel(), enchantment.maxLevel());
            enchantments.add(new ItemEnchantment(key, Math.max(1, level)));
        }
        return enchantments;
    }

    private static List<ItemAttribute> drawAttributes(LootEntry entry, RNG rng) {
        List<ItemAttribute> attributes = new ArrayList<>();
        for (ItemAttributeModifier modifier : entry.attributes()) {
            if (rng.nextDouble() >= modifier.chance()) {
                continue;
            }
            String attribute = modifier.attribute();
            if (attribute == null || attribute.isBlank()) {
                continue;
            }
            attributes.add(new ItemAttribute(attribute, modifier.name(), modifier.operation(), rng.d(modifier.minAmount(), modifier.maxAmount())));
        }
        return attributes;
    }

    private static ItemDrop.Source sourceOf(LootEntry entry, LootTable table) {
        if (table == null) {
            return new ItemDrop.Source(Optional.empty(), 0, entry.rarity());
        }
        return new ItemDrop.Source(Optional.ofNullable(table.name()), table.rarity(), entry.rarity());
    }

    private static boolean isAir(String type) {
        String key = type.startsWith(MINECRAFT_PREFIX) ? type.substring(MINECRAFT_PREFIX.length()) : type;
        return AIR.equalsIgnoreCase(key);
    }
}
