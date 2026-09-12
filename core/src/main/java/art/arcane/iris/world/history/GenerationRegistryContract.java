package art.arcane.iris.world.history;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.util.TreeSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Pattern;

public final class GenerationRegistryContract {
    public static final int FINGERPRINT_SCHEMA_VERSION_ONE = 1;
    public static final int CURRENT_FINGERPRINT_SCHEMA = FINGERPRINT_SCHEMA_VERSION_ONE;

    private static final String FINGERPRINT_DOMAIN = "iris-generation-registry-contract";
    private static final int MAX_DEFINITIONS = 65_535;
    private static final int MAX_RENDERER_IDENTITY_BYTES = 512;
    private static final int MAX_GENERATED_SEMANTIC_BYTES = 1024 * 1024;
    private static final int MAX_GENERATED_SOURCE_BYTES = 1024 * 1024;
    private static final int MAX_TOTAL_GENERATED_SOURCE_BYTES = 8 * 1024 * 1024;
    private static final Pattern RESOURCE_KEY_PATTERN = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");
    private static final Pattern SHA_256_PATTERN = Pattern.compile("[0-9a-f]{64}");

    private final int fingerprintSchema;
    private final String fingerprint;
    private final NavigableMap<PhysicalResourceKey, String> definitions;
    private final NavigableMap<PhysicalResourceKey, GeneratedSource> generatedSources;
    private final NavigableMap<String, List<String>> biomeTags;

    private GenerationRegistryContract(
            int fingerprintSchema,
            String fingerprint,
            NavigableMap<PhysicalResourceKey, String> definitions,
            NavigableMap<PhysicalResourceKey, GeneratedSource> generatedSources,
            Map<String, ? extends Collection<String>> biomeTags
    ) {
        requireSupportedFingerprintSchema(fingerprintSchema);
        this.fingerprintSchema = fingerprintSchema;
        this.definitions = immutableDefinitions(definitions);
        this.generatedSources = immutableGeneratedSources(generatedSources, this.definitions);
        this.biomeTags = canonicalBiomeTags(biomeTags, this.definitions);
        this.fingerprint = requireSha256(fingerprint, "Registry contract fingerprint");
        String expectedFingerprint = fingerprint(fingerprintSchema, this.definitions, this.generatedSources, this.biomeTags);
        if (!this.fingerprint.equals(expectedFingerprint)) {
            throw new IllegalArgumentException("Registry contract fingerprint does not match its definitions.");
        }
    }

    public static GenerationRegistryContract empty() {
        return fromDefinitions(CURRENT_FINGERPRINT_SCHEMA, Map.of());
    }

    public static GenerationRegistryContract fromDefinitions(Map<PhysicalResourceKey, String> definitions) {
        return fromDefinitions(CURRENT_FINGERPRINT_SCHEMA, definitions);
    }

    public static GenerationRegistryContract fromDefinitions(
            int fingerprintSchema,
            Map<PhysicalResourceKey, String> definitions
    ) {
        return fromDefinitionsAndGeneratedSources(fingerprintSchema, definitions, Map.of());
    }

    public static GenerationRegistryContract fromDefinitionsAndGeneratedSources(
            Map<PhysicalResourceKey, String> definitions,
            Map<PhysicalResourceKey, GeneratedSource> generatedSources
    ) {
        return fromDefinitionsAndGeneratedSources(
                CURRENT_FINGERPRINT_SCHEMA,
                definitions,
                generatedSources
        );
    }

    public static GenerationRegistryContract fromDefinitionsAndGeneratedSources(
            int fingerprintSchema,
            Map<PhysicalResourceKey, String> definitions,
            Map<PhysicalResourceKey, GeneratedSource> generatedSources
    ) {
        requireSupportedFingerprintSchema(fingerprintSchema);
        NavigableMap<PhysicalResourceKey, String> canonicalDefinitions = canonicalizeDefinitions(definitions);
        NavigableMap<PhysicalResourceKey, GeneratedSource> canonicalSources = canonicalizeGeneratedSources(
                generatedSources,
                canonicalDefinitions
        );
        return new GenerationRegistryContract(
                fingerprintSchema,
                fingerprint(fingerprintSchema, canonicalDefinitions, canonicalSources, new TreeMap<>()),
                canonicalDefinitions,
                canonicalSources,
                Map.of()
        );
    }

