package com.volmit.iris.engine.framework;

import java.util.function.Function;

public interface ListFunction<T, R> extends Function<T, R> {
    String key();
    String fancyName();
}
