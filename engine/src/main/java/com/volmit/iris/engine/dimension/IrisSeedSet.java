package com.volmit.iris.engine.dimension;

import com.volmit.iris.engine.Engine;
import com.volmit.iris.engine.editor.Mutated;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true, chain = true)
public class IrisSeedSet  implements Mutated {
    private IrisSeedSetMode mode = IrisSeedSetMode.LOCAL_OFFSET;
    private long offset = 1337;

    public double getSeed(Engine engine, long localSeed)
    {
        return switch(mode)
            {
                case WORLD -> engine.getSeedManager().getWorldSeed();
                case LOCAL -> localSeed;
                case LOCAL_OFFSET -> localSeed + offset;
                case RAW -> offset;
                case WORLD_OFFSET -> engine.getSeedManager().getWorldSeed() + offset;
                case RANDOM -> (Math.random() * Long.MAX_VALUE) + (Math.random() * Long.MAX_VALUE);
            };
    }
}
