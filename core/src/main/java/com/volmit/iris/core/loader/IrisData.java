/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package com.volmit.iris.core.loader;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.volmit.iris.Iris;
import com.volmit.iris.core.scripting.environment.PackEnvironment;
import com.volmit.iris.engine.data.cache.AtomicCache;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.*;
import com.volmit.iris.engine.object.annotations.Snippet;
import com.volmit.iris.engine.object.matter.IrisMatterObject;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.context.IrisContext;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.mantle.flag.MantleFlagAdapter;
import com.volmit.iris.util.mantle.flag.MantleFlag;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.parallel.BurstExecutor;
import com.volmit.iris.util.parallel.MultiBurst;
import com.volmit.iris.util.reflect.KeyedType;
import com.volmit.iris.util.scheduling.ChronoLatch;
import com.volmit.iris.util.scheduling.J;
import lombok.Data;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

@Data
public class IrisData implements ExclusionStrategy, TypeAdapterFactory {
    private static final KMap<File, IrisData> dataLoaders = new KMap<>();
    private final File dataFolder;
    private final int id;
    private final PackEnvironment environment;
    private boolean closed = false;
    private ResourceLoader<IrisBiome> biomeLoader;
    private ResourceLoader<IrisLootTable> lootLoader;
    private ResourceLoader<IrisRegion> regionLoader;
    private ResourceLoader<IrisDimension> dimensionLoader;
    private ResourceLoader<IrisGenerator> generatorLoader;
    private ResourceLoader<IrisJigsawPiece> jigsawPieceLoader;
    private ResourceLoader<IrisJigsawPool> jigsawPoolLoader;
    private ResourceLoader<IrisJigsawStructure> jigsawStructureLoader;
    private ResourceLoader<IrisEntity> entityLoader;
    private ResourceLoader<IrisMarker> markerLoader;
    private ResourceLoader<IrisSpawner> spawnerLoader;
    private ResourceLoader<IrisMod> modLoader;
    private ResourceLoader<IrisBlockData> blockLoader;
    private ResourceLoader<IrisExpression> expressionLoader;
    private ResourceLoader<IrisObject> objectLoader;
    private ResourceLoader<IrisMatterObject> matterLoader;
    private ResourceLoader<IrisImage> imageLoader;
    private ResourceLoader<IrisScript> scriptLoader;
    private ResourceLoader<IrisCave> caveLoader;
    private ResourceLoader<IrisRavine> ravineLoader;
    private ResourceLoader<IrisMatterObject> matterObjectLoader;
    private KMap<String, KList<String>> possibleSnippets;
    private Gson gson;
    private Gson snippetLoader;
    private GsonBuilder builder;
    private KMap<Class<? extends IrisRegistrant>, ResourceLoader<? extends IrisRegistrant>> loaders = new KMap<>();
    private Engine engine;

    private IrisData(File dataFolder) {
        this.engine = null;
        this.dataFolder = dataFolder;
        this.id = RNG.r.imax();
        this.environment = PackEnvironment.create(this);
        hotloaded();
    }

    public static IrisData get(File dataFolder) {
        return dataLoaders.computeIfAbsent(dataFolder, IrisData::new);
    }

    public static Optional<IrisData> getLoaded(File dataFolder) {
        return Optional.ofNullable(dataLoaders.get(dataFolder));
    }

    public static void dereference() {
        dataLoaders.v().forEach(IrisData::cleanupEngine);
    }

    public static int cacheSize() {
        int m = 0;
        for (IrisData i : dataLoaders.values()) {
            for (ResourceLoader<?> j : i.getLoaders().values()) {
                m += j.getLoadCache().getSize();
            }
        }

        return m;
    }

    private static void printData(ResourceLoader<?> rl) {
        Iris.warn("  " + rl.getResourceTypeName() + " @ /" + rl.getFolderName() + ": Cache=" + rl.getLoadCache().getSize() + " Folders=" + rl.getFolders().size());
    }

    public static IrisObject loadAnyObject(String key, @Nullable IrisData nearest) {
        return loadAny(IrisObject.class, key, nearest);
    }

    public static IrisMatterObject loadAnyMatter(String key, @Nullable IrisData nearest) {
        return loadAny(IrisMatterObject.class, key, nearest);
    }

    public static IrisBiome loadAnyBiome(String key, @Nullable IrisData nearest) {
        return loadAny(IrisBiome.class, key, nearest);
    }

    public static IrisExpression loadAnyExpression(String key, @Nullable IrisData nearest) {
        return loadAny(IrisExpression.class, key, nearest);
    }

    public static IrisMod loadAnyMod(String key, @Nullable IrisData nearest) {
        return loadAny(IrisMod.class, key, nearest);
    }

    public static IrisJigsawPiece loadAnyJigsawPiece(String key, @Nullable IrisData nearest) {
        return loadAny(IrisJigsawPiece.class, key, nearest);
    }

