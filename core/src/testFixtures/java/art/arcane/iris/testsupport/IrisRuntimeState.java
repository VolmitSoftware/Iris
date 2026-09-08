package art.arcane.iris.testsupport;

import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.IrisServices;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

public final class IrisRuntimeState {
    private static final Map<Class<?>, Object> SERVICES = services();

    private IrisRuntimeState() {
    }

    public static boolean platformBound() {
        return IrisPlatforms.isBound();
    }

    public static List<String> registeredServices() {
        TreeSet<String> names = new TreeSet<>();

        for (Class<?> type : SERVICES.keySet()) {
            names.add(type.getName());
        }

        return Collections.unmodifiableList(new ArrayList<>(names));
    }

    public static void reset() {
        IrisPlatforms.unbind();
        IrisServices.clear();
    }

    private static Map<Class<?>, Object> services() {
        try {
            Field field = IrisServices.class.getDeclaredField("SERVICES");
            field.setAccessible(true);
            return castServices(field.get(null));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("IrisServices no longer exposes its backing map to the test guard", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<Class<?>, Object> castServices(Object value) {
        return (Map<Class<?>, Object>) value;
    }
}
