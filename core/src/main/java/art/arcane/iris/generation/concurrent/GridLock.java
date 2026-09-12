package art.arcane.iris.generation.concurrent;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.parallel.GridLockSupport;

public class GridLock extends GridLockSupport {
    public GridLock(int x, int z) {
        super(x, z, IrisLogging::reportError);
    }
}
