package art.arcane.iris.pack.datapack.export.emit;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.ir.BinaryResource;
import art.arcane.iris.pack.datapack.export.ir.BiomeDef;
import art.arcane.iris.pack.datapack.export.ir.ClimateLayout;
import art.arcane.iris.pack.datapack.export.ir.DimensionDef;
import art.arcane.iris.pack.datapack.export.ir.ExportIr;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.JsonResource;
import art.arcane.iris.pack.datapack.export.ir.NoiseDef;
import art.arcane.iris.pack.datapack.export.lower.ExportCapabilities;
import art.arcane.iris.pack.datapack.export.lower.VanillaBounds;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

abstract class BaseWorldgenJsonWriter implements WorldgenJsonWriter {
    static final int MAX_NESTING = 255;
    private static final String PACK_ICON = "pack.png";
    private static final String PACK_ICON_RESOURCE = "/iris/datapack/pack.png";
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Set<String> DIMENSION_TYPE_KEYS = Set.of("has_skylight", "has_ceiling", "has_ender_dragon_fight", "coordinate_scale", "min_y",
            "height", "logical_height", "infiniburn", "ambient_light", "monster_spawn_light_level", "monster_spawn_block_light_limit", "has_fixed_time",
            "skybox", "cardinal_light", "attributes", "timelines", "default_clock");
    private static final Set<String> REQUIRED_DIMENSION_TYPE_KEYS = Set.of("has_skylight", "has_ceiling", "has_ender_dragon_fight", "coordinate_scale",
            "infiniburn", "ambient_light", "monster_spawn_light_level", "monster_spawn_block_light_limit");
    private static final Set<String> REQUIRED_BIOME_KEYS = Set.of("has_precipitation", "temperature", "downfall", "effects", "carvers", "features");
    private static final Set<String> NOISE_CONSUMERS = Set.of("minecraft:noise", "minecraft:shifted_noise", "minecraft:shift_a", "minecraft:shift_b",
            "minecraft:shift", "minecraft:noise_threshold");
    private static final String VANILLA_NETHER = "{\"type\":\"minecraft:the_nether\",\"generator\":{\"type\":\"minecraft:noise\",\"biome_source\":"
            + "{\"type\":\"minecraft:multi_noise\",\"preset\":\"minecraft:nether\"},\"settings\":\"minecraft:nether\"}}";
    private static final String VANILLA_END = "{\"type\":\"minecraft:the_end\",\"generator\":{\"type\":\"minecraft:noise\",\"biome_source\":"
            + "{\"type\":\"minecraft:the_end\"},\"settings\":\"minecraft:end\"}}";

    private final DataVersion target;
    private final Set<String> biomeKeys;

    BaseWorldgenJsonWriter(DataVersion target, Set<String> biomeKeys) {
        this.target = target;
        this.biomeKeys = Set.copyOf(biomeKeys);
    }

