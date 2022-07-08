package com.volmit.iris.engine;

import art.arcane.amulet.concurrent.J;
import art.arcane.amulet.io.IO;
import art.arcane.amulet.io.JarLoader;
import art.arcane.cram.PakFile;
import art.arcane.cram.PakKey;
import art.arcane.cram.PakResource;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.volmit.iris.engine.dimension.IrisDimension;
import com.volmit.iris.engine.dimension.IrisGenerator;
import com.volmit.iris.engine.dimension.IrisRange;
import com.volmit.iris.engine.resolver.*;
import com.volmit.iris.platform.PlatformNamespaceKey;
import com.volmit.iris.util.NSK;
import lombok.Data;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

@Data
public class EngineData implements TypeAdapterFactory {
    private final Engine engine;
    private final Gson gson;
    private List<Resolvable> resolvableTypes;
    private final Map<Class<?>, Resolver<?>> resolvers;

    public EngineData(Engine engine) throws IOException {
        this.engine = engine;
        this.resolvers = new HashMap<>();
        this.resolvableTypes =  J.attempt(() -> new JarLoader(getClass()).all().parallel()
            .filter(Objects::nonNull)
            .filter(i -> !i.isInterface() && !i.isEnum())
            .filter(i -> i.isAssignableFrom(Resolvable.class) || Resolvable.class.isAssignableFrom(i))
            .filter(i -> !i.equals(EngineResolvable.class))
            .map(i -> J.attempt(() -> (Resolvable) i.getDeclaredConstructor().newInstance(), null)).toList(), List.of());
        GsonBuilder gsonBuilder = new GsonBuilder().registerTypeAdapterFactory(this);
        resolvableTypes.forEach(i -> i.apply(gsonBuilder));
        this.gson = gsonBuilder.setPrettyPrinting().create();
        i("Registered " + resolvableTypes.size() + " Mutators with " + resolvableTypes.stream().filter(i -> i instanceof TypeAdapterFactory).count() + " Type Adapter Factories");
    }

    public void generateSchemas(File folder) throws IOException {
        folder.mkdirs();

        for(Resolvable i : resolvableTypes) {
            IO.writeAll(new File(folder, i.entity().getId() + ".json"), i.generateSchema(this).toString());
        }
    }

    public <T extends Resolvable> T resolve(Class<T> clazz, PlatformNamespaceKey key)
    {
        Resolver<?> r = resolvers.get(clazz);

        if(r == null) {
            return null;
        }

        return (T) r.resolve(key);
    }

    public void registerResolver(Class<?> type, Resolver<?> resolver, String namespace)
    {
        if(resolvers.containsKey(type)) {
            Resolver r = resolvers.get(type);
            resolvers.put(type, r.and(namespace, r));
        }

        else {
            resolvers.put(type, resolver);
        }
    }

    public void loadData(File folder, PlatformNamespaceKey dimension) throws IOException {
        i("Loading Data in " + folder.getPath());
        for(File i : folder.listFiles()) {
            if(i.isDirectory() && i.getName().equals(dimension.getNamespace())) {
                loadDataNamespaced(i, i.getName());

                if(getEngine().getConfiguration().isMutable()) {
                    generateSchemas(new File(i, ".iris/schema"));
                    i("Generated " + new File(i, ".iris/schema").listFiles().length + " Schemas");
                    generateCodeWorkspace(new File(i, dimension.getNamespace() + ".code-workspace"));
                    i("Generated Code Workspace");
                }
            }

            else if(i.getName().equals(dimension.getNamespace() + ".dat")) {
                loadPakFile(folder, i.getName().split("\\Q.\\E")[0]);
            }
        }

        IrisDimension dim = resolve(IrisDimension.class, dimension);

        if(dim == null) {
            f("Failed to load dimension " + dimension);
        }
    }

