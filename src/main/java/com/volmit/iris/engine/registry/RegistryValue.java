package com.volmit.iris.engine.registry;

import lombok.Data;

@Data
public class RegistryValue<NATIVE, T> {
    private final NATIVE nativeValue;
    private final T value;
}