    public static IrisJigsawPool loadAnyJigsawPool(String key, @Nullable IrisData nearest) {
        return loadAny(IrisJigsawPool.class, key, nearest);
    }

    public static IrisEntity loadAnyEntity(String key, @Nullable IrisData nearest) {
        return loadAny(IrisEntity.class, key, nearest);
    }

    public static IrisLootTable loadAnyLootTable(String key, @Nullable IrisData nearest) {
        return loadAny(IrisLootTable.class, key, nearest);
    }

    public static IrisBlockData loadAnyBlock(String key, @Nullable IrisData nearest) {
        return loadAny(IrisBlockData.class, key, nearest);
    }

    public static IrisSpawner loadAnySpaner(String key, @Nullable IrisData nearest) {
        return loadAny(IrisSpawner.class, key, nearest);
    }

    public static IrisScript loadAnyScript(String key, @Nullable IrisData nearest) {
        return loadAny(IrisScript.class, key, nearest);
    }

    public static IrisRavine loadAnyRavine(String key, @Nullable IrisData nearest) {
        return loadAny(IrisRavine.class, key, nearest);
    }

    public static IrisRegion loadAnyRegion(String key, @Nullable IrisData nearest) {
        return loadAny(IrisRegion.class, key, nearest);
    }

    public static IrisMarker loadAnyMarker(String key, @Nullable IrisData nearest) {
        return loadAny(IrisMarker.class, key, nearest);
    }

    public static IrisCave loadAnyCave(String key, @Nullable IrisData nearest) {
        return loadAny(IrisCave.class, key, nearest);
    }

    public static IrisImage loadAnyImage(String key, @Nullable IrisData nearest) {
        return loadAny(IrisImage.class, key, nearest);
    }

    public static IrisDimension loadAnyDimension(String key, @Nullable IrisData nearest) {
        return loadAny(IrisDimension.class, key, nearest);
    }

    public static IrisJigsawStructure loadAnyJigsawStructure(String key, @Nullable IrisData nearest) {
        return loadAny(IrisJigsawStructure.class, key, nearest);
    }

    public static IrisGenerator loadAnyGenerator(String key, @Nullable IrisData nearest) {
        return loadAny(IrisGenerator.class, key, nearest);
    }

    public static <T extends IrisRegistrant> T loadAny(Class<T> type, String key, @Nullable IrisData nearest) {
        try {
            if (nearest != null) {
                T t = nearest.load(type, key, false);
                if (t != null) {
                    return t;
                }
            }

            for (File i : Objects.requireNonNull(Iris.instance.getDataFolder("packs").listFiles())) {
                if (i.isDirectory()) {
                    IrisData dm = get(i);
                    if (dm == nearest) continue;
                    T t = dm.load(type, key, false);

                    if (t != null) {
                        return t;
                    }
                }
            }
        } catch (Throwable e) {
            Iris.reportError(e);
            e.printStackTrace();
        }

        return null;
    }

    public <T extends IrisRegistrant> T load(Class<T> type, String key, boolean warn) {
        var loader = getLoader(type);
        if (loader == null) return null;
        return loader.load(key, warn);
    }

    @SuppressWarnings("unchecked")
    public <T extends IrisRegistrant> ResourceLoader<T> getLoader(Class<T> type) {
        return (ResourceLoader<T>) loaders.get(type);
    }

    public ResourceLoader<?> getTypedLoaderFor(File f) {
        String[] k = f.getPath().split("\\Q" + File.separator + "\\E");

        for (String i : k) {
            for (ResourceLoader<?> j : loaders.values()) {
                if (j.getFolderName().equals(i)) {
                    return j;
                }
            }
        }

        return null;
    }

    public void cleanupEngine() {
        if (engine != null && engine.isClosed()) {
            engine = null;
            Iris.debug("Dereferenced Data<Engine> " + getId() + " " + getDataFolder());
        }
    }

    public void preprocessObject(IrisRegistrant t) {
        try {
            IrisContext ctx = IrisContext.get();
            Engine engine = this.engine;

            if (engine == null && ctx != null && ctx.getEngine() != null) {
                engine = ctx.getEngine();
            }

            if (engine == null && t.getPreprocessors().isNotEmpty()) {
                Iris.error("Failed to preprocess object " + t.getLoadKey() + " because there is no engine context here. (See stack below)");
                try {
                    throw new RuntimeException();
                } catch (Throwable ex) {
                    ex.printStackTrace();
                }
            }

            if (engine == null) return;
            var global = engine.getDimension().getPreProcessors(t.getFolderName());
            var local = t.getPreprocessors();
            if ((global != null && global.isNotEmpty()) || local.isNotEmpty()) {
                synchronized (this) {
                    if (global != null) {
                        for (String i : global) {
                            engine.getExecution().preprocessObject(i, t);
                            Iris.debug("Loader<" + C.GREEN + t.getTypeName() + C.LIGHT_PURPLE + "> iprocess " + C.YELLOW + t.getLoadKey() + C.LIGHT_PURPLE + " in <rainbow>" + i);
                        }
                    }

                    for (String i : local) {
                        engine.getExecution().preprocessObject(i, t);
                        Iris.debug("Loader<" + C.GREEN + t.getTypeName() + C.LIGHT_PURPLE + "> iprocess " + C.YELLOW + t.getLoadKey() + C.LIGHT_PURPLE + " in <rainbow>" + i);
                    }
                }
            }
        } catch (Throwable e) {
            Iris.error("Failed to preprocess object!");
            e.printStackTrace();
        }
    }

