package art.arcane.iris.util.parallel;

import art.arcane.volmlib.util.parallel.NoopGridLockSupport;

public class NOOPGridLock extends NoopGridLockSupport {
    public NOOPGridLock(int x, int z) {
        super(x, z);
    }
}
