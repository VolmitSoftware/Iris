package art.arcane.iris.spi;

import art.arcane.volmlib.nativelib.terrain.NativeGenerationRegistry;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public interface PlatformGenerationRegistry extends NativeGenerationRegistry {
    Pattern SHA_256_PATTERN = Pattern.compile("[0-9a-f]{64}");

    String runtimeIdentity();

    String generatedDefinitionRendererIdentity();

    default String customBiomeResourceKey(String identitySha256) {
        return contentAddressedCustomBiomeResourceKey(identitySha256);
    }

    default List<String> legacyCustomBiomeResourceKeys(
            String packName,
            String dimensionKey,
            String customBiomeId
    ) {
        return List.of();
    }

    String dimensionTypeResourceKey(String packName, String dimensionKey, String dimensionTypeKey);

    static String contentAddressedCustomBiomeResourceKey(String identitySha256) {
        String requiredFingerprint = Objects.requireNonNull(identitySha256, "identitySha256");
        if (!SHA_256_PATTERN.matcher(requiredFingerprint).matches()) {
            throw new IllegalArgumentException("Custom biome identity fingerprint must be a lowercase SHA-256 value.");
        }
        return "iris:biomes/" + requiredFingerprint;
    }

}
