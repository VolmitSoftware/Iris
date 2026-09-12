package art.arcane.iris.pack;

import art.arcane.iris.pack.datapack.IrisDatapackCompiler;
import art.arcane.iris.world.lifecycle.BukkitStartupPaths;
import art.arcane.iris.world.lifecycle.BukkitWorldConfiguration;
import art.arcane.iris.world.lifecycle.BukkitWorldConfiguration.IrisGeneratorBinding;
import art.arcane.iris.platform.bukkit.nms.datapack.DataVersion;
import art.arcane.iris.platform.bukkit.nms.datapack.IDataFixer;
import art.arcane.volmlib.util.collection.KList;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class DefaultPackBootstrapProvisioner {
    private static final List<PackSpec> DEFAULT_PACKS = List.of();
    private static final String WORLD_DATAPACK_DIRECTORY = "iris";
    private static final int MARKER_SCHEMA = 6;
    private static final int MAX_ARCHIVE_ENTRIES = 100_000;
    private static final long MAX_ARCHIVE_BYTES = 512L * 1024L * 1024L;
    private static final long MAX_EXPANDED_BYTES = 2L * 1024L * 1024L * 1024L;
    private static final AtomicBoolean PROVISIONED_THIS_STARTUP = new AtomicBoolean(false);
    private static final AtomicReference<String> PROVISIONED_COMPILER_INPUT_FINGERPRINT =
            new AtomicReference<>("");

    private DefaultPackBootstrapProvisioner() {
    }

    static List<PackSpec> defaultPacks() {
        return DEFAULT_PACKS;
    }

    public static ProvisionResult provision(Path dataDirectory, Consumer<String> feedback) throws IOException {
        return provision(dataDirectory, feedback, BukkitStartupPaths.resolveCurrent());
    }

    public static ProvisionResult provision(
            Path dataDirectory,
            Consumer<String> feedback,
            BukkitStartupPaths startupPaths
    ) throws IOException {
        Objects.requireNonNull(dataDirectory, "dataDirectory");
        Objects.requireNonNull(feedback, "feedback");
        BukkitStartupPaths requiredStartupPaths = Objects.requireNonNull(startupPaths, "startupPaths");
        Path levelRoot = requiredStartupPaths.levelRoot();
        List<IrisGeneratorBinding> bindings = BukkitWorldConfiguration.readIrisGeneratorBindings(
                requiredStartupPaths.bukkitConfiguration().toFile(),
                levelId(levelRoot),
                requiredStartupPaths.levelRoot()
        );
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
        ProvisionOptions options = new ProvisionOptions(
                DEFAULT_PACKS,
                bindings,
                client,
                Clock.systemUTC(),
                Duration.ofMinutes(30),
                Duration.ofSeconds(45),
                3,
                Duration.ofMillis(250),
                MAX_ARCHIVE_BYTES,
                levelRoot
        );
        return provision(dataDirectory, feedback, options);
    }

    public static boolean isProvisioned(Path dataDirectory) {
        if (dataDirectory == null) {
            return false;
        }
        try {
            BukkitStartupPaths startupPaths = BukkitStartupPaths.resolveCurrent();
            List<IrisGeneratorBinding> bindings = BukkitWorldConfiguration.readIrisGeneratorBindings(
                    startupPaths.bukkitConfiguration().toFile(),
                    levelId(startupPaths.levelRoot()),
                    startupPaths.levelRoot()
            );
            return isProvisioned(dataDirectory, startupPaths.levelRoot(), DEFAULT_PACKS, bindings);
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }

    static boolean isProvisioned(
            Path dataDirectory,
            Path levelRoot,
            List<PackSpec> requiredPacks,
            List<IrisGeneratorBinding> bindings
    ) {
        try {
            Path normalizedData = dataDirectory.toAbsolutePath().normalize();
            Path normalizedLevel = levelRoot.toAbsolutePath().normalize();
            Path bootstrapRoot = normalizedData.resolve("bootstrap");
            Path datapackRoot = worldDatapackRoot(normalizedLevel);
            Path markerFile = bootstrapRoot.resolve("provisioned.properties");
            if (!Files.isRegularFile(markerFile) || !isDatapackRoot(datapackRoot)) {
                return false;
            }
            Properties marker = loadProperties(markerFile);
            if (!Integer.toString(MARKER_SCHEMA).equals(marker.getProperty("schema"))) {
                return false;
            }
            IDataFixer fixer = DataVersion.getLatest().get();
            if (fixer == null
                    || !IrisDatapackCompiler.compilerIdentity(fixer)
                    .equals(marker.getProperty("compilerIdentity"))) {
                return false;
            }
            for (PackSpec spec : requiredPacks) {
                Path packRoot = normalizedData.resolve("packs").resolve(spec.key());
                if (!isPackRoot(packRoot, spec)
                        || !spec.source().toString().equals(marker.getProperty(markerKey(spec, "source")))
                        || !spec.requiredDimension().equals(marker.getProperty(markerKey(spec, "requiredDimension")))
                        || !directoryFingerprint(packRoot).equals(marker.getProperty(markerKey(spec, "fingerprint")))) {
                    return false;
                }
            }
            List<File> packRoots = IrisDatapackCompiler.collectPackRoots(
                    normalizedData,
                    normalizedLevel
            );
            return directoryFingerprint(datapackRoot).equals(marker.getProperty("datapackFingerprint"))
                    && datapackRoot.toString().equals(marker.getProperty("datapackPath"))
                    && packRootsFingerprint(packRoots).equals(marker.getProperty("aggregateFingerprint"))
                    && IrisDatapackCompiler.computeInputFingerprint(
                            packRoots,
                            bindings,
                            fixer,
                            false
                    ).equals(marker.getProperty("compilerInputFingerprint"));
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }

    public static boolean wasProvisionedThisStartup() {
        return PROVISIONED_THIS_STARTUP.get();
    }

    public static String compilerInputFingerprintThisStartup() {
        return PROVISIONED_COMPILER_INPUT_FINGERPRINT.get();
    }

    static ProvisionResult provision(Path dataDirectory, Consumer<String> feedback, ProvisionOptions options) throws IOException {
        PROVISIONED_THIS_STARTUP.set(false);
        PROVISIONED_COMPILER_INPUT_FINGERPRINT.set("");
        Path normalizedData = dataDirectory.toAbsolutePath().normalize();
        Path packsRoot = normalizedData.resolve("packs");
        Path bootstrapRoot = normalizedData.resolve("bootstrap");
        Path legacyDatapackRoot = bootstrapRoot.resolve("datapack");
        Path datapacksRoot = options.levelRoot().toAbsolutePath().normalize().resolve("datapacks");
        Path datapackRoot = datapacksRoot.resolve(WORLD_DATAPACK_DIRECTORY);
        Path markerFile = bootstrapRoot.resolve("provisioned.properties");
        Path cacheRoot = normalizedData.resolve("cache/bootstrap");
        Files.createDirectories(packsRoot);
        Files.createDirectories(bootstrapRoot);
        Files.createDirectories(cacheRoot);
        Files.createDirectories(datapacksRoot);

        Properties previousMarker = Files.isRegularFile(markerFile) ? loadProperties(markerFile) : new Properties();
        boolean existingDatapack = isDatapackRoot(datapackRoot);
        List<PackPlan> plans = new ArrayList<>(options.packs().size());
        for (PackSpec spec : options.packs()) {
            Path packRoot = packsRoot.resolve(spec.key()).normalize();
            if (!Objects.equals(packRoot.getParent(), packsRoot)) {
                throw new IOException("Bootstrap pack target escapes the packs directory: " + packRoot);
            }
            boolean existingPack = isPackRoot(packRoot, spec);
            String currentFingerprint = existingPack ? directoryFingerprint(packRoot) : "";
            boolean markerOwned = "true".equals(markerProperty(previousMarker, spec, "managed"));
            boolean unchangedManagedPack = markerOwned
                    && currentFingerprint.equals(markerProperty(previousMarker, spec, "fingerprint"));
            boolean managed = !existingPack || unchangedManagedPack;
            if (Files.isSymbolicLink(packRoot)) {
                managed = false;
            }
            String retainedSourceSha = markerProperty(previousMarker, spec, "sourceSha256");
            Archive archive = managed
                    ? acquireArchive(cacheRoot, previousMarker, spec, feedback, options)
                    : new Archive(null, retainedSourceSha == null || retainedSourceSha.isBlank()
                    ? currentFingerprint
                    : retainedSourceSha);
            boolean replacePack = !existingPack || managed
                    && (!archive.sha256().equals(markerProperty(previousMarker, spec, "sourceSha256"))
                    || !currentFingerprint.equals(markerProperty(previousMarker, spec, "fingerprint")));
            plans.add(new PackPlan(spec, packRoot, existingPack, managed, archive, replacePack));
        }

        Map<PackPlan, Path> stagedPacks = new LinkedHashMap<>();
        List<Path> extractionRoots = new ArrayList<>();
        Path compileContainer = null;
        Path datapackBackup = null;
        boolean datapackReplaced = false;
        List<PackPublication> packPublications = new ArrayList<>();
        boolean committed = false;
        try {
            for (PackPlan plan : plans) {
                if (!plan.replace()) {
                    continue;
                }
                Path extractionRoot = cacheRoot.resolve(".extract-" + plan.spec().key() + "-" + UUID.randomUUID());
                extractionRoots.add(extractionRoot);
                Files.createDirectories(extractionRoot);
                Path extractedPack = extractArchive(plan.archive().path(), extractionRoot, plan.spec());
                Path stagedPack = packsRoot.resolve("." + plan.spec().key() + "-stage-" + UUID.randomUUID());
                copyDirectory(extractedPack, stagedPack);
                validatePackRoot(stagedPack, plan.spec());
                stagedPacks.put(plan, stagedPack);
            }
            for (Map.Entry<PackPlan, Path> entry : stagedPacks.entrySet()) {
                Path backup = replaceWithBackup(entry.getValue(), entry.getKey().root());
                packPublications.add(new PackPublication(entry.getKey().root(), backup));
            }

            List<File> packRoots = IrisDatapackCompiler.collectPackRoots(normalizedData, options.levelRoot());
            IDataFixer fixer = DataVersion.getLatest().get();
            if (fixer == null) {
                throw new IOException("Latest Iris datapack fixer is unavailable during bootstrap");
            }
            String compilerIdentity = IrisDatapackCompiler.compilerIdentity(fixer);
            String aggregateFingerprint = packRootsFingerprint(packRoots);
            String compilerInputFingerprint = IrisDatapackCompiler.computeInputFingerprint(
                    packRoots,
                    options.bindings(),
                    fixer,
                    false
            );
            boolean anyPackReplaced = !packPublications.isEmpty();
            boolean rebuildDatapack = anyPackReplaced
                    || !existingDatapack
                    || !aggregateFingerprint.equals(previousMarker.getProperty("aggregateFingerprint"))
                    || !compilerInputFingerprint.equals(previousMarker.getProperty("compilerInputFingerprint"))
                    || !compilerIdentity.equals(previousMarker.getProperty("compilerIdentity"))
                    || !datapackRoot.toString().equals(previousMarker.getProperty("datapackPath"))
                    || !directoryFingerprint(datapackRoot).equals(previousMarker.getProperty("datapackFingerprint"));
            if (rebuildDatapack) {
                compileContainer = datapacksRoot.resolve("." + WORLD_DATAPACK_DIRECTORY + "-stage-" + UUID.randomUUID());
                Files.createDirectories(compileContainer);
                KList<File> outputFolders = new KList<File>().qadd(compileContainer.toFile());
                IrisDatapackCompiler.compile(packRoots, outputFolders, options.bindings(), fixer, false);
                if (!isDatapackRoot(compileContainer, !packRoots.isEmpty())) {
                    throw new IOException("Canonical Iris datapack compiler produced incomplete output at " + compileContainer);
                }
                datapackBackup = replaceWithBackup(compileContainer, datapackRoot);
                compileContainer = null;
                datapackReplaced = true;
            }

            for (PackPlan plan : plans) {
                validatePackRoot(plan.root(), plan.spec());
            }
            List<File> finalPackRoots = IrisDatapackCompiler.collectPackRoots(
                    normalizedData,
                    options.levelRoot());
            if (!isDatapackRoot(datapackRoot, !finalPackRoots.isEmpty())) {
                throw new IOException("Bootstrap datapack output is incomplete at " + datapackRoot);
            }
            String finalAggregateFingerprint = packRootsFingerprint(finalPackRoots);
            String finalCompilerInputFingerprint = IrisDatapackCompiler.computeInputFingerprint(
                    finalPackRoots,
                    options.bindings(),
                    fixer,
                    false);
            String finalDatapackFingerprint = directoryFingerprint(datapackRoot);
            Properties marker = new Properties();
            marker.setProperty("schema", Integer.toString(MARKER_SCHEMA));
            for (PackPlan plan : plans) {
                marker.setProperty(markerKey(plan.spec(), "source"), plan.spec().source().toString());
                marker.setProperty(markerKey(plan.spec(), "sourceSha256"), plan.archive().sha256());
                marker.setProperty(markerKey(plan.spec(), "managed"), Boolean.toString(plan.managed()));
                marker.setProperty(markerKey(plan.spec(), "fingerprint"), directoryFingerprint(plan.root()));
                marker.setProperty(markerKey(plan.spec(), "requiredDimension"), plan.spec().requiredDimension());
            }
            marker.setProperty("aggregateFingerprint", finalAggregateFingerprint);
            marker.setProperty("compilerIdentity", compilerIdentity);
            marker.setProperty("compilerInputFingerprint", finalCompilerInputFingerprint);
            marker.setProperty("datapackFingerprint", finalDatapackFingerprint);
            marker.setProperty("datapackPath", datapackRoot.toString());
            marker.setProperty("completedAt", Long.toString(options.clock().millis()));
            storePropertiesAtomic(markerFile, marker);
            committed = true;
            PROVISIONED_COMPILER_INPUT_FINGERPRINT.set(finalCompilerInputFingerprint);
            PROVISIONED_THIS_STARTUP.set(true);
            for (PackPublication publication : packPublications) {
                deleteQuietly(publication.backup(), feedback);
            }
            deleteQuietly(datapackBackup, feedback);
            deleteQuietly(legacyDatapackRoot, feedback);

            boolean everyPackMissing = plans.stream().noneMatch(PackPlan::existed);
            ProvisionStatus status;
            if (everyPackMissing && !existingDatapack) {
                status = ProvisionStatus.INSTALLED;
            } else if (anyPackReplaced || rebuildDatapack) {
                status = ProvisionStatus.UPDATED;
            } else {
                status = ProvisionStatus.UNCHANGED;
            }
            Map<String, Path> provisionedPacks = new LinkedHashMap<>();
            for (PackPlan plan : plans) {
                provisionedPacks.put(plan.spec().key(), plan.root());
            }
            return new ProvisionResult(provisionedPacks, datapackRoot, status);
        } catch (IOException failure) {
            if (!committed) {
                IOException rollbackFailure = rollback(
                        packPublications,
                        datapackRoot,
                        datapackBackup,
                        datapackReplaced
                );
                if (rollbackFailure != null) {
                    failure.addSuppressed(rollbackFailure);
                }
            }
            throw failure;
        } catch (RuntimeException | LinkageError failure) {
            if (!committed) {
                IOException rollbackFailure = rollback(
                        packPublications,
                        datapackRoot,
                        datapackBackup,
                        datapackReplaced
                );
                if (rollbackFailure != null) {
                    failure.addSuppressed(rollbackFailure);
                }
            }
            throw new IOException("Iris bootstrap provisioning failed", failure);
        } finally {
            for (Path stagedPack : stagedPacks.values()) {
                deleteQuietly(stagedPack, feedback);
            }
            for (Path extractionRoot : extractionRoots) {
                deleteQuietly(extractionRoot, feedback);
            }
            deleteQuietly(compileContainer, feedback);
        }
    }

    private static Archive acquireArchive(
            Path cacheRoot,
            Properties marker,
            PackSpec spec,
            Consumer<String> feedback,
            ProvisionOptions options
    ) throws IOException {
        Path archivePath = cacheRoot.resolve("default-" + spec.key() + ".zip");
        Path metadataPath = cacheRoot.resolve("default-" + spec.key() + ".properties");
        Properties metadata = Files.isRegularFile(metadataPath) ? loadProperties(metadataPath) : new Properties();
        boolean validCache = false;
        if (Files.isRegularFile(archivePath)) {
            try {
                validCache = validateArchive(archivePath, spec);
            } catch (IOException exception) {
                feedback.accept("Cached Iris " + spec.key() + " release archive is invalid; downloading a replacement.");
            }
        }
        String cachedSource = metadata.getProperty("source", "");
        boolean cacheMatchesSource = validCache && (spec.source().toString().equals(cachedSource)
                || cachedSource.isBlank() && "overworld".equals(spec.key()));
        long fetchedAt = parseLong(metadata.getProperty("fetchedAt"), 0L);
        boolean fresh = cacheMatchesSource
                && options.clock().millis() - fetchedAt < options.refreshInterval().toMillis();
        if (fresh) {
            if (cachedSource.isBlank()) {
                metadata.setProperty("source", spec.source().toString());
                storePropertiesAtomic(metadataPath, metadata);
            }
            return new Archive(archivePath, sha256(archivePath));
        }

        IOException networkFailure = null;
        for (int attempt = 1; attempt <= options.attempts(); attempt++) {
            try {
                HttpRequest.Builder request = HttpRequest.newBuilder(spec.source())
                        .timeout(options.requestTimeout())
                        .header("Accept", "application/octet-stream")
                        .header("User-Agent", "Iris-Bootstrap")
                        .GET();
                if (cacheMatchesSource) {
                    String etag = metadata.getProperty("etag");
                    String lastModified = metadata.getProperty("lastModified");
                    if (etag != null && !etag.isBlank()) {
                        request.header("If-None-Match", etag);
                    }
                    if (lastModified != null && !lastModified.isBlank()) {
                        request.header("If-Modified-Since", lastModified);
                    }
                }
                HttpResponse<InputStream> response = options.client().send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
                int status = response.statusCode();
                if (status == 304 && cacheMatchesSource) {
                    close(response.body());
                    metadata.setProperty("source", spec.source().toString());
                    metadata.setProperty("fetchedAt", Long.toString(options.clock().millis()));
                    storePropertiesAtomic(metadataPath, metadata);
                    return new Archive(archivePath, sha256(archivePath));
                }
                if (status == 200) {
                    Path temporary = cacheRoot.resolve(".download-" + UUID.randomUUID() + ".zip");
                    try {
                        try (InputStream input = response.body(); OutputStream output = Files.newOutputStream(temporary)) {
                            copyLimited(input, output, options.maxArchiveBytes(), spec);
                        }
                        if (!validateArchive(temporary, spec)) {
                            throw new IOException("Downloaded Iris " + spec.key() + " release archive is invalid");
                        }
                        move(temporary, archivePath, true);
                    } finally {
                        Files.deleteIfExists(temporary);
                    }
                    Properties updated = new Properties();
                    response.headers().firstValue("etag").ifPresent(value -> updated.setProperty("etag", value));
                    response.headers().firstValue("last-modified").ifPresent(value -> updated.setProperty("lastModified", value));
                    updated.setProperty("source", spec.source().toString());
                    updated.setProperty("fetchedAt", Long.toString(options.clock().millis()));
                    updated.setProperty("sha256", sha256(archivePath));
                    storePropertiesAtomic(metadataPath, updated);
                    feedback.accept("Downloaded the Iris " + spec.key() + " release archive.");
                    return new Archive(archivePath, updated.getProperty("sha256"));
                }
                close(response.body());
                IOException statusFailure = new IOException("Iris " + spec.key() + " release download returned HTTP " + status);
                if (!retryableStatus(status)) {
                    networkFailure = statusFailure;
                    break;
                }
                networkFailure = statusFailure;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Iris " + spec.key() + " release download was interrupted", exception);
            } catch (IOException exception) {
                networkFailure = exception;
            }
            if (attempt < options.attempts()) {
                pause(options.retryDelay().multipliedBy(attempt));
            }
        }

        if (cacheMatchesSource) {
            feedback.accept("Iris " + spec.key() + " release download failed; using the validated cached archive.");
            return new Archive(archivePath, sha256(archivePath));
        }
        if (isManagedPackOutputUsable(marker, cacheRoot.getParent().getParent(), spec)) {
            String sourceSha = markerProperty(marker, spec, "sourceSha256");
            return new Archive(null, sourceSha);
        }
        throw networkFailure == null
                ? new IOException("Iris " + spec.key() + " release archive is unavailable and no valid cache exists")
                : new IOException("Iris " + spec.key() + " release archive is unavailable and no valid cache exists", networkFailure);
    }

    private static boolean isManagedPackOutputUsable(Properties marker, Path dataDirectory, PackSpec spec) {
        String sourceSha = markerProperty(marker, spec, "sourceSha256");
        String expectedFingerprint = markerProperty(marker, spec, "fingerprint");
        if (sourceSha == null || sourceSha.isBlank() || expectedFingerprint == null || expectedFingerprint.isBlank()) {
            return false;
        }
        Path packRoot = dataDirectory.toAbsolutePath().normalize().resolve("packs").resolve(spec.key());
        try {
            return isPackRoot(packRoot, spec) && directoryFingerprint(packRoot).equals(expectedFingerprint);
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }

    public static Path resolveLevelRoot(Path serverRoot) throws IOException {
        return BukkitStartupPaths.resolve(serverRoot).levelRoot();
    }

    private static boolean validateArchive(Path archive, PackSpec spec) throws IOException {
        int entries = 0;
        long expanded = 0L;
        boolean dimensionFound = false;
        Set<String> paths = new HashSet<>();
        try (InputStream input = Files.newInputStream(archive); ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries++;
                if (entries > MAX_ARCHIVE_ENTRIES) {
                    throw new IOException("Iris " + spec.key() + " release archive contains too many entries");
                }
                String name = normalizedZipEntry(entry.getName());
                if (!paths.add(name)) {
                    throw new IOException("Iris " + spec.key() + " release archive contains duplicate path " + name);
                }
                String requiredDimension = "dimensions/" + spec.requiredDimension() + ".json";
                if (name.equals(requiredDimension) || name.endsWith("/" + requiredDimension)) {
                    dimensionFound = true;
                }
                if (!entry.isDirectory()) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = zip.read(buffer)) >= 0) {
                        expanded += read;
                        if (expanded > MAX_EXPANDED_BYTES) {
                            throw new IOException("Iris " + spec.key() + " release archive expands beyond the safety limit");
                        }
                    }
                }
                zip.closeEntry();
            }
        }
        return entries > 0 && dimensionFound;
    }

    private static Path extractArchive(Path archive, Path extractionRoot, PackSpec spec) throws IOException {
        if (archive == null) {
            throw new IOException("Cached Iris " + spec.key() + " release archive is unavailable for required pack rebuild");
        }
        long expanded = 0L;
        int entries = 0;
        try (InputStream input = Files.newInputStream(archive); ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries++;
                if (entries > MAX_ARCHIVE_ENTRIES) {
                    throw new IOException("Iris " + spec.key() + " release archive contains too many entries");
                }
                String name = normalizedZipEntry(entry.getName());
                Path output = extractionRoot.resolve(name).normalize();
                if (!output.startsWith(extractionRoot)) {
                    throw new IOException("Unsafe path in Iris " + spec.key() + " release archive: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(output);
                } else {
                    Files.createDirectories(output.getParent());
                    try (OutputStream file = Files.newOutputStream(output)) {
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = zip.read(buffer)) >= 0) {
                            expanded += read;
                            if (expanded > MAX_EXPANDED_BYTES) {
                                throw new IOException("Iris " + spec.key() + " release archive expands beyond the safety limit");
                            }
                            file.write(buffer, 0, read);
                        }
                    }
                }
                zip.closeEntry();
            }
        }
        if (isPackRoot(extractionRoot, spec)) {
            return extractionRoot;
        }
        List<Path> candidates = new ArrayList<>();
        try (DirectoryStream<Path> children = Files.newDirectoryStream(extractionRoot)) {
            for (Path child : children) {
                if (Files.isDirectory(child) && isPackRoot(child, spec)) {
                    candidates.add(child);
                }
            }
        }
        if (candidates.size() != 1) {
            throw new IOException("Iris " + spec.key() + " release archive has an invalid root layout");
        }
        return candidates.getFirst();
    }

    private static String normalizedZipEntry(String raw) throws IOException {
        if (raw == null || raw.isBlank() || raw.indexOf('\0') >= 0 || raw.startsWith("/") || raw.startsWith("\\")) {
            throw new IOException("Invalid path in Iris bootstrap pack archive");
        }
        String normalized = raw.replace('\\', '/');
        Path path = Path.of(normalized).normalize();
        if (path.isAbsolute() || path.startsWith("..") || normalized.matches("^[A-Za-z]:.*")) {
            throw new IOException("Unsafe path in Iris bootstrap pack archive: " + raw);
        }
        return path.toString().replace('\\', '/');
    }

    private static boolean isPackRoot(Path path, PackSpec spec) {
        return path != null
                && Files.isRegularFile(path.resolve("dimensions").resolve(spec.requiredDimension() + ".json"));
    }

    private static void validatePackRoot(Path path, PackSpec spec) throws IOException {
        if (!isPackRoot(path, spec)) {
            throw new IOException("Iris " + spec.key() + " release pack is missing dimensions/"
                    + spec.requiredDimension() + ".json at " + path);
        }
    }

    private static boolean isDatapackRoot(Path path) {
        return isDatapackRoot(path, false);
    }

    private static boolean isDatapackRoot(Path path, boolean requiresGeneratedTypes) {
        return path != null
                && Files.isRegularFile(path.resolve("pack.mcmeta"))
                && (!requiresGeneratedTypes || Files.isDirectory(path.resolve("data/iris/dimension_type")));
    }

    static Path worldDatapackRoot(Path levelRoot) {
        return levelRoot.toAbsolutePath().normalize().resolve("datapacks").resolve(WORLD_DATAPACK_DIRECTORY);
    }

    private static String packRootsFingerprint(List<File> packRoots) throws IOException {
        MessageDigest digest = digest();
        List<Path> roots = packRoots.stream()
                .map(File::toPath)
                .map(path -> path.toAbsolutePath().normalize())
                .distinct()
                .sorted(Comparator.comparing(Path::toString))
                .toList();
        for (Path root : roots) {
            digest.update(root.toString().getBytes(StandardCharsets.UTF_8));
            digest.update(directoryFingerprint(root).getBytes(StandardCharsets.UTF_8));
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String directoryFingerprint(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return "";
        }
        Path scanRoot = root.toRealPath();
        MessageDigest digest = digest();
        try (Stream<Path> stream = Files.walk(scanRoot)) {
            List<Path> files = stream.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> scanRoot.relativize(path).toString()))
                    .toList();
            byte[] buffer = new byte[8192];
            for (Path file : files) {
                String relative = scanRoot.relativize(file).toString().replace('\\', '/');
                digest.update(relative.getBytes(StandardCharsets.UTF_8));
                try (InputStream input = Files.newInputStream(file)) {
                    int read;
                    while ((read = input.read(buffer)) >= 0) {
                        digest.update(buffer, 0, read);
                    }
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String sha256(Path file) throws IOException {
        MessageDigest digest = digest();
        byte[] buffer = new byte[8192];
        try (InputStream input = Files.newInputStream(file)) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static Path replaceWithBackup(Path staged, Path target) throws IOException {
        Path backup = target.resolveSibling("." + target.getFileName() + "-backup-" + UUID.randomUUID());
        if (Files.exists(target)) {
            move(target, backup, false);
        } else {
            backup = null;
        }
        try {
            move(staged, target, false);
            return backup;
        } catch (IOException failure) {
            if (backup != null && Files.exists(backup)) {
                move(backup, target, false);
            }
            throw failure;
        }
    }

    private static IOException rollback(
            List<PackPublication> packPublications,
            Path datapackRoot,
            Path datapackBackup,
            boolean datapackReplaced
    ) {
        IOException failure = null;
        try {
            restore(datapackRoot, datapackBackup, datapackReplaced);
        } catch (IOException exception) {
            failure = exception;
        }
        for (int index = packPublications.size() - 1; index >= 0; index--) {
            PackPublication publication = packPublications.get(index);
            try {
                restore(publication.root(), publication.backup(), true);
            } catch (IOException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        return failure;
    }

    private static void restore(Path target, Path backup, boolean replaced) throws IOException {
        if (!replaced) {
            return;
        }
        delete(target);
        if (backup != null && Files.exists(backup)) {
            move(backup, target, false);
        }
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        try (Stream<Path> stream = Files.walk(source)) {
            for (Path entry : stream.toList()) {
                Path relative = source.relativize(entry);
                Path output = target.resolve(relative);
                if (Files.isDirectory(entry)) {
                    Files.createDirectories(output);
                } else if (Files.isRegularFile(entry)) {
                    Files.createDirectories(output.getParent());
                    Files.copy(entry, output, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    private static void move(Path source, Path target, boolean replace) throws IOException {
        if (source == null) {
            return;
        }
        Files.createDirectories(target.getParent());
        try {
            if (replace) {
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
            }
        } catch (AtomicMoveNotSupportedException exception) {
            if (replace) {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(source, target);
            }
        }
    }

    private static void delete(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(path)) {
            List<Path> entries = stream.sorted(Comparator.reverseOrder()).toList();
            for (Path entry : entries) {
                Files.deleteIfExists(entry);
            }
        }
    }

    private static String levelId(Path levelRoot) throws IOException {
        Path fileName = Objects.requireNonNull(levelRoot, "levelRoot").getFileName();
        if (fileName == null || fileName.toString().isBlank()) {
            throw new IOException("Configured level root has no Paper startup level id: " + levelRoot);
        }
        return fileName.toString();
    }

    private static void deleteQuietly(Path path, Consumer<String> feedback) {
        try {
            delete(path);
        } catch (IOException exception) {
            feedback.accept("Unable to clean temporary Iris bootstrap path " + path + ": " + exception.getMessage());
        }
    }

    private static void copyLimited(
            InputStream input,
            OutputStream output,
            long limit,
            PackSpec spec
    ) throws IOException {
        byte[] buffer = new byte[8192];
        long total = 0L;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            total += read;
            if (total > limit) {
                throw new IOException("Iris " + spec.key() + " release archive exceeds the download size limit");
            }
            output.write(buffer, 0, read);
        }
    }

    private static Properties loadProperties(Path path) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        }
        return properties;
    }

    private static void storePropertiesAtomic(Path path, Properties properties) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), path.getFileName().toString(), ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) {
                properties.store(output, null);
            }
            move(temporary, path, true);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static boolean retryableStatus(int status) {
        return status == 408 || status == 425 || status == 429 || status >= 500;
    }

    private static void pause(Duration duration) throws IOException {
        if (duration.isZero() || duration.isNegative()) {
            return;
        }
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Iris bootstrap pack retry wait was interrupted", exception);
        }
    }

    private static long parseLong(String value, long fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static String markerKey(PackSpec spec, String property) {
        return "pack." + spec.key() + "." + property;
    }

    private static String markerProperty(Properties marker, PackSpec spec, String property) {
        String current = marker.getProperty(markerKey(spec, property));
        if (current != null || !"overworld".equals(spec.key())) {
            return current;
        }
        return switch (property) {
            case "source" -> marker.getProperty("source");
            case "sourceSha256" -> marker.getProperty("sourceSha256");
            case "managed" -> marker.getProperty("managedDefault");
            case "fingerprint" -> marker.getProperty("defaultPackFingerprint");
            default -> null;
        };
    }

    private static void close(InputStream input) {
        try {
            input.close();
        } catch (IOException ignored) {
        }
    }

    public enum ProvisionStatus {
        INSTALLED,
        UPDATED,
        UNCHANGED
    }

    public record ProvisionResult(Map<String, Path> packRoots, Path datapackRoot, ProvisionStatus status) {
        public ProvisionResult {
            packRoots = Map.copyOf(Objects.requireNonNull(packRoots, "packRoots"));
            Objects.requireNonNull(datapackRoot, "datapackRoot");
            Objects.requireNonNull(status, "status");
        }
    }

    record ProvisionOptions(
            List<PackSpec> packs,
            List<IrisGeneratorBinding> bindings,
            HttpClient client,
            Clock clock,
            Duration refreshInterval,
            Duration requestTimeout,
            int attempts,
            Duration retryDelay,
            long maxArchiveBytes,
            Path levelRoot
    ) {
        ProvisionOptions {
            packs = List.copyOf(Objects.requireNonNull(packs, "packs"));
            bindings = List.copyOf(Objects.requireNonNull(bindings, "bindings"));
            Objects.requireNonNull(client, "client");
            Objects.requireNonNull(clock, "clock");
            Objects.requireNonNull(refreshInterval, "refreshInterval");
            Objects.requireNonNull(requestTimeout, "requestTimeout");
            Objects.requireNonNull(retryDelay, "retryDelay");
            Objects.requireNonNull(levelRoot, "levelRoot");
            if (attempts < 1 || maxArchiveBytes < 1L) {
                throw new IllegalArgumentException("Invalid bootstrap provisioning options");
            }
            Set<String> keys = new HashSet<>();
            for (PackSpec pack : packs) {
                if (!keys.add(pack.key())) {
                    throw new IllegalArgumentException("Duplicate bootstrap pack key '" + pack.key() + "'");
                }
            }
        }
    }

    record PackSpec(String key, URI source, String requiredDimension) {
        PackSpec {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(requiredDimension, "requiredDimension");
            if (!key.matches("[a-z0-9_-]+") || !requiredDimension.matches("[a-z0-9_/-]+")) {
                throw new IllegalArgumentException("Invalid bootstrap pack specification for '" + key + "'");
            }
        }
    }

    private record PackPlan(
            PackSpec spec,
            Path root,
            boolean existed,
            boolean managed,
            Archive archive,
            boolean replace
    ) {
    }

    private record PackPublication(Path root, Path backup) {
    }

    private record Archive(Path path, String sha256) {
        private Archive {
            Objects.requireNonNull(sha256, "sha256");
        }
    }
}
