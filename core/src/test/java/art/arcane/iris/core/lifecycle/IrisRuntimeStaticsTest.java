package art.arcane.iris.core.lifecycle;

import art.arcane.iris.spi.IrisServices;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertNull;

public class IrisRuntimeStaticsTest {
    @After
    public void clearStatics() {
        IrisRuntimeStatics.reset();
    }

    @Test
    public void resetUnbindsEveryRegisteredService() {
        IrisServices.register(Runnable.class, (Runnable) () -> {
        });

        IrisRuntimeStatics.reset();

        assertNull(IrisServices.getOrNull(Runnable.class));
    }
}
