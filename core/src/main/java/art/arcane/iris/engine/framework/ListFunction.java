package art.arcane.iris.engine.framework;

import art.arcane.iris.core.loader.IrisData;

import java.util.function.Function;

public interface ListFunction<R> extends Function<IrisData, R> {
    String key();
    String fancyName();
}
