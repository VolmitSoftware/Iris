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

import com.volmit.iris.util.collection.KList;

/**
 * Represents a pawn command
 *
 * @author cyberpwn
 */
public interface ICommand {
    KList<String> getRequiredPermissions();

    /**
     * Get the name of this command (node)
     *
     * @return the node
     */
    String getNode();

    /**
     * Get all (realized) nodes of this command
     *
     * @return the nodes
     */
    KList<String> getNodes();

    /**
     * Get all (every) node in this command
     *
     * @return all nodes
     */
    KList<String> getAllNodes();

    /**
     * Add a node to this command
     *
     * @param node the node
     */
    void addNode(String node);

    /**
     * Handle a command. If this is a subcommand, parameters after the subcommand
     * will be adapted in args for you
     *
     * @param sender the volume sender (pre-tagged)
     * @param args   the arguments after this command node
     * @return return true to mark it as handled
     */
    boolean handle(VolmitSender sender, String[] args);

    KList<String> handleTab(VolmitSender sender, String[] args);
}
