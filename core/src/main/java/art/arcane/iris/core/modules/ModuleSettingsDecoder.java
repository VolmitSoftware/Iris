/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.core.modules;

import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;
import art.arcane.volmlib.util.json.JSONTokener;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.volmit.config.Level;
import com.volmit.IrisFeature;
import com.volmit.block.BlockState;
import com.volmit.config.Snippet;
import com.volmit.host.FeatureCatalog;
import com.volmit.host.FeatureDescriptor;
import com.volmit.host.OptionsMetadata;
import com.volmit.resource.ResourceId;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Reads the module settings a dimension, region or biome declares: each settings feature of the pack at
 * that target, under the key the feature declares. Settings are created with their no-argument
 * constructor and the JSON is read into them under IrisApi's {@link DecodingContract}, so an omitted or null option
 * keeps its default. A {@link Snippet}
 * type may be written as {@code "snippet/<name>"} or, as Iris's own snippets are, {@code "snippet/<snippet folder>/<name>"},
 * both read from {@code snippet/<snippet folder>/<name>.json}. IrisApi's value types ({@code BlockSpec},
 * {@code NoiseStyle} and the rest) are read like settings, an {@code Object} as Gson reads any JSON value, and a
 * {@link com.volmit.config.JsonForms} class through its codec ({@link JsonFormsCodecs}).
 * <p>
 * Settings are written in the one shape their class declares: an object of the class's options or, when the feature
 * names a {@code root} option, exactly that option's value. Iris's own classes that built-in settings hold (see
 * {@link IrisModuleHost#isHostType}) are read by Iris's pack Gson, exactly as Iris reads them elsewhere.
 */
public final class ModuleSettingsDecoder {
    private static final String SNIPPET_PREFIX = "snippet/";
    private static final String SNIPPET_FOLDER = "snippet";
    private static final String JSON_EXTENSION = ".json";
    private static final String PLACEMENT_RESOURCE = "object placement";

    private final Map<Level, List<FeatureDescriptor<?>>> settings = new EnumMap<>(Level.class);
    private final Map<Level, Set<String>> keys = new EnumMap<>(Level.class);
    private final Map<String, ModuleSettings> placementSettings = new ConcurrentHashMap<>();
    private final Path packFolder;
    private final Gson gson;
    private final Supplier<Gson> irisGson;

    /** @param irisGson the pack's own Gson, which reads Iris's classes */
    ModuleSettingsDecoder(FeatureCatalog catalog, Path packFolder, Supplier<Gson> irisGson) {
        this.packFolder = packFolder;
        this.irisGson = irisGson;
        PackResourceIds packIds = new PackResourceIds(packFolder.toFile());
        this.gson = DecodingContract.appliedTo(new GsonBuilder())
                .registerTypeAdapter(BlockState.class, textAdapter(BlockState::parse, BlockState::key))
                .registerTypeAdapter(ResourceId.class, textAdapter(text -> resourceIdOf(packIds, text), ResourceId::toString))
                .registerTypeAdapterFactory(new HostTypes())
                .registerTypeAdapterFactory(new SnippetReferences())
                .registerTypeAdapterFactory(new JsonFormsCodecs())
                .create();
        for (Level target : Level.values()) {
            settings.put(target, settingsAt(catalog, target));
            keys.put(target, keysAt(catalog, target));
        }
    }

    /** A resource id as written: {@code namespace:path}, or a pack key such as {@code desert/dunes}, which names a resource of the pack. */
    private static ResourceId resourceIdOf(PackResourceIds packIds, String text) {
        return text.indexOf(':') >= 0 ? ResourceId.parse(text) : packIds.of(text);
    }

    /** Every JSON key the pack's modules read at {@code target}: the keys of their features, object generators' included. */
    public Set<String> keys(Level target) {
        return keys.get(target);
    }

    /**
     * Decodes the settings {@code json} declares for {@code target}.
     *
     * @param resource names the file in errors, such as {@code biome desert/dunes}
     * @throws IllegalArgumentException when a value does not decode into its settings type
     */
    public ModuleSettings decode(Level target, JSONObject json, String resource) {
        Map<Class<? extends IrisFeature>, IrisFeature> values = new LinkedHashMap<>();
        for (FeatureDescriptor<?> feature : settings.get(target)) {
            if (json.has(feature.key())) {
                values.put(feature.type(), read(feature, json.get(feature.key()), resource));
            }
        }
        return ModuleSettings.of(values);
    }

    /**
     * The settings the object placement written as {@code json} declares at {@link Level#PLACEMENT}. A placement is placed
     * many times and always reads the same, so each text is decoded once for as long as the decoder lives.
     *
     * @throws IllegalArgumentException when a value does not decode into its settings type
     */
    public ModuleSettings decodePlacement(String json) {
        return placementSettings.computeIfAbsent(json, text -> decode(Level.PLACEMENT, new JSONObject(text), PLACEMENT_RESOURCE));
    }

    /**
     * Reads the JSON of a module resource file into a new {@code type}, created with its no-argument constructor so an
     * omitted option keeps its default. Iris's own classes, which built-in resource kinds may read into, are read by
     * the pack's Gson like every Iris resource.
     *
     * @throws RuntimeException when the JSON does not decode into {@code type}
     */
    public <T> T decodeResource(Class<T> type, String json) {
        T decoded = gson.fromJson(json, type);
        if (decoded == null) {
            throw new JsonParseException("the file holds null");
        }
        return decoded;
    }

    /**
     * The JSON that declares {@code values} at {@code target}: each settings feature under its key, in the shape the
     * feature is written in (a rooted feature as its root option's value), the inverse of {@link #decode}.
     */
    public JSONObject encode(Level target, ModuleSettings values) {
        JSONObject json = new JSONObject();
        for (FeatureDescriptor<?> feature : settings.get(target)) {
            values.find(feature.type()).ifPresent(value -> json.put(feature.key(), writtenShape(feature, value)));
        }
        return json;
    }

    private Object writtenShape(FeatureDescriptor<?> feature, IrisFeature value) {
        JsonElement settingsTree = gson.toJsonTree(value, feature.type());
        JsonElement written = feature.root().map(root -> settingsTree.getAsJsonObject().get(root)).orElse(settingsTree);
        return new JSONTokener(gson.toJson(written)).nextValue();
    }

    private IrisFeature read(FeatureDescriptor<?> feature, Object value, String resource) {
        try {
            requireShape(feature, value);
            IrisFeature decoded = gson.fromJson(settingsJson(feature, value), feature.type());
            if (decoded == null) {
                throw new JsonParseException("the value is null");
            }
            return decoded;
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("Cannot read settings " + feature.id() + " under \"" + feature.key() + "\" in "
                    + resource + ": " + failure.getMessage(), failure);
        }
    }

    /** The settings object the value stands for: the value itself, or the root option holding it. */
    private static String settingsJson(FeatureDescriptor<?> feature, Object value) {
        return feature.root()
                .map(root -> new JSONObject().put(root, value).toString())
                .orElseGet(() -> JSONObject.valueToString(value));
    }

    private static void requireShape(FeatureDescriptor<?> feature, Object value) {
        if (feature.root().isPresent()) {
            requireRootValue(feature, feature.root().get(), value);
            return;
        }
        requireObjectOrSnippet(feature, value);
    }

    private static void requireObjectOrSnippet(FeatureDescriptor<?> feature, Object value) {
        if (value instanceof JSONObject || (value instanceof String && feature.type().isAnnotationPresent(Snippet.class))) {
            return;
        }
        throw new JsonParseException("write the settings as an object, {\"<option>\": ...}");
    }

    private static void requireRootValue(FeatureDescriptor<?> feature, String root, Object value) {
        if (JSONObject.NULL.equals(value)) {
            throw new JsonParseException("the value is null; write " + describeRootShape(feature, root));
        }
        if (isListOption(feature, root) && !(value instanceof JSONArray)) {
            throw new JsonParseException("write " + describeRootShape(feature, root) + ", not an object of options");
        }
    }

    private static boolean isListOption(FeatureDescriptor<?> feature, String root) {
        Class<?> type = rootOption(feature, root).field().getType();
        return Collection.class.isAssignableFrom(type) || type.isArray();
    }

    private static String describeRootShape(FeatureDescriptor<?> feature, String root) {
        return isListOption(feature, root) ? "a list, [...], of the " + root : "the " + root + " directly";
    }

    private static OptionsMetadata.Option rootOption(FeatureDescriptor<?> feature, String root) {
        return feature.options().options().stream()
                .filter(option -> option.name().equals(root))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Root " + root + " is no option of " + feature.type().getName()));
    }

    private static List<FeatureDescriptor<?>> settingsAt(FeatureCatalog catalog, Level target) {
        return catalog.settings().stream()
                .filter(feature -> feature.targets().contains(target))
                .toList();
    }

    private static Set<String> keysAt(FeatureCatalog catalog, Level target) {
        Set<String> found = new TreeSet<>();
        catalog.settings().stream()
                .filter(feature -> feature.targets().contains(target))
                .forEach(feature -> found.add(feature.key()));
        return Set.copyOf(found);
    }

    private static <T> TypeAdapter<T> textAdapter(Function<String, T> parse, Function<T, String> write) {
        return new TypeAdapter<T>() {
            @Override
            public void write(JsonWriter out, T value) throws IOException {
                out.value(write.apply(value));
            }

            @Override
            public T read(JsonReader in) throws IOException {
                return parse.apply(in.nextString());
            }
        }.nullSafe();
    }

    /** Reads and writes Iris's own classes with the pack's Gson, asked at each use so a hotloaded pack's applies. */
    private final class HostTypes implements TypeAdapterFactory {
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            if (!IrisModuleHost.isHostType(type.getRawType())) {
                return null;
            }
            return new TypeAdapter<T>() {
                @Override
                public void write(JsonWriter out, T value) throws IOException {
                    irisGson.get().getAdapter(type).write(out, value);
                }

                @Override
                public T read(JsonReader in) throws IOException {
                    return irisGson.get().getAdapter(type).read(in);
                }
            };
        }
    }

    /** Lets a {@link Snippet} type be written as a reference to a snippet file. */
    private final class SnippetReferences implements TypeAdapterFactory {
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            Snippet snippet = type.getRawType().getAnnotation(Snippet.class);
            if (snippet == null) {
                return null;
            }
            TypeAdapter<T> delegate = gson.getDelegateAdapter(this, type);
            return new TypeAdapter<T>() {
                @Override
                public void write(JsonWriter out, T value) throws IOException {
                    delegate.write(out, value);
                }

                @Override
                public T read(JsonReader in) throws IOException {
                    if (in.peek() != JsonToken.STRING) {
                        return delegate.read(in);
                    }
                    return delegate.fromJson(readSnippet(snippet.value(), in.nextString()));
                }
            };
        }
    }

    /** The file name below the snippet folder: {@code snippet/<name>} and {@code snippet/<snippet folder>/<name>} name the same file. */
    private static String snippetName(String snippetFolder, String reference) {
        String name = reference.substring(SNIPPET_PREFIX.length());
        String folderPrefix = snippetFolder + "/";
        return name.startsWith(folderPrefix) ? name.substring(folderPrefix.length()) : name;
    }

    private String readSnippet(String snippetFolder, String reference) {
        if (!reference.startsWith(SNIPPET_PREFIX)) {
            throw new JsonParseException("\"" + reference + "\" is neither an object nor a snippet reference; write the object,"
                    + " or snippet/<name> for the file " + SNIPPET_FOLDER + "/" + snippetFolder + "/<name>" + JSON_EXTENSION);
        }
        Path folder = packFolder.resolve(SNIPPET_FOLDER).resolve(snippetFolder).normalize();
        Path file = folder.resolve(snippetName(snippetFolder, reference) + JSON_EXTENSION).normalize();
        if (!file.startsWith(folder) || !Files.isRegularFile(file)) {
            throw new JsonParseException("Snippet " + reference + " has no file " + packFolder.relativize(file));
        }
        try {
            return Files.readString(file);
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot read snippet " + packFolder.relativize(file), failure);
        }
    }
}
