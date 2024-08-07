package com.volmit.iris.engine.service;

import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.framework.EnginePlayer;
import com.volmit.iris.engine.object.IrisEngineService;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;

public class EngineEffectsSVC extends IrisEngineService {
    private KMap<UUID, EnginePlayer> players;
    private Semaphore limit;

    public EngineEffectsSVC(Engine engine) {
        super(engine);
    }

    @Override
    public void onEnable(boolean hotload) {
        players = new KMap<>();
        limit = new Semaphore(1);
    }

    @Override
    public void onDisable(boolean hotload) {
        players = null;
        limit = null;
    }

    public void updatePlayerMap() {
        List<Player> pr = engine.getWorld().getPlayers();

        if (pr == null) {
            return;
        }

        for (Player i : pr) {
            boolean pcc = players.containsKey(i.getUniqueId());
            if (!pcc) {
                players.put(i.getUniqueId(), new EnginePlayer(engine, i));
            }
        }

        for (UUID i : players.k()) {
            if (!pr.contains(players.get(i).getPlayer())) {
                players.remove(i);
            }
        }
    }

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
