package art.arcane.iris.studio.map;

import org.junit.Test;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GpuLibraryTest {
    @Test
    public void missingNativeLibrariesUseSoftwareEvenWhenBindingClassesExist() throws Exception {
        List<URL> classpath = new ArrayList<>();
        for (String entry : System.getProperty("java.class.path").split(Pattern.quote(File.pathSeparator))) {
            if (!entry.contains("natives-")) {
                classpath.add(new File(entry).toURI().toURL());
            }
        }
        ClassLoader loader = GpuLibrary.class.getClassLoader();
        while (loader != null) {
            if (loader instanceof URLClassLoader urls) {
                for (URL url : urls.getURLs()) {
                    if (!url.getPath().contains("natives-")) {
                        classpath.add(url);
                    }
                }
            }
            loader = loader.getParent();
        }
        assertFalse(classpath.isEmpty());
        try (URLClassLoader isolated = new URLClassLoader(classpath.toArray(URL[]::new), ClassLoader.getPlatformClassLoader())) {
            assertEquals("org.lwjgl.system.MemoryStack", Class.forName("org.lwjgl.system.MemoryStack", false, isolated).getName());
            assertTrue(Class.forName("org.lwjgl.system.jawt.JAWT", false, isolated).getClassLoader() == isolated);
            Class<?> library = Class.forName(GpuLibrary.class.getName(), true, isolated);
            Method available = library.getDeclaredMethod("isLoaded");
            available.setAccessible(true);

            assertEquals(Boolean.FALSE, available.invoke(null));
        }
    }
}
