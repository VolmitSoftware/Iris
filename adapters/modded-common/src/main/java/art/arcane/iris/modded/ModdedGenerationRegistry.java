package art.arcane.iris.modded;

import art.arcane.iris.spi.PlatformGenerationRegistry;
import art.arcane.volmlib.nativelib.terrain.NativeGenerationRegistry;

import java.util.List;
import java.util.Objects;

final class ModdedGenerationRegistry implements PlatformGenerationRegistry {
    private final NativeGenerationRegistry nativeRegistry;
    private final String runtimeIdentity;
    private final String rendererIdentity;

    ModdedGenerationRegistry(NativeGenerationRegistry nativeRegistry, Identity identity) {
        this.nativeRegistry = Objects.requireNonNull(nativeRegistry, "nativeRegistry");
        this.runtimeIdentity = requireText(identity.runtime(), "runtimeIdentity");
        this.rendererIdentity = requireText(identity.renderer(), "rendererIdentity");
    }

    @Override
    public String runtimeIdentity() {
        return runtimeIdentity;
    }

    @Override
    public String generatedDefinitionRendererIdentity() {
        return rendererIdentity;
    }

    @Override
    public String customBiomeResourceKey(String identitySha256) {
        return PlatformGenerationRegistry.contentAddressedCustomBiomeResourceKey(identitySha256);
    }

    @Override
    public List<String> legacyCustomBiomeResourceKeys(
            String packName,
            String dimensionKey,
            String customBiomeId
    ) {
        return ModdedWorldgenIds.legacyBiomeRefs(
                requireText(packName, "packName"),
                requireText(dimensionKey, "dimensionKey"),
                requireText(customBiomeId, "customBiomeId")
        );
    }

    @Override
    public String dimensionTypeResourceKey(String packName, String dimensionKey, String dimensionTypeKey) {
        return ModdedWorldgenIds.dimensionTypeRef(
                requireText(packName, "packName"),
                requireText(dimensionKey, "dimensionKey")
        );
    }

    @Override
    public Definition definition(String registryKey, String resourceKey) {
        return nativeRegistry.definition(registryKey, resourceKey);
    }

    @Override
    public Definition canonicalDefinition(String registryKey, String resourceKey, String sourceJson) {
        return nativeRegistry.canonicalDefinition(registryKey, resourceKey, sourceJson);
    }

    @Override
    public Definition generatedDefinition(String registryKey, String resourceKey) {
        return nativeRegistry.generatedDefinition(registryKey, resourceKey);
    }

    private static String requireText(String value, String label) {
        String required = Objects.requireNonNull(value, label).trim();
        if (required.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be blank.");
        }
        return required;
    }

    record Identity(String runtime, String renderer) {
    }
}