    public GenerationRegistryContract withBiomeTags(Map<String, ? extends Collection<String>> tags) {
        NavigableMap<String, List<String>> canonical = canonicalBiomeTags(tags, definitions);
        return new GenerationRegistryContract(fingerprintSchema,
                fingerprint(fingerprintSchema, definitions, generatedSources, canonical),
                definitions, generatedSources, canonical);
    }

    public NavigableMap<String, List<String>> biomeTags() {
        return biomeTags;
    }

    public int fingerprintSchema() {
        return fingerprintSchema;
    }

    public String fingerprint() {
        return fingerprint;
    }

    public NavigableMap<PhysicalResourceKey, String> definitions() {
        return definitions;
    }

    public NavigableMap<PhysicalResourceKey, GeneratedSource> generatedSources() {
        return generatedSources;
    }

    public void requireDefinitionsAvailableIn(GenerationRegistryContract available) throws IOException {
        GenerationRegistryContract requiredAvailable = Objects.requireNonNull(available, "available");
        for (Map.Entry<PhysicalResourceKey, String> entry : definitions.entrySet()) {
            String availableFingerprint = requiredAvailable.definitions.get(entry.getKey());
            if (availableFingerprint == null) {
                throw new IOException("Missing historical registry definition "
                        + entry.getKey().registryKey() + " / " + entry.getKey().resourceKey() + ".");
            }
            if (!entry.getValue().equals(availableFingerprint)) {
                throw new IOException("Historical registry definition changed for "
                        + entry.getKey().registryKey() + " / " + entry.getKey().resourceKey() + ".");
            }
        }
    }

    JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("fingerprintSchema", fingerprintSchema);
        json.addProperty("fingerprint", fingerprint);
        JsonArray definitionArray = new JsonArray();
        for (Map.Entry<PhysicalResourceKey, String> entry : definitions.entrySet()) {
            JsonObject definitionJson = new JsonObject();
            definitionJson.addProperty("registryKey", entry.getKey().registryKey());
            definitionJson.addProperty("resourceKey", entry.getKey().resourceKey());
            definitionJson.addProperty("definitionSha256", entry.getValue());
            definitionArray.add(definitionJson);
        }
        json.add("definitions", definitionArray);
        JsonArray generatedSourceArray = new JsonArray();
        for (Map.Entry<PhysicalResourceKey, GeneratedSource> entry : generatedSources.entrySet()) {
            JsonObject sourceJson = new JsonObject();
            sourceJson.addProperty("registryKey", entry.getKey().registryKey());
            sourceJson.addProperty("resourceKey", entry.getKey().resourceKey());
            sourceJson.addProperty("sourceSchema", entry.getValue().sourceSchema());
            sourceJson.addProperty("semanticSha256", entry.getValue().semanticSha256());
            sourceJson.addProperty("semanticJson", entry.getValue().semanticJson());
            sourceJson.addProperty("rendererIdentity", entry.getValue().rendererIdentity());
            sourceJson.addProperty(
                    "renderedDefinitionSha256",
                    entry.getValue().renderedDefinitionSha256()
            );
            sourceJson.addProperty("sourceJson", entry.getValue().sourceJson());
            generatedSourceArray.add(sourceJson);
        }
        json.add("generatedSources", generatedSourceArray);
        JsonObject tags = new JsonObject();
        for (Map.Entry<String, List<String>> entry : biomeTags.entrySet()) {
            JsonArray values = new JsonArray();
            entry.getValue().forEach(values::add);
            tags.add(entry.getKey(), values);
        }
        json.add("biomeTags", tags);
        return json;
    }

    static GenerationRegistryContract fromJson(JsonObject json) {
        GenerationManifest.JsonSchema.requireFields(
                json,
                "registry contract",
                "fingerprintSchema",
                "fingerprint",
                "definitions",
                "generatedSources",
                "biomeTags"
        );
        int fingerprintSchema = GenerationManifest.JsonSchema.requireInt(
                json,
                "fingerprintSchema",
                "registry contract"
        );
        String fingerprint = GenerationManifest.JsonSchema.requireString(
                json,
                "fingerprint",
                "registry contract"
        );
        JsonArray definitionArray = GenerationManifest.JsonSchema.requireArray(
                json,
                "definitions",
                "registry contract"
        );
        NavigableMap<PhysicalResourceKey, String> definitions = parseCanonicalDefinitions(definitionArray);
        JsonArray generatedSourceArray = GenerationManifest.JsonSchema.requireArray(
                json,
                "generatedSources",
                "registry contract"
        );
        NavigableMap<PhysicalResourceKey, GeneratedSource> generatedSources = parseCanonicalGeneratedSources(
                generatedSourceArray,
                definitions
        );
        JsonObject encodedTags = GenerationManifest.JsonSchema.requireObject(json.get("biomeTags"), "registry biome tags");
        Map<String, List<String>> tags = new TreeMap<>();
        for (Map.Entry<String, JsonElement> entry : encodedTags.entrySet()) {
            JsonArray values = GenerationManifest.JsonSchema.requireArray(encodedTags, entry.getKey(), "registry biome tags");
            List<String> names = new ArrayList<>();
            for (JsonElement value : values) {
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                    throw new IllegalArgumentException("Registry biome tags must contain resource keys.");
                }
                names.add(value.getAsString());
            }
            tags.put(entry.getKey(), names);
        }
        return new GenerationRegistryContract(fingerprintSchema, fingerprint, definitions, generatedSources, tags);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GenerationRegistryContract contract)) {
            return false;
        }
        return fingerprintSchema == contract.fingerprintSchema
                && fingerprint.equals(contract.fingerprint)
                && definitions.equals(contract.definitions)
                && generatedSources.equals(contract.generatedSources)
                && biomeTags.equals(contract.biomeTags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fingerprintSchema, fingerprint, definitions, generatedSources, biomeTags);
    }

    @Override
    public String toString() {
        return "GenerationRegistryContract[fingerprintSchema=" + fingerprintSchema
                + ", fingerprint=" + fingerprint
                + ", definitions=" + definitions
                + ", generatedSourceKeys=" + generatedSources.keySet() + "]";
    }

    public static void requireSupportedFingerprintSchema(int fingerprintSchema) {
        if (fingerprintSchema != FINGERPRINT_SCHEMA_VERSION_ONE) {
            throw new IllegalArgumentException(
                    "Unsupported generation registry fingerprint schema " + fingerprintSchema + "."
            );
        }
    }

    private static NavigableMap<PhysicalResourceKey, String> canonicalizeDefinitions(
            Map<PhysicalResourceKey, String> definitions
    ) {
        Map<PhysicalResourceKey, String> requiredDefinitions = Objects.requireNonNull(definitions, "definitions");
        if (requiredDefinitions.size() > MAX_DEFINITIONS) {
            throw new IllegalArgumentException("Registry contract contains too many definitions.");
        }
        TreeMap<PhysicalResourceKey, String> canonicalDefinitions = new TreeMap<>();
        for (Map.Entry<PhysicalResourceKey, String> entry : requiredDefinitions.entrySet()) {
            PhysicalResourceKey key = Objects.requireNonNull(entry.getKey(), "registry definition key");
            String definitionSha256 = requireSha256(entry.getValue(), "Registry definition fingerprint");
            canonicalDefinitions.put(key, definitionSha256);
        }
        return canonicalDefinitions;
    }

    private static NavigableMap<PhysicalResourceKey, String> parseCanonicalDefinitions(JsonArray definitionArray) {
        if (definitionArray.size() > MAX_DEFINITIONS) {
            throw new IllegalArgumentException("Registry contract contains too many definitions.");
        }
        TreeMap<PhysicalResourceKey, String> definitions = new TreeMap<>();
        PhysicalResourceKey previousKey = null;
        for (int index = 0; index < definitionArray.size(); index++) {
            JsonElement element = definitionArray.get(index);
            JsonObject definitionJson = GenerationManifest.JsonSchema.requireObject(
                    element,
                    "registry contract.definitions[" + index + "]"
            );
            GenerationManifest.JsonSchema.requireFields(
                    definitionJson,
                    "registry contract definition",
                    "registryKey",
                    "resourceKey",
                    "definitionSha256"
            );
            PhysicalResourceKey key = new PhysicalResourceKey(
                    GenerationManifest.JsonSchema.requireString(
                            definitionJson,
                            "registryKey",
                            "registry contract definition"
                    ),
                    GenerationManifest.JsonSchema.requireString(
                            definitionJson,
                            "resourceKey",
                            "registry contract definition"
                    )
            );
            if (previousKey != null && previousKey.compareTo(key) >= 0) {
                throw new IllegalArgumentException(
                        "Registry contract definitions must be strictly sorted by physical resource key."
                );
            }
            String definitionSha256 = requireSha256(
                    GenerationManifest.JsonSchema.requireString(
                            definitionJson,
                            "definitionSha256",
                            "registry contract definition"
                    ),
                    "Registry definition fingerprint"
            );
            definitions.put(key, definitionSha256);
            previousKey = key;
        }
        return definitions;
    }

    private static NavigableMap<PhysicalResourceKey, String> immutableDefinitions(
            NavigableMap<PhysicalResourceKey, String> definitions
    ) {
        NavigableMap<PhysicalResourceKey, String> canonicalDefinitions = canonicalizeDefinitions(definitions);
        return Collections.unmodifiableNavigableMap(canonicalDefinitions);
    }

    private static NavigableMap<PhysicalResourceKey, GeneratedSource> canonicalizeGeneratedSources(
            Map<PhysicalResourceKey, GeneratedSource> generatedSources,
            NavigableMap<PhysicalResourceKey, String> definitions
    ) {
        Map<PhysicalResourceKey, GeneratedSource> requiredSources = Objects.requireNonNull(
                generatedSources,
                "generatedSources"
        );
        if (requiredSources.size() > MAX_DEFINITIONS) {
            throw new IllegalArgumentException("Registry contract contains too many generated sources.");
        }
        TreeMap<PhysicalResourceKey, GeneratedSource> canonicalSources = new TreeMap<>();
        long totalBytes = 0L;
        for (Map.Entry<PhysicalResourceKey, GeneratedSource> entry : requiredSources.entrySet()) {
            PhysicalResourceKey key = Objects.requireNonNull(entry.getKey(), "generated source key");
            if (!definitions.containsKey(key)) {
                throw new IllegalArgumentException("Generated registry source has no matching definition: "
                        + key.registryKey() + " / " + key.resourceKey() + ".");
            }
            GeneratedSource source = Objects.requireNonNull(entry.getValue(), "generated source");
            int sourceBytes = source.sourceJson().getBytes(StandardCharsets.UTF_8).length;
            if (sourceBytes > MAX_GENERATED_SOURCE_BYTES) {
                throw new IllegalArgumentException("Generated registry source is too large: "
                        + key.registryKey() + " / " + key.resourceKey() + ".");
            }
            totalBytes += sourceBytes;
            totalBytes += source.semanticJson().getBytes(StandardCharsets.UTF_8).length;
            totalBytes += source.rendererIdentity().getBytes(StandardCharsets.UTF_8).length;
            totalBytes += source.sourceSchema().getBytes(StandardCharsets.UTF_8).length;
            if (totalBytes > MAX_TOTAL_GENERATED_SOURCE_BYTES) {
                throw new IllegalArgumentException("Registry contract generated sources are too large.");
            }
            canonicalSources.put(key, source);
        }
        return canonicalSources;
    }

    private static NavigableMap<PhysicalResourceKey, GeneratedSource> parseCanonicalGeneratedSources(
            JsonArray generatedSourceArray,
            NavigableMap<PhysicalResourceKey, String> definitions
    ) {
        if (generatedSourceArray.size() > MAX_DEFINITIONS) {
            throw new IllegalArgumentException("Registry contract contains too many generated sources.");
        }
        LinkedHashMap<PhysicalResourceKey, GeneratedSource> sources = new LinkedHashMap<>();
        PhysicalResourceKey previousKey = null;
        for (int index = 0; index < generatedSourceArray.size(); index++) {
            JsonObject sourceJson = GenerationManifest.JsonSchema.requireObject(
                    generatedSourceArray.get(index),
                    "registry contract.generatedSources[" + index + "]"
            );
            GenerationManifest.JsonSchema.requireFields(
                    sourceJson,
                    "registry contract generated source",
                    "registryKey",
                    "resourceKey",
                    "sourceSchema",
                    "semanticSha256",
                    "semanticJson",
                    "rendererIdentity",
                    "renderedDefinitionSha256",
                    "sourceJson"
            );
            PhysicalResourceKey key = new PhysicalResourceKey(
                    GenerationManifest.JsonSchema.requireString(
                            sourceJson,
                            "registryKey",
                            "registry contract generated source"
                    ),
                    GenerationManifest.JsonSchema.requireString(
                            sourceJson,
                            "resourceKey",
                            "registry contract generated source"
                    )
            );
            if (previousKey != null && previousKey.compareTo(key) >= 0) {
                throw new IllegalArgumentException(
                        "Registry contract generated sources must be strictly sorted by physical resource key."
                );
            }
            sources.put(
                    key,
                    new GeneratedSource(
                            GenerationManifest.JsonSchema.requireString(
                                    sourceJson,
                                    "sourceSchema",
                                    "registry contract generated source"
                            ),
                            GenerationManifest.JsonSchema.requireString(
                                    sourceJson,
                                    "semanticSha256",
                                    "registry contract generated source"
                            ),
                            GenerationManifest.JsonSchema.requireString(
                                    sourceJson,
                                    "semanticJson",
                                    "registry contract generated source"
                            ),
                            GenerationManifest.JsonSchema.requireString(
                                    sourceJson,
                                    "rendererIdentity",
                                    "registry contract generated source"
                            ),
                            GenerationManifest.JsonSchema.requireString(
                                    sourceJson,
                                    "renderedDefinitionSha256",
                                    "registry contract generated source"
                            ),
                            GenerationManifest.JsonSchema.requireString(
                                    sourceJson,
                                    "sourceJson",
                                    "registry contract generated source"
                            )
                    )
            );
            previousKey = key;
        }
        return canonicalizeGeneratedSources(sources, definitions);
    }

    private static NavigableMap<PhysicalResourceKey, GeneratedSource> immutableGeneratedSources(
            NavigableMap<PhysicalResourceKey, GeneratedSource> generatedSources,
            NavigableMap<PhysicalResourceKey, String> definitions
    ) {
        return Collections.unmodifiableNavigableMap(
                canonicalizeGeneratedSources(generatedSources, definitions)
        );
    }

    private static NavigableMap<String, List<String>> canonicalBiomeTags(
            Map<String, ? extends Collection<String>> values,
            Map<PhysicalResourceKey, String> definitions
    ) {
        if (values.size() > MAX_DEFINITIONS) {
            throw new IllegalArgumentException("Registry biome tags contain too many biomes.");
        }
        TreeMap<String, List<String>> normalized = new TreeMap<>();
        for (Map.Entry<String, ? extends Collection<String>> entry : values.entrySet()) {
            PhysicalResourceKey biome = new PhysicalResourceKey("minecraft:worldgen/biome", entry.getKey());
            if (!definitions.containsKey(biome)) {
                throw new IllegalArgumentException("Registry biome tags have no matching biome definition: " + entry.getKey());
            }
            TreeSet<String> tags = new TreeSet<>();
            for (String tag : entry.getValue()) {
                tags.add(new PhysicalResourceKey("minecraft:worldgen/biome", tag).resourceKey());
            }
            if (tags.size() > MAX_DEFINITIONS) {
                throw new IllegalArgumentException("Registry biome contains too many tags.");
            }
            normalized.put(biome.resourceKey(), List.copyOf(tags));
        }
        return Collections.unmodifiableNavigableMap(normalized);
    }

    private static String fingerprint(
            int fingerprintSchema,
            NavigableMap<PhysicalResourceKey, String> definitions,
            NavigableMap<PhysicalResourceKey, GeneratedSource> generatedSources,
            NavigableMap<String, List<String>> biomeTags
    ) {
        return switch (fingerprintSchema) {
            case FINGERPRINT_SCHEMA_VERSION_ONE -> fingerprintVersionOne(definitions, generatedSources, biomeTags);
            default -> throw new IllegalArgumentException(
                    "Unsupported generation registry fingerprint schema " + fingerprintSchema + "."
            );
        };
    }

    private static String fingerprintVersionOne(
            NavigableMap<PhysicalResourceKey, String> definitions,
            NavigableMap<PhysicalResourceKey, GeneratedSource> generatedSources,
            NavigableMap<String, List<String>> biomeTags
    ) {
        MessageDigest digest = sha256();
        updateString(digest, FINGERPRINT_DOMAIN);
        updateInt(digest, FINGERPRINT_SCHEMA_VERSION_ONE);
        updateInt(digest, definitions.size());
        for (Map.Entry<PhysicalResourceKey, String> entry : definitions.entrySet()) {
            updateString(digest, entry.getKey().registryKey());
            updateString(digest, entry.getKey().resourceKey());
            updateString(digest, entry.getValue());
        }
        updateInt(digest, generatedSources.size());
        for (Map.Entry<PhysicalResourceKey, GeneratedSource> entry : generatedSources.entrySet()) {
            updateString(digest, entry.getKey().registryKey());
            updateString(digest, entry.getKey().resourceKey());
            updateString(digest, entry.getValue().sourceSchema());
            updateString(digest, entry.getValue().semanticSha256());
            updateString(digest, entry.getValue().semanticJson());
            updateString(digest, entry.getValue().rendererIdentity());
            updateString(digest, entry.getValue().renderedDefinitionSha256());
            updateString(digest, entry.getValue().sourceJson());
        }
        updateInt(digest, biomeTags.size());
        for (Map.Entry<String, List<String>> entry : biomeTags.entrySet()) {
            updateString(digest, entry.getKey());
            updateInt(digest, entry.getValue().size());
            for (String tag : entry.getValue()) {
                updateString(digest, tag);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String requireGeneratedJson(String value, String label, int maximumBytes) {
        String source = Objects.requireNonNull(value, label);
        if (source.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank.");
        }
        if (source.getBytes(StandardCharsets.UTF_8).length > maximumBytes) {
            throw new IllegalArgumentException(label + " is too large.");
        }
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(source);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(label + " must be valid JSON.", exception);
        }
        if (!parsed.isJsonObject()) {
            throw new IllegalArgumentException(label + " must be a JSON object.");
        }
        return source;
    }

    private static String requireGeneratedSemanticJson(String value) {
        String semantic = requireGeneratedJson(
                value,
                "Generated registry semantic JSON",
                MAX_GENERATED_SEMANTIC_BYTES
        );
        return JsonParser.parseString(semantic).toString();
    }

    private static String requireRendererIdentity(String value) {
        return requireBoundedIdentity(value, "Generated registry renderer identity");
    }

    private static String requireBoundedIdentity(String value, String label) {
        String identity = Objects.requireNonNull(value, label).trim();
        if (identity.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be blank.");
        }
        if (identity.getBytes(StandardCharsets.UTF_8).length > MAX_RENDERER_IDENTITY_BYTES) {
            throw new IllegalArgumentException(label + " is too large.");
        }
        return identity;
    }

    private static String requireSha256(String value, String label) {
        String requiredValue = Objects.requireNonNull(value, label);
        if (!SHA_256_PATTERN.matcher(requiredValue).matches()) {
            throw new IllegalArgumentException(label + " must be a lowercase SHA-256 value.");
        }
        return requiredValue;
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

    public record PhysicalResourceKey(String registryKey, String resourceKey)
            implements Comparable<PhysicalResourceKey> {
        public PhysicalResourceKey {
            registryKey = requireResourceKey(registryKey, "Registry key");
            resourceKey = requireResourceKey(resourceKey, "Resource key");
        }

        @Override
        public int compareTo(PhysicalResourceKey other) {
            PhysicalResourceKey requiredOther = Objects.requireNonNull(other, "other");
            int registryComparison = registryKey.compareTo(requiredOther.registryKey);
            if (registryComparison != 0) {
                return registryComparison;
            }
            return resourceKey.compareTo(requiredOther.resourceKey);
        }

        private static String requireResourceKey(String value, String label) {
            String requiredValue = Objects.requireNonNull(value, label);
            if (!RESOURCE_KEY_PATTERN.matcher(requiredValue).matches()) {
                throw new IllegalArgumentException(label + " is invalid: " + requiredValue);
            }
            return requiredValue;
        }
    }

    public record GeneratedSource(
            String sourceSchema,
            String semanticSha256,
            String semanticJson,
            String rendererIdentity,
            String renderedDefinitionSha256,
        String sourceJson
    ) {
        public GeneratedSource {
            sourceSchema = requireBoundedIdentity(sourceSchema, "Generated registry source schema");
            semanticSha256 = requireSha256(semanticSha256, "Generated registry semantic fingerprint");
            semanticJson = requireGeneratedSemanticJson(semanticJson);
            rendererIdentity = requireRendererIdentity(rendererIdentity);
            renderedDefinitionSha256 = requireSha256(
                    renderedDefinitionSha256,
                    "Rendered registry definition fingerprint"
            );
            sourceJson = requireGeneratedJson(
                    sourceJson,
                    "Generated registry source JSON",
                    MAX_GENERATED_SOURCE_BYTES
            );
        }
    }
}
