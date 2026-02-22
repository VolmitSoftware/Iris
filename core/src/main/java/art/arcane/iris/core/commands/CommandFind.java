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

package art.arcane.iris.core.commands;

import art.arcane.iris.core.ExternalDataPackPipeline;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisRegion;
import art.arcane.iris.util.common.director.DirectorExecutor;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.iris.util.common.director.specialhandlers.ObjectHandler;
import art.arcane.iris.util.common.format.C;
import art.arcane.iris.util.common.plugin.VolmitSender;
import art.arcane.iris.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Set;

@Director(name = "find", origin = DirectorOrigin.PLAYER, description = "Iris Find commands", aliases = "goto")
public class CommandFind implements DirectorExecutor {
    @Director(description = "Find a biome")
    public void biome(
            @Param(description = "The biome to look for")
            IrisBiome biome,
            @Param(description = "Should you be teleported", defaultValue = "true")
            boolean teleport
    ) {
        Engine e = engine();

        if (e == null) {
            sender().sendMessage(C.GOLD + "Not in an Iris World!");
            return;
        }

        e.gotoBiome(biome, player(), teleport);
    }

    @Director(description = "Find a region")
    public void region(
            @Param(description = "The region to look for")
            IrisRegion region,
            @Param(description = "Should you be teleported", defaultValue = "true")
            boolean teleport
    ) {
        Engine e = engine();

        if (e == null) {
            sender().sendMessage(C.GOLD + "Not in an Iris World!");
            return;
        }

        e.gotoRegion(region, player(), teleport);
    }

    @Director(description = "Find a point of interest.")
    public void poi(
            @Param(description = "The type of PoI to look for.")
            String type,
            @Param(description = "Should you be teleported", defaultValue = "true")
            boolean teleport
    ) {
        Engine e = engine();
        if (e == null) {
            sender().sendMessage(C.GOLD + "Not in an Iris World!");
            return;
        }

        e.gotoPOI(type, player(), teleport);
    }

    @Director(description = "Find an object")
    public void object(
            @Param(description = "The object to look for", customHandler = ObjectHandler.class)
            String object,
            @Param(description = "Should you be teleported", defaultValue = "true")
            boolean teleport
    ) {
        Engine e = engine();

        if (e == null) {
            sender().sendMessage(C.GOLD + "Not in an Iris World!");
            return;
        }

        if (e.hasObjectPlacement(object)) {
            e.gotoObject(object, player(), teleport);
            return;
        }

        Set<String> structures = ExternalDataPackPipeline.resolveLocateStructuresForObjectKey(object);
        VolmitSender commandSender = sender();
        if (structures.isEmpty()) {
            if (commandSender != null) {
                commandSender.sendMessage(C.RED + object + " is not configured in any region/biome object placements and has no external structure mapping.");
                commandSender.sendMessage(C.GRAY + "Try /iris locateexternal <datapack-id> for external structure lookups.");
            }
            return;
        }

        Player target = player();
        if (target == null) {
            if (commandSender != null) {
                commandSender.sendMessage(C.RED + "No active player sender was available for object lookup.");
            }
            return;
        }

        Runnable dispatchTask = () -> {
            int dispatched = 0;
            for (String structure : structures) {
                String command = "locate structure " + structure;
                boolean accepted = Bukkit.dispatchCommand(target, command);
                if (!accepted) {
                    if (commandSender != null) {
                        commandSender.sendMessage(C.RED + "Failed to dispatch: /" + command);
                    }
                } else {
                    if (commandSender != null) {
                        commandSender.sendMessage(C.GREEN + "Dispatched: /" + command);
                    }
                    dispatched++;
                }
            }

            if (teleport) {
                if (commandSender != null) {
                    commandSender.sendMessage(C.YELLOW + "External object lookups are structure-backed and dispatch locate commands instead of direct teleport.");
                }
            }
            if (commandSender != null) {
                commandSender.sendMessage(C.GREEN + "External object mapping matched locateTargets=" + structures.size() + ", dispatched=" + dispatched + ".");
            }
        };

        if (!J.runEntity(target, dispatchTask)) {
            if (commandSender != null) {
                commandSender.sendMessage(C.RED + "Failed to schedule external object locate dispatch on your region thread.");
            }
        }
    }
}
