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

package com.volmit.iris.util.board;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


public class BoardManager {

    private final JavaPlugin plugin;
    private final Map<UUID, Board> scoreboards;
    private final BukkitTask updateTask;
    private BoardSettings boardSettings;


    public BoardManager(JavaPlugin plugin, BoardSettings boardSettings) {
        this.plugin = plugin;
        this.boardSettings = boardSettings;
        this.scoreboards = new ConcurrentHashMap<>();
        this.updateTask = new BoardUpdateTask(this).runTaskTimer(plugin, 2L, 20L);
        plugin.getServer().getOnlinePlayers().forEach(this::setup);
    }


    public void setBoardSettings(BoardSettings boardSettings) {
        this.boardSettings = boardSettings;
        scoreboards.values().forEach(board -> board.setBoardSettings(boardSettings));
    }


    public boolean hasBoard(Player player) {
        return scoreboards.containsKey(player.getUniqueId());
    }


    public Optional<Board> getBoard(Player player) {
        return Optional.ofNullable(scoreboards.get(player.getUniqueId()));
    }


    public void setup(Player player) {
        Optional.ofNullable(scoreboards.remove(player.getUniqueId())).ifPresent(Board::resetScoreboard);
        if (player.getScoreboard().equals(Bukkit.getScoreboardManager().getMainScoreboard())) {
            player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
        }
        scoreboards.put(player.getUniqueId(), new Board(player, boardSettings));
    }


    public void remove(Player player) {
        Optional.ofNullable(scoreboards.remove(player.getUniqueId())).ifPresent(Board::remove);
    }


    public Map<UUID, Board> getScoreboards() {
        return Collections.unmodifiableMap(scoreboards);
    }


    public void onDisable() {
        updateTask.cancel();
        plugin.getServer().getOnlinePlayers().forEach(this::remove);
        scoreboards.clear();
    }
}
