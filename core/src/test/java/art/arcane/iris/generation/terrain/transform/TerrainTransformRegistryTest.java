package art.arcane.iris.generation.terrain.transform;

import art.arcane.volmlib.util.collection.KMap;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

public class TerrainTransformRegistryTest {
    @Test
    public void absentConfigurationDisablesTransformation() {
        assertNull(new TerrainTransformRegistry(List.of()).resolve(null));
    }

    @Test
    public void selectsThePinnedProviderVersion() {
        TestProvider first = new TestProvider("1", 0);
        TestProvider second = new TestProvider("2", 16);
        TerrainTransformRegistry registry = new TerrainTransformRegistry(List.of(first, second));

        assertSame(first.transformer, registry.resolve(descriptor("1")));
        assertSame(second.transformer, registry.resolve(descriptor("2")));
    }

    @Test
    public void rejectsUnavailableProvidersAndVersions() {
        TerrainTransformRegistry registry = new TerrainTransformRegistry(List.of(new TestProvider("1", 1)));

        assertThrows(IllegalStateException.class, () -> registry.resolve(descriptor("2")));
        assertThrows(IllegalStateException.class, () -> registry.resolve(descriptor("1").setId("missing")));
    }

    @Test
    public void freezesSettingsBeforeCreatingTheTransformer() {
        TestProvider provider = new TestProvider("1", 1);
        KMap<String, String> settings = new KMap<>();
        settings.put("strength", "2");
        IrisTerrainTransform descriptor = descriptor("1").setSettings(settings);

        new TerrainTransformRegistry(List.of(provider)).resolve(descriptor);
        settings.put("strength", "9");

        assertEquals("2", provider.settings.get("strength"));
        assertThrows(UnsupportedOperationException.class, () -> provider.settings.put("strength", "3"));
    }

    @Test
    public void rejectsDuplicateProviderIdentities() {
        assertThrows(IllegalArgumentException.class, () -> new TerrainTransformRegistry(
                List.of(new TestProvider("1", 1), new TestProvider("1", 2))));
    }

    @Test
    public void rejectsMissingIdentityAndInvalidNeighborhoodSizes() {
        assertThrows(IllegalArgumentException.class,
                () -> new TerrainTransformRegistry(List.of(new TestProvider(" ", 1))));
        TerrainTransformRegistry registry = new TerrainTransformRegistry(List.of(new TestProvider("1", 1)));
        assertThrows(IllegalArgumentException.class, () -> registry.resolve(descriptor(" ")));
        assertThrows(IllegalArgumentException.class, () -> registry.resolve(descriptor("1").setId("")));
        assertThrows(IllegalArgumentException.class, () -> new TerrainTransformRegistry(
                List.of(new TestProvider("1", -1))).resolve(descriptor("1")));
        assertThrows(IllegalArgumentException.class, () -> new TerrainTransformRegistry(
                List.of(new TestProvider("1", 17))).resolve(descriptor("1")));
    }

    private static IrisTerrainTransform descriptor(String version) {
        return new IrisTerrainTransform().setId("test").setVersion(version);
    }

    private static final class TestProvider implements TerrainTransformProvider {
        private final String version;
        private final TerrainTransformer transformer;
        private Map<String, String> settings;

        private TestProvider(String version, int radius) {
            this.version = version;
            this.transformer = new TestTransformer(radius);
        }

        @Override
        public String id() {
            return "test";
        }

        @Override
        public String version() {
            return version;
        }

        @Override
        public TerrainTransformer create(Map<String, String> settings) {
            this.settings = settings;
            return transformer;
        }
    }

    private record TestTransformer(int radius) implements TerrainTransformer {
        @Override
        public void transform(TerrainTransformContext context) {
        }
    }
}
