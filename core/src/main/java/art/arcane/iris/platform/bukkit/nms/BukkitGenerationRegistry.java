package art.arcane.iris.platform.bukkit.nms;

import art.arcane.iris.spi.PlatformGenerationRegistry;
import art.arcane.volmlib.nativelib.terrain.NativeGenerationRegistry;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public record BukkitGenerationRegistry(
        NativeGenerationRegistry registry,
        String runtimeIdentity,
        String generatedDefinitionRendererIdentity
) implements PlatformGenerationRegistry {
    public BukkitGenerationRegistry {
        registry = Objects.requireNonNull(registry, "registry");
        runtimeIdentity = requireText(runtimeIdentity, "runtimeIdentity");
        generatedDefinitionRendererIdentity = requireText(generatedDefinitionRendererIdentity, "rendererIdentity");
    }

    @Override
    public List<String> legacyCustomBiomeResourceKeys(String packName, String dimensionKey, String customBiomeId) {
        return List.of(requireText(dimensionKey, "dimensionKey").toLowerCase(Locale.ROOT)
                + ":" + requireText(customBiomeId, "customBiomeId").toLowerCase(Locale.ROOT));
    }

    @Override
    public String dimensionTypeResourceKey(String packName, String dimensionKey, String dimensionTypeKey) {
        return "iris:" + requireText(dimensionTypeKey, "dimensionTypeKey").toLowerCase(Locale.ROOT);
    }

    @Override
    public Definition definition(String registryKey, String resourceKey) {
        return registry.definition(registryKey, resourceKey);
    }

    @Override
    public Definition generatedDefinition(String registryKey, String resourceKey) {
        return registry.generatedDefinition(registryKey, resourceKey);
    }

    @Override
    public Definition canonicalDefinition(String registryKey, String resourceKey, String sourceJson) {
        return registry.canonicalDefinition(registryKey, resourceKey, sourceJson);
    }

    private static String requireText(String value, String label) {
        String required = Objects.requireNonNull(value, label).trim();
        if (required.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be blank.");
        }
        return required;
    }
}
