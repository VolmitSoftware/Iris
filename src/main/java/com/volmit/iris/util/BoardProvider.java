package com.volmit.iris.util;

import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Objective;

import java.util.List;

public interface BoardProvider {

    /**
     * Gets the title for {@link Objective#getDisplayName()}
     *
     * @param player The {@link Player} to supply
     * @return The title for the objective
     */
    String getTitle(Player player);

    /**
     * Gets the contents to be displayed on the {@link Board}
     *
     * @param player The {@link Player} to supply
     * @return The {@link List} of contents
     */
    List<String> getLines(Player player);
}
