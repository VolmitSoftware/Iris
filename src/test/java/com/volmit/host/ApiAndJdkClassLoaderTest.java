package com.volmit.host;

import com.volmit.IrisModule;
import org.junit.jupiter.api.Test;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ApiAndJdkClassLoaderTest {
    private static final ClassLoader HOST = IrisModule.class.getClassLoader();
    private static final String HOST_LIBRARY_SERVICE_FILE = "META-INF/services/org.junit.platform.engine.TestEngine";

    private final ApiAndJdkClassLoader loader = new ApiAndJdkClassLoader(HOST);

    private static String classFile(Class<?> type) {
        return type.getName().replace('.', '/') + ".class";
    }

    @Test
    void loadsApiClassesFromTheHostAndJdkClassesFromThePlatform() throws Exception {
        assertSame(IrisModule.class, loader.loadClass(IrisModule.class.getName()));
        assertSame(List.class, loader.loadClass(List.class.getName()));
        assertSame(java.sql.Connection.class, loader.loadClass(java.sql.Connection.class.getName()));
    }

    @Test
    void hidesHostClassesOutsideTheApi() {
        assertDoesNotThrow(() -> HOST.loadClass(Test.class.getName()));
        assertThrows(ClassNotFoundException.class, () -> loader.loadClass(Test.class.getName()));
    }

    @Test
    void resourcesFollowTheSameRuleAsClasses() throws Exception {
        assertNotNull(loader.getResource(classFile(IrisModule.class)));
        assertNotNull(loader.getResource(classFile(List.class)));
        assertNull(loader.getResource(classFile(Test.class)));
        assertFalse(Collections.list(HOST.getResources(HOST_LIBRARY_SERVICE_FILE)).isEmpty());
        assertTrue(Collections.list(loader.getResources(HOST_LIBRARY_SERVICE_FILE)).isEmpty());
    }

    @Test
    void knowsWhichClassesItHasWithoutLoadingThem() {
        assertTrue(loader.hasClass(IrisModule.class.getName()));
        assertTrue(loader.hasClass("java.lang.String"));
        assertFalse(loader.hasClass(Test.class.getName()));
        assertFalse(loader.hasClass("com.volmit.DoesNotExist"));
    }

    @Test
    void knowsWhichClassesTheHostHasEvenWhenItHidesThem() {
        assertTrue(loader.hostHasClass(Test.class.getName()));
        assertTrue(loader.hostHasClass(IrisModule.class.getName()));
        assertFalse(loader.hostHasClass("org.nowhere.Missing"));
    }
}
