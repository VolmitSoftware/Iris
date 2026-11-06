package com.volmit.example.chunk;

import com.volmit.IrisFeature;
import com.volmit.block.BlockState;
import com.volmit.config.Desc;
import com.volmit.config.Feature;
import com.volmit.config.Level;

@Desc("Covers the top of every terrain column")
@Feature(id = "example:grass", name = "Grass", key = "grass", targets = Level.BIOME)
public final class GrassSettings implements IrisFeature {
    @Desc("Block placed on top of each column")
    private BlockState block = new BlockState("minecraft:grass_block");

    public BlockState block() {
        return block;
    }
}
