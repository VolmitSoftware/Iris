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
import com.volmit.iris.util.scheduling.J;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.ItemTier;
import net.Indyuce.mmoitems.api.Type;
import net.Indyuce.mmoitems.api.block.CustomBlock;
import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;

import java.util.MissingResourceException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class MMOItemsDataProvider extends ExternalDataProvider {

    public MMOItemsDataProvider() {
        super("MMOItems");
    }

    @Override
    public void init() {
        Iris.info("Setting up MMOItems Link...");
    }

    @Override
    public BlockData getBlockData(Identifier blockId, KMap<String, String> state) throws MissingResourceException {
        int id = -1;
        try {
            id = Integer.parseInt(blockId.key());
        } catch (NumberFormatException ignored) {
        }
        CustomBlock block = api().getCustomBlocks().getBlock(id);
        if (block == null)
            throw new MissingResourceException("Failed to find BlockData!", blockId.namespace(), blockId.key());
        return block.getState().getBlockData();
    }

    @Override
    public ItemStack getItemStack(Identifier itemId, KMap<String, Object> customNbt) throws MissingResourceException {
        String[] parts = itemId.namespace().split("_", 2);
        if (parts.length != 2)
            throw new MissingResourceException("Failed to find ItemData!", itemId.namespace(), itemId.key());
        CompletableFuture<ItemStack> future = new CompletableFuture<>();
        Runnable run = () -> {
            try {
                var type = api().getTypes().get(parts[1]);
                int level = -1;
                ItemTier tier = null;

                if (customNbt != null) {
                    level = (int) customNbt.getOrDefault("level", -1);
                    tier = api().getTiers().get(String.valueOf(customNbt.get("tier")));
                }

                ItemStack itemStack;
                if (type == null) {
                    future.complete(null);
                    return;
                }

                if (level != -1 && tier != null) {
                    itemStack = api().getItem(type, itemId.key(), level, tier);
                } else {
                    itemStack = api().getItem(type, itemId.key());
                }
                future.complete(itemStack);
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
        };
        if (Bukkit.isPrimaryThread()) run.run();
        else J.s(run);
        ItemStack item = null;
        try {
            item = future.get();
        } catch (InterruptedException | ExecutionException ignored) {
        }
        if (item == null)
            throw new MissingResourceException("Failed to find ItemData!", itemId.namespace(), itemId.key());
        return item;
    }

    @Override
    public Identifier[] getBlockTypes() {
        KList<Identifier> names = new KList<>();
        for (Integer id : api().getCustomBlocks().getBlockIds()) {
            try {
                Identifier key = new Identifier("mmoitems", String.valueOf(id));
                if (getBlockData(key) != null)
                    names.add(key);
            } catch (MissingResourceException ignored) {
            }
        }
        return names.toArray(new Identifier[0]);
    }

    @Override
    public Identifier[] getItemTypes() {
        KList<Identifier> names = new KList<>();
        Runnable run = () -> {
            for (Type type : api().getTypes().getAll()) {
                for (String name : api().getTemplates().getTemplateNames(type)) {
                    try {
                        Identifier key = new Identifier("mmoitems_" + type.getId(), name);
                        if (getItemStack(key) != null)
                            names.add(key);
                    } catch (MissingResourceException ignored) {
                    }
                }
            }
        };
        if (Bukkit.isPrimaryThread()) run.run();
        else {
            try {
                J.sfut(run).get();
            } catch (InterruptedException | ExecutionException e) {
                Iris.error("Failed getting MMOItems item types!");
                Iris.reportError(e);
            }
        }
        return names.toArray(new Identifier[0]);
    }

    @Override
    public boolean isValidProvider(Identifier id, boolean isItem) {
        return isItem ? id.namespace().split("_", 2).length == 2 : id.namespace().equals("mmoitems");
    }

    private MMOItems api() {
        return MMOItems.plugin;
    }
}
