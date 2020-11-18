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
    private KMap<UUID, EnginePlayer> players;
    private Semaphore limit;

    public IrisEngineEffects(Engine engine) {
        super(engine, "FX");
        players = new KMap<>();
        limit = new Semaphore(1);
    }

    @Override
    public void updatePlayerMap() {
        List<Player> pr = getEngine().getWorld().getPlayers();
        for(Player i : pr)
        {
            Location l = i.getLocation();
            boolean pcc = players.containsKey(i.getUniqueId());
            if(getEngine().contains(l))
            {
                if(!pcc)
                {
                    players.put(i.getUniqueId(), new EnginePlayer(getEngine(), i));
                }
            }

            else if(pcc)
            {
                players.remove(i.getUniqueId());
            }
        }

        for(UUID i : players.k())
        {
            if(!pr.contains(players.get(i).getPlayer()))
            {
                players.remove(i);
            }
        }
    }

    @Override
    public void tickRandomPlayer() {
        if(limit.tryAcquire())
        {
            if(M.r(0.02))
            {
                updatePlayerMap();
                limit.release();
                return;
            }

            if(players.isEmpty())
            {
                limit.release();
                return;
            }

            double limitms = 1.5;
            int max = players.size();
            PrecisionStopwatch p = new PrecisionStopwatch();

            while(max-- > 0 && M.ms() - p.getMilliseconds() < limitms)
            {
                players.v().getRandom().tick();
            }

            limit.release();
        }
    }
}
