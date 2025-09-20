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

package com.volmit.iris.core.service;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.util.board.BoardManager;
import com.volmit.iris.util.board.BoardProvider;
import com.volmit.iris.util.board.BoardSettings;
import com.volmit.iris.util.board.ScoreDirection;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.plugin.IrisService;
import com.volmit.iris.util.scheduling.J;
import lombok.Data;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class BoardSVC implements IrisService, BoardProvider {
    private final KMap<Player, PlayerBoard> boards = new KMap<>();
    private ScheduledExecutorService executor;
    private BoardManager manager;

    @Override
    public void onEnable() {
        executor = Executors.newScheduledThreadPool(0, Thread.ofVirtual().factory());
        manager = new BoardManager(Iris.instance, BoardSettings.builder()
                .boardProvider(this)
                .scoreDirection(ScoreDirection.DOWN)
                .build());
    }

    @Override
    public void onDisable() {
        executor.shutdownNow();
        manager.onDisable();
        boards.clear();
    }

    @EventHandler
    public void on(PlayerChangedWorldEvent e) {
        J.s(() -> updatePlayer(e.getPlayer()));
    }

    @EventHandler
    public void on(PlayerJoinEvent e) {
        J.s(() -> updatePlayer(e.getPlayer()));
    }

    @EventHandler
    public void on(PlayerQuitEvent e) {
        remove(e.getPlayer());
    }

    public void updatePlayer(Player p) {
        if (IrisToolbelt.isIrisStudioWorld(p.getWorld())) {
            manager.remove(p);
            manager.setup(p);
        } else remove(p);
    }

    private void remove(Player player) {
        manager.remove(player);
        var board = boards.remove(player);
        if (board != null) board.task.cancel(true);
    }

    @Override
    public String getTitle(Player player) {
        return C.GREEN + "Iris";
    }

    @Override
    public List<String> getLines(Player player) {
        return boards.computeIfAbsent(player, PlayerBoard::new).lines;
    }

    @Data
    public class PlayerBoard {
        private final Player player;
        private final ScheduledFuture<?> task;
        private volatile List<String> lines;

        public PlayerBoard(Player player) {
            this.player = player;
            this.lines = new ArrayList<>();
            this.task = executor.scheduleAtFixedRate(this::tick, 0, 1, TimeUnit.SECONDS);
        }

        private void tick() {
            if (!Iris.service(StudioSVC.class).isProjectOpen()) {
                return;
            }

            update();
        }

        public void update() {
            final World world = player.getWorld();
            final Location loc = player.getLocation();

            final var access = IrisToolbelt.access(world);
            if (access == null) return;

            final var engine = access.getEngine();
            if (engine == null) return;

            int x = loc.getBlockX();
            int y = loc.getBlockY() - world.getMinHeight();
            int z = loc.getBlockZ();

            List<String> lines = new ArrayList<>(this.lines.size());
            lines.add("&7&m                   ");
            lines.add(C.GREEN + "Speed" + C.GRAY + ":  " + Form.f(engine.getGeneratedPerSecond(), 0) + "/s " + Form.duration(1000D / engine.getGeneratedPerSecond(), 0));
            lines.add(C.AQUA + "Cache" + C.GRAY + ": " + Form.f(IrisData.cacheSize()));
            lines.add(C.AQUA + "Mantle" + C.GRAY + ": " + engine.getMantle().getLoadedRegionCount());

            if (IrisSettings.get().getGeneral().debug) {
                lines.add(C.LIGHT_PURPLE + "Carving" + C.GRAY + ": " + engine.getMantle().isCarved(x,y,z));
            }

            lines.add("&7&m                   ");
            lines.add(C.AQUA + "Region" + C.GRAY + ": " + engine.getRegion(x, z).getName());
            lines.add(C.AQUA + "Biome" + C.GRAY + ":  " + engine.getBiomeOrMantle(x, y, z).getName());
            lines.add(C.AQUA + "Height" + C.GRAY + ": " + Math.round(engine.getHeight(x, z)));
            lines.add(C.AQUA + "Slope" + C.GRAY + ":  " + Form.f(engine.getComplex().getSlopeStream().get(x, z), 2));
            lines.add(C.AQUA + "BUD/s" + C.GRAY + ": " + Form.f(engine.getBlockUpdatesPerSecond()));
            lines.add("&7&m                   ");
            this.lines = lines;
        }
    }
}
