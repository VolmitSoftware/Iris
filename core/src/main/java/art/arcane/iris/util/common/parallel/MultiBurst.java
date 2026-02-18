package art.arcane.iris.util.common.parallel;

import art.arcane.iris.Iris;
import art.arcane.iris.core.IrisSettings;
import art.arcane.volmlib.util.parallel.MultiBurstSupport;
import art.arcane.volmlib.util.math.M;
import kotlinx.coroutines.CoroutineDispatcher;
import kotlinx.coroutines.ExecutorsKt;
import java.util.concurrent.ExecutorService;
import java.util.function.IntSupplier;

public class MultiBurst extends MultiBurstSupport {
    private static final long TIMEOUT = Long.getLong("iris.burst.timeout", 15000);
    public static final MultiBurst burst = new MultiBurst();
    public static final MultiBurst ioBurst = new MultiBurst("Iris IO", () -> IrisSettings.get().getConcurrency().getIoParallelism());
    private volatile CoroutineDispatcher dispatcher;
    private volatile ExecutorService dispatcherService;

    public MultiBurst() {
        this("Iris");
    }

    public MultiBurst(String name) {
        this(name, Thread.MIN_PRIORITY, () -> IrisSettings.get().getConcurrency().getParallelism());
    }

    public MultiBurst(String name, IntSupplier parallelism) {
        this(name, Thread.MIN_PRIORITY, parallelism);
    }

    public MultiBurst(String name, int priority, IntSupplier parallelism) {
        super(name, priority, parallelism, IrisSettings::getThreadCount, M::ms, Iris::reportError, Iris::info, Iris::warn, TIMEOUT);
    }

    public CoroutineDispatcher getDispatcher() {
        ExecutorService service = service();
        if (dispatcherService != service || dispatcher == null) {
            synchronized (this) {
                if (dispatcherService != service || dispatcher == null) {
                    dispatcher = ExecutorsKt.from(service);
                    dispatcherService = service;
                }
            }
        }

        return dispatcher;
    }

    @Override
    public BurstExecutor burst(int estimate) {
        return new BurstExecutor(service(), estimate);
    }

    @Override
    public BurstExecutor burst() {
        return burst(16);
    }

    @Override
    public BurstExecutor burst(boolean multicore) {
        BurstExecutor b = burst();
        b.setMulticore(multicore);
        return b;
    }

    public static void close(ExecutorService service) {
        MultiBurstSupport.close(service, M::ms, Iris::info, Iris::warn, Iris::reportError, TIMEOUT);
    }
}
