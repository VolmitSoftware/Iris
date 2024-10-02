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

import com.ssomar.score.api.executableitems.ExecutableItemsAPI;
import com.volmit.iris.Iris;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;

import java.util.MissingResourceException;
import java.util.Optional;

public class ExecutableItemsDataProvider extends ExternalDataProvider {
    public ExecutableItemsDataProvider() {
        super("ExecutableItems");
    }

    @Override
    public void init() {
        Iris.info("Setting up ExecutableItems Link...");
    }

    @Override
    public BlockData getBlockData(Identifier blockId, KMap<String, String> state) throws MissingResourceException {
        throw new MissingResourceException("Failed to find BlockData!", blockId.namespace(), blockId.key());
    }

    @Override
    public ItemStack getItemStack(Identifier itemId, KMap<String, Object> customNbt) throws MissingResourceException {
        return ExecutableItemsAPI.getExecutableItemsManager().getExecutableItem(itemId.key())
                .map(item -> item.buildItem(1, Optional.empty()))
                .orElseThrow(() -> new MissingResourceException("Failed to find ItemData!", itemId.namespace(), itemId.key()));
    }

    @Override
    public Identifier[] getBlockTypes() {
        return new Identifier[0];
    }

    @Override
    public Identifier[] getItemTypes() {
        KList<Identifier> names = new KList<>();
        for (String name : ExecutableItemsAPI.getExecutableItemsManager().getExecutableItemIdsList()) {
            try {
                Identifier key = new Identifier("executable_items", name);
                if (getItemStack(key) != null)
                    names.add(key);
            } catch (MissingResourceException ignored) {
            }
        }

        return names.toArray(new Identifier[0]);
    }

    @Override
    public boolean isValidProvider(Identifier key, boolean isItem) {
        return key.namespace().equalsIgnoreCase("executable_items") && isItem;
    }
}
