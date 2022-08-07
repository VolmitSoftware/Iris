package com.volmit.iris.engine.feature;

import com.volmit.iris.engine.dimension.IrisBiome;
import com.volmit.iris.util.NoiseCache;
import com.volmit.iris.util.ShortNoiseCache;
import lombok.Data;

@Data
public class FeatureStorage {
    private ShortNoiseCache height;
    private NoiseCache<IrisBiome> biome;
    private final int w;
    private final int h;

    public FeatureStorage(int w, int h)
    {
        this.w = w;
        this.h = h;
        this.height = new ShortNoiseCache(w, h);
        this.biome = new NoiseCache<>(w, h);
    }
}
