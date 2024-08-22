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
    public long lastRanPlayer;

    public IrisMobPiece(Player player, IrisMobDataHandler dh) {
        this.player = player;
        this.dataHandler = dh;
    }

    public void tick() {
        lastRanPlayer = M.ms();



        // Use the engine instance as needed, but without a direct reference
        // For example: engine.getDimension().getEnergy().evaluate(...)
    }

    public UUID getOwner() {
        return player.getUniqueId();
    }
}