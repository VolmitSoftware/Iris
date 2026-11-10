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

package art.arcane.iris.modded.toolworld;

import com.volmit.resource.ResourceId;
import com.volmit.tooling.world.ToolItem;
import com.volmit.tooling.world.ToolText;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;
import java.util.Optional;

/**
 * {@link ToolItem}s as item stacks. The payload is a string in the custom data component of the stack; its presence is what
 * makes a stack a tool item, so an empty payload is stored as an empty string.
 */
final class ToolItems {
    static final String PAYLOAD_KEY = "iris_tool_payload";

    private ToolItems() {
    }

    /**
     * A new stack of {@code item}.
     *
     * @throws IllegalArgumentException when the platform has no such item
     */
    static ItemStack create(ToolItem item) {
        Item type = BuiltInRegistries.ITEM.getOptional(Identifier.parse(item.item().toString()))
                .filter(found -> found != Items.AIR)
                .orElseThrow(() -> new IllegalArgumentException("The platform has no item " + item.item()));
        ItemStack stack = new ItemStack(type);
        if (!item.name().isEmpty()) {
            stack.set(DataComponents.CUSTOM_NAME, ToolTexts.itemComponent(item.name()));
        }
        if (!item.lore().isEmpty()) {
            stack.set(DataComponents.LORE, new ItemLore(item.lore().stream().map(ToolTexts::itemComponent).toList()));
        }
        if (item.glowing()) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        CompoundTag data = new CompoundTag();
        data.putString(PAYLOAD_KEY, item.payload());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return stack;
    }

    /** The tool item {@code stack} is; empty for nothing, air or a stack without the tool payload. */
    static Optional<ToolItem> read(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return Optional.empty();
        }
        Optional<String> payload = data.copyTag().getString(PAYLOAD_KEY);
        if (payload.isEmpty()) {
            return Optional.empty();
        }
        Component name = stack.get(DataComponents.CUSTOM_NAME);
        ItemLore lore = stack.get(DataComponents.LORE);
        return Optional.of(new ToolItem(
                ResourceId.parse(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()),
                name == null ? ToolText.EMPTY : plain(name),
                lore == null ? List.of() : lore.lines().stream().map(ToolItems::plain).toList(),
                Boolean.TRUE.equals(stack.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)),
                payload.get()));
    }

    private static ToolText plain(Component component) {
        String text = component.getString();
        return text.isEmpty() ? ToolText.EMPTY : ToolText.plain(text);
    }
}
