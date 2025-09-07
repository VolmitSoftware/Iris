/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package com.volmit.iris.core.commands;

import lombok.Synchronized;
import org.bukkit.World;

import com.volmit.iris.Iris;
import com.volmit.iris.core.pregenerator.ChunkUpdater;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.util.decree.DecreeExecutor;
import com.volmit.iris.util.decree.DecreeOrigin;
import com.volmit.iris.util.decree.annotations.Decree;
import com.volmit.iris.util.decree.annotations.Param;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.format.Form;

@Decree(name = "updater", origin = DecreeOrigin.BOTH, description = "Iris World Updater")
public class CommandUpdater implements DecreeExecutor {
    private final Object lock = new Object();
    private transient ChunkUpdater chunkUpdater;

    @Decree(description = "Updates all chunk in the specified world")
    public void start(
            @Param(description = "World to update chunks at", contextual = true)
            World world
    ) {
        if (!IrisToolbelt.isIrisWorld(world)) {
            sender().sendMessage(C.GOLD + "This is not an Iris world");
            return;
        }
        synchronized (lock) {
            if (chunkUpdater != null) {
                chunkUpdater.stop();
            }

            chunkUpdater = new ChunkUpdater(world);
            if (sender().isPlayer()) {
                sender().sendMessage(C.GREEN + "Updating " + world.getName()  + C.GRAY + " Total chunks: " + Form.f(chunkUpdater.getChunks()));
            } else {
                Iris.info(C.GREEN + "Updating " + world.getName() + C.GRAY + " Total chunks: " + Form.f(chunkUpdater.getChunks()));
            }
            chunkUpdater.start();
        }
    }

    @Synchronized("lock")
    @Decree(description = "Pause the updater")
    public void pause( ) {
        if (chunkUpdater == null) {
            sender().sendMessage(C.GOLD + "You cant pause something that doesnt exist?");
            return;
        }
        boolean status = chunkUpdater.pause();
        if (sender().isPlayer()) {
            if (status) {
                sender().sendMessage(C.IRIS + "Paused task for: " + C.GRAY + chunkUpdater.getName());
            } else {
                sender().sendMessage(C.IRIS + "Unpause task for: " + C.GRAY + chunkUpdater.getName());
            }
        } else {
            if (status) {
                Iris.info(C.IRIS + "Paused task for: " + C.GRAY + chunkUpdater.getName());
            } else {
                Iris.info(C.IRIS + "Unpause task for: " + C.GRAY + chunkUpdater.getName());
            }
        }
    }

    @Synchronized("lock")
    @Decree(description = "Stops the updater")
    public void stop() {
        if (chunkUpdater == null) {
            sender().sendMessage(C.GOLD + "You cant stop something that doesnt exist?");
            return;
        }
        if (sender().isPlayer()) {
            sender().sendMessage("Stopping Updater for: " + C.GRAY + chunkUpdater.getName());
        } else {
            Iris.info("Stopping Updater for: " + C.GRAY + chunkUpdater.getName());
        }
        chunkUpdater.stop();
    }
}


