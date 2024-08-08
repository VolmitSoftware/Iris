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
import com.volmit.iris.util.reflect.WrappedField;
import com.willfp.ecoitems.items.EcoItem;
import com.willfp.ecoitems.items.EcoItems;
import org.bukkit.NamespacedKey;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;

import java.util.MissingResourceException;

public class EcoItemsDataProvider extends ExternalDataProvider {
    private WrappedField<EcoItem, ItemStack> itemStack;
    private WrappedField<EcoItem, NamespacedKey> id;

    public EcoItemsDataProvider() {
        super("EcoItems");
    }

    @Override
    public void init() {
        Iris.info("Setting up EcoItems Link...");
        itemStack = new WrappedField<>(EcoItem.class, "_itemStack");
        if (this.itemStack.hasFailed()) {
            Iris.error("Failed to set up EcoItems Link: Unable to fetch ItemStack field!");
        }
        id = new WrappedField<>(EcoItem.class, "id");
        if (this.id.hasFailed()) {
            Iris.error("Failed to set up EcoItems Link: Unable to fetch id field!");
        }
    }

    @Override
    public BlockData getBlockData(Identifier blockId, KMap<String, String> state) throws MissingResourceException {
        throw new MissingResourceException("Failed to find BlockData!", blockId.namespace(), blockId.key());
    }

    @Override
    public ItemStack getItemStack(Identifier itemId, KMap<String, Object> customNbt) throws MissingResourceException {
        EcoItem item = EcoItems.INSTANCE.getByID(itemId.key());
        if (item == null)
            throw new MissingResourceException("Failed to find Item!", itemId.namespace(), itemId.key());
        return itemStack.get(item).clone();
    }

    @Override
    public Identifier[] getBlockTypes() {
        return new Identifier[0];
    }

    @Override
    public Identifier[] getItemTypes() {
        KList<Identifier> names = new KList<>();
        for (EcoItem item : EcoItems.INSTANCE.values()) {
            try {
                Identifier key = Identifier.fromNamespacedKey(id.get(item));
                if (getItemStack(key) != null)
                    names.add(key);
            } catch (MissingResourceException ignored) {
            }
        }

        return names.toArray(new Identifier[0]);
    }

    @Override
    public boolean isValidProvider(Identifier id, boolean isItem) {
        return id.namespace().equalsIgnoreCase("ecoitems") && isItem;
    }
}
