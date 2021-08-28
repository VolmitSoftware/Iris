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

package com.volmit.iris.core.service;

import com.volmit.iris.Iris;
import com.volmit.iris.core.decrees.DecIris;
import com.volmit.iris.engine.data.cache.AtomicCache;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.decree.DecreeSystem;
import com.volmit.iris.util.decree.virtual.VirtualDecreeCommand;
import com.volmit.iris.util.plugin.IrisService;

public class CommandSVC implements IrisService, DecreeSystem {
    @Override
    public void onEnable() {
         Iris.instance.getCommand("irisd").setExecutor(this);
    }

    @Override
    public void onDisable() {

    }

    private final transient AtomicCache<VirtualDecreeCommand> commandCache = new AtomicCache<>();
    private final transient AtomicCache<KList<String>> startsCache = new AtomicCache<>();

    @Override
    public VirtualDecreeCommand getRoot() {
        return commandCache.aquire(() -> {
            try {
                return VirtualDecreeCommand.createRoot(new DecIris());
            } catch (Throwable e) {
                e.printStackTrace();
            }

            return null;
        });
    }
}
