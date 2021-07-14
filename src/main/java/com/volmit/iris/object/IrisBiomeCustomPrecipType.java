package com.volmit.iris.object;

import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;

@Desc("Snow, rain, or nothing")
public enum IrisBiomeCustomPrecipType
{
    @Desc("No downfall")
    @DontObfuscate
    none,

    @Desc("Rain downfall")
    @DontObfuscate
    rain,

    @Desc("Snow downfall")
    @DontObfuscate
    snow
}
