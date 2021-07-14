package com.volmit.iris.util;

import org.bukkit.entity.Player;

import java.util.List;

@DontObfuscate
public interface BoardProvider {
    @DontObfuscate
    String getTitle(Player player);

    @DontObfuscate
    List<String> getLines(Player player);
}
