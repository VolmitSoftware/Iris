package com.volmit.item;

import com.volmit.resource.ContentKey;
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
 * after the module made every random choice. The host creates the stack on the platform; a part the item cannot have,
 * such as a leather colour on a sword, is left out.
 *
 * @param item         what the item is
 * @param amount       how many, at least 1
 * @param look         its name, lore and colours
 * @param enchantments applied in order
 * @param attributes   attribute modifiers applied in order
 * @param wear         how worn it is
 * @param extras       what only some platforms understand
 * @param source       where the item came from, present only when the host asked for it to be shown (debug loot)
 */
public record ItemDrop(ContentKey item, int amount, Look look, List<ItemEnchantment> enchantments,
                       List<ItemAttribute> attributes, Wear wear, PlatformExtras extras, Optional<Source> source) {
    public ItemDrop {
        Objects.requireNonNull(item, "item");
        if (amount < 1) throw new IllegalArgumentException("Item amount must be at least 1: " + amount);
        Objects.requireNonNull(look, "look");
        enchantments = List.copyOf(enchantments);
        attributes = List.copyOf(attributes);
        Objects.requireNonNull(wear, "wear");
        Objects.requireNonNull(extras, "extras");
        Objects.requireNonNull(source, "source");
    }

    /** {@code amount} items of kind {@code item} and nothing else. */
    public static ItemDrop of(ContentKey item, int amount) {
        return new ItemDrop(item, amount, Look.PLAIN, List.of(), List.of(), Wear.NEW, PlatformExtras.NONE, Optional.empty());
    }

    /**
     * How the item looks. Names and lore are as authored in the pack, with {@code &} colour codes; the host translates
     * and wraps them.
     *
     * @param displayName  the item's name; empty keeps the platform's
     * @param lore         lines of text under the name
     * @param customModel  custom model number; empty for none
     * @param leatherColor colour of leather armour as {@code 0xRRGGBB}; empty for none
     * @param dyeColor     the dye colour of an item that takes one; empty for none
     */
    public record Look(Optional<String> displayName, List<String> lore, OptionalInt customModel, OptionalInt leatherColor,
                       Optional<DyeColor> dyeColor) {
        public static final Look PLAIN = new Look(Optional.empty(), List.of(), OptionalInt.empty(), OptionalInt.empty(), Optional.empty());

        public Look {
            Objects.requireNonNull(displayName, "displayName");
            lore = List.copyOf(lore);
            Objects.requireNonNull(customModel, "customModel");
            Objects.requireNonNull(leatherColor, "leatherColor");
            Objects.requireNonNull(dyeColor, "dyeColor");
        }
    }

    /**
     * How worn the item is.
     *
     * @param unbreakable whether the item never breaks
     * @param damage      the fraction of the item's durability already used, 0 to 1; empty leaves it undamaged
     */
    public record Wear(boolean unbreakable, OptionalDouble damage) {
        public static final Wear NEW = new Wear(false, OptionalDouble.empty());

        public Wear {
            Objects.requireNonNull(damage, "damage");
        }
    }

    /**
     * What only some platforms understand; a platform without them ignores them.
     *
     * @param flags     the platform's item flags to set, by name; names the platform lacks are ignored
     * @param customNbt custom data the platform stores on the item, or an empty map
     */
    public record PlatformExtras(List<String> flags, Map<String, Object> customNbt) {
        public static final PlatformExtras NONE = new PlatformExtras(List.of(), Map.of());

        public PlatformExtras {
            flags = List.copyOf(flags);
            customNbt = Collections.unmodifiableMap(new LinkedHashMap<>(customNbt));
        }
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
