package art.arcane.iris.pack.datapack.export.compile.biome;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.biome.IrisBiomeCustom;
import art.arcane.iris.generation.biome.IrisVanillaBiomeTags;
import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.VanillaResourceIdentifier;
import art.arcane.iris.pack.datapack.export.compile.PackCompileContext;
import art.arcane.iris.pack.datapack.export.ir.BiomeDef;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;
import art.arcane.iris.pack.datapack.export.ir.JsonResource;
import art.arcane.iris.pack.validation.ContentGate;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.VanillaDataSource;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

public final class BiomeDefinitionCompiler {
    private static final String VANILLA_NAMESPACE = "minecraft";
    private static final String BIOME_FOLDER = "/worldgen/biome/";
    private static final String TAG_FOLDER = "/tags/worldgen/biome";
    private static final String JSON_SUFFIX = ".json";
    private static final String SPAWNS_ATTRIBUTE = "minecraft:gameplay/natural_mob_spawns";
    private static final String SPAWN_PROBABILITY_ATTRIBUTE = "minecraft:gameplay/creature_world_gen_spawn_probability";
    private static final String SPAWN_PROBABILITY = "creature_spawn_probability";
    private static final List<String> CLIMATE_KEYS = List.of("has_precipitation", "temperature", "downfall", "temperature_modifier");
    private static final List<String> CUSTOM_KEYS = List.of("has_precipitation", "temperature", "downfall", "temperature_modifier",
            "effects", "attributes", "spawners", "spawn_costs", SPAWN_PROBABILITY);

    private final Optional<VanillaDataSource> vanilla;

    public BiomeDefinitionCompiler(Optional<VanillaDataSource> vanilla) {
        this.vanilla = Objects.requireNonNull(vanilla);
    }

    public static BiomeDefinitionCompiler forTarget(DataVersion target) {
        if (!IrisPlatforms.isBound()) {
            return new BiomeDefinitionCompiler(Optional.empty());
        }
        IrisPlatform platform = IrisPlatforms.get();
        boolean running = DataVersion.forMinecraftVersion(platform.minecraftVersion()) == target;
        return new BiomeDefinitionCompiler(running ? Optional.ofNullable(platform.vanillaData()) : Optional.empty());
    }

    public Definitions compile(PackCompileContext context, Map<String, String> loadKeyByBiomeId) {
        DataVersion target = context.capabilities().target();
        ContentGate gate = vanilla.isPresent() ? ContentGate.forData(context.data()) : new ContentGate(null, Map.of(), null);
        if (vanilla.isEmpty()) {
            diagnose(context, "dimensions/" + context.dimension().getLoadKey(), "Vanilla biome data for Minecraft " + target.getVersion()
                    + " is not available on this server; biomes are built from their Iris fields without vanilla music, ambience, spawns or structure tags");
        }
        Map<String, List<String>> vanillaTags = vanilla.map(BiomeDefinitionCompiler::directTags).orElse(Map.of());
        SortedMap<String, SortedSet<String>> members = new TreeMap<>();
        List<BiomeDef> biomes = new ArrayList<>(loadKeyByBiomeId.size());
        for (Map.Entry<String, String> entry : new TreeMap<>(loadKeyByBiomeId).entrySet()) {
            String id = entry.getKey();
            IrisBiome biome = Objects.requireNonNull(context.data().getBiomeLoader().load(entry.getValue(), false),
                    "Biome '" + entry.getValue() + "' could not be loaded");
            IrisBiomeCustom custom = custom(context, biome);
            String visualKey = biome.getBiomeScatter().isEmpty() ? biome.getDerivativeKey() : namespaced(biome.getBiomeScatter().getFirst());
            Optional<JsonObject> base = vanilla.flatMap(source -> read(source, visualKey));
            if (vanilla.isPresent() && base.isEmpty()) {
                diagnose(context, "biomes/" + biome.getLoadKey(), "Vanilla biome '" + visualKey + "' has no data for Minecraft " + target.getVersion()
                        + "; the biome is built from its Iris fields");
            }
            JsonObject overlay = custom == null ? null : customJson(custom, target, gate);
            JsonObject json = base.isPresent()
                    ? merge(base.get(), overlay, custom)
                    : retained(overlay == null ? customJson(new IrisBiomeCustom(), target, gate) : overlay);
            json.add("carvers", new JsonArray());
            json.add("features", new JsonArray());
            biomes.add(new BiomeDef(id.substring(id.indexOf(':') + 1), json));
            for (String tag : tags(biome, custom, vanillaTags)) {
                members.computeIfAbsent(tag, ignored -> new TreeSet<>()).add(id);
            }
        }
        List<JsonResource> tags = new ArrayList<>(members.size());
        for (Map.Entry<String, SortedSet<String>> tag : members.entrySet()) {
            JsonObject json = new JsonObject();
            json.addProperty("replace", false);
            JsonArray values = new JsonArray(tag.getValue().size());
            for (String member : tag.getValue()) {
                values.add(member);
            }
            json.add("values", values);
            int separator = tag.getKey().indexOf(':');
            tags.add(new JsonResource("data/" + tag.getKey().substring(0, separator) + TAG_FOLDER + "/" + tag.getKey().substring(separator + 1) + JSON_SUFFIX, json));
        }
        return new Definitions(biomes, tags);
    }

