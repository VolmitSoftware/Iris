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

import art.arcane.volmlib.util.json.JSONObject;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.volmit.FeatureAttachment.Target;
import com.volmit.IrisFeature;
import com.volmit.block.BlockState;
import com.volmit.config.OptionsMetadata;
import com.volmit.config.Snippet;
import com.volmit.host.FeatureCatalog;
import com.volmit.host.FeatureDescriptor;
import com.volmit.host.ObjectRegistration;
import com.volmit.resource.ResourceId;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Reads the module settings a dimension, region or biome declares: each settings feature of the pack at
 * that target, under its id or, for built-ins, its alias. Settings are created with their no-argument
 * constructor and the JSON is read into them, so an omitted option keeps its default. A {@link Snippet}
 * type may be written as {@code "snippet/<name>"} or, as Iris's own snippets are, {@code "snippet/<snippet folder>/<name>"},
 * both read from {@code snippet/<snippet folder>/<name>.json}. IrisApi's value types ({@code BlockSpec},
 * {@code NoiseStyle} and the rest) are read like settings, and an {@code Object} as Gson reads any JSON value.
 * <p>
 * Settings with exactly one option may be written as that option's value: {@code "snow": 0.5} reads as
 * {@code "snow": {"amount": 0.5}}. Iris's own classes that built-in settings hold (see
 * {@link IrisModuleHost#isHostType}) are read by Iris's pack Gson, exactly as Iris reads them elsewhere.
 */
public final class ModuleSettingsDecoder {
    private static final String SNIPPET_PREFIX = "snippet/";
    private static final String SNIPPET_FOLDER = "snippet";
    private static final String JSON_EXTENSION = ".json";

    private final Map<Target, List<FeatureDescriptor<?>>> settings = new EnumMap<>(Target.class);
    private final Map<Target, Set<String>> keys = new EnumMap<>(Target.class);
    private final Path packFolder;
    private final Gson gson;
    private final Supplier<Gson> irisGson;

    /** @param irisGson the pack's own Gson, which reads Iris's classes */
    ModuleSettingsDecoder(FeatureCatalog catalog, Path packFolder, Supplier<Gson> irisGson) {
        this.packFolder = packFolder;
        this.irisGson = irisGson;
        this.gson = new GsonBuilder()
                .registerTypeAdapter(BlockState.class, textAdapter(BlockState::parse, BlockState::key))
                .registerTypeAdapter(ResourceId.class, textAdapter(ResourceId::parse, ResourceId::toString))
                .registerTypeAdapterFactory(new HostTypes())
                .registerTypeAdapterFactory(new SnippetReferences())
                .create();
        for (Target target : Target.values()) {
            settings.put(target, settingsAt(catalog, target));
            keys.put(target, keysAt(catalog, target));
        }
    }

    /** Every JSON key the pack's modules read at {@code target}: feature ids and built-in aliases, object generators' included. */
    public Set<String> keys(Target target) {
        return keys.get(target);
    }

    /**
     * Decodes the settings {@code json} declares for {@code target}.
     *
     * @param resource names the file in errors, such as {@code biome desert/dunes}
     * @throws IllegalArgumentException when the JSON holds both a feature's alias and its id, or a value
     *     that does not decode into the settings type
     */
    public ModuleSettings decode(Target target, JSONObject json, String resource) {
        Map<Class<? extends IrisFeature>, IrisFeature> values = new LinkedHashMap<>();
        for (FeatureDescriptor<?> feature : settings.get(target)) {
            presentKey(feature, json, resource).ifPresent(key -> values.put(feature.type(), read(feature, json.get(key), key, resource)));
        }
        return ModuleSettings.of(values);
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

    private static Optional<String> presentKey(FeatureDescriptor<?> feature, JSONObject json, String resource) {
        boolean hasKey = json.has(feature.key());
        Optional<String> alias = feature.alias().filter(json::has);
        if (hasKey && alias.isPresent()) {
            throw new IllegalArgumentException(resource + " sets both \"" + alias.get() + "\" and \"" + feature.key()
                    + "\", the alias and the id of settings " + feature.id() + "; keep one");
        }
        return hasKey ? Optional.of(feature.key()) : alias;
    }

    private IrisFeature read(FeatureDescriptor<?> feature, Object value, String key, String resource) {
        try {
            IrisFeature decoded = gson.fromJson(JSONObject.valueToString(asObject(feature, value)), feature.type());
            if (decoded == null) {
                throw new JsonParseException("the value is null");
            }
            return decoded;
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("Cannot read settings " + feature.id() + " under \"" + key + "\" in "
                    + resource + ": " + failure.getMessage(), failure);
        }
    }

    /** A bare value of settings with exactly one option, as the object holding it; anything else unchanged. */
    private static Object asObject(FeatureDescriptor<?> feature, Object value) {
        if (value instanceof JSONObject || isSnippetReference(feature, value)) {
            return value;
        }
        List<OptionsMetadata.Option> options = feature.options().options();
        if (options.size() != 1) {
            throw new JsonParseException("write an object; only settings with exactly one option may be written as that"
                    + " option's value, and these have " + options.size());
        }
        return new JSONObject().put(options.getFirst().name(), value);
    }

    private static boolean isSnippetReference(FeatureDescriptor<?> feature, Object value) {
        return feature.type().isAnnotationPresent(Snippet.class) && value instanceof String text && text.startsWith(SNIPPET_PREFIX);
    }

    private static List<FeatureDescriptor<?>> settingsAt(FeatureCatalog catalog, Target target) {
        return catalog.settings().stream()
                .filter(feature -> feature.attachment().targets().contains(target))
                .toList();
    }

    private static Set<String> keysAt(FeatureCatalog catalog, Target target) {
        Stream<FeatureDescriptor<?>> objects = catalog.objects().stream().map(ObjectRegistration::descriptor);
        Set<String> found = new TreeSet<>();
        Stream.concat(catalog.settings().stream(), objects)
                .filter(feature -> feature.attachment().targets().contains(target))
                .forEach(feature -> {
                    found.add(feature.key());
                    feature.alias().ifPresent(found::add);
                });
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
