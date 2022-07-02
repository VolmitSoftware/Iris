package com.volmit.iris.engine.dimension;

import com.volmit.iris.engine.editor.Resolvable;
import com.volmit.iris.platform.PlatformNamespaceKey;
import lombok.Data;

@Data
public class IrisResolvable implements Resolvable {
    private PlatformNamespaceKey key;

    @Override
    public PlatformNamespaceKey getKey() {
        return null;
    }
}
