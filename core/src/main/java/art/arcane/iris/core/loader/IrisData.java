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

package art.arcane.iris.core.loader;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.Strictness;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.core.modules.IrisPackResources;
import art.arcane.iris.core.modules.PackModuleSet;
import art.arcane.iris.core.pack.PackDirectoryResolver;
import art.arcane.volmlib.util.cache.AtomicCache;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.core.structure.authoring.StructureRecoveryResult;
import art.arcane.iris.core.structure.authoring.StructureTransactionWriter;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.material.IrisBlockData;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.noise.expression.IrisExpression;
import art.arcane.iris.engine.object.terrain.IrisGenerator;
import art.arcane.iris.engine.object.image.IrisImage;
import art.arcane.iris.engine.object.image.IrisImageMap;
import art.arcane.iris.engine.object.prefab.IrisObject;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import art.arcane.iris.engine.object.annotations.Snippet;
import art.arcane.iris.engine.object.prefab.matter.IrisMatterObject;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.mantle.flag.MantleFlagAdapter;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.iris.util.common.parallel.BurstExecutor;
import art.arcane.iris.util.common.parallel.MultiBurst;
import art.arcane.iris.util.common.reflect.KeyedType;
import art.arcane.volmlib.util.scheduling.ChronoLatch;
import art.arcane.iris.util.common.scheduling.J;
import art.arcane.iris.util.project.context.IrisContext;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class IrisData implements ExclusionStrategy, TypeAdapterFactory {
    private static final Map<File, IrisData> dataLoaders = new ConcurrentHashMap<>();
    // Loaders (cached or detached) that currently have registered engines; see hasActiveEngines.
    // Identity-keyed on purpose: Lombok's @Data hashCode over this class's mutable loader state
    // drifts while an engine runs, so a hashing set would silently fail removal and pin every
    // engine-hosting IrisData (and its pack graph) for the JVM lifetime.
    private static final Set<IrisData> ENGINE_HOLDERS =
            Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
    /** Every resource type Iris loads itself for generation, studio and validation; module resource kinds come on top. */
    private static final List<Class<? extends IrisRegistrant>> RUNTIME_RESOURCE_TYPES = List.of(IrisRegion.class, IrisBiome.class, IrisDimension.class,
            IrisGenerator.class, IrisBlockData.class, IrisExpression.class, IrisObject.class, IrisImage.class,
            IrisImageMap.class, IrisMatterObject.class);
    /** Pack folders Iris reads that hold no resources of its loaders: snippets and module jars. */
    private static final List<String> OTHER_OWN_FOLDERS = List.of("snippet", "modules");
    private final File dataFolder;
    private final int id;
    private final Purpose purpose;
    private volatile boolean closed = false;
    private final PackModuleSet modules;
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private volatile IrisPackResources packResources;
    private ResourceLoader<IrisBiome> biomeLoader;
    private ResourceLoader<IrisRegion> regionLoader;
    private ResourceLoader<IrisDimension> dimensionLoader;
    private ResourceLoader<IrisGenerator> generatorLoader;
    private ResourceLoader<IrisBlockData> blockLoader;
    private ResourceLoader<IrisExpression> expressionLoader;
    private ResourceLoader<IrisObject> objectLoader;
    private ResourceLoader<IrisMatterObject> matterLoader;
    private ResourceLoader<IrisImage> imageLoader;
    private ResourceLoader<IrisImageMap> imageMapLoader;
    private KMap<String, KList<String>> possibleSnippets;
    private Gson gson;
    private Gson snippetLoader;
    private GsonBuilder builder;
    private volatile KMap<Class<? extends IrisRegistrant>, ResourceLoader<? extends IrisRegistrant>> loaders = new KMap<>();
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private final transient List<Engine> engines = new ArrayList<>();

    /** What an instance is opened for, which decides its loaders and whether opening it may change the pack. */
    private enum Purpose {
        /** Generation and studio: every loader, registered with Iris's services; interrupted structure transactions are recovered. */
        RUNTIME,
        /** The datapack compiler at bootstrap: only the loaders it reads, without Iris's services. */
        DATAPACK_COMPILER,
        /** Validating a pack: every loader, without Iris's services, and nothing in the pack is written. */
        VALIDATION
    }

    private IrisData(File dataFolder) {
        this(dataFolder, Purpose.RUNTIME);
    }

    private IrisData(File dataFolder, Purpose purpose) {
        this.dataFolder = dataFolder;
        this.modules = new PackModuleSet(dataFolder, this::getGson);
        this.id = RNG.r.imax();
        this.purpose = purpose;
        switch (purpose) {
            case RUNTIME -> hotloaded();
            case DATAPACK_COMPILER -> hotloadedDatapackCompiler();
            case VALIDATION -> openForValidation();
        }
    }

    public static IrisData get(File dataFolder) {
        return dataLoaders.computeIfAbsent(dataFolder, IrisData::new);
    }

    public static IrisData openRuntime(File dataFolder) {
        return new IrisData(dataFolder);
    }

    public static IrisData openDatapackCompiler(File dataFolder) {
        return new IrisData(dataFolder, Purpose.DATAPACK_COMPILER);
    }

    /** A detached instance that reads the pack as generation would, for validating it: it writes nothing into the pack
     * and needs none of Iris's services. Close it when done.
     */
    public static IrisData openValidation(File dataFolder) {
        return new IrisData(dataFolder, Purpose.VALIDATION);
    }

    /** Every folder below a pack's root that Iris reads itself, which no module resource kind may read. */
    public static Set<String> ownFolderNames() {
        Set<String> folders = new TreeSet<>(OTHER_OWN_FOLDERS);
        for (Class<? extends IrisRegistrant> type : RUNTIME_RESOURCE_TYPES) {
            try {
                folders.add(type.getConstructor().newInstance().getFolderName());
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Iris resource type " + type.getName() + " cannot be created", failure);
            }
        }
        return Set.copyOf(folders);
    }

    public static Optional<IrisData> getLoaded(File dataFolder) {
        return Optional.ofNullable(dataLoaders.get(dataFolder));
    }

    public static boolean invalidateLoadedStructureResources(File dataFolder) {
        Path requested = dataFolderIdentity(Objects.requireNonNull(
                dataFolder,
                "Iris data folder to invalidate"));
        boolean invalidated = false;
        for (Map.Entry<File, IrisData> entry : dataLoaders.entrySet()) {
            Path loaded = dataFolderIdentity(entry.getKey());
            if (!loaded.equals(requested)) {
                continue;
            }
            entry.getValue().invalidateStructureResources();
            invalidated = true;
        }
        return invalidated;
    }

    public static void dereference() {
        dataLoaders.values().forEach(IrisData::cleanupEngine);
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

    public static IrisBlockData loadAnyBlock(String key, @Nullable IrisData nearest) {
        return loadAny(IrisBlockData.class, key, nearest);
    }

    public static IrisRegion loadAnyRegion(String key, @Nullable IrisData nearest) {
        return loadAny(IrisRegion.class, key, nearest);
    }

    public static IrisImage loadAnyImage(String key, @Nullable IrisData nearest) {
        return loadAny(IrisImage.class, key, nearest);
    }

    public static IrisImageMap loadAnyImageMap(String key, @Nullable IrisData nearest) {
        return loadAny(IrisImageMap.class, key, nearest);
    }

    public static IrisDimension loadAnyDimension(String key, @Nullable IrisData nearest) {
        return loadAny(IrisDimension.class, key, nearest);
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

            for (File i : PackDirectoryResolver.listVisiblePackDirectories(
                    IrisPlatforms.get().packsFolder())) {
                IrisData dm = get(i);
                if (dm == nearest) continue;
                T t = dm.load(type, key, false);

                if (t != null) {
                    return t;
                }
            }
        } catch (Throwable e) {
            IrisLogging.reportError(e);
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
        int removed;
        synchronized (engines) {
            int previousSize = engines.size();
            removeClosedEngines();
            removed = previousSize - engines.size();
        }
        if (removed > 0) {
            IrisLogging.debug("Dereferenced " + removed + " Data<Engine> registration(s) " + getId() + " " + getDataFolder());
        }
    }

    public Engine getEngine() {
        IrisContext context = IrisContext.get();
        if (context != null) {
            Engine contextEngine = context.getEngine();
            if (!contextEngine.isClosed() && contextEngine.getData() == this) {
                return contextEngine;
            }
        }

        synchronized (engines) {
            removeClosedEngines();
            return engines.size() == 1 ? engines.get(0) : null;
        }
    }

    public List<Engine> getEngines() {
        synchronized (engines) {
            removeClosedEngines();
            return List.copyOf(engines);
        }
    }

    /**
     * True when any loader — including detached {@link #openRuntime(File)} instances, which
     * never enter the dataLoaders cache — has a live engine reading the given pack folder.
     * The dataLoaders cache cannot answer this question: engines only ever attach to
     * openRuntime instances.
     */
    public static boolean hasActiveEngines(File dataFolder) {
        Path target = dataFolder.toPath().toAbsolutePath().normalize();
        IrisData[] holders;
        synchronized (ENGINE_HOLDERS) {
            holders = ENGINE_HOLDERS.toArray(new IrisData[0]);
        }
        for (IrisData data : holders) {
            if (data.getEngines().isEmpty()) {
                ENGINE_HOLDERS.remove(data);
                continue;
            }
            if (data.getDataFolder().toPath().toAbsolutePath().normalize().equals(target)) {
                return true;
            }
        }
        return false;
    }

    public void registerEngine(Engine engine) {
        Objects.requireNonNull(engine, "engine");
        synchronized (engines) {
            for (Engine registeredEngine : engines) {
                if (registeredEngine == engine) {
                    return;
                }
            }
            engines.add(engine);
        }
        ENGINE_HOLDERS.add(this);
    }

    public void unregisterEngine(Engine engine) {
        if (engine == null) {
            return;
        }
        synchronized (engines) {
            Iterator<Engine> iterator = engines.iterator();
            while (iterator.hasNext()) {
                if (iterator.next() == engine) {
                    iterator.remove();
                    break;
                }
            }
            if (engines.isEmpty()) {
                ENGINE_HOLDERS.remove(this);
            }
        }
    }

    private void removeClosedEngines() {
        Iterator<Engine> iterator = engines.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().isClosed()) {
                iterator.remove();
            }
        }
    }

    public void preprocessObject(IrisRegistrant t) {
    }

    public void close() {
        closed = true;
        dump();
        modules.close();
        synchronized (engines) {
            engines.clear();
        }
        ENGINE_HOLDERS.remove(this);
        dataLoaders.remove(dataFolder, this);
    }

    public IrisData copy() {
        return IrisData.get(dataFolder);
    }

    private <T extends IrisRegistrant> ResourceLoader<T> registerLoader(Class<T> registrant, KMap<Class<? extends IrisRegistrant>, ResourceLoader<? extends IrisRegistrant>> target) {
        try {
            IrisRegistrant rr = registrant.getConstructor().newInstance();
            ResourceLoader<T> r = null;
            ResourceLoader.Options options = purpose == Purpose.RUNTIME
                    ? ResourceLoader.Options.runtime()
                    : ResourceLoader.Options.datapackCompiler();
            if (registrant.equals(IrisObject.class)) {
                r = (ResourceLoader<T>) new ObjectResourceLoader(dataFolder, this, rr.getFolderName(),
                        rr.getTypeName(), options);
            } else if (registrant.equals(IrisMatterObject.class)) {
                r = (ResourceLoader<T>) new MatterObjectResourceLoader(dataFolder, this, rr.getFolderName(),
                        rr.getTypeName(), options);
            } else if (registrant.equals(IrisImage.class)) {
                r = (ResourceLoader<T>) new ImageResourceLoader(dataFolder, this, rr.getFolderName(),
                        rr.getTypeName(), options);
            } else {
                J.attempt(() -> registrant.getConstructor().newInstance().registerTypeAdapters(builder));
                r = new ResourceLoader<>(dataFolder, this, rr.getFolderName(), rr.getTypeName(), registrant, options);
            }

            target.put(registrant, r);

            return r;
        } catch (Throwable e) {
            IrisLogging.reportError(e);
            IrisLogging.error("Failed to create loader! " + registrant.getCanonicalName());
        }

        return null;
    }

    public synchronized void hotloaded() {
        closed = false;
        dataFolder.mkdirs();
        recoverStructureTransactions();
        loadRuntimeResources();

        for (Engine engine : getEngines()) {
            engine.hotload();
        }
    }

    private void openForValidation() {
        closed = false;
        loadRuntimeResources();
    }

    /** The runtime loaders, the pack's Gson and a new snapshot of its resources, module resource kinds included. */
    private void loadRuntimeResources() {
        possibleSnippets = new KMap<>();
        builder = new GsonBuilder()
                .addDeserializationExclusionStrategy(this)
                .addSerializationExclusionStrategy(this)
                .setStrictness(Strictness.LENIENT)
                .registerTypeAdapterFactory(this)
                .registerTypeAdapter(MantleFlag.class, new MantleFlagAdapter())
                .setPrettyPrinting();
        KMap<Class<? extends IrisRegistrant>, ResourceLoader<? extends IrisRegistrant>> replacement = new KMap<>();
        this.regionLoader = registerLoader(IrisRegion.class, replacement);
        this.biomeLoader = registerLoader(IrisBiome.class, replacement);
        this.dimensionLoader = registerLoader(IrisDimension.class, replacement);
        this.generatorLoader = registerLoader(IrisGenerator.class, replacement);
        this.blockLoader = registerLoader(IrisBlockData.class, replacement);
        this.expressionLoader = registerLoader(IrisExpression.class, replacement);
        this.objectLoader = registerLoader(IrisObject.class, replacement);
        this.imageLoader = registerLoader(IrisImage.class, replacement);
        this.imageMapLoader = registerLoader(IrisImageMap.class, replacement);
        this.matterLoader = registerLoader(IrisMatterObject.class, replacement);
        builder.registerTypeAdapterFactory(KeyedType::createTypeAdapter);

        gson = builder.create();
        loaders = replacement;
        packResources = new IrisPackResources(this);
    }

    private void hotloadedDatapackCompiler() {
        closed = false;
        possibleSnippets = new KMap<>();
        builder = new GsonBuilder()
                .addDeserializationExclusionStrategy(this)
                .addSerializationExclusionStrategy(this)
                .setStrictness(Strictness.LENIENT)
                .registerTypeAdapterFactory(this)
                .registerTypeAdapter(MantleFlag.class, new MantleFlagAdapter())
                .setPrettyPrinting();
        KMap<Class<? extends IrisRegistrant>, ResourceLoader<? extends IrisRegistrant>> replacement = new KMap<>();
        dataFolder.mkdirs();
        biomeLoader = registerLoader(IrisBiome.class, replacement);
        regionLoader = registerLoader(IrisRegion.class, replacement);
        dimensionLoader = registerLoader(IrisDimension.class, replacement);
        generatorLoader = registerLoader(IrisGenerator.class, replacement);
        expressionLoader = registerLoader(IrisExpression.class, replacement);
        imageLoader = registerLoader(IrisImage.class, replacement);
        imageMapLoader = registerLoader(IrisImageMap.class, replacement);
        builder.registerTypeAdapterFactory(KeyedType::createTypeAdapter);
        gson = builder.create();
        loaders = replacement;
        if (biomeLoader == null || regionLoader == null || dimensionLoader == null
                || generatorLoader == null || expressionLoader == null
                || imageLoader == null || imageMapLoader == null) {
            throw new IllegalStateException("Unable to initialize Iris datapack compiler loaders for " + dataFolder);
        }
    }

    /** Forgets everything read so far: the loaders' caches and the module resources, which a new snapshot reads again. */
    public void dump() {
        for (ResourceLoader<?> i : loaders.values()) {
            i.clearCache();
        }
        reloadModuleResources();
    }

    /** Reads objects and module resources (structures, jigsaw pools and pieces among them) again after a tool rewrote their files. */
    public synchronized void invalidateStructureResources() {
        invalidateLoader(objectLoader);
        reloadModuleResources();
    }

    /**
     * Starts a new snapshot of the pack's resources, so the files of every module resource kind are listed and read
     * again, after a tool added, rewrote or deleted some. Iris's own loaders keep their caches. The engines running
     * with this pack restart their modules' worlds with the new snapshot, so no module keeps state built from the old
     * files.
     */
    public void reloadModuleResources() {
        packResources = new IrisPackResources(this);
        if (closed) {
            return;
        }
        for (Engine engine : getEngines()) {
            engine.packResourcesReloaded();
        }
    }

    /** The folders below the pack root that hold its resources: Iris's loaders' and its modules' resource kinds'. */
    public List<String> resourceFolderNames() {
        Set<String> folders = new TreeSet<>();
        for (ResourceLoader<?> loader : loaders.values()) {
            folders.add(loader.getFolderName());
        }
        folders.addAll(packResources.moduleFolderNames());
        return List.copyOf(folders);
    }

    private void recoverStructureTransactions() {
        StructureRecoveryResult recovery = new StructureTransactionWriter(dataFolder.toPath())
                .recoverIncompleteTransactions();
        if (recovery.successful()) {
            if (recovery.recoveredTransactions() > 0) {
                IrisLogging.warn("Recovered " + recovery.recoveredTransactions()
                        + " interrupted structure authoring transaction(s) in " + dataFolder);
            }
            return;
        }
        IllegalStateException failure = new IllegalStateException(
                "Unable to recover interrupted structure authoring transactions in " + dataFolder);
        for (StructureRecoveryResult.Failure recoveryFailure : recovery.failures()) {
            failure.addSuppressed(new IOException(
                    "Recovery failed for " + recoveryFailure.transactionRoot(),
                    recoveryFailure.cause()
            ));
        }
        IrisLogging.reportError(failure);
        throw failure;
    }

    public void clearLists() {
        for (ResourceLoader<?> i : loaders.values()) {
            i.clearList();
        }
        possibleSnippets.clear();
    }

    private void invalidateLoader(ResourceLoader<?> loader) {
        if (loader == null) {
            return;
        }
        loader.clearCache();
        loader.clearList();
    }

    private static Path dataFolderIdentity(File dataFolder) {
        Path normalized = dataFolder.toPath().toAbsolutePath().normalize();
        try {
            return Files.exists(normalized) ? normalized.toRealPath() : normalized;
        } catch (IOException exception) {
            IrisLogging.debug("Unable to resolve Iris data folder identity for "
                    + normalized + "; using its normalized path: " + exception.getMessage());
            return normalized;
        }
    }

    public Set<Class<?>> resolveSnippets() {
        var result = new HashSet<Class<?>>();
        var processed = new HashSet<Class<?>>();

        var queue = new LinkedList<Class<?>>(loaders.keySet());
        while (!queue.isEmpty()) {
            var type = queue.poll();
            if (shouldSkipClass(type) || !processed.add(type))
                continue;
            if (type.isAnnotationPresent(Snippet.class))
                result.add(type);

            try {
                for (var field : type.getDeclaredFields()) {
                    if (shouldSkipField(new FieldAttributes(field)))
                        continue;

                    queue.add(field.getType());
                }
            } catch (Throwable ignored) {
            }
        }

        return result;
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
            IrisLogging.error("Forign file from loader " + f.getPath() + " (loader realm: " + getDataFolder().getPath() + ")");
        }

        IrisLogging.error("Failed to load " + f.getPath() + " (loader realm: " + getDataFolder().getPath() + ")");

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
        String snippetType = snippetFolder(typeToken.getRawType());
        if (snippetType == null) {
            return null;
        }

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
                            IrisLogging.error("Couldn't read snippet " + r + " in " + reader.getPath() + " (" + e.getMessage() + ")");
                        }
                    } else {
                        IrisLogging.error("Couldn't find snippet " + r + " in " + reader.getPath());
                    }

                    return null;
                }

                try {
                    return adapter.read(reader);
                } catch (Throwable e) {
                    IrisLogging.error("Failed to read " + typeToken.getRawType().getCanonicalName() + "... faking objects a little to load the file at least.");
                    IrisLogging.reportError(e);
                    try {
                        return (T) typeToken.getRawType().getConstructor().newInstance();
                    } catch (Throwable ignored) {

                    }
                }
                return null;
            }
        };
    }

    /** The snippet folder of a class Iris's or a built-in module's {@code @Snippet} names, or null. */
    private static String snippetFolder(Class<?> type) {
        if (type.isAnnotationPresent(Snippet.class)) {
            return type.getDeclaredAnnotation(Snippet.class).value();
        }
        if (type.isAnnotationPresent(com.volmit.config.Snippet.class)) {
            return type.getDeclaredAnnotation(com.volmit.config.Snippet.class).value();
        }
        return null;
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
                IrisLogging.reportError("Failed to scan Iris snippets in " + snippetFolder + ".", e);
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
        IrisLogging.info("Saved Prefetch Cache to speed up future world startups");
    }

    public void loadPrefetch(Engine engine) {
        for (ResourceLoader<?> loader : loaders.values()) {
            try {
                loader.loadFirstAccess(engine);
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }
        }
        IrisLogging.debug("Loaded Prefetch Cache to reduce generation disk use.");
    }
}
