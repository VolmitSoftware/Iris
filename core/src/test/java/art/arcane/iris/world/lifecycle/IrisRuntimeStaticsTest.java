package art.arcane.iris.world.lifecycle;

import art.arcane.iris.world.runtime.WorldRuntimeControlService;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.testsupport.BukkitTestServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;

/**
 * Iris keeps process-wide caches that describe the running server: the service registry, and the lifecycle
 * and runtime-control singletons that each hold a capability snapshot of reflective handles onto the live
 * server. Nothing cleared them on disable, so a re-enable in the same JVM kept answering from the previous
 * boot's snapshot.
 */
public class IrisRuntimeStaticsTest {
    @Before
    public void installServer() {
        BukkitTestServer.install();
    }

    @After
    public void clearStatics() {
        IrisRuntimeStatics.reset();
    }

    @Test
    public void resetUnbindsEveryRegisteredService() {
        Runnable service = () -> {
        };
        IrisServices.register(Runnable.class, service);

        IrisRuntimeStatics.reset();

        assertNull(IrisServices.getOrNull(Runnable.class));
    }

    @Test
    public void resetDropsTheCachedCapabilitySingletons() {
        WorldLifecycleService lifecycle = WorldLifecycleService.get();
        WorldRuntimeControlService control = WorldRuntimeControlService.get();

        IrisRuntimeStatics.reset();

        assertNotSame(lifecycle, WorldLifecycleService.get());
        assertNotSame(control, WorldRuntimeControlService.get());
    }
}
