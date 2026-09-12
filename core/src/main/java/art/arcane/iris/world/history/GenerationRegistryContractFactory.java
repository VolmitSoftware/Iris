package art.arcane.iris.world.history;

import art.arcane.iris.pack.validation.ContentGate;
import art.arcane.iris.pack.validation.KeyStatus;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.iris.platform.bukkit.nms.datapack.DataVersion;
import art.arcane.iris.platform.bukkit.nms.datapack.IDataFixer;
import art.arcane.iris.pack.PackDirectoryResolver;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.biome.IrisBiomeCustom;
import art.arcane.iris.generation.biome.IrisBiomeCustomCategory;
import art.arcane.iris.generation.biome.IrisBiomeCustomParticle;
import art.arcane.iris.generation.biome.IrisBiomeCustomPrecipType;
import art.arcane.iris.generation.biome.IrisBiomeCustomSpawn;
import art.arcane.iris.generation.biome.IrisBiomeCustomSpawnType;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisDimensionType;
import art.arcane.iris.generation.terrain.IrisDimensionTypeOptions;
import art.arcane.iris.structure.nativegen.IrisNativeStructure;
import art.arcane.iris.structure.object.IrisObjectIO;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.structure.placement.IrisStructurePlacement;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformGenerationRegistry;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.volmlib.util.collection.KList;
import com.google.gson.JsonArray;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class GenerationRegistryContractFactory {
    public static final String BIOME_REGISTRY = "minecraft:worldgen/biome";
    public static final String STRUCTURE_REGISTRY = "minecraft:worldgen/structure";
    public static final String DIMENSION_TYPE_REGISTRY = "minecraft:dimension_type";
    public static final String BLOCK_REGISTRY = "minecraft:block";
    public static final String ENTITY_TYPE_REGISTRY = "minecraft:entity_type";

    private static final int DEFINITION_FINGERPRINT_SCHEMA = 1;
    private static final String DEFINITION_FINGERPRINT_DOMAIN = "iris-generation-registry-definition";
    private static final String GENERATED_SEMANTIC_FINGERPRINT_DOMAIN =
            "iris-generation-registry-semantic-definition";
    private static final int CUSTOM_BIOME_DEFINITION_FINGERPRINT_SCHEMA = 1;
    private static final String CUSTOM_BIOME_DEFINITION_FINGERPRINT_DOMAIN =
            "iris-custom-biome-definition";
    private static final int CUSTOM_BIOME_AUTHORED_DEFINITION_FINGERPRINT_SCHEMA = 1;
    private static final String CUSTOM_BIOME_AUTHORED_DEFINITION_FINGERPRINT_DOMAIN =
            "iris-custom-biome-authored-definition";
    private static final int CUSTOM_BIOME_IDENTITY_FINGERPRINT_SCHEMA = 1;
    private static final String CUSTOM_BIOME_IDENTITY_FINGERPRINT_DOMAIN =
            "iris-custom-biome-physical-identity";
    public static final String CUSTOM_BIOME_EFFECTIVE_SOURCE_SCHEMA =
            "iris-custom-biome-effective-v1";
    public static final String DIMENSION_TYPE_EFFECTIVE_SOURCE_SCHEMA =
            "iris-dimension-type-effective-v1";
    private static final Pattern SHA_256_PATTERN = Pattern.compile("[0-9a-f]{64}");

    private GenerationRegistryContractFactory() {
    }

    public static GenerationRegistryContract create(
            IrisData data,
            IrisDimension dimension,
            String packFingerprint
    ) throws IOException {
        return create(data, dimension, packFingerprint, CustomBiomeAliasPolicy.CONTENT_ADDRESSED_ONLY);
    }

    public static GenerationRegistryContract create(
            IrisData data,
            IrisDimension dimension,
            String packFingerprint,
            CustomBiomeAliasPolicy aliasPolicy
    ) throws IOException {
        PlatformRegistries registries = IrisPlatforms.get().registries();
        return create(
                data,
                dimension,
                packFingerprint,
                DataVersion.getLatest().get(),
                registries,
                registries.generationRegistry(),
                aliasPolicy
        );
    }

    public static GenerationRegistryContract create(
            IrisData data,
            IrisDimension dimension,
            String packFingerprint,
            IDataFixer fixer,
            PlatformRegistries registries,
            PlatformGenerationRegistry generationRegistry
    ) throws IOException {
        return create(
                data,
                dimension,
                packFingerprint,
                fixer,
                registries,
                generationRegistry,
                CustomBiomeAliasPolicy.CONTENT_ADDRESSED_ONLY
        );
    }

    public static GenerationRegistryContract create(
            IrisData data,
            IrisDimension dimension,
            String packFingerprint,
            IDataFixer fixer,
            PlatformRegistries registries,
            PlatformGenerationRegistry generationRegistry,
            CustomBiomeAliasPolicy aliasPolicy
    ) throws IOException {
        IrisData requiredData = Objects.requireNonNull(data, "data");
        IrisDimension requiredDimension = Objects.requireNonNull(dimension, "dimension");
        String requiredPackFingerprint = requireSha256(packFingerprint, "Pack fingerprint");
        IDataFixer requiredFixer = Objects.requireNonNull(fixer, "fixer");
        PlatformRegistries requiredRegistries = Objects.requireNonNull(registries, "registries");
        PlatformGenerationRegistry requiredGenerationRegistry = Objects.requireNonNull(
                generationRegistry,
                "generationRegistry"
        );
        CustomBiomeAliasPolicy requiredAliasPolicy = Objects.requireNonNull(aliasPolicy, "aliasPolicy");
        requireRuntimeIdentity(requiredGenerationRegistry);
        Path packRoot = requiredData.getDataFolder().toPath().toAbsolutePath().normalize();
        String actualPackFingerprint = GenerationPackFingerprint.compute(
                packRoot,
                GenerationPackFingerprint.CURRENT_VERSION
        );
        if (!requiredPackFingerprint.equals(actualPackFingerprint)) {
            throw new IOException("Iris generation registry capture rejected a changed pack.");
        }

        Map<GenerationRegistryContract.PhysicalResourceKey, PlatformGenerationRegistry.Definition> definitions =
                new LinkedHashMap<>();
        Map<GenerationRegistryContract.PhysicalResourceKey, GenerationRegistryContract.GeneratedSource>
                generatedSources = new LinkedHashMap<>();
        Map<String, Set<String>> biomeTags = new TreeMap<>();
        String packName = requiredData.getDataFolder().getName();
        List<IrisBiome> reachableBiomes = new ArrayList<>(requiredDimension.getReachableBiomes(() -> requiredData));
        for (IrisDimension registryDimension : registryDimensions(requiredData, requiredDimension)) {
            String dimensionKey = requireText(registryDimension.getLoadKey(), "Dimension load key");
            addGeneratedDimensionType(
                    definitions,
                    generatedSources,
                    registryDimension,
                    packName,
                    dimensionKey,
                    requiredFixer,
                    requiredGenerationRegistry
            );
            List<IrisBiome> referencedBiomes = registryDimension == requiredDimension ? reachableBiomes : List.of();
            List<IrisBiome> registryBiomes = new ArrayList<>(registryDimension.getAllBiomes(() -> requiredData));
            registryBiomes.addAll(referencedBiomes);
            addBiomeDefinitions(
                    definitions,
                    generatedSources,
                    registryBiomes,
                    referencedBiomes,
                    registryDimension,
                    packName,
                    dimensionKey,
                    requiredData,
                    requiredFixer,
                    requiredGenerationRegistry,
                    requiredAliasPolicy,
                    biomeTags
            );
        }
        addNativeStructures(
                definitions,
                requiredDimension,
                reachableBiomes,
                requiredData,
                requiredGenerationRegistry
        );
        ReferencedPlatformKeys referenced = referencedPlatformKeys(packRoot, requiredRegistries);
        addPlatformDefinitions(definitions, BLOCK_REGISTRY, referenced.blockKeys(), requiredGenerationRegistry);
        addPlatformDefinitions(definitions, ENTITY_TYPE_REGISTRY, referenced.entityKeys(), requiredGenerationRegistry);
        return contract(definitions, generatedSources, requiredGenerationRegistry).withBiomeTags(biomeTags);
    }

    public static CustomBiomeDefinition customBiomeDefinition(
            IrisData data,
            IrisDimension dimension,
            IrisBiomeCustom customBiome
    ) {
        PlatformGenerationRegistry registry = IrisPlatforms.get().registries().generationRegistry();
        return customBiomeDefinition(
                data,
                dimension,
                customBiome,
                DataVersion.getLatest().get(),
                registry
        );
    }

    public static CustomBiomeDefinition customBiomeDefinition(
            IrisData data,
            IrisDimension dimension,
            IrisBiomeCustom customBiome,
            IDataFixer fixer,
            PlatformGenerationRegistry registry
    ) {
        IrisData requiredData = Objects.requireNonNull(data, "data");
        IrisDimension requiredDimension = Objects.requireNonNull(dimension, "dimension");
        IrisBiomeCustom requiredBiome = Objects.requireNonNull(customBiome, "customBiome");
        IDataFixer requiredFixer = Objects.requireNonNull(fixer, "fixer");
        PlatformGenerationRegistry requiredRegistry = Objects.requireNonNull(registry, "registry");
        String sourceJson = requiredBiome.generateJson(requiredFixer, requiredData.getContentGate());
        String semanticJson = customBiomeEffectiveSemanticJson(
                requiredBiome,
                requiredData.getContentGate()
        );
        String authoredDefinitionFingerprint = fingerprintCustomBiomeAuthoredDefinition(semanticJson);
        String identityFingerprint = fingerprintCustomBiomeIdentity(
                authoredDefinitionFingerprint,
                requiredDimension.getLoadKey(),
                requiredBiome.getId()
        );
        String resourceKey = requiredRegistry.customBiomeResourceKey(identityFingerprint);
        GenerationRegistryContract.PhysicalResourceKey physicalKey = key(BIOME_REGISTRY, resourceKey);
        PlatformGenerationRegistry.Definition canonicalDefinition = requiredRegistry.canonicalDefinition(
                BIOME_REGISTRY,
                resourceKey,
                sourceJson
        );
        requireGeneratedDefinition(canonicalDefinition, "canonical custom biome definition");
        String canonicalSource = canonicalDefinition.value();
        return new CustomBiomeDefinition(
                physicalKey,
                identityFingerprint,
                authoredDefinitionFingerprint,
                semanticJson,
                fingerprintCustomBiomeDefinition(canonicalSource),
                canonicalSource,
                canonicalDefinition
        );
    }

    public static String customBiomeResourceKey(
            String dimensionKey,
            IrisBiomeCustom customBiome
    ) {
        return customBiomeResourceKey(dimensionKey, customBiome, (ContentGate) null);
    }

    public static String customBiomeResourceKey(
            String dimensionKey,
            IrisBiomeCustom customBiome,
            ContentGate contentGate
    ) {
        IrisBiomeCustom requiredBiome = Objects.requireNonNull(customBiome, "customBiome");
        return PlatformGenerationRegistry.contentAddressedCustomBiomeResourceKey(
                fingerprintCustomBiomeIdentity(
                        fingerprintCustomBiomeEffectiveDefinition(requiredBiome, contentGate),
                        dimensionKey,
                        requiredBiome.getId()
                )
        );
    }

    public static String customBiomeResourceKey(
            String dimensionKey,
            IrisBiomeCustom customBiome,
            PlatformGenerationRegistry registry
    ) {
        return customBiomeResourceKey(dimensionKey, customBiome, (ContentGate) null, registry);
    }

    public static String customBiomeResourceKey(
            String dimensionKey,
            IrisBiomeCustom customBiome,
            ContentGate contentGate,
            PlatformGenerationRegistry registry
    ) {
        IrisBiomeCustom requiredBiome = Objects.requireNonNull(customBiome, "customBiome");
        PlatformGenerationRegistry requiredRegistry = Objects.requireNonNull(registry, "registry");
        return requiredRegistry.customBiomeResourceKey(
                fingerprintCustomBiomeIdentity(
                        fingerprintCustomBiomeEffectiveDefinition(requiredBiome, contentGate),
                        dimensionKey,
                        requiredBiome.getId()
                )
        );
    }

    public static String fingerprintCustomBiomeIdentity(
            String authoredDefinitionFingerprint,
            String dimensionKey,
            String customBiomeId
    ) {
        String requiredDefinitionFingerprint = requireSha256(
                authoredDefinitionFingerprint,
                "Custom biome authored definition fingerprint"
        );
        String requiredDimensionKey = requireText(dimensionKey, "Dimension load key").toLowerCase(Locale.ROOT);
        String requiredCustomBiomeId = requireText(customBiomeId, "Custom biome ID").toLowerCase(Locale.ROOT);
        MessageDigest digest = sha256();
        updateString(digest, CUSTOM_BIOME_IDENTITY_FINGERPRINT_DOMAIN);
        updateInt(digest, CUSTOM_BIOME_IDENTITY_FINGERPRINT_SCHEMA);
        updateString(digest, requiredDefinitionFingerprint);
        updateString(digest, requiredDimensionKey);
        updateString(digest, requiredCustomBiomeId);
        return HexFormat.of().formatHex(digest.digest());
    }

    public static String requireCustomBiomeResourceKey(
            GenerationRegistryContract contract,
            String dimensionKey,
            String customBiomeId
    ) throws IOException {
        return requireCustomBiomeResourceKey(
                contract,
                dimensionKey,
                customBiomeId,
                IrisPlatforms.get().registries().generationRegistry()
        );
    }

    public static String requireCustomBiomeResourceKey(
            GenerationRegistryContract contract,
            String dimensionKey,
            String customBiomeId,
            PlatformGenerationRegistry generationRegistry
    ) throws IOException {
        GenerationRegistryContract requiredContract = Objects.requireNonNull(contract, "contract");
        PlatformGenerationRegistry registry = Objects.requireNonNull(
                generationRegistry,
                "generationRegistry"
        );
        String selectedKey = null;
        for (Map.Entry<GenerationRegistryContract.PhysicalResourceKey, GenerationRegistryContract.GeneratedSource>
                entry : requiredContract.generatedSources().entrySet()) {
            GenerationRegistryContract.GeneratedSource source = entry.getValue();
            if (!entry.getKey().registryKey().equals(BIOME_REGISTRY)
                    || !source.sourceSchema().equals(CUSTOM_BIOME_EFFECTIVE_SOURCE_SCHEMA)) {
                continue;
            }
            String identity = fingerprintCustomBiomeIdentity(
                    source.semanticSha256(),
                    dimensionKey,
                    customBiomeId
            );
            String expectedResourceKey = registry.customBiomeResourceKey(identity);
            if (!entry.getKey().resourceKey().equals(expectedResourceKey)) {
                continue;
            }
            String expectedDefinition = requiredContract.definitions().get(entry.getKey());
            requireGeneratedSemanticFingerprint(entry.getKey(), source, expectedDefinition);
            if (selectedKey == null || expectedResourceKey.compareTo(selectedKey) < 0) {
                selectedKey = expectedResourceKey;
            }
        }
        if (selectedKey != null) {
            return selectedKey;
        }
        throw new IOException("Historical generation registry contract has no custom biome mapping for "
                + requireText(dimensionKey, "Dimension load key") + ":"
                + requireText(customBiomeId, "Custom biome ID") + ".");
    }

    public static String fingerprintCustomBiomeAuthoredDefinition(IrisBiomeCustom customBiome) {
        return fingerprintCustomBiomeEffectiveDefinition(customBiome, null);
    }

    public static String customBiomeAuthoredDefinitionJson(IrisBiomeCustom customBiome) {
        return customBiomeEffectiveSemanticJson(customBiome, null);
    }

    public static String fingerprintCustomBiomeEffectiveDefinition(
            IrisBiomeCustom customBiome,
            ContentGate contentGate
    ) {
        return fingerprintCustomBiomeAuthoredDefinition(
                customBiomeEffectiveSemanticJson(customBiome, contentGate)
        );
    }

    public static String customBiomeEffectiveSemanticJson(
            IrisBiomeCustom customBiome,
            ContentGate contentGate
    ) {
        return customBiomeSemanticJson(
                customBiome,
                entityKey -> contentGate == null || contentGate.entity(entityKey) != KeyStatus.MISSING
        );
    }

    private static String customBiomeSemanticJson(
            IrisBiomeCustom customBiome,
            Predicate<String> includeEntity
    ) {
        IrisBiomeCustom requiredBiome = Objects.requireNonNull(customBiome, "customBiome");
        JsonObject authored = new JsonObject();
        authored.addProperty("temperature", requiredBiome.getTemperature());
        authored.addProperty("humidity", requiredBiome.getHumidity());
        authored.addProperty("downfallType", Objects.requireNonNull(
                requiredBiome.getDownfallType(),
                "Custom biome downfall type"
        ).name());
        authored.addProperty("category", Objects.requireNonNull(
                requiredBiome.getCategory(),
                "Custom biome category"
        ).name());
        authored.addProperty("spawnRarity", requiredBiome.getSpawnRarity());
        authored.addProperty("skyColor", customBiomeColor(requiredBiome.getSkyColor()));
        authored.addProperty("fogColor", customBiomeColor(requiredBiome.getFogColor()));
        authored.addProperty("waterColor", customBiomeColor(requiredBiome.getWaterColor()));
        authored.addProperty("waterFogColor", customBiomeColor(requiredBiome.getWaterFogColor()));
        if (requiredBiome.getGrassColor() != null && !requiredBiome.getGrassColor().isEmpty()) {
            authored.addProperty("grassColor", customBiomeColor(requiredBiome.getGrassColor()));
        }
        if (requiredBiome.getFoliageColor() != null && !requiredBiome.getFoliageColor().isEmpty()) {
            authored.addProperty("foliageColor", customBiomeColor(requiredBiome.getFoliageColor()));
        }
        IrisBiomeCustomParticle particle = requiredBiome.getAmbientParticle();
        if (particle != null && particle.getParticleKey() != null) {
            JsonObject authoredParticle = new JsonObject();
            authoredParticle.addProperty("type", particle.getParticleKey());
            authoredParticle.addProperty("rarity", particle.getRarity());
            authored.add("ambientParticle", authoredParticle);
        }
        Map<String, JsonArray> spawnGroups = new TreeMap<>();
        List<IrisBiomeCustomSpawn> spawns = requiredBiome.getSpawns();
        if (spawns != null) {
            for (IrisBiomeCustomSpawn spawn : spawns) {
                if (spawn == null || spawn.getTypeKey() == null) {
                    continue;
                }
                if (!includeEntity.test(spawn.getTypeKey())) {
                    continue;
                }
                IrisBiomeCustomSpawnType group = spawn.getGroup() == null
                        ? IrisBiomeCustomSpawnType.MISC
                        : spawn.getGroup();
                JsonObject authoredSpawn = new JsonObject();
                authoredSpawn.addProperty("type", spawn.getTypeKey());
                authoredSpawn.addProperty("weight", spawn.getWeight());
                authoredSpawn.addProperty("minCount", spawn.getMinCount());
                authoredSpawn.addProperty("maxCount", spawn.getMaxCount());
                spawnGroups.computeIfAbsent(
                        group.name().toLowerCase(Locale.ROOT),
                        ignored -> new JsonArray()
                ).add(authoredSpawn);
            }
        }
        JsonObject authoredSpawns = new JsonObject();
        for (Map.Entry<String, JsonArray> entry : spawnGroups.entrySet()) {
            authoredSpawns.add(entry.getKey(), entry.getValue());
        }
        authored.add("spawners", authoredSpawns);
        return authored.toString();
    }

    public static String fingerprintCustomBiomeAuthoredDefinition(String semanticJson) {
        MessageDigest digest = sha256();
        updateString(digest, CUSTOM_BIOME_AUTHORED_DEFINITION_FINGERPRINT_DOMAIN);
        updateInt(digest, CUSTOM_BIOME_AUTHORED_DEFINITION_FINGERPRINT_SCHEMA);
        updateJson(digest, parseJson(Objects.requireNonNull(semanticJson, "semanticJson")));
        return HexFormat.of().formatHex(digest.digest());
    }

    public static String fingerprintCustomBiomeDefinition(String sourceJson) {
        MessageDigest digest = sha256();
        updateString(digest, CUSTOM_BIOME_DEFINITION_FINGERPRINT_DOMAIN);
        updateInt(digest, CUSTOM_BIOME_DEFINITION_FINGERPRINT_SCHEMA);
        updateJson(digest, parseJson(Objects.requireNonNull(sourceJson, "sourceJson")));
        return HexFormat.of().formatHex(digest.digest());
    }

    public static GenerationRegistryContract captureRequiredDefinitions(
            Collection<GenerationRegistryContract> requiredContracts
    ) throws IOException {
        return captureRequiredDefinitions(
                requiredContracts,
                DataVersion.getLatest().get(),
                IrisPlatforms.get().registries().generationRegistry()
        );
    }

    public static GenerationRegistryContract captureRequiredDefinitions(
            Collection<GenerationRegistryContract> requiredContracts,
            PlatformGenerationRegistry generationRegistry
    ) throws IOException {
        return captureRequiredDefinitions(
                requiredContracts,
                DataVersion.getLatest().get(),
                generationRegistry
        );
    }

    public static GenerationRegistryContract captureRequiredDefinitions(
            Collection<GenerationRegistryContract> requiredContracts,
            IDataFixer fixer,
            PlatformGenerationRegistry generationRegistry
    ) throws IOException {
        Collection<GenerationRegistryContract> required = Objects.requireNonNull(
                requiredContracts,
                "requiredContracts"
        );
        IDataFixer requiredFixer = Objects.requireNonNull(fixer, "fixer");
        PlatformGenerationRegistry registry = Objects.requireNonNull(generationRegistry, "generationRegistry");
        requireRuntimeIdentity(registry);
        TreeMap<GenerationRegistryContract.PhysicalResourceKey, String> expected = new TreeMap<>();
        TreeMap<GenerationRegistryContract.PhysicalResourceKey, GenerationRegistryContract.GeneratedSource>
                generatedSources = new TreeMap<>();
        for (GenerationRegistryContract contract : required) {
            GenerationRegistryContract requiredContract = Objects.requireNonNull(contract, "required registry contract");
            for (Map.Entry<GenerationRegistryContract.PhysicalResourceKey, String> entry
                    : requiredContract.definitions().entrySet()) {
                String previous = expected.putIfAbsent(entry.getKey(), entry.getValue());
                if (previous != null && !previous.equals(entry.getValue())) {
                    throw new IOException("Retained generation epochs require conflicting definitions for "
                            + entry.getKey().registryKey() + " / " + entry.getKey().resourceKey() + ".");
                }
            }
            for (Map.Entry<GenerationRegistryContract.PhysicalResourceKey, GenerationRegistryContract.GeneratedSource>
                    entry
                    : requiredContract.generatedSources().entrySet()) {
                GenerationRegistryContract.GeneratedSource previous = generatedSources.get(entry.getKey());
                if (previous != null && !sameGeneratedSemantic(previous, entry.getValue())) {
                    throw new IOException("Retained generation epochs require conflicting generated sources for "
                            + entry.getKey().registryKey() + " / " + entry.getKey().resourceKey() + ".");
                }
                generatedSources.put(
                        entry.getKey(),
                        preferredGeneratedSource(previous, entry.getValue(), registry)
                );
            }
        }
        Map<GenerationRegistryContract.PhysicalResourceKey, String> captured = new TreeMap<>();
        for (Map.Entry<GenerationRegistryContract.PhysicalResourceKey, String> entry : expected.entrySet()) {
            GenerationRegistryContract.PhysicalResourceKey key = entry.getKey();
            GenerationRegistryContract.GeneratedSource generatedSource = generatedSources.get(key);
            PlatformGenerationRegistry.Definition definition;
            if (generatedSource == null) {
                definition = requireDefinition(registry, key);
                String fingerprint = fingerprintDefinition(key, definition, registry.runtimeIdentity());
                if (!entry.getValue().equals(fingerprint)) {
                    throw new IOException("Historical registry definition changed for "
                            + key.registryKey() + " / " + key.resourceKey() + ".");
                }
            } else {
                requireGeneratedSemanticFingerprint(key, generatedSource, entry.getValue());
                PlatformGenerationRegistry.Definition expectedDefinition = canonicalGeneratedDefinition(
                        key,
                        generatedSource,
                        requiredFixer,
                        registry
                );
                definition = requireGeneratedDefinition(registry, key);
                String expectedRenderedFingerprint = fingerprintDefinition(key, expectedDefinition, "generated");
                String actualRenderedFingerprint = fingerprintDefinition(key, definition, "generated");
                if (!expectedRenderedFingerprint.equals(actualRenderedFingerprint)) {
                    throw new IOException("Historical generated registry definition changed for "
                            + key.registryKey() + " / " + key.resourceKey() + ".");
                }
            }
            captured.put(key, entry.getValue());
        }
        return GenerationRegistryContract.fromDefinitions(captured);
    }

    public static String requireGeneratedSource(
            GenerationRegistryContract contract,
            GenerationRegistryContract.PhysicalResourceKey key
    ) throws IOException {
        return requireGeneratedSource(
                contract,
                key,
                DataVersion.getLatest().get(),
                IrisPlatforms.get().registries().generationRegistry()
        );
    }

    public static String renderGeneratedSource(
            GenerationRegistryContract contract,
            GenerationRegistryContract.PhysicalResourceKey key,
            IDataFixer fixer
    ) throws IOException {
        GenerationRegistryContract requiredContract = Objects.requireNonNull(contract, "contract");
        GenerationRegistryContract.PhysicalResourceKey requiredKey = Objects.requireNonNull(key, "key");
        IDataFixer requiredFixer = Objects.requireNonNull(fixer, "fixer");
        GenerationRegistryContract.GeneratedSource source = requiredContract.generatedSources().get(requiredKey);
        if (source == null) {
            throw new IOException("Historical generated registry source is missing for "
                    + requiredKey.registryKey() + " / " + requiredKey.resourceKey() + ".");
        }
        requireGeneratedSemanticFingerprint(requiredKey, source, requiredContract.definitions().get(requiredKey));
        String sourceFingerprint = fingerprintDefinition(requiredKey,
                PlatformGenerationRegistry.Definition.exactJson(source.sourceJson()), "generated");
        if (!source.renderedDefinitionSha256().equals(sourceFingerprint)) {
            throw new IOException("Historical generated registry source changed for "
                    + requiredKey.registryKey() + " / " + requiredKey.resourceKey() + ".");
        }
        return renderGeneratedSemantic(requiredKey, source, requiredFixer);
    }

    public static String requireGeneratedSource(
            GenerationRegistryContract contract,
            GenerationRegistryContract.PhysicalResourceKey key,
            IDataFixer fixer
    ) throws IOException {
        return requireGeneratedSource(
                contract,
                key,
                fixer,
                IrisPlatforms.get().registries().generationRegistry()
        );
    }

    public static String requireGeneratedSource(
            GenerationRegistryContract contract,
            GenerationRegistryContract.PhysicalResourceKey key,
            PlatformGenerationRegistry generationRegistry
    ) throws IOException {
        return requireGeneratedSource(
                contract,
                key,
                DataVersion.getLatest().get(),
                generationRegistry
        );
    }

    public static String requireGeneratedSource(
            GenerationRegistryContract contract,
            GenerationRegistryContract.PhysicalResourceKey key,
            IDataFixer fixer,
            PlatformGenerationRegistry generationRegistry
    ) throws IOException {
        GenerationRegistryContract requiredContract = Objects.requireNonNull(contract, "contract");
        GenerationRegistryContract.PhysicalResourceKey requiredKey = Objects.requireNonNull(key, "key");
        IDataFixer requiredFixer = Objects.requireNonNull(fixer, "fixer");
        PlatformGenerationRegistry registry = Objects.requireNonNull(
                generationRegistry,
                "generationRegistry"
        );
        GenerationRegistryContract.GeneratedSource source = requiredContract.generatedSources().get(requiredKey);
        if (source == null) {
            throw new IOException("Historical generated registry source is missing for "
                    + requiredKey.registryKey() + " / " + requiredKey.resourceKey() + ".");
        }
        String expectedFingerprint = requiredContract.definitions().get(requiredKey);
        if (expectedFingerprint == null) {
            throw new IOException("Historical generated registry definition is missing for "
                    + requiredKey.registryKey() + " / " + requiredKey.resourceKey() + ".");
        }
        requireGeneratedSemanticFingerprint(requiredKey, source, expectedFingerprint);
        return canonicalGeneratedDefinition(requiredKey, source, requiredFixer, registry).value();
    }

    public static String fingerprintDefinition(
            GenerationRegistryContract.PhysicalResourceKey key,
            PlatformGenerationRegistry.Definition definition,
            String runtimeIdentity
    ) {
        GenerationRegistryContract.PhysicalResourceKey requiredKey = Objects.requireNonNull(key, "key");
        PlatformGenerationRegistry.Definition requiredDefinition = Objects.requireNonNull(
                definition,
                "definition"
        );
        Objects.requireNonNull(runtimeIdentity, "runtimeIdentity");
        MessageDigest digest = sha256();
        updateString(digest, DEFINITION_FINGERPRINT_DOMAIN);
        updateInt(digest, DEFINITION_FINGERPRINT_SCHEMA);
        updateString(digest, requiredKey.registryKey());
        updateString(digest, requiredKey.resourceKey());
        updateString(digest, requiredDefinition.encoding().name());
        switch (requiredDefinition.encoding()) {
            case JSON -> updateJson(digest, parseJson(requiredDefinition.value()));
            case CONSERVATIVE_IDENTITY -> {
                updateString(digest, requiredDefinition.value());
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public static String fingerprintGeneratedSemantic(
            GenerationRegistryContract.PhysicalResourceKey key,
            GenerationRegistryContract.GeneratedSource source
    ) {
        GenerationRegistryContract.PhysicalResourceKey requiredKey = Objects.requireNonNull(key, "key");
        GenerationRegistryContract.GeneratedSource requiredSource = Objects.requireNonNull(source, "source");
        MessageDigest digest = sha256();
        updateString(digest, GENERATED_SEMANTIC_FINGERPRINT_DOMAIN);
        updateInt(digest, DEFINITION_FINGERPRINT_SCHEMA);
        updateString(digest, requiredKey.registryKey());
        updateString(digest, requiredKey.resourceKey());
        updateString(digest, requiredSource.sourceSchema());
        updateString(digest, requiredSource.semanticSha256());
        updateJson(digest, parseJson(requiredSource.semanticJson()));
        return HexFormat.of().formatHex(digest.digest());
    }

    private static List<IrisDimension> registryDimensions(IrisData data, IrisDimension owner) throws IOException {
        ResourceLoader<IrisDimension> loader = data.getDimensionLoader();
        String[] possibleKeys = loader.getPossibleKeys();
        if (possibleKeys == null || possibleKeys.length == 0) {
            throw new IOException("Iris pack has no dimension definitions: " + data.getDataFolder());
        }
        String ownerKey = requireText(owner.getLoadKey(), "Dimension load key");
        List<IrisDimension> dimensions = new ArrayList<>(possibleKeys.length);
        boolean foundOwner = false;
        for (String possibleKey : possibleKeys) {
            IrisDimension dimension;
            if (ownerKey.equals(possibleKey)) {
                dimension = owner;
                foundOwner = true;
            } else {
                dimension = loader.load(possibleKey);
            }
            if (dimension == null) {
                throw new IOException("Unable to load Iris dimension '" + possibleKey + "' from " + data.getDataFolder());
            }
            dimensions.add(dimension);
        }
        if (!foundOwner) {
            throw new IOException("Iris pack does not contain its generation dimension '" + ownerKey + "'.");
        }
        return dimensions;
    }

    private static void addGeneratedDimensionType(
            Map<GenerationRegistryContract.PhysicalResourceKey, PlatformGenerationRegistry.Definition> definitions,
            Map<GenerationRegistryContract.PhysicalResourceKey, GenerationRegistryContract.GeneratedSource>
                    generatedSources,
            IrisDimension dimension,
            String packName,
            String dimensionKey,
            IDataFixer fixer,
            PlatformGenerationRegistry registry
    ) throws IOException {
        String resourceKey = registry.dimensionTypeResourceKey(
                packName,
                dimensionKey,
                dimension.getDimensionTypeKey()
        );
        PlatformGenerationRegistry.Definition canonicalDefinition = registry.canonicalDefinition(
                DIMENSION_TYPE_REGISTRY,
                resourceKey,
                dimension.getDimensionType().toJson(fixer)
        );
        requireGeneratedDefinition(canonicalDefinition, "canonical dimension type definition");
        putGeneratedDefinition(
                definitions,
                generatedSources,
                key(DIMENSION_TYPE_REGISTRY, resourceKey),
                DIMENSION_TYPE_EFFECTIVE_SOURCE_SCHEMA,
                GenerationEpochContractFactory.fingerprintDimensionType(dimension.getDimensionType()),
                GenerationEpochContractFactory.dimensionTypeSemanticJson(dimension.getDimensionType()),
                registry.generatedDefinitionRendererIdentity(),
                canonicalDefinition
        );
    }

    private static void addBiomeDefinitions(
            Map<GenerationRegistryContract.PhysicalResourceKey, PlatformGenerationRegistry.Definition> definitions,
            Map<GenerationRegistryContract.PhysicalResourceKey, GenerationRegistryContract.GeneratedSource>
                    generatedSources,
            List<IrisBiome> biomes,
            List<IrisBiome> referencedBiomes,
            IrisDimension dimension,
            String packName,
            String dimensionKey,
            IrisData data,
            IDataFixer fixer,
            PlatformGenerationRegistry registry,
            CustomBiomeAliasPolicy aliasPolicy,
            Map<String, Set<String>> biomeTags
    ) throws IOException {
        TreeSet<String> platformBiomeKeys = new TreeSet<>();
        Map<String, String> customResourceKeys = new LinkedHashMap<>();
        Map<String, CustomBiomeDefinition> legacyDefinitions = new TreeMap<>();
        for (IrisBiome biome : biomes) {
            if (biome == null) {
                continue;
            }
            List<IrisBiomeCustom> customBiomes = biome.getCustomDerivitives();
            if (customBiomes == null) {
                continue;
            }
            for (IrisBiomeCustom customBiome : customBiomes) {
                if (customBiome == null) {
                    continue;
                }
                String customBiomeId = requireText(customBiome.getId(), "Custom biome ID");
                CustomBiomeDefinition generated = customBiomeDefinition(
                        data,
                        dimension,
                        customBiome,
                        fixer,
                        registry
                );
                putGeneratedDefinition(
                        definitions,
                        generatedSources,
                        generated.physicalKey(),
                        CUSTOM_BIOME_EFFECTIVE_SOURCE_SCHEMA,
                        generated.semanticFingerprint(),
                        generated.semanticJson(),
                        registry.generatedDefinitionRendererIdentity(),
                        generated.definition()
                );
                Set<String> tags = biomeTags.computeIfAbsent(generated.physicalKey().resourceKey(), ignored -> new TreeSet<>());
                for (String rawTag : customBiome.getEffectiveTags(biome.getVanillaDerivativeKey())) {
                    if (rawTag == null || rawTag.isBlank()) {
                        continue;
                    }
                    String tag = rawTag.trim().toLowerCase(Locale.ROOT);
                    if (tag.indexOf(':') < 0) {
                        tag = "minecraft:" + tag;
                    }
                    if (tag.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) {
                        tags.add(tag);
                    }
                }
                String logicalKey = dimensionKey.toLowerCase(Locale.ROOT)
                        + ":" + customBiomeId.toLowerCase(Locale.ROOT);
                customResourceKeys.merge(
                        logicalKey,
                        generated.physicalKey().resourceKey(),
                        (first, second) -> first.compareTo(second) <= 0 ? first : second
                );
                if (aliasPolicy == CustomBiomeAliasPolicy.RETAIN_LEGACY_ALIASES) {
                    for (String alias : registry.legacyCustomBiomeResourceKeys(
                            packName,
                            dimensionKey,
                            customBiomeId
                    )) {
                        legacyDefinitions.merge(
                                alias,
                                generated,
                                (first, second) -> first.physicalKey().resourceKey()
                                        .compareTo(second.physicalKey().resourceKey()) <= 0 ? first : second
                        );
                    }
                }
            }
        }
        for (Map.Entry<String, CustomBiomeDefinition> entry : legacyDefinitions.entrySet()) {
            CustomBiomeDefinition generated = entry.getValue();
            biomeTags.put(entry.getKey(), new TreeSet<>(biomeTags.get(generated.physicalKey().resourceKey())));
            putGeneratedDefinition(
                    definitions,
                    generatedSources,
                    key(BIOME_REGISTRY, entry.getKey()),
                    CUSTOM_BIOME_EFFECTIVE_SOURCE_SCHEMA,
                    generated.semanticFingerprint(),
                    generated.semanticJson(),
                    registry.generatedDefinitionRendererIdentity(),
                    generated.definition()
            );
        }
        for (IrisBiome biome : referencedBiomes) {
            if (biome == null) {
                continue;
            }
            addMappedBiomeResourceKey(platformBiomeKeys, customResourceKeys, biome.getDerivativeKey());
            addMappedBiomeResourceKey(platformBiomeKeys, customResourceKeys, biome.getVanillaDerivativeKey());
            addMappedBiomeResourceKey(platformBiomeKeys, customResourceKeys, biome.getStructureDerivativeKey());
            addMappedBiomeResourceKeys(platformBiomeKeys, customResourceKeys, biome.getBiomeScatter());
            addMappedBiomeResourceKeys(platformBiomeKeys, customResourceKeys, biome.getBiomeSkyScatter());
        }
        addPlatformDefinitions(definitions, BIOME_REGISTRY, platformBiomeKeys, registry);
    }

    private static void addNativeStructures(
            Map<GenerationRegistryContract.PhysicalResourceKey, PlatformGenerationRegistry.Definition> definitions,
            IrisDimension dimension,
            List<IrisBiome> biomes,
            IrisData data,
            PlatformGenerationRegistry registry
    ) throws IOException {
        TreeSet<String> structureKeys = new TreeSet<>();
        collectNativeStructures(structureKeys, dimension.getStructures());
        for (IrisRegion region : dimension.getAllRegions(() -> data)) {
            if (region != null) {
                collectNativeStructures(structureKeys, region.getStructures());
            }
        }
        for (IrisBiome biome : biomes) {
            if (biome != null) {
                collectNativeStructures(structureKeys, biome.getStructures());
            }
        }
        addPlatformDefinitions(definitions, STRUCTURE_REGISTRY, structureKeys, registry);
    }

    private static void collectNativeStructures(
            Set<String> keys,
            List<IrisStructurePlacement> placements
    ) {
        if (placements == null) {
            return;
        }
        for (IrisStructurePlacement placement : placements) {
            if (placement == null || placement.getNativeStructures() == null) {
                continue;
            }
            for (IrisNativeStructure structure : placement.getNativeStructures()) {
                if (structure != null) {
                    addResourceKey(keys, structure.getStructure());
                }
            }
        }
    }

    private static void addPlatformDefinitions(
            Map<GenerationRegistryContract.PhysicalResourceKey, PlatformGenerationRegistry.Definition> definitions,
            String registryKey,
            Collection<String> resourceKeys,
            PlatformGenerationRegistry registry
    ) throws IOException {
        TreeSet<String> sorted = new TreeSet<>(resourceKeys);
        for (String resourceKey : sorted) {
            GenerationRegistryContract.PhysicalResourceKey physicalKey = key(registryKey, resourceKey);
            if (definitions.containsKey(physicalKey)) {
                continue;
            }
            putDefinition(definitions, physicalKey, requireDefinition(registry, physicalKey));
        }
    }

    private static void addMappedBiomeResourceKeys(
            Set<String> keys,
            Map<String, String> customResourceKeys,
            List<String> values
    ) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            addMappedBiomeResourceKey(keys, customResourceKeys, value);
        }
    }

    private static void addMappedBiomeResourceKey(
            Set<String> keys,
            Map<String, String> customResourceKeys,
            String value
    ) {
        if (value == null || value.isBlank()) {
            return;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.contains(":")) {
            normalized = "minecraft:" + normalized;
        }
        keys.add(customResourceKeys.getOrDefault(normalized, normalized));
    }

    private static GenerationRegistryContract contract(
            Map<GenerationRegistryContract.PhysicalResourceKey, PlatformGenerationRegistry.Definition> definitions,
            Map<GenerationRegistryContract.PhysicalResourceKey, GenerationRegistryContract.GeneratedSource>
                    generatedSources,
            PlatformGenerationRegistry registry
    ) {
        TreeMap<GenerationRegistryContract.PhysicalResourceKey, String> fingerprints = new TreeMap<>();
        for (Map.Entry<GenerationRegistryContract.PhysicalResourceKey, PlatformGenerationRegistry.Definition> entry
                : definitions.entrySet()) {
            GenerationRegistryContract.GeneratedSource generatedSource = generatedSources.get(entry.getKey());
            fingerprints.put(
                    entry.getKey(),
                    generatedSource == null
                            ? fingerprintDefinition(entry.getKey(), entry.getValue(), registry.runtimeIdentity())
                            : fingerprintGeneratedSemantic(entry.getKey(), generatedSource)
            );
        }
        return GenerationRegistryContract.fromDefinitionsAndGeneratedSources(
                fingerprints,
                generatedSources
        );
    }

    private static PlatformGenerationRegistry.Definition requireDefinition(
            PlatformGenerationRegistry registry,
            GenerationRegistryContract.PhysicalResourceKey key
    ) throws IOException {
        try {
            PlatformGenerationRegistry.Definition definition = registry.definition(
                    key.registryKey(),
                    key.resourceKey()
            );
            if (definition == null) {
                throw new IOException("Missing generation registry definition for "
                        + key.registryKey() + " / " + key.resourceKey() + ".");
            }
            return definition;
        } catch (IOException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IOException("Unable to capture generation registry definition for "
                    + key.registryKey() + " / " + key.resourceKey() + ".", exception);
        }
    }

    private static PlatformGenerationRegistry.Definition requireGeneratedDefinition(
            PlatformGenerationRegistry registry,
            GenerationRegistryContract.PhysicalResourceKey key
    ) throws IOException {
        try {
            PlatformGenerationRegistry.Definition definition = registry.generatedDefinition(
                    key.registryKey(),
                    key.resourceKey()
            );
            requireGeneratedDefinition(definition, "generated registry definition");
            return definition;
        } catch (RuntimeException exception) {
            throw new IOException("Unable to capture generated registry definition for "
                    + key.registryKey() + " / " + key.resourceKey() + ".", exception);
        }
    }

    private static void putDefinition(
            Map<GenerationRegistryContract.PhysicalResourceKey, PlatformGenerationRegistry.Definition> definitions,
            GenerationRegistryContract.PhysicalResourceKey key,
            PlatformGenerationRegistry.Definition definition
    ) throws IOException {
        PlatformGenerationRegistry.Definition previous = definitions.putIfAbsent(key, definition);
        if (previous == null || previous.equals(definition)) {
            return;
        }
        throw new IOException("Generation pack defines conflicting registry resources for "
                + key.registryKey() + " / " + key.resourceKey() + ".");
    }

    private static void putGeneratedDefinition(
            Map<GenerationRegistryContract.PhysicalResourceKey, PlatformGenerationRegistry.Definition> definitions,
            Map<GenerationRegistryContract.PhysicalResourceKey, GenerationRegistryContract.GeneratedSource>
                    generatedSources,
            GenerationRegistryContract.PhysicalResourceKey key,
            String sourceSchema,
            String semanticFingerprint,
            String semanticJson,
            String rendererIdentity,
            PlatformGenerationRegistry.Definition definition
    ) throws IOException {
        requireGeneratedDefinition(definition, "generated registry definition");
        putDefinition(definitions, key, definition);
        GenerationRegistryContract.GeneratedSource source = new GenerationRegistryContract.GeneratedSource(
                sourceSchema,
                semanticFingerprint,
                semanticJson,
                rendererIdentity,
                fingerprintDefinition(key, definition, "generated"),
                definition.value()
        );
        GenerationRegistryContract.GeneratedSource previousSource = generatedSources.putIfAbsent(key, source);
        if (previousSource != null && !previousSource.equals(source)) {
            throw new IOException("Generation pack defines conflicting generated registry sources for "
                    + key.registryKey() + " / " + key.resourceKey() + ".");
        }
    }

    private static GenerationRegistryContract.GeneratedSource preferredGeneratedSource(
            GenerationRegistryContract.GeneratedSource previous,
            GenerationRegistryContract.GeneratedSource candidate,
            PlatformGenerationRegistry registry
    ) {
        if (previous == null) {
            return candidate;
        }
        String currentRenderer = requireText(
                registry.generatedDefinitionRendererIdentity(),
                "Generated registry renderer identity"
        );
        boolean previousMatches = previous.rendererIdentity().equals(currentRenderer);
        boolean candidateMatches = candidate.rendererIdentity().equals(currentRenderer);
        if (previousMatches != candidateMatches) {
            return candidateMatches ? candidate : previous;
        }
        return generatedSourceOrder(candidate).compareTo(generatedSourceOrder(previous)) < 0
                ? candidate
                : previous;
    }

    private static String generatedSourceOrder(GenerationRegistryContract.GeneratedSource source) {
        return source.rendererIdentity() + '\u0000'
                + source.renderedDefinitionSha256() + '\u0000'
                + source.sourceJson();
    }

    private static boolean sameGeneratedSemantic(
            GenerationRegistryContract.GeneratedSource left,
            GenerationRegistryContract.GeneratedSource right
    ) {
        return left.sourceSchema().equals(right.sourceSchema())
                && left.semanticSha256().equals(right.semanticSha256())
                && parseJson(left.semanticJson()).equals(parseJson(right.semanticJson()));
    }

    private static PlatformGenerationRegistry.Definition canonicalGeneratedDefinition(
            GenerationRegistryContract.PhysicalResourceKey key,
            GenerationRegistryContract.GeneratedSource source,
            IDataFixer fixer,
            PlatformGenerationRegistry registry
    ) throws IOException {
        String currentRenderer = requireText(
                registry.generatedDefinitionRendererIdentity(),
                "Generated registry renderer identity"
        );
        String renderedSource;
        if (source.rendererIdentity().equals(currentRenderer)) {
            renderedSource = source.sourceJson();
        } else {
            renderedSource = renderGeneratedSemantic(key, source, fixer);
        }
        PlatformGenerationRegistry.Definition definition;
        try {
            definition = registry.canonicalDefinition(
                    key.registryKey(),
                    key.resourceKey(),
                    renderedSource
            );
            requireGeneratedDefinition(definition, "historical generated registry definition");
        } catch (RuntimeException failure) {
            throw new IOException("Historical generated registry source is incompatible for "
                    + key.registryKey() + " / " + key.resourceKey() + ".", failure);
        }
        if (source.rendererIdentity().equals(currentRenderer)) {
            String renderedFingerprint = fingerprintDefinition(key, definition, "generated");
            if (!source.renderedDefinitionSha256().equals(renderedFingerprint)) {
                throw new IOException("Historical generated registry source changed for "
                        + key.registryKey() + " / " + key.resourceKey() + ".");
            }
        }
        return definition;
    }

    private static String renderGeneratedSemantic(
            GenerationRegistryContract.PhysicalResourceKey key,
            GenerationRegistryContract.GeneratedSource source,
            IDataFixer fixer
    ) throws IOException {
        try {
            return switch (source.sourceSchema()) {
                case CUSTOM_BIOME_EFFECTIVE_SOURCE_SCHEMA -> customBiomeFromSemantic(
                        key,
                        source.semanticJson()
                ).generateJson(
                        fixer,
                        new ContentGate(null, Map.of(), null)
                );
                case DIMENSION_TYPE_EFFECTIVE_SOURCE_SCHEMA -> dimensionTypeFromSemantic(
                        source.semanticJson()
                ).toJson(fixer);
                default -> throw new IOException("Unsupported historical generated registry source schema '"
                        + source.sourceSchema() + "'.");
            };
        } catch (IOException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new IOException("Unable to render historical generated registry semantics for "
                    + key.registryKey() + " / " + key.resourceKey() + ".", failure);
        }
    }

    private static void requireGeneratedSemanticFingerprint(
            GenerationRegistryContract.PhysicalResourceKey key,
            GenerationRegistryContract.GeneratedSource source,
            String expectedDefinitionFingerprint
    ) throws IOException {
        String semanticFingerprint;
        try {
            semanticFingerprint = switch (source.sourceSchema()) {
                case CUSTOM_BIOME_EFFECTIVE_SOURCE_SCHEMA -> fingerprintCustomBiomeAuthoredDefinition(
                        source.semanticJson()
                );
                case DIMENSION_TYPE_EFFECTIVE_SOURCE_SCHEMA -> GenerationEpochContractFactory.fingerprintDimensionType(
                        dimensionTypeFromSemantic(source.semanticJson())
                );
                default -> throw new IOException("Unsupported historical generated registry source schema '"
                        + source.sourceSchema() + "'.");
            };
        } catch (IOException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new IOException("Invalid historical generated registry semantics for "
                    + key.registryKey() + " / " + key.resourceKey() + ".", failure);
        }
        if (!source.semanticSha256().equals(semanticFingerprint)) {
            throw new IOException("Historical generated registry semantic fingerprint changed for "
                    + key.registryKey() + " / " + key.resourceKey() + ".");
        }
        String contractFingerprint = fingerprintGeneratedSemantic(key, source);
        if (!expectedDefinitionFingerprint.equals(contractFingerprint)) {
            throw new IOException("Historical generated registry contract changed for "
                    + key.registryKey() + " / " + key.resourceKey() + ".");
        }
    }

    private static IrisDimensionType dimensionTypeFromSemantic(String semanticJson) {
        JsonObject semantic = requireJsonObject(semanticJson, "Dimension type semantic source");
        GenerationManifest.JsonSchema.requireFields(
                semantic,
                "dimension type semantic source",
                "base", "logicalHeight", "height", "minY", "options"
        );
        JsonObject optionsJson = GenerationManifest.JsonSchema.requireObject(
                semantic, "options", "dimension type semantic source"
        );
        GenerationManifest.JsonSchema.requireFields(
                optionsJson, "dimension type options",
                "coordinateScale", "ambientLight", "fixedTime", "cloudHeight", "monsterSpawnBlockLightLimit",
                "ultrawarm", "natural", "piglinSafe", "respawnAnchorWorks", "bedWorks", "raids", "skylight", "ceiling"
        );
        IrisDimensionTypeOptions options = new Gson().fromJson(optionsJson, IrisDimensionTypeOptions.class).copy();
        return new IrisDimensionType(
                IDataFixer.Dimension.valueOf(GenerationManifest.JsonSchema.requireString(
                        semantic, "base", "dimension type semantic source"
                )),
                options,
                GenerationManifest.JsonSchema.requireInt(semantic, "logicalHeight", "dimension type semantic source"),
                GenerationManifest.JsonSchema.requireInt(semantic, "height", "dimension type semantic source"),
                GenerationManifest.JsonSchema.requireInt(semantic, "minY", "dimension type semantic source")
        );
    }

    private static IrisBiomeCustom customBiomeFromSemantic(
            GenerationRegistryContract.PhysicalResourceKey key,
            String semanticJson
    ) {
        JsonObject semantic = requireJsonObject(semanticJson, "Custom biome semantic source");
        Set<String> requiredFields = Set.of(
                "temperature",
                "humidity",
                "downfallType",
                "category",
                "spawnRarity",
                "skyColor",
                "fogColor",
                "waterColor",
                "waterFogColor",
                "spawners"
        );
        Set<String> optionalFields = Set.of("grassColor", "foliageColor", "ambientParticle");
        requireSemanticFields(semantic, requiredFields, optionalFields, "custom biome semantic source");
        IrisBiomeCustom biome = new IrisBiomeCustom()
                .setId(key.resourceKey())
                .setTemperature(GenerationManifest.JsonSchema.requireDouble(
                        semantic,
                        "temperature",
                        "custom biome semantic source"
                ))
                .setHumidity(GenerationManifest.JsonSchema.requireDouble(
                        semantic,
                        "humidity",
                        "custom biome semantic source"
                ))
                .setDownfallType(IrisBiomeCustomPrecipType.valueOf(
                        GenerationManifest.JsonSchema.requireString(
                                semantic,
                                "downfallType",
                                "custom biome semantic source"
                        )
                ))
                .setCategory(IrisBiomeCustomCategory.valueOf(
                        GenerationManifest.JsonSchema.requireString(
                                semantic,
                                "category",
                                "custom biome semantic source"
                        )
                ))
                .setSpawnRarity(GenerationManifest.JsonSchema.requireInt(
                        semantic,
                        "spawnRarity",
                        "custom biome semantic source"
                ))
                .setSkyColor(requireColor(semantic, "skyColor"))
                .setFogColor(requireColor(semantic, "fogColor"))
                .setWaterColor(requireColor(semantic, "waterColor"))
                .setWaterFogColor(requireColor(semantic, "waterFogColor"))
                .setGrassColor(optionalColor(semantic, "grassColor"))
                .setFoliageColor(optionalColor(semantic, "foliageColor"))
                .setSpawns(spawnsFromSemantic(semantic));
        JsonElement particleElement = semantic.get("ambientParticle");
        if (particleElement != null) {
            JsonObject particle = GenerationManifest.JsonSchema.requireObject(
                    particleElement,
                    "custom biome semantic source.ambientParticle"
            );
            GenerationManifest.JsonSchema.requireFields(
                    particle,
                    "custom biome semantic source.ambientParticle",
                    "type",
                    "rarity"
            );
            biome.setAmbientParticle(new IrisBiomeCustomParticle()
                    .setParticle(GenerationManifest.JsonSchema.requireString(
                            particle,
                            "type",
                            "custom biome semantic source.ambientParticle"
                    ))
                    .setRarity(GenerationManifest.JsonSchema.requireInt(
                            particle,
                            "rarity",
                            "custom biome semantic source.ambientParticle"
                    )));
        }
        return biome;
    }

    private static KList<IrisBiomeCustomSpawn> spawnsFromSemantic(JsonObject semantic) {
        JsonObject spawners = GenerationManifest.JsonSchema.requireObject(
                semantic,
                "spawners",
                "custom biome semantic source"
        );
        KList<IrisBiomeCustomSpawn> result = new KList<>();
        for (Map.Entry<String, JsonElement> groupEntry : spawners.entrySet()) {
            IrisBiomeCustomSpawnType group = IrisBiomeCustomSpawnType.valueOf(
                    groupEntry.getKey().toUpperCase(Locale.ROOT)
            );
            if (!groupEntry.getValue().isJsonArray()) {
                throw new IllegalArgumentException("custom biome semantic source.spawners."
                        + groupEntry.getKey() + " must be an array.");
            }
            for (JsonElement spawnElement : groupEntry.getValue().getAsJsonArray()) {
                JsonObject spawn = GenerationManifest.JsonSchema.requireObject(
                        spawnElement,
                        "custom biome semantic source spawn"
                );
                GenerationManifest.JsonSchema.requireFields(
                        spawn,
                        "custom biome semantic source spawn",
                        "type",
                        "weight",
                        "minCount",
                        "maxCount"
                );
                result.add(new IrisBiomeCustomSpawn()
                        .setType(GenerationManifest.JsonSchema.requireString(
                                spawn,
                                "type",
                                "custom biome semantic source spawn"
                        ))
                        .setWeight(GenerationManifest.JsonSchema.requireInt(
                                spawn,
                                "weight",
                                "custom biome semantic source spawn"
                        ))
                        .setMinCount(GenerationManifest.JsonSchema.requireInt(
                                spawn,
                                "minCount",
                                "custom biome semantic source spawn"
                        ))
                        .setMaxCount(GenerationManifest.JsonSchema.requireInt(
                                spawn,
                                "maxCount",
                                "custom biome semantic source spawn"
                        ))
                        .setGroup(group));
            }
        }
        return result;
    }

    private static String requireColor(JsonObject semantic, String field) {
        int color = GenerationManifest.JsonSchema.requireInt(
                semantic,
                field,
                "custom biome semantic source"
        );
        if (color < 0 || color > 0xFFFFFF) {
            throw new IllegalArgumentException("custom biome semantic source."
                    + field + " must be an RGB color.");
        }
        return String.format(Locale.ROOT, "#%06x", color);
    }

    private static String optionalColor(JsonObject semantic, String field) {
        return semantic.has(field) ? requireColor(semantic, field) : "";
    }

    private static void requireSemanticFields(
            JsonObject semantic,
            Set<String> requiredFields,
            Set<String> optionalFields,
            String context
    ) {
        Set<String> missing = new TreeSet<>(requiredFields);
        missing.removeAll(semantic.keySet());
        Set<String> unknown = new TreeSet<>(semantic.keySet());
        unknown.removeAll(requiredFields);
        unknown.removeAll(optionalFields);
        if (!missing.isEmpty() || !unknown.isEmpty()) {
            throw new IllegalArgumentException(
                    "Invalid " + context + " fields. Missing=" + missing + ", unknown=" + unknown + "."
            );
        }
    }

    private static JsonObject requireJsonObject(String json, String label) {
        JsonElement element = parseJson(Objects.requireNonNull(json, label));
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException(label + " must be an object.");
        }
        return element.getAsJsonObject();
    }

    private static void requireGeneratedDefinition(
            PlatformGenerationRegistry.Definition definition,
            String label
    ) {
        PlatformGenerationRegistry.Definition requiredDefinition = Objects.requireNonNull(definition, label);
        if (requiredDefinition.encoding() != PlatformGenerationRegistry.Encoding.JSON) {
            throw new IllegalArgumentException(label + " must use canonical JSON encoding.");
        }
        parseJson(requiredDefinition.value());
    }

    private static ReferencedPlatformKeys referencedPlatformKeys(
            Path packRoot,
            PlatformRegistries registries
    ) throws IOException {
        Set<String> knownBlocks = normalizedKeys(registries.blockTypeKeys());
        Set<String> knownEntities = normalizedKeys(registries.entityKeys());
        Set<String> blocks = new TreeSet<>();
        Set<String> entities = new TreeSet<>();
        try (Stream<Path> stream = Files.walk(packRoot)) {
            List<Path> files = stream.filter(path -> isVisibleRegularFile(packRoot, path))
                    .sorted(Comparator.comparing(path -> packRoot.relativize(path).toString()))
                    .toList();
            for (Path file : files) {
                String fileName = file.getFileName().toString();
                if (fileName.endsWith(".json")) {
                    JsonElement json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                    collectPlatformKeys(json, knownBlocks, knownEntities, blocks, entities);
                } else if (fileName.endsWith(".iob")) {
                    for (String blockState : IrisObjectIO.readPaletteKeys(file.toFile())) {
                        addKnownKey(blocks, blockState, knownBlocks);
                    }
                }
            }
        } catch (RuntimeException exception) {
            throw new IOException("Unable to inspect Iris pack platform registry references.", exception);
        }
        return new ReferencedPlatformKeys(Set.copyOf(blocks), Set.copyOf(entities));
    }

    private static boolean isVisibleRegularFile(Path root, Path path) {
        if (path.equals(root) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        Path relative = root.relativize(path);
        if (relative.getNameCount() > 0
                && PackDirectoryResolver.isHiddenName(relative.getName(0).toString())) {
            return false;
        }
        return !Files.isSymbolicLink(path);
    }

    private static void collectPlatformKeys(
            JsonElement element,
            Set<String> knownBlocks,
            Set<String> knownEntities,
            Set<String> blocks,
            Set<String> entities
    ) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                collectPlatformKeys(entry.getValue(), knownBlocks, knownEntities, blocks, entities);
            }
            return;
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                collectPlatformKeys(child, knownBlocks, knownEntities, blocks, entities);
            }
            return;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (!primitive.isString()) {
            return;
        }
        String value = primitive.getAsString();
        addKnownKey(blocks, value, knownBlocks);
        addKnownKey(entities, value, knownEntities);
    }

    private static void addKnownKey(Set<String> destination, String value, Set<String> known) {
        if (value == null) {
            return;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        int stateStart = normalized.indexOf('[');
        if (stateStart >= 0) {
            normalized = normalized.substring(0, stateStart);
        }
        if (!normalized.contains(":")) {
            normalized = "minecraft:" + normalized;
        }
        if (known.contains(normalized)) {
            destination.add(normalized);
        }
    }

    private static Set<String> normalizedKeys(List<String> keys) throws IOException {
        if (keys == null) {
            throw new IOException("Platform registry key enumeration returned null.");
        }
        Set<String> normalized = new HashSet<>(Math.max(16, keys.size() * 2));
        for (String key : keys) {
            if (key != null && !key.isBlank()) {
                String value = key.toLowerCase(Locale.ROOT);
                int stateStart = value.indexOf('[');
                normalized.add(stateStart < 0 ? value : value.substring(0, stateStart));
            }
        }
        return normalized;
    }

    private static void addResourceKey(Set<String> keys, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        keys.add(normalized.contains(":") ? normalized : "minecraft:" + normalized);
    }

    private static GenerationRegistryContract.PhysicalResourceKey key(
            String registryKey,
            String resourceKey
    ) {
        return new GenerationRegistryContract.PhysicalResourceKey(registryKey, resourceKey);
    }

    private static void requireRuntimeIdentity(PlatformGenerationRegistry registry) {
        requireText(registry.runtimeIdentity(), "Platform generation runtime identity");
    }

    private static String requireSha256(String value, String label) {
        String required = Objects.requireNonNull(value, label);
        if (!SHA_256_PATTERN.matcher(required).matches()) {
            throw new IllegalArgumentException(label + " must be a lowercase SHA-256 value.");
        }
        return required;
    }

    private static String requireText(String value, String label) {
        String required = Objects.requireNonNull(value, label).trim();
        if (required.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be blank.");
        }
        return required;
    }

    private static int customBiomeColor(String value) {
        String required = Objects.requireNonNull(value, "Custom biome color");
        String normalized = (required.startsWith("#") ? required : "#" + required).trim();
        try {
            return Color.decode(normalized).getRGB() & 0x00FFFFFF;
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static JsonElement parseJson(String value) {
        try {
            return JsonParser.parseString(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Platform registry definition is not valid JSON.", exception);
        }
    }

    private static void updateJson(MessageDigest digest, JsonElement element) {
        if (element == null || element.isJsonNull()) {
            digest.update((byte) 0);
            return;
        }
        if (element.isJsonObject()) {
            digest.update((byte) 1);
            JsonObject object = element.getAsJsonObject();
            List<Map.Entry<String, JsonElement>> entries = new ArrayList<>(object.entrySet());
            entries.sort(Map.Entry.comparingByKey());
            updateInt(digest, entries.size());
            for (Map.Entry<String, JsonElement> entry : entries) {
                updateString(digest, entry.getKey());
                updateJson(digest, entry.getValue());
            }
            return;
        }
        if (element.isJsonArray()) {
            digest.update((byte) 2);
            JsonArray array = element.getAsJsonArray();
            updateInt(digest, array.size());
            for (JsonElement child : array) {
                updateJson(digest, child);
            }
            return;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            digest.update((byte) 3);
            digest.update(primitive.getAsBoolean() ? (byte) 1 : (byte) 0);
        } else if (primitive.isString()) {
            digest.update((byte) 4);
            updateString(digest, primitive.getAsString());
        } else if (primitive.isNumber()) {
            digest.update((byte) 5);
            BigDecimal number = primitive.getAsBigDecimal().stripTrailingZeros();
            updateString(digest, number.signum() == 0 ? "0" : number.toString());
        } else {
            throw new IllegalArgumentException("Platform registry definition contains an unsupported JSON value.");
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static void updateString(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        updateInt(digest, bytes.length);
        digest.update(bytes);
    }

    private static void updateInt(MessageDigest digest, int value) {
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value).array());
    }

    public enum CustomBiomeAliasPolicy {
        CONTENT_ADDRESSED_ONLY,
        RETAIN_LEGACY_ALIASES
    }

    public static final class CustomBiomeResourceResolver {
        private final GenerationRegistryContract contract;
        private final UnaryOperator<String> resourceKeyFactory;
        private final Set<String> includedEntities;
        private final Map<String, Map<IrisBiomeCustom, String>> resolved = new LinkedHashMap<>();

        public CustomBiomeResourceResolver(
                GenerationRegistryContract contract,
                UnaryOperator<String> resourceKeyFactory
        ) {
            this.contract = Objects.requireNonNull(contract, "contract");
            this.resourceKeyFactory = Objects.requireNonNull(resourceKeyFactory, "resourceKeyFactory");
            Set<String> entities = new HashSet<>();
            for (Map.Entry<GenerationRegistryContract.PhysicalResourceKey, GenerationRegistryContract.GeneratedSource>
                    entry : contract.generatedSources().entrySet()) {
                GenerationRegistryContract.GeneratedSource source = entry.getValue();
                if (!BIOME_REGISTRY.equals(entry.getKey().registryKey())
                        || !CUSTOM_BIOME_EFFECTIVE_SOURCE_SCHEMA.equals(source.sourceSchema())) {
                    continue;
                }
                IrisBiomeCustom frozenBiome = customBiomeFromSemantic(entry.getKey(), source.semanticJson());
                for (IrisBiomeCustomSpawn spawn : frozenBiome.getSpawns()) {
                    entities.add(spawn.getTypeKey());
                }
            }
            includedEntities = Set.copyOf(entities);
        }

        public synchronized String resolve(String dimensionKey, IrisBiomeCustom customBiome) throws IOException {
            String dimension = requireText(dimensionKey, "Dimension load key").toLowerCase(Locale.ROOT);
            IrisBiomeCustom requiredBiome = Objects.requireNonNull(customBiome, "customBiome");
            Map<IrisBiomeCustom, String> dimensionKeys = resolved.computeIfAbsent(
                    dimension,
                    ignored -> new IdentityHashMap<>()
            );
            String existing = dimensionKeys.get(requiredBiome);
            if (existing != null) {
                return existing;
            }
            String semanticFingerprint = fingerprintCustomBiomeAuthoredDefinition(
                    customBiomeSemanticJson(requiredBiome, includedEntities::contains)
            );
            String resourceKey = resourceKeyFactory.apply(fingerprintCustomBiomeIdentity(
                    semanticFingerprint,
                    dimension,
                    requiredBiome.getId()
            ));
            GenerationRegistryContract.PhysicalResourceKey physicalKey = key(BIOME_REGISTRY, resourceKey);
            GenerationRegistryContract.GeneratedSource source = contract.generatedSources().get(physicalKey);
            if (source == null || !semanticFingerprint.equals(source.semanticSha256())) {
                throw new IOException("Historical generation registry contract has no exact custom biome definition for "
                        + dimension + ":" + requiredBiome.getId() + ".");
            }
            requireGeneratedSemanticFingerprint(physicalKey, source, contract.definitions().get(physicalKey));
            dimensionKeys.put(requiredBiome, resourceKey);
            return resourceKey;
        }
    }

    public record CustomBiomeDefinition(
            GenerationRegistryContract.PhysicalResourceKey physicalKey,
            String identityFingerprint,
            String semanticFingerprint,
            String semanticJson,
            String contentFingerprint,
            String sourceJson,
            PlatformGenerationRegistry.Definition definition
    ) {
        public CustomBiomeDefinition {
            physicalKey = Objects.requireNonNull(physicalKey, "physicalKey");
            if (!BIOME_REGISTRY.equals(physicalKey.registryKey())) {
                throw new IllegalArgumentException("Custom biome definition must use the biome registry.");
            }
            identityFingerprint = requireSha256(
                    identityFingerprint,
                    "Custom biome identity fingerprint"
            );
            semanticFingerprint = requireSha256(
                    semanticFingerprint,
                    "Custom biome semantic fingerprint"
            );
            semanticJson = requireText(semanticJson, "Custom biome semantic JSON");
            contentFingerprint = requireSha256(contentFingerprint, "Custom biome content fingerprint");
            sourceJson = requireText(sourceJson, "Custom biome source JSON");
            definition = Objects.requireNonNull(definition, "definition");
        }
    }

    private record ReferencedPlatformKeys(Set<String> blockKeys, Set<String> entityKeys) {
    }
}
