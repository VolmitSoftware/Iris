package art.arcane.iris.world;

import art.arcane.iris.world.history.GenerationHistory;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.generator.WorldInfo;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Stream;

public final class IrisWorldStorage {
    private static final String IRIS_NAMESPACE = "iris";
    private static final String DEFAULT_LEVEL_NAME = "world";

    /**
     * Server#getLevelDirectory is Paper-API-only. Once a call throws NoSuchMethodError (plain
     * Spigot/CraftBukkit) this flips and every later call goes straight to the fallback.
     */
    private static volatile boolean levelDirectoryUnavailable;
    private static volatile String cachedFallbackLevelName;

    private IrisWorldStorage() {
    }

    public static File levelRoot() {
        if (!levelDirectoryUnavailable) {
            try {
                return Bukkit.getServer().getLevelDirectory().toAbsolutePath().normalize().toFile();
            } catch (NoSuchMethodError e) {
                levelDirectoryUnavailable = true;
            }
        }
        return new File(Bukkit.getWorldContainer(), fallbackLevelName()).getAbsoluteFile();
    }

    private static String fallbackLevelName() {
        String cached = cachedFallbackLevelName;
        if (cached == null) {
            cached = levelNameFromProperties(new File("server.properties"));
            cachedFallbackLevelName = cached;
        }
        return cached;
    }

    public static String configuredLevelName() {
        return levelNameFromProperties(new File("server.properties"));
    }

    static String levelNameFromProperties(File serverProperties) {
        Properties properties = new Properties();
        if (Objects.requireNonNull(serverProperties, "serverProperties").isFile()) {
            try (InputStream in = new FileInputStream(serverProperties)) {
                properties.load(in);
            } catch (IOException ignored) {
                // Unreadable server.properties: fall through to the default level name.
            }
        }
        return levelNameFromProperties(properties);
    }

    static String levelNameFromProperties(Properties properties) {
        String levelName = Objects.requireNonNull(properties, "properties").getProperty("level-name", DEFAULT_LEVEL_NAME).trim();
        return levelName.isEmpty() ? DEFAULT_LEVEL_NAME : levelName;
    }

    public static File levelRoot(File dimensionRoot) {
        Path current = Objects.requireNonNull(dimensionRoot, "dimensionRoot").toPath().toAbsolutePath().normalize();
        while (current != null) {
            Path fileName = current.getFileName();
            if (fileName != null && "dimensions".equals(fileName.toString())) {
                Path parent = current.getParent();
                if (parent != null) {
                    return parent.toFile();
                }
                break;
            }
            current = current.getParent();
        }
        return dimensionRoot.getAbsoluteFile();
    }

    public static NamespacedKey keyFromName(String worldName) {
        return keyFromName(worldName, levelRoot().getName());
    }

    public static NamespacedKey managedKeyFromName(String worldName) {
        String requestedName = Objects.requireNonNull(worldName, "worldName").trim();
        if (requestedName.contains(":")) {
            return managedKeyFromName(requestedName, DEFAULT_LEVEL_NAME);
        }
        return managedKeyFromName(requestedName, levelRoot().getName());
    }

    public static NamespacedKey managedKeyFromName(String worldName, String levelName) {
        String requestedName = Objects.requireNonNull(worldName, "worldName").trim();
        if (requestedName.isEmpty()) {
            throw new IllegalArgumentException("World name cannot be empty.");
        }
        if (requestedName.contains("/") || requestedName.contains("\\") || requestedName.contains("..")) {
            throw new IllegalArgumentException("World name must be a safe single path segment.");
        }

        NamespacedKey key;
        if (requestedName.contains(":")) {
            key = NamespacedKey.fromString(requestedName.toLowerCase(Locale.ENGLISH));
            if (key == null) {
                throw new IllegalArgumentException("World identifier is invalid: " + requestedName);
            }
        } else {
            // "iris worlds" and Multiverse print the Bukkit startup name (<level>_iris_<key>), so
            // accept that form here instead of turning it into iris:<level>_iris_<key>.
            key = keyFromConfiguredWorldName(requestedName, levelName);
        }

        if (!IRIS_NAMESPACE.equals(key.getNamespace()) || !key.getKey().matches("[a-z0-9_-]+")) {
            throw new IllegalArgumentException("Only Iris-managed dimension worlds can be changed.");
        }
        return key;
    }

