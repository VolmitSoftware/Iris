package com.volmit.host;

import com.volmit.IrisModule;
import example.builtin.CarveModule;
import org.junit.jupiter.api.Test;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ExposedHostClassLoaderTest {
    private static final ClassLoader HOST = IrisModule.class.getClassLoader();
    private static final String HOST_LIBRARY_SERVICE_FILE = "META-INF/services/org.junit.platform.engine.TestEngine";

    private final ExposedHostClassLoader loader = new ExposedHostClassLoader(HOST, ExposedPackages.apiOnly());

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
    void hidesHostClassesOutsideTheExposedPackages() {
        assertDoesNotThrow(() -> HOST.loadClass(Test.class.getName()));
        assertThrows(ClassNotFoundException.class, () -> loader.loadClass(Test.class.getName()));
        assertThrows(ClassNotFoundException.class, () -> loader.loadClass(CarveModule.class.getName()));
    }

    @Test
    void loadsClassesAndResourcesOfExtraExposedPackagesFromTheHost() throws Exception {
        var exposing = new ExposedHostClassLoader(HOST, ExposedPackages.apiAnd(List.of(CarveModule.class.getPackageName())));

        assertSame(CarveModule.class, exposing.loadClass(CarveModule.class.getName()));
        assertSame(IrisModule.class, exposing.loadClass(IrisModule.class.getName()));
        assertNotNull(exposing.getResource(classFile(CarveModule.class)));
        assertTrue(exposing.hasClass(CarveModule.class.getName()));
        assertThrows(ClassNotFoundException.class, () -> exposing.loadClass(Test.class.getName()));
    }

    @Test
    void resourcesFollowTheSameRuleAsClasses() throws Exception {
        assertNotNull(loader.getResource(classFile(IrisModule.class)));
        assertNotNull(loader.getResource(classFile(List.class)));
        assertNull(loader.getResource(classFile(Test.class)));
        assertNull(loader.getResource(classFile(CarveModule.class)));
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
