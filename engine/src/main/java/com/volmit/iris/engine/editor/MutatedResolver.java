package com.volmit.iris.engine.editor;

import com.volmit.iris.platform.PlatformNamespaced;

@FunctionalInterface
public interface MutatedResolver<T extends Mutated> {
    T resolve(PlatformNamespaced key);
}
