package com.volmit.tooling.world;

import com.volmit.resource.ResourceId;
import java.util.List;
import java.util.Objects;

/**
 * An item a tool hands to a player, or a menu shows as an icon: the platform's item, a name and lore, and a payload the
 * host keeps on the item. The host guarantees the payload survives on the item, as a string in the item's persistent
 * data on Bukkit and as custom data on modded platforms, and hands it back through {@link ToolPlayer#held()} and
 * {@link ToolUse#item()}. A tool tells its own items apart by their payload.
 *
 * <p>The host shows the name and the lore the way the platform shows text a plugin sets on an item, in the colours of
 * the {@link ToolText}. An item the host reads back from a player carries its name and lore as plain text.
 *
 * @param item    the platform's item, such as {@code minecraft:stick}
 * @param name    the display name; empty keeps the item's own name
 * @param lore    the lines under the name
 * @param glowing whether the item shows the enchantment glint
 * @param payload what the tool keeps on the item; empty for nothing
 */
public record ToolItem(ResourceId item, ToolText name, List<ToolText> lore, boolean glowing, String payload) {
    public ToolItem {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(payload, "payload");
        lore = List.copyOf(lore);
    }
}
