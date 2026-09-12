package art.arcane.iris.world;

import art.arcane.iris.configuration.IrisSettings;

import art.arcane.iris.world.lifecycle.MissingWorldStorageLog;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.PackDownloader;
import art.arcane.iris.studio.StudioSVC;
import art.arcane.iris.generation.cache.AtomicCache;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.platform.bootstrap.ServerProperties;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.io.IO;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Type;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class IrisWorlds {
    private static final String IRIS_GENERATOR_NAME = "iris";
    private static final String IRIS_GENERATOR_PREFIX = "iris:";
    private static final String IRIS_NAMESPACE = "iris";
    private static final AtomicCache<IrisWorlds> cache = new AtomicCache<>();
    private static final Set<String> UNUSABLE_STORAGE_REPORTED = ConcurrentHashMap.newKeySet();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = TypeToken.getParameterized(KMap.class, String.class, String.class).getType();
    private final Path levelRoot;
    private final Path registryFile;
    private final KMap<String, String> worlds;
    private volatile boolean dirty = false;

    private IrisWorlds(Path levelRoot, KMap<String, String> worlds) {
        this.levelRoot = Objects.requireNonNull(levelRoot, "levelRoot").toAbsolutePath().normalize();
        registryFile = registryFile(this.levelRoot);
        this.worlds = new KMap<>();
        // One unreadable entry drops itself. This runs behind a static cache whose callers all dereference
        // the result immediately, so anything thrown here stops Iris from enabling for every world.
        worlds.forEach((identity, type) -> {
            try {
                this.worlds.put(WorldIdentity.parse(identity).toString(), type);
            } catch (IllegalArgumentException unreadable) {
                IrisLogging.warn("Dropping unreadable worlds.json entry %s: %s", identity, unreadable.getMessage());
                dirty = true;
            }
        });
        readBukkitWorlds(this.levelRoot).forEach((name, type) -> put0(
                IrisWorldStorage.keyFromConfiguredWorldName(name, this.levelRoot.getFileName().toString()).toString(),
                type));
        save();
    }

    /**
     * The world registry, never null.
     * <p>
     * A failure here used to be swallowed into a null return, and every caller dereferences the result
     * immediately, so a single unusable world folder turned into an NPE that stopped Iris from enabling -
     * and a server whose Iris worlds then generated vanilla terrain over their own region files. Per-world
     * storage problems are isolated in {@link #clean()} and {@link #loadDimension(String, String)}; anything
     * that gets past those is a real failure and is raised, not hidden.
     */
    public static IrisWorlds get() {
        return cache.aquireOnceOrThrow(() -> {
            Path levelRoot = IrisWorldStorage.levelRoot().toPath().toAbsolutePath().normalize();
            File file = registryFile(levelRoot).toFile();
            if (!file.exists()) {
                return new IrisWorlds(levelRoot, new KMap<>());
            }

            try {
                String json = IO.readAll(file);
                KMap<String, String> worlds = GSON.fromJson(json, TYPE);
                return new IrisWorlds(levelRoot, Objects.requireNonNullElseGet(worlds, KMap::new));
            } catch (Throwable e) {
                IrisLogging.error("Failed to read worlds.json for level root " + levelRoot
                        + "; rebuilding it from bukkit.yml.");
                IrisLogging.reportError(e);
            }

            return new IrisWorlds(levelRoot, new KMap<>());
        });
    }

    public synchronized void put(String identity, String type) {
        String canonicalIdentity = WorldIdentity.parse(identity).toString();
        String requiredType = Objects.requireNonNull(type, "type");
        String previous = worlds.put(canonicalIdentity, requiredType);
        if (requiredType.equals(previous)) {
            return;
        }
        dirty = true;
        try {
            saveOrThrow();
        } catch (IOException e) {
            if (previous == null) {
                worlds.remove(canonicalIdentity);
            } else {
                worlds.put(canonicalIdentity, previous);
            }
            dirty = true;
            throw new UncheckedIOException("Failed to persist Iris world registry entry for " + canonicalIdentity, e);
        }
    }

    public synchronized boolean remove(String identity) {
        String canonicalIdentity = WorldIdentity.parse(identity).toString();
        String previous = worlds.remove(canonicalIdentity);
        if (previous == null) {
            return false;
        }
        dirty = true;
        try {
            saveOrThrow();
            return true;
        } catch (IOException e) {
            worlds.put(canonicalIdentity, previous);
            dirty = true;
            throw new UncheckedIOException("Failed to remove Iris world registry entry for " + canonicalIdentity, e);
        }
    }

    private void put0(String identity, String type) {
        String canonicalIdentity = WorldIdentity.parse(identity).toString();
        String old = worlds.put(canonicalIdentity, type);
        if (!type.equals(old))
            dirty = true;
    }

    public synchronized KMap<String, String> getWorlds() {
        clean();
        KMap<String, String> result = new KMap<>();
        readBukkitWorlds(levelRoot).forEach((name, type) -> result.put(
                IrisWorldStorage.keyFromConfiguredWorldName(name, levelRoot.getFileName().toString()).toString(),
                type));
        return result.put(worlds);
    }

    public Stream<IrisData> getPacks() {
        return getDimensions()
                .map(IrisDimension::getLoader)
                .filter(Objects::nonNull);
    }

    /**
     * The dimension one registered world generates from, or null when its pack snapshot cannot supply it.
     * Same resolution {@link #getDimensions()} uses, for callers that hold a single world identity.
     */
    public IrisDimension getDimension(String worldIdentity, String id) {
        return loadDimension(worldIdentity, id);
    }

    public Stream<IrisDimension> getDimensions() {
        return getWorlds()
                .entrySet()
                .stream()
                .map(entry -> loadDimension(entry.getKey(), entry.getValue()))
                .filter(Objects::nonNull);
    }

    /**
     * Drops registry entries whose pack snapshot no longer backs them.
     * <p>
     * A world whose storage is present but unusable is kept and reported instead of dropped: the entry is
     * what lets {@code /iris remove} find the world, and dropping it would not stop the server from loading
     * the folder anyway. It is excluded from {@link #getDimensions()} so one broken world cannot fail the
     * whole registry - that failure used to propagate out of the constructor and null out {@link #get()}.
     */
    public synchronized void clean() {
        boolean removed = worlds.entrySet().removeIf(entry -> {
            try {
                Optional<File> packRoot = packRoot(entry.getKey());
                return packRoot.isEmpty()
                        || !new File(packRoot.get(), "dimensions/" + entry.getValue() + ".json").exists();
            } catch (IllegalArgumentException e) {
                return true;
            } catch (IllegalStateException e) {
                warnUnusableStorage(entry.getKey(), e);
                return false;
            }
        });
        dirty = dirty || removed;
    }

    public synchronized void save() {
        try {
            saveOrThrow();
        } catch (IOException e) {
            IrisLogging.error("Failed to save worlds.json!");
            IrisLogging.reportError(e);
        }
    }

    private void saveOrThrow() throws IOException {
        clean();
        if (!dirty) {
            return;
        }

        Path target = registryFile;
        Path parent = target.getParent();
        if (parent == null) {
            throw new IOException("worlds.json target has no parent: " + target);
        }
        Files.createDirectories(parent);
        Path staged = Files.createTempFile(parent, ".iris-worlds-", ".json");
        try {
            Files.writeString(staged, GSON.toJson(worlds, TYPE), StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(staged, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            try {
                Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
            }
            dirty = false;
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    public static Long readBukkitWorldSeed(String world) {
        YamlConfiguration bukkit = YamlConfiguration.loadConfiguration(ServerProperties.BUKKIT_YML);
        ConfigurationSection worlds = bukkit.getConfigurationSection("worlds");
        if (worlds == null || !worlds.contains(world + ".seed")) {
            return null;
        }

        return worlds.getLong(world + ".seed");
    }

    public static KMap<String, String> readBukkitWorlds() {
        return readBukkitWorlds(IrisWorldStorage.levelRoot().toPath());
    }

    static Path registryFile(Path levelRoot) {
        return Objects.requireNonNull(levelRoot, "levelRoot")
                .toAbsolutePath()
                .normalize()
                .resolve("iris")
                .resolve("worlds.json");
    }

    static KMap<String, String> filterBukkitWorldsByStorage(
            Path levelRoot,
            Map<String, String> configuredWorlds
    ) {
        Path root = Objects.requireNonNull(levelRoot, "levelRoot").toAbsolutePath().normalize();
        Path levelNamePath = root.getFileName();
        if (levelNamePath == null) {
            throw new IllegalArgumentException("Selected level root has no name: " + root);
        }

        KMap<String, String> result = new KMap<>();
        for (Map.Entry<String, String> entry : Objects.requireNonNull(configuredWorlds, "configuredWorlds").entrySet()) {
            String configuredWorldName = entry.getKey();
            NamespacedKey worldKey;
            try {
                worldKey = IrisWorldStorage.keyFromConfiguredWorldName(
                        configuredWorldName,
                        levelNamePath.toString());
                if (!IrisWorldStorage.configuredWorldName(worldKey, levelNamePath.toString())
                        .equals(configuredWorldName)) {
                    continue;
                }
            } catch (IllegalArgumentException notAnIrisWorldName) {
                // A bukkit.yml name that has no Iris identity is somebody else's world, never this
                // registry's, and must not stop the whole registry from being built.
                continue;
            }
            Path worldContainer = root.getParent();
            if (worldContainer == null) {
                throw new IllegalArgumentException("Selected level root has no world container: " + root);
            }
            boolean storagePresent;
            try {
                storagePresent = IrisWorldStorage.frozenDimensionRoot(
                        worldContainer.toFile(),
                        root.toFile(),
                        configuredWorldName,
                        worldKey
                ).isPresent();
            } catch (IllegalStateException unusableStorage) {
                storagePresent = false;
            }
            if (storagePresent) {
                result.put(configuredWorldName, entry.getValue());
                continue;
            }
            // Vanilla slots are absent on every server that never created them; only an Iris world that
            // bukkit.yml still points at is an orphan worth reporting.
            if (IRIS_NAMESPACE.equals(worldKey.getNamespace())) {
                MissingWorldStorageLog.warnOnce(
                        configuredWorldName,
                        root.resolve("dimensions").resolve(IRIS_NAMESPACE).resolve(worldKey.getKey()));
            }
        }
        return result;
    }

    private static KMap<String, String> readBukkitWorlds(Path levelRoot) {
        YamlConfiguration bukkit = YamlConfiguration.loadConfiguration(ServerProperties.BUKKIT_YML);
        ConfigurationSection worlds = bukkit.getConfigurationSection("worlds");
        if (worlds == null) return new KMap<>();

        KMap<String, String> result = new KMap<>();
        String defaultWorldType = IrisSettings.get().getGenerator().getDefaultWorldType();
        for (String world : worlds.getKeys(false)) {
            String loadKey = generatorLoadKey(worlds.getString(world + ".generator"), defaultWorldType);
            if (loadKey == null) continue;

            result.put(world, loadKey);
        }

        return filterBukkitWorldsByStorage(levelRoot, result);
    }

    /**
     * Maps a bukkit.yml generator string onto the Iris pack it selects, or null when the string is
     * not Iris. Bukkit matches generator plugin names case-insensitively, so the prefix does too.
     */
    static String generatorLoadKey(String generator, String defaultWorldType) {
        if (generator == null) return null;
        if (generator.equalsIgnoreCase(IRIS_GENERATOR_NAME)) return defaultWorldType;

        int prefixLength = IRIS_GENERATOR_PREFIX.length();
        if (generator.length() > prefixLength
                && generator.regionMatches(true, 0, IRIS_GENERATOR_PREFIX, 0, prefixLength)) {
            return generator.substring(prefixLength);
        }
        return null;
    }

    private Optional<File> packRoot(String worldIdentity) {
        NamespacedKey worldKey = WorldIdentity.parse(worldIdentity);
        Path worldContainer = levelRoot.getParent();
        if (worldContainer == null) {
            throw new IllegalStateException("Selected level root has no world container: " + levelRoot);
        }
        String configuredWorldName = IrisWorldStorage.configuredWorldName(
                worldKey,
                levelRoot.getFileName().toString()
        );
        Optional<File> dimensionRoot = IrisWorldStorage.frozenDimensionRoot(
                worldContainer.toFile(),
                levelRoot.toFile(),
                configuredWorldName,
                worldKey
        );
        return dimensionRoot.map(IrisWorldStorage::requireActiveGenerationPackRoot);
    }

    private static void warnUnusableStorage(String worldIdentity, IllegalStateException failure) {
        if (!UNUSABLE_STORAGE_REPORTED.add(worldIdentity)) {
            return;
        }
        IrisLogging.error("Iris world %s has unusable world storage and is excluded: %s",
                worldIdentity, failure.getMessage());
        IrisLogging.error("Restore its iris/generation history, or drop the world with /iris remove world=%s delete=true.",
                worldIdentity);
    }

    private IrisDimension loadDimension(String worldIdentity, String id) {
        File pack;
        try {
            pack = packRoot(worldIdentity).orElse(null);
        } catch (IllegalStateException unusableStorage) {
            warnUnusableStorage(worldIdentity, unusableStorage);
            return null;
        }
        IrisDimension dimension = pack == null ? null : IrisData.get(pack).getDimensionLoader().load(id);
        if (dimension == null) {
            dimension = IrisData.loadAnyDimension(id, null);
        }
        if (dimension == null) {
            File packsRoot = IrisPlatforms.get().packsFolderNoCreate();
            if (PackDownloader.isPackPresent(packsRoot, id)) {
                IrisLogging.error("Pack '" + id + "' exists at " + new File(packsRoot, id).getPath()
                        + " but its dimension failed to load; not redownloading. Fix or delete the pack folder.");
                return null;
            }
            IrisLogging.warn("Unable to find dimension type " + id + ". Install it with "
                    + PackDownloader.downloadCommandFor(id) + " and restart the server.");
        }
        return dimension;
    }
}
