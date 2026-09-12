package art.arcane.iris.generation.runtime;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.world.WorldUnloadEvent;
import org.junit.Test;

import java.lang.reflect.Method;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class IrisEngineLifecycleContractTest {
    @Test
    public void engineServiceExclusivelyOwnsWorldUnload() throws NoSuchMethodException {
        assertMonitorUnloadHandler(IrisEngineSVC.class.getMethod("onWorldUnload", WorldUnloadEvent.class));
        for (Method method : EngineAssignedWorldManager.class.getDeclaredMethods()) {
            boolean handlesWorldUnload = method.getParameterCount() == 1
                    && method.getParameterTypes()[0] == WorldUnloadEvent.class
                    && method.isAnnotationPresent(EventHandler.class);
            assertFalse("EngineAssignedWorldManager must defer world unload ownership to IrisEngineSVC.", handlesWorldUnload);
        }
    }

    @Test
    public void registrationIdentityConflictsByWorldIdentityOrFolder() {
        Path sharedFolder = Path.of("build", "worlds", "shared");
        IrisEngineSVC.RegistrationIdentity first =
                new IrisEngineSVC.RegistrationIdentity("minecraft:first", sharedFolder);
        IrisEngineSVC.RegistrationIdentity sameIdentity =
                new IrisEngineSVC.RegistrationIdentity("minecraft:first", Path.of("build", "worlds", "other"));
        IrisEngineSVC.RegistrationIdentity sameFolder =
                new IrisEngineSVC.RegistrationIdentity("minecraft:other", sharedFolder);
        IrisEngineSVC.RegistrationIdentity distinct =
                new IrisEngineSVC.RegistrationIdentity("minecraft:distinct", Path.of("build", "worlds", "distinct"));

        assertTrue(first.conflictsWith(sameIdentity));
        assertTrue(first.conflictsWith(sameFolder));
        assertFalse(first.conflictsWith(distinct));
    }

    private static void assertMonitorUnloadHandler(Method method) {
        EventHandler eventHandler = method.getAnnotation(EventHandler.class);
        assertNotNull(eventHandler);
        assertEquals(EventPriority.MONITOR, eventHandler.priority());
        assertTrue(eventHandler.ignoreCancelled());
    }
}
