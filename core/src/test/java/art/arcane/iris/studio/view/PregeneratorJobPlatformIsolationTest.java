package art.arcane.iris.studio.view;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.assertFalse;

public class PregeneratorJobPlatformIsolationTest {
    @Test
    public void pregeneratorJobHasNoBukkitSignatures() {
        for (Field field : PregeneratorJob.class.getDeclaredFields()) {
            assertPlatformNeutral(field.getType());
        }
        for (Constructor<?> constructor : PregeneratorJob.class.getDeclaredConstructors()) {
            for (Class<?> parameter : constructor.getParameterTypes()) {
                assertPlatformNeutral(parameter);
            }
        }
        for (Method method : PregeneratorJob.class.getDeclaredMethods()) {
            assertPlatformNeutral(method.getReturnType());
            for (Class<?> parameter : method.getParameterTypes()) {
                assertPlatformNeutral(parameter);
            }
        }
    }

    private static void assertPlatformNeutral(Class<?> type) {
        Class<?> component = type;
        while (component.isArray()) {
            component = component.getComponentType();
        }
        assertFalse(component.getName(), component.getName().startsWith("org.bukkit."));
    }
}
