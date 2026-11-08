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

package art.arcane.iris.core.service;

import art.arcane.iris.core.localization.BukkitUiMessages;
import art.arcane.iris.core.localization.IrisLanguage;
import art.arcane.iris.core.localization.RuntimeUiMessages;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.tools.IrisToolbelt;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.platform.PlatformChunkGenerator;
import art.arcane.volmlib.util.board.Board;
import art.arcane.volmlib.util.board.BoardProvider;
import art.arcane.volmlib.util.board.BoardSettings;
import art.arcane.volmlib.util.board.ScoreDirection;
import art.arcane.volmlib.util.format.Form;
import art.arcane.iris.util.common.plugin.IrisService;
import art.arcane.iris.util.common.scheduling.J;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.volmlib.util.localization.MessageArgument;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class BoardSVC implements IrisService, BoardProvider {
    private static final Pattern LEGACY_COLOR = Pattern.compile("(?i)\\u00a7[0-9A-FK-ORX]");

    private final Map<Player, PlayerBoard> boards = new ConcurrentHashMap<>();
    private final Map<UUID, ToolSidebar> toolSidebars = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> yieldedWorlds = new ConcurrentHashMap<>();
    private final Set<UUID> hiddenPlayers = ConcurrentHashMap.newKeySet();
    private volatile BoardSettings settings;
    private volatile boolean boardEnabled;

    @Override
    public void onEnable() {
        boardEnabled = true;
        settings = BoardSettings.builder()
                .boardProvider(this)
                .scoreDirection(ScoreDirection.DOWN)
                .build();

        cleanupLeakedMainScoreboard();

        for (Player player : art.arcane.iris.platform.bukkit.BukkitPlatform.volmitPlugin().getServer().getOnlinePlayers()) {
            J.runEntity(player, () -> updatePlayer(player));
        }
    }

    private void cleanupLeakedMainScoreboard() {
        try {
            if (Bukkit.getScoreboardManager() == null) {
                return;
            }
            Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
            if (main == null) {
                return;
            }

            Objective objective = main.getObjective("board");
            if (objective == null || !"Iris".equalsIgnoreCase(ChatColor.stripColor(objective.getDisplayName()))) {
                return;
            }

            objective.unregister();
            Team team = main.getTeam("board");
            if (team != null) {
                team.unregister();
            }
        } catch (Throwable e) {
            IrisLogging.reportError(e);
        }
    }

    @Override
    public void onDisable() {
        boardEnabled = false;
        for (PlayerBoard board : new ArrayList<>(boards.values())) {
            board.cancel();
        }
        boards.clear();
        toolSidebars.clear();
        yieldedWorlds.clear();
        hiddenPlayers.clear();
        settings = null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(PlayerChangedWorldEvent e) {
        J.runEntity(e.getPlayer(), () -> updatePlayer(e.getPlayer()));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(PlayerJoinEvent e) {
        J.runEntity(e.getPlayer(), () -> updatePlayer(e.getPlayer()));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(PlayerQuitEvent e) {
        remove(e.getPlayer());
        toolSidebars.remove(e.getPlayer().getUniqueId());
        yieldedWorlds.remove(e.getPlayer().getUniqueId());
        clearPlayerPreference(e.getPlayer().getUniqueId());
    }

    public void updatePlayer(Player p) {
        if (p == null || !boardEnabled || settings == null) {
            return;
        }

        if (!J.isOwnedByCurrentRegion(p)) {
            J.runEntity(p, () -> updatePlayer(p));
            return;
        }

        UUID playerId = p.getUniqueId();
        UUID worldId = p.getWorld().getUID();
        UUID yieldedWorld = yieldedWorlds.get(playerId);
        if (yieldedWorld != null) {
            if (yieldedWorld.equals(worldId)) {
                remove(p);
                return;
            }
            yieldedWorlds.remove(playerId, yieldedWorld);
        }

        if (!isEligibleWorld(p)) {
            toolSidebars.remove(playerId);
            remove(p);
            return;
        }

        PlayerBoard playerBoard = boards.computeIfAbsent(p, PlayerBoard::new);
        ToolSidebar toolSidebar = currentToolSidebar(p);
        if (toolSidebar != null) {
            playerBoard.showTool(toolSidebar);
        } else {
            playerBoard.showOrdinary();
        }
    }

    /** What a tool shows a player in a tool world: a title and plain lines, top to bottom, for as long as the player stays in {@code worldId}. */
    public record ToolSidebar(UUID worldId, String title, List<String> lines) {
        public ToolSidebar {
            Objects.requireNonNull(worldId, "worldId");
            Objects.requireNonNull(title, "title");
            lines = List.copyOf(lines);
        }
    }

    public void applyToolSidebar(Player player, ToolSidebar sidebar) {
        Objects.requireNonNull(player, "Tool sidebar player");
        Objects.requireNonNull(sidebar, "Tool sidebar");
        if (!J.isOwnedByCurrentRegion(player)) {
            J.runEntity(player, () -> applyToolSidebar(player, sidebar));
            return;
        }
        if (!player.isOnline()) {
            return;
        }
        toolSidebars.put(player.getUniqueId(), sidebar);
        if (sidebar.worldId().equals(player.getWorld().getUID())) {
            updatePlayer(player);
        }
    }

    public void clearToolSidebar(Player player) {
        Objects.requireNonNull(player, "Tool sidebar player");
        if (!J.isOwnedByCurrentRegion(player)) {
            J.runEntity(player, () -> clearToolSidebar(player));
            return;
        }
        toolSidebars.remove(player.getUniqueId());
        updatePlayer(player);
    }

    public void refreshOrdinaryContext(Player player) {
        Objects.requireNonNull(player, "Studio board player");
        if (!J.isOwnedByCurrentRegion(player)) {
            J.runEntity(player, () -> refreshOrdinaryContext(player));
            return;
        }
        PlayerBoard previousBoard = boards.get(player);
        boolean alreadyOrdinary = previousBoard != null && previousBoard.isOrdinary();
        toolSidebars.remove(player.getUniqueId());
        updatePlayer(player);
        PlayerBoard playerBoard = boards.get(player);
        if (alreadyOrdinary && playerBoard == previousBoard) {
            playerBoard.refreshOrdinary();
        }
    }

    private void remove(Player player) {
        if (player == null) {
            return;
        }

        if (!J.isOwnedByCurrentRegion(player)) {
            J.runEntity(player, () -> remove(player));
            return;
        }

        PlayerBoard board = boards.remove(player);
        if (board != null) {
            board.cancel();
        }
    }

    public boolean toggle(Player player) {
        Objects.requireNonNull(player, "player");
        boolean visible = togglePlayerBoard(player.getUniqueId());
        if (visible) {
            yieldedWorlds.remove(player.getUniqueId());
        }
        updatePlayer(player);
        return visible;
    }

    @Override
    public String getTitle(Player player) {
        PlayerBoard board = boards.get(player);
        if (board != null && board.view == BoardView.TOOL) {
            return board.title;
        }
        return IrisLanguage.text(BukkitUiMessages.SCOREBOARD_TITLE);
    }

    @Override
    public List<String> getLines(Player player) {
        PlayerBoard board = boards.get(player);
        if (board == null) {
            return List.of();
        }
        return board.lines;
    }

    private boolean isEligibleWorld(Player player) {
        if (player == null) {
            return false;
        }

        return isPlayerBoardEnabled(player.getUniqueId())
                && isStudioGeneratorEligible(IrisToolbelt.access(player.getWorld()));
    }

    static boolean isStudioGeneratorEligible(PlatformChunkGenerator generator) {
        return generator != null
                && generator.isStudio()
                && !generator.isClosing()
                && generator.getEngine() != null;
    }

    static String untrustedBoardValue(String value) {
        String normalized = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ');
        return LEGACY_COLOR.matcher(normalized).replaceAll("")
                .replace("&", "＆")
                .replace("<", "‹")
                .replace(">", "›");
    }

    boolean isPlayerBoardEnabled(UUID playerId) {
        return playerId != null && !hiddenPlayers.contains(playerId);
    }

    boolean togglePlayerBoard(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (hiddenPlayers.remove(playerId)) {
            return true;
        }

        hiddenPlayers.add(playerId);
        return false;
    }

    void clearPlayerPreference(UUID playerId) {
        if (playerId != null) {
            hiddenPlayers.remove(playerId);
        }
    }

    private ToolSidebar currentToolSidebar(Player player) {
        ToolSidebar sidebar = toolSidebars.get(player.getUniqueId());
        if (sidebar == null || !sidebar.worldId().equals(player.getWorld().getUID())) {
            return null;
        }
        return sidebar;
    }

    static List<String> toolLines(ToolSidebar sidebar) {
        return sidebar.lines().stream().map(BoardSVC::untrustedBoardValue).toList();
    }

    public class PlayerBoard {
        private final Player player;
        private final Board board;
        private volatile List<String> lines;
        private volatile ToolSidebar toolSidebar;
        private volatile String title;
        private volatile BoardView view;
        private volatile boolean cancelled;
        private volatile boolean ordinaryTickScheduled;

        public PlayerBoard(Player player) {
            this.player = player;
            this.board = new Board(player, settings);
            this.lines = List.of();
            this.toolSidebar = null;
            this.title = "";
            this.view = BoardView.NONE;
            this.cancelled = false;
            this.ordinaryTickScheduled = false;
        }

        private void showOrdinary() {
            if (cancelled) {
                return;
            }
            if (!board.ownsScoreboardAssignment()) {
                yieldBoard(player, this);
                return;
            }
            boolean switched = view != BoardView.ORDINARY;
            view = BoardView.ORDINARY;
            toolSidebar = null;
            if (switched) {
                updateOrdinary();
                board.update();
            }
            scheduleOrdinaryTick();
        }

        private void refreshOrdinary() {
            if (cancelled || view != BoardView.ORDINARY || !board.ownsScoreboardAssignment()) {
                return;
            }
            updateOrdinary();
            board.update();
        }

        private boolean isOrdinary() {
            return !cancelled && view == BoardView.ORDINARY;
        }

        private void showTool(ToolSidebar sidebar) {
            if (cancelled) {
                return;
            }
            if (!board.ownsScoreboardAssignment()) {
                yieldBoard(player, this);
                return;
            }
            if (view == BoardView.TOOL && sidebar.equals(toolSidebar)) {
                return;
            }
            view = BoardView.TOOL;
            toolSidebar = sidebar;
            title = untrustedBoardValue(sidebar.title());
            lines = toolLines(sidebar);
            board.update();
        }

        private void scheduleOrdinaryTick() {
            if (ordinaryTickScheduled || cancelled || view != BoardView.ORDINARY
                    || !boardEnabled || !player.isOnline()) {
                return;
            }
            ordinaryTickScheduled = true;
            boolean scheduled = J.runEntity(
                    player,
                    () -> {
                        ordinaryTickScheduled = false;
                        ordinaryTick();
                    },
                    20,
                    () -> ordinaryTickScheduled = false);
            if (!scheduled) {
                ordinaryTickScheduled = false;
            }
        }

        private void ordinaryTick() {
            if (cancelled || view != BoardView.ORDINARY || !boardEnabled || !player.isOnline()) {
                return;
            }
            if (!isEligibleWorld(player)) {
                boards.remove(player, this);
                cancel();
                return;
            }
            ToolSidebar sidebar = currentToolSidebar(player);
            if (sidebar != null) {
                showTool(sidebar);
                return;
            }
            if (!board.ownsScoreboardAssignment()) {
                yieldBoard(player, this);
                return;
            }
            updateOrdinary();
            board.update();
            scheduleOrdinaryTick();
        }

        public void cancel() {
            if (cancelled) {
                return;
            }
            cancelled = true;
            if (J.isOwnedByCurrentRegion(player) && player.isOnline()) {
                removeNow();
            } else {
                J.runEntity(player, this::removeNow);
            }
        }

        private void removeNow() {
            try {
                board.remove();
            } catch (Throwable e) {
                IrisLogging.reportError("Failed to remove the Studio scoreboard for " + player.getName() + ".", e);
            }
        }

        private void updateOrdinary() {
            World world = player.getWorld();
            Location loc = player.getLocation();

            PlatformChunkGenerator access = IrisToolbelt.access(world);
            if (access == null) {
                return;
            }

            Engine engine = access.getEngine();
            if (engine == null) {
                return;
            }

            int x = loc.getBlockX();
            int y = loc.getBlockY() - world.getMinHeight();
            int z = loc.getBlockZ();

            List<String> lines = new ArrayList<>(this.lines.size());
            lines.add("&7&m                   ");
            lines.add(IrisLanguage.text(
                    BukkitUiMessages.SCOREBOARD_SPEED,
                    MessageArgument.trusted("speed", Form.f(engine.getGeneratedPerSecond(), 0)),
                    MessageArgument.trusted("duration", Form.duration(1000D / engine.getGeneratedPerSecond(), 0))
            ));
            lines.add(IrisLanguage.text(BukkitUiMessages.SCOREBOARD_CACHE, MessageArgument.trusted("count", Form.f(IrisData.cacheSize()))));
            lines.add(IrisLanguage.text(BukkitUiMessages.SCOREBOARD_MANTLE, MessageArgument.trusted("count", engine.getMantle().getLoadedRegionCount())));

            if (IrisSettings.get().getGeneral().debug) {
                boolean carving = engine.getMantle().getMantle().get(x, y, z, MatterCavern.class) != null;
                lines.add(IrisLanguage.text(
                        BukkitUiMessages.SCOREBOARD_CARVING,
                        MessageArgument.trusted("state", IrisLanguage.text(carving ? RuntimeUiMessages.STATUS_TRUE : RuntimeUiMessages.STATUS_FALSE))
                ));
            }

            lines.add("&7&m                   ");
            lines.add(IrisLanguage.text(BukkitUiMessages.SCOREBOARD_REGION, MessageArgument.untrusted("region", engine.getRegion(x, z).getName())));
            lines.add(IrisLanguage.text(BukkitUiMessages.SCOREBOARD_BIOME, MessageArgument.untrusted("biome", engine.getBiomeOrMantle(x, y, z).getName())));
            lines.add(IrisLanguage.text(BukkitUiMessages.SCOREBOARD_HEIGHT, MessageArgument.trusted("height", Math.round(engine.getHeight(x, z)))));
            lines.add(IrisLanguage.text(BukkitUiMessages.SCOREBOARD_SLOPE, MessageArgument.trusted("slope", Form.f(engine.getComplex().getSlopeStream().get(x, z), 2))));
            lines.add(IrisLanguage.text(BukkitUiMessages.SCOREBOARD_BLOCK_UPDATES, MessageArgument.trusted("updates", Form.f(engine.getBlockUpdatesPerSecond()))));
            lines.add("&7&m                   ");
            this.lines = lines;
        }
    }

    private void yieldBoard(Player player, PlayerBoard playerBoard) {
        yieldedWorlds.put(player.getUniqueId(), player.getWorld().getUID());
        boards.remove(player, playerBoard);
        playerBoard.cancel();
    }

    private enum BoardView {
        NONE,
        ORDINARY,
        TOOL
    }
}
