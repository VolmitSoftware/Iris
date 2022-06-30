package com.volmit.iris.engine;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
public class EngineSeedManager {
    private final Engine engine;
    private final long worldSeed;

    public EngineSeedManager(Engine engine)
    {
        this.engine = engine;
        this.worldSeed = engine.getWorld().getSeed();
    }
}
