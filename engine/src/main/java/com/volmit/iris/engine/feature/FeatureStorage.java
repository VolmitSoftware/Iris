package com.volmit.iris.engine.feature;

import com.volmit.iris.engine.dimension.IrisBiome;
import com.volmit.iris.util.NoiseCache;
import com.volmit.iris.util.ShortNoiseCache;
import lombok.Data;

@Data
public class FeatureStorage {
    private ShortNoiseCache heightmap;
    private NoiseCache<IrisBiome> biomemap;
    private final int width;
    private final int height;

    public FeatureStorage(int width, int height)
    {
        this.width = width;
        this.height = height;
        this.heightmap = new ShortNoiseCache(width, height);
        this.biomemap = new NoiseCache<>(width, height);
    }
}
