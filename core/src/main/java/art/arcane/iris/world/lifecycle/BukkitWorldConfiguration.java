package art.arcane.iris.world.lifecycle;

import art.arcane.iris.world.IrisWorldStorage;
import art.arcane.iris.world.WorldSlotKey;
import art.arcane.iris.generation.runtime.IrisEngineMantle;
import art.arcane.iris.world.history.GenerationHistoryPaths;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;

public final class BukkitWorldConfiguration {
    private static final String DEFAULT_WORLD_CONTAINER = ".";
    private static final Object MUTATION_LOCK = new Object();

    private BukkitWorldConfiguration() {
    }

    public static Registration register(File configurationFile, String worldName, String dimension, Long seed) throws IOException {
        Objects.requireNonNull(configurationFile, "configurationFile");
        String requiredWorldName = requireWorldName(worldName);
        String requiredDimension = requireName(dimension, "Dimension");
        synchronized (MUTATION_LOCK) {
            YamlConfiguration configuration = load(configurationFile);
            ConfigurationSection worlds = configuration.getConfigurationSection("worlds");
            if (worlds == null) {
                worlds = configuration.createSection("worlds");
            }

            ConfigurationSection existing = worlds.getConfigurationSection(requiredWorldName);
            String generator = "Iris:" + requiredDimension;
            if (existing != null) {
                String existingGenerator = existing.getString("generator");
                Long existingSeed = existing.contains("seed") ? existing.getLong("seed") : null;
                if (!generator.equals(existingGenerator) || !Objects.equals(seed, existingSeed)) {
                    throw new IOException("bukkit.yml already contains a different definition for world \""
                            + requiredWorldName + "\".");
                }
                return Registration.UNCHANGED;
            }

            ConfigurationSection created = worlds.createSection(requiredWorldName);
            created.set("generator", generator);
            if (seed != null) {
                created.set("seed", seed);
            }
            saveAtomic(configurationFile.toPath(), configuration);
            return Registration.CREATED;
        }
    }

    public static WorldGeneratorSnapshot snapshot(File configurationFile, String worldName) throws IOException {
        Objects.requireNonNull(configurationFile, "configurationFile");
        String requiredWorldName = requireWorldName(worldName);
        synchronized (MUTATION_LOCK) {
            return snapshot(load(configurationFile), requiredWorldName);
        }
    }

    public static String readWorldContainer(File configurationFile) throws IOException {
        File requiredConfigurationFile = Objects.requireNonNull(configurationFile, "configurationFile");
        Path configurationPath = requiredConfigurationFile.toPath();
        if (!Files.exists(configurationPath, LinkOption.NOFOLLOW_LINKS)) {
            return DEFAULT_WORLD_CONTAINER;
        }
        if (!Files.isRegularFile(configurationPath)) {
            throw new IOException("Configured Bukkit configuration is not a regular file: " + configurationPath);
        }
        synchronized (MUTATION_LOCK) {
            YamlConfiguration configuration = load(requiredConfigurationFile);
            Object configured = configuration.get("settings.world-container");
            if (configured == null) {
                return DEFAULT_WORLD_CONTAINER;
            }
            if (!(configured instanceof String value)) {
                throw new IOException("Bukkit settings.world-container must be a path string");
            }
            if (value.isBlank()) {
                throw new IOException("Configured world container is empty");
            }
            return value;
        }
    }

