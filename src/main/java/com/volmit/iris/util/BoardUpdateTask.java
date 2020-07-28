package com.volmit.iris.util;

import lombok.RequiredArgsConstructor;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;
import java.util.function.Predicate;

/**
 * @author Missionary (missionarymc@gmail.com)
 * @since 5/31/2018
 */
@RequiredArgsConstructor
public class BoardUpdateTask extends BukkitRunnable {

    private static final Predicate<UUID> PLAYER_IS_ONLINE = uuid -> Bukkit.getPlayer(uuid) != null;

    private final BoardManager boardManager;

    @Override
    public void run() {
        boardManager.getScoreboards().entrySet().stream().filter(entrySet -> PLAYER_IS_ONLINE.test(entrySet.getKey())).forEach(entrySet -> entrySet.getValue().update());
    }
}
