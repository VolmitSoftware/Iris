package com.volmit.iris.engine;

import com.volmit.iris.platform.PlatformBiome;
import com.volmit.iris.platform.PlatformBlock;
import com.volmit.iris.platform.PlatformRegistry;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class EngineRegistry {
    private final PlatformRegistry<PlatformBlock> blockRegistry;
    private final PlatformRegistry<PlatformBiome> biomeRegistry;
}
