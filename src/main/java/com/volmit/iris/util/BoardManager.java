package ninja.bytecode.iris.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Missionary (missionarymc@gmail.com)
 * @since 3/23/2018
 */
public class BoardManager implements Listener {

    private final JavaPlugin plugin;
    private BoardSettings boardSettings;
    private Map<UUID, Board> scoreboards;
    private BukkitTask updateTask;

    public BoardManager(JavaPlugin plugin, BoardSettings boardSettings) {
        this.plugin = plugin;
        this.boardSettings = boardSettings;
        this.scoreboards = new ConcurrentHashMap<>();
        this.updateTask = new BoardUpdateTask(this).runTaskTimer(plugin, 2L, 2L);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
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

    private void setup(Player player) {
        Optional.ofNullable(scoreboards.remove(player.getUniqueId())).ifPresent(Board::resetScoreboard);
        if (player.getScoreboard() == Bukkit.getScoreboardManager().getMainScoreboard()) {
            player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
        }
        scoreboards.put(player.getUniqueId(), new Board(player, boardSettings));
    }

    private void remove(Player player) {
        Optional.ofNullable(scoreboards.remove(player.getUniqueId())).ifPresent(Board::remove);
    }

    public Map<UUID, Board> getScoreboards() {
        return Collections.unmodifiableMap(scoreboards);
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent e) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (e.getPlayer().isOnline()) { // Set this up 2 ticks later.
                setup(e.getPlayer());
            }
        }, 2L);
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent e) {
        this.remove(e.getPlayer());
    }

    public void onDisable() {
        updateTask.cancel();
        plugin.getServer().getOnlinePlayers().forEach(this::remove);
        scoreboards.clear();
    }
}
