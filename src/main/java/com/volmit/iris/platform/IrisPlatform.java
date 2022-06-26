package com.volmit.iris.platform;

import com.volmit.iris.engine.object.NSKey;
import com.volmit.iris.engine.object.biome.NativeBiome;
import com.volmit.iris.engine.object.block.IrisBlock;

public interface IrisPlatform<NS, BLOCK, BIOME> {
    String getPlatformName();

    PlatformTransformer<NS, NSKey> getNamespaceTransformer();

    PlatformDataTransformer<BLOCK, IrisBlock> getBlockDataTransformer();

    PlatformDataTransformer<BIOME, NativeBiome> getBiomeTransformer();
}
