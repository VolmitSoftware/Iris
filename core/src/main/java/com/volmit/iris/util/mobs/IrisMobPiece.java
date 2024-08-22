package com.volmit.iris.util.mobs;

import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.IrisEngineChunkData;
import com.volmit.iris.engine.object.IrisEngineSpawnerCooldown;
import com.volmit.iris.engine.object.IrisEntitySpawn;
import com.volmit.iris.engine.object.IrisPosition;
import com.volmit.iris.engine.service.EngineMobHandlerSVC;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.math.RNG;
import jakarta.activation.DataHandler;
import lombok.Getter;
import org.bukkit.entity.Player;

import java.util.UUID;


public class IrisMobPiece {
    @Getter
    private final Player player;
    private IrisMobDataHandler dataHandler;
    private long lastRanPlayer;

    public IrisMobPiece(Player player, IrisMobDataHandler dh) {
        this.player = player;
        this.dataHandler = dh;
    }


    /**
     * Predict if it should tick the player or if it should skip it for this round.
     * @return true = should tick
     */
    public boolean shouldTick() {

        return true;

    }

    /**
     * Ticks the current player
     */
    public void tick() {
        lastRanPlayer = M.ms();


    }


    public UUID getOwner() {
        return player.getUniqueId();
    }

    public void close() {

    }

//    private void spawn(IrisPosition c, IrisEntitySpawn i) {
//        boolean allow = true;
//
//        if (!i.getReferenceSpawner().getMaximumRatePerChunk().isInfinite()) {
//            allow = false;
//            IrisEngineChunkData cd = dataHandler.getEngine().getEngineData().getChunk(c.getX() >> 4, c.getZ() >> 4);
//            IrisEngineSpawnerCooldown sc = null;
//            for (IrisEngineSpawnerCooldown j : cd.getCooldowns()) {
//                if (j.getSpawner().equals(i.getReferenceSpawner().getLoadKey())) {
//                    sc = j;
//                    break;
//                }
//            }
//
//            if (sc == null) {
//                sc = new IrisEngineSpawnerCooldown();
//                sc.setSpawner(i.getReferenceSpawner().getLoadKey());
//                cd.getCooldowns().add(sc);
//            }
//
//            if (sc.canSpawn(i.getReferenceSpawner().getMaximumRatePerChunk())) {
//                sc.spawn(dataHandler.getEngine());
//                allow = true;
//            }
//        }
//
//        if (allow) {
//            int s = i.spawn(dataHandler.getEngine(), c, RNG.r);
//            actuallySpawned += s;
//            if (s > 0) {
//                getCooldown(i.getReferenceSpawner()).spawn(dataHandler.getEngine());
//                energy -= s * ((i.getEnergyMultiplier() * i.getReferenceSpawner().getEnergyMultiplier() * 1));
//            }
//        }
//    }
}