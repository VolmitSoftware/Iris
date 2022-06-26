package com.volmit.iris.platform;

public interface PlatformTransformer<NATIVE, T> {
    T toIris(NATIVE nativeType);

    NATIVE toNative(T t);
}
