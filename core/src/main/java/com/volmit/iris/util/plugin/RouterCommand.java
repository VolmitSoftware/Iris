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

package com.volmit.iris.util.plugin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * Assistive command router
 *
 * @author cyberpwn
 */
public class RouterCommand extends org.bukkit.command.Command {
    private final CommandExecutor ex;
    private String usage;

    /**
     * The router command routes commands to bukkit executors
     *
     * @param realCommand the real command
     * @param ex          the executor
     */
    public RouterCommand(ICommand realCommand, CommandExecutor ex) {
        super(realCommand.getNode().toLowerCase());
        setAliases(realCommand.getNodes());

        this.ex = ex;
    }


    @Override
    public Command setUsage(String u) {
        this.usage = u;
        return this;
    }


    @Override
    public String getUsage() {
        return usage;
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        return ex.onCommand(sender, this, commandLabel, args);
    }
}
