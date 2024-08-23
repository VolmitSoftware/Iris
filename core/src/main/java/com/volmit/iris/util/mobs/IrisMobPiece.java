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

import java.util.Collections;
import java.util.List;
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
     * + This method should be very fast.
     * Its supposed to be a faster alternative to the getTickCosts method.
     * @return true = should tick
     */
    public boolean shouldTick() {

        return true;

    }

    /**
     * Returns the estimated Energy cost to run this tick.
     * Handy for if you are on a resource limit and need to prioritize who gets ticked and who not and what to expect.
     * @param predict > The Prediction size on how far it should predict, return 0 if shouldTick return false on the Irritation.
     * @return The Predictions it made.
     */
    public List<Integer> getTickCosts(int predict) {


        return Collections.singletonList(0);

    }

    /**
     * Ticks the current player
     * @param energy the energy given for the tick
     */
    public void tick(int energy) {
        lastRanPlayer = M.ms();

    }


    public UUID getOwner() {
        return player.getUniqueId();
    }

    public void close() {

    }
}