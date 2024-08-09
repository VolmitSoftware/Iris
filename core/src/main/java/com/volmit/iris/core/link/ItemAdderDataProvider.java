/*
 *  Iris is a World Generator for Minecraft Bukkit Servers
 *  Copyright (c) 2024 Arcane Arts (Volmit Software)
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

package com.volmit.iris.core.link;

import com.volmit.iris.Iris;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import dev.lone.itemsadder.api.CustomBlock;
import dev.lone.itemsadder.api.CustomStack;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;

import java.util.MissingResourceException;

public class ItemAdderDataProvider extends ExternalDataProvider {

    private KList<String> itemNamespaces, blockNamespaces;

    public ItemAdderDataProvider() {
        super("ItemsAdder");
    }

    @Override
    public void init() {
        this.itemNamespaces = new KList<>();
        this.blockNamespaces = new KList<>();

        for (Identifier i : getItemTypes()) {
            itemNamespaces.addIfMissing(i.namespace());
        }
        for (Identifier i : getBlockTypes()) {
            blockNamespaces.addIfMissing(i.namespace());
            Iris.info("Found ItemAdder Block: " + i);
        }
    }

    @Override
    public BlockData getBlockData(Identifier blockId, KMap<String, String> state) throws MissingResourceException {
        return CustomBlock.getBaseBlockData(blockId.toString());
    }

    @Override
    public ItemStack getItemStack(Identifier itemId, KMap<String, Object> customNbt) throws MissingResourceException {
        CustomStack stack = CustomStack.getInstance(itemId.toString());
        if (stack == null) {
            throw new MissingResourceException("Failed to find ItemData!", itemId.namespace(), itemId.key());
        }
        return stack.getItemStack();
    }

    @Override
    public Identifier[] getBlockTypes() {
        KList<Identifier> keys = new KList<>();
        for (String s : CustomBlock.getNamespacedIdsInRegistry()) {
            keys.add(Identifier.fromString(s));
        }
        return keys.toArray(new Identifier[0]);
    }

    @Override
    public Identifier[] getItemTypes() {
        KList<Identifier> keys = new KList<>();
        for (String s : CustomStack.getNamespacedIdsInRegistry()) {
            keys.add(Identifier.fromString(s));
        }
        return keys.toArray(new Identifier[0]);
    }

    @Override
    public boolean isValidProvider(Identifier id, boolean isItem) {
        return isItem ? this.itemNamespaces.contains(id.namespace()) : this.blockNamespaces.contains(id.namespace());
    }
}
