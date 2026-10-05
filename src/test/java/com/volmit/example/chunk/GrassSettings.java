package com.volmit.example.chunk;

import com.volmit.FeatureAttachment;
import com.volmit.IrisFeature;
import com.volmit.config.Feature;

@Feature(id = "example:grass", name = "Grass", field = "grass", targets = FeatureAttachment.Target.BIOME)
public final class GrassSettings implements IrisFeature {
    private String block = "minecraft:grass_block";

    public String block() {
        return block;
    }
}
