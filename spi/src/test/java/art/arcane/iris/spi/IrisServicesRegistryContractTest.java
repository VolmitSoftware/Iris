package art.arcane.iris.spi;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class IrisServicesRegistryContractTest {
    @Before
    public void requireEmptyRegistry() {
        IrisServices.clear();
    }

    @After
    public void releaseRegistry() {
        IrisServices.clear();
    }

    @Test
    public void registeredServicesResolveThroughBothAccessors() {
        Greeter greeter = new Greeter();

        IrisServices.register(Greeter.class, greeter);

        assertSame(greeter, IrisServices.get(Greeter.class));
        assertSame(greeter, IrisServices.getOrNull(Greeter.class));
    }

    @Test
    public void anAbsentServiceNamesItselfInTheFailure() {
        assertNull(IrisServices.getOrNull(Greeter.class));

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> IrisServices.get(Greeter.class));

        assertTrue(failure.getMessage(), failure.getMessage().contains(Greeter.class.getName()));
    }

    @Test
    public void reregisteringATypeReplacesThePreviousImplementation() {
        Greeter first = new Greeter();
        Greeter second = new Greeter();

        IrisServices.register(Greeter.class, first);
        IrisServices.register(Greeter.class, second);

        assertSame(second, IrisServices.get(Greeter.class));
    }

    @Test
    public void removingAServiceLeavesTheRegistryUsable() {
        IrisServices.register(Greeter.class, new Greeter());

        IrisServices.remove(Greeter.class);
        IrisServices.remove(Greeter.class);

        assertNull(IrisServices.getOrNull(Greeter.class));
    }

    @Test
    public void clearDropsEveryRegistration() {
        IrisServices.register(Greeter.class, new Greeter());
        IrisServices.register(Counter.class, new Counter());

        IrisServices.clear();

        assertNull(IrisServices.getOrNull(Greeter.class));
        assertNull(IrisServices.getOrNull(Counter.class));
    }

    @Test
    public void aMismatchedImplementationIsRejectedBeforeItEntersTheRegistry() {
        assertThrows(ClassCastException.class, () -> IrisServices.register(Greeter.class, new Counter()));

        assertNull(IrisServices.getOrNull(Greeter.class));
    }

    @Test
    public void aNullImplementationIsRejectedBeforeItEntersTheRegistry() {
        assertThrows(NullPointerException.class, () -> IrisServices.register(Greeter.class, null));

        assertNull(IrisServices.getOrNull(Greeter.class));
    }

    private static final class Greeter {
    }

    private static final class Counter {
    }
}
