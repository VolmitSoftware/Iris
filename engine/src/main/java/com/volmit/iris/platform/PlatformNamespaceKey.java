package com.volmit.iris.platform;

import art.arcane.cram.PakKey;
import com.volmit.iris.util.NSK;

public interface PlatformNamespaceKey {
    String getNamespace();

    String getKey();

    String toString();

    static PlatformNamespaceKey of(PakKey key) {
        return new NSK(key.getNamespace(), key.getKey());
    }
}