    public static List<IrisGeneratorBinding> readIrisGeneratorBindings(
            File configurationFile,
            String levelName,
            Path levelRoot
    ) throws IOException {
        File requiredConfigurationFile = Objects.requireNonNull(configurationFile, "configurationFile");
        String requiredLevelName = requireName(levelName, "Level name");
        Path requiredLevelRoot = Objects.requireNonNull(levelRoot, "levelRoot")
                .toAbsolutePath()
                .normalize();
        Path configurationPath = requiredConfigurationFile.toPath();
        if (!Files.exists(configurationPath, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        synchronized (MUTATION_LOCK) {
            Map<WorldSlotKey, IrisGeneratorBinding> bindings = new LinkedHashMap<>();
            for (IrisWorldCandidate candidate : readIrisWorldCandidates(
                    load(requiredConfigurationFile),
                    requiredLevelName
            )) {
                String configuredName = candidate.configuredWorldName();
                Path worldContainer = requiredLevelRoot.getParent();
                if (worldContainer == null) {
                    throw new IOException("Selected level root has no world container: " + requiredLevelRoot);
                }
                boolean storagePresent;
                try {
                    storagePresent = IrisWorldStorage.frozenDimensionRoot(
                            worldContainer.toFile(),
                            requiredLevelRoot.toFile(),
                            configuredName,
                            candidate.worldKey()
                    ).isPresent();
                } catch (IllegalStateException failure) {
                    storagePresent = false;
                }
                if (!storagePresent) {
                    MissingWorldStorageLog.warnOnce(
                            configuredName,
                            expectedDimensionRoot(requiredLevelRoot, candidate.worldKey()));
                    continue;
                }
                String dimension = selectedIrisDimension(candidate.configuredGenerator(), configuredName);
                WorldSlotKey worldKey = new WorldSlotKey(
                        candidate.worldKey().getNamespace(),
                        candidate.worldKey().getKey()
                );
                IrisGeneratorBinding binding = new IrisGeneratorBinding(configuredName, worldKey, dimension);
                IrisGeneratorBinding previous = bindings.putIfAbsent(worldKey, binding);
                if (previous != null) {
                    throw new IOException("bukkit.yml maps both \"" + previous.configuredWorldName()
                            + "\" and \"" + configuredName + "\" to " + worldKey + ".");
                }
            }
            return List.copyOf(bindings.values());
        }
    }

    /**
     * Classifies the world storage behind every Iris entry in bukkit.yml.
     * <p>
     * {@link #readIrisGeneratorBindings} answers "which worlds can Iris bind", which deliberately drops
     * anything without storage. Startup needs the opposite view: a world whose folder is on disk but whose
     * frozen pack snapshot is gone still gets loaded by the server, and if Iris cannot supply a generator
     * for it the server writes vanilla terrain into Iris region files. That state has to be found before
     * any level loads, so it is reported here rather than discovered at world-generation time.
     */
    public static List<IrisWorldStorageEntry> auditIrisWorldStorage(
            File configurationFile,
            String levelName,
            Path levelRoot
    ) throws IOException {
        File requiredConfigurationFile = Objects.requireNonNull(configurationFile, "configurationFile");
        String requiredLevelName = requireName(levelName, "Level name");
        Path requiredLevelRoot = Objects.requireNonNull(levelRoot, "levelRoot")
                .toAbsolutePath()
                .normalize();
        Path configurationPath = requiredConfigurationFile.toPath();
        if (!Files.exists(configurationPath, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        Path worldContainer = requiredLevelRoot.getParent();
        if (worldContainer == null) {
            throw new IOException("Selected level root has no world container: " + requiredLevelRoot);
        }
        synchronized (MUTATION_LOCK) {
            List<IrisWorldStorageEntry> entries = new ArrayList<>();
            for (IrisWorldCandidate candidate : readIrisWorldCandidates(
                    load(requiredConfigurationFile),
                    requiredLevelName
            )) {
                entries.add(classifyStorage(worldContainer, requiredLevelRoot, candidate));
            }
            return List.copyOf(entries);
        }
    }

    private static IrisWorldStorageEntry classifyStorage(
            Path worldContainer,
            Path levelRoot,
            IrisWorldCandidate candidate
    ) {
        String configuredName = candidate.configuredWorldName();
        NamespacedKey worldKey = candidate.worldKey();
        WorldSlotKey slotKey = new WorldSlotKey(worldKey.getNamespace(), worldKey.getKey());
        Path expectedRoot = expectedDimensionRoot(levelRoot, worldKey);
        File dimensionRoot;
        try {
            dimensionRoot = IrisWorldStorage.frozenDimensionRoot(
                    worldContainer.toFile(),
                    levelRoot.toFile(),
                    configuredName,
                    worldKey
            ).orElse(null);
        } catch (IllegalStateException unusable) {
            // The server only loads a level it can see as a directory, so an entry with no directory at
            // either candidate path is an orphan, not a world that is about to generate.
            return hasDimensionDirectory(worldContainer, levelRoot, configuredName, worldKey)
                    ? new IrisWorldStorageEntry(configuredName, slotKey, expectedRoot,
                    IrisWorldStorageState.UNUSABLE, unusable.getMessage())
                    : new IrisWorldStorageEntry(configuredName, slotKey, expectedRoot,
                    IrisWorldStorageState.MISSING, null);
        }
        if (dimensionRoot == null) {
            return new IrisWorldStorageEntry(configuredName, slotKey, expectedRoot,
                    IrisWorldStorageState.MISSING, null);
        }
        Path resolvedRoot = dimensionRoot.toPath().toAbsolutePath().normalize();
        try {
            IrisWorldStorage.requireActiveGenerationPackRoot(dimensionRoot);
        } catch (IllegalStateException unusablePack) {
            GenerationHistoryPaths historyPaths = GenerationHistoryPaths.forDimension(resolvedRoot);
            boolean generationHistoryExists = Files.exists(
                    historyPaths.generationRoot(),
                    LinkOption.NOFOLLOW_LINKS
            ) || Files.isSymbolicLink(historyPaths.generationRoot());
            boolean adoptableLegacyPack = !generationHistoryExists
                    && Files.isDirectory(historyPaths.legacyPackRoot(), LinkOption.NOFOLLOW_LINKS)
                    && !Files.isSymbolicLink(historyPaths.legacyPackRoot());
            if (adoptableLegacyPack) {
                return new IrisWorldStorageEntry(configuredName, slotKey, resolvedRoot,
                        IrisWorldStorageState.PRESENT, null);
            }
            // Only a folder that owns something can be corrupted by a vanilla generator writing into it.
            return new IrisWorldStorageEntry(configuredName, slotKey, resolvedRoot,
                    hasWorldData(resolvedRoot)
                            ? IrisWorldStorageState.UNUSABLE
                            : IrisWorldStorageState.EMPTY,
                    unusablePack.getMessage());
        }
        return new IrisWorldStorageEntry(configuredName, slotKey, resolvedRoot,
                IrisWorldStorageState.PRESENT, null);
    }

    /**
     * True when the world folder holds something a vanilla generator could overwrite: an {@code iris/}
     * directory marking it as an Iris world whose pack snapshot broke, or stored chunk data.
     * <p>
     * Deleting a loaded world's folder and letting the server save the level back recreates a small
     * {@code data/} skeleton with no regions and no {@code iris/} directory. That husk owns nothing, so
     * refusing to boot over it only strands the server behind a manual delete.
     */
    private static boolean hasWorldData(Path worldRoot) {
        if (Files.isDirectory(worldRoot.resolve("iris"), LinkOption.NOFOLLOW_LINKS)) {
            return true;
        }
        for (String directory : new String[]{
                "region", "entities", "poi", IrisEngineMantle.STORAGE_FOLDER_NAME
        }) {
            if (hasEntries(worldRoot.resolve(directory))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasEntries(Path directory) {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.findAny().isPresent();
        } catch (IOException unreadable) {
            // An unreadable directory is not proof that there is nothing to lose.
            return true;
        }
    }

    private static boolean hasDimensionDirectory(
            Path worldContainer,
            Path levelRoot,
            String configuredWorldName,
            NamespacedKey worldKey
    ) {
        if (occupiesDimensionPath(levelRoot, expectedDimensionRoot(levelRoot, worldKey))) {
            return true;
        }
        Path configuredLevelRoot = worldContainer.resolve(configuredWorldName).normalize();
        return occupiesDimensionPath(configuredLevelRoot, expectedDimensionRoot(configuredLevelRoot, worldKey));
    }

    /**
     * True when something the server could load sits at the dimension path: a real directory, or a symbolic
     * link on any segment below the level root.
     * <p>
     * {@link IrisWorldStorage} refuses to resolve through a link, so a linked path is storage Iris cannot use,
     * not storage that is not there. Calling it missing tells the operator to restore a folder that is already
     * present and leaves whatever the link points at open to a vanilla generator.
     */
    private static boolean occupiesDimensionPath(Path levelRoot, Path dimensionRoot) {
        Path root = levelRoot.toAbsolutePath().normalize();
        Path target = dimensionRoot.toAbsolutePath().normalize();
        if (!target.startsWith(root) || target.equals(root)) {
            return Files.isSymbolicLink(target) || Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS);
        }

        Path current = root;
        for (Path segment : root.relativize(target)) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) {
                return true;
            }
        }
        return Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS);
    }

    private static Path expectedDimensionRoot(Path levelRoot, NamespacedKey worldKey) {
        return levelRoot.toAbsolutePath()
                .normalize()
                .resolve("dimensions")
                .resolve(worldKey.getNamespace())
                .resolve(worldKey.getKey())
                .normalize();
    }

    /**
     * Every canonical Iris entry in bukkit.yml, in configured-name order, before any storage is inspected.
     * Malformed entries throw exactly as they always did; entries that are not Iris', or whose name is not
     * the canonical startup name for their key, are dropped.
     */
    private static List<IrisWorldCandidate> readIrisWorldCandidates(
            YamlConfiguration configuration,
            String levelName
    ) throws IOException {
        Object rawWorlds = configuration.get("worlds");
        ConfigurationSection worlds = configuration.getConfigurationSection("worlds");
        if (rawWorlds != null && worlds == null) {
            throw new IOException("bukkit.yml worlds entry is not a section.");
        }
        if (worlds == null) {
            return List.of();
        }

        List<String> configuredNames = new ArrayList<>(worlds.getKeys(false));
        configuredNames.sort(Comparator.naturalOrder());
        List<IrisWorldCandidate> candidates = new ArrayList<>(configuredNames.size());
        for (String configuredName : configuredNames) {
            Object rawWorld = worlds.get(configuredName);
            ConfigurationSection world = worlds.getConfigurationSection(configuredName);
            if (rawWorld != null && world == null) {
                throw new IOException("bukkit.yml world entry \"" + configuredName + "\" is not a section.");
            }
            if (world == null || !world.getKeys(false).contains("generator")) {
                continue;
            }
            Object rawGenerator = world.get("generator");
            if (!(rawGenerator instanceof String generator)) {
                throw new IOException("bukkit.yml generator for world \"" + configuredName
                        + "\" is not a string.");
            }
            String configuredGenerator = generator.trim();
            if (!configuredGenerator.equalsIgnoreCase("Iris")
                    && !configuredGenerator.regionMatches(true, 0, "Iris:", 0, 5)) {
                continue;
            }

            NamespacedKey namespacedKey;
            try {
                namespacedKey = IrisWorldStorage.keyFromConfiguredWorldName(configuredName, levelName);
            } catch (IllegalArgumentException failure) {
                throw new IOException("bukkit.yml contains an invalid Iris world name \""
                        + configuredName + "\".", failure);
            }
            if (!"iris".equals(namespacedKey.getNamespace())) {
                continue;
            }
            if (!configuredName.equals(IrisWorldStorage.configuredWorldName(namespacedKey, levelName))) {
                continue;
            }
            candidates.add(new IrisWorldCandidate(configuredName, namespacedKey, configuredGenerator));
        }
        return candidates;
    }

    public static GeneratorReplacement replaceIfMatching(
            File configurationFile,
            String worldName,
            WorldGeneratorSnapshot expected,
            String dimension,
            Long seed
    ) throws IOException {
        Objects.requireNonNull(configurationFile, "configurationFile");
        String requiredWorldName = requireWorldName(worldName);
        WorldGeneratorSnapshot requiredExpected = Objects.requireNonNull(expected, "expected");
        String requiredDimension = requireName(dimension, "Dimension");
        WorldGeneratorSnapshot replacement = WorldGeneratorSnapshot.configured(requiredDimension, seed);
        synchronized (MUTATION_LOCK) {
            YamlConfiguration configuration = load(configurationFile);
            WorldGeneratorSnapshot current = snapshot(configuration, requiredWorldName);
            if (!current.matchesGeneratorAndSeed(requiredExpected)) {
                return new GeneratorReplacement(false, current, replacement);
            }
            apply(configuration, requiredWorldName, replacement);
            saveAtomic(configurationFile.toPath(), configuration, true);
            return new GeneratorReplacement(true, current, replacement);
        }
    }

    public static boolean restoreIfMatching(
            File configurationFile,
            String worldName,
            WorldGeneratorSnapshot expectedCurrent,
            WorldGeneratorSnapshot restoration
    ) throws IOException {
        Objects.requireNonNull(configurationFile, "configurationFile");
        String requiredWorldName = requireWorldName(worldName);
        WorldGeneratorSnapshot requiredExpected = Objects.requireNonNull(expectedCurrent, "expectedCurrent");
        WorldGeneratorSnapshot requiredRestoration = Objects.requireNonNull(restoration, "restoration");
        synchronized (MUTATION_LOCK) {
            YamlConfiguration configuration = load(configurationFile);
            WorldGeneratorSnapshot current = snapshot(configuration, requiredWorldName);
            if (!current.matchesGeneratorAndSeed(requiredExpected)) {
                return false;
            }
            apply(configuration, requiredWorldName, requiredRestoration);
            saveAtomic(configurationFile.toPath(), configuration, true);
            return true;
        }
    }

    public static boolean remove(File configurationFile, String worldName) throws IOException {
        Objects.requireNonNull(configurationFile, "configurationFile");
        String requiredWorldName = requireWorldName(worldName);
        synchronized (MUTATION_LOCK) {
            YamlConfiguration configuration = load(configurationFile);
            ConfigurationSection worlds = configuration.getConfigurationSection("worlds");
            if (worlds == null || worlds.get(requiredWorldName) == null) {
                return false;
            }

            worlds.set(requiredWorldName, null);
            if (worlds.getKeys(false).isEmpty()) {
                configuration.set("worlds", null);
            }
            saveAtomic(configurationFile.toPath(), configuration);
            return true;
        }
    }

    public static boolean removeIfMatching(
            File configurationFile,
            String worldName,
            String dimension,
            Long seed
    ) throws IOException {
        Objects.requireNonNull(configurationFile, "configurationFile");
        String requiredWorldName = requireWorldName(worldName);
        String requiredDimension = requireName(dimension, "Dimension");
        synchronized (MUTATION_LOCK) {
            YamlConfiguration configuration = load(configurationFile);
            ConfigurationSection worlds = configuration.getConfigurationSection("worlds");
            if (worlds == null) {
                return false;
            }

            ConfigurationSection existing = worlds.getConfigurationSection(requiredWorldName);
            if (existing == null) {
                return false;
            }
            String expectedGenerator = "Iris:" + requiredDimension;
            String actualGenerator = existing.getString("generator");
            Long actualSeed = existing.contains("seed") ? existing.getLong("seed") : null;
            if (!expectedGenerator.equals(actualGenerator) || !Objects.equals(seed, actualSeed)) {
                return false;
            }

            worlds.set(requiredWorldName, null);
            if (worlds.getKeys(false).isEmpty()) {
                configuration.set("worlds", null);
            }
            saveAtomic(configurationFile.toPath(), configuration);
            return true;
        }
    }

    public static int removeMatching(File configurationFile, Predicate<String> matcher) throws IOException {
        Objects.requireNonNull(configurationFile, "configurationFile");
        Predicate<String> requiredMatcher = Objects.requireNonNull(matcher, "matcher");
        synchronized (MUTATION_LOCK) {
            YamlConfiguration configuration = load(configurationFile);
            ConfigurationSection worlds = configuration.getConfigurationSection("worlds");
            if (worlds == null) {
                return 0;
            }

            int removed = 0;
            for (String worldName : new ArrayList<>(worlds.getKeys(false))) {
                if (!requiredMatcher.test(worldName)) {
                    continue;
                }
                worlds.set(worldName, null);
                removed++;
            }
            if (removed == 0) {
                return 0;
            }
            if (worlds.getKeys(false).isEmpty()) {
                configuration.set("worlds", null);
            }
            saveAtomic(configurationFile.toPath(), configuration);
            return removed;
        }
    }

    static void saveAtomic(Path target, YamlConfiguration configuration) throws IOException {
        saveAtomic(target, configuration, false);
    }

    private static void saveAtomic(
            Path target,
            YamlConfiguration configuration,
            boolean requireAtomicReplacement
    ) throws IOException {
        Path absoluteTarget = target.toAbsolutePath().normalize();
        requireRegularConfigurationFile(absoluteTarget);
        Path parent = absoluteTarget.getParent();
        if (parent == null) {
            throw new IOException("bukkit.yml target has no parent: " + absoluteTarget);
        }
        Files.createDirectories(parent);
        Path staged = Files.createTempFile(parent, ".bukkit-worlds-", ".yml");
        try {
            configuration.save(staged.toFile());
            try (FileChannel channel = FileChannel.open(staged, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            requireRegularConfigurationFile(absoluteTarget);
            try {
                Files.move(staged, absoluteTarget, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                if (requireAtomicReplacement) {
                    throw new IOException(
                            "Exact world replacement requires atomic bukkit.yml publication on this filesystem.",
                            exception
                    );
                }
                Files.move(staged, absoluteTarget, StandardCopyOption.REPLACE_EXISTING);
            }
            // bukkit.yml is the commit record the bootstrap reconciles against; its rename
            // needs the same directory-durability barrier the journal and world moves get.
            if (requireAtomicReplacement) {
                DirectoryDurability.forceDirectoryRequired(parent);
            } else {
                DirectoryDurability.forceDirectoryAfterCommit(parent, "A bukkit.yml world configuration change");
            }
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    private static YamlConfiguration load(File configurationFile) throws IOException {
        requireRegularConfigurationFile(configurationFile.toPath());
        YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.load(configurationFile);
            return configuration;
        } catch (InvalidConfigurationException exception) {
            throw new IOException("bukkit.yml is invalid and was not changed.", exception);
        }
    }

    private static void requireRegularConfigurationFile(Path configurationFile) throws IOException {
        Path path = configurationFile.toAbsolutePath().normalize();
        BasicFileAttributes attributes = Files.readAttributes(
                path,
                BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS
        );
        if (attributes.isSymbolicLink() || !attributes.isRegularFile()) {
            throw new IOException("bukkit.yml must be an existing regular file and was not changed: " + path);
        }
    }

    private static WorldGeneratorSnapshot snapshot(
            YamlConfiguration configuration,
            String worldName
    ) throws IOException {
        Object rawWorlds = configuration.get("worlds");
        ConfigurationSection worlds = configuration.getConfigurationSection("worlds");
        if (rawWorlds != null && worlds == null) {
            throw new IOException("bukkit.yml worlds entry is not a section and was not changed.");
        }
        if (worlds == null) {
            return WorldGeneratorSnapshot.absent();
        }

        Object rawWorld = worlds.get(worldName);
        ConfigurationSection world = worlds.getConfigurationSection(worldName);
        if (rawWorld != null && world == null) {
            throw new IOException("bukkit.yml world entry \"" + worldName + "\" is not a section and was not changed.");
        }
        if (world == null) {
            return WorldGeneratorSnapshot.absentWorld(true);
        }

        boolean generatorPresent = world.getKeys(false).contains("generator");
        String generator = null;
        if (generatorPresent) {
            Object rawGenerator = world.get("generator");
            if (!(rawGenerator instanceof String generatorValue)) {
                throw new IOException("bukkit.yml generator for world \"" + worldName
                        + "\" is not a string and was not changed.");
            }
            generator = generatorValue;
        }

        boolean seedPresent = world.getKeys(false).contains("seed");
        Long seed = null;
        if (seedPresent) {
            Object rawSeed = world.get("seed");
            if (!(rawSeed instanceof Byte
                    || rawSeed instanceof Short
                    || rawSeed instanceof Integer
                    || rawSeed instanceof Long)) {
                throw new IOException("bukkit.yml seed for world \"" + worldName
                        + "\" is not an integer and was not changed.");
            }
            seed = ((Number) rawSeed).longValue();
        }

        return new WorldGeneratorSnapshot(
                true,
                true,
                generatorPresent,
                generator,
                seedPresent,
                seed
        );
    }

    private static void apply(
            YamlConfiguration configuration,
            String worldName,
            WorldGeneratorSnapshot snapshot
    ) throws IOException {
        ConfigurationSection worlds = configuration.getConfigurationSection("worlds");
        if (worlds == null) {
            Object rawWorlds = configuration.get("worlds");
            if (rawWorlds != null) {
                throw new IOException("bukkit.yml worlds entry is not a section and was not changed.");
            }
            worlds = configuration.createSection("worlds");
        }

        ConfigurationSection world = worlds.getConfigurationSection(worldName);
        if (world == null) {
            Object rawWorld = worlds.get(worldName);
            if (rawWorld != null) {
                throw new IOException("bukkit.yml world entry \"" + worldName
                        + "\" is not a section and was not changed.");
            }
            world = worlds.createSection(worldName);
        }

        world.set("generator", snapshot.generatorPresent() ? snapshot.generator() : null);
        world.set("seed", snapshot.seedPresent() ? snapshot.seed() : null);
        if (!snapshot.worldSectionPresent() && world.getKeys(false).isEmpty()) {
            worlds.set(worldName, null);
        }
        if (!snapshot.worldsSectionPresent() && worlds.getKeys(false).isEmpty()) {
            configuration.set("worlds", null);
        }
    }

    private static String requireWorldName(String value) {
        String worldName = requireName(value, "World name");
        if (!worldName.matches("[a-z0-9_-]+")) {
            throw new IllegalArgumentException("World name must contain only lowercase letters, numbers, underscores, or hyphens.");
        }
        return worldName;
    }

    private static String requireName(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " cannot be empty.");
        }
        return value.trim();
    }

    private static String selectedIrisDimension(String configuredGenerator, String worldName) throws IOException {
        if (configuredGenerator.equalsIgnoreCase("Iris")) {
            throw new IOException("bukkit.yml Iris generator for custom world \"" + worldName
                    + "\" must select a dimension with Iris:<dimension>.");
        }
        String dimension = configuredGenerator.substring(5).trim();
        if (dimension.isEmpty()) {
            throw new IOException("bukkit.yml Iris generator for world \"" + worldName
                    + "\" does not select a dimension.");
        }
        return dimension;
    }

    public enum Registration {
        CREATED,
        UNCHANGED
    }

    public enum IrisWorldStorageState {
        /**
         * Storage resolves and carries a usable frozen pack snapshot.
         */
        PRESENT,
        /**
         * No world directory at all. The server never enumerates the level, so nothing generates; the
         * bukkit.yml and Multiverse entries are left alone so putting the folder back restores the world.
         */
        MISSING,
        /**
         * A world directory the server will load that holds no pack snapshot and no world data - the
         * skeleton the server writes back when it saves a level whose folder was deleted underneath it.
         * There is nothing to overwrite, so it is reported and the server starts.
         */
        EMPTY,
        /**
         * A world directory the server will load, whose frozen pack snapshot cannot be used. Iris cannot
         * generate it and nothing else may.
         */
        UNUSABLE
    }

    public record IrisWorldStorageEntry(
            String configuredWorldName,
            WorldSlotKey worldKey,
            Path storagePath,
            IrisWorldStorageState state,
            String detail
    ) {
        public IrisWorldStorageEntry {
            configuredWorldName = requireName(configuredWorldName, "Configured world name");
            Objects.requireNonNull(worldKey, "worldKey");
            Objects.requireNonNull(storagePath, "storagePath");
            Objects.requireNonNull(state, "state");
        }
    }

    private record IrisWorldCandidate(
            String configuredWorldName,
            NamespacedKey worldKey,
            String configuredGenerator
    ) {
    }

    public record IrisGeneratorBinding(
            String configuredWorldName,
            WorldSlotKey worldKey,
            String dimension
    ) {
        public IrisGeneratorBinding {
            configuredWorldName = requireName(configuredWorldName, "Configured world name");
            WorldSlotKey requiredWorldKey = Objects.requireNonNull(worldKey, "worldKey");
            if (!"iris".equals(requiredWorldKey.namespace())
                    || !requiredWorldKey.key().matches("[a-z0-9_-]+")) {
                throw new IllegalArgumentException("Only safe Iris-managed keys can have LevelStem bindings.");
            }
            dimension = requireName(dimension, "Iris dimension");
        }
    }

    public record WorldGeneratorSnapshot(
            boolean worldsSectionPresent,
            boolean worldSectionPresent,
            boolean generatorPresent,
            String generator,
            boolean seedPresent,
            Long seed
    ) {
        public WorldGeneratorSnapshot {
            if (worldSectionPresent && !worldsSectionPresent) {
                throw new IllegalArgumentException("A world section requires a worlds section.");
            }
            if (!worldSectionPresent && (generatorPresent || seedPresent)) {
                throw new IllegalArgumentException("Generator and seed values require a world section.");
            }
            if (generatorPresent != (generator != null)) {
                throw new IllegalArgumentException("Generator presence and value must agree.");
            }
            if (seedPresent != (seed != null)) {
                throw new IllegalArgumentException("Seed presence and value must agree.");
            }
        }

        private static WorldGeneratorSnapshot absent() {
            return new WorldGeneratorSnapshot(false, false, false, null, false, null);
        }

        private static WorldGeneratorSnapshot absentWorld(boolean worldsSectionPresent) {
            return new WorldGeneratorSnapshot(worldsSectionPresent, false, false, null, false, null);
        }

        private static WorldGeneratorSnapshot configured(String dimension, Long seed) {
            return new WorldGeneratorSnapshot(true, true, true, "Iris:" + dimension, seed != null, seed);
        }

        public boolean matchesGeneratorAndSeed(WorldGeneratorSnapshot other) {
            return generatorPresent == other.generatorPresent
                    && Objects.equals(generator, other.generator)
                    && seedPresent == other.seedPresent
                    && Objects.equals(seed, other.seed);
        }
    }

    public record GeneratorReplacement(
            boolean applied,
            WorldGeneratorSnapshot observed,
            WorldGeneratorSnapshot replacement
    ) {
        public GeneratorReplacement {
            Objects.requireNonNull(observed, "observed");
            Objects.requireNonNull(replacement, "replacement");
        }
    }
}
