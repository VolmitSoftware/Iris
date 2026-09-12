package art.arcane.iris.pack.datapack;

import art.arcane.iris.world.WorldSlotKey;

import art.arcane.iris.BuildConstants;
import art.arcane.iris.world.lifecycle.BukkitWorldConfiguration.IrisGeneratorBinding;
import art.arcane.iris.pack.validation.ContentGate;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.iris.platform.bukkit.nms.datapack.DataVersion;
import art.arcane.iris.platform.bukkit.nms.datapack.IDataFixer;
import art.arcane.iris.pack.AtomicDirectoryPublisher;
import art.arcane.iris.pack.PackDirectoryResolver;
import art.arcane.iris.world.history.GenerationEpoch;
import art.arcane.iris.world.history.GenerationHistory;
import art.arcane.iris.world.history.GenerationRegistryContract;
import art.arcane.iris.world.history.GenerationRegistryContract.PhysicalResourceKey;
import art.arcane.iris.world.history.GenerationRegistryContractFactory;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.biome.IrisBiomeCustom;
import art.arcane.iris.generation.biome.IrisCustomBiomeAliasResolver;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KSet;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

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
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class IrisDatapackCompiler {
    private static final int INPUT_FINGERPRINT_SCHEMA = 4;
    private static final int INPUT_BUFFER_BYTES = 64 * 1024;
    private static final int WORLD_PACK_SCAN_DEPTH = 8;
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
        collectCanonicalWorldPackRoot(serverRoot, roots, validateWholePack);
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
        for (GenerationRegistryContract contract : retainedRegistryContracts(packRoots)) {
            updateDigestString(digest, contract.fingerprint());
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
        Map<String, Set<String>> biomeTags = new TreeMap<>();
        for (File packRoot : packRoots) {
            PackDirectoryResolver.requireSafePackTree(packRoot);
            if (!hasDimensions(packRoot.toPath())) {
                continue;
            }
            BiomeAliasContext aliasContext = biomeAliasContext(packRoot.toPath());
            IrisData data = IrisData.openDatapackCompiler(packRoot);
            try {
                if (aliasContext.retainedContract() != null) {
                    data.bindGenerationRegistryContract(aliasContext.retainedContract());
                }
                ResourceLoader<IrisDimension> loader = data.getDimensionLoader();
                String[] possibleKeys = loader.getPossibleKeys();
                if (possibleKeys == null || possibleKeys.length == 0) {
                    throw new IOException("Iris pack has no dimension definitions: " + packRoot);
                }
                for (String possibleKey : possibleKeys) {
                    IrisDimension dimension = loader.load(possibleKey);
                    if (dimension == null) {
                        throw new IOException("Unable to load Iris dimension '" + possibleKey + "' from " + packRoot);
                    }
                    collectRegistryRequirements(
                            requirements,
                            biomeTags,
                            dimension,
                            fixer,
                            aliasContext);
                }
            } finally {
                data.close();
            }
        }
        for (GenerationRegistryContract contract : retainedRegistryContracts(packRoots)) {
            for (PhysicalResourceKey key : contract.generatedSources().keySet()) {
                putRegistryRequirement(requirements, registryPath(key) + "/" + key.resourceKey(),
                        fingerprintContent(GenerationRegistryContractFactory.renderGeneratedSource(contract, key, fixer)));
            }
            contract.biomeTags().forEach((biome, tags) ->
                    biomeTags.computeIfAbsent(biome, ignored -> new TreeSet<>()).addAll(tags));
        }
        addBiomeTagRequirements(requirements, biomeTags);
        return Map.copyOf(requirements);
    }

    public static Map<String, String> computeRegistryRequirements(
            IrisDimension dimension,
            IDataFixer fixer
    ) throws IOException {
        IrisDimension requiredDimension = Objects.requireNonNull(dimension, "dimension");
        IDataFixer requiredFixer = Objects.requireNonNull(fixer, "fixer");
        IrisData data = Objects.requireNonNull(
                requiredDimension.getLoader(),
                "Iris dimension loader"
        );
        LinkedHashMap<String, String> requirements = new LinkedHashMap<>();
        Map<String, Set<String>> biomeTags = new TreeMap<>();
        BiomeAliasContext aliasContext = BiomeAliasContext.contentAddressedOnly();
        collectRegistryRequirements(
                requirements,
                biomeTags,
                requiredDimension,
                requiredFixer,
                aliasContext);
        collectDimensionStackRegistryRequirements(
                requirements,
                biomeTags,
                requiredDimension,
                requiredFixer,
                aliasContext);
        addBiomeTagRequirements(requirements, biomeTags);
        return Map.copyOf(requirements);
    }

    private static void collectDimensionStackRegistryRequirements(
            Map<String, String> requirements,
            Map<String, Set<String>> biomeTags,
            IrisDimension hostDimension,
            IDataFixer fixer,
            BiomeAliasContext aliasContext
    ) throws IOException {
        if (!hostDimension.hasDimensionStack()) {
            return;
        }
        IrisData data = hostDimension.getLoader();
        if (data == null) {
            throw new IOException("Dimension stack host '" + hostDimension.getLoadKey()
                    + "' has no pack loader");
        }
        LinkedHashSet<String> visited = new LinkedHashSet<>();
        visited.add(hostDimension.getLoadKey());
        for (String dimensionKey : hostDimension.getDimensionStack().getDimensions()) {
            if (dimensionKey == null || dimensionKey.isBlank() || !visited.add(dimensionKey)) {
                continue;
            }
            IrisDimension sourceDimension = data.getDimensionLoader().load(dimensionKey);
            if (sourceDimension == null) {
                throw new IOException("Dimension stack host '" + hostDimension.getLoadKey()
                        + "' references missing dimension '" + dimensionKey + "'");
            }
            collectRegistryRequirements(
                    requirements,
                    biomeTags,
                    sourceDimension,
                    fixer,
                    aliasContext);
        }
    }

    private static void collectRegistryRequirements(
            Map<String, String> requirements,
            Map<String, Set<String>> biomeTags,
            IrisDimension dimension,
            IDataFixer fixer,
            BiomeAliasContext aliasContext
    ) throws IOException {
        String dimensionTypeResourceKey = "iris:" + dimension.getDimensionTypeKey();
        String dimensionTypeSource = aliasContext.generatedSource(
                GenerationRegistryContractFactory.DIMENSION_TYPE_REGISTRY,
                dimensionTypeResourceKey,
                dimension.getDimensionType().toJson(fixer),
                fixer
        );
        putRegistryRequirement(
                requirements,
                "dimension_type/" + dimensionTypeResourceKey,
                fingerprintContent(dimensionTypeSource));

        ContentGate contentGate = dimension.getLoader() == null ? null : dimension.getLoader().getContentGate();
        for (IrisBiome biome : dimension.getAllBiomes(dimension::getLoader)) {
            if (biome == null || !biome.isCustom()) {
                continue;
            }
            String derivativeKey = biome.getVanillaDerivativeKey();
            for (IrisBiomeCustom customBiome : biome.getCustomDerivitives()) {
                if (customBiome == null) {
                    continue;
                }
                String json = customBiome.generateJson(fixer, contentGate);
                String currentPhysicalKey = GenerationRegistryContractFactory.customBiomeResourceKey(
                        dimension.getLoadKey(),
                        customBiome,
                        contentGate
                );
                String physicalKey = aliasContext.physicalResourceKey(
                        dimension,
                        biome,
                        customBiome,
                        currentPhysicalKey
                );
                LinkedHashSet<String> resourceKeys = new LinkedHashSet<>();
                resourceKeys.add(physicalKey);
                Collection<String> aliases = aliasContext.aliases(dimension, biome, customBiome, physicalKey);
                if (!aliases.isEmpty() && physicalKey.equals(dimension.getLoader()
                        .customBiomeResourceKey(dimension, customBiome.getId()))) {
                    resourceKeys.addAll(aliases);
                }
                TreeSet<String> tags = new TreeSet<>();
                for (String tag : customBiome.getEffectiveTags(derivativeKey)) {
                    String normalizedTag = normalizeRegistryKey(tag);
                    if (normalizedTag != null) {
                        tags.add(normalizedTag);
                    }
                }
                for (String resourceKey : resourceKeys) {
                    String emittedJson = aliasContext.generatedSource(
                            GenerationRegistryContractFactory.BIOME_REGISTRY,
                            resourceKey,
                            json,
                            fixer
                    );
                    putRegistryRequirement(
                            requirements,
                            "worldgen/biome/" + resourceKey,
                            fingerprintContent(emittedJson)
                    );
                    biomeTags.computeIfAbsent(resourceKey, ignored -> new TreeSet<>()).addAll(tags);
                }
            }
        }
    }

    private static void addBiomeTagRequirements(
            Map<String, String> requirements,
            Map<String, Set<String>> biomeTags
    ) throws IOException {
        for (Map.Entry<String, Set<String>> entry : biomeTags.entrySet()) {
            putRegistryRequirement(
                    requirements,
                    "worldgen/biome_tags/" + entry.getKey(),
                    fingerprintContent(String.join("\n", entry.getValue()))
            );
        }
    }

    private static void putRegistryRequirement(
            Map<String, String> requirements,
            String resourceKey,
            String contentFingerprint
    ) throws IOException {
        String previous = requirements.putIfAbsent(resourceKey, contentFingerprint);
        if (previous != null && !previous.equals(contentFingerprint)) {
            throw new IOException("Conflicting Iris datapack registry resource '" + resourceKey + "'.");
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
        int packCount = 0;
        int dimensionCount = 0;
        for (File packRoot : packRoots) {
            PackDirectoryResolver.requireSafePackTree(packRoot);
            if (!hasDimensions(packRoot.toPath())) {
                continue;
            }
            BiomeAliasContext aliasContext = biomeAliasContext(packRoot.toPath());
            IrisData data = IrisData.openDatapackCompiler(packRoot);
            try {
                if (aliasContext.retainedContract() != null) {
                    data.bindGenerationRegistryContract(aliasContext.retainedContract());
                }
                ResourceLoader<IrisDimension> loader = data.getDimensionLoader();
                String[] possibleKeys = loader.getPossibleKeys();
                if (possibleKeys == null || possibleKeys.length == 0) {
                    throw new IOException("Iris pack has no dimension definitions: " + packRoot);
                }

                int installedDimensions = 0;
                for (String possibleKey : possibleKeys) {
                    IrisDimension dimension = loader.load(possibleKey);
                    if (dimension == null) {
                        throw new IOException("Unable to load Iris dimension '" + possibleKey + "' from " + packRoot);
                    }
                    IrisLogging.debug("  Compiling Dimension " + dimension.getLoadFile().getPath());
                    height.merge(dimension);
                    dimensions.computeIfAbsent(dimension.getLoadKey(), ignored -> new ArrayList<>())
                            .add(new DimensionCandidate(
                                    dimension,
                                    packRoot.toPath().toAbsolutePath().normalize(),
                                    aliasContext.generatedSource(
                                            GenerationRegistryContractFactory.DIMENSION_TYPE_REGISTRY,
                                            "iris:" + dimension.getDimensionTypeKey(),
                                            dimension.getDimensionType().toJson(fixer),
                                            fixer
                                    )
                            ));
                    KSet<String> seenBiomes = biomes.computeIfAbsent(dimension.getLoadKey(), ignored -> new KSet<>());
                    dimension.installBiomes(
                            fixer,
                            dimension::getLoader,
                            datapackRoots,
                            seenBiomes,
                            aliasContext
                    );
                    dimension.installDimensionType(fixer, datapackRoots, aliasContext);
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
        installRetainedRegistrySources(packRoots, datapackRoots, fixer);
        installLevelStemBindings(normalizedBindings, dimensions, datapackRoots);
        validateOutputs(datapackRoots, dimensionCount, normalizedBindings.size());
        return new CompilationResult(packCount, dimensionCount, countBiomes(biomes));
    }

    public static void installRetainedRegistrySources(List<File> packRoots, Collection<File> datapackRoots,
                                                     IDataFixer fixer) throws IOException {
        Map<PhysicalResourceKey, String> sources = new TreeMap<>();
        Map<String, Set<String>> tags = new TreeMap<>();
        for (GenerationRegistryContract contract : retainedRegistryContracts(packRoots)) {
            for (PhysicalResourceKey key : contract.generatedSources().keySet()) {
                String source = GenerationRegistryContractFactory.renderGeneratedSource(contract, key, fixer);
                String previous = sources.putIfAbsent(key, source);
                if (previous != null && !JsonParser.parseString(previous).equals(JsonParser.parseString(source))) {
                    throw new IOException("Conflicting retained registry source for " + key);
                }
            }
            contract.biomeTags().forEach((biome, memberships) -> {
                for (String tag : memberships) {
                    tags.computeIfAbsent(tag, ignored -> new TreeSet<>()).add(biome);
                }
            });
        }
        for (File root : datapackRoots) {
            for (Map.Entry<PhysicalResourceKey, String> entry : sources.entrySet()) {
                Path output = registryOutput(root.toPath(), registryPath(entry.getKey()), entry.getKey().resourceKey());
                if (Files.exists(output)) {
                    String previous = Files.readString(output, StandardCharsets.UTF_8);
                    if (!JsonParser.parseString(previous).equals(JsonParser.parseString(entry.getValue()))) {
                        throw new IOException("Conflicting retained registry output at " + output);
                    }
                    continue;
                }
                Files.createDirectories(output.getParent());
                Files.writeString(output, entry.getValue(), StandardCharsets.UTF_8);
            }
            for (Map.Entry<String, Set<String>> entry : tags.entrySet()) {
                writeRetainedBiomeTag(registryOutput(root.toPath(), "tags/worldgen/biome", entry.getKey()), entry.getValue());
            }
        }
    }

    private static void writeRetainedBiomeTag(Path output, Set<String> biomes) throws IOException {
        Set<String> merged = new TreeSet<>(biomes);
        if (Files.exists(output)) {
            JsonObject existing = JsonParser.parseString(Files.readString(output, StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement value : existing.getAsJsonArray("values")) {
                merged.add(value.getAsString());
            }
        }
        JsonArray values = new JsonArray();
        merged.forEach(values::add);
        JsonObject tag = new JsonObject();
        tag.addProperty("replace", false);
        tag.add("values", values);
        Files.createDirectories(output.getParent());
        Files.writeString(output, tag.toString(), StandardCharsets.UTF_8);
    }

    private static Path registryOutput(Path root, String registryPath, String resourceKey) throws IOException {
        int separator = resourceKey.indexOf(':');
        Path directory = root.toAbsolutePath().normalize().resolve("data")
                .resolve(resourceKey.substring(0, separator)).resolve(registryPath);
        Path output = directory.resolve(resourceKey.substring(separator + 1) + ".json").normalize();
        if (!output.startsWith(directory)) {
            throw new IOException("Unsafe retained registry resource key " + resourceKey);
        }
        return output;
    }

    private static String registryPath(PhysicalResourceKey key) {
        return key.registryKey().substring(key.registryKey().indexOf(':') + 1);
    }

    private static List<GenerationRegistryContract> retainedRegistryContracts(List<File> packRoots) throws IOException {
        Map<String, GenerationRegistryContract> contracts = new TreeMap<>();
        Set<Path> loadedWorlds = new LinkedHashSet<>();
        for (File pack : packRoots) {
            Path dimensionRoot = historyDimensionRoot(pack.toPath());
            if (dimensionRoot == null || !loadedWorlds.add(dimensionRoot)) {
                continue;
            }
            for (GenerationEpoch epoch : GenerationHistory.open(dimensionRoot).manifest().epochs()) {
                contracts.putIfAbsent(epoch.registryContract().fingerprint(), epoch.registryContract());
            }
        }
        return List.copyOf(contracts.values());
    }

    private static Path historyDimensionRoot(Path packRoot) {
        Path pack = packRoot.toAbsolutePath().normalize();
        Path epoch = pack.getParent();
        Path epochs = epoch == null ? null : epoch.getParent();
        Path generation = epochs == null ? null : epochs.getParent();
        Path iris = generation == null ? null : generation.getParent();
        if (iris == null || iris.getParent() == null || !"pack".equals(pack.getFileName().toString())
                || !"epochs".equals(epochs.getFileName().toString())
                || !"generation".equals(generation.getFileName().toString())
                || !"iris".equals(iris.getFileName().toString())) {
            return null;
        }
        return iris.getParent();
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

    private static void collectCanonicalWorldPackRoot(
            Path worldRoot,
            Map<Path, File> roots,
            boolean validateWholePack
    ) throws IOException {
        if (Files.isSymbolicLink(worldRoot)
                || !Files.isDirectory(worldRoot, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        List<Path> candidates = new ArrayList<>();
        collectWorldRootPackRoots(worldRoot, candidates);
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
            ) throws IOException {
                if (!directory.equals(namespace)
                        && PackDirectoryResolver.isHiddenName(directory.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                if (collectWorldRootPackRoots(directory, candidates)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static boolean collectWorldRootPackRoots(
            Path worldRoot,
            List<Path> candidates
    ) throws IOException {
        Path irisRoot = worldRoot.resolve("iris");
        Path generationRoot = irisRoot.resolve("generation");
        if (!Files.isSymbolicLink(irisRoot)
                && (Files.exists(generationRoot, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(generationRoot))) {
            GenerationHistory history = GenerationHistory.open(worldRoot);
            candidates.add(history.activePackRoot());
            if (history.pendingActivation().isPresent()) {
                candidates.add(history.packRoot(history.pendingActivation().orElseThrow().activationId()));
            }
            return true;
        }
        Path legacyPack = irisRoot.resolve("pack");
        if (!Files.isSymbolicLink(irisRoot)
                && !Files.isSymbolicLink(legacyPack)
                && Files.isDirectory(legacyPack, LinkOption.NOFOLLOW_LINKS)
                && hasDimensions(legacyPack)) {
            candidates.add(legacyPack);
            return true;
        }
        return false;
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

    private static BiomeAliasContext biomeAliasContext(Path packRoot) throws IOException {
        Path normalizedRoot = packRoot.toAbsolutePath().normalize();
        Path rootParent = normalizedRoot.getParent();
        if (rootParent == null || !"pack".equals(normalizedRoot.getFileName().toString())) {
            return BiomeAliasContext.contentAddressedOnly();
        }
        if ("iris".equals(rootParent.getFileName().toString())) {
            return BiomeAliasContext.legacyPack();
        }

        Path epochsRoot = rootParent.getParent();
        Path generationRoot = epochsRoot == null ? null : epochsRoot.getParent();
        Path irisRoot = generationRoot == null ? null : generationRoot.getParent();
        Path dimensionRoot = irisRoot == null ? null : irisRoot.getParent();
        if (epochsRoot == null
                || generationRoot == null
                || irisRoot == null
                || dimensionRoot == null
                || !"epochs".equals(epochsRoot.getFileName().toString())
                || !"generation".equals(generationRoot.getFileName().toString())
                || !"iris".equals(irisRoot.getFileName().toString())) {
            return BiomeAliasContext.contentAddressedOnly();
        }

        String epochId = rootParent.getFileName().toString();
        GenerationHistory history = GenerationHistory.open(dimensionRoot);
        GenerationEpoch epoch = history.manifest().epoch(epochId).orElseThrow(
                () -> new IOException("Retained Iris pack is not referenced by its generation history: "
                        + normalizedRoot)
        );
        Path expectedRoot = history.paths().packRoot(epochId).toAbsolutePath().normalize();
        if (!expectedRoot.equals(normalizedRoot)) {
            throw new IOException("Retained Iris pack does not match its generation history: " + normalizedRoot);
        }
        return BiomeAliasContext.retained(epoch.registryContract());
    }

    private static boolean hasDimensions(Path root) {
        Path dimensions = root.resolve("dimensions");
        if (Files.isSymbolicLink(dimensions)
                || !Files.isDirectory(dimensions, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        try (Stream<Path> stream = Files.walk(dimensions)) {
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
            if (dimensionCount > 0 && !Files.isDirectory(root.resolve("data/iris/dimension_type"))) {
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

    private record BiomeAliasContext(
            boolean retainAllLegacyAliases,
            GenerationRegistryContract retainedContract
    ) implements IrisCustomBiomeAliasResolver {
        private static BiomeAliasContext contentAddressedOnly() {
            return new BiomeAliasContext(false, null);
        }

        private static BiomeAliasContext legacyPack() {
            return new BiomeAliasContext(true, null);
        }

        private static BiomeAliasContext retained(GenerationRegistryContract registryContract) {
            return new BiomeAliasContext(
                    false,
                    Objects.requireNonNull(registryContract, "registryContract")
            );
        }

        @Override
        public String physicalResourceKey(
                IrisDimension dimension,
                IrisBiome biome,
                IrisBiomeCustom customBiome,
                String currentPhysicalResourceKey
        ) throws IOException {
            if (retainedContract == null) {
                return currentPhysicalResourceKey;
            }
            return dimension.getLoader().customBiomeResourceKey(dimension, customBiome);
        }

        @Override
        public Collection<String> aliases(
                IrisDimension dimension,
                IrisBiome biome,
                IrisBiomeCustom customBiome,
                String physicalResourceKey
        ) {
            String alias = dimension.getLoadKey().toLowerCase(Locale.ROOT)
                    + ":" + customBiome.getId().toLowerCase(Locale.ROOT);
            if (retainAllLegacyAliases) {
                return List.of(alias);
            }
            if (retainedContract == null) {
                return List.of();
            }
            PhysicalResourceKey key = new PhysicalResourceKey(
                    GenerationRegistryContractFactory.BIOME_REGISTRY,
                    alias
            );
            return retainedContract.definitions().containsKey(key) ? List.of(alias) : List.of();
        }

        @Override
        public String generatedSource(
                String registryKey,
                String resourceKey,
                String currentSource,
                IDataFixer fixer
        ) throws IOException {
            if (retainedContract == null) {
                return currentSource;
            }
            PhysicalResourceKey key = new PhysicalResourceKey(registryKey, resourceKey);
            return GenerationRegistryContractFactory.renderGeneratedSource(retainedContract, key, fixer);
        }
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