    private void generateCodeWorkspace(File file) throws IOException {
        file.getParentFile().mkdirs();
        JsonObject ws = new JsonObject();
        JsonArray folders = new JsonArray();
        JsonObject folder = new JsonObject();
        folder.add("path", new JsonPrimitive("."));
        folders.add(folder);
        ws.add("folders", folders);
        JsonObject settings = new JsonObject();
        settings.add("workbench.colorTheme", new JsonPrimitive("Monokai"));
        settings.add("workbench.preferredDarkColorTheme", new JsonPrimitive("Solarized Dark"));
        settings.add("workbench.tips.enabled", new JsonPrimitive(false));
        settings.add("workbench.tree.indent", new JsonPrimitive(24));
        settings.add("files.autoSave", new JsonPrimitive("onFocusChange"));
        JsonObject jc = new JsonObject();
        jc.add("editor.autoIndent", new JsonPrimitive("brackets"));
        jc.add("editor.acceptSuggestionOnEnter", new JsonPrimitive("smart"));
        jc.add("editor.cursorSmoothCaretAnimation", new JsonPrimitive(true));
        jc.add("editor.dragAndDrop", new JsonPrimitive(false));
        jc.add("files.trimTrailingWhitespace", new JsonPrimitive(true));
        jc.add("diffEditor.ignoreTrimWhitespace", new JsonPrimitive(true));
        jc.add("files.trimFinalNewlines", new JsonPrimitive(true));
        jc.add("editor.suggest.showKeywords", new JsonPrimitive(false));
        jc.add("editor.suggest.showSnippets", new JsonPrimitive(false));
        jc.add("editor.suggest.showWords", new JsonPrimitive(false));
        JsonObject st = new JsonObject();
        st.add("strings", new JsonPrimitive(true));
        jc.add("editor.quickSuggestions", st);
        jc.add("editor.suggest.insertMode", new JsonPrimitive("replace"));
        settings.add("[json]", jc);
        settings.add("json.maxItemsComputed", new JsonPrimitive(30000));
        JsonArray schemas = new JsonArray();

        for(Resolvable i : resolvableTypes) {
            String id = i.entity().getId();
            JsonObject o = new JsonObject();
            JsonArray fileMatch = new JsonArray();
            fileMatch.add("/" + id + "/*.json");
            o.add("fileMatch", fileMatch);
            o.add("url", new JsonPrimitive(".iris/schema/" + id + ".json"));
            schemas.add(o);
        }

        settings.add("json.schemas", schemas);
        ws.add("settings", settings);
        IO.writeAll(file, ws.toString());
    }

    public void loadDataNamespaced(File folder, String namespace) throws IOException {
        i("Loading Namespace " + namespace + " in " + folder.getPath());
        for(Resolvable i : resolvableTypes)
        {
            new File(folder, i.entity().getId()).mkdirs();
            IO.writeAll(new File(new File(folder, i.entity().getId()), "example.json"), gson.toJson(i));
        }

        for(File i : folder.listFiles())
        {
            if(i.isDirectory()) {
                loadDataFolder(i, namespace);
            }
        }
    }

    public void loadDataFolder(File folder, String namespace) {
        for(Resolvable i : resolvableTypes)
        {
            if(!folder.getName().equals(i.entity().getId())) {
                continue;
            }

            registerResolver(i.getClass(), Resolver.hotDirectoryJson(namespace, i.getClass(), folder, gson), namespace);
        }
    }

    public void loadPakFile(File folder, String name) throws IOException {
        PakFile pakFile = new PakFile(folder, name);
        Map<PakKey, PakResource> resources = pakFile.getAllResources();

        for(Resolvable i : resolvableTypes)
        {
            Class<? extends Resolvable> resolvableClass = i.getClass();
            CompositeResolver<?> composite = Resolver.frozen(resources, (p) -> p.getClass().equals(resolvableClass));

            for(String j : composite.getResolvers().keySet())
            {
                Resolver<? extends Resolvable> resolver = composite.getResolvers().get(i);
                this.registerResolver(i.getClass(), resolver, j);
            }
        }
    }

    public void printResolvers() {
        resolvers.forEach((k, i) -> i.print(k.simpleName(), this));
    }

    private <T> void writeSafeJson(TypeAdapter<T> delegate, JsonWriter out, T value) {
        try {
            delegate.write(out, value);
        } catch (IOException e) {
            try {
                delegate.write(out, null);
            } catch(IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        final TypeAdapter<T> delegate = gson.getDelegateAdapter(this, type);

        for(Resolvable i : resolvableTypes) {
            if(type.getRawType().equals(i.getClass())) {
                return new TypeAdapter<>() {
                    public void write(JsonWriter out, T value) {writeSafeJson(delegate, out, value);}

                    @SuppressWarnings("unchecked")
                    public T read(JsonReader in) throws IOException {
                        JsonToken token = in.peek();

                        if(token == JsonToken.STRING) {
                            Resolver<?> resolver = getResolvers().get(i.getClass());
                            String key = in.nextString();

                            if(resolver == null) {
                                w("Unable to find a resolver for " + i.getClass() + " received " + key);
                                return null;
                            }

                            T t = (T) resolver.resolve(new NSK(key));

                            if(t == null) {
                                w("Unable to resolve " + i.getClass() + " " + key);
                            }

                            return t;
                        }

                        return delegate.read(in);
                    }
                };
            }
        }

        return null;
    }

    public Resolvable getInstance(Class<?> type) {
        for(Resolvable i : resolvableTypes) {
            if(i.getClass().equals(type)) {
                return i;
            }
        }

        return null;
    }

    public List<PlatformNamespaceKey> getAllKeys(Class<?> type) {
        List<PlatformNamespaceKey> keys = new ArrayList<>();
        Resolver<?> resolver = resolvers.get(type);

        if(resolver != null) {
            resolver.addAllKeys(keys);
        }

        return keys;
    }
}
