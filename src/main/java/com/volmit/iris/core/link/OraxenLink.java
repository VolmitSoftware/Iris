/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
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

import com.volmit.iris.engine.data.B;
import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public class OraxenLink {
    public boolean supported() {
        return getOraxen() != null;
    }

    public BlockData getBlockDataFor(String id)
    {
        // TODO: Unimplemented
        return B.get("AIR");
    }

    public ItemStack getItemStackForType(String item)
    {
        try {
            Object itemBuilder = Class.forName("io.th0rgal.oraxen.items.OraxenItems").getDeclaredMethod("getItemById", String.class).invoke(null, item);
            return (ItemStack) itemBuilder.getClass().getDeclaredMethod("getReferenceClone").invoke(itemBuilder);
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return null;
    }

    public String[] getItemTypes() {
        try {
            return supported() ? (String[]) Class.forName("io.th0rgal.oraxen.items.OraxenItems").getDeclaredMethod("getItemNames").invoke(null) : new String[0];
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return new String[0];
    }

    public Plugin getOraxen() {

        return Bukkit.getPluginManager().getPlugin("Oraxen");
    }
}
