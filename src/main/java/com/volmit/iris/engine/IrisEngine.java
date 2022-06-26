package com.volmit.iris.engine;

import com.volmit.iris.engine.registry.EngineRegistry;
import com.volmit.iris.engine.registry.PlatformRegistry;
import com.volmit.iris.platform.IrisPlatform;
import lombok.Data;

@Data
public class IrisEngine<NS, BLOCK, BIOME> {
    private IrisPlatform<NS, BLOCK, BIOME> platform;
    private EngineRegistry<BLOCK, BIOME> registry;
    private EngineConfiguration configuration;

    public IrisEngine(IrisPlatform<NS, BLOCK, BIOME> platform, EngineConfiguration configuration)
    {
        this.configuration = configuration;
        this.platform = platform;
        this.registry = EngineRegistry.<BLOCK, BIOME>builder()
            .blockRegistry(new PlatformRegistry<>(getPlatform().getBlockDataTransformer()))
            .biomeRegistry(new PlatformRegistry<>(getPlatform().getBiomeTransformer()))
            .build();
    }
}
