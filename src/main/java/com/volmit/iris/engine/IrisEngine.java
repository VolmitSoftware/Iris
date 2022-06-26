package com.volmit.iris.engine;

import com.volmit.iris.platform.IrisPlatform;
import com.volmit.iris.platform.PlatformRegistry;
import lombok.Data;

@Data
public class IrisEngine {
    private IrisPlatform platform;
    private EngineRegistry registry;
    private EngineConfiguration configuration;

    public IrisEngine(IrisPlatform platform, EngineConfiguration configuration) {
        this.configuration = configuration;
        this.platform = platform;
        this.registry = EngineRegistry.builder()
            .blockRegistry(new PlatformRegistry<>(platform.getBlocks()))
            .biomeRegistry(new PlatformRegistry<>(platform.getBiomes()))
            .build();
    }
}
