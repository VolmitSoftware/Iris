package art.arcane.iris.core;

import art.arcane.iris.engine.object.world.biome.custom.IrisBiomeCustom;
import art.arcane.iris.BuildConstants;
import art.arcane.iris.core.lifecycle.BukkitWorldConfiguration.IrisGeneratorBinding;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.modules.PackModulesException;
import art.arcane.iris.core.nms.datapack.DataVersion;
import art.arcane.iris.core.nms.datapack.IDataFixer;
import art.arcane.iris.core.pack.AtomicDirectoryPublisher;
import art.arcane.iris.core.pack.PackDirectoryResolver;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KSet;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class IrisDatapackCompiler {
    private static final int INPUT_FINGERPRINT_SCHEMA = 2;
    private static final int INPUT_BUFFER_BYTES = 64 * 1024;
    private static final int WORLD_PACK_SCAN_DEPTH = 8;
    private static final String DIMENSION_TYPE_FOLDER = "data/iris/dimension_type";
    private static final Pattern REGISTRY_KEY_PATTERN = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");
    private static final List<String> INPUT_DIRECTORIES = List.of("dimensions", "biomes", "snippet");
    private static final String FLAT_VOID_LEVEL_STEM = """
            {
              "type": "%s",
              "generator": {
                "type": "minecraft:flat",
                "settings": {
                  "biome": "minecraft:the_void",
                  "features": false,
                  "lakes": false,
                  "layers": [
                    {
                      "block": "minecraft:air",
                      "height": 1
                    }
                  ],
                  "structure_overrides": []
                }
              }
            }
            """;

    private IrisDatapackCompiler() {
    }

    public static List<File> collectPackRoots(Path dataDirectory, Path serverRoot) throws IOException {
        return collectPackRoots(dataDirectory, serverRoot, true);
    }

    public static List<File> collectCompilerInputRoots(Path dataDirectory, Path serverRoot) throws IOException {
        return collectPackRoots(dataDirectory, serverRoot, false);
    }

    private static List<File> collectPackRoots(
            Path dataDirectory,
            Path serverRoot,
            boolean validateWholePack
    ) throws IOException {
        LinkedHashMap<Path, File> roots = new LinkedHashMap<>();
        collectInstalledPackRoots(dataDirectory.resolve("packs"), roots, validateWholePack);
        collectWorldPackRoots(serverRoot.resolve("dimensions"), roots, validateWholePack);
        return new ArrayList<>(roots.values());
    }

    public static String computeInputFingerprint(
            List<File> packRoots,
            List<IrisGeneratorBinding> bindings,
            IDataFixer fixer,
            boolean adjustVanillaHeight
    ) throws IOException {
        Objects.requireNonNull(fixer, "fixer");
        return computeInputFingerprint(
                packRoots,
                bindings,
                adjustVanillaHeight,
                compilerIdentity(fixer));
    }

    static String computeInputFingerprint(
            List<File> packRoots,
            List<IrisGeneratorBinding> bindings,
            boolean adjustVanillaHeight,
            String compilerIdentity
    ) throws IOException {
        Objects.requireNonNull(packRoots, "packRoots");
        Objects.requireNonNull(compilerIdentity, "compilerIdentity");
        List<IrisGeneratorBinding> normalizedBindings = normalizeBindings(bindings);
        MessageDigest digest = sha256();
        updateDigestString(digest, "iris-datapack-compiler-input");
        updateDigestInt(digest, INPUT_FINGERPRINT_SCHEMA);
        updateDigestString(digest, compilerIdentity);
        digest.update((byte) (adjustVanillaHeight ? 1 : 0));
        updateDigestInt(digest, normalizedBindings.size());
        for (IrisGeneratorBinding binding : normalizedBindings) {
            updateDigestString(digest, binding.worldKey().toString());
            updateDigestString(digest, binding.dimension());
        }
        updateDigestInt(digest, packRoots.size());

        for (int index = 0; index < packRoots.size(); index++) {
            File packRoot = Objects.requireNonNull(packRoots.get(index), "pack root");
            Path normalizedRoot = packRoot.toPath().toAbsolutePath().normalize();
            if (!Files.isDirectory(normalizedRoot)) {
                throw new IOException("Iris datapack compiler input root is missing or unsafe: " + normalizedRoot);
            }
            Path realRoot = normalizedRoot.toRealPath();
            updateDigestInt(digest, index);
            updateDigestString(digest, normalizedRoot.toString());
            updateDigestString(digest, realRoot.toString());
            boolean active = hasDimensions(normalizedRoot);
            digest.update((byte) (active ? 1 : 0));
            if (!active) {
                continue;
            }

            List<CompilerInputEntry> entries = collectCompilerInputEntries(normalizedRoot);
            updateDigestInt(digest, entries.size());
            byte[] buffer = new byte[INPUT_BUFFER_BYTES];
            for (CompilerInputEntry entry : entries) {
                updateDigestString(digest, entry.relativePath());
                updateDigestLong(digest, entry.size());
                long readBytes = 0L;
                try (InputStream input = Files.newInputStream(
                        entry.source(),
                        StandardOpenOption.READ,
                        LinkOption.NOFOLLOW_LINKS)) {
                    int read;
                    while ((read = input.read(buffer)) >= 0) {
                        if (read > 0) {
                            digest.update(buffer, 0, read);
                            readBytes += read;
                        }
                    }
                }
                if (readBytes != entry.size()) {
                    throw new IOException("Iris datapack compiler input changed while hashing: " + entry.source());
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public static Map<String, String> computeRegistryRequirements(
            List<File> packRoots,
            IDataFixer fixer
    ) throws IOException {
        Objects.requireNonNull(packRoots, "packRoots");
        Objects.requireNonNull(fixer, "fixer");
        LinkedHashMap<String, String> requirements = new LinkedHashMap<>();
        Set<String> installedBiomeKeys = new LinkedHashSet<>();
        for (File packRoot : packRoots) {
            PackDirectoryResolver.requireSafePackTree(packRoot);
            if (!hasDimensions(packRoot.toPath())) {
                continue;
            }
            IrisData data = IrisData.openDatapackCompiler(packRoot);
            try {
                for (IrisDimension dimension : loadPack(packRoot, data).dimensions()) {
                    collectRegistryRequirements(
                            requirements,
                            installedBiomeKeys,
                            dimension,
                            fixer);
                }
            } finally {
                data.close();
            }
        }
        return Map.copyOf(requirements);
    }

    public static Map<String, String> computeRegistryRequirements(
            IrisDimension dimension,
            IDataFixer fixer
    ) throws IOException {
        IrisDimension requiredDimension = Objects.requireNonNull(dimension, "dimension");
        IDataFixer requiredFixer = Objects.requireNonNull(fixer, "fixer");
        LinkedHashMap<String, String> requirements = new LinkedHashMap<>();
        collectRegistryRequirements(
                requirements,
                new LinkedHashSet<>(),
                requiredDimension,
                requiredFixer);
        return Map.copyOf(requirements);
    }

    private static void collectRegistryRequirements(
            Map<String, String> requirements,
            Set<String> installedBiomeKeys,
            IrisDimension dimension,
            IDataFixer fixer
    ) {
        requirements.put(
                "dimension_type/iris:" + dimension.getDimensionTypeKey(),
                fingerprintContent(dimension.getDimensionType().toJson(fixer)));

        String namespace = dimension.getLoadKey().toLowerCase(Locale.ROOT);
        for (IrisBiome biome : dimension.getAllBiomes(dimension::getLoader)) {
            if (biome == null || !biome.isCustom()) {
                continue;
            }
            String derivativeKey = biome.getVanillaDerivativeKey();
            for (IrisBiomeCustom customBiome : biome.getCustomDerivitives()) {
                if (customBiome == null) {
                    continue;
                }
                String biomeKey = namespace + ":" + customBiome.getId();
                if (!installedBiomeKeys.add(biomeKey)) {
                    continue;
                }
                requirements.put(
                        "worldgen/biome/" + biomeKey,
                        fingerprintContent(customBiome.generateJson(fixer)));
                TreeSet<String> tags = new TreeSet<>();
                for (String tag : customBiome.getEffectiveTags(derivativeKey)) {
                    String normalizedTag = normalizeRegistryKey(tag);
                    if (normalizedTag != null) {
                        tags.add(normalizedTag);
                    }
                }
                requirements.put(
                        "worldgen/biome_tags/" + biomeKey,
                        fingerprintContent(String.join("\n", tags)));
            }
        }
    }

    private static String normalizeRegistryKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        if (normalized.indexOf(':') < 0) {
            normalized = "minecraft:" + normalized;
        }
        return REGISTRY_KEY_PATTERN.matcher(normalized).matches() ? normalized : null;
    }

    private static String fingerprintContent(String content) {
        MessageDigest digest = sha256();
        updateDigestString(digest, Objects.requireNonNull(content, "registry content"));
        return HexFormat.of().formatHex(digest.digest());
    }

    public static String compilerIdentity(IDataFixer fixer) {
        IDataFixer requiredFixer = Objects.requireNonNull(fixer, "fixer");
        return String.join(
                "|",
                Integer.toString(INPUT_FINGERPRINT_SCHEMA),
                BuildConstants.COMMIT,
                BuildConstants.MINECRAFT_VERSION,
                requiredFixer.getClass().getName(),
                Integer.toString(DataVersion.minSupportedPackFormat()),
                Integer.toString(DataVersion.getLatest().getPackFormat()));
    }

    public static CompilationResult compile(
            List<File> packRoots,
            KList<File> datapackRoots,
            List<IrisGeneratorBinding> bindings,
            IDataFixer fixer,
            boolean adjustVanillaHeight
    ) throws IOException {
        Objects.requireNonNull(packRoots, "packRoots");
        Objects.requireNonNull(datapackRoots, "datapackRoots");
        Objects.requireNonNull(fixer, "fixer");
        List<IrisGeneratorBinding> normalizedBindings = normalizeBindings(bindings);
        if (datapackRoots.isEmpty()) {
            throw new IOException("No Iris datapack output roots were provided");
        }

        resetOutputRoots(datapackRoots);
        IrisDimension.clearGeneratedBiomeTags(datapackRoots);

        DimensionHeight height = new DimensionHeight(fixer);
        Map<String, KSet<String>> biomes = new LinkedHashMap<>();
        Map<String, List<DimensionCandidate>> dimensions = new LinkedHashMap<>();
        PackUse packUse = new PackUse(packRoots, normalizedBindings);
        int packCount = 0;
        int dimensionCount = 0;
        for (File packRoot : packRoots) {
            PackDirectoryResolver.requireSafePackTree(packRoot);
            if (!hasDimensions(packRoot.toPath())) {
                continue;
            }
            IrisData data = IrisData.openDatapackCompiler(packRoot);
            try {
                int installedDimensions = 0;
                for (IrisDimension dimension : dimensionsOfUsedPack(packRoot, loadPack(packRoot, data), packUse)) {
                    IrisLogging.debug("  Compiling Dimension " + dimension.getLoadFile().getPath());
                    height.merge(dimension);
                    dimensions.computeIfAbsent(dimension.getLoadKey(), ignored -> new ArrayList<>())
                            .add(new DimensionCandidate(
                                    dimension,
                                    packRoot.toPath().toAbsolutePath().normalize(),
                                    dimension.getDimensionType().toJson(fixer)
                            ));
                    KSet<String> seenBiomes = biomes.computeIfAbsent(dimension.getLoadKey(), ignored -> new KSet<>());
                    dimension.installBiomes(fixer, dimension::getLoader, datapackRoots, seenBiomes);
                    dimension.installDimensionType(fixer, datapackRoots);
                    installedDimensions++;
                    dimensionCount++;
                }
                if (installedDimensions > 0) {
                    packCount++;
                }
            } finally {
                data.close();
            }
        }

        IrisDimension.writeShared(datapackRoots, height, adjustVanillaHeight);
        createDimensionTypeFolders(datapackRoots);
        installLevelStemBindings(normalizedBindings, dimensions, datapackRoots);
        validateOutputs(datapackRoots, dimensionCount, normalizedBindings.size());
        return new CompilationResult(packCount, dimensionCount, countBiomes(biomes));
    }

    /**
     * The dimensions of the pack at {@code packRoot}. A pack that cannot load stops the compile when a world uses it,
     * so the server never starts that world without its dimension; a pack no world uses is left out, with a warning.
     */
    private static List<IrisDimension> dimensionsOfUsedPack(File packRoot, LoadedPack pack, PackUse packUse)
            throws IOException {
        if (pack.loads()) {
            return pack.dimensions();
        }
        List<String> users = packUse.worldsUsing(packRoot, pack.dimensionKeys());
        if (!users.isEmpty()) {
            throw new IOException("Iris pack " + packRoot + " cannot load (" + pack.refusal() + ") and world(s) "
                    + String.join(", ", users) + " use it. Fix the pack, then start the server again.");
        }
        IrisLogging.warn("Iris pack %s cannot load (%s). No world uses it, so it is left out and the server starts.",
                packRoot, pack.refusal());
        return List.of();
    }

    /**
     * Every dimension of the pack, or none with the reason when its modules or any of its dimensions cannot load. A
     * pack that cannot load is left out of the datapack as a whole, never in part.
     */
    private static LoadedPack loadPack(File packRoot, IrisData data) throws IOException {
        String[] dimensionKeys = data.getDimensionLoader().getPossibleKeys();
        if (dimensionKeys == null || dimensionKeys.length == 0) {
            throw new IOException("Iris pack has no dimension definitions: " + packRoot);
        }
        List<String> keys = List.of(dimensionKeys);
        String refusal = moduleRefusal(data);
        if (refusal != null) {
            return LoadedPack.refused(keys, refusal);
        }
        List<IrisDimension> loaded = new ArrayList<>();
        for (String key : keys) {
            IrisDimension dimension = data.getDimensionLoader().load(key);
            if (dimension == null) {
                return LoadedPack.refused(keys, "its dimension '" + key + "' failed to load; the error above says why");
            }
            loaded.add(dimension);
        }
        return new LoadedPack(keys, List.copyOf(loaded), null);
    }

    private record LoadedPack(List<String> dimensionKeys, List<IrisDimension> dimensions, String refusal) {
        static LoadedPack refused(List<String> dimensionKeys, String refusal) {
            return new LoadedPack(dimensionKeys, List.of(), refusal);
        }

        boolean loads() {
            return refusal == null;
        }
    }

    /** Why the pack's modules cannot load, or null when they load. */
    private static String moduleRefusal(IrisData data) {
        try {
            data.getModules().pack();
            return null;
        } catch (PackModulesException failure) {
            return failure.getMessage();
        }
    }

    /**
     * The folder of Iris's dimension types exists in every compiled datapack, also when every pack was left out, so a
     * datapack that compiled no type still reads as complete.
     */
    private static void createDimensionTypeFolders(Collection<File> datapackRoots) throws IOException {
        for (File datapackRoot : datapackRoots) {
            Files.createDirectories(datapackRoot.toPath().resolve(DIMENSION_TYPE_FOLDER));
        }
    }

    private static void installLevelStemBindings(
            List<IrisGeneratorBinding> bindings,
            Map<String, List<DimensionCandidate>> dimensions,
            Collection<File> datapackRoots
    ) throws IOException {
        for (IrisGeneratorBinding binding : bindings) {
            DimensionCandidate selected = resolveDimension(binding, dimensions.get(binding.dimension()));
            String typeKey = "iris:" + selected.dimension().getDimensionTypeKey();
            String levelStem = FLAT_VOID_LEVEL_STEM.formatted(typeKey);
            for (File datapackRoot : datapackRoots) {
                Path output = datapackRoot.toPath()
                        .toAbsolutePath()
                        .normalize()
                        .resolve("data/iris/dimension")
                        .resolve(binding.worldKey().key() + ".json");
                Files.createDirectories(output.getParent());
                Files.writeString(
                        output,
                        levelStem,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE
                );
            }
        }
    }

    private static DimensionCandidate resolveDimension(
            IrisGeneratorBinding binding,
            List<DimensionCandidate> candidates
    ) throws IOException {
        if (candidates == null || candidates.isEmpty()) {
            throw new IOException("Iris world " + binding.worldKey() + " selects missing dimension \""
                    + binding.dimension() + "\".");
        }
        DimensionCandidate selected = candidates.getFirst();
        for (int index = 1; index < candidates.size(); index++) {
            DimensionCandidate candidate = candidates.get(index);
            if (!selected.dimensionTypeJson().equals(candidate.dimensionTypeJson())) {
                throw new IOException("Iris world " + binding.worldKey() + " selects ambiguous dimension \""
                        + binding.dimension() + "\" from " + selected.packRoot() + " and "
                        + candidate.packRoot() + ".");
            }
        }
        return selected;
    }

    private static List<IrisGeneratorBinding> normalizeBindings(List<IrisGeneratorBinding> bindings)
            throws IOException {
        List<IrisGeneratorBinding> requiredBindings = List.copyOf(
                Objects.requireNonNull(bindings, "bindings")
        );
        Map<WorldSlotKey, IrisGeneratorBinding> byWorld = new LinkedHashMap<>();
        for (IrisGeneratorBinding binding : requiredBindings) {
            IrisGeneratorBinding requiredBinding = Objects.requireNonNull(binding, "binding");
            IrisGeneratorBinding previous = byWorld.putIfAbsent(
                    requiredBinding.worldKey(),
                    requiredBinding
            );
            if (previous != null) {
                throw new IOException("Multiple Iris LevelStem bindings target "
                        + requiredBinding.worldKey() + ".");
            }
        }
        ArrayList<IrisGeneratorBinding> normalized = new ArrayList<>(byWorld.values());
        normalized.sort(Comparator.comparing(binding -> binding.worldKey().toString()));
        return List.copyOf(normalized);
    }

    private static void collectInstalledPackRoots(
            Path packsRoot,
            Map<Path, File> roots,
            boolean validateWholePack
    ) throws IOException {
        List<File> candidates = PackDirectoryResolver.listVisiblePackDirectoriesOrThrow(packsRoot.toFile());
        for (File candidate : candidates) {
            addPackRoot(candidate.toPath(), roots, validateWholePack);
        }
    }

    private static void collectWorldPackRoots(
            Path dimensionsRoot,
            Map<Path, File> roots,
            boolean validateWholePack
    ) throws IOException {
        if (Files.isSymbolicLink(dimensionsRoot)
                || !Files.isDirectory(dimensionsRoot, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        List<Path> namespaces = visibleDirectories(dimensionsRoot);
        List<Path> candidates = new ArrayList<>();
        for (Path namespace : namespaces) {
            collectNamespaceWorldPackRoots(namespace, candidates);
        }
        candidates.sort(Comparator.comparing(Path::toString));
        for (Path candidate : candidates) {
            addPackRoot(candidate, roots, validateWholePack);
        }
    }

    private static void collectNamespaceWorldPackRoots(
            Path namespace,
            List<Path> candidates
    ) throws IOException {
        Files.walkFileTree(namespace, Set.of(), WORLD_PACK_SCAN_DEPTH, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(
                    Path directory,
                    BasicFileAttributes attributes
            ) {
                if (!directory.equals(namespace)
                        && PackDirectoryResolver.isHiddenName(directory.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Path irisRoot = directory.resolve("iris");
                Path candidate = irisRoot.resolve("pack");
                if (!Files.isSymbolicLink(irisRoot)
                        && !Files.isSymbolicLink(candidate)
                        && Files.isDirectory(candidate, LinkOption.NOFOLLOW_LINKS)
                        && hasDimensions(candidate)) {
                    candidates.add(candidate);
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static List<Path> visibleDirectories(Path root) throws IOException {
        try (Stream<Path> entries = Files.list(root)) {
            return entries
                    .filter(entry -> !PackDirectoryResolver.isHiddenName(entry.getFileName().toString()))
                    .filter(entry -> !Files.isSymbolicLink(entry))
                    .filter(entry -> Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
    }

    private static void addPackRoot(
            Path root,
            Map<Path, File> roots,
            boolean validateWholePack
    ) throws IOException {
        if (!hasDimensions(root)) {
            return;
        }
        Path normalized = root.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized)) {
            return;
        }
        Path identity = normalized.toRealPath();
        if (!Files.isDirectory(identity, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (validateWholePack) {
            PackDirectoryResolver.requireSafePackTree(normalized.toFile());
        }
        roots.putIfAbsent(identity, normalized.toFile());
    }

    private static boolean hasDimensions(Path root) {
        Path dimensions = root.resolve("dimensions");
        if (Files.isSymbolicLink(dimensions)
                || !Files.isDirectory(dimensions, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        try (Stream<Path> stream = Files.list(dimensions)) {
            return stream.anyMatch(path -> !Files.isSymbolicLink(path)
                    && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                    && path.getFileName().toString().endsWith(".json"));
        } catch (IOException e) {
            return false;
        }
    }

    private static List<CompilerInputEntry> collectCompilerInputEntries(Path packRoot) throws IOException {
        List<CompilerInputEntry> entries = new ArrayList<>();
        for (String directoryName : INPUT_DIRECTORIES) {
            Path directory = packRoot.resolve(directoryName);
            if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            if (Files.isSymbolicLink(directory)
                    || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Iris datapack compiler input is missing or unsafe: " + directory);
            }
            Files.walkFileTree(directory, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path child, BasicFileAttributes attributes) throws IOException {
                    if (attributes.isSymbolicLink() || Files.isSymbolicLink(child)) {
                        throw new IOException("Iris datapack compiler input contains a symbolic link: " + child);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                    if (attributes.isSymbolicLink() || Files.isSymbolicLink(file)) {
                        throw new IOException("Iris datapack compiler input contains a symbolic link: " + file);
                    }
                    if (!attributes.isRegularFile()) {
                        throw new IOException("Iris datapack compiler input contains an unsupported entry: " + file);
                    }
                    if (file.getFileName().toString().endsWith(".json")) {
                        String relativePath = packRoot.relativize(file).toString().replace(File.separatorChar, '/');
                        entries.add(new CompilerInputEntry(file, relativePath, attributes.size()));
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException failure) throws IOException {
                    throw new IOException("Unable to inspect Iris datapack compiler input: " + file, failure);
                }
            });
        }
        entries.sort(Comparator.comparing(CompilerInputEntry::relativePath));
        return entries;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }

    private static void updateDigestString(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        updateDigestInt(digest, bytes.length);
        digest.update(bytes);
    }

    private static void updateDigestInt(MessageDigest digest, int value) {
        for (int shift = Integer.SIZE - Byte.SIZE; shift >= 0; shift -= Byte.SIZE) {
            digest.update((byte) (value >>> shift));
        }
    }

    private static void updateDigestLong(MessageDigest digest, long value) {
        for (int shift = Long.SIZE - Byte.SIZE; shift >= 0; shift -= Byte.SIZE) {
            digest.update((byte) (value >>> shift));
        }
    }

    private static void resetOutputRoots(Collection<File> datapackRoots) throws IOException {
        for (File datapackRoot : datapackRoots) {
            Path root = datapackRoot.toPath().toAbsolutePath().normalize();
            if (Files.isSymbolicLink(root)) {
                throw new IOException("Iris datapack output root is a symbolic link: " + root);
            }
            if (Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
                if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Iris datapack output root is not a directory: " + root);
                }
                AtomicDirectoryPublisher.deleteTree(root);
            }
            Files.createDirectories(root);
        }
    }

    private static void validateOutputs(
            Collection<File> datapackRoots,
            int dimensionCount,
            int levelStemCount
    ) throws IOException {
        for (File datapackRoot : datapackRoots) {
            Path root = datapackRoot.toPath();
            if (!Files.isRegularFile(root.resolve("pack.mcmeta"))) {
                throw new IOException("Iris datapack metadata was not generated at " + root);
            }
            if (dimensionCount > 0 && !Files.isDirectory(root.resolve(DIMENSION_TYPE_FOLDER))) {
                throw new IOException("Iris dimension types were not generated at " + root);
            }
            if (levelStemCount > 0 && !Files.isDirectory(root.resolve("data/iris/dimension"))) {
                throw new IOException("Iris LevelStem bindings were not generated at " + root);
            }
        }
    }

    private static int countBiomes(Map<String, KSet<String>> biomes) {
        int count = 0;
        for (KSet<String> values : biomes.values()) {
            count += values.size();
        }
        return count;
    }

    public record CompilationResult(int packCount, int dimensionCount, int biomeCount) {
    }

    private record CompilerInputEntry(Path source, String relativePath, long size) {
    }

    private record DimensionCandidate(
            IrisDimension dimension,
            Path packRoot,
            String dimensionTypeJson
    ) {
    }

    public static final class DimensionHeight {
        private final IDataFixer fixer;
        private final AtomicIntegerArray[] dimensions = new AtomicIntegerArray[3];

        public DimensionHeight(IDataFixer fixer) {
            this.fixer = Objects.requireNonNull(fixer, "fixer");
            for (int index = 0; index < dimensions.length; index++) {
                dimensions[index] = new AtomicIntegerArray(new int[]{
                        Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE
                });
            }
        }

        public void merge(IrisDimension dimension) {
            AtomicIntegerArray values = dimensions[dimension.getBaseDimension().ordinal()];
            values.updateAndGet(0, current -> Math.min(current, dimension.getMinHeight()));
            values.updateAndGet(1, current -> Math.max(current, dimension.getMaxHeight()));
            values.updateAndGet(2, current -> Math.max(current, dimension.getLogicalHeight()));
        }

        public String[] jsonStrings() {
            IDataFixer.Dimension[] types = IDataFixer.Dimension.values();
            String[] output = new String[types.length];
            for (int index = 0; index < types.length; index++) {
                output[index] = jsonString(types[index]);
            }
            return output;
        }

        private String jsonString(IDataFixer.Dimension dimension) {
            AtomicIntegerArray values = dimensions[dimension.ordinal()];
            int minY = values.get(0);
            int maxY = values.get(1);
            int logicalHeight = values.get(2);
            if (minY == Integer.MAX_VALUE || maxY == Integer.MIN_VALUE || logicalHeight == Integer.MIN_VALUE) {
                return null;
            }
            return fixer.createDimension(dimension, minY, maxY - minY, logicalHeight, null).toString(4);
        }
    }
}
