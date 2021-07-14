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

package com.volmit.iris.generator;

import com.volmit.iris.scaffold.engine.Engine;
import com.volmit.iris.scaffold.engine.EngineAssignedComponent;
import com.volmit.iris.scaffold.engine.EngineEffects;
import com.volmit.iris.scaffold.engine.EnginePlayer;
import com.volmit.iris.util.KMap;
import com.volmit.iris.util.M;
import com.volmit.iris.util.PrecisionStopwatch;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;

public class IrisEngineEffects extends EngineAssignedComponent implements EngineEffects {
    private final KMap<UUID, EnginePlayer> players;
    private final Semaphore limit;

    public IrisEngineEffects(Engine engine) {
        super(engine, "FX");
        players = new KMap<>();
        limit = new Semaphore(1);
    }

    @Override
    public void updatePlayerMap() {
        List<Player> pr = getEngine().getWorld().getPlayers();

        if (pr == null) return; //Fix for paper returning a world with a null playerlist

        for (Player i : pr) {
            Location l = i.getLocation();
            boolean pcc = players.containsKey(i.getUniqueId());
            if (getEngine().contains(l)) {
                if (!pcc) {
                    players.put(i.getUniqueId(), new EnginePlayer(getEngine(), i));
                }
            } else if (pcc) {
                players.remove(i.getUniqueId());
            }
        }

        for (UUID i : players.k()) {
            if (!pr.contains(players.get(i).getPlayer())) {
                players.remove(i);
            }
        }
    }

    @Override
    public void tickRandomPlayer() {
        if (limit.tryAcquire()) {
            if (M.r(0.02)) {
                updatePlayerMap();
                limit.release();
                return;
            }

            if (players.isEmpty()) {
                limit.release();
                return;
            }

            double limitms = 1.5;
            int max = players.size();
            PrecisionStopwatch p = new PrecisionStopwatch();

            while (max-- > 0 && M.ms() - p.getMilliseconds() < limitms) {
                players.v().getRandom().tick();
            }

            limit.release();
        }
    }
}
