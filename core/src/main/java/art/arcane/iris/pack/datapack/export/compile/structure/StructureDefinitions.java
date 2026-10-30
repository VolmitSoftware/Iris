package art.arcane.iris.pack.datapack.export.compile.structure;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.VanillaDataSource;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

final class StructureDefinitions {
    private static final String STRUCTURE_FOLDER = "/worldgen/structure/";
    private static final String VANILLA_STRUCTURES = "data/minecraft/worldgen/structure";
    private static final String JSON_SUFFIX = ".json";

    private final SortedMap<String, byte[]> bundled;
    private final Optional<VanillaDataSource> vanilla;

    StructureDefinitions(SortedMap<String, byte[]> bundledFiles, Optional<VanillaDataSource> vanilla) {
        this.vanilla = Objects.requireNonNull(vanilla);
        bundled = new TreeMap<>();
        for (Map.Entry<String, byte[]> file : bundledFiles.entrySet()) {
            if (key(file.getKey()) != null) {
                bundled.put(file.getKey(), file.getValue());
            }
        }
    }

    static StructureDefinitions of(DatapackBundle bundle, DataVersion target) {
        return new StructureDefinitions(bundle.files(), vanillaFor(target));
    }

    static String path(String key) {
        int separator = key.indexOf(':');
        return "data/" + key.substring(0, separator) + STRUCTURE_FOLDER + key.substring(separator + 1) + JSON_SUFFIX;
    }

    boolean vanillaAvailable() {
        return vanilla.isPresent();
    }

    SortedSet<String> keys() {
        SortedSet<String> keys = new TreeSet<>();
        for (String path : bundled.keySet()) {
            keys.add(key(path));
        }
        if (vanilla.isPresent()) {
            for (String path : vanilla.get().list(VANILLA_STRUCTURES)) {
                String key = key(path);
                if (key != null) {
                    keys.add(key);
                }
            }
        }
        return keys;
    }

    Optional<JsonObject> read(String key) {
        String path = path(key);
        byte[] bytes = bundled.get(path);
        if (bytes == null && vanilla.isPresent()) {
            bytes = vanilla.get().read(path).orElse(null);
        }
        if (bytes == null) {
            return Optional.empty();
        }
        try {
            JsonElement json = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
            if (!json.isJsonObject()) {
                throw new IllegalStateException("Structure '" + key + "' at " + path + " is not a JSON object");
            }
            return Optional.of(json.getAsJsonObject());
        } catch (JsonParseException exception) {
            throw new IllegalStateException("Structure '" + key + "' at " + path + " is not valid JSON", exception);
        }
    }

    private static Optional<VanillaDataSource> vanillaFor(DataVersion target) {
        if (!IrisPlatforms.isBound()) {
            return Optional.empty();
        }
        IrisPlatform platform = IrisPlatforms.get();
        boolean running = DataVersion.forMinecraftVersion(platform.minecraftVersion()) == target;
        return running ? Optional.ofNullable(platform.vanillaData()) : Optional.empty();
    }

    private static String key(String path) {
        if (!path.startsWith("data/") || !path.endsWith(JSON_SUFFIX)) {
            return null;
        }
        int separator = path.indexOf('/', "data/".length());
        if (separator < 0 || !path.startsWith(STRUCTURE_FOLDER, separator)) {
            return null;
        }
        String local = path.substring(separator + STRUCTURE_FOLDER.length(), path.length() - JSON_SUFFIX.length());
        return local.isEmpty() ? null : path.substring("data/".length(), separator) + ":" + local;
    }
}
