package com.volmit.iris.core.nms.container;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public record BlockProperty(String name, String defaultValue, Set<String> value) {
    public static <T> BlockProperty of(String name, T defaultValue, Collection<T> values, Function<T, String> nameFunction) {
        return new BlockProperty(name, nameFunction.apply(defaultValue), values.stream().map(nameFunction).collect(Collectors.toSet()));
    }

    @Override
    public @NotNull String toString() {
        return name + "=" + defaultValue + " [" + String.join(",", value) + "]";
    }
}
