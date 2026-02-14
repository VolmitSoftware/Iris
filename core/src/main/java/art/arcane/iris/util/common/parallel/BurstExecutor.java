package art.arcane.iris.util.parallel;

import art.arcane.iris.Iris;
import art.arcane.volmlib.util.parallel.BurstExecutorSupport;
import java.util.concurrent.ExecutorService;

public class BurstExecutor extends BurstExecutorSupport {
    public BurstExecutor(ExecutorService executor, int burstSizeEstimate) {
        super(executor, burstSizeEstimate, Iris::reportError);
    }
}