    @Override
    public final SortedMap<String, byte[]> write(ExportIr ir) {
        if (ir.target() != target) {
            throw new IllegalArgumentException("The " + target.getVersion() + " writer cannot write an export for " + ir.target().getVersion());
        }
        String namespace = ir.namespace();
        String dimension = ir.dimension().name();
        DensityFunctionEmitter emitter = DensityFunctionEmitter.forTarget(ExportCapabilities.forTarget(target), namespace, VanillaBounds.of(namespace, ir.noises()));
        Roots roots = new Roots(new FunctionTable(namespace), emitter, dimension);
        SortedMap<String, JsonElement> files = new TreeMap<>();
        files.put("pack.mcmeta", packMetadata(ir));
        for (NoiseDef noise : ir.noises()) {
            files.put(path(namespace, "worldgen/noise", noise.id()), noise(noise));
        }
        JsonElement rule = materialRule(ir);
        JsonObject settings = noiseSettings(ir, roots, rule);
        NestingSplitter.split(roots.table(), MAX_NESTING);
        for (Map.Entry<String, JsonElement> function : roots.table().files().entrySet()) {
            files.put(path(namespace, "worldgen/density_function", function.getKey()), function.getValue());
        }
        files.put(path(namespace, "worldgen/noise_settings", dimension), settings);
        files.putAll(materialFiles(ir, rule));
        List<JsonElement> noiseConsumers = new ArrayList<>(roots.table().files().values());
        noiseConsumers.add(settings);
        noiseConsumers.add(rule);
        requireRegisteredNoises(ir, noiseConsumers);
        for (BiomeDef biome : ir.biomes()) {
            files.put(path(namespace, "worldgen/biome", biome.id()), validatedBiome(biome));
        }
        files.put(path(namespace, "dimension_type", dimension), dimensionType(ir.dimension()));
        addModeFiles(ir, levelStem(ir), files);
        for (JsonResource resource : ir.jsonResources()) {
            requireUnclaimed(files.keySet(), resource.path());
            files.put(resource.path(), resource.json());
        }
        SortedMap<String, byte[]> bytes = serialize(files);
        for (BinaryResource resource : ir.binaryResources()) {
            requireUnclaimed(bytes.keySet(), resource.path());
            bytes.put(resource.path(), resource.bytes());
        }
        requireUnclaimed(bytes.keySet(), PACK_ICON);
        bytes.put(PACK_ICON, PackIcon.BYTES.clone());
        return bytes;
    }

    protected abstract JsonObject noise(NoiseDef noise);

    protected abstract JsonObject noiseSettings(ExportIr ir, Roots roots, JsonElement materialRule);

    protected abstract JsonElement materialRule(ExportIr ir);

    protected abstract Map<String, JsonElement> materialFiles(ExportIr ir, JsonElement rule);

    final DataVersion target() {
        return target;
    }

    final JsonPrimitive number(double value) {
        return JsonNumbers.constant(value, target);
    }

    static String path(String namespace, String registry, String id) {
        return "data/" + namespace + "/" + registry + "/" + id + ".json";
    }

    static JsonArray span(double min, double max, DataVersion target) {
        JsonArray span = new JsonArray(2);
        span.add(JsonNumbers.constant(min, target));
        span.add(JsonNumbers.constant(max, target));
        return span;
    }

    private JsonObject packMetadata(ExportIr ir) {
        JsonObject pack = new JsonObject();
        pack.addProperty("description", "Iris worldgen export " + ir.namespace() + ":" + ir.dimension().name() + ". Keep Default selected");
        pack.addProperty("min_format", target.getPackFormat());
        pack.addProperty("max_format", target.getPackFormat());
        JsonObject metadata = new JsonObject();
        metadata.add("pack", pack);
        return metadata;
    }

    private static void addModeFiles(ExportIr ir, JsonObject stem, SortedMap<String, JsonElement> files) {
        String id = ir.namespace() + ":" + ir.dimension().name();
        switch (ir.mode()) {
            case PRESET -> {
                JsonObject dimensions = new JsonObject();
                dimensions.add("minecraft:overworld", stem);
                dimensions.add("minecraft:the_nether", JsonParser.parseString(VANILLA_NETHER));
                dimensions.add("minecraft:the_end", JsonParser.parseString(VANILLA_END));
                JsonObject preset = new JsonObject();
                preset.add("dimensions", dimensions);
                files.put(path(ir.namespace(), "worldgen/world_preset", ir.dimension().name()), preset);
                JsonArray values = new JsonArray(1);
                values.add(id);
                JsonObject tag = new JsonObject();
                tag.add("values", values);
                files.put("data/minecraft/tags/worldgen/world_preset/normal.json", tag);
            }
            case DIMENSION -> files.put(path(ir.namespace(), "dimension", ir.dimension().name()), stem);
            case OVERWORLD -> files.put("data/minecraft/dimension/overworld.json", stem);
        }
    }

    private JsonObject validatedBiome(BiomeDef biome) {
        JsonObject json = biome.json();
        requireKeys(json, biomeKeys, REQUIRED_BIOME_KEYS, "Biome '" + biome.id() + "'");
        return json;
    }

