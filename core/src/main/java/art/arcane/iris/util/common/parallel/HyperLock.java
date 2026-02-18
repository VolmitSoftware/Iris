package art.arcane.iris.util.common.parallel;

import art.arcane.iris.Iris;
import art.arcane.volmlib.util.parallel.HyperLockSupport;

public class HyperLock extends HyperLockSupport {
    public HyperLock() {
        this(1024, false);
    }

    public HyperLock(int capacity) {
        this(capacity, false);
    }

    public HyperLock(int capacity, boolean fair) {
        super(capacity, fair, Iris::warn, Iris::reportError);
    }
}
