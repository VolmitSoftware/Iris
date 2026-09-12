package art.arcane.iris.integration;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ExternalDataSVCRegistryTest {
    @Test
    public void registryLoadsWithoutResolvingOptionalProviderClasses() throws Exception {
        Class<?> serviceClass = Class.forName(ExternalDataSVC.class.getName(), true, ExternalDataSVC.class.getClassLoader());
        Field registryField = serviceClass.getDeclaredField("BUILT_IN_PROVIDERS");
        registryField.setAccessible(true);
        List<?> definitions = (List<?>) registryField.get(null);
        List<String> pluginIds = new ArrayList<>(definitions.size());

        for (Object definition : definitions) {
            Method pluginIdMethod = definition.getClass().getDeclaredMethod("pluginId");
            Method classNameMethod = definition.getClass().getDeclaredMethod("className");
            pluginIdMethod.setAccessible(true);
            classNameMethod.setAccessible(true);
            pluginIds.add((String) pluginIdMethod.invoke(definition));
            assertTrue(((String) classNameMethod.invoke(definition)).startsWith("art.arcane.iris.integration.data."));
        }

        assertEquals(List.of(
                "CraftEngine",
                "Nexo",
                "Oraxen",
                "ItemsAdder",
                "ExecutableItems",
                "MMOItems",
                "EcoItems",
                "MythicMobs",
                "MythicCrucible",
                "KGenerators"
        ), pluginIds);
    }
}