    private JsonObject dimensionType(DimensionDef dimension) {
        JsonObject type = dimension.dimensionType();
        type.addProperty("min_y", dimension.minY());
        type.addProperty("height", dimension.height());
        type.addProperty("logical_height", dimension.logicalHeight());
        requireKeys(type, DIMENSION_TYPE_KEYS, REQUIRED_DIMENSION_TYPE_KEYS, "Dimension type '" + dimension.name() + "'");
        return type;
    }

    private JsonObject levelStem(ExportIr ir) {
        String id = ir.namespace() + ":" + ir.dimension().name();
        if (ir.climate().entries().isEmpty()) {
            throw new IllegalArgumentException("Export '" + id + "' has no climate entries for its biome source");
        }
        JsonArray biomes = new JsonArray(ir.climate().entries().size());
        for (ClimateLayout.ClimateEntry entry : ir.climate().entries()) {
            JsonObject parameters = new JsonObject();
            for (String axis : List.of("temperature", "humidity", "continentalness", "erosion", "depth", "weirdness")) {
                parameters.add(axis, number(0.0));
            }
            parameters.add(switch (entry.axis()) {
                case TEMPERATURE -> "temperature";
                case HUMIDITY -> "humidity";
                case DEPTH -> "depth";
            }, span(entry.min(), entry.max(), target));
            parameters.add("offset", number(0.0));
            JsonObject point = new JsonObject();
            point.addProperty("biome", entry.biomeId());
            point.add("parameters", parameters);
            biomes.add(point);
        }
        JsonObject source = new JsonObject();
        source.addProperty("type", "minecraft:multi_noise");
        source.add("biomes", biomes);
        JsonObject generator = new JsonObject();
        generator.addProperty("type", "minecraft:noise");
        generator.addProperty("settings", id);
        generator.add("biome_source", source);
        JsonObject stem = new JsonObject();
        stem.addProperty("type", id);
        stem.add("generator", generator);
        return stem;
    }

    private static void requireRegisteredNoises(ExportIr ir, List<JsonElement> consumers) {
        Set<String> registered = new HashSet<>(ir.noises().size());
        for (NoiseDef noise : ir.noises()) {
            registered.add(ir.namespace() + ":" + noise.id());
        }
        Set<String> missing = new TreeSet<>();
        for (JsonElement consumer : consumers) {
            collectUnregisteredNoises(consumer, ir.namespace(), registered, missing);
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Export references noises that are not registered worldgen/noise files: " + String.join(", ", missing));
        }
    }

