package com.volmit.iris.util.mobs;

import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.service.EngineMobHandlerSVC;
import com.volmit.iris.util.math.M;
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
}