    private static IrisBiomeCustom custom(PackCompileContext context, IrisBiome biome) {
        boolean customs = biome.getCustomDerivitives() != null && biome.getCustomDerivitives().size() > 1;
        if (customs || biome.getBiomeScatter().size() > 1) {
            context.diagnostics().add(new ExportDiagnostic(ExportDiagnostic.Severity.INFO, ExportLimit.BIOME_VARIANT, "biomes/" + biome.getLoadKey(),
                    "Biomes with several custom derivatives or scatter biomes export as one biome using the first entry"));
        }
        return biome.isCustom() ? biome.getCustomDerivitives().getFirst() : null;
    }

    private static JsonObject customJson(IrisBiomeCustom custom, DataVersion target, ContentGate gate) {
        return JsonParser.parseString(custom.generateJson(target.get(), gate)).getAsJsonObject();
    }

    private static JsonObject merge(JsonObject base, JsonObject overlay, IrisBiomeCustom custom) {
        JsonObject merged = base.deepCopy();
        if (overlay == null) {
            return merged;
        }
        for (String key : CLIMATE_KEYS) {
            if (overlay.has(key)) {
                merged.add(key, overlay.get(key).deepCopy());
            }
        }
        boolean spawnRarity = custom.getSpawnRarity() > 0;
        putAll(object(merged, "effects"), overlay.getAsJsonObject("effects"));
        JsonObject overlayAttributes = overlay.getAsJsonObject("attributes");
        if (overlayAttributes != null) {
            JsonObject attributes = object(merged, "attributes");
            for (Map.Entry<String, JsonElement> attribute : overlayAttributes.entrySet()) {
                switch (attribute.getKey()) {
                    case SPAWNS_ATTRIBUTE -> mergeSpawnAttribute(attributes, attribute.getValue());
                    case SPAWN_PROBABILITY_ATTRIBUTE -> {
                        if (spawnRarity) {
                            attributes.add(attribute.getKey(), attribute.getValue().deepCopy());
                        }
                    }
                    default -> attributes.add(attribute.getKey(), attribute.getValue().deepCopy());
                }
            }
        }
        if (overlay.has("spawners")) {
            mergeSpawns(merged, overlay);
        }
        if (spawnRarity && overlay.has(SPAWN_PROBABILITY)) {
            merged.add(SPAWN_PROBABILITY, overlay.get(SPAWN_PROBABILITY).deepCopy());
        }
        return merged;
    }

    private static void mergeSpawnAttribute(JsonObject attributes, JsonElement overlay) {
        JsonElement existing = attributes.get(SPAWNS_ATTRIBUTE);
        if (existing == null || !existing.isJsonObject() || !overlay.isJsonObject()) {
            attributes.add(SPAWNS_ATTRIBUTE, overlay.deepCopy());
            return;
        }
        JsonObject target = spawnHolder(existing.getAsJsonObject());
        JsonObject source = spawnHolder(overlay.getAsJsonObject());
        appendCategories(object(target, "spawns_by_category"), source.getAsJsonObject("spawns_by_category"));
        putAll(object(target, "spawn_costs"), source.getAsJsonObject("spawn_costs"));
    }

    private static void mergeSpawns(JsonObject merged, JsonObject overlay) {
        appendCategories(object(merged, "spawners"), overlay.getAsJsonObject("spawners"));
        putAll(object(merged, "spawn_costs"), overlay.getAsJsonObject("spawn_costs"));
    }

    private static JsonObject spawnHolder(JsonObject attribute) {
        JsonElement argument = attribute.get("argument");
        return argument != null && argument.isJsonObject() ? argument.getAsJsonObject() : attribute;
    }

