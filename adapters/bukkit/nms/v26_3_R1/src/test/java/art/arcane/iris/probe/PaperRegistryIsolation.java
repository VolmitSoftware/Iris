package art.arcane.iris.probe;

import io.papermc.paper.registry.PaperRegistryAccess;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public final class PaperRegistryIsolation implements TestRule {
    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                Map<Object, Object> registries = registries();
                Map<Object, Object> previous = new HashMap<>(registries);
                registries.clear();
                try {
                    base.evaluate();
                } finally {
                    registries.clear();
                    registries.putAll(previous);
                }
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> registries() throws ReflectiveOperationException {
        Field field = PaperRegistryAccess.class.getDeclaredField("registries");
        field.setAccessible(true);
        return (Map<Object, Object>) field.get(PaperRegistryAccess.instance());
    }
}
