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

import com.volmit.iris.util.format.C;
import lombok.NonNull;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * @author Missionary (missionarymc@gmail.com)
 * @since 3/23/2018
 */
public class Board {

    private static final String[] CACHED_ENTRIES = new String[C.values().length];

    private static final Function<String, String> APPLY_COLOR_TRANSLATION = s -> C.translateAlternateColorCodes('&', s);

    static {
        IntStream.range(0, 15).forEach(i -> CACHED_ENTRIES[i] = C.values()[i].toString() + C.RESET);
    }

    private final Player player;
    private final Objective objective;
    @Setter
    private BoardSettings boardSettings;
    private boolean ready;

    @SuppressWarnings("deprecation")
    public Board(@NonNull final Player player, final BoardSettings boardSettings) {
        this.player = player;
        this.boardSettings = boardSettings;
        this.objective = this.getScoreboard().getObjective("board") == null ? this.getScoreboard().registerNewObjective("board", "dummy") : this.getScoreboard().getObjective("board");
        this.objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        Team team = this.getScoreboard().getTeam("board") == null ? this.getScoreboard().registerNewTeam("board") : this.getScoreboard().getTeam("board");
        team.setAllowFriendlyFire(true);
        team.setCanSeeFriendlyInvisibles(false);
        team.setPrefix("");
        team.setSuffix("");
        this.ready = true;
    }

    public Scoreboard getScoreboard() {
        return (player != null) ? player.getScoreboard() : null;
    }

    public void remove() {
        this.resetScoreboard();
    }

    public void update() {
        // Checking if we are ready to start updating the Scoreboard.
        if (!ready) {
            return;
        }

        // Making sure the player is connected.
        if (!player.isOnline()) {
            remove();
            return;
        }

        // Making sure the Scoreboard Provider is set.
        if (boardSettings == null) {
            return;
        }

        // Getting their Scoreboard display from the Scoreboard Provider.
        final List<String> entries = boardSettings.getBoardProvider().getLines(player).stream().map(APPLY_COLOR_TRANSLATION).collect(Collectors.toList());

        if (boardSettings.getScoreDirection() == ScoreDirection.UP) {
            Collections.reverse(entries);
        }

        // Setting the Scoreboard title
        String title = boardSettings.getBoardProvider().getTitle(player);
        if (title.length() > 32) {
            Bukkit.getLogger().warning("The title " + title + " is over 32 characters in length, substringing to prevent errors.");
            title = title.substring(0, 32);
        }
        objective.setDisplayName(C.translateAlternateColorCodes('&', title));

        // Clearing previous Scoreboard values if entry sizes don't match.
        if (this.getScoreboard().getEntries().size() != entries.size())
            this.getScoreboard().getEntries().forEach(this::removeEntry);

        // Setting Scoreboard lines.
        for (int i = 0; i < entries.size(); i++) {
            String str = entries.get(i);
            BoardEntry entry = BoardEntry.translateToEntry(str);
            Team team = getScoreboard().getTeam(CACHED_ENTRIES[i]);

            if (team == null) {
                team = this.getScoreboard().registerNewTeam(CACHED_ENTRIES[i]);
                team.addEntry(team.getName());
            }

            team.setPrefix(entry.getPrefix());
            team.setSuffix(entry.getSuffix());

            switch (boardSettings.getScoreDirection()) {
                case UP -> objective.getScore(team.getName()).setScore(1 + i);
                case DOWN -> objective.getScore(team.getName()).setScore(15 - i);
            }
        }
    }

    public void removeEntry(String id) {
        this.getScoreboard().resetScores(id);
    }

    public void resetScoreboard() {
        ready = false;
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }
}
