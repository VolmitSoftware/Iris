package art.arcane.iris.platform.bukkit.nms.v26_2_R1;

import art.arcane.iris.spi.PlatformGenerationRegistry;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.DimensionType;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

final class NmsGenerationRegistry implements PlatformGenerationRegistry {
    private static final String BIOME_REGISTRY = "minecraft:worldgen/biome";
    private static final String STRUCTURE_REGISTRY = "minecraft:worldgen/structure";
    private static final String DIMENSION_TYPE_REGISTRY = "minecraft:dimension_type";
    private static final String BLOCK_REGISTRY = "minecraft:block";
    private static final String ENTITY_TYPE_REGISTRY = "minecraft:entity_type";

    private final Supplier<RegistryAccess> registryAccess;
    private final String runtimeIdentity;
    private final String rendererIdentity;

    NmsGenerationRegistry(
            Supplier<RegistryAccess> registryAccess,
            String runtimeIdentity,
            String rendererIdentity
    ) {
        this.registryAccess = Objects.requireNonNull(registryAccess, "registryAccess");
        this.runtimeIdentity = requireText(runtimeIdentity, "runtimeIdentity");
        this.rendererIdentity = requireText(rendererIdentity, "rendererIdentity");
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
        return List.of(requireText(dimensionKey, "dimensionKey").toLowerCase(Locale.ROOT)
                + ":" + requireText(customBiomeId, "customBiomeId").toLowerCase(Locale.ROOT));
    }

    @Override
    public String dimensionTypeResourceKey(String packName, String dimensionKey, String dimensionTypeKey) {
        return "iris:" + requireText(dimensionTypeKey, "dimensionTypeKey").toLowerCase(Locale.ROOT);
    }

    @Override
    public Definition definition(String registryKey, String resourceKey) {
        RegistryAccess access = Objects.requireNonNull(registryAccess.get(), "Minecraft registry access");
        return switch (registryKey) {
            case BIOME_REGISTRY -> presence(access, Registries.BIOME, registryKey, resourceKey);
            case STRUCTURE_REGISTRY -> presence(access, Registries.STRUCTURE, registryKey, resourceKey);
            case DIMENSION_TYPE_REGISTRY -> presence(
                    access,
                    Registries.DIMENSION_TYPE,
                    registryKey,
                    resourceKey
            );
            case BLOCK_REGISTRY -> presence(access, Registries.BLOCK, registryKey, resourceKey);
            case ENTITY_TYPE_REGISTRY -> presence(access, Registries.ENTITY_TYPE, registryKey, resourceKey);
            default -> throw new IllegalArgumentException("Unsupported generation registry: " + registryKey);
        };
    }

    @Override
    public Definition canonicalDefinition(String registryKey, String resourceKey, String sourceJson) {
        RegistryAccess access = Objects.requireNonNull(registryAccess.get(), "Minecraft registry access");
        return switch (registryKey) {
            case BIOME_REGISTRY -> canonicalize(access, Biome.DIRECT_CODEC, sourceJson);
            case DIMENSION_TYPE_REGISTRY -> canonicalize(access, DimensionType.DIRECT_CODEC, sourceJson);
            default -> throw new IllegalArgumentException(
                    "Registry does not accept generated JSON definitions: " + registryKey
            );
        };
    }

    @Override
    public Definition generatedDefinition(String registryKey, String resourceKey) {
        RegistryAccess access = Objects.requireNonNull(registryAccess.get(), "Minecraft registry access");
        return switch (registryKey) {
            case BIOME_REGISTRY -> exactRegistered(
                    access,
                    Registries.BIOME,
                    Biome.DIRECT_CODEC,
                    resourceKey
            );
            case DIMENSION_TYPE_REGISTRY -> exactRegistered(
                    access,
                    Registries.DIMENSION_TYPE,
                    DimensionType.DIRECT_CODEC,
                    resourceKey
            );
            default -> throw new IllegalArgumentException(
                    "Registry does not contain Iris-generated definitions: " + registryKey
            );
        };
    }

    private static <T> Definition presence(
            RegistryAccess access,
            ResourceKey<? extends Registry<T>> registryKey,
            String registryResourceKey,
            String resourceKey
    ) {
        Registry<T> registry = access.lookupOrThrow(registryKey);
        Identifier identifier = requireIdentifier(resourceKey);
        T value = registry.getValue(identifier);
        if (value == null) {
            throw new IllegalStateException("Missing registry definition " + registryKey.identifier()
                    + " / " + resourceKey + ".");
        }
        return Definition.resourceIdentity(registryResourceKey, resourceKey);
    }

    private static <T> Definition canonicalize(RegistryAccess access, Codec<T> codec, String sourceJson) {
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, access);
        JsonElement source = JsonParser.parseString(Objects.requireNonNull(sourceJson, "sourceJson"));
        T decoded = codec.parse(ops, source).getOrThrow(IllegalArgumentException::new);
        JsonElement encoded = codec.encodeStart(ops, decoded).getOrThrow(IllegalStateException::new);
        return Definition.exactJson(encoded.toString());
    }

    private static <T> Definition exactRegistered(
            RegistryAccess access,
            ResourceKey<? extends Registry<T>> registryKey,
            Codec<T> codec,
            String resourceKey
    ) {
        Registry<T> registry = access.lookupOrThrow(registryKey);
        Identifier identifier = requireIdentifier(resourceKey);
        T value = registry.getValue(identifier);
        if (value == null) {
            throw new IllegalStateException("Missing generated registry definition "
                    + registryKey.identifier() + " / " + resourceKey + ".");
        }
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, access);
        JsonElement encoded = codec.encodeStart(ops, value).getOrThrow(IllegalStateException::new);
        return Definition.exactJson(encoded.toString());
    }

    private static Identifier requireIdentifier(String resourceKey) {
        Identifier identifier = Identifier.tryParse(resourceKey);
        if (identifier == null) {
            throw new IllegalArgumentException("Invalid registry resource key: " + resourceKey);
        }
        return identifier;
    }

    private static String requireText(String value, String label) {
        String required = Objects.requireNonNull(value, label).trim();
        if (required.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be blank.");
        }
        return required;
    }
}