    public void close() {
        closed = true;
        dump();
        dataLoaders.remove(dataFolder);
    }

    public IrisData copy() {
        return IrisData.get(dataFolder);
    }

    private <T extends IrisRegistrant> ResourceLoader<T> registerLoader(Class<T> registrant) {
        try {
            IrisRegistrant rr = registrant.getConstructor().newInstance();
            ResourceLoader<T> r = null;
            if (registrant.equals(IrisObject.class)) {
                r = (ResourceLoader<T>) new ObjectResourceLoader(dataFolder, this, rr.getFolderName(),
                        rr.getTypeName());
            } else if (registrant.equals(IrisMatterObject.class)) {
                r = (ResourceLoader<T>) new MatterObjectResourceLoader(dataFolder, this, rr.getFolderName(),
                        rr.getTypeName());
            } else if (registrant.equals(IrisScript.class)) {
                r = (ResourceLoader<T>) new ScriptResourceLoader(dataFolder, this, rr.getFolderName(),
                        rr.getTypeName());
            } else if (registrant.equals(IrisImage.class)) {
                r = (ResourceLoader<T>) new ImageResourceLoader(dataFolder, this, rr.getFolderName(),
                        rr.getTypeName());
            } else {
                J.attempt(() -> registrant.getConstructor().newInstance().registerTypeAdapters(builder));
                r = new ResourceLoader<>(dataFolder, this, rr.getFolderName(), rr.getTypeName(), registrant);
            }

            loaders.put(registrant, r);

            return r;
        } catch (Throwable e) {
            Iris.reportError(e);
            e.printStackTrace();
            Iris.error("Failed to create loader! " + registrant.getCanonicalName());
        }

        return null;
    }

    public synchronized void hotloaded() {
        closed = false;
        environment.close();
        possibleSnippets = new KMap<>();
        builder = new GsonBuilder()
                .addDeserializationExclusionStrategy(this)
                .addSerializationExclusionStrategy(this)
                .setLenient()
                .registerTypeAdapterFactory(this)
                .registerTypeAdapter(MantleFlag.class, new MantleFlagAdapter())
                .setPrettyPrinting();
        loaders.clear();
        File packs = dataFolder;
        packs.mkdirs();
        this.lootLoader = registerLoader(IrisLootTable.class);
        this.spawnerLoader = registerLoader(IrisSpawner.class);
        this.entityLoader = registerLoader(IrisEntity.class);
        this.regionLoader = registerLoader(IrisRegion.class);
        this.biomeLoader = registerLoader(IrisBiome.class);
        this.modLoader = registerLoader(IrisMod.class);
        this.dimensionLoader = registerLoader(IrisDimension.class);
        this.jigsawPoolLoader = registerLoader(IrisJigsawPool.class);
        this.jigsawStructureLoader = registerLoader(IrisJigsawStructure.class);
        this.jigsawPieceLoader = registerLoader(IrisJigsawPiece.class);
        this.generatorLoader = registerLoader(IrisGenerator.class);
        this.caveLoader = registerLoader(IrisCave.class);
        this.markerLoader = registerLoader(IrisMarker.class);
        this.ravineLoader = registerLoader(IrisRavine.class);
        this.blockLoader = registerLoader(IrisBlockData.class);
        this.expressionLoader = registerLoader(IrisExpression.class);
        this.objectLoader = registerLoader(IrisObject.class);
        this.imageLoader = registerLoader(IrisImage.class);
        this.scriptLoader = registerLoader(IrisScript.class);
        this.matterObjectLoader = registerLoader(IrisMatterObject.class);
        builder.registerTypeAdapterFactory(KeyedType::createTypeAdapter);

        gson = builder.create();
        dimensionLoader.streamAll()
                .map(IrisDimension::getDataScripts)
                .flatMap(KList::stream)
                .forEach(environment::execute);
    }

    public void dump() {
        for (ResourceLoader<?> i : loaders.values()) {
            i.clearCache();
        }
    }

