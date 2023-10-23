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
import com.volmit.iris.engine.data.cache.AtomicCache;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.*;
import com.volmit.iris.engine.object.annotations.Snippet;
import com.volmit.iris.engine.object.matter.IrisMatterObject;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.context.IrisContext;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.parallel.BurstExecutor;
import com.volmit.iris.util.parallel.MultiBurst;
import com.volmit.iris.util.scheduling.ChronoLatch;
import com.volmit.iris.util.scheduling.J;
import lombok.Data;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Objects;
import java.util.function.Function;

@Data
public class IrisData implements ExclusionStrategy, TypeAdapterFactory {
    private static final KMap<File, IrisData> dataLoaders = new KMap<>();
    private final File dataFolder;
    private final int id;
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
        hotloaded();
    }

    public static IrisData get(File dataFolder) {
        return dataLoaders.computeIfAbsent(dataFolder, IrisData::new);
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

    public static IrisObject loadAnyObject(String key) {
        return loadAny(key, (dm) -> dm.getObjectLoader().load(key, false));
    }

    public static IrisMatterObject loadAnyMatter(String key) {
        return loadAny(key, (dm) -> dm.getMatterLoader().load(key, false));
    }

    public static IrisBiome loadAnyBiome(String key) {
        return loadAny(key, (dm) -> dm.getBiomeLoader().load(key, false));
    }

    public static IrisExpression loadAnyExpression(String key) {
        return loadAny(key, (dm) -> dm.getExpressionLoader().load(key, false));
    }

    public static IrisMod loadAnyMod(String key) {
        return loadAny(key, (dm) -> dm.getModLoader().load(key, false));
    }

    public static IrisJigsawPiece loadAnyJigsawPiece(String key) {
        return loadAny(key, (dm) -> dm.getJigsawPieceLoader().load(key, false));
    }

    public static IrisJigsawPool loadAnyJigsawPool(String key) {
        return loadAny(key, (dm) -> dm.getJigsawPoolLoader().load(key, false));
    }

    public static IrisEntity loadAnyEntity(String key) {
        return loadAny(key, (dm) -> dm.getEntityLoader().load(key, false));
    }

    public static IrisLootTable loadAnyLootTable(String key) {
        return loadAny(key, (dm) -> dm.getLootLoader().load(key, false));
    }

    public static IrisBlockData loadAnyBlock(String key) {
        return loadAny(key, (dm) -> dm.getBlockLoader().load(key, false));
    }

    public static IrisSpawner loadAnySpaner(String key) {
        return loadAny(key, (dm) -> dm.getSpawnerLoader().load(key, false));
    }

    public static IrisScript loadAnyScript(String key) {
        return loadAny(key, (dm) -> dm.getScriptLoader().load(key, false));
    }

    public static IrisRavine loadAnyRavine(String key) {
        return loadAny(key, (dm) -> dm.getRavineLoader().load(key, false));
    }

    public static IrisRegion loadAnyRegion(String key) {
        return loadAny(key, (dm) -> dm.getRegionLoader().load(key, false));
    }

    public static IrisMarker loadAnyMarker(String key) {
        return loadAny(key, (dm) -> dm.getMarkerLoader().load(key, false));
    }

    public static IrisCave loadAnyCave(String key) {
        return loadAny(key, (dm) -> dm.getCaveLoader().load(key, false));
    }

    public static IrisImage loadAnyImage(String key) {
        return loadAny(key, (dm) -> dm.getImageLoader().load(key, false));
    }

    public static IrisDimension loadAnyDimension(String key) {
        return loadAny(key, (dm) -> dm.getDimensionLoader().load(key, false));
    }

    public static IrisJigsawStructure loadAnyJigsawStructure(String key) {
        return loadAny(key, (dm) -> dm.getJigsawStructureLoader().load(key, false));
    }

    public static IrisGenerator loadAnyGenerator(String key) {
        return loadAny(key, (dm) -> dm.getGeneratorLoader().load(key, false));
    }

    public static <T extends IrisRegistrant> T loadAny(String key, Function<IrisData, T> v) {
        try {
            for (File i : Objects.requireNonNull(Iris.instance.getDataFolder("packs").listFiles())) {
                if (i.isDirectory()) {
                    IrisData dm = get(i);
                    T t = v.apply(dm);

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

            if (engine != null && t.getPreprocessors().isNotEmpty()) {
                synchronized (this) {
                    engine.getExecution().getAPI().setPreprocessorObject(t);

                    for (String i : t.getPreprocessors()) {
                        engine.getExecution().execute(i);
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
            e.printStackTrace();
            Iris.error("Failed to create loader! " + registrant.getCanonicalName());
        }

        return null;
    }

    public synchronized void hotloaded() {
        possibleSnippets = new KMap<>();
        builder = new GsonBuilder()
                .addDeserializationExclusionStrategy(this)
                .addSerializationExclusionStrategy(this)
                .setLenient()
                .registerTypeAdapterFactory(this)
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
        gson = builder.create();
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

                    if (r.startsWith("snippet/" + snippetType + "/")) {
                        File f = new File(getDataFolder(), r + ".json");

                        if (f.exists()) {
                            try {
                                JsonReader snippetReader = new JsonReader(new FileReader(f));
                                return adapter.read(snippetReader);
                            } catch (Throwable e) {
                                Iris.error("Couldn't read snippet " + r + " in " + reader.getPath() + " (" + e.getMessage() + ")");
                            }
                        } else {
                            Iris.error("Couldn't find snippet " + r + " in " + reader.getPath());
                        }
                    }

                    return null;
                }

                try {
                    return adapter.read(reader);
                } catch (Throwable e) {
                    Iris.error("Failed to read " + typeToken.getRawType().getCanonicalName() + "... faking objects a little to load the file at least.");
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

            if (snippetFolder.exists() && snippetFolder.isDirectory()) {
                for (File i : snippetFolder.listFiles()) {
                    l.add("snippet/" + f + "/" + i.getName().split("\\Q.\\E")[0]);
                }
            }

            return l;
        });
    }

    public boolean isClosed() {
        return closed;
    }

    public void savePrefetch(Engine engine) {
        BurstExecutor b = MultiBurst.burst.burst(loaders.size());

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
        BurstExecutor b = MultiBurst.burst.burst(loaders.size());

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