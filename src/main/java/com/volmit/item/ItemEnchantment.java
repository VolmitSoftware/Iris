package com.volmit.item;

import java.util.Objects;

/** An enchantment of an {@link ItemDrop}, named by the key the platform's registry knows ({@code sharpness},
 * {@code mymod:vorpal}) with the level the module decided.
 */
public record ItemEnchantment(String key, int level) {
    public ItemEnchantment {
        Objects.requireNonNull(key, "key");
        if (level < 1) throw new IllegalArgumentException("Enchantment level must be at least 1: " + level);
    }
}