    public static NamespacedKey replacementKeyFromName(String requestedName, String levelName) {
        String requested = Objects.requireNonNull(requestedName, "requestedName").trim();
        String configuredLevelName = Objects.requireNonNull(levelName, "levelName").trim();
        if (requested.isEmpty()) {
            throw new IllegalArgumentException("World name cannot be empty.");
        }
        if (configuredLevelName.isEmpty()) {
            throw new IllegalArgumentException("Configured level name cannot be empty.");
        }
        if (requested.contains("/") || requested.contains("\\") || requested.contains("..")) {
            throw new IllegalArgumentException("World name must be a safe single path segment.");
        }

        String normalized = requested.toLowerCase(Locale.ENGLISH);
        if (normalized.contains(":")) {
            int separator = normalized.indexOf(':');
            if (separator == 0
                    || separator == normalized.length() - 1
                    || separator != normalized.lastIndexOf(':')) {
                throw new IllegalArgumentException("World identifier is invalid: " + requestedName);
            }
            NamespacedKey explicit = NamespacedKey.fromString(normalized);
            if (explicit == null) {
                throw new IllegalArgumentException("World identifier is invalid: " + requestedName);
            }
            return explicit;
        }
        String configuredIrisPrefix = configuredLevelName + "_" + IRIS_NAMESPACE + "_";
        if (requested.startsWith(configuredIrisPrefix)) {
            return keyFromConfiguredWorldName(requested, configuredLevelName);
        }
        if (requested.equalsIgnoreCase(configuredLevelName)) {
            return NamespacedKey.minecraft("overworld");
        }
        if (requested.equalsIgnoreCase(configuredLevelName + "_nether")) {
            return NamespacedKey.minecraft("the_nether");
        }
        if (requested.equalsIgnoreCase(configuredLevelName + "_the_end")) {
            return NamespacedKey.minecraft("the_end");
        }
        return switch (normalized) {
            case "main", "overworld" -> NamespacedKey.minecraft("overworld");
            case "nether", "the_nether" -> NamespacedKey.minecraft("the_nether");
            case "end", "the_end" -> NamespacedKey.minecraft("the_end");
            default -> new NamespacedKey(IRIS_NAMESPACE, normalized.replace(' ', '_'));
        };
    }

    static NamespacedKey keyFromName(String worldName, String levelName) {
        String name = Objects.requireNonNull(worldName, "worldName").trim();
        String mainLevelName = Objects.requireNonNull(levelName, "levelName").trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("World name cannot be empty.");
        }
        if (name.equals(mainLevelName)) {
            return NamespacedKey.minecraft("overworld");
        }
        if (name.equals(mainLevelName + "_nether")) {
            return NamespacedKey.minecraft("the_nether");
        }
        if (name.equals(mainLevelName + "_the_end")) {
            return NamespacedKey.minecraft("the_end");
        }

