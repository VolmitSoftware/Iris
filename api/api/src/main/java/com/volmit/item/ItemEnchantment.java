package com.volmit.item;

import com.volmit.resource.ResourceId;
import java.util.Objects;

/** An enchantment of an {@link ItemDrop}, such as {@code minecraft:sharpness} or {@code mymod:vorpal}, with the level
 * the module decided.
 */
public record ItemEnchantment(ResourceId key, int level) {
    public ItemEnchantment {
        Objects.requireNonNull(key, "key");
        if (level < 1) throw new IllegalArgumentException("Enchantment level must be at least 1: " + level);
    }
}
