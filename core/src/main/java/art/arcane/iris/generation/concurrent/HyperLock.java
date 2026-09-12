package art.arcane.iris.generation.concurrent;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.parallel.HyperLockSupport;

public class HyperLock extends HyperLockSupport {
    public HyperLock() {
        this(1024, false);
    }

    public HyperLock(int capacity) {
        this(capacity, false);
    }

    public HyperLock(int capacity, boolean fair) {
        super(capacity, fair, IrisLogging::warn, IrisLogging::reportError);
    }
}
