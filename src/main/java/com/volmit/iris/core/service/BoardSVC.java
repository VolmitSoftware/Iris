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
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.util.board.BoardManager;
import com.volmit.iris.util.board.BoardProvider;
import com.volmit.iris.util.board.BoardSettings;
import com.volmit.iris.util.board.ScoreDirection;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.plugin.IrisService;
import com.volmit.iris.util.scheduling.J;
import lombok.Data;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.List;

public class BoardSVC implements IrisService, BoardProvider {
    private final KMap<Player, PlayerBoard> boards = new KMap<>();
    private com.volmit.iris.util.board.BoardManager manager;

    @Override
    public void onEnable() {
        J.ar(this::tick, 20);
        manager = new BoardManager(Iris.instance, BoardSettings.builder()
                .boardProvider(this)
                .scoreDirection(ScoreDirection.DOWN)
                .build());
    }

    @Override
    public void onDisable() {
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

    public void updatePlayer(Player p) {
        if (IrisToolbelt.isIrisStudioWorld(p.getWorld())) {
            manager.remove(p);
            manager.setup(p);
        } else {
            manager.remove(p);
            boards.remove(p);
        }
    }

    @Override
    public String getTitle(Player player) {
        return C.GREEN + "Iris";
    }

    public void tick() {
        if (!Iris.service(StudioSVC.class).isProjectOpen()) {
            return;
        }

        boards.forEach((k, v) -> v.update());
    }

    @Override
    public List<String> getLines(Player player) {
        PlayerBoard pb = boards.computeIfAbsent(player, PlayerBoard::new);
        synchronized (pb.lines) {
            return pb.lines;
        }
    }

    @Data
    public static class PlayerBoard {
        private final Player player;
        private final KList<String> lines;

        public PlayerBoard(Player player) {
            this.player = player;
            this.lines = new KList<>();
            update();
        }

        public void update() {
            synchronized (lines) {
                lines.clear();

                if (!IrisToolbelt.isIrisStudioWorld(player.getWorld())) {
                    return;
                }

                Engine engine = IrisToolbelt.access(player.getWorld()).getEngine();
                int x = player.getLocation().getBlockX();
                int y = player.getLocation().getBlockY() - player.getWorld().getMinHeight();
                int z = player.getLocation().getBlockZ();

                lines.add("&7&m                   ");
                lines.add(C.GREEN + "Speed" + C.GRAY + ":  " + Form.f(engine.getGeneratedPerSecond(), 0) + "/s " + Form.duration(1000D / engine.getGeneratedPerSecond(), 0));
                lines.add(C.AQUA + "Cache" + C.GRAY + ": " + Form.f(IrisData.cacheSize()));
                lines.add(C.AQUA + "Mantle" + C.GRAY + ": " + engine.getMantle().getLoadedRegionCount());
                lines.add("&7&m                   ");
                lines.add(C.AQUA + "Region" + C.GRAY + ": " + engine.getRegion(x, z).getName());
                lines.add(C.AQUA + "Biome" + C.GRAY + ":  " + engine.getBiomeOrMantle(x, y, z).getName());
                lines.add(C.AQUA + "Height" + C.GRAY + ": " + Math.round(engine.getHeight(x, z) + player.getWorld().getMinHeight()));
                lines.add(C.AQUA + "Slope" + C.GRAY + ":  " + Form.f(engine.getComplex().getSlopeStream().get(x, z), 2));
                lines.add(C.AQUA + "BUD/s" + C.GRAY + ": " + Form.f(engine.getBlockUpdatesPerSecond()));
                lines.add("&7&m                   ");
            }
        }
    }
}
