package art.arcane.iris.world.task;

import org.junit.After;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class JRepeatingTaskLifecycleTest {
    @After
    public void clearTrackedTasks() {
        trackedTasks().clear();
    }

    @Test
    public void pluginCancellationDrainsEveryTrackedRepeaterOnce() {
        AtomicInteger cancellations = new AtomicInteger();
        Map<Integer, Runnable> tracked = trackedTasks();
        tracked.put(-1, cancellations::incrementAndGet);
        tracked.put(-2, cancellations::incrementAndGet);

        J.cancelPluginTasks();
        J.cancelPluginTasks();

        assertEquals(2, cancellations.get());
        assertTrue(tracked.isEmpty());
    }

    @SuppressWarnings("unchecked")
    private static Map<Integer, Runnable> trackedTasks() {
        try {
            Field field = J.class.getDeclaredField("REPEATING_CANCELLERS");
            field.setAccessible(true);
            return (Map<Integer, Runnable>) field.get(null);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Unable to inspect tracked Iris repeaters", failure);
        }
    }
}
