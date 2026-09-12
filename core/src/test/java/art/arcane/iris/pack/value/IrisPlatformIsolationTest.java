package art.arcane.iris.pack.value;

import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.world.IrisEnvironment;
import art.arcane.iris.world.IrisWorld;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class IrisPlatformIsolationTest {
    @Test
    public void irisWorldHasNoBukkitSignatures() {
        IrisWorld world = IrisWorld.builder()
                .platformIdentity("minecraft:overworld")
                .name("overworld")
                .seed(1337L)
                .minHeight(-64)
                .maxHeight(320)
                .build();

        assertEquals("minecraft:overworld", world.identity());
        assertEquals(1337L, world.getRawWorldSeed());
        assertEquals(384, world.getHeight());
        assertNoBukkitSignatures(IrisWorld.class);
        assertNoBukkitSignatures(IrisWorld.IrisWorldBuilder.class);
    }

    @Test
    public void irisDimensionEnvironmentAndHashCodeArePlatformNeutral() {
        IrisDimension dimension = new IrisDimension();

        assertEquals(IrisEnvironment.NORMAL, dimension.getEnvironment());
        dimension.hashCode();
        assertNoBukkitSignatures(IrisDimension.class);
    }

    private static void assertNoBukkitSignatures(Class<?> type) {
        for (Field field : type.getDeclaredFields()) {
            assertPlatformNeutral(field.getType());
        }
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            for (Class<?> parameter : constructor.getParameterTypes()) {
                assertPlatformNeutral(parameter);
            }
        }
        for (Method method : type.getDeclaredMethods()) {
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
