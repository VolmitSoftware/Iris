package art.arcane.iris.world.lifecycle;

import java.lang.reflect.Method;
import java.util.function.Predicate;

final class CapabilityResolution {
    private CapabilityResolution() {
    }

    static Method resolveMethod(Class<?> owner, String name, Predicate<Method> predicate) {
        Method selected = scanMethods(owner.getMethods(), name, predicate);
        if (selected != null) {
            return selected;
        }

        Class<?> current = owner;
        while (current != null) {
            selected = scanMethods(current.getDeclaredMethods(), name, predicate);
            if (selected != null) {
                selected.setAccessible(true);
                return selected;
            }
            current = current.getSuperclass();
        }

        return null;
    }

    private static Method scanMethods(Method[] methods, String name, Predicate<Method> predicate) {
        for (Method method : methods) {
            if (!method.getName().equals(name)) {
                continue;
            }
            if (predicate.test(method)) {
                return method;
            }
        }
        return null;
    }

}
