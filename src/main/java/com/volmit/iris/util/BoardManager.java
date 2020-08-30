package com.volmit.iris.util;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

@DontObfuscate
public class BoardManager
{
	@DontObfuscate
	private final JavaPlugin plugin;

	@DontObfuscate
	private BoardSettings boardSettings;

	@DontObfuscate
	private Map<UUID, Board> scoreboards;

	@DontObfuscate
	private BukkitTask updateTask;

	@DontObfuscate
	public BoardManager(JavaPlugin plugin, BoardSettings boardSettings)
	{
		this.plugin = plugin;
		this.boardSettings = boardSettings;
		this.scoreboards = new ConcurrentHashMap<>();
		this.updateTask = new BoardUpdateTask(this).runTaskTimer(plugin, 2L, 2L);
		plugin.getServer().getOnlinePlayers().forEach(this::setup);
	}

	@DontObfuscate
	public void setBoardSettings(BoardSettings boardSettings)
	{
		this.boardSettings = boardSettings;
		scoreboards.values().forEach(board -> board.setBoardSettings(boardSettings));
	}

	@DontObfuscate
	public boolean hasBoard(Player player)
	{
		return scoreboards.containsKey(player.getUniqueId());
	}

	@DontObfuscate
	public Optional<Board> getBoard(Player player)
	{
		return Optional.ofNullable(scoreboards.get(player.getUniqueId()));
	}

	@DontObfuscate
	public void setup(Player player)
	{
		Optional.ofNullable(scoreboards.remove(player.getUniqueId())).ifPresent(Board::resetScoreboard);
		if(player.getScoreboard().equals(Bukkit.getScoreboardManager().getMainScoreboard()))
		{
			player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
		}
		scoreboards.put(player.getUniqueId(), new Board(player, boardSettings));
	}

	@DontObfuscate
	public void remove(Player player)
	{
		Optional.ofNullable(scoreboards.remove(player.getUniqueId())).ifPresent(Board::remove);
	}

	@DontObfuscate
	public Map<UUID, Board> getScoreboards()
	{
		return Collections.unmodifiableMap(scoreboards);
	}

	@DontObfuscate
	public void onDisable()
	{
		updateTask.cancel();
		plugin.getServer().getOnlinePlayers().forEach(this::remove);
		scoreboards.clear();
	}
}
