package com.volmit.iris.object;

import com.volmit.iris.util.*;
import lombok.Data;

@Data
@DontObfuscate
@Desc("Represents a potential Iris zone")
public class IrisFeaturePotential {

    @MinNumber(0)
    @Required
    @DontObfuscate
    @Desc("The rarity is 1 in X chance per chunk")
    private int rarity = 100;

    @Required
    @DontObfuscate
    @Desc("")
    private IrisFeature zone = new IrisFeature();

    public boolean hasZone(RNG rng, int cx, int cz)
    {
        return rng.nextInt(rarity) == 0;
    }
}
