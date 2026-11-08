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

package art.arcane.iris.core.toolworld;

import com.volmit.resource.ResourceId;
import com.volmit.tooling.world.ToolItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Optional;

/**
 * {@link ToolItem}s as Bukkit item stacks. The payload is a string in the item's persistent data; its presence is what
 * makes a stack a tool item, so an empty payload is stored as an empty string.
 */
final class BukkitToolItems {
    static final NamespacedKey PAYLOAD_KEY = new NamespacedKey("iris", "tool_payload");

    private BukkitToolItems() {
    }

    /** A new stack of {@code item}. */
    static ItemStack create(ToolItem item) {
        Material material = Material.matchMaterial(item.item().toString());
        if (material == null || !material.isItem()) {
            throw new IllegalArgumentException("The platform has no item " + item.item());
        }
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException("The item " + item.item() + " cannot carry a name or a payload");
        }
        if (!item.name().isEmpty()) {
            meta.displayName(plain(item.name()));
        }
        if (!item.lore().isEmpty()) {
            meta.lore(item.lore().stream().map(BukkitToolItems::plain).toList());
        }
        if (item.glowing()) {
            meta.setEnchantmentGlintOverride(true);
        }
        meta.getPersistentDataContainer().set(PAYLOAD_KEY, PersistentDataType.STRING, item.payload());
        if (!stack.setItemMeta(meta)) {
            throw new IllegalStateException("The metadata of tool item " + item.item() + " was rejected");
        }
        return stack;
    }

    /** The tool item {@code stack} is; empty for nothing, air or a stack without the tool payload. */
    static Optional<ToolItem> read(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return Optional.empty();
        }
        PersistentDataContainer data = meta.getPersistentDataContainer();
        String payload = data.get(PAYLOAD_KEY, PersistentDataType.STRING);
        if (payload == null) {
            return Optional.empty();
        }
        return Optional.of(new ToolItem(
                ResourceId.parse(stack.getType().getKey().toString()),
                meta.hasDisplayName() ? plainText(meta.displayName()) : "",
                meta.hasLore() ? lore(meta) : List.of(),
                Boolean.TRUE.equals(meta.getEnchantmentGlintOverride()),
                payload));
    }

    private static List<String> lore(ItemMeta meta) {
        List<Component> lines = meta.lore();
        return lines == null ? List.of() : lines.stream().map(BukkitToolItems::plainText).toList();
    }

    private static Component plain(String text) {
        return Component.text(text).decoration(TextDecoration.ITALIC, false);
    }

    private static String plainText(Component component) {
        return component == null ? "" : PlainTextComponentSerializer.plainText().serialize(component);
    }
}
