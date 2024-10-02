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

import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.util.collection.KMap;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.MissingResourceException;

@RequiredArgsConstructor
public abstract class ExternalDataProvider {

    @Getter
    private final String pluginId;

    public Plugin getPlugin() {
        return Bukkit.getPluginManager().getPlugin(pluginId);
    }

    public boolean isReady() {
        return getPlugin() != null && getPlugin().isEnabled();
    }

    public abstract void init();

    public BlockData getBlockData(Identifier blockId) throws MissingResourceException {
        return getBlockData(blockId, new KMap<>());
    }

    public abstract BlockData getBlockData(Identifier blockId, KMap<String, String> state) throws MissingResourceException;

    public ItemStack getItemStack(Identifier itemId) throws MissingResourceException {
        return getItemStack(itemId, new KMap<>());
    }

    public abstract ItemStack getItemStack(Identifier itemId, KMap<String, Object> customNbt) throws MissingResourceException;

    public void processUpdate(Engine engine, Block block, Identifier blockId) {
    }

    public abstract Identifier[] getBlockTypes();

    public abstract Identifier[] getItemTypes();

    public abstract boolean isValidProvider(Identifier id, boolean isItem);
}