    private static void collectUnregisteredNoises(JsonElement element, String namespace, Set<String> registered, Set<String> missing) {
        if (element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray()) {
                collectUnregisteredNoises(item, namespace, registered, missing);
            }
            return;
        }
        if (!element.isJsonObject()) {
            return;
        }
        JsonObject object = element.getAsJsonObject();
        JsonElement type = object.get("type");
        JsonElement noise = object.get("noise");
        if (type != null && type.isJsonPrimitive() && NOISE_CONSUMERS.contains(type.getAsString())) {
            if (noise == null || !noise.isJsonPrimitive() || !noise.getAsJsonPrimitive().isString()) {
                missing.add("inline noise in " + type.getAsString());
            } else if (!(noise.getAsString().startsWith(namespace + ":") ? registered.contains(noise.getAsString()) : noise.getAsString().startsWith("minecraft:"))) {
                missing.add(noise.getAsString());
            }
        }
        for (Map.Entry<String, JsonElement> member : object.entrySet()) {
            collectUnregisteredNoises(member.getValue(), namespace, registered, missing);
        }
    }

    private static void requireUnclaimed(Set<String> generated, String path) {
        if (generated.contains(path)) {
            throw new IllegalArgumentException("Export resource '" + path + "' collides with a file the writer generates");
        }
    }

    private static void requireKeys(JsonObject json, Set<String> allowed, Set<String> required, String resource) {
        Set<String> unknown = new TreeSet<>(json.keySet());
        unknown.removeAll(allowed);
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException(resource + " has keys the codec ignores: " + String.join(", ", unknown));
        }
        Set<String> absent = new TreeSet<>(required);
        absent.removeAll(json.keySet());
        if (!absent.isEmpty()) {
            throw new IllegalArgumentException(resource + " is missing required keys: " + String.join(", ", absent));
        }
    }

    private static SortedMap<String, byte[]> serialize(SortedMap<String, JsonElement> files) {
        SortedMap<String, byte[]> bytes = new TreeMap<>();
        for (Map.Entry<String, JsonElement> file : files.entrySet()) {
            int depth = NestingSplitter.depth(file.getValue());
            if (depth > MAX_NESTING) {
                throw new IllegalStateException("Export file '" + file.getKey() + "' nests " + depth + " levels; the limit is " + MAX_NESTING);
            }
            bytes.put(file.getKey(), (GSON.toJson(sorted(file.getValue())) + "\n").getBytes(StandardCharsets.UTF_8));
        }
        return bytes;
    }

    private static JsonElement sorted(JsonElement element) {
        if (element.isJsonObject()) {
            JsonObject sorted = new JsonObject();
            for (Map.Entry<String, JsonElement> member : new TreeMap<>(element.getAsJsonObject().asMap()).entrySet()) {
                sorted.add(member.getKey(), sorted(member.getValue()));
            }
            return sorted;
        }
        if (element.isJsonArray()) {
            JsonArray sorted = new JsonArray(element.getAsJsonArray().size());
            for (JsonElement item : element.getAsJsonArray()) {
                sorted.add(sorted(item));
            }
            return sorted;
        }
        return element;
    }

    private static final class PackIcon {
        private static final byte[] BYTES = read();

        private PackIcon() {
        }

        private static byte[] read() {
            try (InputStream input = BaseWorldgenJsonWriter.class.getResourceAsStream(PACK_ICON_RESOURCE)) {
                if (input == null) {
                    throw new IllegalStateException("Datapack icon resource " + PACK_ICON_RESOURCE + " is missing");
                }
                return input.readAllBytes();
            } catch (IOException exception) {
                throw new UncheckedIOException("Datapack icon resource " + PACK_ICON_RESOURCE + " could not be read", exception);
            }
        }
    }

    static final class Roots {
        private final FunctionTable table;
        private final DensityFunctionEmitter emitter;
        private final String dimension;

        private Roots(FunctionTable table, DensityFunctionEmitter emitter, String dimension) {
            this.table = table;
            this.emitter = emitter;
            this.dimension = dimension;
        }

        FunctionTable table() {
            return table;
        }

        JsonElement inline(String name, Field field) {
            JsonElement emitted = emitter.emit(field, table);
            if (emitted.isJsonPrimitive()) {
                return emitted;
            }
            return new JsonPrimitive(table.register(dimension + "/" + name, emitted));
        }

        String named(String name, Field field) {
            JsonElement emitted = emitter.emit(field, table);
            if (emitted.isJsonPrimitive() && emitted.getAsJsonPrimitive().isString()) {
                emitted = localBody(emitted.getAsString(), name);
            }
            return table.registerNamed(dimension + "/" + name, emitted);
        }

        private JsonElement localBody(String reference, String name) {
            String prefix = table.namespace() + ":";
            JsonElement body = reference.startsWith(prefix) ? table.files().get(reference.substring(prefix.length())) : null;
            if (body == null) {
                throw new IllegalStateException("Router slot '" + name + "' of dimension '" + dimension + "' is the external density function '" + reference
                        + "' and cannot be written under its own name");
            }
            return body;
        }

        String registered(String name, Field field) {
            JsonElement emitted = emitter.emit(field, table);
            if (emitted.isJsonPrimitive() && emitted.getAsJsonPrimitive().isString()) {
                return emitted.getAsString();
            }
            return table.register(dimension + "/" + name, emitted);
        }
    }
}
