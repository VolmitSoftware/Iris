package art.arcane.iris.configuration;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.volmlib.util.config.ConfigComments;
import art.arcane.volmlib.util.config.ConfigExposePolicy;
import art.arcane.volmlib.util.config.ConfigJson;
import art.arcane.volmlib.util.config.TomlCodec;
import art.arcane.volmlib.util.config.TomlDocumentEditor;
import art.arcane.volmlib.util.io.AtomicFileIO;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads and writes the Iris settings file. Settings live in iris.toml so each key can carry a comment.
 */
public final class IrisSettingsFile {
    public static final String FILE_NAME = "iris.toml";
    private static final String SOURCE_TAG = "iris";

    private IrisSettingsFile() {
    }

    public static File location() {
        return IrisPlatforms.get().dataFile(FILE_NAME);
    }

    public static void write(IrisSettings settings, File file) throws IOException {
        requireWritable(file);
        AtomicFileIO.writeString(file.toPath(), documentWithSettings(file, settings));
    }

    static IrisSettings load() {
        File file = location();
        if (file.exists()) {
            return loadExisting(file);
        }

        IrisSettings defaults = new IrisSettings();
        rewrite(file, defaults);
        return defaults;
    }

    static IrisSettings parse(String toml) throws IOException {
        IrisSettings parsed = TomlCodec.fromToml(toml, IrisSettings.class);
        if (parsed == null) {
            throw new IOException("Iris settings did not contain a table");
        }
        parsed.fillMissingSections();
        return parsed;
    }

    static String render(IrisSettings settings) {
        return TomlCodec.toToml(settings, SOURCE_TAG, ConfigExposePolicy.ALL, ConfigComments.ANNOTATED_ONLY);
    }

    private static IrisSettings loadExisting(File file) {
        String raw;
        IrisSettings loaded;
        try {
            raw = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            loaded = parse(raw);
        } catch (Throwable failure) {
            // reportError reads the settings again while they are still loading, so only log the line.
            IrisLogging.error("Configuration Error in " + FILE_NAME + "! " + rootCause(failure)
                    + ". Using defaults until the file is fixed.");
            return new IrisSettings();
        }

        if (!containsEverySetting(raw, loaded)) {
            rewrite(file, loaded);
        }
        return loaded;
    }

    private static boolean containsEverySetting(String raw, IrisSettings loaded) {
        try {
            JsonObject document = parseDocument(raw);
            return settingPaths(settingsTree(loaded)).stream().allMatch(path -> valueAt(document, path) != null);
        } catch (IOException unreadable) {
            return false;
        }
    }

    private static void requireWritable(File file) throws AccessDeniedException {
        if (file.exists() && !file.canWrite()) {
            throw new AccessDeniedException(file.getAbsolutePath());
        }
    }

    private static String documentWithSettings(File file, IrisSettings settings) {
        if (!file.exists()) {
            return render(settings);
        }
        try {
            return applySettings(Files.readString(file.toPath(), StandardCharsets.UTF_8), settingsTree(settings));
        } catch (IOException | RuntimeException uneditable) {
            return render(settings);
        }
    }

    private static String applySettings(String document, JsonObject settings) throws IOException {
        JsonObject written = parseDocument(document);
        String updated = document;
        for (List<String> path : settingPaths(settings)) {
            JsonElement value = valueAt(settings, path);
            if (value.equals(valueAt(written, path))) {
                continue;
            }
            updated = TomlDocumentEditor.set(updated, path, value);
        }
        return updated;
    }

    /**
     * Each table's own settings come out last to first: the editor inserts a missing setting directly below its
     * table header, so this order leaves inserted settings in their documented order.
     */
    private static List<List<String>> settingPaths(JsonObject table) {
        List<List<String>> paths = new ArrayList<>();
        collectSettingPaths(table, List.of(), paths);
        return paths;
    }

    private static void collectSettingPaths(JsonObject table, List<String> tablePath, List<List<String>> paths) {
        List<List<String>> ownSettings = new ArrayList<>();
        List<Map.Entry<String, JsonElement>> childTables = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : table.entrySet()) {
            if (entry.getValue().isJsonObject()) {
                childTables.add(entry);
            } else {
                ownSettings.add(childPath(tablePath, entry.getKey()));
            }
        }
        paths.addAll(ownSettings.reversed());
        for (Map.Entry<String, JsonElement> child : childTables) {
            collectSettingPaths(child.getValue().getAsJsonObject(), childPath(tablePath, child.getKey()), paths);
        }
    }

    private static List<String> childPath(List<String> parent, String key) {
        List<String> path = new ArrayList<>(parent);
        path.add(key);
        return List.copyOf(path);
    }

    private static JsonObject settingsTree(IrisSettings settings) {
        return ConfigJson.NORMAL.toJsonTree(settings).getAsJsonObject();
    }

    private static JsonElement valueAt(JsonObject document, List<String> path) {
        JsonElement current = document;
        for (String key : path) {
            if (!current.isJsonObject()) {
                return null;
            }
            current = current.getAsJsonObject().get(key);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private static JsonObject parseDocument(String toml) throws IOException {
        JsonElement parsed = TomlCodec.toJsonElement(toml);
        if (parsed == null || !parsed.isJsonObject()) {
            throw new IOException("Iris settings did not contain a table");
        }
        return parsed.getAsJsonObject();
    }

    private static boolean rewrite(File file, IrisSettings settings) {
        try {
            write(settings, file);
            return true;
        } catch (IOException failure) {
            IrisLogging.reportError("Could not rewrite " + file.getAbsolutePath()
                    + "; settings changes made in game will not survive a restart.", failure);
            return false;
        }
    }

    private static String rootCause(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return root.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }
}