    public void clearLists() {
        for (ResourceLoader<?> i : loaders.values()) {
            i.clearList();
        }
        possibleSnippets.clear();
    }

    public String toLoadKey(File f) {
        if (f.getPath().startsWith(getDataFolder().getPath())) {
            String[] full = f.getPath().split("\\Q" + File.separator + "\\E");
            String[] df = getDataFolder().getPath().split("\\Q" + File.separator + "\\E");
            StringBuilder g = new StringBuilder();
            boolean m = true;
            for (int i = 0; i < full.length; i++) {
                if (i >= df.length) {
                    if (m) {
                        m = false;
                        continue;
                    }

                    g.append("/").append(full[i]);
                }
            }

            return g.substring(1).split("\\Q.\\E")[0];
        } else {
            Iris.error("Forign file from loader " + f.getPath() + " (loader realm: " + getDataFolder().getPath() + ")");
        }

        Iris.error("Failed to load " + f.getPath() + " (loader realm: " + getDataFolder().getPath() + ")");

        return null;
    }

    @Override
    public boolean shouldSkipField(FieldAttributes f) {
        return false;
    }

    @Override
    public boolean shouldSkipClass(Class<?> c) {
        if (c.equals(AtomicCache.class)) {
            return true;
        } else return c.equals(ChronoLatch.class);
    }

    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
        if (!typeToken.getRawType().isAnnotationPresent(Snippet.class)) {
            return null;
        }

        String snippetType = typeToken.getRawType().getDeclaredAnnotation(Snippet.class).value();
        String snippedBase = "snippet/" + snippetType + "/";

        return new TypeAdapter<>() {
            @Override
            public void write(JsonWriter jsonWriter, T t) throws IOException {
                gson.getDelegateAdapter(IrisData.this, typeToken).write(jsonWriter, t);
            }

            @Override
            public T read(JsonReader reader) throws IOException {
                TypeAdapter<T> adapter = gson.getDelegateAdapter(IrisData.this, typeToken);

                if (reader.peek().equals(JsonToken.STRING)) {
                    String r = reader.nextString();
                    if (!r.startsWith("snippet/"))
                        return null;
                    if (!r.startsWith(snippedBase))
                        r = snippedBase + r.substring(8);

                    File f = new File(getDataFolder(), r + ".json");
                    if (f.exists()) {
                        try (JsonReader snippetReader = new JsonReader(new FileReader(f))){
                            return adapter.read(snippetReader);
                        } catch (Throwable e) {
                            Iris.error("Couldn't read snippet " + r + " in " + reader.getPath() + " (" + e.getMessage() + ")");
                        }
                    } else {
                        Iris.error("Couldn't find snippet " + r + " in " + reader.getPath());
                    }

                    return null;
                }

                try {
                    return adapter.read(reader);
                } catch (Throwable e) {
                    Iris.error("Failed to read " + typeToken.getRawType().getCanonicalName() + "... faking objects a little to load the file at least.");
                    Iris.reportError(e);
                    try {
                        return (T) typeToken.getRawType().getConstructor().newInstance();
                    } catch (Throwable ignored) {

                    }
                }
                return null;
            }
        };
    }

    public KList<String> getPossibleSnippets(String f) {
        return possibleSnippets.computeIfAbsent(f, (k) -> {
            KList<String> l = new KList<>();

            File snippetFolder = new File(getDataFolder(), "snippet/" + f);
            if (!snippetFolder.exists()) return l;

            String absPath = snippetFolder.getAbsolutePath();
            try (var stream = Files.walk(snippetFolder.toPath())) {
                stream.filter(Files::isRegularFile)
                        .map(Path::toAbsolutePath)
                        .map(Path::toString)
                        .filter(s -> s.endsWith(".json"))
                        .map(s -> s.substring(absPath.length() + 1))
                        .map(s -> s.replace("\\", "/"))
                        .map(s -> s.split("\\Q.\\E")[0])
                        .forEach(s -> l.add("snippet/" + s));
            } catch (Throwable e) {
                e.printStackTrace();
            }

            return l;
        });
    }

    public boolean isClosed() {
        return closed;
    }

    public void savePrefetch(Engine engine) {
        BurstExecutor b = MultiBurst.ioBurst.burst(loaders.size());

        for (ResourceLoader<?> i : loaders.values()) {
            b.queue(() -> {
                try {
                    i.saveFirstAccess(engine);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        b.complete();
        Iris.info("Saved Prefetch Cache to speed up future world startups");
    }

    public void loadPrefetch(Engine engine) {
        BurstExecutor b = MultiBurst.ioBurst.burst(loaders.size());

        for (ResourceLoader<?> i : loaders.values()) {
            b.queue(() -> {
                try {
                    i.loadFirstAccess(engine);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        b.complete();
        Iris.info("Loaded Prefetch Cache to reduce generation disk use.");
    }
}