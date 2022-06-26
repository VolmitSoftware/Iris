package com.volmit.iris.platform;

import com.volmit.iris.engine.object.Namespaced;
import com.volmit.iris.engine.object.NSKey;

import java.text.Normalizer;
import java.util.stream.Stream;

public interface PlatformDataTransformer<NATIVE, T extends Namespaced> extends PlatformTransformer<NATIVE, T> {
    default Stream<NSKey> getRegistryKeys() {
        return getRegistry().map(this::getKey);
    }

    Stream<NATIVE> getRegistry();

    NSKey getKey(NATIVE nativeType);

    String getTypeName();

    default String getTypeNamePlural()
    {
        return getTypeName() + "s";
    }

    default String countSuffixName(int count)
    {
        return count + " " + (count == 1 ? getTypeName() : getTypeNamePlural());
    }
}
