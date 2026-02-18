package art.arcane.iris.util.common.parallel;

import art.arcane.iris.Iris;
import art.arcane.volmlib.util.parallel.GridLockSupport;

public class GridLock extends GridLockSupport {
    public GridLock(int x, int z) {
        super(x, z, Iris::reportError);
    }
}
