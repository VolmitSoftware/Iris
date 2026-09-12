package art.arcane.iris.generation.runtime;

import art.arcane.iris.pack.loading.IrisData;

import java.util.function.Function;

public interface ListFunction<R> extends Function<IrisData, R> {
    String key();
    String fancyName();
}
