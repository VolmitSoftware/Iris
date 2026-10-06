package com.volmit.example.chunk;

import com.volmit.FeatureAttachment;
import com.volmit.IrisFeature;
import com.volmit.block.BlockState;
import com.volmit.config.Desc;
import com.volmit.config.Feature;

@Desc("Covers the top of every terrain column")
@Feature(id = "example:grass", name = "Grass", targets = FeatureAttachment.Target.BIOME)
public final class GrassSettings implements IrisFeature {
    @Desc("Block placed on top of each column")
    private BlockState block = new BlockState("minecraft:grass_block");

    public BlockState block() {
        return block;
    }
}