    private static void appendCategories(JsonObject target, JsonObject source) {
        if (source == null) {
            return;
        }
        for (Map.Entry<String, JsonElement> category : source.entrySet()) {
            JsonElement existing = target.get(category.getKey());
            JsonArray spawns = existing != null && existing.isJsonArray() ? existing.getAsJsonArray() : new JsonArray();
            spawns.addAll(category.getValue().deepCopy().getAsJsonArray());
            target.add(category.getKey(), spawns);
        }
    }

    private static void putAll(JsonObject target, JsonObject source) {
        if (source == null) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
            target.add(entry.getKey(), entry.getValue().deepCopy());
        }
    }

    private static JsonObject object(JsonObject parent, String key) {
        JsonElement existing = parent.get(key);
        if (existing != null && existing.isJsonObject()) {
            return existing.getAsJsonObject();
        }
        JsonObject created = new JsonObject();
        parent.add(key, created);
        return created;
    }

    private static JsonObject retained(JsonObject custom) {
        JsonObject retained = new JsonObject();
        for (String key : CUSTOM_KEYS) {
            if (custom.has(key)) {
                retained.add(key, custom.get(key).deepCopy());
            }
        }
        return retained;
    }

    private static SortedSet<String> tags(IrisBiome biome, IrisBiomeCustom custom, Map<String, List<String>> vanillaTags) {
        SortedSet<String> tags = new TreeSet<>();
        if (custom != null && custom.getTags() != null) {
            for (String tag : custom.getTags()) {
                String normalized = tagId(tag);
                if (normalized != null) {
                    tags.add(normalized);
                }
            }
        }
        String structureKey = biome.getVanillaDerivativeKey();
        tags.addAll(vanillaTags.isEmpty() ? IrisVanillaBiomeTags.tagsFor(structureKey) : vanillaTags.getOrDefault(structureKey, List.of()));
        return tags;
    }

    private static Map<String, List<String>> directTags(VanillaDataSource source) {
        String root = "data/" + VANILLA_NAMESPACE + TAG_FOLDER;
        Map<String, List<String>> tags = new HashMap<>();
        for (String path : source.list(root)) {
            if (!path.endsWith(JSON_SUFFIX)) {
                continue;
            }
            String tag = VANILLA_NAMESPACE + ":" + path.substring(root.length() + 1, path.length() - JSON_SUFFIX.length());
            Optional<JsonObject> json = read(source, path);
            JsonArray values = json.isEmpty() ? null : json.get().getAsJsonArray("values");
            if (values == null) {
                continue;
            }
            for (JsonElement value : values) {
                String member = value.isJsonObject() ? value.getAsJsonObject().get("id").getAsString() : value.getAsString();
                if (!member.startsWith("#")) {
                    tags.computeIfAbsent(namespaced(member), ignored -> new ArrayList<>()).add(tag);
                }
            }
        }
        return tags;
    }

    private static Optional<JsonObject> read(VanillaDataSource source, String biomeKeyOrPath) {
        String path = biomeKeyOrPath.startsWith("data/") ? biomeKeyOrPath : biomePath(biomeKeyOrPath);
        if (path == null) {
            return Optional.empty();
        }
        return source.read(path).map(bytes -> JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject());
    }

    private static String biomePath(String biomeKey) {
        if (biomeKey == null || !VanillaResourceIdentifier.validIdentifier(biomeKey)) {
            return null;
        }
        int separator = biomeKey.indexOf(':');
        return "data/" + biomeKey.substring(0, separator) + BIOME_FOLDER + biomeKey.substring(separator + 1) + JSON_SUFFIX;
    }

    private static String namespaced(String key) {
        String trimmed = key.trim().toLowerCase(Locale.ROOT);
        return trimmed.indexOf(':') < 0 ? VANILLA_NAMESPACE + ":" + trimmed : trimmed;
    }

    private static String tagId(String tag) {
        if (tag == null || tag.isBlank()) {
            return null;
        }
        String trimmed = tag.trim();
        String normalized = namespaced(trimmed.startsWith("#") ? trimmed.substring(1) : trimmed);
        return VanillaResourceIdentifier.validIdentifier(normalized) ? normalized : null;
    }

    private static void diagnose(PackCompileContext context, String resource, String message) {
        context.diagnostics().add(new ExportDiagnostic(ExportDiagnostic.Severity.WARNING, ExportLimit.VANILLA_BIOME_DATA, resource, message));
    }

    public record Definitions(List<BiomeDef> biomes, List<JsonResource> tags) {
        public Definitions {
            biomes = List.copyOf(biomes);
            tags = List.copyOf(tags);
        }
    }
}
