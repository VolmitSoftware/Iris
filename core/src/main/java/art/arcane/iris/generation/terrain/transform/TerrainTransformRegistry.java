package art.arcane.iris.generation.terrain.transform;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;

public final class TerrainTransformRegistry {
    private final Map<ProviderIdentity, TerrainTransformProvider> providers;

    public TerrainTransformRegistry(Iterable<TerrainTransformProvider> providers) {
        Map<ProviderIdentity, TerrainTransformProvider> indexed = new HashMap<>();
        for (TerrainTransformProvider provider : Objects.requireNonNull(providers, "terrain transform providers")) {
            Objects.requireNonNull(provider, "terrain transform provider");
            ProviderIdentity identity = new ProviderIdentity(provider.id(), provider.version());
            if (indexed.putIfAbsent(identity, provider) != null) {
                throw new IllegalArgumentException("Duplicate terrain transform provider " + identity + ".");
            }
        }
        this.providers = Map.copyOf(indexed);
    }

    public static TerrainTransformRegistry discover() {
        return new TerrainTransformRegistry(ServiceLoader.load(
                TerrainTransformProvider.class, TerrainTransformProvider.class.getClassLoader()));
    }

    public TerrainTransformer resolve(IrisTerrainTransform descriptor) {
        if (descriptor == null) {
            return null;
        }
        ProviderIdentity identity = new ProviderIdentity(descriptor.getId(), descriptor.getVersion());
        TerrainTransformProvider provider = providers.get(identity);
        if (provider == null) {
            throw new IllegalStateException("Required terrain transform provider " + identity + " is unavailable.");
        }
        Map<String, String> settings = Map.copyOf(Objects.requireNonNull(
                descriptor.getSettings(), "terrain transform settings"));
        TerrainTransformer transformer = Objects.requireNonNull(
                provider.create(settings), "terrain transformer returned by " + identity);
        int radius = transformer.radius();
        if (radius < 0 || radius > 16) {
            throw new IllegalArgumentException("Terrain transform provider " + identity
                    + " requires radius " + radius + "; the supported range is 0 through 16 blocks.");
        }
        return transformer;
    }

    private static String requireIdentity(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank.");
        }
        return value;
    }

    private record ProviderIdentity(String id, String version) {
        private ProviderIdentity {
            id = requireIdentity(id, "Terrain transform provider ID");
            version = requireIdentity(version, "Terrain transform provider version");
        }
    }
}
