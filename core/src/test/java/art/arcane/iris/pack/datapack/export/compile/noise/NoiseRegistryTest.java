package art.arcane.iris.pack.datapack.export.compile.noise;

import art.arcane.iris.pack.datapack.export.ir.NoiseDef;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class NoiseRegistryTest {
    @Test
    public void registerReturnsNamespacedId() {
        NoiseRegistry registry = new NoiseRegistry("iris_export");
        assertEquals("iris_export:terrain/base", registry.register(new NoiseDef("terrain/base", -7, List.of(1.0, 0.5))));
        assertEquals("iris_export", registry.namespace());
    }

    @Test
    public void identicalDefinitionsShareOneEntry() {
        NoiseRegistry registry = new NoiseRegistry("iris_export");
        String first = registry.register(new NoiseDef("terrain/base", -7, List.of(1.0, 0.5)));
        String second = registry.register(new NoiseDef("terrain/base", -7, List.of(1.0, 0.5)));
        assertEquals(first, second);
        assertEquals(1, registry.definitions().size());
    }

    @Test
    public void sameParametersUnderDifferentIdsStayIndependent() {
        NoiseRegistry registry = new NoiseRegistry("iris_export");
        String first = registry.register(new NoiseDef("terrain/a", -7, List.of(1.0)));
        String second = registry.register(new NoiseDef("terrain/b", -7, List.of(1.0)));
        assertEquals("iris_export:terrain/a", first);
        assertEquals("iris_export:terrain/b", second);
        assertEquals(2, registry.definitions().size());
    }

    @Test
    public void conflictingDefinitionForIdIsRejected() {
        NoiseRegistry registry = new NoiseRegistry("iris_export");
        registry.register(new NoiseDef("terrain/base", -7, List.of(1.0)));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> registry.register(new NoiseDef("terrain/base", -6, List.of(1.0))));
        assertEquals("Noise 'iris_export:terrain/base' is already registered with different parameters", error.getMessage());
    }

    @Test
    public void definitionsAreSortedById() {
        NoiseRegistry registry = new NoiseRegistry("iris_export");
        registry.register(new NoiseDef("zeta", 0, List.of(1.0)));
        registry.register(new NoiseDef("alpha", 0, List.of(1.0)));
        registry.register(new NoiseDef("cell_hash", 0, List.of(1.0)));
        assertEquals(List.of("alpha", "cell_hash", "zeta"), registry.definitions().stream().map(NoiseDef::id).toList());
    }

    @Test
    public void byReferenceKeysDefinitionsByNamespacedId() {
        NoiseRegistry registry = new NoiseRegistry("iris_export");
        NoiseDef base = new NoiseDef("terrain/base", -7, List.of(1.0));
        NoiseDef detail = new NoiseDef("terrain/detail", -3, List.of(1.0, 0.5));
        registry.register(detail);
        registry.register(base);
        Map<String, NoiseDef> references = registry.byReference();
        assertEquals(List.of("iris_export:terrain/base", "iris_export:terrain/detail"), List.copyOf(references.keySet()));
        assertEquals(base, references.get("iris_export:terrain/base"));
        assertThrows(UnsupportedOperationException.class, () -> references.put("iris_export:other", base));
    }

    @Test
    public void namespaceIsValidated() {
        assertThrows(IllegalArgumentException.class, () -> new NoiseRegistry("Iris Export"));
    }
}