        String key = name.toLowerCase(Locale.ENGLISH).replace(' ', '_');
        return new NamespacedKey(IRIS_NAMESPACE, key);
    }

    public static NamespacedKey keyFromConfiguredWorldName(String configuredWorldName, String levelName) {
        String name = Objects.requireNonNull(configuredWorldName, "configuredWorldName").trim();
        String mainLevelName = Objects.requireNonNull(levelName, "levelName").trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Configured world name cannot be empty.");
        }
        if (mainLevelName.isEmpty()) {
            throw new IllegalArgumentException("Level name cannot be empty.");
        }

        String irisPrefix = mainLevelName + "_" + IRIS_NAMESPACE + "_";
        if (name.startsWith(irisPrefix)) {
            return managedKeyFromName(IRIS_NAMESPACE + ":" + name.substring(irisPrefix.length()), mainLevelName);
        }
        return keyFromName(name, mainLevelName);
    }

    public static String configuredWorldName(NamespacedKey key, String levelName) {
        NamespacedKey worldKey = Objects.requireNonNull(key, "key");
        String mainLevelName = Objects.requireNonNull(levelName, "levelName").trim();
        if (mainLevelName.isEmpty()) {
            throw new IllegalArgumentException("Level name cannot be empty.");
        }
        if (NamespacedKey.minecraft("overworld").equals(worldKey)) {
            return mainLevelName;
        }
        if (NamespacedKey.minecraft("the_nether").equals(worldKey)) {
            return mainLevelName + "_nether";
        }
        if (NamespacedKey.minecraft("the_end").equals(worldKey)) {
            return mainLevelName + "_the_end";
        }
        if (!IRIS_NAMESPACE.equals(worldKey.getNamespace()) || !worldKey.getKey().matches("[a-z0-9_-]+")) {
            throw new IllegalArgumentException("Only safe Iris-managed world keys have a Bukkit startup name.");
        }
        return mainLevelName + "_" + worldKey.getNamespace() + "_" + worldKey.getKey();
    }

    public static String configuredWorldName(WorldSlotKey key, String levelName) {
        WorldSlotKey worldKey = Objects.requireNonNull(key, "key");
        NamespacedKey namespacedKey = NamespacedKey.fromString(worldKey.toString());
        if (namespacedKey == null) {
            throw new IllegalArgumentException("World key is invalid: " + worldKey);
        }
        return configuredWorldName(namespacedKey, levelName);
    }

    public static String logicalName(WorldInfo world) {
        return logicalName(WorldIdentity.key(world));
    }

    public static String logicalName(NamespacedKey key) {
        NamespacedKey worldKey = Objects.requireNonNull(key, "key");
        if (IRIS_NAMESPACE.equals(worldKey.getNamespace())) {
            return worldKey.getKey();
        }
        return logicalName(worldKey, levelRoot().getName());
    }

    static String logicalName(NamespacedKey key, String levelName) {
        NamespacedKey worldKey = Objects.requireNonNull(key, "key");
        String mainLevelName = Objects.requireNonNull(levelName, "levelName").trim();
        if (NamespacedKey.minecraft("overworld").equals(worldKey)) {
            return mainLevelName;
        }
        if (NamespacedKey.minecraft("the_nether").equals(worldKey)) {
            return mainLevelName + "_nether";
        }
        if (NamespacedKey.minecraft("the_end").equals(worldKey)) {
            return mainLevelName + "_the_end";
        }
        return worldKey.getKey();
    }

    public static File dimensionRoot(String worldName) {
        return dimensionRoot(keyFromName(worldName));
    }

    public static File dimensionRoot(WorldInfo world) {
        NamespacedKey key = WorldIdentity.key(world);
        Optional<World> loadedWorld = WorldIdentity.resolve(key);
        if (loadedWorld.isPresent()) {
            return loadedWorld.get().getWorldFolder().getAbsoluteFile();
        }
        return dimensionRoot(key);
    }

    public static File dimensionRoot(NamespacedKey key) {
        return dimensionRoot(levelRoot(), key);
    }

    public static File requireSafeManagedDimensionRoot(NamespacedKey key) {
        return requireSafeManagedDimensionRoot(levelRoot(), key);
    }

    public static File requireSafePersistentDimensionRoot(NamespacedKey key) {
        return requireSafePersistentDimensionRoot(levelRoot(), key);
    }

    static File requireSafePersistentDimensionRoot(File levelRoot, NamespacedKey key) {
        NamespacedKey worldKey = Objects.requireNonNull(key, "key");
        WorldSlotKey slotKey = new WorldSlotKey(worldKey.getNamespace(), worldKey.getKey());
        return ExactWorldSlotPathPolicy.resolve(
                Objects.requireNonNull(levelRoot, "levelRoot").toPath(),
                slotKey
        ).worldDirectory().toFile();
    }

    public static File requireSafeManagedDimensionRoot(File levelRoot, NamespacedKey key) {
        NamespacedKey worldKey = Objects.requireNonNull(key, "key");
        if (!IRIS_NAMESPACE.equals(worldKey.getNamespace()) || !worldKey.getKey().matches("[a-z0-9_-]+")) {
            throw new IllegalArgumentException("Only safe Iris-managed dimension worlds can be changed.");
        }

        Path root = Objects.requireNonNull(levelRoot, "levelRoot").toPath().toAbsolutePath().normalize();
        Path dimensions = root.resolve("dimensions");
        Path namespace = dimensions.resolve(IRIS_NAMESPACE);
        Path target = namespace.resolve(worldKey.getKey()).normalize();
        if (!Objects.equals(target.getParent(), namespace)) {
            throw new IllegalArgumentException("World target escapes the Iris namespace root.");
        }
        for (Path path : new Path[]{dimensions, namespace, target}) {
            if (Files.isSymbolicLink(path)) {
                throw new IllegalArgumentException("World storage path contains a symbolic link: " + path);
            }
        }
        return target.toFile();
    }

    /**
     * True when the level root holds at least one Iris-managed world directory. Used by the failure paths
     * that have to decide whether letting the server continue would put vanilla terrain into Iris storage.
     */
    public static boolean hasManagedWorldStorage(File levelRoot) {
        if (levelRoot == null) {
            return false;
        }
        Path namespace = levelRoot.toPath()
                .toAbsolutePath()
                .normalize()
                .resolve("dimensions")
                .resolve(IRIS_NAMESPACE);
        if (!Files.isDirectory(namespace, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        try (Stream<Path> entries = Files.list(namespace)) {
            return entries.anyMatch(entry -> Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS));
        } catch (IOException unreadable) {
            return false;
        }
    }

    public static boolean isExistingManagedDimensionRoot(File levelRoot, NamespacedKey key) {
        try {
            Path target = requireSafeManagedDimensionRoot(levelRoot, key).toPath();
            return Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public static File dimensionRoot(File levelRoot, NamespacedKey key) {
        Path dimensionsRoot = Objects.requireNonNull(levelRoot, "levelRoot")
                .toPath()
                .toAbsolutePath()
                .normalize()
                .resolve("dimensions");
        NamespacedKey worldKey = Objects.requireNonNull(key, "key");
        Path namespaceRoot = dimensionsRoot.resolve(worldKey.getNamespace()).normalize();
        Path dimensionRoot = namespaceRoot.resolve(worldKey.getKey()).normalize();
        if (!dimensionRoot.startsWith(namespaceRoot)) {
            throw new IllegalArgumentException("World key escapes its namespace storage root: " + worldKey);
        }
        return dimensionRoot.toFile();
    }

    public static Optional<NamespacedKey> keyFromDimensionRoot(File levelRoot, File dimensionRoot) {
        Path dimensionsPath = Objects.requireNonNull(levelRoot, "levelRoot")
                .toPath()
                .toAbsolutePath()
                .normalize()
                .resolve("dimensions");
        Path worldPath = Objects.requireNonNull(dimensionRoot, "dimensionRoot")
                .toPath()
                .toAbsolutePath()
                .normalize();
        if (!worldPath.startsWith(dimensionsPath)) {
            return Optional.empty();
        }

        Path relative = dimensionsPath.relativize(worldPath);
        if (relative.getNameCount() < 2) {
            return Optional.empty();
        }

        String namespace = relative.getName(0).toString();
        StringBuilder key = new StringBuilder();
        for (int i = 1; i < relative.getNameCount(); i++) {
            if (!key.isEmpty()) {
                key.append('/');
            }
            key.append(relative.getName(i));
        }

        return Optional.ofNullable(NamespacedKey.fromString(namespace + ":" + key));
    }

    public static File requireFrozenDimensionRoot(
            File worldContainer,
            File levelRoot,
            String bukkitWorldName,
            NamespacedKey key
    ) {
        NamespacedKey worldKey = Objects.requireNonNull(key, "key");
        return frozenDimensionRoot(worldContainer, levelRoot, bukkitWorldName, worldKey)
                .orElseThrow(() -> new IllegalStateException(
                        "Frozen Iris world storage is missing for " + worldKey + "."));
    }

    public static Optional<File> frozenDimensionRoot(
            File worldContainer,
            File levelRoot,
            String bukkitWorldName,
            NamespacedKey key
    ) {
        File requiredLevelRoot = Objects.requireNonNull(levelRoot, "levelRoot").getAbsoluteFile();
        NamespacedKey worldKey = Objects.requireNonNull(key, "key");
        String requiredWorldName = Objects.requireNonNull(bukkitWorldName, "bukkitWorldName").trim();
        if (requiredWorldName.isEmpty()) {
            throw new IllegalArgumentException("Bukkit world name cannot be empty.");
        }

        File directRoot = dimensionRoot(requiredLevelRoot, worldKey);
        boolean directExists = isExistingSafeDimensionRoot(requiredLevelRoot.toPath(), directRoot.toPath());
        if (!IRIS_NAMESPACE.equals(worldKey.getNamespace())) {
            return directExists ? Optional.of(directRoot) : Optional.empty();
        }

        String configuredName = configuredWorldName(worldKey, requiredLevelRoot.getName());
        if (!configuredName.equals(requiredWorldName)) {
            return directExists ? Optional.of(directRoot) : Optional.empty();
        }

        File configuredRoot = configuredDimensionRoot(worldContainer, requiredLevelRoot, worldKey);
        boolean configuredExists = isExistingSafeDimensionRoot(
                Objects.requireNonNull(worldContainer, "worldContainer").toPath(),
                configuredRoot.toPath()
        );
        if (directExists == configuredExists) {
            if (directExists) {
                throw new IllegalStateException("Frozen Iris world storage is ambiguous for " + worldKey + ".");
            }
            return Optional.empty();
        }
        return Optional.of(directExists ? directRoot : configuredRoot);
    }

    public static File configuredLevelRoot(File worldContainer, File levelRoot, NamespacedKey key) {
        Path container = Objects.requireNonNull(worldContainer, "worldContainer")
                .toPath()
                .toAbsolutePath()
                .normalize();
        String configuredName = configuredWorldName(
                Objects.requireNonNull(key, "key"),
                Objects.requireNonNull(levelRoot, "levelRoot").getName()
        );
        Path configuredLevelRoot = container.resolve(configuredName).normalize();
        if (!Objects.equals(configuredLevelRoot.getParent(), container)) {
            throw new IllegalStateException("Configured Bukkit world storage escapes the world container.");
        }
        isExistingSafeDimensionRoot(container, configuredLevelRoot.resolve("dimensions"));
        return configuredLevelRoot.toFile();
    }

    public static File configuredDimensionRoot(File worldContainer, File levelRoot, NamespacedKey key) {
        File configuredLevelRoot = configuredLevelRoot(worldContainer, levelRoot, key);
        File configuredRoot = dimensionRoot(configuredLevelRoot, key);
        isExistingSafeDimensionRoot(
                Objects.requireNonNull(worldContainer, "worldContainer").toPath(),
                configuredRoot.toPath()
        );
        return configuredRoot;
    }

    public static File requireActiveGenerationPackRoot(File dimensionRoot) {
        Path root = Objects.requireNonNull(dimensionRoot, "dimensionRoot")
                .toPath()
                .toAbsolutePath()
                .normalize();
        try {
            return GenerationHistory.open(root).activePackRoot().toFile();
        } catch (IOException failure) {
            throw new IllegalStateException("Iris generation history is unusable at " + root + ".", failure);
        }
    }

    public static File requireActiveGenerationPackRoot(File dimensionRoot, long expectedWorldSeed) {
        Path root = Objects.requireNonNull(dimensionRoot, "dimensionRoot")
                .toPath()
                .toAbsolutePath()
                .normalize();
        try {
            return GenerationHistory.open(root, expectedWorldSeed).activePackRoot().toFile();
        } catch (IOException failure) {
            throw new IllegalStateException("Iris generation history is unusable at " + root + ".", failure);
        }
    }

    private static boolean isExistingSafeDimensionRoot(Path storageRoot, Path dimensionRoot) {
        Path root = storageRoot.toAbsolutePath().normalize();
        Path target = dimensionRoot.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(root)) {
            throw new IllegalStateException("Iris world storage root is a symbolic link: " + root);
        }
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("Iris world storage root is not a directory: " + root);
        }
        if (!target.startsWith(root) || Objects.equals(target, root)) {
            throw new IllegalStateException("Iris world storage escapes its expected root: " + target);
        }

        Path relative = root.relativize(target);
        Path current = root;
        for (Path segment : relative) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) {
                throw new IllegalStateException("Iris world storage contains a symbolic link: " + current);
            }
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalStateException("Iris world storage path is not a directory: " + current);
            }
        }
        return Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS);
    }
}
