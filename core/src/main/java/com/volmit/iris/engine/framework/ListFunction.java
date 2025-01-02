package com.volmit.iris.engine.framework;

import com.volmit.iris.core.loader.IrisData;

import java.util.function.Function;

public interface ListFunction<R> extends Function<IrisData, R> {
    String key();
    String fancyName();
}
