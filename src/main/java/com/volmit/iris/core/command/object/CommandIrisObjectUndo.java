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

package com.volmit.iris.core.command.object;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.plugin.MortarCommand;
import com.volmit.iris.util.plugin.VolmitSender;
import com.volmit.iris.util.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class CommandIrisObjectUndo extends MortarCommand {

    private static Map<UUID, Deque<Map<Block, BlockData>>> undos = new HashMap<>();

    public CommandIrisObjectUndo() {
        super("undo", "u", "revert");
        requiresPermission(Iris.perm);
        setCategory("Object");
        setDescription("Undo an object paste ");
    }

    @Override
    public void addTabOptions(VolmitSender sender, String[] args, KList<String> list) {

    }

    @Override
    public boolean handle(VolmitSender sender, String[] args) {
        if (!IrisSettings.get().isStudio()) {
            sender.sendMessage("To use Iris Studio Objects, please enable studio in Iris/settings.json");
            return true;
        }

        UUID player = null;
        int amount = 1;

        for (int i = 0; i < args.length; i++) {
            String str = args[i];
            if (str.equalsIgnoreCase("-u") || str.equalsIgnoreCase("-user")
                    || str.equalsIgnoreCase("-p") || str.equalsIgnoreCase("-player")) {
                if (i + 1 >= args.length) {
                    sender.sendMessage("No user parameter provided! Usage is -user <player>");
                    return true;
                }

                OfflinePlayer p = Bukkit.getOfflinePlayer(args[i + 1]);
                if (!p.hasPlayedBefore()) {
                    sender.sendMessage("\"" + args[i + 1] + "\" is not a player that has played before!");
                    return true;
                }
                player = p.getUniqueId();
            } else if (str.equalsIgnoreCase("-n") || str.equalsIgnoreCase("-number")) {
                if (i + 1 >= args.length) {
                    sender.sendMessage("No number parameter provided! Usage is -number <amount>");
                    return true;
                }
                try {
                    amount = Integer.parseInt(args[i + 1]);
                } catch (NumberFormatException e) {
                    sender.sendMessage("\"" + args[i + 1] + "\" is not a number!");
                    return true;
                }
            } else if (str.startsWith("-")) {
                sender.sendMessage("Unknown flag \"" + args[i + 1] + "\" provided! Valid flags are -number and -user");
                return true;
            }
        }

        if (!sender.isPlayer() && player == null) {
            sender.sendMessage("Please specify a player to revert!");
            return true;
        } else if (sender.isPlayer()) {
            player = sender.player().getUniqueId();
        }

        if (amount < 0) {
            sender.sendMessage("Please specify an amount greater than 0!");
            return true;
        }

        if (!undos.containsKey(player) || undos.get(player).size() == 0) {
            sender.sendMessage("No pastes to undo");
            return true;
        }

        int actualReverts = Math.min(undos.get(player).size(), amount);
        revertChanges(player, amount);
        sender.sendMessage("Reverted " + actualReverts + " pastes!");

        return true;
    }

    @Override
    protected String getArgsUsage() {
        return "[-number [num]] [-user [username]]";
    }

    public static void addChanges(Player player, Map<Block, BlockData> oldBlocks) {
        if (!undos.containsKey(player.getUniqueId())) {
            undos.put(player.getUniqueId(), new ArrayDeque<>());
        }

        undos.get(player.getUniqueId()).add(oldBlocks);
    }

    public static void revertChanges(UUID player, int amount) {
        loopChange(player, amount);
    }

    private static void loopChange(UUID uuid, int amount) {
        Deque<Map<Block, BlockData>> queue = undos.get(uuid);
        if (queue != null && queue.size() > 0) {
            revert(queue.pollLast());
            if (amount > 1) {
                J.s(() -> loopChange(uuid, amount - 1), 2);
            }
        }
    }

    /**
     * Reverts all the block changes provided, 200 blocks per tick
     * @param blocks The blocks to remove
     */
    private static void revert(Map<Block, BlockData> blocks) {
        int amount = 0;
        Iterator<Map.Entry<Block, BlockData>> it = blocks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Block, BlockData> entry = it.next();
            BlockData data = entry.getValue();
            entry.getKey().setBlockData(data, false);
            it.remove();

            amount++;

            if (amount > 200) {
                J.s(() -> revert(blocks), 1);
            }
        }
    }
}
