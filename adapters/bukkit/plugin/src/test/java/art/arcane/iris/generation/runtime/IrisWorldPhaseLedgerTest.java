package art.arcane.iris.generation.runtime;

import art.arcane.iris.api.world.IrisWorldPhase;
import org.bukkit.World;
import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class IrisWorldPhaseLedgerTest {
    @Test
    public void aWorldIsAnnouncedReadyOnceNoMatterHowOftenRegistrationRuns() {
        List<String> fired = new ArrayList<>();
        IrisWorldPhaseLedger ledger = ledger(fired);
        World world = world("alpha");

        ledger.ready(world);
        ledger.ready(world);
        ledger.ready(world);

        assertEquals(List.of("alpha:ENGINE_READY"), fired);
    }

    @Test
    public void closingIsNeverAnnouncedForAWorldThatWasNeverAnnouncedReady() {
        List<String> fired = new ArrayList<>();
        IrisWorldPhaseLedger ledger = ledger(fired);
        World world = world("alpha");

        ledger.closing(world);

        assertEquals(List.of(), fired);
    }

    @Test
    public void everyReadyWorldIsAnnouncedClosingExactlyOnce() {
        List<String> fired = new ArrayList<>();
        IrisWorldPhaseLedger ledger = ledger(fired);
        World world = world("alpha");

        ledger.ready(world);
        ledger.closing(world);
        ledger.closing(world);

        assertEquals(List.of("alpha:ENGINE_READY", "alpha:ENGINE_CLOSING"), fired);
    }

    @Test
    public void anEngineReplacementClosesBeforeItIsAnnouncedReadyAgain() {
        List<String> fired = new ArrayList<>();
        IrisWorldPhaseLedger ledger = ledger(fired);
        World world = world("alpha");

        ledger.ready(world);
        ledger.closing(world);
        ledger.ready(world);
        ledger.closing(world);

        assertEquals(List.of(
                "alpha:ENGINE_READY",
                "alpha:ENGINE_CLOSING",
                "alpha:ENGINE_READY",
                "alpha:ENGINE_CLOSING"), fired);
    }

    @Test
    public void worldsAreTrackedIndependently() {
        List<String> fired = new ArrayList<>();
        IrisWorldPhaseLedger ledger = ledger(fired);
        World first = world("alpha");
        World second = world("beta");

        ledger.ready(first);
        ledger.ready(second);
        ledger.closing(first);
        ledger.ready(first);

        assertEquals(List.of(
                "alpha:ENGINE_READY",
                "beta:ENGINE_READY",
                "alpha:ENGINE_CLOSING",
                "alpha:ENGINE_READY"), fired);
    }

    @Test
    public void aWorldThatIsNoLongerAddressableIsNeverAnnounced() {
        List<String> fired = new ArrayList<>();
        IrisWorldPhaseLedger ledger = ledger(fired);

        ledger.ready(null);
        ledger.closing(null);

        assertEquals(List.of(), fired);
    }

    private static IrisWorldPhaseLedger ledger(List<String> fired) {
        return new IrisWorldPhaseLedger((World world, IrisWorldPhase phase) ->
                fired.add(world.getName() + ":" + phase.name()));
    }

    private static World world(String name) {
        UUID identity = UUID.nameUUIDFromBytes(name.getBytes());
        return (World) Proxy.newProxyInstance(
                IrisWorldPhaseLedgerTest.class.getClassLoader(),
                new Class<?>[]{World.class},
                (Object proxy, Method method, Object[] arguments) -> switch (method.getName()) {
                    case "getUID" -> identity;
                    case "getName", "toString" -> name;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
