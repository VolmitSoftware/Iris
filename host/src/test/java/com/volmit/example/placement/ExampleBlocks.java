package com.volmit.example.placement;

import com.volmit.block.BlockState;

final class ExampleBlocks {
    static final BlockState AIR = new BlockState("minecraft:air");

    private ExampleBlocks() {}

    static boolean isAir(BlockState state) {
        return state.material().equals(AIR.material());
    }
}
