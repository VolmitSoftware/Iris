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

package art.arcane.iris.pack.loading;

import art.arcane.iris.pack.validation.CompatStatus;
import art.arcane.iris.pack.validation.ContentGate;
import art.arcane.iris.pack.validation.PackCompatReport;
import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.pack.PackDirectoryResolver;
import art.arcane.volmlib.util.cache.AtomicCache;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisEngineStreamType;
import art.arcane.iris.generation.decoration.IrisProceduralBlocks;
import art.arcane.iris.world.loot.IrisBlockDrops;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.iris.structure.placement.IrisStructureLocator;
import art.arcane.iris.structure.graph.StructureGraphCatalog;
import art.arcane.iris.world.history.GenerationPackFingerprint;
import art.arcane.iris.world.history.GenerationRegistryContract;
import art.arcane.iris.world.history.GenerationRegistryContractFactory;
import art.arcane.iris.structure.authoring.StructureRecoveryResult;
import art.arcane.iris.structure.authoring.StructureTransactionWriter;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.biome.IrisBiomeCustom;
import art.arcane.iris.spi.PlatformGenerationRegistry;
import art.arcane.iris.generation.block.IrisBlockData;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.world.entity.IrisEntity;
import art.arcane.iris.generation.noise.IrisExpression;
import art.arcane.iris.structure.object.IrisObjectScale;
import art.arcane.iris.generation.noise.IrisGenerator;
import art.arcane.iris.generation.image.IrisImage;
import art.arcane.iris.generation.image.IrisImageMap;
import art.arcane.iris.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.structure.jigsaw.IrisJigsawPool;
import art.arcane.iris.world.loot.IrisLootTable;
import art.arcane.iris.world.entity.IrisMarker;
import art.arcane.iris.pack.mod.IrisMod;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.world.entity.IrisSpawner;
import art.arcane.iris.structure.placement.IrisStructure;
import art.arcane.iris.pack.schema.annotation.Snippet;
import art.arcane.iris.structure.object.matter.IrisMatterObject;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.mantle.flag.MantleFlagAdapter;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.iris.generation.concurrent.BurstExecutor;
import art.arcane.iris.generation.concurrent.MultiBurst;
import art.arcane.iris.platform.reflect.KeyedType;
import art.arcane.volmlib.util.scheduling.ChronoLatch;
import art.arcane.iris.world.task.J;
import art.arcane.iris.generation.context.IrisContext;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class IrisData implements ExclusionStrategy, TypeAdapterFactory {
    private static final int CUSTOM_BIOME_KEY_CACHE_LIMIT = 4096;
    private static final Map<File, IrisData> dataLoaders = new ConcurrentHashMap<>();
    // Loaders (cached or detached) that currently have registered engines; see hasActiveEngines.
    // Identity-keyed on purpose: Lombok's @Data hashCode over this class's mutable loader state
    // drifts while an engine runs, so a hashing set would silently fail removal and pin every
    // engine-hosting IrisData (and its pack graph) for the JVM lifetime.
    private static final Set<IrisData> ENGINE_HOLDERS =
            Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
    private final File dataFolder;
    private final transient PackCompatReport compatReport = new PackCompatReport();
    private transient volatile ContentGate contentGate;
    private final int id;
    private final boolean datapackCompiler;
    private volatile boolean closed = false;
    private ResourceLoader<IrisBiome> biomeLoader;
    private ResourceLoader<IrisLootTable> lootLoader;
    private ResourceLoader<IrisRegion> regionLoader;
    private ResourceLoader<IrisDimension> dimensionLoader;
    private ResourceLoader<IrisGenerator> generatorLoader;
    private ResourceLoader<IrisEntity> entityLoader;
    private ResourceLoader<IrisMarker> markerLoader;
    private ResourceLoader<IrisSpawner> spawnerLoader;
    private ResourceLoader<IrisMod> modLoader;
    private ResourceLoader<IrisBlockData> blockLoader;
    private ResourceLoader<IrisExpression> expressionLoader;
    private ResourceLoader<IrisObject> objectLoader;
    private ResourceLoader<IrisMatterObject> matterLoader;
    private ResourceLoader<IrisImage> imageLoader;
    private ResourceLoader<IrisImageMap> imageMapLoader;
    private ResourceLoader<IrisStructure> structureLoader;
    private ResourceLoader<IrisJigsawPool> jigsawPoolLoader;
    private ResourceLoader<IrisJigsawPiece> jigsawPieceLoader;
    private KMap<String, KList<String>> possibleSnippets;
    private Gson gson;
    private Gson snippetLoader;
    private GsonBuilder builder;
    private volatile KMap<Class<? extends IrisRegistrant>, ResourceLoader<? extends IrisRegistrant>> loaders = new KMap<>();
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private final transient List<Engine> engines = new ArrayList<>();
    private final transient Map<IrisDimension, Map<String, String>> dimensionCustomBiomeResourceKeys =
            Collections.synchronizedMap(new IdentityHashMap<>());
    private transient volatile String generationPackFingerprint;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @EqualsAndHashCode.Exclude
    private transient volatile GenerationRegistryContract generationRegistryContract;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @EqualsAndHashCode.Exclude
    private transient volatile GenerationRegistryContractFactory.CustomBiomeResourceResolver customBiomeResourceResolver;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @EqualsAndHashCode.Exclude
    private transient volatile CustomBiomeKeyCache customBiomeKeyCache;
    private transient volatile Engine soleEngine;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private transient volatile Set<String> blockDropMaterials;

    private IrisData(File dataFolder) {
        this(dataFolder, false);
    }

    private IrisData(File dataFolder, boolean datapackCompiler) {
        this.dataFolder = dataFolder;
        this.id = RNG.r.imax();
        this.datapackCompiler = datapackCompiler;
        if (datapackCompiler) {
            hotloadedDatapackCompiler();
        } else {
            hotloaded();
        }
    }

    public static IrisData get(File dataFolder) {
        return dataLoaders.computeIfAbsent(dataFolder, IrisData::new);
    }

    public static IrisData openRuntime(File dataFolder) {
        return new IrisData(dataFolder);
    }

    public static IrisData openDatapackCompiler(File dataFolder) {
        return new IrisData(dataFolder, true);
    }

    public static Optional<IrisData> getLoaded(File dataFolder) {
        return Optional.ofNullable(dataLoaders.get(dataFolder));
    }

    public static void invalidateLoadedAuthoringResources(File source) {
        Path requested = dataFolderIdentity(Objects.requireNonNull(source, "authoring pack source"));
        for (Map.Entry<File, IrisData> entry : dataLoaders.entrySet()) {
            if (dataFolderIdentity(entry.getKey()).equals(requested)) {
                entry.getValue().invalidateAuthoringResources();
            }
        }
    }

    public static void invalidateLoadedContentRegistries(File source) {
        Path requested = dataFolderIdentity(Objects.requireNonNull(source, "authoring pack source"));
        for (Map.Entry<File, IrisData> entry : dataLoaders.entrySet()) {
            IrisData data = entry.getValue();
            if (dataFolderIdentity(entry.getKey()).equals(requested)) {
                synchronized (data) {
                    if (data.generationRegistryContract == null && data.getEngines().isEmpty()) {
                        data.invalidateAuthoringResources();
                        data.compatReport.clear();
                    }
                }
            }
        }
    }

    private synchronized void invalidateAuthoringResources() {
        if (generationRegistryContract != null) {
            throw new IllegalStateException("Cannot invalidate an immutable generation pack as an authoring source.");
        }
        dump();
        clearLists();
        generationPackFingerprint = null;
        contentGate = null;
        dimensionCustomBiomeResourceKeys.clear();
    }

    public String customBiomeResourceKey(IrisDimension dimension, IrisBiomeCustom customBiome) {
        IrisDimension requiredDimension = Objects.requireNonNull(dimension, "dimension");
        IrisBiomeCustom requiredBiome = Objects.requireNonNull(customBiome, "customBiome");
        GenerationRegistryContractFactory.CustomBiomeResourceResolver resolver = customBiomeResourceResolver;
        ContentGate gate = resolver == null ? getContentGate() : null;
        Object owner = resolver == null ? gate : resolver;
        CustomBiomeKeyCache cache = customBiomeKeyCache;
        if (cache == null || cache.owner() != owner) {
            cache = new CustomBiomeKeyCache(owner, new ConcurrentHashMap<>());
            customBiomeKeyCache = cache;
        }
        CustomBiomeKey key = new CustomBiomeKey(requiredDimension.getLoadKey(), requiredBiome);
        String cached = cache.keys().get(key);
        if (cached != null) {
            return cached;
        }
        String resolved = resolveCustomBiomeResourceKey(requiredDimension, requiredBiome, resolver, gate);
        if (cache.keys().size() >= CUSTOM_BIOME_KEY_CACHE_LIMIT) {
            cache.keys().clear();
        }
        cache.keys().put(key, resolved);
        return resolved;
    }

    private String resolveCustomBiomeResourceKey(
            IrisDimension requiredDimension,
            IrisBiomeCustom requiredBiome,
            GenerationRegistryContractFactory.CustomBiomeResourceResolver resolver,
            ContentGate gate
    ) {
        if (resolver != null) {
            try {
                return resolver.resolve(
                        requiredDimension.getLoadKey(),
                        requiredBiome
                );
            } catch (IOException failure) {
                throw new IllegalStateException("Immutable Iris epoch has no registry mapping for custom biome '"
                        + requiredDimension.getLoadKey() + ":" + requiredBiome.getId() + "'.", failure);
            }
        }
        return GenerationRegistryContractFactory.customBiomeResourceKey(
                requiredDimension.getLoadKey(),
                requiredBiome,
                gate
        );
    }

    public String customBiomeResourceKey(IrisDimension dimension, String customBiomeId) {
        IrisDimension requiredDimension = Objects.requireNonNull(dimension, "dimension");
        String logicalKey = requiredDimension.getLoadKey().toLowerCase(Locale.ROOT)
                + ":" + Objects.requireNonNull(customBiomeId, "customBiomeId").toLowerCase(Locale.ROOT);
        String physicalKey = customBiomeResourceKeys(requiredDimension).get(logicalKey);
        if (physicalKey == null) {
            throw new IllegalArgumentException("Unknown custom biome '" + logicalKey + "'.");
        }
        return physicalKey;
    }

    public String physicalBiomeResourceKey(IrisDimension dimension, String resourceKey) {
        if (resourceKey == null || resourceKey.isBlank()) {
            return resourceKey;
        }
        String normalizedKey = resourceKey.trim().toLowerCase(Locale.ROOT);
        if (!normalizedKey.contains(":")) {
            normalizedKey = "minecraft:" + normalizedKey;
        }
        return customBiomeResourceKeys(Objects.requireNonNull(dimension, "dimension"))
                .getOrDefault(normalizedKey, normalizedKey);
    }

    public String generationPackFingerprint() {
        String fingerprint = generationPackFingerprint;
        if (fingerprint != null) {
            return fingerprint;
        }
        synchronized (this) {
            fingerprint = generationPackFingerprint;
            if (fingerprint != null) {
                return fingerprint;
            }
            try {
                fingerprint = GenerationPackFingerprint.compute(
                        dataFolder.toPath(),
                        GenerationPackFingerprint.CURRENT_VERSION
                );
            } catch (IOException failure) {
                throw new IllegalStateException("Unable to fingerprint Iris generation pack at "
                        + dataFolder + ".", failure);
            }
            generationPackFingerprint = fingerprint;
            return fingerprint;
        }
    }

    public boolean hasGenerationRegistryContract() {
        return generationRegistryContract != null;
    }

    public synchronized void bindGenerationRegistryContract(GenerationRegistryContract contract) {
        GenerationRegistryContract required = Objects.requireNonNull(contract, "contract");
        if (generationRegistryContract != null && !generationRegistryContract.equals(required)) {
            throw new IllegalStateException("Iris data is already bound to another generation registry contract.");
        }
        generationRegistryContract = required;
        customBiomeResourceResolver = new GenerationRegistryContractFactory.CustomBiomeResourceResolver(
                required,
                PlatformGenerationRegistry::contentAddressedCustomBiomeResourceKey
        );
        synchronized (dimensionCustomBiomeResourceKeys) {
            dimensionCustomBiomeResourceKeys.clear();
        }
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

    public static IrisMod loadAnyMod(String key, @Nullable IrisData nearest) {
        return loadAny(IrisMod.class, key, nearest);
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

    public static IrisRegion loadAnyRegion(String key, @Nullable IrisData nearest) {
        return loadAny(IrisRegion.class, key, nearest);
    }

    public static IrisMarker loadAnyMarker(String key, @Nullable IrisData nearest) {
        return loadAny(IrisMarker.class, key, nearest);
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

    public static IrisJigsawPool loadAnyJigsawPool(String key, @Nullable IrisData nearest) {
        return loadAny(IrisJigsawPool.class, key, nearest);
    }

    public static IrisJigsawPiece loadAnyJigsawPiece(String key, @Nullable IrisData nearest) {
        return loadAny(IrisJigsawPiece.class, key, nearest);
    }

    public static <T extends IrisRegistrant> T loadAny(Class<T> type, String key, @Nullable IrisData nearest) {
        try {
            if (nearest != null) {
                T t = nearest.load(type, key, false);
                if (t != null || nearest.generationRegistryContract != null) {
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
            refreshSoleEngine();
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

        // Every noise sample of a thread without a generation context lands here, so the sole
        // engine is answered from a snapshot; the registry lock is only taken to refresh it.
        Engine sole = soleEngine;
        if (sole != null && !sole.isClosed()) {
            return sole;
        }
        synchronized (engines) {
            removeClosedEngines();
            refreshSoleEngine();
            return soleEngine;
        }
    }

    /** Must be called with the engines lock held after every change to the registry. */
    private void refreshSoleEngine() {
        soleEngine = engines.size() == 1 ? engines.get(0) : null;
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
            refreshSoleEngine();
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
            refreshSoleEngine();
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
        if (t == null) {
            return;
        }
        try {
            t.setCompat(t.evaluateCompat(getContentGate()));
        } catch (Throwable e) {
            IrisLogging.reportError(e);
            t.setCompat(CompatStatus.OK);
        }
    }

    /** Every version-content gating decision made for this pack instance. */
    public PackCompatReport getCompatReport() {
        return compatReport;
    }

    /** The version-content gate for this pack instance (lazy; rebuilt on hotload with the loaders). */
    public ContentGate getContentGate() {
        ContentGate gate = contentGate;
        if (gate == null) {
            synchronized (this) {
                gate = contentGate;
                if (gate == null) {
                    gate = ContentGate.forData(this);
                    contentGate = gate;
                }
            }
        }
        return gate;
    }

    public void close() {
        closed = true;
        dump();
        synchronized (engines) {
            engines.clear();
            refreshSoleEngine();
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
            ResourceLoader.Options options = datapackCompiler
                    ? ResourceLoader.Options.datapackCompiler()
                    : ResourceLoader.Options.runtime();
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
        blockDropMaterials = null;
        StructureGraphCatalog.invalidate(this);
        IrisObjectScale.invalidate(this);
        contentGate = null;
        generationPackFingerprint = null;
        synchronized (dimensionCustomBiomeResourceKeys) {
            dimensionCustomBiomeResourceKeys.clear();
        }
        compatReport.clear();
        closed = false;
        possibleSnippets = new KMap<>();
        builder = new GsonBuilder()
                .addDeserializationExclusionStrategy(this)
                .addSerializationExclusionStrategy(this)
                .setLenient()
                .registerTypeAdapterFactory(this)
                .registerTypeAdapter(MantleFlag.class, new MantleFlagAdapter())
                .setPrettyPrinting();
        KMap<Class<? extends IrisRegistrant>, ResourceLoader<? extends IrisRegistrant>> replacement = new KMap<>();
        File packs = dataFolder;
        packs.mkdirs();
        recoverStructureTransactions();
        this.lootLoader = registerLoader(IrisLootTable.class, replacement);
        this.spawnerLoader = registerLoader(IrisSpawner.class, replacement);
        this.entityLoader = registerLoader(IrisEntity.class, replacement);
        this.regionLoader = registerLoader(IrisRegion.class, replacement);
        this.biomeLoader = registerLoader(IrisBiome.class, replacement);
        this.modLoader = registerLoader(IrisMod.class, replacement);
        this.dimensionLoader = registerLoader(IrisDimension.class, replacement);
        this.generatorLoader = registerLoader(IrisGenerator.class, replacement);
        this.markerLoader = registerLoader(IrisMarker.class, replacement);
        this.blockLoader = registerLoader(IrisBlockData.class, replacement);
        this.expressionLoader = registerLoader(IrisExpression.class, replacement);
        this.objectLoader = registerLoader(IrisObject.class, replacement);
        this.imageLoader = registerLoader(IrisImage.class, replacement);
        this.imageMapLoader = registerLoader(IrisImageMap.class, replacement);
        this.matterLoader = registerLoader(IrisMatterObject.class, replacement);
        this.structureLoader = registerLoader(IrisStructure.class, replacement);
        this.jigsawPoolLoader = registerLoader(IrisJigsawPool.class, replacement);
        this.jigsawPieceLoader = registerLoader(IrisJigsawPiece.class, replacement);
        builder.registerTypeAdapterFactory(KeyedType::createTypeAdapter);

        gson = builder.create();
        loaders = replacement;

        for (Engine engine : getEngines()) {
            engine.hotload();
        }
    }

    private void hotloadedDatapackCompiler() {
        closed = false;
        possibleSnippets = new KMap<>();
        builder = new GsonBuilder()
                .addDeserializationExclusionStrategy(this)
                .addSerializationExclusionStrategy(this)
                .setLenient()
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

    public void dump() {
        blockDropMaterials = null;
        StructureGraphCatalog.invalidate(this);
        IrisObjectScale.invalidate(this);
        for (ResourceLoader<?> i : loaders.values()) {
            i.clearCache();
        }
    }

    public synchronized void invalidateStructureResources() {
        StructureGraphCatalog.invalidate(this);
        invalidateLoader(objectLoader);
        invalidateLoader(structureLoader);
        invalidateLoader(jigsawPoolLoader);
        invalidateLoader(jigsawPieceLoader);
        for (Engine engine : getEngines()) {
            IrisStructureLocator.invalidate(engine);
        }
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
        blockDropMaterials = null;
        for (ResourceLoader<?> i : loaders.values()) {
            i.clearList();
        }
        possibleSnippets.clear();
    }

    public boolean hasBlockDropRules(String material) {
        Set<String> materials = blockDropMaterials;
        return materials == null || materials.contains(material);
    }

    public void prepareBlockDropRules() {
        Set<String> materials = new HashSet<>();
        for (IrisDimension dimension : dimensionLoader.loadAll(dimensionLoader.getPossibleKeys())) {
            collectBlockDropMaterials(materials, dimension.getBlockDrops());
        }
        for (IrisRegion region : regionLoader.loadAll(regionLoader.getPossibleKeys())) {
            collectBlockDropMaterials(materials, region.getBlockDrops());
        }
        for (IrisBiome biome : biomeLoader.loadAll(biomeLoader.getPossibleKeys())) {
            collectBlockDropMaterials(materials, biome.getBlockDrops());
        }
        blockDropMaterials = Set.copyOf(materials);
    }

    private void collectBlockDropMaterials(Set<String> materials, KList<IrisBlockDrops> rules) {
        for (IrisBlockDrops rule : rules) {
            for (IrisBlockData block : rule.getBlocks()) {
                NativeBlockState state = block.getBlockData(this);
                if (state != null) {
                    materials.add(IrisProceduralBlocks.materialKey(state.placementBaseState()));
                }
            }
        }
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
        Path root = getDataFolder().toPath().toAbsolutePath().normalize();
        Path resource = f.toPath().toAbsolutePath().normalize();
        if (resource.startsWith(root)) {
            Path relative = root.relativize(resource);
            if (relative.getNameCount() > 1) {
                String key = relative.subpath(1, relative.getNameCount()).toString().replace(File.separatorChar, '/');
                int extension = key.lastIndexOf('.');
                return extension > key.lastIndexOf('/') ? key.substring(0, extension) : key;
            }
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
        String listedSnippetType = snippetTypeListedBy(typeToken);
        if (listedSnippetType != null) {
            return snippetListAdapter(gson, typeToken, listedSnippetType);
        }

        String snippetType = snippetTypeOf(typeToken.getRawType());
        if (snippetType == null) {
            return null;
        }

        return new TypeAdapter<>() {
            @Override
            public void write(JsonWriter jsonWriter, T t) throws IOException {
                gson.getDelegateAdapter(IrisData.this, typeToken).write(jsonWriter, t);
            }

            @Override
            public T read(JsonReader reader) throws IOException {
                TypeAdapter<T> adapter = gson.getDelegateAdapter(IrisData.this, typeToken);
                String path = reader.getPath();

                if (reader.peek() == JsonToken.STRING) {
                    return readSnippetReference(adapter, snippetType, reader.nextString(), path);
                }

                JsonElement value = gson.getAdapter(JsonElement.class).read(reader);
                try {
                    return adapter.fromJsonTree(value);
                } catch (IrisEngineStreamType.UnknownStreamException unknownStream) {
                    throw unknownStream;
                } catch (Throwable e) {
                    IrisLogging.error("Failed to read " + typeToken.getRawType().getCanonicalName() + " at " + path
                            + " (" + e.getMessage() + "), using defaults");
                    IrisLogging.reportError(e);
                    return defaultInstance(typeToken);
                }
            }
        };
    }

    private <T> @Nullable T readSnippetReference(TypeAdapter<T> adapter, String snippetType, String reference, String path) {
        JsonElement snippet = readSnippetFile(snippetType, reference, path);
        if (snippet == null) {
            return null;
        }

        if (snippet.isJsonArray()) {
            IrisLogging.error("Snippet " + reference + " in " + path + " holds several entries, which only a list can take");
            return null;
        }

        try {
            return adapter.fromJsonTree(snippet);
        } catch (IrisEngineStreamType.UnknownStreamException unknownStream) {
            throw unknownStream;
        } catch (Throwable e) {
            IrisLogging.error("Couldn't read snippet " + reference + " in " + path + " (" + e.getMessage() + ")");
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> @Nullable T defaultInstance(TypeToken<T> typeToken) {
        try {
            return (T) typeToken.getRawType().getConstructor().newInstance();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static @Nullable String snippetTypeOf(Class<?> type) {
        Snippet snippet = type.getDeclaredAnnotation(Snippet.class);
        return snippet == null ? null : snippet.value();
    }

    private static @Nullable String snippetTypeListedBy(TypeToken<?> typeToken) {
        if (!Collection.class.isAssignableFrom(typeToken.getRawType())
                || !(typeToken.getType() instanceof ParameterizedType listType)) {
            return null;
        }

        Type[] elementTypes = listType.getActualTypeArguments();
        if (elementTypes.length != 1 || !(elementTypes[0] instanceof Class<?> elementType)) {
            return null;
        }

        return snippetTypeOf(elementType);
    }

    /** A snippet file holding a JSON array stands for all of its entries, so inside a list it becomes that many items. */
    private <T> TypeAdapter<T> snippetListAdapter(Gson gson, TypeToken<T> listType, String snippetType) {
        TypeAdapter<T> listAdapter = gson.getDelegateAdapter(this, listType);

        return new TypeAdapter<>() {
            @Override
            public void write(JsonWriter writer, T list) throws IOException {
                listAdapter.write(writer, list);
            }

            @Override
            public T read(JsonReader reader) throws IOException {
                if (reader.peek() != JsonToken.BEGIN_ARRAY) {
                    return listAdapter.read(reader);
                }

                String path = reader.getPath();
                JsonArray entries = gson.getAdapter(JsonArray.class).read(reader);
                return listAdapter.fromJsonTree(withSnippetsInlined(entries, snippetType, path));
            }
        };
    }

    /** A snippet that can't be read keeps its slot as null, the way a single snippet reference loads. */
    private JsonArray withSnippetsInlined(JsonArray entries, String snippetType, String path) {
        JsonArray inlined = new JsonArray();
        for (int index = 0; index < entries.size(); index++) {
            JsonElement entry = entries.get(index);
            if (!isSnippetReference(entry)) {
                inlined.add(entry);
                continue;
            }

            JsonElement snippet = readSnippetFile(snippetType, entry.getAsString(), path + "[" + index + "]");
            if (snippet == null) {
                inlined.add(JsonNull.INSTANCE);
            } else if (snippet.isJsonArray()) {
                entriesOfSnippet(snippet.getAsJsonArray()).forEach(inlined::add);
            } else {
                inlined.add(snippet);
            }
        }

        return inlined;
    }

    /** The entries a snippet file holding a list stands for. Lenient parsing reads a trailing comma as a null entry. */
    public static List<JsonElement> entriesOfSnippet(JsonArray snippet) {
        return snippet.asList().stream().filter(entry -> !entry.isJsonNull()).toList();
    }

    private static boolean isSnippetReference(JsonElement entry) {
        return entry.isJsonPrimitive()
                && entry.getAsJsonPrimitive().isString()
                && entry.getAsString().startsWith(SnippetFolder.PREFIX);
    }

    /** The parsed snippet file, or null when the reference isn't a snippet or the file can't be read. */
    private @Nullable JsonElement readSnippetFile(String snippetType, String reference, String path) {
        if (!reference.startsWith(SnippetFolder.PREFIX)) {
            return null;
        }

        SnippetFolder.Lookup lookup = new SnippetFolder(snippetType).find(getDataFolder(), reference);
        if (!lookup.found()) {
            IrisLogging.error("Couldn't find snippet " + lookup.key() + " in " + path);
            return null;
        }

        try (JsonReader snippetReader = new JsonReader(new FileReader(lookup.file()))) {
            snippetReader.setStrictness(Strictness.LENIENT);
            return JsonParser.parseReader(snippetReader);
        } catch (Throwable e) {
            IrisLogging.error("Couldn't read snippet " + lookup.key() + " in " + path + " (" + e.getMessage() + ")");
            return null;
        }
    }

    /** Every snippet of the type, as "snippet/&lt;path inside its folder&gt;". */
    public KList<String> getPossibleSnippets(String snippetType) {
        return possibleSnippets.computeIfAbsent(snippetType, (k) -> {
            File snippetFolder = new File(getDataFolder(), new SnippetFolder(snippetType).path());
            return new KList<>(listSnippetReferences(snippetFolder));
        });
    }

    private static List<String> listSnippetReferences(File snippetFolder) {
        if (!snippetFolder.exists()) {
            return List.of();
        }

        String absPath = snippetFolder.getAbsolutePath();
        try (var stream = Files.walk(snippetFolder.toPath())) {
            return stream.filter(Files::isRegularFile)
                    .map(Path::toAbsolutePath)
                    .map(Path::toString)
                    .filter(s -> s.endsWith(".json"))
                    .map(s -> s.substring(absPath.length() + 1))
                    .map(s -> s.replace("\\", "/"))
                    .map(s -> s.substring(0, s.length() - ".json".length()))
                    .map(s -> SnippetFolder.PREFIX + s)
                    .toList();
        } catch (Throwable e) {
            IrisLogging.reportError("Failed to scan Iris snippets in " + snippetFolder + ".", e);
            return List.of();
        }
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

    private Map<String, String> customBiomeResourceKeys(IrisDimension dimension) {
        synchronized (dimensionCustomBiomeResourceKeys) {
            Map<String, String> cached = dimensionCustomBiomeResourceKeys.get(dimension);
            if (cached != null) {
                return cached;
            }
            String namespace = dimension.getLoadKey().toLowerCase(Locale.ROOT);
            Map<String, String> resolved = new LinkedHashMap<>();
            List<IrisBiome> registryBiomes = new ArrayList<>(dimension.getAllBiomes(() -> this));
            registryBiomes.addAll(dimension.getReachableBiomes(() -> this));
            for (IrisBiome biome : registryBiomes) {
                if (biome == null || !biome.isCustom()) {
                    continue;
                }
                for (IrisBiomeCustom customBiome : biome.getCustomDerivitives()) {
                    if (customBiome == null || customBiome.getId() == null || customBiome.getId().isBlank()) {
                        continue;
                    }
                    String logicalKey = namespace + ":" + customBiome.getId().toLowerCase(Locale.ROOT);
                    String physicalKey = customBiomeResourceKey(dimension, customBiome);
                    resolved.merge(logicalKey, physicalKey,
                            (first, second) -> first.compareTo(second) <= 0 ? first : second);
                }
            }
            Map<String, String> immutable = Map.copyOf(resolved);
            dimensionCustomBiomeResourceKeys.put(dimension, immutable);
            return immutable;
        }
    }

    private record CustomBiomeKeyCache(Object owner, ConcurrentHashMap<CustomBiomeKey, String> keys) {
    }

    private static final class CustomBiomeKey {
        private final String dimensionKey;
        private final IrisBiomeCustom biome;
        private final int hash;

        private CustomBiomeKey(String dimensionKey, IrisBiomeCustom biome) {
            this.dimensionKey = dimensionKey;
            this.biome = biome;
            hash = 31 * Objects.hashCode(dimensionKey) + System.identityHashCode(biome);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof CustomBiomeKey key && key.biome == biome && Objects.equals(key.dimensionKey, dimensionKey);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
