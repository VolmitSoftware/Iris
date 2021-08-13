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

package com.volmit.iris.util.decree;

import com.volmit.iris.util.decree.annotations.Decree;
import com.volmit.iris.util.decree.annotations.Param;
import org.bukkit.entity.Player;

@Decree(description = "Description goes here!", aliases = {"ex", "e"})
// The description here is shown when hovering over elements in the chat
// The parameter `name` is undefined, which means it defaults to the name of the class, lowercase, so "example"
// The aliases defined give alternate options for calling this category
// You can also define "origin" which gives who can send the command.
//  By default, if omitted, this is DecreeOrigin.BOTH, but it can be .PLAYER & .CONSOLE
public class Example implements DecreeCommand {

    // This subcommand, given that it implements DecreeCommand, is automatically indexed and recognised by Decree.
    // The way this command is called isn't defined from here.
    // Since subCommand can have another name than in /iris example subCommand.
    SubExample subCommand;

    @Decree(description = "Kick a player", aliases = "k", origin = DecreeOrigin.CONSOLE)
    public void kick(
            @Param(name = "player", description = "The Player to kick from the server", aliases = "p")
                    Player player,
            @Param(name = "reason", description = "A reason to kick the player for", value = "No reason!", aliases = "k")
                    String reason)
    {
        player.kickPlayer(reason);
        DecreeContext.get().sendMessage("Kicked " + player.getName());
    }
}
