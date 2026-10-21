package art.arcane.iris.generation.runtime;

import java.util.concurrent.atomic.LongAdder;

public enum GenerationCounter {
    GENERATOR_EVALUATIONS("generator_evaluations"),
    CORNER_STARCASTS("corner_starcasts"),
    HYDROLOGY_LOOKUPS("hydrology_lookups");

    public static final boolean ENABLED = Boolean.getBoolean("iris.generation.counters");

    private final String key;
    private final LongAdder count = new LongAdder();

    GenerationCounter(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public void add(long amount) {
        count.add(amount);
    }

    public void increment() {
        count.increment();
    }

    public long sum() {
        return count.sum();
    }
}
