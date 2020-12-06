package com.volmit.iris.scaffold.engine;

import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisEffect;
import com.volmit.iris.object.IrisRegion;
import com.volmit.iris.util.J;
import com.volmit.iris.util.M;
import lombok.Data;
import org.bukkit.Location;
import org.bukkit.entity.Player;

@Data
public class EnginePlayer {
    private final Engine engine;
    private final Player player;
    private IrisBiome biome;
    private IrisRegion region;
    private Location lastLocation;
    private long lastSample;

    public EnginePlayer(Engine engine, Player player)
    {
        this.engine = engine;
        this.player = player;
        lastLocation = player.getLocation().clone();
        lastSample = -1;
        sample();
    }

    public void tick()
    {
        sample();

        J.s(() -> {
            if(region != null)
            {
                for(IrisEffect j : region.getEffects())
                {
                    j.apply(player, getEngine());
                }
            }

            if(biome != null)
            {
                for(IrisEffect j : biome.getEffects())
                {
                    j.apply(player, getEngine());
                }
            }
        });
    }

    public long ticksSinceLastSample()
    {
        return M.ms() - lastSample;
    }

    public void sample() {
        try
        {
            if(ticksSinceLastSample() > 55 && player.getLocation().distanceSquared(lastLocation) > 9 * 9)
            {
                lastLocation = player.getLocation().clone();
                lastSample = M.ms();
                sampleBiomeRegion();
            }
        }

        catch(Throwable ew)
        {

        }
    }

    private void sampleBiomeRegion() {
        Location l = player.getLocation();
        biome = engine.getBiome(l);
        region = engine.getRegion(l);
    }
}
