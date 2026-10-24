package com.volmit.item;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * One stack of items a module decided to give: what a loot entry, a block's drops or an entity's equipment turned into
 * after the module made every random choice. The host creates the stack on the platform. Names and lore are as authored
 * in the pack, with {@code &} colour codes; the host translates and wraps them.
 *
 * @param item            the item's key as the pack wrote it, see {@link ItemRegistry}
 * @param amount          how many, at least 1
 * @param displayName     the item's name; empty keeps the platform's
 * @param lore            lines of text under the name
 * @param enchantments    applied in order
 * @param attributes      attribute modifiers applied in order
 * @param unbreakable     whether the item never breaks
 * @param flags           platform item flags to set, by name; names the platform lacks are ignored
 * @param customModel     custom model number; empty for none
 * @param damage          the fraction of the item's durability already used, 0 to 1; empty leaves it undamaged
 * @param leatherColor    colour of leather armour as {@code #rrggbb}; empty for none
 * @param dyeColor        dye colour name; empty for none
 * @param customNbt       custom data the platform stores on the item, or an empty map
 * @param source          where the item came from, present only when the host asked for it to be shown (debug loot)
 */
public record ItemDrop(String item, int amount, Optional<String> displayName, List<String> lore, List<ItemEnchantment> enchantments,
                       List<ItemAttribute> attributes, boolean unbreakable, List<String> flags, OptionalInt customModel,
                       OptionalDouble damage, Optional<String> leatherColor, Optional<String> dyeColor, Map<String, Object> customNbt,
                       Optional<Source> source) {
    public ItemDrop {
        Objects.requireNonNull(item, "item");
        if (amount < 1) throw new IllegalArgumentException("Item amount must be at least 1: " + amount);
        Objects.requireNonNull(displayName, "displayName");
        lore = List.copyOf(lore);
        enchantments = List.copyOf(enchantments);
        attributes = List.copyOf(attributes);
        flags = List.copyOf(flags);
        Objects.requireNonNull(customModel, "customModel");
        Objects.requireNonNull(damage, "damage");
        Objects.requireNonNull(leatherColor, "leatherColor");
        Objects.requireNonNull(dyeColor, "dyeColor");
        customNbt = Collections.unmodifiableMap(new LinkedHashMap<>(customNbt));
        Objects.requireNonNull(source, "source");
    }

    /** {@code amount} items of kind {@code item} and nothing else. */
    public static ItemDrop of(String item, int amount) {
        return new ItemDrop(item, amount, Optional.empty(), List.of(), List.of(), List.of(), false, List.of(), OptionalInt.empty(),
                OptionalDouble.empty(), Optional.empty(), Optional.empty(), Map.of(), Optional.empty());
    }

    /**
     * Where a shown item came from: the loot table, when it came from one, and the 1-in-N chances it passed.
     *
     * @param table       the table's name; empty for an item that came from no table
     * @param tableRarity the table's rarity, 1 in this many
     * @param entryRarity the entry's rarity within the table
     */
    public record Source(Optional<String> table, int tableRarity, int entryRarity) {
        public Source {
            Objects.requireNonNull(table, "table");
        }
    }
}
