package art.arcane.iris.probe;

import io.papermc.paper.registry.PaperRegistryAccess;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

final class PaperRegistryScope implements AutoCloseable {
    private final Map<Object, Object> registries;
    private final Map<Object, Object> previous;

    private PaperRegistryScope(Map<Object, Object> registries) {
        this.registries = registries;
        this.previous = new HashMap<>(registries);
        registries.clear();
    }

    static PaperRegistryScope open() {
        return new PaperRegistryScope(registries());
    }

    @Override
    public void close() {
        registries.clear();
        registries.putAll(previous);
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> registries() {
        try {
            Field field = PaperRegistryAccess.class.getDeclaredField("registries");
            field.setAccessible(true);
            return (Map<Object, Object>) field.get(PaperRegistryAccess.instance());
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Paper registry access has no registries map to isolate", failure);
        }
    }
}
