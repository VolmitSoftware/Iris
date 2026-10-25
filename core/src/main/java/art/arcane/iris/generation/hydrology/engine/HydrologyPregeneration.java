package art.arcane.iris.generation.hydrology.engine;

import java.util.List;
import java.util.Objects;

public final class HydrologyPregeneration implements AutoCloseable {
    private final HydrologyRuntime runtime;
    private final Object epoch = new Object();
    private boolean closed;

    HydrologyPregeneration(HydrologyRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    public synchronized void prepare(List<long[]> regionOrder) {
        if (!closed) {
            runtime.preparePregeneration(epoch, regionOrder);
        }
    }

    @Override
    public synchronized void close() {
        if (!closed) {
            closed = true;
            runtime.cancelPregeneration(epoch);
        }
    }
}
