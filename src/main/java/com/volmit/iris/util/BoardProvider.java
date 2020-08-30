package com.volmit.iris.util;

import java.util.List;

import org.bukkit.entity.Player;

@DontObfuscate
public interface BoardProvider
{
	@DontObfuscate
	String getTitle(Player player);

	@DontObfuscate
	List<String> getLines(Player player);
}
