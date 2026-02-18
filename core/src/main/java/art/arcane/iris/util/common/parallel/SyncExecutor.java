package art.arcane.iris.util.common.parallel;

import art.arcane.volmlib.util.parallel.SyncExecutorSupport;
import art.arcane.volmlib.util.math.M;
import art.arcane.volmlib.util.scheduling.SR;

public class SyncExecutor extends SyncExecutorSupport {
    public SyncExecutor(int msPerTick) {
        super(msPerTick, M::ms, task -> {
            SR sr = new SR() {
                @Override
                public void run() {
                    task.run();
                }
            };

            return sr::cancel;
        });
    }
}
