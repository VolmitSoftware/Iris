package com.volmit.item;

import java.util.OptionalInt;

/** What a module may ask the platform about items, so that its decisions follow the item they concern. Item keys are
 * written as in packs: {@code diamond_sword}, {@code minecraft:diamond_sword}, or a custom item of a provider such as
 * {@code itemsadder:ruby}. Safe for concurrent use.
 */
public interface ItemRegistry {
    /**
     * The most damage an item of this kind takes before it breaks, 0 for an item that cannot be damaged; empty when no
     * item is known by {@code key}.
     */
    OptionalInt maxDurability(String key);
}
