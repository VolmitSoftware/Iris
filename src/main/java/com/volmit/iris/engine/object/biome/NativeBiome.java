package com.volmit.iris.engine.object.biome;

import com.volmit.iris.engine.object.Namespaced;
import com.volmit.iris.engine.object.NSKey;
import lombok.Data;

@Data
public class NativeBiome implements Namespaced {
    private final NSKey key;

    public NativeBiome(NSKey key)
    {
        this.key = key;
    }

    public String toString()
    {
        return key.toString();
    }
}
