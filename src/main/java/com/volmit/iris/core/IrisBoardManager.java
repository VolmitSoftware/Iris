/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
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

package com.volmit.iris.core;

import com.volmit.iris.Iris;
import com.volmit.iris.core.tools.IrisWorlds;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.framework.IrisAccess;
import com.volmit.iris.engine.object.IrisFeaturePositional;
import com.volmit.iris.util.board.BoardManager;
import com.volmit.iris.util.board.BoardProvider;
import com.volmit.iris.util.board.BoardSettings;
import com.volmit.iris.util.board.ScoreDirection;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.math.RollingSequence;
import com.volmit.iris.util.scheduling.ChronoLatch;
import com.volmit.iris.util.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;

import java.util.List;

public class IrisBoardManager implements BoardProvider, Listener {

    private BossBar energyBar;
    private final BoardManager manager;
    private String mem = "...";
    public final RollingSequence hits = new RollingSequence(20);
    public final RollingSequence tp = new RollingSequence(100);
    private final ChronoLatch cl = new ChronoLatch(1000);
    private final ChronoLatch ecl = new ChronoLatch(50);

    public IrisBoardManager() {
        Iris.instance.registerListener(this);
        //@builder
        manager = new BoardManager(Iris.instance, BoardSettings.builder()
                .boardProvider(this)
                .scoreDirection(ScoreDirection.DOWN)
                .build());
        energyBar = Bukkit.createBossBar("Spawner Energy " + 0, BarColor.BLUE, BarStyle.SOLID);
        //@done
    }

    @EventHandler
    public void on(PlayerChangedWorldEvent e) {
        J.s(() -> updatePlayer(e.getPlayer()));
    }


    private boolean isIrisWorld(World w) {
        return IrisWorlds.isIrisWorld(w) && IrisWorlds.access(w).isStudio();
    }

    public void updatePlayer(Player p) {
        if (isIrisWorld(p.getWorld())) {
            manager.remove(p);
            manager.setup(p);
            energyBar.removePlayer(p);
            energyBar.addPlayer(p);
        } else {
            manager.remove(p);
            energyBar.removePlayer(p);
        }
    }

    @Override
    public String getTitle(Player player) {
        return C.GREEN + "Iris";
    }


    @Override
    public List<String> getLines(Player player) {
        KList<String> v = new KList<>();

        if (!isIrisWorld(player.getWorld())) {
            return v;
        }

        IrisAccess g = IrisWorlds.access(player.getWorld());

        if (cl.flip()) {
            // TODO MEMORY
            mem = Form.memSize(0, 2);
        }

        int x = player.getLocation().getBlockX();
        int y = player.getLocation().getBlockY();
        int z = player.getLocation().getBlockZ();

        if (g.getCompound() == null) {
            v.add("Loading...");
            return v;
        }

        Engine engine = g.getCompound().getEngineForHeight(y);
        if(ecl.flip())
        {
            energyBar.setProgress(Math.min(1000D, engine.getWorldManager().getEnergy()) / 1000D);
            energyBar.setTitle("Spawner Energy: " + Form.f((int)Math.min(1000D, engine.getWorldManager().getEnergy())));
        }

        int parallaxChunks = 0;
        int parallaxRegions = 0;
        long memoryGuess = 0;
        int loadedObjects = 0;

        for (int i = 0; i < g.getCompound().getSize(); i++) {
            parallaxRegions += g.getCompound().getEngine(i).getParallax().getRegionCount();
            parallaxChunks += g.getCompound().getEngine(i).getParallax().getChunkCount();
            loadedObjects += g.getCompound().getData().getObjectLoader().getSize();
            memoryGuess += g.getCompound().getData().getObjectLoader().getTotalStorage() * 225L;
            memoryGuess += parallaxChunks * 3500L;
            memoryGuess += parallaxRegions * 1700000L;
        }

        tp.put(0); // TODO: CHUNK SPEED


        v.add("&7&m------------------");
        v.add(C.GREEN + "Speed" + C.GRAY + ":  " + Form.f(g.getGeneratedPerSecond(), 0) + "/s " + Form.duration(1000D / g.getGeneratedPerSecond(), 0));
        v.add(C.GREEN + "Memory Use" + C.GRAY + ":  ~" + Form.memSize(memoryGuess, 0));

        if (engine != null) {
            v.add("&7&m------------------");
            KList<IrisFeaturePositional> f = new KList<>();
            f.add(engine.getFramework().getEngineParallax().forEachFeature(x, z));
            v.add(C.AQUA + "Engine" + C.GRAY + ": " + engine.getName() + " " + engine.getMinHeight() + "-" + engine.getMaxHeight());
            v.add(C.AQUA + "Region" + C.GRAY + ": " + engine.getRegion(x, z).getName());
            v.add(C.AQUA + "Biome" + C.GRAY + ":  " + engine.getBiome(x, y, z).getName());
            v.add(C.AQUA + "Height" + C.GRAY + ": " + Math.round(engine.getHeight(x, z)));
            v.add(C.AQUA + "Slope" + C.GRAY + ":  " + Form.f(engine.getFramework().getComplex().getSlopeStream().get(x, z), 2));
            v.add(C.AQUA + "Features" + C.GRAY + ": " + Form.f(f.size()));
            v.add(C.AQUA + "Energy" + C.GRAY + ": " + Form.f(engine.getWorldManager().getEnergy(), 0));
            v.add(C.AQUA + "Sat" + C.GRAY + ": " + Form.f(engine.getWorldManager().getEntityCount()) + "e / " + Form.f(engine.getWorldManager().getChunkCount()) + "c (" + Form.pc(engine.getWorldManager().getEntitySaturation(), 0) + ")");
        }

        if (Iris.jobCount() > 0) {
            v.add("&7&m------------------");
            v.add(C.LIGHT_PURPLE + "Tasks" + C.GRAY + ": " + Iris.jobCount());
        }

        v.add("&7&m------------------");

        return v;
    }


    public void disable() {
        manager.onDisable();
        energyBar.removeAll();
    }
}
