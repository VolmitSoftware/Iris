package art.arcane.iris.generation.concurrent;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.parallel.BurstExecutorSupport;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

public class BurstExecutor extends BurstExecutorSupport {
    public BurstExecutor(ExecutorService executor, int burstSizeEstimate) {
        super(executor, burstSizeEstimate, IrisLogging::reportError);
    }

    public BurstExecutor(Supplier<ExecutorService> executorSource, int burstSizeEstimate) {
        super(executorSource, burstSizeEstimate, IrisLogging::reportError);
    }
}
