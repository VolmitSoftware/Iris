package com.volmit.iris.engine.registry;

import com.volmit.iris.engine.object.biome.NativeBiome;
import com.volmit.iris.engine.object.block.IrisBlock;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class EngineRegistry<BLOCK, BIOME> {
    private final PlatformRegistry<BLOCK, IrisBlock> blockRegistry;
    private final PlatformRegistry<BIOME, NativeBiome> biomeRegistry;
}
