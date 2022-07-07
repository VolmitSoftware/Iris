package com.volmit.iris.engine.resolver;

import com.volmit.iris.platform.PlatformNamespaceKey;
import lombok.Data;

@Data
public class EngineResolvable implements Resolvable {
    private PlatformNamespaceKey key;
}
