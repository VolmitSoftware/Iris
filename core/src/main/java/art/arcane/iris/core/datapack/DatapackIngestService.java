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

package art.arcane.iris.core.datapack;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.core.IrisStartupValidation;
import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.core.ServerConfigurator;
import art.arcane.iris.core.datapack.ModrinthResolver.ResolvedDatapack;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.nms.MinecraftVersion;
import art.arcane.iris.core.project.IrisProject;
import art.arcane.iris.core.project.IrisCodeWorkspace;
import art.arcane.iris.core.structure.BulkStructureImporter;
import art.arcane.iris.core.structure.StructureImporter;
import art.arcane.iris.core.structure.authoring.StructureKey;
import art.arcane.iris.core.structure.authoring.StructureSource;
import art.arcane.iris.core.structure.authoring.StructureTransactionWriter;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.engine.object.IrisImportedStructureControl;
import art.arcane.iris.util.common.format.C;
import art.arcane.iris.util.common.io.Durability;
import art.arcane.iris.util.common.plugin.VolmitSender;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.io.IO;
import art.arcane.volmlib.util.io.ZipUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.Server;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.nio.file.LinkOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

public final class DatapackIngestService {
    private static final String USER_AGENT = "VolmitSoftware/Iris (datapack-ingest)";
    private static final String FINDER_METADATA = ".DS_Store";
    private static final String OVERRIDES_STRIPPED_MARKER = ".iris-overrides-stripped";
    private static final String OWNERSHIP_MARKER = ".iris-managed.json";
    private static final String TRANSACTION_DIRECTORY = ".iris-datapack-transactions";
    private static final String TRANSACTION_JOURNAL = "journal.json";
    private static final String TRANSACTION_JOURNAL_NEXT = "journal.next.json";
    private static final String STARTUP_VALIDATION_CACHE = "startup-validation.json";
    private static final String LOCAL_IMPORT_DIRECTORY = "imports";
    private static final int OWNERSHIP_SCHEMA = 1;
    private static final int TRANSACTION_SCHEMA = 2;
    private static final int STARTUP_VALIDATION_SCHEMA = 1;
    private static final int STRUCTURE_IMPORT_FORMAT_REVISION = 3;
    private static final int MAX_REDIRECTS = 5;
    private static final int MAX_ARCHIVE_ENTRIES = 100_000;
    private static final int MAX_CACHE_FILES = 32;
    private static final int MAX_MANAGED_PATHS = MAX_ARCHIVE_ENTRIES + 16;
    private static final long MAX_DOWNLOAD_BYTES = 256L * 1024L * 1024L;
    private static final long MAX_EXPANDED_BYTES = 1024L * 1024L * 1024L;
    private static final long MAX_ENTRY_BYTES = 256L * 1024L * 1024L;
    private static final long MAX_CACHE_BYTES = 1024L * 1024L * 1024L;
    private static final long MAX_MANIFEST_BYTES = 16L * 1024L * 1024L;
    private static final long MAX_TRANSACTION_JOURNAL_BYTES = 4L * 1024L * 1024L;
    private static final long MAX_METADATA_BYTES = 1024L * 1024L;
    private static final long MAX_OWNERSHIP_BYTES = 1024L * 1024L;
    private static final int MAX_TRANSACTION_COUNT = 1_024;
    private static final int MAX_SCRATCH_DELETE_ATTEMPTS = 3;
    private static final int WINDOWS_LEGACY_PATH_LIMIT = 247;
    private static final int HASH_BUFFER_BYTES = 64 * 1024;
    private static final Set<String> RESERVED_IDS = Set.of("iris");
    private static final ReentrantLock TRANSACTION_LOCK = new ReentrantLock();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile StartupValidationCache activeStartupValidation;

    private DatapackIngestService() {
    }

    public static Report ingestAll(VolmitSender sender, boolean restart) {
        return ingest(sender, collectConfiguredImports(), restart);
    }

    public static void autoIngestOnStartup() {
        StartupValidationOutcome outcome = validateOnStartup();
        if (outcome == StartupValidationOutcome.READY) {
            runPostStartupTasks();
        }
    }

    public static StartupValidationOutcome validateOnStartup() {
        activeStartupValidation = null;
        IrisStartupValidation.beginDatapackValidation();
        KList<String> configured = collectConfiguredImports();
        List<String> urls = configured.stream().sorted().toList();
        boolean autoIngest = IrisSettings.get().getGeneral().autoIngestDatapacks;
        boolean stripOverrides = resolveStripOverrides();
        String mcVersion = serverMcVersion();
        int irisVersion = IrisPlatforms.get().irisVersionNumber();
        File root = IrisPlatforms.get().dataFolder("datapacks");
        KList<File> worldFolders = ServerConfigurator.getDatapacksFolder();
        Path cacheFile = new File(root, STARTUP_VALIDATION_CACHE).toPath();

        try {
            StartupValidationCache cached = readStartupValidationCache(cacheFile);
            if (startupValidationContextMatches(
                    cached, mcVersion, irisVersion, autoIngest, stripOverrides, urls)) {
                String localFingerprint = startupValidationFingerprint(root, worldFolders, urls);
                if (startupValidationCacheMatches(
                        cached,
                        mcVersion,
                        irisVersion,
                        autoIngest,
                        stripOverrides,
                        urls,
                        localFingerprint)) {
                    activeStartupValidation = cached;
                    IrisLogging.info("External datapacks match the persisted startup validation; remote resolution and full revalidation were skipped.");
                    IrisStartupValidation.markDatapacksReady();
                    return StartupValidationOutcome.READY;
                }
            }
        } catch (IOException | RuntimeException exception) {
            IrisLogging.warn("Persisted external datapack validation could not be reused: "
                    + failureMessage(exception));
        }

        if (autoIngest && !configured.isEmpty()) {
            IrisLogging.info("Validating " + configured.size()
                    + " configured external datapack import(s) before player admission...");
            Report report = ingest(null, configured, true, true);
            if (!report.getFailed().isEmpty()) {
                String failure = report.getFailed().getFirst();
                IrisStartupValidation.markDatapacksInvalid(failure);
                return StartupValidationOutcome.FAILED;
            }
            StartupValidationOutcome outcome = report.changed()
                    ? StartupValidationOutcome.RESTART_REQUIRED
                    : StartupValidationOutcome.READY;
            activeStartupValidation = cacheStartupValidation(root, worldFolders, cacheFile, mcVersion, irisVersion,
                    autoIngest, stripOverrides, urls);
            if (outcome == StartupValidationOutcome.RESTART_REQUIRED) {
                IrisStartupValidation.requireRestart(
                        "Iris installed updated external datapacks; restart must complete before player admission or world creation.");
            } else {
                IrisStartupValidation.markDatapacksReady();
            }
            return outcome;
        }

        ReapplyOutcome reapply = reapplyFromStaging(worldFolders);
        if (!reapply.succeeded()) {
            String failure = reapply.failure()
                    .map(DatapackIngestService::failureMessage)
                    .orElse("External datapack recovery failed.");
            IrisStartupValidation.markDatapacksInvalid(failure);
            return StartupValidationOutcome.FAILED;
        }
        activeStartupValidation = cacheStartupValidation(root, worldFolders, cacheFile, mcVersion, irisVersion,
                autoIngest, stripOverrides, urls);
        if (reapply.changed()) {
            IrisStartupValidation.requireRestart(
                    "Iris repaired external datapack files; restart must complete before player admission or world creation.");
            return StartupValidationOutcome.RESTART_REQUIRED;
        }
        IrisStartupValidation.markDatapacksReady();
        return StartupValidationOutcome.READY;
    }

    public static void runPostStartupTasks() {
        refreshWorkspaces();
        boolean maintenanceChanged = autoImportDatapackStructures();
        refreshStartupValidationAfterMaintenance(maintenanceChanged);
    }

    private static StartupValidationCache cacheStartupValidation(
            File root,
            KList<File> worldFolders,
            Path cacheFile,
            String mcVersion,
            int irisVersion,
            boolean autoIngest,
            boolean stripOverrides,
            List<String> urls
    ) {
        try {
            StartupValidationCache cache = createStartupValidationCache(
                    mcVersion,
                    irisVersion,
                    autoIngest,
                    stripOverrides,
                    urls,
                    startupValidationFingerprint(root, worldFolders, urls));
            writeStartupValidationCache(cacheFile, cache);
            return cache;
        } catch (IOException | RuntimeException exception) {
            IrisLogging.warn("Could not persist external datapack startup validation: "
                    + failureMessage(exception));
            return null;
        }
    }

    private static void refreshStartupValidationAfterMaintenance(boolean maintenanceChanged) {
        if (!maintenanceChanged) {
            return;
        }
        StartupValidationCache validated = activeStartupValidation;
        if (validated == null || !IrisStartupValidation.isReady()) {
            return;
        }
        KList<String> configured = collectConfiguredImports();
        List<String> urls = configured.stream().sorted().toList();
        boolean autoIngest = IrisSettings.get().getGeneral().autoIngestDatapacks;
        boolean stripOverrides = resolveStripOverrides();
        String mcVersion = serverMcVersion();
        int irisVersion = IrisPlatforms.get().irisVersionNumber();
        if (!startupValidationContextMatches(
                validated, mcVersion, irisVersion, autoIngest, stripOverrides, urls)) {
            return;
        }
        File root = IrisPlatforms.get().dataFolder("datapacks");
        KList<File> worldFolders = ServerConfigurator.getDatapacksFolder();
        Path cacheFile = new File(root, STARTUP_VALIDATION_CACHE).toPath();
        TRANSACTION_LOCK.lock();
        try {
            recoverTransactions(root, worldFolders);
            StartupValidationCache refreshed = refreshStartupValidationCache(
                    validated, root, worldFolders);
            writeStartupValidationCache(cacheFile, refreshed);
            activeStartupValidation = refreshed;
        } catch (IOException | RuntimeException exception) {
            IrisLogging.warn("Could not refresh external datapack validation after startup maintenance: "
                    + failureMessage(exception));
        } finally {
            TRANSACTION_LOCK.unlock();
        }
    }

    static StartupValidationCache refreshStartupValidationCache(
            StartupValidationCache validated,
            File root,
            KList<File> worldFolders
    ) throws IOException {
        Objects.requireNonNull(validated, "Validated external datapack startup state");
        return createStartupValidationCache(
                validated.minecraftVersion,
                validated.irisVersion,
                validated.autoIngest,
                validated.stripOverrides,
                validated.urls,
                startupValidationFingerprint(root, worldFolders, validated.urls));
    }

    private static StartupValidationCache createStartupValidationCache(
            String mcVersion,
            int irisVersion,
            boolean autoIngest,
            boolean stripOverrides,
            List<String> urls,
            String localFingerprint
    ) {
        StartupValidationCache cache = new StartupValidationCache();
        cache.schemaVersion = STARTUP_VALIDATION_SCHEMA;
        cache.minecraftVersion = Objects.requireNonNullElse(mcVersion, "");
        cache.irisVersion = irisVersion;
        cache.autoIngest = autoIngest;
        cache.stripOverrides = stripOverrides;
        cache.urls = List.copyOf(urls);
        cache.localFingerprint = localFingerprint;
        return cache;
    }

    static boolean startupValidationContextMatches(
            StartupValidationCache cache,
            String mcVersion,
            int irisVersion,
            boolean autoIngest,
            boolean stripOverrides,
            List<String> urls
    ) {
        return cache != null
                && cache.schemaVersion == STARTUP_VALIDATION_SCHEMA
                && Objects.equals(cache.minecraftVersion, Objects.requireNonNullElse(mcVersion, ""))
                && cache.irisVersion == irisVersion
                && cache.autoIngest == autoIngest
                && cache.stripOverrides == stripOverrides
                && Objects.equals(cache.urls, urls);
    }

    static boolean startupValidationCacheMatches(
            StartupValidationCache cache,
            String mcVersion,
            int irisVersion,
            boolean autoIngest,
            boolean stripOverrides,
            List<String> urls,
            String localFingerprint
    ) {
        return startupValidationContextMatches(
                cache, mcVersion, irisVersion, autoIngest, stripOverrides, urls)
                && localFingerprint != null && !localFingerprint.isBlank()
                && Objects.equals(cache.localFingerprint, localFingerprint);
    }

    static StartupValidationCache readStartupValidationCache(Path cacheFile) {
        if (cacheFile == null
                || Files.isSymbolicLink(cacheFile)
                || !Files.isRegularFile(cacheFile, LinkOption.NOFOLLOW_LINKS)) {
            return null;
        }
        try {
            if (Files.size(cacheFile) > MAX_METADATA_BYTES) {
                return null;
            }
            StartupValidationCache cache = GSON.fromJson(
                    readBoundedUtf8(cacheFile, MAX_METADATA_BYTES, "External datapack startup validation"),
                    StartupValidationCache.class);
            if (cache == null || cache.urls == null) {
                return null;
            }
            List<String> sortedUrls = cache.urls.stream()
                    .filter(Objects::nonNull)
                    .sorted()
                    .toList();
            if (sortedUrls.size() != cache.urls.size()
                    || new HashSet<>(sortedUrls).size() != sortedUrls.size()
                    || !sortedUrls.equals(cache.urls)) {
                return null;
            }
            cache.urls = sortedUrls;
            return cache;
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    private static void writeStartupValidationCache(Path cacheFile, StartupValidationCache cache) throws IOException {
        Path absolute = cacheFile.toAbsolutePath().normalize();
        Path parent = Objects.requireNonNull(absolute.getParent(), "External datapack startup validation parent");
        Files.createDirectories(parent);
        Path staged = Files.createTempFile(parent, ".startup-validation-", ".tmp");
        try {
            byte[] content = GSON.toJson(cache).getBytes(StandardCharsets.UTF_8);
            if (content.length > MAX_METADATA_BYTES) {
                throw new IOException("External datapack startup validation exceeds " + MAX_METADATA_BYTES + " bytes");
            }
            Files.write(staged, content, StandardOpenOption.TRUNCATE_EXISTING);
            forceFile(staged);
            try {
                Files.move(staged, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(staged, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
            forceDirectoryIfSupported(parent);
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    static String startupValidationFingerprint(File root, KList<File> worldFolders) throws IOException {
        return startupValidationFingerprint(root, worldFolders, List.of());
    }

    static String startupValidationFingerprint(
            File root,
            KList<File> worldFolders,
            Iterable<String> sources
    ) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            Path manifestPath = new File(root, "manifest.json").toPath();
            updateFingerprintValue(digest, "manifest");
            if (Files.exists(manifestPath, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isSymbolicLink(manifestPath)
                        || !Files.isRegularFile(manifestPath, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Invalid datapack manifest path " + manifestPath);
                }
                byte[] manifest = readBoundedBytes(
                        manifestPath, MAX_MANIFEST_BYTES, "Datapack manifest fingerprint");
                updateDigestLong(digest, manifest.length);
                digest.update(manifest);
            } else if (Files.notExists(manifestPath, LinkOption.NOFOLLOW_LINKS)) {
                updateDigestLong(digest, -1L);
            } else {
                throw new IOException("Cannot determine datapack manifest state at " + manifestPath);
            }

            Manifest manifest = readCommittedManifest(root);
            updateDirectoryFingerprint(digest, "staging", new File(root, "staging"));
            updateDirectoryFingerprint(digest, "transactions", new File(root, TRANSACTION_DIRECTORY));
            updateDirectoryFingerprint(
                    digest,
                    "storage-install-scratch",
                    installScratchRoot(new File(root, "staging")));

            List<Entry> entries = new ArrayList<>(manifest.entries);
            entries.sort(Comparator.comparing(entry -> entry.id));
            List<File> targets = new ArrayList<>(worldFolders == null ? List.of() : worldFolders);
            targets.sort(Comparator.comparing(file -> file.toPath().toAbsolutePath().normalize().toString()));
            for (File worldFolder : targets) {
                String worldIdentity = worldFolder.toPath().toAbsolutePath().normalize().toString();
                updateFingerprintValue(digest, "world:" + worldIdentity);
                updateDirectoryFingerprint(
                        digest,
                        "world-install-scratch:" + worldIdentity,
                        installScratchRoot(worldFolder));
                for (Entry entry : entries) {
                    updateDirectoryFingerprint(
                            digest,
                            "world-pack:" + worldIdentity + ":" + entry.id,
                            new File(worldFolder, entry.id));
                }
            }
            updateLocalSourceFingerprint(digest, sources);
            return hex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("SHA-256 algorithm unavailable", exception);
        }
    }

    private static void updateLocalSourceFingerprint(
            MessageDigest digest,
            Iterable<String> sources
    ) throws IOException {
        if (sources == null) {
            return;
        }
        List<String> localSources = new ArrayList<>();
        for (String source : sources) {
            URI uri = parseSourceUri(source);
            if (uri != null && "file".equalsIgnoreCase(uri.getScheme())) {
                localSources.add(uri.normalize().toASCIIString());
            }
        }
        localSources.sort(String::compareTo);
        byte[] buffer = new byte[HASH_BUFFER_BYTES];
        for (String source : localSources) {
            Path path = requireLocalDatapackPath(parseSourceUri(source));
            updateFingerprintValue(digest, "local-source:" + source);
            try (InputStream input = Files.newInputStream(
                    path,
                    StandardOpenOption.READ,
                    LinkOption.NOFOLLOW_LINKS)) {
                long bytes = 0;
                int length;
                while ((length = input.read(buffer)) > 0) {
                    bytes += length;
                    if (bytes > MAX_DOWNLOAD_BYTES) {
                        throw new IOException("Local datapack exceeds " + MAX_DOWNLOAD_BYTES + " bytes: " + path);
                    }
                    digest.update(buffer, 0, length);
                }
                updateDigestLong(digest, bytes);
            }
        }
    }

    private static URI parseSourceUri(String source) throws IOException {
        if (source == null || source.isBlank()) {
            return null;
        }
        try {
            return new URI(source.trim());
        } catch (URISyntaxException exception) {
            throw new IOException("Invalid datapack URL " + source, exception);
        }
    }

    private static void updateDirectoryFingerprint(
            MessageDigest digest,
            String identity,
            File directory
    ) throws IOException {
        updateFingerprintValue(digest, identity);
        Path path = directory.toPath();
        if (Files.notExists(path, LinkOption.NOFOLLOW_LINKS)) {
            updateFingerprintValue(digest, "missing");
            return;
        }
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(path)
                || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Invalid external datapack validation directory " + path);
        }
        updateFingerprintValue(digest, directoryHash(directory));
        Path ownership = new File(directory, OWNERSHIP_MARKER).toPath();
        if (Files.isRegularFile(ownership, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(ownership)) {
            byte[] marker = readBoundedBytes(
                    ownership, MAX_OWNERSHIP_BYTES, "External datapack ownership fingerprint");
            updateDigestLong(digest, marker.length);
            digest.update(marker);
        } else {
            updateDigestLong(digest, -1L);
        }
    }

    private static void updateFingerprintValue(MessageDigest digest, String value) {
        byte[] bytes = Objects.requireNonNullElse(value, "").getBytes(StandardCharsets.UTF_8);
        updateDigestInt(digest, bytes.length);
        digest.update(bytes);
    }

    private static String failureMessage(Throwable exception) {
        if (exception == null) {
            return "unknown failure";
        }
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    public static void refreshWorkspaces() {
        try (Stream<IrisData> stream = ServerConfigurator.allPacks()) {
            stream.forEach(DatapackIngestService::refreshWorkspace);
        }
    }

    public static void refreshWorkspace(IrisData data) {
        if (data == null || !hasImports(data)) {
            return;
        }
        try {
            new IrisCodeWorkspace(new IrisProject(data.getDataFolder())).updateWorkspace();
        } catch (Throwable e) {
            IrisLogging.reportError(e);
        }
    }

    public static Report ingest(VolmitSender sender, KList<String> urls, boolean restart) {
        return ingest(sender, urls, restart, false);
    }

    /**
     * Only startup validation owns the global player-admission gate (gateAdmission=true). The
     * runtime admin command must never flip it: a transient download failure there would lock
     * every login until restart, and the PENDING window would close logins for the whole
     * (potentially very slow) download. World-creation safety on the ungated path is carried
     * by the loaded-runtime invalidation plus requireDatapackRestart when files change.
     */
    private static Report ingest(VolmitSender sender, KList<String> urls, boolean restart, boolean gateAdmission) {
        if (gateAdmission) {
            IrisStartupValidation.beginDatapackValidation();
        }
        ServerConfigurator.LoadedDatapackRuntimeInvalidation invalidation =
                ServerConfigurator.invalidateLoadedDatapackRuntime();
        Report report;
        boolean settled = false;
        TRANSACTION_LOCK.lock();
        try {
            report = ingestLocked(sender, urls, restart);
            settled = true;
        } finally {
            TRANSACTION_LOCK.unlock();
            if (!settled && gateAdmission) {
                // Never strand the admission gate at PENDING on an unexpected throw.
                IrisStartupValidation.markDatapacksInvalid(
                        "External datapack ingest failed unexpectedly; check the log above.");
            }
        }
        if (!report.changed() && report.getFailed().isEmpty()) {
            ServerConfigurator.restoreLoadedDatapackRuntimeIfUnchanged(invalidation);
        } else if (report.changed()) {
            if (restart) {
                ServerConfigurator.restart();
            } else {
                ServerConfigurator.requireDatapackRestart();
            }
        }
        if (gateAdmission) {
            if (!report.getFailed().isEmpty()) {
                IrisStartupValidation.markDatapacksInvalid(report.getFailed().getFirst());
            } else if (!report.changed()) {
                IrisStartupValidation.markDatapacksReady();
            }
        }
        return report;
    }

    private static Report ingestLocked(VolmitSender sender, KList<String> urls, boolean restart) {
        Report report = new Report();
        if (urls == null || urls.isEmpty()) {
            message(sender, C.YELLOW + "No external datapacks found. Add an HTTP(S) or file URL to a dimension's 'datapackImports', or place a ZIP in Iris/datapacks/imports, then run /iris datapack ingest.");
            return report;
        }

        File root = IrisPlatforms.get().dataFolder("datapacks");
        File cacheDir = new File(root, "cache");
        File stagingDir = new File(root, "staging");
        try {
            ensureScratchDirectory(cacheDir, "datapack download cache");
            ensureScratchDirectory(stagingDir, "datapack staging");
        } catch (IOException e) {
            report.failed.add("local storage - " + e.getMessage());
            message(sender, C.RED + "Datapack ingest failed: " + e.getMessage());
            IrisLogging.reportError(e);
            return report;
        }

        KList<File> worldFolders = ServerConfigurator.getDatapacksFolder();
        String mcVersion = serverMcVersion();
        try {
            recoverTransactions(root, worldFolders);
        } catch (IOException e) {
            report.failed.add("transaction recovery - " + e.getMessage());
            message(sender, C.RED + "Datapack ingest blocked by incomplete transaction recovery: " + e.getMessage());
            IrisLogging.reportError(e);
            return report;
        }
        Manifest manifest = readManifest(root);
        boolean stripOverrides = resolveStripOverrides();
        List<InstallExecution> installs = new ArrayList<>();

        message(sender, C.GRAY + "Ingesting " + C.WHITE + urls.size() + C.GRAY + " datapack import(s)" + (mcVersion == null ? "" : " for MC " + mcVersion) + (stripOverrides ? C.GRAY + " (datapackOverrides=false: minecraft-namespaced structure overrides will be stripped)" : "") + "...");

        for (String url : urls) {
            try {
                ingestSingle(
                        sender,
                        url,
                        mcVersion,
                        cacheDir,
                        stagingDir,
                        worldFolders,
                        manifest,
                        report,
                        stripOverrides,
                        installs
                );
            } catch (Exception e) {
                report.failed.add(url + " - " + e.getMessage());
                message(sender, C.RED + "  Failed: " + C.WHITE + url + C.RED + " - " + e.getMessage());
                IrisLogging.reportError(e);
            }
        }

        diagnoseConflicts(sender, manifest);
        ManifestWrite manifestWrite = null;
        boolean manifestDurabilityConfirmed = false;
        try {
            for (InstallExecution install : installs) {
                verifyInstallExecution(install);
            }
            manifestWrite = prepareManifestWrite(root, manifest);
            manifestWrite.publish();
            manifestDurabilityConfirmed = true;
        } catch (IOException manifestFailure) {
            if (manifestWrite == null || !manifestWrite.published()) {
                rollbackInstallExecutions(installs, manifestFailure);
                report.failed.add("manifest - " + manifestFailure.getMessage());
                report.updated.clear();
                report.upToDate.clear();
                report.requiresRestart = false;
                message(sender, C.RED + "Datapack ingest rolled back before the manifest commit: "
                        + manifestFailure.getMessage());
                IrisLogging.reportError(manifestFailure);
                return report;
            }
            IrisLogging.reportError("Datapack manifest was published but durability confirmation failed; "
                    + "transaction backups are retained for restart recovery.", manifestFailure);
        } finally {
            if (manifestWrite != null) {
                try {
                    manifestWrite.discard();
                } catch (IOException cleanupFailure) {
                    IrisLogging.reportError("Datapack manifest staging cleanup failed.", cleanupFailure);
                }
            }
        }
        if (manifestDurabilityConfirmed) {
            for (InstallExecution install : installs) {
                try {
                    finishInstallExecution(install);
                } catch (IOException cleanupFailure) {
                    IrisLogging.reportError("Datapack install committed but transaction cleanup requires restart recovery.",
                            cleanupFailure);
                }
            }
        }
        pruneCache(cacheDir);
        message(sender, C.GREEN + "Datapack ingest complete: " + C.WHITE + report.updated.size() + C.GREEN + " updated, " + C.WHITE + report.upToDate.size() + C.GREEN + " up to date, " + C.WHITE + report.failed.size() + C.GREEN + " failed.");

        if (report.changed()) {
            message(sender, C.YELLOW + "New datapack structures were installed. A server restart is required for them to register and generate.");
            message(sender, C.GRAY + "After the restart they generate natively in Iris dimensions that declare their source URL; ZIPs from Iris/datapacks/imports are enabled for every Iris dimension. To get editable Iris copies (jigsaw pools, pieces & objects written into the pack) run /iris structure import <dimension>, or set general.autoImportDatapackStructures=true to do it on every ingest. Place any registered key directly with a 'structures' placement using nativeStructures.");
            message(sender, C.GRAY + "Datapacks replace matching vanilla structure keys by default. Set 'importedStructures.datapackOverrides' to false to keep minecraft-namespaced structure definitions untouched; deny non-minecraft datapack and mod structure families with importedStructures.disabled or complete keys with importedStructures.disabledExact.");
            if (!restart) {
                message(sender, C.GRAY + "Run with restart=true to restart now, or restart manually. After restart, run /iris structure list <dimension> to see the new keys.");
            }
        }

        return report;
    }

    public static ReapplyOutcome reapplyFromStaging(KList<File> worldFolders) {
        ServerConfigurator.LoadedDatapackRuntimeInvalidation invalidation =
                ServerConfigurator.invalidateLoadedDatapackRuntime();
        ReapplyOutcome outcome;
        TRANSACTION_LOCK.lock();
        try {
            outcome = reapplyFromStagingLocked(worldFolders);
        } finally {
            TRANSACTION_LOCK.unlock();
        }
        if (outcome.succeeded() && !outcome.changed()) {
            ServerConfigurator.restoreLoadedDatapackRuntimeIfUnchanged(invalidation);
        } else if (outcome.changed()) {
            ServerConfigurator.requireDatapackRestart();
        } else {
            IrisStartupValidation.markDatapacksInvalid(outcome.failure()
                    .map(DatapackIngestService::failureMessage)
                    .orElse("External datapack recovery failed."));
        }
        return outcome;
    }

    private static ReapplyOutcome reapplyFromStagingLocked(KList<File> worldFolders) {
        File root = IrisPlatforms.get().dataFolder("datapacks");
        ReapplyOutcome recovery = recoverBeforeReapplyOutcome(root, worldFolders);
        if (!recovery.succeeded()) {
            return reportReapplyFailure(recovery);
        }
        File stagingDir = IrisPlatforms.get().dataFolderNoCreate("datapacks", "staging");
        ReapplyOutcome repair = reapplyStagingRootOutcome(
                root,
                stagingDir,
                worldFolders,
                resolveStripOverrides());
        if (!repair.succeeded()) {
            return reportReapplyFailure(repair);
        }
        return ReapplyOutcome.success(recovery.recovered(), repair.repaired());
    }

    static boolean reapplyStagingRoot(
            File root,
            File stagingDir,
            KList<File> worldFolders,
            boolean stripOverrides
    ) {
        return reportReapplyFailure(reapplyStagingRootOutcome(
                root,
                stagingDir,
                worldFolders,
                stripOverrides)).succeeded();
    }

    static ReapplyOutcome reapplyStagingRootOutcome(
            File root,
            File stagingDir,
            KList<File> worldFolders,
            boolean stripOverrides
    ) {
        Manifest manifest = readManifest(root);
        if (stagingDir == null
                || !Files.exists(stagingDir.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            if (manifest.entries.isEmpty()) {
                return ReapplyOutcome.success(false, false);
            }
            File missing = stagingDir == null ? new File(root, "staging") : stagingDir;
            return ReapplyOutcome.failed(new IOException(
                    "Managed datapack staging is missing at " + missing.getPath()));
        }
        if (Files.isSymbolicLink(stagingDir.toPath())
                || !Files.isDirectory(stagingDir.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            return ReapplyOutcome.failed(new IOException(
                    "Managed datapack staging is not a safe directory at " + stagingDir.getPath()));
        }
        return reapplyStagedDirectoriesOutcome(
                root, stagingDir, worldFolders, stripOverrides, manifest);
    }

    static boolean reapplyStagedDirectories(
            File root,
            File stagingDir,
            KList<File> worldFolders,
            boolean stripOverrides
    ) {
        if (Files.isSymbolicLink(stagingDir.toPath())
                || !Files.isDirectory(stagingDir.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            return reportReapplyFailure(ReapplyOutcome.failed(new IOException(
                    "Managed datapack staging is not a safe directory at "
                            + stagingDir.getPath()))).succeeded();
        }
        return reportReapplyFailure(reapplyStagedDirectoriesOutcome(
                root,
                stagingDir,
                worldFolders,
                stripOverrides,
                readManifest(root))).succeeded();
    }

    private static ReapplyOutcome reapplyStagedDirectoriesOutcome(
            File root,
            File stagingDir,
            KList<File> worldFolders,
            boolean stripOverrides,
            Manifest manifest
    ) {
        File[] staged = stagingDir.listFiles(File::isDirectory);
        if (staged == null) {
            return ReapplyOutcome.failed(new IOException(
                    "Unable to enumerate managed datapack staging at " + stagingDir.getPath()));
        }
        boolean repaired = false;
        IOException failure = null;
        for (Entry entry : manifest.entries) {
            File stagedDir = new File(stagingDir, entry.id);
            if (isRecordedUnchangedInstall(stagedDir, worldFolders, entry, stripOverrides)) {
                continue;
            }
            if (!isUsableStaging(stagedDir, entry)) {
                forgetInstallMetadata(entry);
                failure = appendFailure(failure, new IOException(
                        "Managed datapack staging is unusable for '" + entry.id
                                + "' at " + stagedDir.getPath()));
                continue;
            }
            try {
                InstallResult result = install(stagedDir, worldFolders, entry, stripOverrides);
                if (result.changed()) {
                    repaired = true;
                    IrisLogging.warn("Repaired installed datapack '" + entry.id
                            + "' from Iris staging before datapack compilation.");
                }
                recordInstallMetadata(stagedDir, worldFolders, entry);
            } catch (IOException e) {
                forgetInstallMetadata(entry);
                failure = appendFailure(failure, e);
            }
        }
        writeManifest(root, manifest);
        return failure == null
                ? ReapplyOutcome.success(false, repaired)
                : ReapplyOutcome.failed(failure);
    }

    private static IOException appendFailure(IOException current, IOException additional) {
        if (current == null) {
            return additional;
        }
        current.addSuppressed(additional);
        return current;
    }

    private static ReapplyOutcome reportReapplyFailure(ReapplyOutcome outcome) {
        if (!outcome.succeeded()) {
            IrisLogging.reportError(
                    "External datapack recovery or staging repair failed.",
                    outcome.failure().orElseThrow());
        }
        return outcome;
    }

    private static boolean isRecordedUnchangedInstall(
            File stagedDir,
            KList<File> worldFolders,
            Entry entry,
            boolean stripOverrides
    ) {
        if (entry.stagingMetadata == null || entry.stagingMetadata.isBlank()
                || entry.installMetadata == null || entry.installMetadata.size() != worldFolders.size()) {
            return false;
        }
        try {
            if (!isRecordedManagedDirectory(stagedDir, entry)
                    || !entry.stagingMetadata.equals(metadataDigest(stagedDir))) {
                return false;
            }
            for (File worldFolder : worldFolders) {
                File target = new File(worldFolder, entry.id);
                if (!isRecordedManagedDirectory(target, entry)
                        || new File(target, OVERRIDES_STRIPPED_MARKER).isFile() != stripOverrides) {
                    return false;
                }
                String recorded = entry.installMetadata.get(installMetadataKey(target));
                if (recorded == null || !recorded.equals(metadataDigest(target))) {
                    return false;
                }
            }
            return true;
        } catch (IOException e) {
            IrisLogging.debug("Managed datapack '" + entry.id
                    + "' requires full verification: " + e.getMessage());
            return false;
        }
    }

    private static boolean isRecordedManagedDirectory(File directory, Entry entry) throws IOException {
        Path path = directory.toPath();
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            return false;
        }
        if (!new File(directory, "pack.mcmeta").isFile()) {
            return false;
        }
        Ownership ownership = readOwnershipOrNull(directory);
        return ownership != null
                && ownershipSourceMatches(ownership, entry)
                && Objects.equals(ownership.versionId, entry.versionId)
                && Objects.equals(ownership.versionNumber, entry.versionNumber)
                && Objects.equals(ownership.sha1, entry.sha1);
    }

    private static void recordInstallMetadata(File stagedDir, KList<File> worldFolders, Entry entry) {
        try {
            Map<String, String> recorded = new HashMap<>();
            for (File worldFolder : worldFolders) {
                File target = new File(worldFolder, entry.id);
                recorded.put(installMetadataKey(target), metadataDigest(target));
            }
            entry.stagingMetadata = metadataDigest(stagedDir);
            entry.installMetadata = recorded;
        } catch (IOException e) {
            IrisLogging.debug("Unable to record managed datapack metadata for '" + entry.id
                    + "': " + e.getMessage());
            forgetInstallMetadata(entry);
        }
    }

    private static void forgetInstallMetadata(Entry entry) {
        entry.stagingMetadata = "";
        entry.installMetadata = new HashMap<>();
    }

    private static String installMetadataKey(File target) {
        return target.toPath().toAbsolutePath().normalize().toString();
    }

    private static String metadataDigest(File root) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            Path rootPath = root.toPath().toAbsolutePath().normalize();
            List<MetadataEntry> entries = new ArrayList<>();
            try (Stream<Path> paths = Files.walk(rootPath)) {
                Iterator<Path> iterator = paths.iterator();
                int pathCount = 0;
                while (iterator.hasNext()) {
                    Path path = iterator.next();
                    if (path.equals(rootPath) || isFinderMetadata(path)) {
                        continue;
                    }
                    pathCount++;
                    if (pathCount > MAX_MANAGED_PATHS) {
                        throw new IOException("Datapack contains more than " + MAX_MANAGED_PATHS + " paths");
                    }
                    if (Files.isSymbolicLink(path)) {
                        throw new IOException("Datapack contains a symbolic link: " + path);
                    }
                    String relativePath = rootPath.relativize(path).toString();
                    entries.add(new MetadataEntry(path, relativePath));
                }
            }
            entries.sort(Comparator.comparing(MetadataEntry::relativePath));
            for (MetadataEntry entry : entries) {
                String relative = entry.relativePath().replace(File.separatorChar, '/');
                byte[] relativeBytes = relative.getBytes(StandardCharsets.UTF_8);
                BasicFileAttributes attributes = Files.readAttributes(
                        entry.path(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (!attributes.isDirectory() && !attributes.isRegularFile()) {
                    throw new IOException("Datapack contains an unsupported filesystem entry: " + entry.path());
                }
                digest.update((byte) (attributes.isDirectory() ? 1 : 2));
                updateDigestInt(digest, relativeBytes.length);
                digest.update(relativeBytes);
                if (!attributes.isDirectory()) {
                    updateDigestLong(digest, attributes.size());
                    updateDigestLong(digest, attributes.lastModifiedTime().toMillis());
                }
            }
            return hex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 algorithm unavailable", e);
        }
    }

    static boolean recoverBeforeReapply(File root, List<File> worldFolders) {
        return reportReapplyFailure(recoverBeforeReapplyOutcome(root, worldFolders)).succeeded();
    }

    private static ReapplyOutcome recoverBeforeReapplyOutcome(File root, List<File> worldFolders) {
        try {
            return ReapplyOutcome.success(recoverTransactions(root, worldFolders), false);
        } catch (IOException e) {
            return ReapplyOutcome.failed(e);
        }
    }

    enum RemoveOutcome {
        REMOVED,
        REJECTED,
        FAILED_AFTER_MUTATION
    }

    public static boolean remove(VolmitSender sender, String id) {
        ServerConfigurator.LoadedDatapackRuntimeInvalidation invalidation =
                ServerConfigurator.invalidateLoadedDatapackRuntime();
        RemoveOutcome outcome;
        TRANSACTION_LOCK.lock();
        try {
            outcome = removeOutcomeLocked(sender, id);
        } finally {
            TRANSACTION_LOCK.unlock();
        }
        if (outcome == RemoveOutcome.REMOVED) {
            ServerConfigurator.requireDatapackRestart();
        } else if (outcome == RemoveOutcome.REJECTED) {
            // Rejected before any mutation: nothing on disk changed, so the loaded runtime is
            // still valid. Leaving it invalidated forced a full datapack reinstall on every
            // later world creation for the rest of the session.
            ServerConfigurator.restoreLoadedDatapackRuntimeIfUnchanged(invalidation);
        }
        return outcome == RemoveOutcome.REMOVED;
    }

    private static RemoveOutcome removeOutcomeLocked(VolmitSender sender, String id) {
        File root = IrisPlatforms.get().dataFolder("datapacks");
        return removeOutcomeLocked(sender, id, root, ServerConfigurator.getDatapacksFolder());
    }

    static boolean removeLocked(VolmitSender sender, String id, File root, List<File> worldFolders) {
        return removeOutcomeLocked(sender, id, root, worldFolders) == RemoveOutcome.REMOVED;
    }

    static RemoveOutcome removeOutcomeLocked(VolmitSender sender, String id, File root, List<File> worldFolders) {
        String requested = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        String cleaned = sanitizeId(id);
        if (requested.isBlank() || !requested.equals(cleaned) || RESERVED_IDS.contains(cleaned)) {
            message(sender, C.RED + "Invalid Iris-managed datapack id '" + requested + "'. Run /iris datapack list and use the exact listed id.");
            return RemoveOutcome.REJECTED;
        }
        try {
            recoverTransactions(root, worldFolders);
        } catch (IOException e) {
            message(sender, C.RED + "Datapack removal blocked by incomplete transaction recovery: " + e.getMessage());
            IrisLogging.reportError(e);
            return RemoveOutcome.REJECTED;
        }
        Manifest manifest = readManifest(root);
        Entry ownedEntry = manifest.findById(cleaned);
        if (ownedEntry == null) {
            message(sender, C.YELLOW + "No Iris-managed datapack named '" + cleaned + "'. Unmanaged world datapacks are never removed by Iris.");
            return RemoveOutcome.REJECTED;
        }

        List<File> targets;
        try {
            targets = preflightRemovalTargets(root, worldFolders, ownedEntry);
        } catch (IOException e) {
            message(sender, C.RED + "Refused to remove datapack '" + cleaned + "': " + e.getMessage());
            IrisLogging.reportError(e);
            return RemoveOutcome.REJECTED;
        }

        EditableImportRemoval editableRemoval = null;
        DirectoryRemoval directoryRemoval = null;
        ManifestWrite manifestWrite = null;
        DatapackCoordinator coordinator = null;
        try {
            editableRemoval = prepareEntryImportRemoval(ownedEntry, manifest.entries);
            directoryRemoval = prepareOwnedDirectories(targets);
            if (!manifest.removeById(cleaned)) {
                throw new IOException("Datapack manifest entry disappeared during removal");
            }
            manifestWrite = prepareManifestWrite(root, manifest);
            coordinator = createRemovalCoordinator(root, ownedEntry, directoryRemoval, editableRemoval);
            coordinator.phase(CoordinatorPhase.PUBLISHING);
            directoryRemoval.prepare();
            coordinator.phase(CoordinatorPhase.PUBLISHED);
            manifestWrite.publish();
        } catch (IOException | RuntimeException removalFailure) {
            boolean manifestCommitted = manifestWrite != null && manifestWrite.published();
            if (manifestCommitted) {
                finishCommittedRemoval(cleaned, manifestWrite, directoryRemoval, editableRemoval, coordinator,
                        removalFailure);
                message(sender, C.GREEN + "Removed datapack '" + C.WHITE + cleaned + C.GREEN
                        + "'. Restart for it to stop generating, and delete its URL from the pack's datapackImports to keep it gone.");
                return RemoveOutcome.REMOVED;
            }
            boolean restored = rollbackRemoval(manifestWrite, directoryRemoval, editableRemoval, removalFailure);
            if (restored && coordinator != null) {
                try {
                    coordinator.finish();
                } catch (IOException cleanupFailure) {
                    removalFailure.addSuppressed(cleanupFailure);
                }
            }
            message(sender, C.RED + "Failed to remove datapack '" + cleaned
                    + "'; Iris attempted to restore every prior location: " + removalFailure.getMessage());
            IrisLogging.reportError(removalFailure);
            // A mutation was attempted; even a successful rollback is not provably identical
            // (the fingerprint does not cover datapacks/), so stay conservatively invalidated.
            return RemoveOutcome.FAILED_AFTER_MUTATION;
        }
        finishCommittedRemoval(cleaned, manifestWrite, directoryRemoval, editableRemoval, coordinator, null);
        message(sender, C.GREEN + "Removed datapack '" + C.WHITE + cleaned + C.GREEN
                + "'. Restart for it to stop generating, and delete its URL from the pack's datapackImports to keep it gone.");
        return RemoveOutcome.REMOVED;
    }

    private static void finishCommittedRemoval(
            String id,
            ManifestWrite manifestWrite,
            DirectoryRemoval directoryRemoval,
            EditableImportRemoval editableRemoval,
            DatapackCoordinator coordinator,
            Throwable priorFailure
    ) {
        if (priorFailure != null) {
            IOException recoveryFailure = new IOException(
                    "Datapack manifest committed before publication durability was confirmed",
                    priorFailure
            );
            if (editableRemoval != null) {
                try {
                    editableRemoval.leaveForRecovery();
                } catch (IOException releaseFailure) {
                    recoveryFailure.addSuppressed(releaseFailure);
                }
            }
            try {
                manifestWrite.discard();
            } catch (IOException cleanupFailure) {
                recoveryFailure.addSuppressed(cleanupFailure);
            }
            IrisLogging.reportError("Datapack '" + id
                    + "' was removed but transaction cleanup requires restart recovery.", recoveryFailure);
            return;
        }

        IOException failure = null;
        if (coordinator != null) {
            try {
                coordinator.phase(CoordinatorPhase.COMMITTED);
            } catch (IOException phaseFailure) {
                failure = appendIOException(failure, phaseFailure);
                if (editableRemoval != null) {
                    try {
                        editableRemoval.leaveForRecovery();
                    } catch (IOException releaseFailure) {
                        failure = appendIOException(failure, releaseFailure);
                    }
                }
                try {
                    manifestWrite.discard();
                } catch (IOException cleanupFailure) {
                    failure = appendIOException(failure, cleanupFailure);
                }
                IrisLogging.reportError("Datapack '" + id
                        + "' was removed but transaction cleanup requires restart recovery.", failure);
                return;
            }
        }
        if (editableRemoval != null) {
            try {
                editableRemoval.markCommitted();
                editableRemoval.finishCommit();
            } catch (IOException | RuntimeException cleanupFailure) {
                failure = appendIOException(failure, cleanupFailure);
                try {
                    editableRemoval.leaveForRecovery();
                } catch (IOException releaseFailure) {
                    failure = appendIOException(failure, releaseFailure);
                }
            }
        }
        if (directoryRemoval != null) {
            try {
                directoryRemoval.finishCommit();
            } catch (IOException | RuntimeException cleanupFailure) {
                failure = appendIOException(failure, cleanupFailure);
            }
        }
        try {
            manifestWrite.discard();
        } catch (IOException cleanupFailure) {
            failure = appendIOException(failure, cleanupFailure);
        }
        if (failure == null && coordinator != null) {
            try {
                coordinator.finish();
            } catch (IOException cleanupFailure) {
                failure = cleanupFailure;
            }
        }
        if (failure != null) {
            IrisLogging.reportError("Datapack '" + id
                    + "' was removed but transaction cleanup requires restart recovery.", failure);
        }
    }

    private static IOException appendIOException(IOException current, Throwable failure) {
        IOException next = failure instanceof IOException ioFailure
                ? ioFailure : new IOException("Datapack transaction participant failed", failure);
        if (current == null) {
            return next;
        }
        current.addSuppressed(next);
        return current;
    }

    private static List<File> preflightRemovalTargets(File root, List<File> worldFolders, Entry entry) throws IOException {
        List<File> targets = new ArrayList<>();
        Set<Path> seen = new HashSet<>();
        File staging = new File(root, "staging");
        verifyDirectoryContainerIfPresent(staging, "datapack staging");
        File stagedDir = new File(staging, entry.id);
        verifyAndCollectRemovalTarget(stagedDir, entry, targets, seen);
        for (File worldFolder : worldFolders) {
            verifyDirectoryContainerIfPresent(worldFolder, "world datapacks");
            verifyAndCollectRemovalTarget(new File(worldFolder, entry.id), entry, targets, seen);
        }
        return targets;
    }

    private static void verifyAndCollectRemovalTarget(
            File target,
            Entry entry,
            List<File> targets,
            Set<Path> seen
    ) throws IOException {
        verifyOwnedDirectoryIfPresent(target, entry);
        Path normalized = target.toPath().toAbsolutePath().normalize();
        if (pathExists(normalized, "datapack removal target")) {
            Path identity = normalized.toRealPath();
            if (!seen.add(identity)) {
                throw new IOException("Aliased datapack removal target " + normalized);
            }
            targets.add(target);
        }
    }

    private static EditableImportRemoval prepareEntryImportRemoval(
            Entry entry,
            List<Entry> manifestEntries
    ) throws IOException {
        List<PreparedEditableImport> prepared = new ArrayList<>();
        Set<String> targetIdSet = new TreeSet<>(entry.importedBundles.keySet());
        targetIdSet.addAll(entry.importedTargets.keySet());
        targetIdSet.addAll(entry.importAttempts.keySet());
        List<String> targetIds = new ArrayList<>(targetIdSet);
        targetIds.sort(String::compareTo);
        try {
            for (String targetId : targetIds) {
                Set<String> retainedKeys = invalidateRetainedImportClaims(
                        entry, targetId, manifestEntries);
                File dataFolder = new File(targetId);
                if (!dataFolder.isDirectory()) {
                    continue;
                }
                StructureTransactionWriter writer = new StructureTransactionWriter(dataFolder.toPath());
                List<StructureTransactionWriter.OwnedRemoval> removals = ownedImportRemovals(
                        writer,
                        entry,
                        targetId,
                        retainedKeys
                );
                StructureTransactionWriter.PreparedRemoval removal =
                        writer.prepareMatchingOwnedRemovals(removals);
                prepared.add(new PreparedEditableImport(dataFolder, removal));
            }
            return new EditableImportRemoval(prepared);
        } catch (IOException | RuntimeException preparationFailure) {
            IOException failure = preparationFailure instanceof IOException ioFailure
                    ? ioFailure
                    : new IOException("Failed preparing editable datapack import cleanup", preparationFailure);
            for (int i = prepared.size() - 1; i >= 0; i--) {
                try {
                    prepared.get(i).removal().rollback();
                } catch (IOException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
            }
            throw failure;
        }
    }

    private static List<StructureTransactionWriter.OwnedRemoval> ownedImportRemovals(
            StructureTransactionWriter writer,
            Entry removingEntry,
            String targetId,
            Set<String> retainedKeys
    ) throws IOException {
        List<StructureTransactionWriter.OwnedRemoval> removals = new ArrayList<>();
        Map<String, Set<String>> removingClaims = importBundleClaims(removingEntry, targetId);
        for (Map.Entry<String, Set<String>> bundle : removingClaims.entrySet()) {
            try {
                StructureKey targetKey = StructureKey.parse(bundle.getKey());
                if (retainedKeys.contains(bundle.getKey())) {
                    continue;
                }
                Optional<StructureSource> ownedSource = writer.ownedSource(targetKey);
                if (ownedSource.isEmpty() || !sourceClaimsContain(bundle.getValue(), ownedSource.get())) {
                    continue;
                }
                removals.add(StructureTransactionWriter.OwnedRemoval.managedDatapack(
                        targetKey,
                        ownedSource.get().kind(),
                        ownedSource.get().key()
                ));
            } catch (RuntimeException e) {
                throw new IOException("Invalid editable structure import inventory entry '"
                        + bundle.getKey() + "' -> '" + bundle.getValue() + "'", e);
            }
        }
        return List.copyOf(removals);
    }

    private static Set<String> invalidateRetainedImportClaims(
            Entry removingEntry,
            String targetId,
            List<Entry> manifestEntries
    ) {
        Set<String> removingKeys = importBundleClaims(removingEntry, targetId).keySet();
        Set<String> retainedKeys = new TreeSet<>();
        for (Entry candidate : manifestEntries) {
            if (candidate == removingEntry) {
                continue;
            }
            Set<String> candidateKeys = importBundleClaims(candidate, targetId).keySet();
            boolean candidateRetained = false;
            for (String candidateKey : candidateKeys) {
                if (removingKeys.contains(candidateKey)) {
                    retainedKeys.add(candidateKey);
                    candidateRetained = true;
                }
            }
            if (candidateRetained) {
                candidate.importedTargets.remove(targetId);
                candidate.importAttempts.remove(targetId);
                candidate.structuresImported = false;
            }
        }
        return Set.copyOf(retainedKeys);
    }

    private static Map<String, Set<String>> importBundleClaims(Entry entry, String targetId) {
        Map<String, Set<String>> claims = new TreeMap<>();
        addImportBundleClaims(claims, entry.importedBundles.getOrDefault(targetId, Map.of()));
        if (entry.importedBundles.containsKey(targetId) || entry.importedTargets.containsKey(targetId)) {
            addImportBundleClaims(claims, importBundleInventory(entry));
        }
        return claims;
    }

    private static void addImportBundleClaims(
            Map<String, Set<String>> claims,
            Map<String, String> inventory
    ) {
        for (Map.Entry<String, String> bundle : inventory.entrySet()) {
            claims.computeIfAbsent(bundle.getKey(), ignored -> new TreeSet<>()).add(bundle.getValue());
        }
    }

    private static boolean sourceClaimsContain(Set<String> claims, StructureSource source) throws IOException {
        for (String claimedKey : claims) {
            try {
                StructureKey sourceKey = StructureKey.parse(claimedKey);
                StructureSource.Kind sourceKind = sourceKey.namespace().equals("minecraft")
                        ? StructureSource.Kind.VANILLA : StructureSource.Kind.DATAPACK;
                if (source.kind() == sourceKind && source.key().equals(sourceKey)) {
                    return true;
                }
            } catch (RuntimeException e) {
                throw new IOException("Invalid editable structure source key '" + claimedKey + "'", e);
            }
        }
        return false;
    }

    private static DirectoryRemoval prepareOwnedDirectories(List<File> targets) throws IOException {
        List<DirectoryMove> planned = new ArrayList<>();
        for (File target : targets) {
            File parent = target.getParentFile();
            File backupRoot = new File(parent.getParentFile() == null ? parent : parent.getParentFile(), ".iris-datapack-remove");
            File backup = new File(backupRoot, target.getName() + "-" + UUID.randomUUID());
            ensureScratchDirectory(backupRoot, "datapack removal backup");
            validateInstallTree(target, parent, "Datapack removal target");
            planned.add(new DirectoryMove(
                    target,
                    backup,
                    directoryHash(target),
                    ownershipMarkerFingerprint(target),
                    directoryIdentity(target),
                    realDirectoryPath(parent, "datapack target root"),
                    realDirectoryPath(backupRoot, "datapack removal scratch root"),
                    directoryIdentity(parent),
                    directoryIdentity(backupRoot)
            ));
        }
        return new DirectoryRemoval(planned);
    }

    private static boolean rollbackRemoval(
            ManifestWrite manifestWrite,
            DirectoryRemoval directoryRemoval,
            EditableImportRemoval editableRemoval,
            Throwable removalFailure
    ) {
        if (manifestWrite != null) {
            try {
                manifestWrite.discard();
            } catch (IOException discardFailure) {
                removalFailure.addSuppressed(discardFailure);
            }
        }
        if (directoryRemoval != null) {
            try {
                directoryRemoval.rollback();
            } catch (IOException rollbackFailure) {
                removalFailure.addSuppressed(rollbackFailure);
            }
        }
        if (editableRemoval != null) {
            try {
                editableRemoval.rollback();
            } catch (IOException rollbackFailure) {
                removalFailure.addSuppressed(rollbackFailure);
            }
        }
        return removalFailure.getSuppressed().length == 0;
    }

    private static void verifyOwnedDirectoryIfPresent(File directory, Entry entry) throws IOException {
        if (!Files.exists(directory.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (Files.isSymbolicLink(directory.toPath())
                || !Files.isDirectory(directory.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Refusing non-directory or symbolic-link target " + directory.getPath());
        }
        Ownership ownership = readOwnership(directory);
        if (!ownershipSourceMatches(ownership, entry)) {
            throw new IOException("Ownership marker at " + directory.getPath() + " belongs to '" + ownership.id + "'");
        }
        removeFinderMetadata(directory);
        if (!Objects.equals(ownership.contentHash, directoryHash(directory))) {
            throw new IOException("Refusing to remove modified or corrupt Iris-managed datapack " + directory.getPath());
        }
    }

    public static KList<String> collectConfiguredImports() {
        LinkedHashSet<String> sources = new LinkedHashSet<>();
        try (Stream<IrisData> stream = ServerConfigurator.allPacks()) {
            stream.forEach(data -> collectImports(data, sources));
        }
        sources.addAll(localDatapackImports());
        KList<String> result = new KList<>();
        result.addAll(sources);
        return result;
    }

    public static Set<String> configuredImports(IrisDimension dimension) {
        Iterable<String> explicit = dimension == null ? List.of() : dimension.getDatapackImports();
        return mergeConfiguredImports(explicit, localDatapackImports());
    }

    static Set<String> mergeConfiguredImports(
            Iterable<String> explicit,
            Iterable<String> discovered
    ) {
        LinkedHashSet<String> sources = new LinkedHashSet<>();
        if (explicit != null) {
            addImports(explicit, sources);
        }
        if (discovered != null) {
            addImports(discovered, sources);
        }
        return sources.isEmpty() ? Set.of() : Set.copyOf(sources);
    }

    static List<String> discoverLocalDatapackImports(File directory) throws IOException {
        Path root = directory.toPath().toAbsolutePath().normalize();
        if (Files.notExists(root, LinkOption.NOFOLLOW_LINKS)) {
            Files.createDirectories(root);
        }
        if (Files.isSymbolicLink(root)
                || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Local datapack import path is not a safe directory: " + root);
        }
        try (Stream<Path> stream = Files.list(root)) {
            return stream
                    .filter(path -> !Files.isSymbolicLink(path))
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(DatapackIngestService::isLocalDatapackArchive)
                    .sorted(Comparator.comparing(
                                    (Path path) -> path.getFileName().toString(),
                                    String.CASE_INSENSITIVE_ORDER)
                            .thenComparing((Path path) -> path.getFileName().toString()))
                    .map(path -> path.toAbsolutePath().normalize().toUri().toASCIIString())
                    .toList();
        }
    }

    private static boolean isLocalDatapackArchive(Path path) {
        String name = path.getFileName().toString();
        return !name.startsWith(".") && name.toLowerCase(Locale.ROOT).endsWith(".zip");
    }

    private static List<String> localDatapackImports() {
        File directory = IrisPlatforms.get().dataFolder("datapacks", LOCAL_IMPORT_DIRECTORY);
        try {
            return discoverLocalDatapackImports(directory);
        } catch (IOException exception) {
            IrisLogging.reportError("Could not discover local datapack imports under "
                    + directory.getPath() + ".", exception);
            return List.of();
        }
    }

    public static List<Entry> installed() {
        TRANSACTION_LOCK.lock();
        try {
            File root = IrisPlatforms.get().dataFolder("datapacks");
            try {
                recoverTransactions(root, ServerConfigurator.getDatapacksFolder());
            } catch (IOException e) {
                IrisLogging.reportError("Could not recover datapack transactions before listing installed packs.", e);
                return List.of();
            }
            return List.copyOf(readManifest(root).entries);
        } finally {
            TRANSACTION_LOCK.unlock();
        }
    }

    public static List<StructureScopeResources> installedStructureScopeResources() throws IOException {
        TRANSACTION_LOCK.lock();
        try {
            File root = IrisPlatforms.get().dataFolder("datapacks");
            Manifest manifest = readManifest(root);
            KList<File> datapackFolders = ServerConfigurator.getDatapacksFolder();
            List<StructureScopeResources> resources = new ArrayList<>();
            for (Entry entry : manifest.entries) {
                boolean found = false;
                for (File datapackFolder : datapackFolders) {
                    File installedDirectory = new File(datapackFolder, entry.id);
                    if (!Files.exists(installedDirectory.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                        continue;
                    }
                    resources.add(scanInstalledStructureScope(installedDirectory, entry));
                    found = true;
                }
                if (!found) {
                    throw new IOException("Missing installed Iris-managed datapack '" + entry.id + "'");
                }
            }
            return List.copyOf(resources);
        } finally {
            TRANSACTION_LOCK.unlock();
        }
    }

    static StructureScopeResources scanInstalledStructureScope(File directory, Entry entry) throws IOException {
        Path path = directory.toPath();
        if (Files.isSymbolicLink(path)
                || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Invalid installed Iris-managed datapack directory " + directory.getPath());
        }
        validatePackMetadata(directory);
        rejectSymbolicLinks(directory);
        Ownership ownership = readOwnership(directory);
        if (!ownershipSourceMatches(ownership, entry)) {
            throw new IOException("Installed datapack ownership mismatch at " + directory.getPath());
        }
        if (!Objects.equals(ownership.contentHash, directoryHash(directory))) {
            throw new IOException("Installed Iris-managed datapack is modified or corrupt at "
                    + directory.getPath());
        }
        PackResources resources = scanPackResources(directory);
        return new StructureScopeResources(
                entry.url,
                resources.structureKeys(),
                resources.structureSetKeys());
    }

    private static void ingestSingle(
            VolmitSender sender,
            String url,
            String mcVersion,
            File cacheDir,
            File stagingDir,
            KList<File> worldFolders,
            Manifest manifest,
            Report report,
            boolean stripOverrides,
            List<InstallExecution> installs
    ) throws IOException {
        ResolvedDatapack resolved = ModrinthResolver.resolve(url, mcVersion);
        Entry existing = manifest.find(url);
        String id = existing == null ? deriveId(resolved) : existing.id;
        if (RESERVED_IDS.contains(id)) {
            throw new IOException("Datapack id '" + id + "' is reserved by Iris");
        }
        Entry idCollision = manifest.findById(id);
        if (idCollision != null && !Objects.equals(idCollision.url, url)) {
            throw new IOException("Datapack id '" + id + "' is already owned by " + idCollision.url);
        }
        File stagedDir = new File(stagingDir, id);
        boolean stageUsable = existing != null && isUsableStaging(stagedDir, existing);
        boolean recoveringManagedStaging = existing != null && !stageUsable;
        boolean sameVersion = !resolved.isDirect()
                && existing != null
                && Objects.equals(existing.versionId, resolved.getVersionId())
                && (resolved.getSha1() == null || Objects.equals(existing.sha1, resolved.getSha1()))
                && stageUsable;

        if (sameVersion) {
            InstallExecution execution = prepareInstallExecution(
                    stagedDir, worldFolders, existing, stripOverrides, cacheDir.getParentFile());
            installs.add(execution);
            InstallResult installResult = execution.result();
            recordInstallResult(
                    sender, report, stagedDir, worldFolders, existing, installResult, resolved.getVersionNumber());
            return;
        }

        message(sender, C.GRAY + "  Checking " + C.WHITE + id + C.GRAY + " " + safe(resolved.getVersionNumber()) + "...");
        File zip = new File(cacheDir, id + "-" + safeFile(resolved.getVersionId()) + ".zip");
        DownloadResult download = download(
                resolved.getDownloadUrl(),
                zip,
                resolved.isDirect() && stageUsable ? existing.etag : null,
                resolved.isDirect() && stageUsable ? existing.lastModified : null
        );
        if (download.notModified()) {
            if (!stageUsable) {
                throw new IOException("Remote returned not-modified but Iris staging is missing or corrupt for " + id);
            }
            Entry updated = copyEntry(existing);
            updated.etag = download.etag();
            updated.lastModified = download.lastModified();
            InstallExecution execution = prepareInstallExecution(
                    stagedDir, worldFolders, updated, stripOverrides, cacheDir.getParentFile());
            installs.add(execution);
            InstallResult installResult = execution.result();
            manifest.put(updated);
            recordInstallResult(
                    sender, report, stagedDir, worldFolders, updated, installResult, resolved.getVersionNumber());
            return;
        }

        String checksum = sha1(zip);
        if (resolved.getSha1() != null && !resolved.getSha1().isBlank() && !resolved.getSha1().equalsIgnoreCase(checksum)) {
            IO.delete(zip);
            throw new IOException("Checksum mismatch for " + id + " (expected " + resolved.getSha1() + ", got " + checksum + ")");
        }

        if (resolved.isDirect() && existing != null && Objects.equals(existing.sha1, checksum) && stageUsable) {
            Entry updated = copyEntry(existing);
            updated.etag = download.etag();
            updated.lastModified = download.lastModified();
            updated.installedEpoch = System.currentTimeMillis();
            InstallExecution execution = prepareInstallExecution(
                    stagedDir, worldFolders, updated, stripOverrides, cacheDir.getParentFile());
            installs.add(execution);
            InstallResult installResult = execution.result();
            writeOwnership(stagedDir, updated);
            manifest.put(updated);
            recordInstallResult(
                    sender, report, stagedDir, worldFolders, updated, installResult, resolved.getVersionNumber());
            return;
        }

        Entry entry = existing != null ? copyEntry(existing) : new Entry();
        entry.url = url;
        entry.id = id;
        entry.versionId = resolved.getVersionId();
        entry.versionNumber = resolved.getVersionNumber();
        entry.sha1 = checksum;
        entry.filename = resolved.getFileName();
        entry.etag = download.etag();
        entry.lastModified = download.lastModified();
        entry.installedEpoch = System.currentTimeMillis();
        entry.structuresImported = false;
        File extractedDir = extractArchive(zip, stagingDir, entry);
        InstallExecution execution;
        try {
            VerifiedStagingInstall verifiedStagingInstall =
                    authorizeVerifiedStagingInstall(
                            cacheDir.getParentFile(), stagingDir, extractedDir, entry);
            execution = prepareInstallExecution(
                    extractedDir, worldFolders, entry, stripOverrides, cacheDir.getParentFile(),
                    verifiedStagingInstall);
        } finally {
            cleanupExtractedStaging(extractedDir);
        }
        installs.add(execution);
        InstallResult installResult = execution.result();
        recordInstallMetadata(stagedDir, worldFolders, entry);
        manifest.put(entry);

        report.updated.add(id + " (" + safe(resolved.getVersionNumber()) + ")");
        if (freshInstallRequiresRestart(installResult.changed(), recoveringManagedStaging)) {
            report.requiresRestart = true;
        }
        message(sender, C.GREEN + "  Installed " + C.WHITE + id + C.GREEN + " " + safe(resolved.getVersionNumber()));
    }

    static boolean freshInstallRequiresRestart(boolean contentChanged, boolean recoveringManagedStaging) {
        return contentChanged || recoveringManagedStaging;
    }

    private static File extractArchive(File zip, File stagingRoot, Entry entry) throws IOException {
        ensureScratchDirectory(stagingRoot, "datapack staging");
        File pending = new File(stagingRoot, ".pending-" + entry.id + "-" + UUID.randomUUID());
        try {
            if (!pending.mkdirs() && !pending.isDirectory()) {
                throw new IOException("Couldn't create datapack extraction directory " + pending.getPath());
            }
            ZipUtils.unzipFile(zip, pending, MAX_ARCHIVE_ENTRIES, MAX_EXPANDED_BYTES, MAX_ENTRY_BYTES);
            flattenIfWrapped(pending);
            validatePackMetadata(pending);
            PackResources resources = scanPackResources(pending);
            entry.structureKeys = resources.structureKeys;
            entry.templateKeys = resources.templateKeys;
            writeOwnership(pending, entry);
            return pending;
        } catch (UncheckedIOException e) {
            IOException failure = e.getCause();
            cleanupFailedExtraction(pending, failure);
            throw failure;
        } catch (IOException | RuntimeException e) {
            cleanupFailedExtraction(pending, e);
            throw e;
        }
    }

    private static void cleanupFailedExtraction(File pending, Throwable failure) {
        try {
            deleteInstallScratch(pending, "failed datapack extraction");
        } catch (IOException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    private static void cleanupExtractedStaging(File extractedDir) {
        try {
            deleteInstallScratch(extractedDir, "extracted datapack staging");
        } catch (IOException e) {
            IrisLogging.warn("Preserving extracted datapack staging for restart cleanup: "
                    + extractedDir.getPath() + " (" + e.getMessage() + ")");
        }
    }

    static VerifiedStagingInstall authorizeVerifiedStagingInstall(
            File root,
            File stagingRoot,
            File verifiedSource,
            Entry entry
    ) throws IOException {
        Path normalizedRoot = requireDirectoryIdentity(root, "datapack storage root");
        Path normalizedStagingRoot = requireDirectoryIdentity(stagingRoot, "datapack staging root");
        requireNoSymbolicLinkComponents(normalizedRoot, "datapack storage root");
        requireNoSymbolicLinkComponents(normalizedStagingRoot, "datapack staging root");
        Path expectedStagingRoot = normalizedRoot.resolve("staging").normalize();
        if (!normalizedStagingRoot.equals(expectedStagingRoot)
                || !Files.isSameFile(normalizedStagingRoot, expectedStagingRoot)) {
            throw new IOException("Verified datapack staging root is not Iris's canonical staging directory");
        }
        Path normalizedSource = requireDirectoryIdentity(verifiedSource, "verified datapack extraction");
        requireNoSymbolicLinkComponents(normalizedSource, "verified datapack extraction");
        if (!Objects.equals(normalizedSource.getParent(), normalizedStagingRoot)
                || !Files.isSameFile(normalizedSource.getParent(), normalizedStagingRoot)) {
            throw new IOException("Verified datapack extraction is outside Iris's canonical staging directory");
        }
        verifyPendingExtractionName(normalizedSource.getFileName().toString(), entry.id);
        validateManagedDirectory(verifiedSource, entry.id);
        validateScratchTree(normalizedSource);
        if (!sameScratchVolume(normalizedSource, normalizedStagingRoot)) {
            throw new IOException("Verified datapack extraction crosses a filesystem boundary");
        }
        String desiredHash = directoryHash(verifiedSource);
        Ownership ownership = readOwnership(verifiedSource);
        if (!ownershipMetadataMatches(ownership, entry, desiredHash)) {
            throw new IOException("Verified datapack extraction does not match the resolved archive metadata");
        }
        Manifest committedManifest = readCommittedManifest(root);
        Entry committed = committedManifest.findById(entry.id);
        boolean legacyReplacementAuthorized = committed != null
                && Objects.equals(committed.id, entry.id)
                && Objects.equals(committed.url, entry.url);
        LegacyStagingSnapshot legacyStagingSnapshot = captureLegacyStagingSnapshot(
                normalizedStagingRoot, entry.id, legacyReplacementAuthorized);
        return new VerifiedStagingInstall(
                normalizedRoot,
                normalizedStagingRoot,
                normalizedSource,
                entry,
                desiredHash,
                committed,
                legacyReplacementAuthorized,
                legacyStagingSnapshot
        );
    }

    private static LegacyStagingSnapshot captureLegacyStagingSnapshot(
            Path normalizedStagingRoot,
            String id,
            boolean legacyReplacementAuthorized
    ) throws IOException {
        if (!legacyReplacementAuthorized) {
            return null;
        }
        Path target = normalizedStagingRoot.resolve(id).normalize();
        if (!pathExists(target, "legacy datapack staging target")) {
            return null;
        }
        if (Files.isSymbolicLink(target)
                || !Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Invalid legacy datapack staging target " + target);
        }
        validateInstallTree(target.toFile(), normalizedStagingRoot.toFile(), "Legacy datapack staging");
        String markerHash = ownershipMarkerFingerprint(target.toFile());
        if (!"absent".equals(markerHash)) {
            return null;
        }
        return new LegacyStagingSnapshot(
                target,
                target.toRealPath(),
                directoryIdentity(target.toFile()),
                directoryHash(target.toFile()),
                markerHash
        );
    }

    private static Path requireDirectoryIdentity(File directory, String purpose) throws IOException {
        Path normalized = directory.toPath().toAbsolutePath().normalize();
        BasicFileAttributes attributes = Files.readAttributes(
                normalized, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!isSupportedScratchDirectory(attributes)) {
            throw new IOException("Invalid " + purpose + " " + normalized);
        }
        normalized.toRealPath();
        return normalized;
    }

    private static void requireNoSymbolicLinkComponents(Path normalized, String purpose) throws IOException {
        Path current = normalized.getRoot();
        for (Path component : normalized) {
            current = current == null ? component : current.resolve(component);
            BasicFileAttributes attributes = Files.readAttributes(
                    current, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (attributes.isSymbolicLink()) {
                throw new IOException("Refusing symbolic-link component in " + purpose + " " + normalized);
            }
            if (attributes.isOther()) {
                throw new IOException("Refusing special filesystem component in " + purpose + " " + normalized);
            }
            if (!isSupportedScratchDirectory(attributes)) {
                throw new IOException("Refusing unsupported component in " + purpose + " " + normalized);
            }
        }
    }

    private static void verifyPendingExtractionName(String name, String id) throws IOException {
        String prefix = ".pending-" + id + "-";
        if (!name.startsWith(prefix)) {
            throw new IOException("Verified datapack extraction has an invalid staging name " + name);
        }
        try {
            UUID.fromString(name.substring(prefix.length()));
        } catch (IllegalArgumentException e) {
            throw new IOException("Verified datapack extraction has an invalid staging identity " + name, e);
        }
    }

    private static void validateInstallParticipants(
            List<File> worldFolders,
            VerifiedStagingInstall verifiedStagingInstall,
            Entry entry
    ) throws IOException {
        List<File> roots = new ArrayList<>(worldFolders);
        if (verifiedStagingInstall != null) {
            roots.add(verifiedStagingInstall.stagingRoot().toFile());
        }
        Set<Path> normalizedTargets = new HashSet<>();
        Set<Path> realRoots = new HashSet<>();
        Set<Path> targetIdentities = new HashSet<>();
        List<Path> existingTargets = new ArrayList<>();
        for (File folder : roots) {
            ensureInstallTargetRoot(folder);
            Path normalizedRoot = folder.toPath().toAbsolutePath().normalize();
            Path realRoot = normalizedRoot.toRealPath();
            Path normalizedTarget = normalizedRoot.resolve(entry.id).normalize();
            if (!realRoots.add(realRoot) || !normalizedTargets.add(normalizedTarget)) {
                throw new IOException("Duplicate datapack install target " + normalizedTarget);
            }
            Path targetIdentity = realRoot.resolve(entry.id).normalize();
            if (Files.exists(normalizedTarget, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isSymbolicLink(normalizedTarget)
                        || !Files.isDirectory(normalizedTarget, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Refusing invalid datapack install target " + normalizedTarget);
                }
                targetIdentity = normalizedTarget.toRealPath();
                for (Path existingTarget : existingTargets) {
                    if (Files.isSameFile(normalizedTarget, existingTarget)) {
                        throw new IOException("Aliased datapack install target " + normalizedTarget);
                    }
                }
                existingTargets.add(normalizedTarget);
            }
            if (!targetIdentities.add(targetIdentity)) {
                throw new IOException("Aliased datapack install target " + normalizedTarget);
            }
        }
        if (verifiedStagingInstall != null) {
            verifiedStagingInstall.verifyStagingRoot();
        }
    }

    private static void ensureInstallTargetRoot(File directory) throws IOException {
        if (!Files.exists(directory.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            Files.createDirectories(directory.toPath());
        }
        requireDirectoryIdentity(directory, "datapack install root");
    }

    static void recordInstallResult(
            VolmitSender sender,
            Report report,
            File stagedDir,
            KList<File> worldFolders,
            Entry entry,
            InstallResult result,
            String versionNumber
    ) {
        recordInstallMetadata(stagedDir, worldFolders, entry);
        if (result.changed()) {
            report.updated.add(entry.id + " (" + safe(versionNumber) + ")");
            report.requiresRestart = true;
            message(sender, C.GREEN + "  Repaired " + C.WHITE + entry.id + C.GREEN + " " + safe(versionNumber));
            return;
        }
        report.upToDate.add(entry.id + " (" + safe(versionNumber) + ")");
        message(sender, C.GRAY + "  Up to date: " + C.WHITE + entry.id + C.GRAY + " " + safe(versionNumber));
    }

    private static void collectImports(IrisData data, LinkedHashSet<String> urls) {
        if (data == null || data.getDimensionLoader() == null) {
            return;
        }
        for (IrisDimension dimension : data.getDimensionLoader().loadAll(data.getDimensionLoader().getPossibleKeys())) {
            if (dimension == null) {
                continue;
            }
            KList<String> imports = dimension.getDatapackImports();
            if (imports == null) {
                continue;
            }
            addImports(imports, urls);
        }
    }

    private static void addImports(Iterable<String> imports, LinkedHashSet<String> sources) {
        for (String source : imports) {
            if (source != null && !source.isBlank()) {
                sources.add(normalizeConfiguredSource(source));
            }
        }
    }

    static String normalizeConfiguredSource(String source) {
        String normalized = source.trim();
        try {
            URI uri = new URI(normalized);
            if ("file".equalsIgnoreCase(uri.getScheme())) {
                return Path.of(uri).toAbsolutePath().normalize().toUri().toASCIIString();
            }
        } catch (IllegalArgumentException | URISyntaxException ignored) {
        }
        return normalized;
    }

    private static Set<String> configuredImports(IrisData data) {
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        collectImports(data, urls);
        urls.addAll(localDatapackImports());
        return urls;
    }

    private static boolean hasImports(IrisData data) {
        return !configuredImports(data).isEmpty();
    }

    static InstallResult install(File stagedDir, KList<File> worldFolders, Entry entry, boolean stripOverrides) throws IOException {
        File root = inferDatapackRoot(stagedDir);
        recoverTransactions(root, worldFolders);
        InstallExecution execution = prepareInstallExecution(
                stagedDir,
                worldFolders,
                entry,
                stripOverrides,
                root
        );
        try {
            verifyInstallExecution(execution);
        } catch (IOException failure) {
            rollbackInstallExecutions(List.of(execution), failure);
            throw failure;
        }
        finishInstallExecution(execution);
        return execution.result();
    }

    static InstallExecution prepareInstallExecution(
            File stagedDir,
            KList<File> worldFolders,
            Entry entry,
            boolean stripOverrides,
            File root
    ) throws IOException {
        return prepareInstallExecution(
                stagedDir, worldFolders, entry, stripOverrides, root, null);
    }

    static InstallExecution prepareInstallExecution(
            File stagedDir,
            KList<File> worldFolders,
            Entry entry,
            boolean stripOverrides,
            File root,
            VerifiedStagingInstall verifiedStagingInstall
    ) throws IOException {
        validateManagedDirectory(stagedDir, entry.id);
        Ownership stagedOwnership = readOwnership(stagedDir);
        String stagedHash = directoryHash(stagedDir);
        if (!ownershipMetadataMatches(stagedOwnership, entry, stagedHash)) {
            throw new IOException("Iris datapack staging does not match the committed manifest entry for " + entry.id);
        }
        validateInstallParticipants(worldFolders, verifiedStagingInstall, entry);
        List<InstallPlan> plans = new ArrayList<>();
        try {
            for (File worldFolder : worldFolders) {
                plans.add(prepareInstall(
                        stagedDir, worldFolder, entry, stagedHash, stripOverrides, verifiedStagingInstall));
            }
            if (verifiedStagingInstall != null) {
                plans.add(prepareInstall(
                        stagedDir,
                        verifiedStagingInstall.stagingRoot().toFile(),
                        entry,
                        stagedHash,
                        false,
                        verifiedStagingInstall));
            }
        } catch (IOException | RuntimeException preparationFailure) {
            for (InstallPlan plan : plans) {
                try {
                    cleanupInstallPlan(plan, false);
                } catch (IOException cleanupFailure) {
                    preparationFailure.addSuppressed(cleanupFailure);
                }
            }
            if (preparationFailure instanceof UncheckedIOException unchecked) {
                throw unchecked.getCause();
            }
            if (preparationFailure instanceof IOException ioFailure) {
                throw ioFailure;
            }
            throw preparationFailure;
        }

        boolean changed = false;
        List<InstallPlan> publishPlans = new ArrayList<>();
        List<InstallPlan> unchangedPlans = new ArrayList<>();
        try {
            for (InstallPlan plan : plans) {
                changed |= plan.contentChanged();
                if (plan.publishRequired()) {
                    publishPlans.add(plan);
                } else {
                    unchangedPlans.add(plan);
                    cleanupInstallPlan(plan, true);
                }
            }
        } catch (IOException cleanupFailure) {
            for (InstallPlan plan : plans) {
                try {
                    cleanupInstallPlan(plan, false);
                } catch (IOException additionalFailure) {
                    cleanupFailure.addSuppressed(additionalFailure);
                }
            }
            throw cleanupFailure;
        }
        if (publishPlans.isEmpty()) {
            return new InstallExecution(
                    new InstallResult(changed),
                    null,
                    stagedDir,
                    entry,
                    stagedHash,
                    verifiedStagingInstall == null,
                    publishPlans,
                    unchangedPlans);
        }

        Manifest committedManifest = readCommittedManifest(root);
        boolean manifestAlreadyMatched = manifestEntryMatches(committedManifest.findById(entry.id), entry);
        DatapackCoordinator coordinator;
        try {
            coordinator = createInstallCoordinator(root, entry, publishPlans, manifestAlreadyMatched);
        } catch (IOException | RuntimeException coordinatorFailure) {
            for (InstallPlan plan : publishPlans) {
                try {
                    cleanupInstallPlan(plan, false);
                } catch (IOException cleanupFailure) {
                    coordinatorFailure.addSuppressed(cleanupFailure);
                }
            }
            if (coordinatorFailure instanceof IOException ioFailure) {
                throw ioFailure;
            }
            throw coordinatorFailure;
        }

        try {
            coordinator.phase(CoordinatorPhase.PUBLISHING);
            for (InstallPlan plan : publishPlans) {
                publishInstallPlan(plan);
            }
            coordinator.phase(CoordinatorPhase.PUBLISHED);
        } catch (IOException | RuntimeException publishFailure) {
            try {
                resolveCoordinatorDirectories(coordinator.journal, false);
                coordinator.finish();
            } catch (IOException rollbackFailure) {
                publishFailure.addSuppressed(rollbackFailure);
            }
            if (publishFailure instanceof IOException ioFailure) {
                throw ioFailure;
            }
            throw publishFailure;
        }
        return new InstallExecution(
                new InstallResult(changed),
                coordinator,
                stagedDir,
                entry,
                stagedHash,
                verifiedStagingInstall == null,
                publishPlans,
                unchangedPlans);
    }

    static void verifyInstallExecution(InstallExecution execution) throws IOException {
        if (execution.verified()) {
            return;
        }
        if (execution.verifyStagedSource()) {
            validateManagedDirectory(execution.stagedDir(), execution.entry().id);
            Ownership stagedOwnership = readOwnership(execution.stagedDir());
            String stagedHash = directoryHash(execution.stagedDir());
            if (!Objects.equals(stagedHash, execution.stagedHash())
                    || !ownershipMetadataMatches(stagedOwnership, execution.entry(), stagedHash)) {
                throw new IOException("Iris datapack staging changed before installation commit for "
                        + execution.entry().id);
            }
        }
        for (InstallPlan plan : execution.publishedPlans()) {
            verifyDesiredInstallSnapshot(plan.target(), plan, "published datapack target");
        }
        for (InstallPlan plan : execution.unchangedPlans()) {
            verifyOriginalInstallSnapshot(plan.target(), plan, "unchanged datapack target");
        }
        execution.markVerified();
    }

    static void finishInstallExecution(InstallExecution execution) throws IOException {
        if (!execution.verified()) {
            throw new IOException("Datapack install cannot commit before final verification");
        }
        if (execution.coordinator() == null) {
            return;
        }
        execution.coordinator().phase(CoordinatorPhase.COMMITTED);
        resolveCoordinatorDirectories(execution.coordinator().journal, true);
        execution.coordinator().finish();
    }

    static void rollbackInstallExecutions(List<InstallExecution> executions, Throwable failure) {
        List<InstallExecution> reversed = new ArrayList<>(executions);
        Collections.reverse(reversed);
        for (InstallExecution execution : reversed) {
            if (execution.coordinator() == null) {
                continue;
            }
            try {
                resolveCoordinatorDirectories(execution.coordinator().journal, false);
                execution.coordinator().finish();
            } catch (IOException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
        }
    }

    private static File inferDatapackRoot(File stagedDir) {
        File parent = stagedDir.getParentFile();
        if (parent != null && "staging".equals(parent.getName()) && parent.getParentFile() != null) {
            return parent.getParentFile();
        }
        return parent == null ? stagedDir : parent;
    }

    private static boolean manifestEntryMatches(Entry committed, Entry desired) {
        return committed != null
                && Objects.equals(committed.id, desired.id)
                && Objects.equals(committed.url, desired.url)
                && Objects.equals(committed.versionId, desired.versionId)
                && Objects.equals(committed.versionNumber, desired.versionNumber)
                && Objects.equals(committed.sha1, desired.sha1);
    }

    static String directoryIdentity(File directory) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(
                directory.toPath(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isDirectory() || attributes.isSymbolicLink()) {
            throw new IOException("Invalid datapack directory identity at " + directory.getPath());
        }
        Object fileKey = attributes.fileKey();
        if (fileKey != null) {
            return "key:" + fileKey;
        }
        // Windows exposes no inode-equivalent for a directory, and returning nothing here fails
        // the identity gate closed, which disables legacy staging replacement on that platform
        // outright. Creation time is the one stable discriminator the filesystem still offers: a
        // directory swapped in at the same path brings its own, so a swap is still caught. It is
        // a weaker guarantee than an inode, so it is only ever the fallback.
        return "created:" + attributes.creationTime();
    }

    private static boolean pathExists(Path path, String purpose) throws IOException {
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return true;
        }
        if (Files.notExists(path, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        throw new IOException("Cannot determine " + purpose + " state at " + path);
    }

    private static void validateInstallTree(File directory, File storeAnchor, String purpose) throws IOException {
        validateScratchTree(directory.toPath());
        if (!sameScratchVolume(directory.toPath(), storeAnchor.toPath())) {
            throw new IOException(purpose + " crosses a filesystem boundary at " + directory.getPath());
        }
    }

    static InstallPlan prepareInstall(
            File stagedDir,
            File worldFolder,
            Entry entry,
            String stagedHash,
            boolean stripOverrides,
            VerifiedStagingInstall verifiedStagingInstall
    ) throws IOException {
        ensureInstallTargetRoot(worldFolder);
        File target = new File(worldFolder, entry.id);
        InstallPlan unchanged = tryPrepareUnchangedManagedInstall(
                stagedDir,
                worldFolder,
                target,
                entry,
                stagedHash,
                stripOverrides,
                verifiedStagingInstall);
        if (unchanged != null) {
            return unchanged;
        }
        boolean canonicalStagingInstall = verifiedStagingInstall != null
                && verifiedStagingInstall.isCanonicalInstall(worldFolder, target);
        boolean legacyReplacementAuthorized = canonicalStagingInstall
                && verifiedStagingInstall.consume(stagedDir, worldFolder, target, entry, stagedHash);
        VerifiedStagingInstall legacyWorldAuthorization = null;
        File pendingRoot = installScratchRoot(worldFolder);
        File pending = new File(pendingRoot, entry.id + "-" + UUID.randomUUID());
        File backup = new File(pendingRoot, entry.id + "-backup-" + UUID.randomUUID());
        try {
            ensureScratchDirectory(pendingRoot, "datapack install staging");
            String targetRootIdentity = realDirectoryPath(worldFolder, "datapack target root");
            String scratchRootIdentity = realDirectoryPath(pendingRoot, "datapack install scratch root");
            String targetRootFileIdentity = directoryIdentity(worldFolder);
            String scratchRootFileIdentity = directoryIdentity(pendingRoot);
            IO.copyDirectory(stagedDir.toPath(), pending.toPath());
            Files.deleteIfExists(new File(pending, OWNERSHIP_MARKER).toPath());
            String copiedHash = directoryHash(pending);
            if (!Objects.equals(stagedHash, copiedHash)) {
                throw new IOException("Datapack staging changed or copied incompletely while preparing " + entry.id);
            }
            boolean removedOverrideMarker = Files.deleteIfExists(
                    new File(pending, OVERRIDES_STRIPPED_MARKER).toPath());
            if (stripOverrides) {
                stripVanillaStructureOverrides(pending);
                writeMarker(new File(pending, OVERRIDES_STRIPPED_MARKER));
            }
            validatePackMetadata(pending);
            if (!stripOverrides && !removedOverrideMarker) {
                writeOwnership(pending, entry, copiedHash);
            } else {
                writeOwnership(pending, entry);
            }
            validateInstallTree(pending, worldFolder, "Prepared datapack install");
            Ownership desiredOwnership = readOwnership(pending);
            String desiredHash = desiredOwnership.contentHash;
            String desiredMarkerHash = ownershipMarkerFingerprint(pending);
            String desiredIdentity = directoryIdentity(pending);
            boolean hadTarget = pathExists(target.toPath(), "datapack install target");
            String originalHash = "";
            String originalMarkerHash = "absent";
            String originalIdentity = "";
            boolean contentChanged = !hadTarget;
            boolean publishRequired = !hadTarget;
            if (hadTarget) {
                if (Files.isSymbolicLink(target.toPath())
                        || !Files.isDirectory(target.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Refusing to replace non-directory or symbolic-link datapack " + target.getPath());
                }
                validateInstallTree(target, worldFolder, "Existing datapack install");
                Ownership ownership = readOwnershipOrNull(target);
                if (ownership != null) {
                    removeFinderMetadata(target);
                }
                String currentHash = directoryHash(target);
                originalHash = currentHash;
                originalMarkerHash = ownershipMarkerFingerprint(target);
                originalIdentity = directoryIdentity(target);
                if (ownership == null) {
                    boolean differingLegacyTarget = !Objects.equals(currentHash, desiredHash);
                    if (differingLegacyTarget && !legacyReplacementAuthorized
                            && verifiedStagingInstall != null
                            && verifiedStagingInstall.authorizeLegacyWorldReplacement(
                            stagedDir,
                            worldFolder,
                            target,
                            entry,
                            stagedHash,
                            currentHash,
                            originalMarkerHash)) {
                        legacyReplacementAuthorized = true;
                        legacyWorldAuthorization = verifiedStagingInstall;
                    }
                    if (differingLegacyTarget && !legacyReplacementAuthorized) {
                        throw new IOException("Refusing to replace unmanaged datapack at " + target.getPath());
                    }
                    if (differingLegacyTarget && (originalIdentity.isEmpty()
                            || targetRootFileIdentity.isEmpty()
                            || scratchRootFileIdentity.isEmpty()
                            || verifiedStagingInstall == null
                            || !verifiedStagingInstall.hasStablePathIdentities())) {
                        throw new IOException("Cannot safely identify legacy datapack staging at " + target.getPath());
                    }
                    publishRequired = true;
                } else {
                    if (!entry.id.equals(ownership.id)) {
                        throw new IOException("Datapack ownership mismatch at " + target.getPath());
                    }
                    publishRequired = !Objects.equals(ownership.contentHash, currentHash)
                            || !ownershipMetadataMatches(ownership, entry, desiredHash);
                }
                contentChanged = !Objects.equals(currentHash, desiredHash);
                publishRequired |= contentChanged;
                if (ownership != null && !Objects.equals(ownership.contentHash, currentHash)) {
                    IrisLogging.warn("Repairing modified or corrupt Iris-managed datapack at " + target.getPath());
                }
            }
            return new InstallPlan(
                    target,
                    pending,
                    backup,
                    pendingRoot,
                    hadTarget,
                    publishRequired,
                    contentChanged,
                    originalHash,
                    desiredHash,
                    originalMarkerHash,
                    desiredMarkerHash,
                    originalIdentity,
                    desiredIdentity,
                    targetRootIdentity,
                    scratchRootIdentity,
                    targetRootFileIdentity,
                    scratchRootFileIdentity,
                    entry.id,
                    entry.url,
                    legacyWorldAuthorization
            );
        } catch (UncheckedIOException e) {
            IOException cause = e.getCause();
            cleanupPreparedInstall(pending, pendingRoot, cause);
            throw cause;
        } catch (IOException e) {
            cleanupPreparedInstall(pending, pendingRoot, e);
            throw e;
        } catch (RuntimeException e) {
            cleanupPreparedInstall(pending, pendingRoot, e);
            throw e;
        }
    }

    private static InstallPlan tryPrepareUnchangedManagedInstall(
            File stagedDir,
            File worldFolder,
            File target,
            Entry entry,
            String stagedHash,
            boolean stripOverrides,
            VerifiedStagingInstall verifiedStagingInstall
    ) throws IOException {
        if (verifiedStagingInstall != null
                || stripOverrides
                || pathExists(new File(stagedDir, OVERRIDES_STRIPPED_MARKER).toPath(),
                "staged datapack override marker")
                || !pathExists(target.toPath(), "datapack install target")) {
            return null;
        }
        if (Files.isSymbolicLink(target.toPath())
                || !Files.isDirectory(target.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Refusing to replace non-directory or symbolic-link datapack " + target.getPath());
        }
        Ownership ownership = readOwnershipOrNull(target);
        if (ownership == null) {
            return null;
        }
        if (!entry.id.equals(ownership.id)) {
            throw new IOException("Datapack ownership mismatch at " + target.getPath());
        }
        if (!ownershipMetadataMatches(ownership, entry, stagedHash)) {
            return null;
        }
        validateInstallTree(target, worldFolder, "Existing datapack install");
        removeFinderMetadata(target);
        String currentHash = directoryHash(target);
        if (!Objects.equals(currentHash, stagedHash)) {
            return null;
        }
        String markerHash = ownershipMarkerFingerprint(target);
        String identity = directoryIdentity(target);
        File pendingRoot = installScratchRoot(worldFolder);
        return new InstallPlan(
                target,
                new File(pendingRoot, entry.id + "-" + UUID.randomUUID()),
                new File(pendingRoot, entry.id + "-backup-" + UUID.randomUUID()),
                pendingRoot,
                true,
                false,
                false,
                currentHash,
                currentHash,
                markerHash,
                markerHash,
                identity,
                identity,
                realDirectoryPath(worldFolder, "datapack target root"),
                "",
                directoryIdentity(worldFolder),
                "",
                entry.id,
                entry.url,
                null
        );
    }

    private static File installScratchRoot(File targetFolder) {
        File parent = targetFolder.getParentFile();
        return new File(parent == null ? targetFolder : parent, ".iris-datapack-install");
    }

    private static void cleanupPreparedInstall(File pending, File pendingRoot, Throwable failure) {
        try {
            deleteInstallScratch(pending, "prepared datapack install");
        } catch (IOException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
        pendingRoot.delete();
    }

    private static boolean ownershipMetadataMatches(Ownership ownership, Entry entry, String contentHash) {
        return ownership.schemaVersion == OWNERSHIP_SCHEMA
                && Objects.equals(ownership.id, entry.id)
                && Objects.equals(ownership.url, entry.url)
                && Objects.equals(ownership.versionId, entry.versionId)
                && Objects.equals(ownership.versionNumber, entry.versionNumber)
                && Objects.equals(ownership.sha1, entry.sha1)
                && Objects.equals(ownership.contentHash, contentHash)
                && copyList(ownership.structureKeys).equals(copyList(entry.structureKeys))
                && copyList(ownership.templateKeys).equals(copyList(entry.templateKeys));
    }

    private static boolean ownershipSourceMatches(Ownership ownership, Entry entry) {
        return ownership.schemaVersion == OWNERSHIP_SCHEMA
                && Objects.equals(ownership.id, entry.id)
                && Objects.equals(ownership.url, entry.url);
    }

    static void publishInstallPlan(InstallPlan plan) throws IOException {
        verifyDirectoryContainerIdentity(
                plan.target().getParentFile(), plan.targetRootIdentity(),
                plan.targetRootFileIdentity(), "datapack target root");
        verifyDirectoryContainerIdentity(
                plan.pendingRoot(), plan.scratchRootIdentity(),
                plan.scratchRootFileIdentity(), "datapack install scratch root");
        verifyDesiredInstallSnapshot(plan.pending(), plan, "prepared datapack install");
        if (plan.hadTarget()) {
            verifyOriginalInstallSnapshot(plan.target(), plan, "original datapack target");
        } else if (pathExists(plan.target().toPath(), "new datapack target")) {
            throw new IOException("Datapack install target was concurrently created at " + plan.target().getPath());
        }
        if (plan.legacyWorldAuthorization() != null) {
            plan.legacyWorldAuthorization().verifyLegacyWorldSnapshot();
        }
        try {
            if (plan.hadTarget()) {
                moveNew(plan.target().toPath(), plan.backup().toPath());
                verifyOriginalInstallSnapshot(plan.backup(), plan, "datapack install backup");
                if (pathExists(plan.target().toPath(), "moved datapack target")) {
                    throw new IOException("Datapack install target reappeared after backup at " + plan.target().getPath());
                }
                forceInstallMoveDirectories(plan);
            }
            moveNew(plan.pending().toPath(), plan.target().toPath());
            verifyDesiredInstallSnapshot(plan.target(), plan, "installed datapack target");
            forceInstallMoveDirectories(plan);
        } catch (IOException publishFailure) {
            if (plan.hadTarget()
                    && Files.exists(plan.backup().toPath(), LinkOption.NOFOLLOW_LINKS)
                    && Files.exists(plan.pending().toPath(), LinkOption.NOFOLLOW_LINKS)
                    && Files.notExists(plan.target().toPath(), LinkOption.NOFOLLOW_LINKS)) {
                try {
                    verifyOriginalInstallSnapshot(plan.backup(), plan, "datapack install backup");
                    moveNew(plan.backup().toPath(), plan.target().toPath());
                    forceInstallMoveDirectories(plan);
                } catch (IOException restoreFailure) {
                    publishFailure.addSuppressed(restoreFailure);
                }
            }
            throw publishFailure;
        }
    }

    private static void forceInstallMoveDirectories(InstallPlan plan) throws IOException {
        forceDirectoryIfSupported(plan.target().getParentFile().toPath());
        forceDirectoryIfSupported(plan.pendingRoot().toPath());
    }

    private static void verifyDirectoryContainerIdentity(
            File directory,
            String expectedRealPath,
            String expectedFileIdentity,
            String purpose
    ) throws IOException {
        Path normalized = directory.toPath().toAbsolutePath().normalize();
        if (Files.isSymbolicLink(normalized)
                || !Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)
                || !Objects.equals(normalized.toRealPath(), Path.of(expectedRealPath))
                || (!expectedFileIdentity.isEmpty()
                && !Objects.equals(directoryIdentity(directory), expectedFileIdentity))) {
            throw new IOException("Changed or unsafe " + purpose + " " + normalized);
        }
    }

    private static void verifyOriginalInstallSnapshot(
            File directory,
            InstallPlan plan,
            String purpose
    ) throws IOException {
        verifyDirectorySnapshot(
                directory,
                plan.target().getParentFile(),
                plan.originalHash(),
                plan.originalMarkerHash(),
                plan.originalIdentity(),
                purpose
        );
    }

    private static void verifyDesiredInstallSnapshot(
            File directory,
            InstallPlan plan,
            String purpose
    ) throws IOException {
        verifyDirectorySnapshot(
                directory,
                plan.target().getParentFile(),
                plan.desiredHash(),
                plan.desiredMarkerHash(),
                plan.desiredIdentity(),
                purpose
        );
        Ownership ownership = readOwnership(directory);
        if (!Objects.equals(ownership.id, plan.id())
                || !Objects.equals(ownership.url, plan.url())
                || !Objects.equals(ownership.contentHash, plan.desiredHash())) {
            throw new IOException("Datapack ownership changed in " + purpose + " at " + directory.getPath());
        }
    }

    private static void verifyDirectorySnapshot(
            File directory,
            File storeAnchor,
            String expectedHash,
            String expectedMarkerHash,
            String expectedIdentity,
            String purpose
    ) throws IOException {
        if (!pathExists(directory.toPath(), purpose)
                || Files.isSymbolicLink(directory.toPath())
                || !Files.isDirectory(directory.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Missing or unsafe " + purpose + " at " + directory.getPath());
        }
        validateInstallTree(directory, storeAnchor, purpose);
        if (Files.exists(new File(directory, OWNERSHIP_MARKER).toPath(), LinkOption.NOFOLLOW_LINKS)) {
            removeFinderMetadata(directory);
        }
        if (!expectedIdentity.isEmpty()
                && !Objects.equals(directoryIdentity(directory), expectedIdentity)) {
            throw new IOException("Datapack directory identity changed in " + purpose + " at " + directory.getPath());
        }
        if (!Objects.equals(expectedHash, directoryHash(directory))
                || !Objects.equals(expectedMarkerHash, ownershipMarkerFingerprint(directory))) {
            throw new IOException("Datapack content changed in " + purpose + " at " + directory.getPath());
        }
    }

    private static void cleanupInstallPlan(InstallPlan plan, boolean committed) throws IOException {
        deleteInstallScratch(plan.pending, "datapack install pending directory");
        if (committed) {
            deleteInstallScratch(plan.backup, "datapack install backup");
        }
        if (Files.exists(plan.backup.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Preserving prior datapack backup after incomplete install: "
                    + plan.backup.getPath());
        }
        plan.pendingRoot.delete();
    }

    static void deleteInstallScratch(File scratch, String purpose) throws IOException {
        Path scratchPath = scratch.toPath();
        File parent = Objects.requireNonNull(scratch.getParentFile(), "datapack scratch parent");
        for (int attempt = 0; attempt < MAX_SCRATCH_DELETE_ATTEMPTS; attempt++) {
            if (Files.notExists(scratchPath, LinkOption.NOFOLLOW_LINKS)) {
                return;
            }
            if (!Files.exists(scratchPath, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(scratchPath)
                    || !Files.isDirectory(scratchPath, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Refusing to remove unsafe " + purpose + " " + scratch.getPath());
            }
            validateInstallTree(scratch, parent, purpose);
            removeFinderMetadata(scratch);
            IO.delete(scratch);
            if (Files.notExists(scratchPath, LinkOption.NOFOLLOW_LINKS)) {
                return;
            }
        }
        throw new IOException("Could not remove " + purpose + " " + scratch.getPath());
    }

    private static boolean resolveStripOverrides() {
        try (Stream<IrisData> stream = ServerConfigurator.allPacks()) {
            return stream.anyMatch(DatapackIngestService::packDisablesOverrides);
        }
    }

    private static boolean packDisablesOverrides(IrisData data) {
        if (data == null || data.getDimensionLoader() == null) {
            return false;
        }
        for (IrisDimension dimension : data.getDimensionLoader().loadAll(data.getDimensionLoader().getPossibleKeys())) {
            if (dimension == null) {
                continue;
            }
            IrisImportedStructureControl control = dimension.getImportedStructures();
            if (control != null && !control.isDatapackOverrides()) {
                return true;
            }
        }
        return false;
    }

    private static void stripVanillaStructureOverrides(File datapackRoot) throws IOException {
        stripVanillaStructureOverrides(datapackRoot, IO::delete);
    }

    static void stripVanillaStructureOverrides(
            File datapackRoot,
            DirectoryDeleter deleter
    ) throws IOException {
        File minecraftData = new File(new File(datapackRoot, "data"), "minecraft");
        if (!minecraftData.isDirectory()) {
            return;
        }
        String[] relativeTrees = {
                "worldgen" + File.separator + "structure_set",
                "worldgen" + File.separator + "structure",
                "worldgen" + File.separator + "template_pool",
                "structure"
        };
        for (String tree : relativeTrees) {
            File dir = new File(minecraftData, tree);
            if (dir.exists()) {
                deleter.delete(dir);
                if (Files.exists(dir.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Could not remove vanilla structure override tree " + dir.getPath());
                }
            }
        }
    }

    private static void writeMarker(File marker) throws IOException {
        Files.writeString(marker.toPath(), "stripped", StandardCharsets.UTF_8);
    }

    static void validatePackMetadata(File datapackRoot) throws IOException {
        File metadata = new File(datapackRoot, "pack.mcmeta");
        if (!metadata.isFile() || Files.isSymbolicLink(metadata.toPath())) {
            throw new IOException("Datapack is missing a regular pack.mcmeta");
        }
        if (metadata.length() > MAX_METADATA_BYTES) {
            throw new IOException("Datapack pack.mcmeta exceeds 1 MiB");
        }
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(readBoundedUtf8(
                    metadata.toPath(), MAX_METADATA_BYTES, "Datapack pack.mcmeta"));
        } catch (RuntimeException e) {
            throw new IOException("Datapack pack.mcmeta is not valid JSON", e);
        }
        if (!parsed.isJsonObject()) {
            throw new IOException("Datapack pack.mcmeta root must be an object");
        }
        JsonObject root = parsed.getAsJsonObject();
        if (!root.has("pack") || !root.get("pack").isJsonObject()) {
            throw new IOException("Datapack pack.mcmeta must contain a pack object");
        }
        JsonObject pack = root.getAsJsonObject("pack");
        boolean hasPackFormat = pack.has("pack_format") && pack.get("pack_format").isJsonPrimitive()
                && pack.getAsJsonPrimitive("pack_format").isNumber();
        boolean hasRange = validFormatValue(pack.get("min_format")) && validFormatValue(pack.get("max_format"));
        if (!hasPackFormat && !hasRange) {
            throw new IOException("Datapack pack.mcmeta must declare numeric pack_format or valid min_format/max_format");
        }
        if (!pack.has("description") || pack.get("description").isJsonNull()) {
            throw new IOException("Datapack pack.mcmeta must declare a description");
        }
        File data = new File(datapackRoot, "data");
        if (data.exists() && (!data.isDirectory() || Files.isSymbolicLink(data.toPath()))) {
            throw new IOException("Datapack data path is not a regular directory");
        }
    }

    private static boolean validFormatValue(JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return false;
        }
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            return true;
        }
        if (!value.isJsonArray() || value.getAsJsonArray().size() != 2) {
            return false;
        }
        for (JsonElement component : value.getAsJsonArray()) {
            if (!component.isJsonPrimitive() || !component.getAsJsonPrimitive().isNumber()) {
                return false;
            }
        }
        return true;
    }

    static void writeOwnership(File directory, Entry entry) throws IOException {
        writeOwnership(directory, entry, directoryHash(directory));
    }

    private static void writeOwnership(File directory, Entry entry, String contentHash) throws IOException {
        if (!isValidManagedId(entry.id) || entry.url == null || entry.url.isBlank()) {
            throw new IOException("Invalid Iris datapack ownership identity for " + directory.getPath());
        }
        if (contentHash == null || contentHash.isBlank()) {
            throw new IOException("Missing Iris datapack ownership content hash for " + directory.getPath());
        }
        Ownership ownership = new Ownership(
                OWNERSHIP_SCHEMA,
                entry.id,
                entry.url,
                entry.versionId,
                entry.versionNumber,
                entry.sha1,
                contentHash,
                copyList(entry.structureKeys),
                copyList(entry.templateKeys)
        );
        Path marker = new File(directory, OWNERSHIP_MARKER).toPath();
        Path temporary = Files.createTempFile(directory.toPath(), OWNERSHIP_MARKER, ".tmp");
        try {
            Files.writeString(
                    temporary,
                    GSON.toJson(ownership),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            move(temporary, marker);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static Ownership readOwnership(File directory) throws IOException {
        Ownership ownership = readOwnershipOrNull(directory);
        if (ownership == null) {
            throw new IOException("Missing Iris datapack ownership marker in " + directory.getPath());
        }
        return ownership;
    }

    private static Ownership readOwnershipOrNull(File directory) throws IOException {
        File marker = new File(directory, OWNERSHIP_MARKER);
        Path markerPath = marker.toPath();
        if (Files.notExists(markerPath, LinkOption.NOFOLLOW_LINKS)) {
            return null;
        }
        if (!Files.exists(markerPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Cannot determine Iris datapack ownership marker state in " + directory.getPath());
        }
        if (Files.isSymbolicLink(markerPath)
                || !Files.isRegularFile(markerPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Invalid Iris datapack ownership marker in " + directory.getPath());
        }
        if (marker.length() > MAX_OWNERSHIP_BYTES) {
            throw new IOException("Oversized Iris datapack ownership marker in " + directory.getPath());
        }
        try {
            Ownership ownership = GSON.fromJson(readBoundedUtf8(
                    marker.toPath(), MAX_OWNERSHIP_BYTES, "Iris datapack ownership marker"), Ownership.class);
            if (ownership == null || ownership.schemaVersion != OWNERSHIP_SCHEMA
                    || !isValidManagedId(ownership.id) || ownership.url == null || ownership.url.isBlank()
                    || ownership.contentHash == null || ownership.contentHash.isBlank()) {
                throw new IOException("Invalid Iris datapack ownership marker in " + directory.getPath());
            }
            return ownership;
        } catch (RuntimeException e) {
            throw new IOException("Invalid Iris datapack ownership marker in " + directory.getPath(), e);
        }
    }

    static String ownershipMarkerFingerprint(File directory) throws IOException {
        Path marker = new File(directory, OWNERSHIP_MARKER).toPath();
        if (Files.notExists(marker, LinkOption.NOFOLLOW_LINKS)) {
            return "absent";
        }
        if (!Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Cannot determine Iris datapack ownership marker state in " + directory.getPath());
        }
        if (Files.isSymbolicLink(marker)
                || !Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Invalid Iris datapack ownership marker in " + directory.getPath());
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return "sha256:" + hex(digest.digest(readBoundedBytes(
                    marker, MAX_OWNERSHIP_BYTES, "Iris datapack ownership marker")));
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 algorithm unavailable", e);
        }
    }

    static boolean isUsableStaging(File stagedDir, Entry entry) {
        return inspectUsableStaging(stagedDir, entry).usable();
    }

    private static StagingInspection inspectUsableStaging(File stagedDir, Entry entry) {
        try {
            validateManagedDirectory(stagedDir, entry.id);
            Ownership ownership = readOwnership(stagedDir);
            String contentHash = directoryHash(stagedDir);
            if (!ownershipSourceMatches(ownership, entry)
                    || !Objects.equals(ownership.versionId, entry.versionId)
                    || !Objects.equals(ownership.versionNumber, entry.versionNumber)
                    || !Objects.equals(ownership.sha1, entry.sha1)
                    || !Objects.equals(ownership.contentHash, contentHash)) {
                return new StagingInspection(false, false, false);
            }
            PackResources resources = scanPackResources(stagedDir);
            List<String> previousStructureKeys = copyList(entry.structureKeys);
            List<String> previousTemplateKeys = copyList(entry.templateKeys);
            boolean ownershipCorrected = false;
            if (!copyList(resources.structureKeys).equals(copyList(ownership.structureKeys))
                    || !copyList(resources.templateKeys).equals(copyList(ownership.templateKeys))) {
                Entry corrected = copyEntry(entry);
                corrected.structureKeys = resources.structureKeys;
                corrected.templateKeys = resources.templateKeys;
                writeOwnership(stagedDir, corrected);
                ownershipCorrected = true;
            }
            entry.structureKeys = resources.structureKeys;
            entry.templateKeys = resources.templateKeys;
            boolean manifestChanged = !previousStructureKeys.equals(copyList(entry.structureKeys))
                    || !previousTemplateKeys.equals(copyList(entry.templateKeys));
            return new StagingInspection(true, ownershipCorrected, manifestChanged);
        } catch (IOException e) {
            IrisLogging.warn("Ignoring unusable Iris datapack staging at " + stagedDir.getPath() + ": " + e.getMessage());
            return new StagingInspection(false, false, false);
        }
    }

    private static void validateManagedDirectory(File directory, String id) throws IOException {
        Path path = directory.toPath();
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Missing datapack directory " + directory.getPath());
        }
        if (Files.isSymbolicLink(path)) {
            throw new IOException("Refusing symbolic-link datapack directory " + directory.getPath());
        }
        validatePackMetadata(directory);
        rejectSymbolicLinks(directory);
        Ownership ownership = readOwnershipOrNull(directory);
        if (ownership == null) {
            throw new IOException("Datapack is not Iris-managed: " + directory.getPath());
        }
        if (!id.equals(ownership.id)) {
            throw new IOException("Datapack ownership mismatch at " + directory.getPath());
        }
        removeFinderMetadata(directory);
    }

    private static void rejectSymbolicLinks(File root) throws IOException {
        try (Stream<Path> paths = Files.walk(root.toPath())) {
            Path symbolicLink = paths.filter(Files::isSymbolicLink).findFirst().orElse(null);
            if (symbolicLink != null) {
                throw new IOException("Datapack contains a symbolic link: " + symbolicLink);
            }
        }
    }

    private static boolean sameDatapackVolume(
            Path root,
            FileStore rootStore,
            Path entry
    ) throws IOException {
        return sameScratchVolume(root, rootStore, entry, Files.getFileStore(entry));
    }

    private static String directoryHash(File root) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            Path rootPath = root.toPath().toAbsolutePath().normalize();
            Path rootMarker = rootPath.resolve(OWNERSHIP_MARKER);
            FileStore rootStore = Files.getFileStore(rootPath);
            List<Path> entries = new ArrayList<>();
            try (Stream<Path> paths = Files.walk(rootPath)) {
                Iterator<Path> iterator = paths.iterator();
                int pathCount = 0;
                while (iterator.hasNext()) {
                    Path path = iterator.next();
                    if (path.equals(rootPath) || path.equals(rootMarker)) {
                        continue;
                    }
                    if (isFinderMetadata(path)) {
                        if (Files.isSymbolicLink(path)
                                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                            throw new IOException("Suspicious Finder metadata in datapack: " + path);
                        }
                        continue;
                    }
                    pathCount++;
                    if (pathCount > MAX_MANAGED_PATHS) {
                        throw new IOException("Datapack contains more than " + MAX_MANAGED_PATHS + " paths");
                    }
                    if (Files.isSymbolicLink(path)) {
                        throw new IOException("Datapack contains a symbolic link: " + path);
                    }
                    if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                            && !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                        throw new IOException("Datapack contains an unsupported filesystem entry: " + path);
                    }
                    entries.add(path);
                }
            }
            entries.sort(Comparator.comparing(path -> rootPath.relativize(path).toString()));
            byte[] buffer = new byte[HASH_BUFFER_BYTES];
            long totalBytes = 0;
            for (Path entry : entries) {
                String relative = rootPath.relativize(entry).toString().replace(File.separatorChar, '/');
                byte[] relativeBytes = relative.getBytes(StandardCharsets.UTF_8);
                BasicFileAttributes attributes = Files.readAttributes(
                        entry,
                        BasicFileAttributes.class,
                        LinkOption.NOFOLLOW_LINKS
                );
                if (attributes.isSymbolicLink()
                        || attributes.isOther()
                        || !attributes.isDirectory() && !attributes.isRegularFile()) {
                    throw new IOException("Datapack entry changed while hashing: " + relative);
                }
                if (!sameScratchVolume(rootPath, rootStore, entry, Files.getFileStore(entry))) {
                    throw new IOException("Datapack entry crosses a filesystem boundary: " + entry);
                }
                boolean directory = attributes.isDirectory();
                digest.update((byte) (directory ? 1 : 2));
                updateDigestInt(digest, relativeBytes.length);
                digest.update(relativeBytes);
                if (!directory) {
                    long expectedBytes = attributes.size();
                    if (expectedBytes > MAX_ENTRY_BYTES) {
                        throw new IOException("Datapack file exceeds " + MAX_ENTRY_BYTES + " bytes: " + relative);
                    }
                    totalBytes += expectedBytes;
                    if (totalBytes > MAX_EXPANDED_BYTES) {
                        throw new IOException("Datapack contents exceed " + MAX_EXPANDED_BYTES + " bytes");
                    }
                    updateDigestLong(digest, expectedBytes);
                    long entryBytes = 0;
                    try (InputStream input = Files.newInputStream(
                            entry,
                            StandardOpenOption.READ,
                            LinkOption.NOFOLLOW_LINKS
                    )) {
                        int length;
                        while ((length = input.read(buffer)) > 0) {
                            entryBytes += length;
                            if (entryBytes > expectedBytes) {
                                throw new IOException("Datapack file changed while hashing: " + relative);
                            }
                            digest.update(buffer, 0, length);
                        }
                    }
                    if (entryBytes != expectedBytes) {
                        throw new IOException("Datapack file changed while hashing: " + relative);
                    }
                }
            }
            return hex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 algorithm unavailable", e);
        }
    }

    private static void removeFinderMetadata(File root) throws IOException {
        List<Path> entries;
        try (Stream<Path> paths = Files.walk(root.toPath())) {
            entries = paths.limit(MAX_MANAGED_PATHS + 1L).toList();
        }
        if (entries.size() > MAX_MANAGED_PATHS) {
            throw new IOException("Datapack contains more than " + MAX_MANAGED_PATHS + " paths");
        }
        for (Path path : entries) {
            if (!isFinderMetadata(path)) {
                continue;
            }
            if (Files.isSymbolicLink(path)
                    || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Suspicious Finder metadata in datapack: " + path);
            }
            Files.delete(path);
        }
    }

    private static boolean isFinderMetadata(Path path) {
        Path fileName = path.getFileName();
        return fileName != null && FINDER_METADATA.equals(fileName.toString());
    }

    private static PackResources scanPackResources(File root) throws IOException {
        TreeSet<String> structureKeys = new TreeSet<>();
        TreeSet<String> structureSetKeys = new TreeSet<>();
        TreeSet<String> templateKeys = new TreeSet<>();
        Path dataRoot = new File(root, "data").toPath();
        if (!Files.isDirectory(dataRoot, LinkOption.NOFOLLOW_LINKS)) {
            return new PackResources(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        }
        try (Stream<Path> paths = Files.walk(dataRoot)) {
            for (Path path : paths.filter(file -> Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)).toList()) {
                Path relative = dataRoot.relativize(path);
                if (relative.getNameCount() < 3) {
                    continue;
                }
                String namespace = relative.getName(0).toString().toLowerCase(Locale.ROOT);
                String normalized = relative.subpath(1, relative.getNameCount()).toString().replace(File.separatorChar, '/');
                addResourceKey(structureKeys, namespace, normalized, "worldgen/structure/", ".json");
                addResourceKey(structureKeys, namespace, normalized, "worldgen/structures/", ".json");
                addResourceKey(structureSetKeys, namespace, normalized, "worldgen/structure_set/", ".json");
                addResourceKey(structureSetKeys, namespace, normalized, "worldgen/structure_sets/", ".json");
                addResourceKey(templateKeys, namespace, normalized, "structure/", ".nbt");
                addResourceKey(templateKeys, namespace, normalized, "structures/", ".nbt");
            }
        }
        return new PackResources(
                new ArrayList<>(structureKeys),
                new ArrayList<>(structureSetKeys),
                new ArrayList<>(templateKeys));
    }

    private static void addResourceKey(Set<String> keys, String namespace, String path, String prefix, String suffix) {
        if (!path.startsWith(prefix) || !path.endsWith(suffix)) {
            return;
        }
        String resourcePath = path.substring(prefix.length(), path.length() - suffix.length());
        if (!resourcePath.isBlank()) {
            keys.add(namespace + ":" + resourcePath);
        }
    }

    static boolean deleteOwnedDirectory(File directory, String id) throws IOException {
        Path path = directory.toPath();
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Refusing non-directory or symbolic-link target " + directory.getPath());
        }
        Ownership ownership = readOwnership(directory);
        if (!id.equals(ownership.id)) {
            throw new IOException("Ownership marker belongs to '" + ownership.id + "'");
        }
        removeFinderMetadata(directory);
        if (!Objects.equals(ownership.contentHash, directoryHash(directory))) {
            throw new IOException("Refusing to delete modified or corrupt Iris-managed datapack " + directory.getPath());
        }
        File parent = Objects.requireNonNull(directory.getParentFile(), "managed datapack parent");
        validateInstallTree(directory, parent, "Managed datapack deletion");
        IO.delete(directory);
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Could not delete " + directory.getPath());
        }
        return true;
    }

    private static boolean autoImportDatapackStructures() {
        TRANSACTION_LOCK.lock();
        try {
            return autoImportDatapackStructuresLocked();
        } finally {
            TRANSACTION_LOCK.unlock();
        }
    }

    private static boolean autoImportDatapackStructuresLocked() {
        boolean autoImportEnabled = IrisSettings.get().getGeneral().autoImportDatapackStructures;
        File root = IrisPlatforms.get().dataFolder("datapacks");
        boolean recovered;
        try {
            recovered = recoverTransactions(root, ServerConfigurator.getDatapacksFolder());
        } catch (IOException e) {
            IrisLogging.reportError("Automatic datapack structure import blocked by incomplete transaction recovery.", e);
            return false;
        }
        Manifest manifest = readManifest(root);
        if (manifest.entries.isEmpty()) {
            return recovered;
        }
        Map<String, Entry> manifestEntriesByUrl = new HashMap<>();
        for (Entry entry : manifest.entries) {
            if (entry.url != null) {
                manifestEntriesByUrl.put(entry.url, entry);
            }
        }

        List<IrisData> packs;
        try (Stream<IrisData> stream = ServerConfigurator.allPacks()) {
            packs = stream.filter(Objects::nonNull).toList();
        }
        if (!autoImportEnabled && !hasRemovedImportState(packs, manifest.entries)) {
            return recovered;
        }

        Map<String, Entry> entriesByUrl = new HashMap<>();
        File stagingRoot = new File(root, "staging");
        boolean stagingStateChanged = false;
        boolean manifestStagingMetadataChanged = false;
        for (Entry entry : manifest.entries) {
            if (entry.url == null) {
                continue;
            }
            StagingInspection inspection = inspectUsableStaging(new File(stagingRoot, entry.id), entry);
            stagingStateChanged |= inspection.ownershipCorrected();
            manifestStagingMetadataChanged |= inspection.manifestChanged();
            if (inspection.usable()) {
                entriesByUrl.put(entry.url, entry);
            }
        }

        int attemptedPacks = 0;
        int completedPacks = 0;
        int cleanupTargets = 0;
        Set<String> completedUrls = new HashSet<>();
        Set<String> failedUrls = new HashSet<>();
        for (IrisData data : packs) {
            Set<String> configured = configuredImports(data);
            String targetId = data.getDataFolder().toPath().toAbsolutePath().normalize().toString();
            for (Entry entry : manifest.entries) {
                if (!configured.contains(entry.url) && hasImportState(entry, targetId)) {
                    cleanupTargets++;
                }
            }
            if (!cleanupRemovedImports(data, targetId, configured, manifest.entries, manifestEntriesByUrl)) {
                for (Entry entry : manifest.entries) {
                    if (!configured.contains(entry.url) && hasImportState(entry, targetId)) {
                        failedUrls.add(entry.url);
                    }
                }
            }
            if (!autoImportEnabled) {
                continue;
            }

            Set<String> pendingUrls = new HashSet<>();
            for (String url : configured) {
                Entry entry = entriesByUrl.get(url);
                if (entry != null && importPending(entry, targetId)) {
                    pendingUrls.add(url);
                }
            }
            if (pendingUrls.isEmpty()) {
                continue;
            }
            for (String pendingUrl : pendingUrls) {
                Entry entry = entriesByUrl.get(pendingUrl);
                prepareImportRecoveryInventory(entry, targetId);
            }
            try {
                writeManifestChecked(root, manifest);
            } catch (IOException e) {
                failedUrls.addAll(pendingUrls);
                IrisLogging.reportError("Datapack structure import for pack '"
                        + data.getDataFolder().getPath()
                        + "' was blocked because its recovery inventory could not be persisted.", e);
                continue;
            }
            Set<String> structureKeys = new TreeSet<>();
            Set<String> templateKeys = new TreeSet<>();
            for (Entry entry : manifest.entries) {
                if (!configured.contains(entry.url)) {
                    continue;
                }
                structureKeys.addAll(copyList(entry.structureKeys));
                templateKeys.addAll(copyList(entry.templateKeys));
            }
            attemptedPacks++;
            try {
                BulkStructureImporter.Report report = BulkStructureImporter.importManagedDatapackStructures(
                        data,
                        StructureImporter.Mode.OVERWRITE,
                        BukkitPlatform.console(),
                        structureKeys,
                        templateKeys
                );
                Set<String> successfulPendingUrls = new HashSet<>(pendingUrls);
                Set<String> incompleteUrls = new HashSet<>();
                for (String pendingUrl : pendingUrls) {
                    Entry entry = entriesByUrl.get(pendingUrl);
                    Map<String, String> desired = importBundleInventory(entry);
                    if (!report.successfulBundles().entrySet().containsAll(desired.entrySet())) {
                        successfulPendingUrls.remove(pendingUrl);
                        incompleteUrls.add(pendingUrl);
                        failedUrls.add(pendingUrl);
                    }
                }
                if (report.failed() > 0 && incompleteUrls.isEmpty()) {
                    successfulPendingUrls.clear();
                    incompleteUrls.addAll(pendingUrls);
                    failedUrls.addAll(pendingUrls);
                }
                if (!incompleteUrls.isEmpty()) {
                    boolean reconciled = reconcileFailedImportInventories(
                            root, manifest, data, targetId, incompleteUrls, entriesByUrl);
                    if (report.retryRequired() || !reconciled) {
                        IrisLogging.error("Datapack structure import for pack '%s' reported %d incomplete source(s) and remains pending because a retryable runtime failure occurred.",
                                data.getDataFolder().getPath(), incompleteUrls.size());
                    } else if (recordDeterministicImportAttempts(
                            root, manifest, targetId, incompleteUrls, entriesByUrl)) {
                        IrisLogging.warn("Datapack structure import for pack '"
                                + data.getDataFolder().getPath() + "' left " + incompleteUrls.size()
                                + " source(s) incomplete after deterministic validation failures. Iris will retain the partial editable imports without retrying until the datapack source, importer format, or target pack changes.");
                    }
                }
                Map<String, String> sharedBundles = desiredBundles(configured, entriesByUrl);
                boolean packCompleted = false;
                for (String pendingUrl : successfulPendingUrls) {
                    Entry entry = entriesByUrl.get(pendingUrl);
                    Map<String, String> desired = importBundleInventory(entry);
                    Map<String, String> previous = entry.importedBundles.getOrDefault(targetId, Map.of());
                    Map<String, String> stale = new TreeMap<>(previous);
                    stale.keySet().removeAll(desired.keySet());
                    stale.keySet().removeAll(sharedBundles.keySet());
                    Map<String, String> remaining = cleanupImportedBundles(data, stale);
                    if (!remaining.isEmpty()) {
                        failedUrls.add(pendingUrl);
                        continue;
                    }
                    entry.importedBundles.put(targetId, desired);
                    recordSuccessfulImport(entry, targetId);
                    completedUrls.add(pendingUrl);
                    packCompleted = true;
                }
                if (packCompleted) {
                    completedPacks++;
                }
            } catch (RuntimeException e) {
                failedUrls.addAll(pendingUrls);
                IrisLogging.reportError("Datapack structure import failed for pack '"
                        + data.getDataFolder().getPath() + "'; the manifest remains pending for retry.", e);
                reconcileFailedImportInventories(root, manifest, data, targetId, pendingUrls, entriesByUrl);
            }
        }

        for (Entry entry : manifest.entries) {
            if (failedUrls.contains(entry.url)) {
                entry.structuresImported = false;
            } else if (completedUrls.contains(entry.url)) {
                entry.structuresImported = true;
            }
        }
        if (attemptedPacks == 0 && cleanupTargets == 0) {
            if (manifestStagingMetadataChanged) {
                writeManifest(root, manifest);
            }
            return recovered || stagingStateChanged || manifestStagingMetadataChanged;
        }
        writeManifest(root, manifest);
        if (attemptedPacks == 0) {
            IrisLogging.info("Datapack editable-import cleanup reconciled " + cleanupTargets + " removed source target(s).");
            return true;
        }
        IrisLogging.info("Datapack structure import refreshed " + completedUrls.size() + " source(s) across "
                + completedPacks + "/" + attemptedPacks
                + " pack(s). Reference the imported keys from a 'structures' placement to position them manually.");
        return true;
    }

    private static boolean hasRemovedImportState(List<IrisData> packs, List<Entry> entries) {
        for (IrisData data : packs) {
            Set<String> configured = configuredImports(data);
            String targetId = data.getDataFolder().toPath().toAbsolutePath().normalize().toString();
            for (Entry entry : entries) {
                if (!configured.contains(entry.url) && hasImportState(entry, targetId)) {
                    return true;
                }
            }
        }
        return false;
    }

    static String importRevision(Entry entry) {
        return importRevision(entry, STRUCTURE_IMPORT_FORMAT_REVISION);
    }

    static String importRevision(Entry entry, int importerFormatRevision) {
        return "v" + importerFormatRevision + ":" + safe(entry.versionId) + ":" + safe(entry.sha1);
    }

    static boolean importPending(Entry entry, String targetId) {
        String revision = importRevision(entry);
        return !revision.equals(entry.importedTargets.get(targetId))
                && !revision.equals(entry.importAttempts.get(targetId));
    }

    static void recordDeterministicImportAttempt(Entry entry, String targetId) {
        entry.importedTargets.remove(targetId);
        entry.importAttempts.put(targetId, importRevision(entry));
        entry.structuresImported = false;
    }

    static void recordSuccessfulImport(Entry entry, String targetId) {
        entry.importedTargets.put(targetId, importRevision(entry));
        entry.importAttempts.remove(targetId);
    }

    static void prepareImportRecoveryInventory(Entry entry, String targetId) {
        Map<String, String> desired = importBundleInventory(entry);
        Map<String, String> recovery = new TreeMap<>(desired);
        recovery.putAll(entry.importedBundles.getOrDefault(targetId, Map.of()));
        entry.importedBundles.put(targetId, recovery);
        entry.importedTargets.remove(targetId);
        entry.importAttempts.remove(targetId);
        entry.structuresImported = false;
    }

    private static boolean reconcileFailedImportInventories(
            File root,
            Manifest manifest,
            IrisData data,
            String targetId,
            Set<String> pendingUrls,
            Map<String, Entry> entriesByUrl
    ) {
        boolean reconciled = true;
        for (String pendingUrl : pendingUrls) {
            Entry entry = entriesByUrl.get(pendingUrl);
            try {
                reconcileFailedImportInventory(data, entry, targetId);
            } catch (IOException | RuntimeException e) {
                reconciled = false;
                IrisLogging.reportError("Could not reconcile partial editable structure imports for '"
                        + pendingUrl + "' in pack '" + data.getDataFolder().getPath()
                        + "'; the conservative recovery inventory remains pending.", e);
            }
        }
        try {
            writeManifestChecked(root, manifest);
        } catch (IOException e) {
            reconciled = false;
            IrisLogging.reportError("Could not persist reconciled partial editable structure imports for pack '"
                    + data.getDataFolder().getPath() + "'; the earlier recovery inventory remains durable.", e);
        }
        return reconciled;
    }

    private static boolean recordDeterministicImportAttempts(
            File root,
            Manifest manifest,
            String targetId,
            Set<String> incompleteUrls,
            Map<String, Entry> entriesByUrl
    ) {
        for (String incompleteUrl : incompleteUrls) {
            recordDeterministicImportAttempt(entriesByUrl.get(incompleteUrl), targetId);
        }
        try {
            writeManifestChecked(root, manifest);
            return true;
        } catch (IOException e) {
            for (String incompleteUrl : incompleteUrls) {
                entriesByUrl.get(incompleteUrl).importAttempts.remove(targetId);
            }
            IrisLogging.reportError("Could not persist deterministic editable structure import attempts; the sources remain pending for retry.", e);
            return false;
        }
    }

    private static void reconcileFailedImportInventory(
            IrisData data,
            Entry entry,
            String targetId
    ) throws IOException {
        Map<String, Set<String>> claims = importBundleClaims(entry, targetId);
        Map<String, String> reconciled = new TreeMap<>();
        StructureTransactionWriter writer = new StructureTransactionWriter(data.getDataFolder().toPath());
        for (Map.Entry<String, Set<String>> bundle : claims.entrySet()) {
            StructureKey targetKey;
            try {
                targetKey = StructureKey.parse(bundle.getKey());
            } catch (RuntimeException e) {
                throw new IOException("Invalid editable structure target key '" + bundle.getKey() + "'", e);
            }
            Optional<StructureSource> source = writer.ownedSource(targetKey);
            if (source.isPresent() && sourceClaimsContain(bundle.getValue(), source.get())) {
                reconciled.put(bundle.getKey(), source.get().key().value());
            }
        }
        if (reconciled.isEmpty()) {
            entry.importedBundles.remove(targetId);
        } else {
            entry.importedBundles.put(targetId, reconciled);
        }
        entry.importedTargets.remove(targetId);
        entry.importAttempts.remove(targetId);
        entry.structuresImported = false;
    }

    static boolean cleanupRemovedImports(
            IrisData data,
            String targetId,
            Set<String> configured,
            List<Entry> entries,
            Map<String, Entry> entriesByUrl
    ) {
        boolean successful = true;
        Map<String, String> retainedBundles = desiredBundles(configured, entriesByUrl);
        for (Entry entry : entries) {
            if (configured.contains(entry.url)) {
                continue;
            }
            Map<String, String> inventory = entry.importedBundles.get(targetId);
            if (inventory == null && !hasImportState(entry, targetId)) {
                continue;
            }
            Map<String, String> resolvedInventory = inventory == null ? Map.of() : inventory;
            for (Entry retainedEntry : entries) {
                if (configured.contains(retainedEntry.url)) {
                    retainedEntry.importedTargets.remove(targetId);
                    retainedEntry.importAttempts.remove(targetId);
                    retainedEntry.structuresImported = false;
                }
            }
            Map<String, String> removable = new TreeMap<>(resolvedInventory);
            removable.keySet().removeAll(retainedBundles.keySet());
            Map<String, String> remaining = cleanupImportedBundles(data, removable);
            if (!remaining.isEmpty()) {
                Map<String, String> retained = new TreeMap<>();
                for (Map.Entry<String, String> bundle : resolvedInventory.entrySet()) {
                    if (retainedBundles.containsKey(bundle.getKey()) || remaining.containsKey(bundle.getKey())) {
                        retained.put(bundle.getKey(), bundle.getValue());
                    }
                }
                entry.importedBundles.put(targetId, retained);
                entry.importedTargets.remove(targetId);
                entry.importAttempts.remove(targetId);
                entry.structuresImported = false;
                successful = false;
                continue;
            }
            entry.importedBundles.remove(targetId);
            entry.importedTargets.remove(targetId);
            entry.importAttempts.remove(targetId);
        }
        return successful;
    }

    private static boolean hasImportState(Entry entry, String targetId) {
        return entry.importedBundles.containsKey(targetId)
                || entry.importedTargets.containsKey(targetId)
                || entry.importAttempts.containsKey(targetId);
    }

    private static Map<String, String> cleanupImportedBundles(IrisData data, Map<String, String> inventory) {
        Map<String, String> remaining = new TreeMap<>();
        StructureTransactionWriter writer = new StructureTransactionWriter(data.getDataFolder().toPath());
        boolean removed = false;
        for (Map.Entry<String, String> bundle : inventory.entrySet()) {
            StructureKey sourceKey;
            try {
                sourceKey = StructureKey.parse(bundle.getValue());
                StructureSource.Kind sourceKind = sourceKey.namespace().equals("minecraft")
                        ? StructureSource.Kind.VANILLA : StructureSource.Kind.DATAPACK;
                removed |= writer.removeManagedDatapackOwned(
                        StructureKey.parse(bundle.getKey()),
                        sourceKind,
                        sourceKey
                );
            } catch (IOException | RuntimeException e) {
                remaining.put(bundle.getKey(), bundle.getValue());
                IrisLogging.reportError("Preserving imported structure bundle '" + bundle.getKey()
                        + "' in pack '" + data.getDataFolder().getPath()
                        + "' because ownership-safe cleanup failed.", e);
            }
        }
        if (removed) {
            data.invalidateStructureResources();
        }
        return remaining;
    }

    private static Map<String, String> desiredBundles(Set<String> configured, Map<String, Entry> entriesByUrl) {
        Map<String, String> bundles = new TreeMap<>();
        for (String url : configured) {
            Entry entry = entriesByUrl.get(url);
            if (entry != null) {
                bundles.putAll(importBundleInventory(entry));
            }
        }
        return bundles;
    }

    static Map<String, String> importBundleInventory(Entry entry) {
        Map<String, String> bundles = new TreeMap<>();
        for (String structureKey : copyList(entry.structureKeys)) {
            bundles.put("iris:" + StructureImporter.deriveName(structureKey), structureKey);
        }
        for (String templateKey : copyList(entry.templateKeys)) {
            bundles.put("iris:" + BulkStructureImporter.templateNameFor(templateKey), templateKey);
        }
        return bundles;
    }

    private static void flattenIfWrapped(File dir) throws IOException {
        if (new File(dir, "pack.mcmeta").isFile()) {
            return;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        File singleDir = null;
        int dirCount = 0;
        int fileCount = 0;
        for (File child : children) {
            if (child.isDirectory()) {
                dirCount++;
                singleDir = child;
            } else {
                fileCount++;
            }
        }
        if (dirCount != 1 || fileCount != 0 || singleDir == null || !new File(singleDir, "pack.mcmeta").isFile()) {
            return;
        }
        File[] inner = singleDir.listFiles();
        if (inner != null) {
            for (File item : inner) {
                File moved = new File(dir, item.getName());
                if (item.renameTo(moved)) {
                    continue;
                }
                if (item.isDirectory()) {
                    IO.copyDirectory(item.toPath(), moved.toPath());
                } else {
                    Files.copy(item.toPath(), moved.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        deleteInstallScratch(singleDir, "wrapped datapack extraction");
    }

    static DownloadResult download(String url, File dest, String etag, String lastModified) throws IOException {
        URI current = parseSourceUri(url);
        if (current == null) {
            throw new IOException("Empty datapack URL");
        }
        if ("file".equalsIgnoreCase(current.getScheme())) {
            return copyLocalDatapack(current, dest);
        }
        for (int attempt = 0; attempt < MAX_REDIRECTS; attempt++) {
            if (!"http".equalsIgnoreCase(current.getScheme()) && !"https".equalsIgnoreCase(current.getScheme())) {
                throw new IOException("Datapack URL must use HTTP or HTTPS: " + current);
            }
            URL target = current.toURL();
            HttpURLConnection connection = (HttpURLConnection) target.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", USER_AGENT);
            if (etag != null && !etag.isBlank()) {
                connection.setRequestProperty("If-None-Match", etag);
            }
            if (lastModified != null && !lastModified.isBlank()) {
                connection.setRequestProperty("If-Modified-Since", lastModified);
            }
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(60000);
            connection.setInstanceFollowRedirects(false);
            try {
                int code = connection.getResponseCode();
                if (code == HttpURLConnection.HTTP_NOT_MODIFIED) {
                    String responseEtag = headerOrFallback(connection, "ETag", etag);
                    String responseLastModified = headerOrFallback(connection, "Last-Modified", lastModified);
                    return new DownloadResult(true, responseEtag, responseLastModified);
                }
                if (code / 100 == 3) {
                    String location = connection.getHeaderField("Location");
                    if (location == null || location.isBlank()) {
                        throw new IOException("Redirect without a location header from " + current);
                    }
                    try {
                        current = current.resolve(new URI(location));
                    } catch (URISyntaxException e) {
                        throw new IOException("Invalid redirect location from " + current + ": " + location, e);
                    }
                    continue;
                }
                if (code != 200) {
                    throw new IOException("HTTP " + code + " downloading " + current);
                }
                long declaredLength = connection.getContentLengthLong();
                if (declaredLength > MAX_DOWNLOAD_BYTES) {
                    throw new IOException("Datapack download exceeds " + MAX_DOWNLOAD_BYTES + " bytes");
                }

                File parent = dest.getParentFile();
                Path parentPath = parent == null ? Path.of(".").toAbsolutePath().normalize() : parent.toPath();
                ensureScratchDirectory(parentPath.toFile(), "datapack download cache");
                Path temporary = Files.createTempFile(parentPath, dest.getName() + "-", ".part");
                try {
                    long downloaded = 0;
                    String responseEtag = connection.getHeaderField("ETag");
                    String responseLastModified = connection.getHeaderField("Last-Modified");
                    try (InputStream in = connection.getInputStream();
                         OutputStream out = Files.newOutputStream(temporary)) {
                        byte[] buffer = new byte[8192];
                        int length;
                        while ((length = in.read(buffer)) > 0) {
                            downloaded += length;
                            if (downloaded > MAX_DOWNLOAD_BYTES) {
                                throw new IOException("Datapack download exceeds " + MAX_DOWNLOAD_BYTES + " bytes");
                            }
                            out.write(buffer, 0, length);
                        }
                    }
                    move(temporary, dest.toPath());
                    return new DownloadResult(false, responseEtag, responseLastModified);
                } finally {
                    Files.deleteIfExists(temporary);
                }
            } finally {
                connection.disconnect();
            }
        }
        throw new IOException("Too many redirects downloading " + url);
    }

    private static DownloadResult copyLocalDatapack(URI source, File destination) throws IOException {
        Path sourcePath = requireLocalDatapackPath(source);
        Path destinationPath = destination.toPath().toAbsolutePath().normalize();
        if (Files.exists(destinationPath, LinkOption.NOFOLLOW_LINKS)
                && Files.isSameFile(sourcePath, destinationPath)) {
            throw new IOException("Local datapack source and cache destination are the same file: " + sourcePath);
        }
        Path parent = destinationPath.getParent();
        if (parent == null) {
            throw new IOException("Local datapack cache destination has no parent: " + destinationPath);
        }
        ensureScratchDirectory(parent.toFile(), "datapack download cache");
        BasicFileAttributes before = Files.readAttributes(
                sourcePath,
                BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
        Path temporary = Files.createTempFile(parent, destination.getName() + "-", ".part");
        try {
            long copied = 0;
            try (InputStream input = Files.newInputStream(
                    sourcePath,
                    StandardOpenOption.READ,
                    LinkOption.NOFOLLOW_LINKS);
                 OutputStream output = Files.newOutputStream(temporary)) {
                byte[] buffer = new byte[HASH_BUFFER_BYTES];
                int length;
                while ((length = input.read(buffer)) > 0) {
                    copied += length;
                    if (copied > MAX_DOWNLOAD_BYTES) {
                        throw new IOException("Local datapack exceeds " + MAX_DOWNLOAD_BYTES + " bytes: " + sourcePath);
                    }
                    output.write(buffer, 0, length);
                }
            }
            BasicFileAttributes after = Files.readAttributes(
                    sourcePath,
                    BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            if (!after.isRegularFile()
                    || before.size() != after.size()
                    || !before.lastModifiedTime().equals(after.lastModifiedTime())
                    || !Objects.equals(before.fileKey(), after.fileKey())
                    || copied != after.size()) {
                throw new IOException("Local datapack changed while Iris was copying it: " + sourcePath);
            }
            move(temporary, destinationPath);
            return new DownloadResult(false, null, null);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static Path requireLocalDatapackPath(URI source) throws IOException {
        if (source == null
                || !"file".equalsIgnoreCase(source.getScheme())
                || source.isOpaque()
                || source.getRawAuthority() != null
                || source.getRawQuery() != null
                || source.getRawFragment() != null) {
            throw new IOException("Datapack file URL must be an absolute local file URI without authority, query, or fragment: " + source);
        }
        Path path;
        try {
            path = Path.of(source).toAbsolutePath().normalize();
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid local datapack file URL: " + source, exception);
        }
        if (Files.isSymbolicLink(path)
                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Local datapack is not a regular non-symbolic-link file: " + path);
        }
        long size = Files.size(path);
        if (size > MAX_DOWNLOAD_BYTES) {
            throw new IOException("Local datapack exceeds " + MAX_DOWNLOAD_BYTES + " bytes: " + path);
        }
        return path;
    }

    private static String headerOrFallback(HttpURLConnection connection, String name, String fallback) {
        String value = connection.getHeaderField(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String sha1(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            try (InputStream in = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int length;
                while ((length = in.read(buffer)) > 0) {
                    digest.update(buffer, 0, length);
                }
            }
            byte[] hash = digest.digest();
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-1 algorithm unavailable", e);
        }
    }

    private static String deriveId(ResolvedDatapack resolved) {
        String base = resolved.getProjectSlug();
        if (base == null || base.isBlank()) {
            base = resolved.getFileName();
            int dot = base.lastIndexOf('.');
            if (dot > 0) {
                base = base.substring(0, dot);
            }
        }
        String id = sanitizeId(base);
        if (resolved.isDirect()) {
            return id + "-" + ModrinthResolver.directIdentity(resolved.getDownloadUrl());
        }
        return id;
    }

    private static String sanitizeId(String value) {
        if (value == null) {
            return "datapack";
        }
        String lower = value.toLowerCase(Locale.ROOT).trim();
        StringBuilder builder = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.') {
                builder.append(c);
            } else if (c == ' ' || c == '/' || c == '\\') {
                builder.append('-');
            }
        }
        String cleaned = builder.toString().replaceAll("-+", "-");
        cleaned = cleaned.replaceAll("^[-_.]+", "").replaceAll("[-_.]+$", "");
        return cleaned.isBlank() ? "datapack" : cleaned;
    }

    private static boolean isValidManagedId(String value) {
        return value != null && !value.isBlank() && value.equals(sanitizeId(value))
                && !RESERVED_IDS.contains(value);
    }

    private static String serverMcVersion() {
        return serverMcVersion(Bukkit.getServer());
    }

    static String serverMcVersion(Server server) {
        MinecraftVersion detected = MinecraftVersion.detect(server);
        return detected == null ? null : detected.value();
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "?" : value;
    }

    private static String safeFile(String value) {
        return value == null || value.isBlank() ? "unknown" : value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static void diagnoseConflicts(VolmitSender sender, Manifest manifest) {
        Map<String, List<String>> owners = new TreeMap<>();
        for (Entry entry : manifest.entries) {
            for (String key : copyList(entry.structureKeys)) {
                owners.computeIfAbsent("structure " + key, ignored -> new ArrayList<>()).add(entry.id);
            }
            for (String key : copyList(entry.templateKeys)) {
                owners.computeIfAbsent("template " + key, ignored -> new ArrayList<>()).add(entry.id);
            }
        }
        int conflicts = 0;
        for (Map.Entry<String, List<String>> resource : owners.entrySet()) {
            if (resource.getValue().size() < 2) {
                continue;
            }
            conflicts++;
            if (conflicts <= 20) {
                message(sender, C.YELLOW + "  Registry conflict: " + C.WHITE + resource.getKey()
                        + C.YELLOW + " is supplied by " + String.join(", ", resource.getValue()));
            }
        }
        if (conflicts > 0) {
            message(sender, C.YELLOW + "Detected " + conflicts + " external datapack registry conflict(s). Minecraft's enabled-pack order in level.dat determines precedence; datapackImports order does not.");
        }
        if (conflicts > 20) {
            message(sender, C.GRAY + "  " + (conflicts - 20) + " additional conflict(s) omitted.");
        }
    }

    private static void pruneCache(File cacheDir) {
        File[] files = cacheDir.listFiles(File::isFile);
        if (files == null) {
            return;
        }
        List<File> archives = new ArrayList<>();
        long totalBytes = 0;
        for (File file : files) {
            if (file.getName().endsWith(".part")) {
                IO.delete(file);
                continue;
            }
            if (file.getName().endsWith(".zip")) {
                archives.add(file);
                totalBytes += Math.max(0, file.length());
            }
        }
        archives.sort(Comparator.comparingLong(File::lastModified));
        int remaining = archives.size();
        for (File archive : archives) {
            if (remaining <= MAX_CACHE_FILES && totalBytes <= MAX_CACHE_BYTES) {
                break;
            }
            long size = Math.max(0, archive.length());
            IO.delete(archive);
            if (!archive.exists()) {
                remaining--;
                totalBytes -= size;
            }
        }
    }

    private static List<String> copyList(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    static Entry copyEntry(Entry source) {
        Entry resolved = Objects.requireNonNull(source, "Datapack manifest entry must not be null");
        Entry copy = new Entry();
        copy.url = resolved.url;
        copy.id = resolved.id;
        copy.versionId = resolved.versionId;
        copy.versionNumber = resolved.versionNumber;
        copy.sha1 = resolved.sha1;
        copy.filename = resolved.filename;
        copy.etag = resolved.etag;
        copy.lastModified = resolved.lastModified;
        copy.installedEpoch = resolved.installedEpoch;
        copy.structuresImported = resolved.structuresImported;
        copy.stagingMetadata = resolved.stagingMetadata;
        copy.structureKeys = new ArrayList<>(copyList(resolved.structureKeys));
        copy.templateKeys = new ArrayList<>(copyList(resolved.templateKeys));
        copy.installMetadata = new HashMap<>(Objects.requireNonNullElseGet(
                resolved.installMetadata, Map::of));
        copy.importedTargets = new HashMap<>(Objects.requireNonNullElseGet(
                resolved.importedTargets, Map::of));
        copy.importAttempts = new HashMap<>(Objects.requireNonNullElseGet(
                resolved.importAttempts, Map::of));
        copy.importedBundles = new HashMap<>();
        if (resolved.importedBundles != null) {
            for (Map.Entry<String, Map<String, String>> bundle : resolved.importedBundles.entrySet()) {
                copy.importedBundles.put(bundle.getKey(), new HashMap<>(bundle.getValue()));
            }
        }
        return copy;
    }

    private static String hex(byte[] hash) {
        StringBuilder builder = new StringBuilder(hash.length * 2);
        for (byte value : hash) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    private static void updateDigestInt(MessageDigest digest, int value) {
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }

    private static void updateDigestLong(MessageDigest digest, long value) {
        digest.update((byte) (value >>> 56));
        digest.update((byte) (value >>> 48));
        digest.update((byte) (value >>> 40));
        digest.update((byte) (value >>> 32));
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }

    private static void message(VolmitSender sender, String text) {
        if (sender != null) {
            sender.sendMessage(text);
            return;
        }
        IrisLogging.info(text);
    }

    private static Manifest readManifest(File root) {
        File file = new File(root, "manifest.json");
        Manifest manifest = null;
        boolean recoverFromStaging = false;
        Path path = file.toPath();
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            manifest = new Manifest();
        } else if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            IrisLogging.error("Unreadable datapack manifest " + file.getPath()
                    + "; moving the non-regular path aside before recovering Iris-managed staging");
            recoverFromStaging = quarantine(path);
        } else {
            try {
                String json = readBoundedUtf8(path, MAX_MANIFEST_BYTES, "Datapack manifest");
                manifest = GSON.fromJson(json, Manifest.class);
                if (manifest == null) {
                    throw new IOException("Datapack manifest is empty");
                }
            } catch (Exception e) {
                IrisLogging.reportError("Unreadable datapack manifest " + file.getPath()
                        + "; moving it aside before recovering Iris-managed staging", e);
                recoverFromStaging = quarantine(file.toPath());
            }
        }
        if (manifest == null) {
            manifest = new Manifest();
        }
        normalizeManifest(manifest);
        if (recoverFromStaging) {
            recoverManifestFromStaging(root, manifest);
        }
        return manifest;
    }

    private static boolean quarantine(Path file) {
        try {
            move(file, file.resolveSibling(file.getFileName().toString() + ".corrupt-" + System.currentTimeMillis()));
            return true;
        } catch (IOException e) {
            IrisLogging.reportError("Failed to move aside corrupt datapack manifest " + file, e);
            return false;
        }
    }

    private static void normalizeManifest(Manifest manifest) {
        if (manifest.entries == null) {
            manifest.entries = new ArrayList<>();
            return;
        }
        List<Entry> normalized = new ArrayList<>();
        Set<String> urls = new HashSet<>();
        Set<String> ids = new HashSet<>();
        for (Entry entry : manifest.entries) {
            if (entry == null || entry.url == null || entry.url.isBlank() || entry.id == null || entry.id.isBlank()) {
                continue;
            }
            entry.url = entry.url.trim();
            if (!isValidManagedId(entry.id)) {
                IrisLogging.warn("Ignoring datapack manifest entry with invalid id '" + entry.id + "'");
                continue;
            }
            entry.structureKeys = normalizeKeys(entry.structureKeys);
            entry.templateKeys = normalizeKeys(entry.templateKeys);
            entry.stagingMetadata = entry.stagingMetadata == null ? "" : entry.stagingMetadata.trim();
            entry.installMetadata = normalizeImportedTargets(entry.installMetadata);
            entry.importedTargets = normalizeImportedTargets(entry.importedTargets);
            entry.importAttempts = normalizeImportedTargets(entry.importAttempts);
            entry.importedBundles = normalizeImportedBundles(entry.importedBundles);
            if (!urls.add(entry.url) || !ids.add(entry.id)) {
                IrisLogging.warn("Ignoring duplicate datapack manifest entry for id '" + entry.id + "' and url " + entry.url);
                continue;
            }
            normalized.add(entry);
        }
        manifest.entries = normalized;
    }

    private static void recoverManifestFromStaging(File root, Manifest manifest) {
        File staging = new File(root, "staging");
        File[] directories = staging.listFiles(File::isDirectory);
        if (directories == null) {
            return;
        }
        for (File directory : directories) {
            try {
                Ownership ownership = readOwnershipOrNull(directory);
                if (ownership == null || !directory.getName().equals(ownership.id)
                        || manifest.find(ownership.url) != null || manifest.findById(ownership.id) != null) {
                    continue;
                }
                validateManagedDirectory(directory, ownership.id);
                if (!Objects.equals(ownership.contentHash, directoryHash(directory))) {
                    IrisLogging.warn("Ignoring corrupt Iris-managed datapack staging at " + directory.getPath());
                    continue;
                }
                Entry recovered = ownership.toEntry();
                recovered.installedEpoch = directory.lastModified();
                manifest.put(recovered);
                IrisLogging.warn("Recovered Iris-managed datapack manifest entry '" + recovered.id + "' from staging.");
            } catch (IOException e) {
                IrisLogging.warn("Ignoring orphan datapack staging at " + directory.getPath() + ": " + e.getMessage());
            }
        }
    }

    private static List<String> normalizeKeys(List<String> keys) {
        TreeSet<String> normalized = new TreeSet<>();
        if (keys != null) {
            for (String key : keys) {
                if (key != null && !key.isBlank()) {
                    normalized.add(key.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        return new ArrayList<>(normalized);
    }

    private static Map<String, String> normalizeImportedTargets(Map<String, String> targets) {
        Map<String, String> normalized = new HashMap<>();
        if (targets == null) {
            return normalized;
        }
        for (Map.Entry<String, String> target : targets.entrySet()) {
            if (target.getKey() != null && !target.getKey().isBlank()
                    && target.getValue() != null && !target.getValue().isBlank()) {
                normalized.put(target.getKey(), target.getValue());
            }
        }
        return normalized;
    }

    private static Map<String, Map<String, String>> normalizeImportedBundles(
            Map<String, Map<String, String>> targets
    ) {
        Map<String, Map<String, String>> normalized = new HashMap<>();
        if (targets == null) {
            return normalized;
        }
        for (Map.Entry<String, Map<String, String>> target : targets.entrySet()) {
            if (target.getKey() == null || target.getKey().isBlank() || target.getValue() == null) {
                continue;
            }
            Map<String, String> bundles = new TreeMap<>();
            for (Map.Entry<String, String> bundle : target.getValue().entrySet()) {
                if (bundle.getKey() != null && !bundle.getKey().isBlank()
                        && bundle.getValue() != null && !bundle.getValue().isBlank()) {
                    bundles.put(bundle.getKey(), bundle.getValue());
                }
            }
            normalized.put(target.getKey(), bundles);
        }
        return normalized;
    }

    private static void writeManifest(File root, Manifest manifest) {
        try {
            writeManifestChecked(root, manifest);
        } catch (IOException e) {
            IrisLogging.reportError("Failed to write datapack manifest "
                    + new File(root, "manifest.json").toPath(), e);
        }
    }

    private static void writeManifestChecked(File root, Manifest manifest) throws IOException {
        try (ManifestWrite write = prepareManifestWrite(root, manifest)) {
            write.publish();
        }
    }

    private static ManifestWrite prepareManifestWrite(File root, Manifest manifest) throws IOException {
        Path file = new File(root, "manifest.json").toPath();
        Path parent = file.getParent();
        Files.createDirectories(parent);
        Path temp = Files.createTempFile(parent, "manifest", ".json.tmp");
        Path rollback = null;
        try {
            byte[] content = GSON.toJson(manifest).getBytes(StandardCharsets.UTF_8);
            if (content.length > MAX_MANIFEST_BYTES) {
                throw new IOException("Datapack manifest exceeds " + MAX_MANIFEST_BYTES + " bytes");
            }
            Files.write(temp, content, StandardOpenOption.TRUNCATE_EXISTING);
            forceFile(temp);
            if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)) {
                    throw new IOException("Datapack manifest is not a regular file: " + file);
                }
                rollback = Files.createTempFile(parent, "manifest-rollback", ".json.tmp");
                Files.copy(file, rollback, StandardCopyOption.REPLACE_EXISTING);
                forceFile(rollback);
            }
            return new ManifestWrite(temp, file, rollback);
        } catch (IOException | RuntimeException e) {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException cleanupFailure) {
                e.addSuppressed(cleanupFailure);
            }
            if (rollback != null) {
                try {
                    Files.deleteIfExists(rollback);
                } catch (IOException cleanupFailure) {
                    e.addSuppressed(cleanupFailure);
                }
            }
            throw e;
        }
    }

    private static void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void moveNew(Path source, Path target) throws IOException {
        if (pathExists(target, "move target")) {
            throw new IOException("Refusing to replace concurrently-created path " + target);
        }
        Files.move(source, target);
    }

    private static void ensureScratchDirectory(File directory, String purpose) throws IOException {
        Path path = directory.toPath();
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Refusing invalid " + purpose + " directory " + directory.getPath());
            }
            return;
        }
        Files.createDirectories(path);
        if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Could not create a safe " + purpose + " directory " + directory.getPath());
        }
    }

    private static void verifyDirectoryContainerIfPresent(File directory, String purpose) throws IOException {
        Path path = directory.toPath();
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Refusing invalid " + purpose + " directory " + directory.getPath());
        }
    }

    private static DatapackCoordinator createInstallCoordinator(
            File root,
            Entry entry,
            List<InstallPlan> plans,
            boolean manifestAlreadyMatched
    ) throws IOException {
        CoordinatorJournal journal = newCoordinatorJournal(
                CoordinatorOperation.INSTALL,
                entry,
                manifestAlreadyMatched
        );
        for (InstallPlan plan : plans) {
            if (!plan.publishRequired()) {
                continue;
            }
            CoordinatorDirectory directory = new CoordinatorDirectory();
            directory.target = normalizedPath(plan.target());
            directory.pending = normalizedPath(plan.pending());
            directory.backup = normalizedPath(plan.backup());
            directory.hadTarget = plan.hadTarget();
            directory.originalHash = plan.originalHash();
            directory.desiredHash = plan.desiredHash();
            directory.originalMarkerHash = plan.originalMarkerHash();
            directory.desiredMarkerHash = plan.desiredMarkerHash();
            directory.originalIdentity = plan.originalIdentity();
            directory.desiredIdentity = plan.desiredIdentity();
            directory.targetRoot = plan.targetRootIdentity();
            directory.scratchRoot = plan.scratchRootIdentity();
            directory.targetRootIdentity = plan.targetRootFileIdentity();
            directory.scratchRootIdentity = plan.scratchRootFileIdentity();
            journal.directories.add(directory);
        }
        return createCoordinator(root, journal);
    }

    private static DatapackCoordinator createRemovalCoordinator(
            File root,
            Entry entry,
            DirectoryRemoval directoryRemoval,
            EditableImportRemoval editableRemoval
    ) throws IOException {
        CoordinatorJournal journal = newCoordinatorJournal(CoordinatorOperation.REMOVE, entry, true);
        for (DirectoryMove move : directoryRemoval.moves()) {
            CoordinatorDirectory directory = new CoordinatorDirectory();
            directory.target = normalizedPath(move.target());
            directory.pending = "";
            directory.backup = normalizedPath(move.backup());
            directory.hadTarget = true;
            directory.originalHash = move.originalHash();
            directory.desiredHash = "";
            directory.originalMarkerHash = move.originalMarkerHash();
            directory.desiredMarkerHash = "";
            directory.originalIdentity = move.originalIdentity();
            directory.desiredIdentity = "";
            directory.targetRoot = move.targetRootIdentity();
            directory.scratchRoot = move.scratchRootIdentity();
            directory.targetRootIdentity = move.targetRootFileIdentity();
            directory.scratchRootIdentity = move.scratchRootFileIdentity();
            journal.directories.add(directory);
        }
        for (StructureTransactionWriter.PreparedRemovalToken token : editableRemoval.recoveryTokens()) {
            CoordinatorEditable editable = new CoordinatorEditable();
            editable.packRoot = token.packRoot().toString();
            editable.transactionId = token.transactionId().toString();
            editable.claimId = UUID.randomUUID().toString();
            journal.editables.add(editable);
        }
        Path transactionRoot = coordinatorTransactionRoot(root, journal);
        try {
            editableRemoval.claimRecoveryOwners(transactionRoot, journal);
            writeCoordinatorJournal(transactionRoot, journal);
            return new DatapackCoordinator(transactionRoot, journal);
        } catch (IOException | RuntimeException creationFailure) {
            try {
                deleteInstallScratch(transactionRoot.toFile(), "incomplete datapack transaction");
            } catch (IOException cleanupFailure) {
                creationFailure.addSuppressed(cleanupFailure);
            }
            if (creationFailure instanceof IOException ioFailure) {
                throw ioFailure;
            }
            throw creationFailure;
        }
    }

    private static CoordinatorJournal newCoordinatorJournal(
            CoordinatorOperation operation,
            Entry entry,
            boolean manifestAlreadyMatched
    ) {
        CoordinatorJournal journal = new CoordinatorJournal();
        journal.schemaVersion = TRANSACTION_SCHEMA;
        journal.transactionId = UUID.randomUUID().toString();
        journal.operation = operation;
        journal.phase = CoordinatorPhase.PREPARED;
        journal.id = entry.id;
        journal.url = entry.url;
        journal.versionId = entry.versionId;
        journal.versionNumber = entry.versionNumber;
        journal.sha1 = entry.sha1;
        journal.manifestAlreadyMatched = manifestAlreadyMatched;
        return journal;
    }

    private static DatapackCoordinator createCoordinator(File root, CoordinatorJournal journal) throws IOException {
        Path transactionRoot = coordinatorTransactionRoot(root, journal);
        writeCoordinatorJournal(transactionRoot, journal);
        return new DatapackCoordinator(transactionRoot, journal);
    }

    private static Path coordinatorTransactionRoot(File root, CoordinatorJournal journal) throws IOException {
        File transactionDirectory = new File(root, TRANSACTION_DIRECTORY);
        ensureScratchDirectory(transactionDirectory, "datapack transaction");
        Path transactionRoot = new File(transactionDirectory, journal.transactionId).toPath().toAbsolutePath().normalize();
        if (Files.exists(transactionRoot, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Datapack transaction id already exists: " + journal.transactionId);
        }
        return transactionRoot;
    }

    private static String normalizedPath(File file) {
        return file.toPath().toAbsolutePath().normalize().toString();
    }

    private static String realDirectoryPath(File directory, String purpose) throws IOException {
        Path path = directory.toPath().toAbsolutePath().normalize();
        if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Invalid " + purpose + " " + path);
        }
        return path.toRealPath().toString();
    }

    static boolean recoverTransactions(File root, List<File> worldFolders) throws IOException {
        boolean changed = recoverStagingScratch(new File(root, "staging"));
        File transactionDirectory = new File(root, TRANSACTION_DIRECTORY);
        Path transactionPath = transactionDirectory.toPath();
        if (!Files.exists(transactionPath, LinkOption.NOFOLLOW_LINKS)) {
            return recoverInstallScratch(root, worldFolders) | changed;
        }
        verifyDirectoryContainerIfPresent(transactionDirectory, "datapack transaction");
        Manifest committedManifest = readCommittedManifest(root);
        List<Path> transactionRoots;
        try (Stream<Path> paths = Files.list(transactionPath)) {
            transactionRoots = paths.limit(MAX_TRANSACTION_COUNT + 2L).sorted().toList();
        }
        if (transactionRoots.size() == MAX_TRANSACTION_COUNT + 2) {
            throw new IOException("Datapack transaction directory contains too many entries");
        }
        int transactionCount = 0;
        for (Path transactionRoot : transactionRoots) {
            if (isHarmlessRecoveryArtifact(transactionRoot)) {
                continue;
            }
            transactionCount++;
            if (transactionCount > MAX_TRANSACTION_COUNT) {
                throw new IOException("Datapack transaction count exceeds " + MAX_TRANSACTION_COUNT);
            }
        }
        for (Path transactionRoot : transactionRoots) {
            if (isHarmlessRecoveryArtifact(transactionRoot)) {
                changed |= Files.deleteIfExists(transactionRoot);
                continue;
            }
            recoverTransaction(root, worldFolders, committedManifest, transactionPath, transactionRoot);
            changed = true;
        }
        changed |= transactionDirectory.delete();
        return recoverInstallScratch(root, worldFolders) | changed;
    }

    private static boolean recoverInstallScratch(File root, List<File> worldFolders) throws IOException {
        Set<Path> scratchRoots = new TreeSet<>();
        scratchRoots.add(installScratchRoot(new File(root, "staging")).toPath().toAbsolutePath().normalize());
        for (File worldFolder : worldFolders) {
            scratchRoots.add(installScratchRoot(worldFolder).toPath().toAbsolutePath().normalize());
        }
        boolean changed = false;
        for (Path scratchRoot : scratchRoots) {
            changed |= recoverInstallScratchRoot(scratchRoot);
        }
        return changed;
    }

    private static boolean recoverInstallScratchRoot(Path scratchRoot) throws IOException {
        if (!Files.exists(scratchRoot, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        verifyDirectoryContainerIfPresent(scratchRoot.toFile(), "datapack install scratch");
        List<Path> children;
        try (Stream<Path> paths = Files.list(scratchRoot)) {
            children = paths.limit(MAX_MANAGED_PATHS + 2L).sorted().toList();
        }
        int managedEntries = 0;
        for (Path child : children) {
            if (!isHarmlessRecoveryArtifact(child)) {
                managedEntries++;
            }
            if (managedEntries > MAX_MANAGED_PATHS) {
                throw new IOException("Datapack install scratch contains too many entries");
            }
        }

        List<StagingScratch> pending = new ArrayList<>();
        List<StagingScratch> backups = new ArrayList<>();
        boolean changed = false;
        for (Path child : children) {
            if (isHarmlessRecoveryArtifact(child)) {
                changed |= Files.deleteIfExists(child);
                continue;
            }
            StagingScratch scratch = parseInstallScratch(scratchRoot, child);
            if (scratch == null) {
                throw new IOException("Unexpected datapack install scratch artifact " + child);
            }
            if (Files.isSymbolicLink(child) || !Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Invalid datapack install scratch artifact " + child);
            }
            validateScratchTree(child);
            if (scratch.kind() == StagingScratchKind.BACKUP) {
                backups.add(scratch);
            } else {
                pending.add(scratch);
            }
        }
        if (!backups.isEmpty()) {
            throw new IOException("Preserving unjournaled datapack install backup "
                    + backups.getFirst().path());
        }
        for (StagingScratch scratch : pending) {
            deleteInstallScratch(scratch.path().toFile(), "orphan datapack install pending directory");
            changed = true;
        }
        changed |= scratchRoot.toFile().delete();
        return changed;
    }

    private static StagingScratch parseInstallScratch(Path scratchRoot, Path child) throws IOException {
        Path normalized = child.toAbsolutePath().normalize();
        if (!Objects.equals(normalized.getParent(), scratchRoot)) {
            throw new IOException("Datapack install scratch artifact escapes its root: " + child);
        }
        String name = normalized.getFileName().toString();
        int uuidStart = name.length() - 36;
        if (uuidStart <= 1 || name.charAt(uuidStart - 1) != '-') {
            return null;
        }
        try {
            UUID.fromString(name.substring(uuidStart));
        } catch (IllegalArgumentException e) {
            return null;
        }
        String stem = name.substring(0, uuidStart - 1);
        StagingScratchKind kind = stem.endsWith("-backup")
                ? StagingScratchKind.BACKUP : StagingScratchKind.PENDING;
        String id = kind == StagingScratchKind.BACKUP
                ? stem.substring(0, stem.length() - "-backup".length()) : stem;
        if (id.isBlank() || !id.equals(sanitizeId(id)) || RESERVED_IDS.contains(id)) {
            return null;
        }
        return new StagingScratch(kind, id, normalized);
    }

    private static boolean recoverStagingScratch(File stagingDirectory) throws IOException {
        Path stagingRoot = stagingDirectory.toPath().toAbsolutePath().normalize();
        if (!Files.exists(stagingRoot, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        verifyDirectoryContainerIfPresent(stagingDirectory, "datapack staging");
        List<Path> children;
        try (Stream<Path> paths = Files.list(stagingRoot)) {
            children = paths.limit(MAX_MANAGED_PATHS + 1L).sorted().toList();
        }
        if (children.size() > MAX_MANAGED_PATHS) {
            throw new IOException("Datapack staging contains too many entries");
        }

        List<StagingScratch> pending = new ArrayList<>();
        Map<String, List<StagingScratch>> backups = new TreeMap<>();
        for (Path child : children) {
            StagingScratch scratch = parseStagingScratch(stagingRoot, child);
            if (scratch == null) {
                continue;
            }
            if (Files.isSymbolicLink(child) || !Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Invalid datapack staging scratch artifact " + child);
            }
            if (scratch.kind() == StagingScratchKind.PENDING) {
                validateScratchTree(child);
                pending.add(scratch);
            } else {
                backups.computeIfAbsent(scratch.id(), ignored -> new ArrayList<>()).add(scratch);
            }
        }

        for (Map.Entry<String, List<StagingScratch>> entry : backups.entrySet()) {
            if (entry.getValue().size() != 1) {
                throw new IOException("Ambiguous datapack staging backups for '" + entry.getKey() + "'");
            }
            StagingScratch backup = entry.getValue().getFirst();
            Ownership backupOwnership = verifyManagedScratchDirectory(backup.path().toFile(), backup.id());
            Path target = stagingRoot.resolve(backup.id()).normalize();
            if (!Objects.equals(target.getParent(), stagingRoot)) {
                throw new IOException("Datapack staging backup target escapes its root");
            }
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                Ownership targetOwnership = verifyManagedScratchDirectory(target.toFile(), backup.id());
                if (!Objects.equals(backupOwnership.url, targetOwnership.url)) {
                    throw new IOException("Datapack staging backup source does not match its target: " + target);
                }
            }
        }

        boolean changed = false;
        for (List<StagingScratch> matches : backups.values()) {
            StagingScratch backup = matches.getFirst();
            Path target = stagingRoot.resolve(backup.id()).normalize();
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                deleteVerifiedDirectory(backup.path().toFile());
            } else {
                moveNew(backup.path(), target);
            }
            changed = true;
        }
        for (StagingScratch scratch : pending) {
            deleteVerifiedDirectory(scratch.path().toFile());
            changed = true;
        }
        forceDirectoryIfSupported(stagingRoot);
        return changed;
    }

    private static StagingScratch parseStagingScratch(Path stagingRoot, Path child) throws IOException {
        Path normalized = child.toAbsolutePath().normalize();
        if (!Objects.equals(normalized.getParent(), stagingRoot)) {
            throw new IOException("Datapack staging artifact escapes its root: " + child);
        }
        String name = normalized.getFileName().toString();
        StagingScratchKind kind;
        String prefix;
        if (name.startsWith(".pending-")) {
            kind = StagingScratchKind.PENDING;
            prefix = ".pending-";
        } else if (name.startsWith(".backup-")) {
            kind = StagingScratchKind.BACKUP;
            prefix = ".backup-";
        } else {
            return null;
        }
        int uuidStart = name.length() - 36;
        if (uuidStart <= prefix.length() || name.charAt(uuidStart - 1) != '-') {
            return null;
        }
        String id = name.substring(prefix.length(), uuidStart - 1);
        if (!id.equals(sanitizeId(id)) || RESERVED_IDS.contains(id)) {
            return null;
        }
        try {
            UUID.fromString(name.substring(uuidStart));
        } catch (IllegalArgumentException e) {
            return null;
        }
        return new StagingScratch(kind, id, normalized);
    }

    private static void validateScratchTree(Path root) throws IOException {
        FileStore rootStore = Files.getFileStore(root);
        int[] pathCount = new int[]{0};
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            private FileVisitResult inspect(Path entry, BasicFileAttributes attributes) throws IOException {
                pathCount[0]++;
                if (pathCount[0] > MAX_MANAGED_PATHS) {
                    throw new IOException("Datapack scratch contains too many paths: " + root);
                }
                if (attributes.isSymbolicLink()
                        || attributes.isOther()
                        || (!attributes.isDirectory() && !attributes.isRegularFile())) {
                    throw new IOException("Datapack scratch contains an unsupported file: " + entry);
                }
                if (!sameDatapackVolume(root, rootStore, entry)) {
                    throw new IOException("Datapack scratch crosses a filesystem boundary: " + entry);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(
                    Path directory,
                    BasicFileAttributes attributes
            ) throws IOException {
                return inspect(directory, attributes);
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                return inspect(file, attributes);
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException failure) throws IOException {
                throw new IOException("Unable to inspect datapack scratch entry: " + file, failure);
            }
        });
    }

    private static boolean sameScratchVolume(Path first, Path second) throws IOException {
        return sameScratchVolume(first, Files.getFileStore(first), second, Files.getFileStore(second));
    }

    static boolean sameScratchVolume(
            Path first,
            FileStore firstStore,
            Path second,
            FileStore secondStore
    ) {
        if (Objects.equals(firstStore, secondStore)) {
            return true;
        }
        if (!isDefaultWindowsPath(first) || !isDefaultWindowsPath(second)) {
            return false;
        }
        Path firstAbsolute = first.toAbsolutePath().normalize();
        Path secondAbsolute = second.toAbsolutePath().normalize();
        if ((firstAbsolute.toString().length() > WINDOWS_LEGACY_PATH_LIMIT)
                == (secondAbsolute.toString().length() > WINDOWS_LEGACY_PATH_LIMIT)) {
            return false;
        }
        Path firstRoot = firstAbsolute.getRoot();
        Path secondRoot = secondAbsolute.getRoot();
        if (firstRoot == null || secondRoot == null) {
            return false;
        }
        return sameWindowsVolume(
                firstStore, firstRoot.toString(), secondStore, secondRoot.toString());
    }

    static boolean sameWindowsVolume(
            FileStore firstStore,
            String firstRoot,
            FileStore secondStore,
            String secondRoot
    ) {
        if (firstRoot == null || secondRoot == null || !firstRoot.equalsIgnoreCase(secondRoot)) {
            return false;
        }
        try {
            Object firstSerial = firstStore.getAttribute("volume:vsn");
            Object secondSerial = secondStore.getAttribute("volume:vsn");
            return firstSerial != null && firstSerial.equals(secondSerial);
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }

    static boolean isSupportedScratchDirectory(BasicFileAttributes attributes) {
        return attributes != null
                && attributes.isDirectory()
                && !attributes.isSymbolicLink()
                && !attributes.isOther();
    }

    private static boolean isDefaultWindowsPath(Path path) {
        return File.separatorChar == '\\'
                && path.getFileSystem().equals(FileSystems.getDefault());
    }

    private static Ownership verifyManagedScratchDirectory(File directory, String id) throws IOException {
        validateManagedDirectory(directory, id);
        Ownership ownership = readOwnership(directory);
        if (!Objects.equals(ownership.contentHash, directoryHash(directory))) {
            throw new IOException("Datapack staging backup is modified or corrupt: " + directory.getPath());
        }
        return ownership;
    }

    private static boolean isHarmlessRecoveryArtifact(Path path) throws IOException {
        if (!isFinderMetadata(path)) {
            return false;
        }
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Suspicious datapack recovery artifact " + path);
        }
        return true;
    }

    private static void recoverTransaction(
            File root,
            List<File> worldFolders,
            Manifest committedManifest,
            Path transactionDirectory,
            Path transactionRoot
    ) throws IOException {
        Path normalizedRoot = transactionRoot.toAbsolutePath().normalize();
        if (!Objects.equals(normalizedRoot.getParent(), transactionDirectory.toAbsolutePath().normalize())
                || Files.isSymbolicLink(normalizedRoot)
                || !Files.isDirectory(normalizedRoot, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Invalid datapack transaction directory " + transactionRoot);
        }
        try {
            UUID.fromString(normalizedRoot.getFileName().toString());
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid datapack transaction directory name " + transactionRoot, e);
        }
        CoordinatorJournal journal = readCoordinatorJournal(normalizedRoot);
        if (journal == null) {
            deleteCoordinatorTransaction(normalizedRoot);
            return;
        }
        validateCoordinatorJournal(root, worldFolders, committedManifest, normalizedRoot, journal);
        boolean commit = coordinatorCommitDecision(committedManifest, journal);
        resolveCoordinatorDirectories(journal, commit);
        resolveCoordinatorEditables(journal, normalizedRoot, commit);
        deleteCoordinatorTransaction(normalizedRoot);
    }

    private static Manifest readCommittedManifest(File root) throws IOException {
        Path manifestPath = new File(root, "manifest.json").toPath();
        if (!Files.exists(manifestPath, LinkOption.NOFOLLOW_LINKS)) {
            return new Manifest();
        }
        if (Files.isSymbolicLink(manifestPath)
                || !Files.isRegularFile(manifestPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Datapack manifest is not a regular file: " + manifestPath);
        }
        Manifest manifest;
        try {
            manifest = GSON.fromJson(
                    readBoundedUtf8(manifestPath, MAX_MANIFEST_BYTES, "Datapack manifest"),
                    Manifest.class
            );
        } catch (RuntimeException e) {
            throw new IOException("Invalid datapack manifest " + manifestPath, e);
        }
        if (manifest == null) {
            throw new IOException("Empty datapack manifest " + manifestPath);
        }
        normalizeManifest(manifest);
        return manifest;
    }

    private static CoordinatorJournal readCoordinatorJournal(Path transactionRoot) throws IOException {
        Path committed = transactionRoot.resolve(TRANSACTION_JOURNAL);
        Path next = transactionRoot.resolve(TRANSACTION_JOURNAL_NEXT);
        if (Files.exists(committed, LinkOption.NOFOLLOW_LINKS)) {
            return parseCoordinatorJournal(committed);
        }
        if (!Files.exists(next, LinkOption.NOFOLLOW_LINKS)) {
            try (Stream<Path> entries = Files.list(transactionRoot)) {
                if (entries.findAny().isEmpty()) {
                    return null;
                }
            }
            throw new IOException("Missing datapack transaction journal " + committed);
        }
        try {
            return parseCoordinatorJournal(next);
        } catch (IOException firstWriteFailure) {
            try (Stream<Path> entries = Files.list(transactionRoot)) {
                List<Path> contents = entries.limit(2).toList();
                if (contents.size() == 1 && Objects.equals(contents.getFirst(), next)
                        && !Files.isSymbolicLink(next)) {
                    return null;
                }
            }
            throw firstWriteFailure;
        }
    }

    private static CoordinatorJournal parseCoordinatorJournal(Path journalPath) throws IOException {
        if (Files.isSymbolicLink(journalPath)
                || !Files.isRegularFile(journalPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Invalid datapack transaction journal " + journalPath);
        }
        try {
            CoordinatorJournal journal = GSON.fromJson(
                    readBoundedUtf8(
                            journalPath,
                            MAX_TRANSACTION_JOURNAL_BYTES,
                            "Datapack transaction journal"
                    ),
                    CoordinatorJournal.class
            );
            if (journal == null) {
                throw new IOException("Empty datapack transaction journal " + journalPath);
            }
            return journal;
        } catch (RuntimeException e) {
            throw new IOException("Invalid datapack transaction journal " + journalPath, e);
        }
    }

    private static void validateCoordinatorJournal(
            File root,
            List<File> worldFolders,
            Manifest committedManifest,
            Path transactionRoot,
            CoordinatorJournal journal
    ) throws IOException {
        if (journal.schemaVersion != TRANSACTION_SCHEMA || journal.transactionId == null
                || journal.operation == null || journal.phase == null || journal.id == null
                || journal.url == null || journal.directories == null || journal.editables == null) {
            throw new IOException("Incomplete datapack transaction journal at " + transactionRoot);
        }
        UUID transactionId;
        try {
            transactionId = UUID.fromString(journal.transactionId);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid datapack transaction id " + journal.transactionId, e);
        }
        if (!transactionId.toString().equals(transactionRoot.getFileName().toString())) {
            throw new IOException("Datapack transaction id does not match its directory");
        }
        if (!journal.id.equals(sanitizeId(journal.id)) || RESERVED_IDS.contains(journal.id)) {
            throw new IOException("Invalid datapack transaction entry id " + journal.id);
        }
        if (journal.directories.size() > worldFolders.size() + 1 || journal.editables.size() > 10_000) {
            throw new IOException("Datapack transaction journal contains too many participants");
        }
        if (journal.operation == CoordinatorOperation.INSTALL && !journal.editables.isEmpty()) {
            throw new IOException("Datapack install transaction unexpectedly contains editable participants");
        }

        Set<Path> allowedTargets = new HashSet<>();
        allowedTargets.add(new File(new File(root, "staging"), journal.id).toPath().toAbsolutePath().normalize());
        for (File worldFolder : worldFolders) {
            allowedTargets.add(new File(worldFolder, journal.id).toPath().toAbsolutePath().normalize());
        }
        Set<Path> seenTargets = new HashSet<>();
        Set<Path> seenTargetIdentities = new HashSet<>();
        for (CoordinatorDirectory directory : journal.directories) {
            validateCoordinatorDirectory(
                    directory, journal.operation, allowedTargets, seenTargets, seenTargetIdentities);
        }
        Set<String> seenEditables = new HashSet<>();
        Set<Path> allowedEditableRoots = journal.editables.isEmpty()
                ? Set.of() : authoritativeEditableRoots(committedManifest, journal);
        for (CoordinatorEditable editable : journal.editables) {
            if (editable == null || editable.packRoot == null || editable.transactionId == null
                    || editable.claimId == null
                    || !seenEditables.add(editable.packRoot)) {
                throw new IOException("Invalid duplicate editable participant in datapack transaction");
            }
            Path packRoot = coordinatorPath(editable.packRoot, "editable pack root");
            Path realPackRoot = Files.isDirectory(packRoot, LinkOption.NOFOLLOW_LINKS)
                    ? packRoot.toRealPath() : packRoot;
            if (Files.isSymbolicLink(packRoot) || !Files.isDirectory(packRoot, LinkOption.NOFOLLOW_LINKS)
                    || !packRoot.equals(realPackRoot) || !allowedEditableRoots.contains(realPackRoot)) {
                throw new IOException("Invalid editable pack root in datapack transaction: " + packRoot);
            }
            try {
                UUID.fromString(editable.transactionId);
                UUID.fromString(editable.claimId);
            } catch (IllegalArgumentException e) {
                throw new IOException("Invalid editable transaction recovery identity", e);
            }
            StructureTransactionWriter writer = new StructureTransactionWriter(packRoot);
            boolean recoveryDataPresent = writer.verifyRecoveryOwner(
                    new StructureTransactionWriter.PreparedRemovalToken(
                            packRoot,
                            UUID.fromString(editable.transactionId)
                    ),
                    new StructureTransactionWriter.RecoveryOwner(
                            transactionRoot,
                            UUID.fromString(journal.transactionId),
                            UUID.fromString(editable.claimId)
                    ),
                    committedManifest.findById(journal.id) == null
                            && journal.phase == CoordinatorPhase.PUBLISHED
            );
            if (!recoveryDataPresent && journal.phase != CoordinatorPhase.COMMITTED) {
                throw new IOException("Editable structure recovery data disappeared before commit");
            }
        }
    }

    private static Set<Path> authoritativeEditableRoots(
            Manifest committedManifest,
            CoordinatorJournal journal
    ) throws IOException {
        Set<Path> roots = new HashSet<>();
        Entry committed = committedManifest.findById(journal.id);
        if (committed != null) {
            if (!Objects.equals(committed.url, journal.url)) {
                throw new IOException("Datapack transaction conflicts with the committed editable pack owner");
            }
            addExistingPackRoots(roots, committed.importedTargets.keySet());
            addExistingPackRoots(roots, committed.importAttempts.keySet());
            addExistingPackRoots(roots, committed.importedBundles.keySet());
            return roots;
        }
        if (journal.operation != CoordinatorOperation.REMOVE || !journal.phase.published()) {
            throw new IOException("Datapack transaction has no committed editable pack authority");
        }
        for (CoordinatorEditable editable : journal.editables) {
            Path packRoot = coordinatorPath(editable.packRoot, "editable pack root");
            if (Files.isSymbolicLink(packRoot) || !Files.isDirectory(packRoot, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Invalid editable pack root in datapack transaction: " + packRoot);
            }
            Path realPackRoot = packRoot.toRealPath();
            if (!packRoot.equals(realPackRoot)) {
                throw new IOException("Editable pack root changed before datapack recovery: " + packRoot);
            }
            roots.add(realPackRoot);
        }
        return roots;
    }

    private static void addExistingPackRoots(Set<Path> roots, Set<String> paths) throws IOException {
        for (String path : paths) {
            try {
                Path root = Path.of(path).toAbsolutePath().normalize();
                if (Files.isDirectory(root)) {
                    roots.add(root.toRealPath());
                }
            } catch (RuntimeException e) {
                throw new IOException("Invalid editable pack root in the datapack manifest: " + path, e);
            }
        }
    }

    private static void validateCoordinatorDirectory(
            CoordinatorDirectory directory,
            CoordinatorOperation operation,
            Set<Path> allowedTargets,
            Set<Path> seenTargets,
            Set<Path> seenTargetIdentities
    ) throws IOException {
        if (directory == null || directory.target == null || directory.backup == null
                || directory.originalHash == null || directory.desiredHash == null
                || directory.originalMarkerHash == null || directory.desiredMarkerHash == null
                || directory.originalIdentity == null || directory.desiredIdentity == null
                || directory.targetRoot == null || directory.scratchRoot == null
                || directory.targetRootIdentity == null || directory.scratchRootIdentity == null) {
            throw new IOException("Incomplete directory participant in datapack transaction");
        }
        Path target = coordinatorPath(directory.target, "target");
        if (!allowedTargets.contains(target) || !seenTargets.add(target)) {
            throw new IOException("Datapack transaction target is outside its configured roots: " + target);
        }
        File targetFile = target.toFile();
        File targetParent = targetFile.getParentFile();
        Path targetRootIdentity = coordinatorPath(directory.targetRoot, "target root");
        if (!seenTargetIdentities.add(targetRootIdentity.resolve(target.getFileName()).normalize())) {
            throw new IOException("Aliased datapack transaction target " + target);
        }
        validateRecoveryContainer(
                targetParent.toPath(),
                targetRootIdentity,
                true,
                "datapack target root"
        );
        verifyDirectoryContainerIdentity(
                targetParent, directory.targetRoot, directory.targetRootIdentity, "datapack target root");
        File scratchRoot = new File(
                targetParent.getParentFile() == null ? targetParent : targetParent.getParentFile(),
                operation == CoordinatorOperation.INSTALL ? ".iris-datapack-install" : ".iris-datapack-remove"
        );
        Path expectedScratch = scratchRoot.toPath().toAbsolutePath().normalize();
        Path backup = coordinatorPath(directory.backup, "backup");
        if (!Objects.equals(backup.getParent(), expectedScratch) || Files.isSymbolicLink(backup)) {
            throw new IOException("Invalid datapack transaction backup path " + backup);
        }
        Path pending = null;
        if (operation == CoordinatorOperation.INSTALL) {
            if (directory.pending == null || directory.pending.isBlank()) {
                throw new IOException("Install transaction is missing its pending directory");
            }
            pending = coordinatorPath(directory.pending, "pending directory");
            if (!Objects.equals(pending.getParent(), expectedScratch) || Files.isSymbolicLink(pending)) {
                throw new IOException("Invalid datapack transaction pending path " + pending);
            }
        } else if (directory.pending != null && !directory.pending.isBlank()) {
            throw new IOException("Removal transaction unexpectedly contains a pending directory");
        }
        boolean scratchRequired = Files.exists(backup, LinkOption.NOFOLLOW_LINKS)
                || pending != null && Files.exists(pending, LinkOption.NOFOLLOW_LINKS);
        validateRecoveryContainer(
                expectedScratch,
                coordinatorPath(directory.scratchRoot, "scratch root"),
                scratchRequired,
                "datapack transaction scratch root"
        );
        if (scratchRequired) {
            verifyDirectoryContainerIdentity(
                    expectedScratch.toFile(), directory.scratchRoot,
                    directory.scratchRootIdentity, "datapack transaction scratch root");
        }
    }

    private static void validateRecoveryContainer(
            Path container,
            Path expectedRealPath,
            boolean required,
            String purpose
    ) throws IOException {
        Path normalized = container.toAbsolutePath().normalize();
        Path normalizedExpected = expectedRealPath.toAbsolutePath().normalize();
        if (!Files.exists(normalized, LinkOption.NOFOLLOW_LINKS)) {
            if (required) {
                throw new IOException("Missing " + purpose + " " + normalized);
            }
            return;
        }
        if (Files.isSymbolicLink(normalized)
                || !Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)
                || !Objects.equals(normalized.toRealPath(), normalizedExpected)) {
            throw new IOException("Changed or unsafe " + purpose + " " + normalized);
        }
    }

    private static boolean coordinatorCommitDecision(Manifest manifest, CoordinatorJournal journal) throws IOException {
        Entry committed = manifest.findById(journal.id);
        if (journal.operation == CoordinatorOperation.REMOVE) {
            if (committed == null) {
                if (!journal.phase.published()) {
                    throw new IOException("Datapack removal manifest changed before every participant was published");
                }
                return true;
            }
            if (!Objects.equals(committed.url, journal.url) || journal.phase == CoordinatorPhase.COMMITTED) {
                throw new IOException("Datapack removal journal conflicts with the committed manifest");
            }
            return false;
        }

        boolean matches = committed != null
                && Objects.equals(committed.url, journal.url)
                && Objects.equals(committed.versionId, journal.versionId)
                && Objects.equals(committed.versionNumber, journal.versionNumber)
                && Objects.equals(committed.sha1, journal.sha1);
        if (journal.manifestAlreadyMatched) {
            if (!matches) {
                throw new IOException("Committed datapack changed while an install transaction was incomplete");
            }
            return journal.phase.published();
        }
        if (matches) {
            if (!journal.phase.published()) {
                throw new IOException("Datapack install manifest changed before every target was published");
            }
            return true;
        }
        if (journal.phase == CoordinatorPhase.COMMITTED) {
            throw new IOException("Committed datapack install journal conflicts with the manifest");
        }
        return false;
    }

    private static void resolveCoordinatorDirectories(CoordinatorJournal journal, boolean commit) throws IOException {
        List<CoordinatorDirectory> directories = new ArrayList<>(journal.directories);
        if (!commit) {
            Collections.reverse(directories);
        }
        IOException failure = null;
        for (CoordinatorDirectory directory : directories) {
            try {
                if (journal.operation == CoordinatorOperation.INSTALL) {
                    resolveInstallDirectory(journal, directory, commit);
                } else {
                    resolveRemovalDirectory(directory, commit);
                }
            } catch (IOException | RuntimeException e) {
                IOException participantFailure = e instanceof IOException ioFailure
                        ? ioFailure : new IOException("Failed resolving datapack directory participant", e);
                if (failure == null) {
                    failure = participantFailure;
                } else {
                    failure.addSuppressed(participantFailure);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static void resolveInstallDirectory(
            CoordinatorJournal journal,
            CoordinatorDirectory directory,
            boolean commit
    ) throws IOException {
        File target = new File(directory.target);
        File pending = new File(directory.pending);
        File backup = new File(directory.backup);
        File targetRoot = new File(directory.targetRoot);
        if (commit) {
            verifyDesiredDirectory(
                    target, targetRoot, journal, directory.desiredHash,
                    directory.desiredMarkerHash, directory.desiredIdentity);
            deleteOriginalBackupIfPresent(
                    backup, targetRoot, directory.originalHash,
                    directory.originalMarkerHash, directory.originalIdentity);
            deleteDesiredDirectoryIfPresent(
                    pending, targetRoot, journal, directory.desiredHash,
                    directory.desiredMarkerHash, directory.desiredIdentity);
            cleanupScratchParent(backup);
            return;
        }

        if (directory.hadTarget) {
            if (Files.exists(backup.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                verifyDirectorySnapshot(
                        backup, targetRoot, directory.originalHash,
                        directory.originalMarkerHash, directory.originalIdentity,
                        "datapack install backup");
                deleteDesiredDirectoryIfPresent(
                        target, targetRoot, journal, directory.desiredHash,
                        directory.desiredMarkerHash, directory.desiredIdentity);
                moveNew(backup.toPath(), target.toPath());
                forceDirectoryIfSupported(target.getParentFile().toPath());
                forceDirectoryIfSupported(backup.getParentFile().toPath());
            } else {
                verifyDirectorySnapshot(
                        target, targetRoot, directory.originalHash,
                        directory.originalMarkerHash, directory.originalIdentity,
                        "original datapack target");
            }
        } else {
            if (Files.exists(backup.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Unexpected backup for newly-installed datapack " + backup.getPath());
            }
            deleteDesiredDirectoryIfPresent(
                    target, targetRoot, journal, directory.desiredHash,
                    directory.desiredMarkerHash, directory.desiredIdentity);
        }
        deleteDesiredDirectoryIfPresent(
                pending, targetRoot, journal, directory.desiredHash,
                directory.desiredMarkerHash, directory.desiredIdentity);
        cleanupScratchParent(backup);
    }

    private static void resolveRemovalDirectory(CoordinatorDirectory directory, boolean commit) throws IOException {
        File target = new File(directory.target);
        File backup = new File(directory.backup);
        File targetRoot = new File(directory.targetRoot);
        if (commit) {
            if (Files.exists(target.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Removed datapack target reappeared before transaction cleanup: " + target.getPath());
            }
            deleteOriginalBackupIfPresent(
                    backup, targetRoot, directory.originalHash,
                    directory.originalMarkerHash, directory.originalIdentity);
            cleanupScratchParent(backup);
            return;
        }
        if (Files.exists(backup.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            verifyDirectorySnapshot(
                    backup, targetRoot, directory.originalHash,
                    directory.originalMarkerHash, directory.originalIdentity,
                    "datapack removal backup");
            if (Files.exists(target.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Datapack removal target was concurrently recreated: " + target.getPath());
            }
            moveNew(backup.toPath(), target.toPath());
            forceDirectoryIfSupported(target.getParentFile().toPath());
            forceDirectoryIfSupported(backup.getParentFile().toPath());
        } else {
            verifyDirectorySnapshot(
                    target, targetRoot, directory.originalHash,
                    directory.originalMarkerHash, directory.originalIdentity,
                    "original datapack target");
        }
        cleanupScratchParent(backup);
    }

    private static void resolveCoordinatorEditables(
            CoordinatorJournal journal,
            Path transactionRoot,
            boolean commit
    ) throws IOException {
        IOException failure = null;
        for (CoordinatorEditable editable : journal.editables) {
            try {
                Path packRoot = coordinatorPath(editable.packRoot, "editable pack root");
                StructureTransactionWriter writer = new StructureTransactionWriter(packRoot);
                writer.resolvePreparedRemoval(
                        new StructureTransactionWriter.PreparedRemovalToken(
                                packRoot,
                                UUID.fromString(editable.transactionId)
                        ),
                        new StructureTransactionWriter.RecoveryOwner(
                                transactionRoot,
                                UUID.fromString(journal.transactionId),
                                UUID.fromString(editable.claimId)
                        ),
                        commit
                );
                IrisData.invalidateLoadedStructureResources(packRoot.toFile());
            } catch (IOException | RuntimeException e) {
                IOException participantFailure = e instanceof IOException ioFailure
                        ? ioFailure : new IOException("Failed resolving editable structure participant", e);
                if (failure == null) {
                    failure = participantFailure;
                } else {
                    failure.addSuppressed(participantFailure);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static void verifyDesiredDirectory(
            File directory,
            File targetRoot,
            CoordinatorJournal journal,
            String expectedHash,
            String expectedMarkerHash,
            String expectedIdentity
    ) throws IOException {
        verifyDirectorySnapshot(
                directory, targetRoot, expectedHash, expectedMarkerHash,
                expectedIdentity, "installed datapack target");
        Ownership ownership = readOwnership(directory);
        if (!Objects.equals(ownership.id, journal.id) || !Objects.equals(ownership.url, journal.url)
                || !Objects.equals(ownership.contentHash, expectedHash)) {
            throw new IOException("Installed datapack ownership does not match its transaction at " + directory.getPath());
        }
    }

    private static void deleteDesiredDirectoryIfPresent(
            File directory,
            File targetRoot,
            CoordinatorJournal journal,
            String expectedHash,
            String expectedMarkerHash,
            String expectedIdentity
    ) throws IOException {
        if (!Files.exists(directory.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        verifyDesiredDirectory(
                directory, targetRoot, journal, expectedHash,
                expectedMarkerHash, expectedIdentity);
        deleteVerifiedDirectory(directory);
    }

    private static void deleteOriginalBackupIfPresent(
            File backup,
            File targetRoot,
            String expectedHash,
            String expectedMarkerHash,
            String expectedIdentity
    ) throws IOException {
        if (!Files.exists(backup.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        verifyDirectorySnapshot(
                backup, targetRoot, expectedHash, expectedMarkerHash,
                expectedIdentity, "datapack transaction backup");
        deleteVerifiedDirectory(backup);
    }

    private static void deleteVerifiedDirectory(File directory) throws IOException {
        File parent = Objects.requireNonNull(directory.getParentFile(), "datapack transaction parent");
        validateInstallTree(directory, parent, "Datapack transaction directory");
        IO.delete(directory);
        if (Files.exists(directory.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Could not delete datapack transaction directory " + directory.getPath());
        }
    }

    private static void cleanupScratchParent(File participant) {
        File parent = participant.getParentFile();
        if (parent != null) {
            parent.delete();
        }
    }

    private static void deleteCoordinatorTransaction(Path transactionRoot) throws IOException {
        deleteInstallScratch(transactionRoot.toFile(), "completed datapack transaction");
        forceDirectoryIfSupported(Objects.requireNonNull(transactionRoot.getParent(), "transaction parent"));
    }

    private static void writeCoordinatorJournal(Path transactionRoot, CoordinatorJournal journal) throws IOException {
        byte[] content = GSON.toJson(journal).getBytes(StandardCharsets.UTF_8);
        if (content.length > MAX_TRANSACTION_JOURNAL_BYTES) {
            throw new IOException("Datapack transaction journal exceeds " + MAX_TRANSACTION_JOURNAL_BYTES + " bytes");
        }
        Files.createDirectories(transactionRoot);
        Path next = transactionRoot.resolve(TRANSACTION_JOURNAL_NEXT);
        if (Files.isSymbolicLink(next)) {
            throw new IOException("Datapack transaction journal cannot be a symbolic link: " + next);
        }
        Files.deleteIfExists(next);
        Files.write(next, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        forceFile(next);
        move(next, transactionRoot.resolve(TRANSACTION_JOURNAL));
        forceDirectoryIfSupported(transactionRoot);
        forceDirectoryIfSupported(Objects.requireNonNull(transactionRoot.getParent(), "transaction parent"));
    }

    private static String readBoundedUtf8(Path path, long maxBytes, String purpose) throws IOException {
        return new String(readBoundedBytes(path, maxBytes, purpose), StandardCharsets.UTF_8);
    }

    private static byte[] readBoundedBytes(Path path, long maxBytes, String purpose) throws IOException {
        int readLimit = Math.toIntExact(maxBytes + 1);
        byte[] content;
        try (InputStream input = Files.newInputStream(
                path,
                StandardOpenOption.READ,
                LinkOption.NOFOLLOW_LINKS
        )) {
            content = input.readNBytes(readLimit);
        }
        if (content.length > maxBytes) {
            throw new IOException(purpose + " exceeds " + maxBytes + " bytes");
        }
        return content;
    }

    private static Path coordinatorPath(String value, String purpose) throws IOException {
        try {
            return Path.of(value).toAbsolutePath().normalize();
        } catch (RuntimeException e) {
            throw new IOException("Invalid datapack transaction " + purpose + " path", e);
        }
    }

    private static void forceFile(Path file) throws IOException {
        if (!Durability.enabled()) {
            return;
        }

        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            Durability.force(channel);
        }
    }

    private static void forceDirectoryIfSupported(Path directory) throws IOException {
        if (!Durability.enabled()) {
            return;
        }

        if (!Files.getFileStore(directory).supportsFileAttributeView("posix")) {
            return;
        }
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            Durability.force(channel);
        }
    }

    private enum CoordinatorOperation {
        INSTALL,
        REMOVE
    }

    private enum StagingScratchKind {
        PENDING,
        BACKUP
    }

    private enum CoordinatorPhase {
        PREPARED,
        PUBLISHING,
        PUBLISHED,
        COMMITTED;

        private boolean published() {
            return this == PUBLISHED || this == COMMITTED;
        }
    }

    private static final class CoordinatorJournal {
        private int schemaVersion;
        private String transactionId;
        private CoordinatorOperation operation;
        private CoordinatorPhase phase;
        private String id;
        private String url;
        private String versionId;
        private String versionNumber;
        private String sha1;
        private boolean manifestAlreadyMatched;
        private List<CoordinatorDirectory> directories = new ArrayList<>();
        private List<CoordinatorEditable> editables = new ArrayList<>();
    }

    private static final class CoordinatorDirectory {
        private String target;
        private String pending;
        private String backup;
        private boolean hadTarget;
        private String originalHash;
        private String desiredHash;
        private String originalMarkerHash;
        private String desiredMarkerHash;
        private String originalIdentity;
        private String desiredIdentity;
        private String targetRoot;
        private String scratchRoot;
        private String targetRootIdentity;
        private String scratchRootIdentity;
    }

    private static final class CoordinatorEditable {
        private String packRoot;
        private String transactionId;
        private String claimId;
    }

    private static final class DatapackCoordinator {
        private final Path transactionRoot;
        private final CoordinatorJournal journal;

        private DatapackCoordinator(Path transactionRoot, CoordinatorJournal journal) {
            this.transactionRoot = transactionRoot;
            this.journal = journal;
        }

        private void phase(CoordinatorPhase phase) throws IOException {
            journal.phase = phase;
            writeCoordinatorJournal(transactionRoot, journal);
        }

        private void finish() throws IOException {
            deleteCoordinatorTransaction(transactionRoot);
        }
    }

    static final class VerifiedStagingInstall {
        private final Path normalizedRoot;
        private final Path realRoot;
        private final String rootIdentity;
        private final Path normalizedStagingRoot;
        private final Path realStagingRoot;
        private final String stagingRootIdentity;
        private final Path normalizedSource;
        private final Path realSource;
        private final String sourceIdentity;
        private final String id;
        private final String url;
        private final String versionId;
        private final String versionNumber;
        private final String sha1;
        private final String desiredHash;
        private final Entry committedEntrySnapshot;
        private final boolean legacyReplacementAuthorized;
        private final LegacyStagingSnapshot legacyStagingSnapshot;
        private boolean consumed;

        private VerifiedStagingInstall(
                Path normalizedRoot,
                Path normalizedStagingRoot,
                Path normalizedSource,
                Entry entry,
                String desiredHash,
                Entry committedEntrySnapshot,
                boolean legacyReplacementAuthorized,
                LegacyStagingSnapshot legacyStagingSnapshot
        ) throws IOException {
            this.normalizedRoot = normalizedRoot;
            this.realRoot = normalizedRoot.toRealPath();
            this.rootIdentity = directoryIdentity(normalizedRoot.toFile());
            this.normalizedStagingRoot = normalizedStagingRoot;
            this.realStagingRoot = normalizedStagingRoot.toRealPath();
            this.stagingRootIdentity = directoryIdentity(normalizedStagingRoot.toFile());
            this.normalizedSource = normalizedSource;
            this.realSource = normalizedSource.toRealPath();
            this.sourceIdentity = directoryIdentity(normalizedSource.toFile());
            this.id = entry.id;
            this.url = entry.url;
            this.versionId = entry.versionId;
            this.versionNumber = entry.versionNumber;
            this.sha1 = entry.sha1;
            this.desiredHash = desiredHash;
            this.committedEntrySnapshot = committedEntrySnapshot == null ? null : copyEntry(committedEntrySnapshot);
            this.legacyReplacementAuthorized = legacyReplacementAuthorized;
            this.legacyStagingSnapshot = legacyStagingSnapshot;
        }

        private Path stagingRoot() {
            return normalizedStagingRoot;
        }

        private boolean hasStablePathIdentities() {
            return !rootIdentity.isEmpty()
                    && !stagingRootIdentity.isEmpty()
                    && !sourceIdentity.isEmpty();
        }

        private boolean isCanonicalInstall(File installRoot, File target) {
            Path suppliedRoot = installRoot.toPath().toAbsolutePath().normalize();
            Path suppliedTarget = target.toPath().toAbsolutePath().normalize();
            return suppliedRoot.equals(normalizedStagingRoot)
                    && suppliedTarget.equals(normalizedStagingRoot.resolve(id).normalize());
        }

        private void verifyStagingRoot() throws IOException {
            verifyPathIdentity(normalizedRoot, realRoot, rootIdentity, "datapack storage root");
            verifyPathIdentity(
                    normalizedStagingRoot, realStagingRoot, stagingRootIdentity, "datapack staging root");
            if (!Objects.equals(normalizedStagingRoot.getParent(), normalizedRoot)
                    || !Files.isSameFile(normalizedStagingRoot.getParent(), normalizedRoot)) {
                throw new IOException("Verified datapack staging root changed identity");
            }
        }

        private boolean consume(
                File source,
                File installRoot,
                File target,
                Entry entry,
                String stagedHash
        ) throws IOException {
            if (consumed) {
                throw new IOException("Verified datapack staging authorization was already consumed");
            }
            consumed = true;
            Path suppliedRoot = installRoot.toPath().toAbsolutePath().normalize();
            Path suppliedTarget = target.toPath().toAbsolutePath().normalize();
            if (!suppliedRoot.equals(normalizedStagingRoot)
                    || !suppliedTarget.equals(normalizedStagingRoot.resolve(id).normalize())) {
                throw new IOException("Verified datapack staging authorization was paired with a different path");
            }
            verifyAuthority(source, entry, stagedHash);
            return legacyReplacementAuthorized;
        }

        private boolean authorizeLegacyWorldReplacement(
                File source,
                File installRoot,
                File target,
                Entry entry,
                String stagedHash,
                String currentHash,
                String currentMarkerHash
        ) throws IOException {
            if (legacyStagingSnapshot == null
                    || !"absent".equals(currentMarkerHash)
                    || !Objects.equals(currentHash, legacyStagingSnapshot.contentHash())
                    || legacyStagingSnapshot.targetIdentity().isEmpty()) {
                return false;
            }
            Path suppliedRoot = installRoot.toPath().toAbsolutePath().normalize();
            Path suppliedTarget = target.toPath().toAbsolutePath().normalize();
            if (!Objects.equals(suppliedTarget.getParent(), suppliedRoot)
                    || !Objects.equals(suppliedTarget.getFileName().toString(), id)
                    || Files.isSameFile(suppliedRoot, normalizedStagingRoot)
                    || Files.isSameFile(suppliedTarget, legacyStagingSnapshot.normalizedTarget())) {
                return false;
            }
            verifyAuthority(source, entry, stagedHash);
            verifyLegacyWorldSnapshot();
            return true;
        }

        private void verifyAuthority(File source, Entry entry, String stagedHash) throws IOException {
            verifyAuthorityState();
            Path suppliedSource = source.toPath().toAbsolutePath().normalize();
            if (!suppliedSource.equals(normalizedSource)) {
                throw new IOException("Verified datapack staging authorization was paired with a different path");
            }
            if (!Objects.equals(entry.id, id)
                    || !Objects.equals(entry.url, url)
                    || !Objects.equals(entry.versionId, versionId)
                    || !Objects.equals(entry.versionNumber, versionNumber)
                    || !Objects.equals(entry.sha1, sha1)
                    || !Objects.equals(stagedHash, desiredHash)) {
                throw new IOException("Verified datapack staging authorization was paired with different metadata");
            }
        }

        private void verifyAuthorityState() throws IOException {
            verifyStagingRoot();
            verifyPathIdentity(normalizedSource, realSource, sourceIdentity, "verified datapack extraction");
            Manifest committedManifest = readCommittedManifest(normalizedRoot.toFile());
            Entry committed = committedManifest.findById(id);
            if (!manifestEntrySnapshotMatches(committed, committedEntrySnapshot)) {
                throw new IOException("Committed datapack staging authority changed before installation");
            }
            if (committedEntrySnapshot != null && !legacyReplacementAuthorized) {
                throw new IOException("Committed datapack staging authority conflicts with " + id);
            }
        }

        private static boolean manifestEntrySnapshotMatches(Entry current, Entry expected) {
            if (current == null || expected == null) {
                return current == expected;
            }
            return Objects.equals(current.id, expected.id)
                    && Objects.equals(current.url, expected.url)
                    && Objects.equals(current.versionId, expected.versionId)
                    && Objects.equals(current.versionNumber, expected.versionNumber)
                    && Objects.equals(current.sha1, expected.sha1)
                    && Objects.equals(current.filename, expected.filename)
                    && Objects.equals(current.etag, expected.etag)
                    && Objects.equals(current.lastModified, expected.lastModified)
                    && current.installedEpoch == expected.installedEpoch
                    && current.structuresImported == expected.structuresImported
                    && copyList(current.structureKeys).equals(copyList(expected.structureKeys))
                    && copyList(current.templateKeys).equals(copyList(expected.templateKeys))
                    && Objects.equals(current.importedTargets, expected.importedTargets)
                    && Objects.equals(current.importAttempts, expected.importAttempts)
                    && Objects.equals(current.importedBundles, expected.importedBundles);
        }

        private void verifyLegacyWorldSnapshot() throws IOException {
            verifyAuthorityState();
            if (legacyStagingSnapshot == null || legacyStagingSnapshot.targetIdentity().isEmpty()) {
                throw new IOException("Missing stable canonical legacy datapack staging snapshot for " + id);
            }
            if (!Objects.equals(legacyStagingSnapshot.normalizedTarget().getParent(), normalizedStagingRoot)
                    || !Files.isSameFile(
                    legacyStagingSnapshot.normalizedTarget().getParent(), normalizedStagingRoot)
                    || !sameScratchVolume(
                    legacyStagingSnapshot.normalizedTarget(), normalizedStagingRoot)) {
                throw new IOException("Changed or unsafe canonical legacy datapack staging target for " + id);
            }
            verifyDirectorySnapshot(
                    legacyStagingSnapshot.normalizedTarget().toFile(),
                    normalizedStagingRoot.toFile(),
                    legacyStagingSnapshot.contentHash(),
                    legacyStagingSnapshot.markerHash(),
                    legacyStagingSnapshot.targetIdentity(),
                    "canonical legacy datapack staging target"
            );
            if (!Objects.equals(
                    legacyStagingSnapshot.normalizedTarget().toRealPath(),
                    legacyStagingSnapshot.realTarget())) {
                throw new IOException("Changed canonical legacy datapack staging target identity for " + id);
            }
        }

        private static void verifyPathIdentity(
                Path normalized,
                Path expectedReal,
                String expectedIdentity,
                String purpose
        ) throws IOException {
            if (Files.isSymbolicLink(normalized)
                    || !Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)
                    || !Objects.equals(normalized.toRealPath(), expectedReal)
                    || (!expectedIdentity.isEmpty()
                    && !Objects.equals(directoryIdentity(normalized.toFile()), expectedIdentity))) {
                throw new IOException("Changed or unsafe " + purpose + " " + normalized);
            }
        }
    }

    public static final class Report {
        private final KList<String> updated = new KList<>();
        private final KList<String> upToDate = new KList<>();
        private final KList<String> failed = new KList<>();
        private boolean requiresRestart;

        public boolean changed() {
            return requiresRestart;
        }

        public KList<String> getUpdated() {
            return updated;
        }

        public KList<String> getUpToDate() {
            return upToDate;
        }

        public KList<String> getFailed() {
            return failed;
        }
    }

    public static final class Entry {
        public String url;
        public String id;
        public String versionId;
        public String versionNumber;
        public String sha1;
        public String filename;
        public String etag;
        public String lastModified;
        public long installedEpoch;
        public boolean structuresImported;
        public String stagingMetadata = "";
        public List<String> structureKeys = new ArrayList<>();
        public List<String> templateKeys = new ArrayList<>();
        public Map<String, String> installMetadata = new HashMap<>();
        public Map<String, String> importedTargets = new HashMap<>();
        public Map<String, String> importAttempts = new HashMap<>();
        public Map<String, Map<String, String>> importedBundles = new HashMap<>();
    }

    static final class StartupValidationCache {
        int schemaVersion;
        String minecraftVersion;
        int irisVersion;
        boolean autoIngest;
        boolean stripOverrides;
        List<String> urls;
        String localFingerprint;
    }

    public enum StartupValidationOutcome {
        READY,
        RESTART_REQUIRED,
        FAILED
    }

    private static final class Manifest {
        private List<Entry> entries = new ArrayList<>();

        private Entry find(String url) {
            for (Entry entry : entries) {
                if (entry.url != null && entry.url.equals(url)) {
                    return entry;
                }
            }
            return null;
        }

        private Entry findById(String id) {
            for (Entry entry : entries) {
                if (entry.id != null && entry.id.equals(id)) {
                    return entry;
                }
            }
            return null;
        }

        private void put(Entry entry) {
            for (int i = 0; i < entries.size(); i++) {
                Entry current = entries.get(i);
                if (current.url != null && current.url.equals(entry.url)) {
                    entries.set(i, entry);
                    return;
                }
            }
            entries.add(entry);
        }

        private boolean removeById(String id) {
            return entries.removeIf(entry -> id.equals(entry.id));
        }
    }

    static record DownloadResult(boolean notModified, String etag, String lastModified) {
    }

    static record InstallResult(boolean changed) {
    }

    public record ReapplyOutcome(
            ReapplyStatus status,
            Optional<Throwable> failure
    ) {
        public ReapplyOutcome {
            status = Objects.requireNonNull(status, "External datapack reapply status");
            failure = Objects.requireNonNull(failure, "External datapack reapply failure");
            if (status == ReapplyStatus.FAILED && failure.isEmpty()) {
                throw new IllegalArgumentException("Failed external datapack reapply requires a cause");
            }
            if (status != ReapplyStatus.FAILED && failure.isPresent()) {
                throw new IllegalArgumentException("Successful external datapack reapply cannot carry a cause");
            }
        }

        public static ReapplyOutcome success(boolean recovered, boolean repaired) {
            ReapplyStatus status;
            if (recovered && repaired) {
                status = ReapplyStatus.RECOVERED_AND_REPAIRED;
            } else if (recovered) {
                status = ReapplyStatus.RECOVERED;
            } else if (repaired) {
                status = ReapplyStatus.REPAIRED;
            } else {
                status = ReapplyStatus.UNCHANGED;
            }
            return new ReapplyOutcome(status, Optional.empty());
        }

        public static ReapplyOutcome failed(Throwable failure) {
            return new ReapplyOutcome(
                    ReapplyStatus.FAILED,
                    Optional.of(Objects.requireNonNull(failure, "External datapack reapply failure cause")));
        }

        public boolean succeeded() {
            return status != ReapplyStatus.FAILED;
        }

        public boolean changed() {
            return recovered() || repaired();
        }

        public boolean recovered() {
            return status == ReapplyStatus.RECOVERED
                    || status == ReapplyStatus.RECOVERED_AND_REPAIRED;
        }

        public boolean repaired() {
            return status == ReapplyStatus.REPAIRED
                    || status == ReapplyStatus.RECOVERED_AND_REPAIRED;
        }
    }

    public enum ReapplyStatus {
        UNCHANGED,
        RECOVERED,
        REPAIRED,
        RECOVERED_AND_REPAIRED,
        FAILED
    }

    static final class InstallExecution {
        private final InstallResult result;
        private final DatapackCoordinator coordinator;
        private final File stagedDir;
        private final Entry entry;
        private final String stagedHash;
        private final boolean verifyStagedSource;
        private final List<InstallPlan> publishedPlans;
        private final List<InstallPlan> unchangedPlans;
        private boolean verified;

        private InstallExecution(
                InstallResult result,
                DatapackCoordinator coordinator,
                File stagedDir,
                Entry entry,
                String stagedHash,
                boolean verifyStagedSource,
                List<InstallPlan> publishedPlans,
                List<InstallPlan> unchangedPlans
        ) {
            this.result = result;
            this.coordinator = coordinator;
            this.stagedDir = stagedDir;
            this.entry = copyEntry(entry);
            this.stagedHash = stagedHash;
            this.verifyStagedSource = verifyStagedSource;
            this.publishedPlans = List.copyOf(publishedPlans);
            this.unchangedPlans = List.copyOf(unchangedPlans);
        }

        InstallResult result() {
            return result;
        }

        private DatapackCoordinator coordinator() {
            return coordinator;
        }

        private File stagedDir() {
            return stagedDir;
        }

        private Entry entry() {
            return entry;
        }

        private String stagedHash() {
            return stagedHash;
        }

        private boolean verifyStagedSource() {
            return verifyStagedSource;
        }

        private List<InstallPlan> publishedPlans() {
            return publishedPlans;
        }

        private List<InstallPlan> unchangedPlans() {
            return unchangedPlans;
        }

        private boolean verified() {
            return verified;
        }

        private void markVerified() {
            verified = true;
        }
    }

    private record PackResources(
            List<String> structureKeys,
            List<String> structureSetKeys,
            List<String> templateKeys
    ) {
    }

    private record StagingInspection(
            boolean usable,
            boolean ownershipCorrected,
            boolean manifestChanged
    ) {
    }

    public record StructureScopeResources(
            String source,
            List<String> structureKeys,
            List<String> structureSetKeys
    ) {
    }

    private static final class EditableImportRemoval {
        private final List<PreparedEditableImport> prepared;
        private boolean closed;

        private EditableImportRemoval(List<PreparedEditableImport> prepared) {
            this.prepared = List.copyOf(prepared);
        }

        private List<StructureTransactionWriter.PreparedRemovalToken> recoveryTokens() {
            List<StructureTransactionWriter.PreparedRemovalToken> tokens = new ArrayList<>();
            for (PreparedEditableImport editableImport : prepared) {
                editableImport.removal().recoveryToken().ifPresent(tokens::add);
            }
            return List.copyOf(tokens);
        }

        private void claimRecoveryOwners(Path transactionRoot, CoordinatorJournal journal) throws IOException {
            int ownerIndex = 0;
            for (PreparedEditableImport editableImport : prepared) {
                Optional<StructureTransactionWriter.PreparedRemovalToken> token =
                        editableImport.removal().recoveryToken();
                if (token.isEmpty()) {
                    continue;
                }
                if (ownerIndex >= journal.editables.size()) {
                    throw new IOException("Missing datapack coordinator claim for editable structure removal");
                }
                CoordinatorEditable editable = journal.editables.get(ownerIndex++);
                StructureTransactionWriter.PreparedRemovalToken recoveryToken = token.get();
                if (!Objects.equals(recoveryToken.packRoot().toString(), editable.packRoot)
                        || !Objects.equals(recoveryToken.transactionId().toString(), editable.transactionId)) {
                    throw new IOException("Datapack coordinator editable claim order changed during preparation");
                }
                editableImport.removal().claimRecoveryOwner(
                        new StructureTransactionWriter.RecoveryOwner(
                                transactionRoot,
                                UUID.fromString(journal.transactionId),
                                UUID.fromString(editable.claimId)
                        )
                );
            }
            if (ownerIndex != journal.editables.size()) {
                throw new IOException("Unexpected datapack coordinator editable recovery claim");
            }
        }

        private void markCommitted() throws IOException {
            if (closed) {
                throw new IllegalStateException("Editable import removal transaction is closed");
            }
            for (PreparedEditableImport editableImport : prepared) {
                editableImport.removal().markCommitted();
            }
        }

        private void finishCommit() throws IOException {
            if (closed) {
                return;
            }
            IOException failure = null;
            for (PreparedEditableImport editableImport : prepared) {
                try {
                    editableImport.removal().finishCommit();
                } catch (IOException | RuntimeException cleanupFailure) {
                    IOException transactionFailure = cleanupFailure instanceof IOException ioFailure
                            ? ioFailure
                            : new IOException("Failed finalizing editable import removal", cleanupFailure);
                    if (failure == null) {
                        failure = transactionFailure;
                    } else {
                        failure.addSuppressed(transactionFailure);
                    }
                }
                if (editableImport.removal().changed()) {
                    try {
                        IrisData.getLoaded(editableImport.dataFolder())
                                .ifPresent(IrisData::invalidateStructureResources);
                    } catch (RuntimeException invalidationFailure) {
                        IOException transactionFailure = new IOException(
                                "Failed invalidating editable import structure resources",
                                invalidationFailure
                        );
                        if (failure == null) {
                            failure = transactionFailure;
                        } else {
                            failure.addSuppressed(transactionFailure);
                        }
                    }
                }
            }
            closed = true;
            if (failure != null) {
                throw failure;
            }
        }

        private void rollback() throws IOException {
            if (closed) {
                return;
            }
            IOException failure = null;
            for (int i = prepared.size() - 1; i >= 0; i--) {
                try {
                    prepared.get(i).removal().rollback();
                } catch (IOException rollbackFailure) {
                    if (failure == null) {
                        failure = rollbackFailure;
                    } else {
                        failure.addSuppressed(rollbackFailure);
                    }
                }
            }
            closed = true;
            if (failure != null) {
                throw failure;
            }
        }

        private void leaveForRecovery() throws IOException {
            if (closed) {
                return;
            }
            IOException failure = null;
            for (PreparedEditableImport editableImport : prepared) {
                try {
                    editableImport.removal().leaveForRecovery();
                } catch (IOException releaseFailure) {
                    if (failure == null) {
                        failure = releaseFailure;
                    } else {
                        failure.addSuppressed(releaseFailure);
                    }
                }
            }
            closed = true;
            if (failure != null) {
                throw failure;
            }
        }
    }

    private static final class DirectoryRemoval {
        private final List<DirectoryMove> moved;
        private int prepared;
        private boolean closed;

        private DirectoryRemoval(List<DirectoryMove> moved) {
            this.moved = List.copyOf(moved);
        }

        private List<DirectoryMove> moves() {
            return moved;
        }

        private void prepare() throws IOException {
            if (closed || prepared > 0) {
                throw new IllegalStateException("Datapack directory removal transaction is not new");
            }
            try {
                for (DirectoryMove directoryMove : moved) {
                    verifyDirectoryContainerIdentity(
                            directoryMove.target().getParentFile(),
                            directoryMove.targetRootIdentity(),
                            directoryMove.targetRootFileIdentity(),
                            "datapack removal target root"
                    );
                    verifyDirectoryContainerIdentity(
                            directoryMove.backup().getParentFile(),
                            directoryMove.scratchRootIdentity(),
                            directoryMove.scratchRootFileIdentity(),
                            "datapack removal scratch root"
                    );
                    verifyDirectorySnapshot(
                            directoryMove.target(),
                            new File(directoryMove.targetRootIdentity()),
                            directoryMove.originalHash(),
                            directoryMove.originalMarkerHash(),
                            directoryMove.originalIdentity(),
                            "datapack removal target"
                    );
                    moveNew(directoryMove.target().toPath(), directoryMove.backup().toPath());
                    prepared++;
                    forceDirectoryIfSupported(directoryMove.target().getParentFile().toPath());
                    forceDirectoryIfSupported(directoryMove.backup().getParentFile().toPath());
                    verifyDirectorySnapshot(
                            directoryMove.backup(),
                            new File(directoryMove.targetRootIdentity()),
                            directoryMove.originalHash(),
                            directoryMove.originalMarkerHash(),
                            directoryMove.originalIdentity(),
                            "datapack removal backup"
                    );
                }
            } catch (IOException removalFailure) {
                try {
                    rollback();
                } catch (IOException restoreFailure) {
                    removalFailure.addSuppressed(restoreFailure);
                }
                throw removalFailure;
            }
        }

        private void rollback() throws IOException {
            if (closed) {
                return;
            }
            IOException failure = null;
            for (int i = prepared - 1; i >= 0; i--) {
                DirectoryMove directoryMove = moved.get(i);
                if (!Files.exists(directoryMove.backup().toPath(), LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                try {
                    verifyDirectoryContainerIdentity(
                            directoryMove.target().getParentFile(),
                            directoryMove.targetRootIdentity(),
                            directoryMove.targetRootFileIdentity(),
                            "datapack removal target root"
                    );
                    verifyDirectoryContainerIdentity(
                            directoryMove.backup().getParentFile(),
                            directoryMove.scratchRootIdentity(),
                            directoryMove.scratchRootFileIdentity(),
                            "datapack removal scratch root"
                    );
                    verifyDirectorySnapshot(
                            directoryMove.backup(),
                            new File(directoryMove.targetRootIdentity()),
                            directoryMove.originalHash(),
                            directoryMove.originalMarkerHash(),
                            directoryMove.originalIdentity(),
                            "datapack removal backup"
                    );
                    if (pathExists(directoryMove.target().toPath(), "datapack removal target")) {
                        throw new IOException("Datapack removal target was concurrently recreated at "
                                + directoryMove.target().getPath());
                    }
                    DatapackIngestService.moveNew(
                            directoryMove.backup().toPath(),
                            directoryMove.target().toPath()
                    );
                    forceDirectoryIfSupported(directoryMove.target().getParentFile().toPath());
                    forceDirectoryIfSupported(directoryMove.backup().getParentFile().toPath());
                } catch (IOException restoreFailure) {
                    if (failure == null) {
                        failure = restoreFailure;
                    } else {
                        failure.addSuppressed(restoreFailure);
                    }
                }
            }
            for (int i = 0; i < prepared; i++) {
                DirectoryMove directoryMove = moved.get(i);
                directoryMove.backup().getParentFile().delete();
            }
            closed = true;
            if (failure != null) {
                throw failure;
            }
        }

        private void finishCommit() throws IOException {
            if (closed) {
                return;
            }
            IOException failure = null;
            for (int i = 0; i < prepared; i++) {
                DirectoryMove directoryMove = moved.get(i);
                try {
                    verifyDirectoryContainerIdentity(
                            directoryMove.backup().getParentFile(),
                            directoryMove.scratchRootIdentity(),
                            directoryMove.scratchRootFileIdentity(),
                            "datapack removal scratch root"
                    );
                    deleteOriginalBackupIfPresent(
                            directoryMove.backup(),
                            new File(directoryMove.targetRootIdentity()),
                            directoryMove.originalHash(),
                            directoryMove.originalMarkerHash(),
                            directoryMove.originalIdentity()
                    );
                } catch (IOException cleanupFailure) {
                    failure = appendIOException(failure, cleanupFailure);
                }
                directoryMove.backup().getParentFile().delete();
            }
            closed = true;
            if (failure != null) {
                throw failure;
            }
        }
    }

    private static final class ManifestWrite implements AutoCloseable {
        private final Path staged;
        private final Path target;
        private final Path rollback;
        private boolean published;

        private ManifestWrite(Path staged, Path target, Path rollback) {
            this.staged = staged;
            this.target = target;
            this.rollback = rollback;
        }

        private void publish() throws IOException {
            if (published) {
                throw new IllegalStateException("Datapack manifest write was already published");
            }
            try {
                move(staged, target);
                published = true;
            } catch (IOException publishFailure) {
                try {
                    restoreOriginal();
                } catch (IOException restoreFailure) {
                    publishFailure.addSuppressed(restoreFailure);
                }
                throw publishFailure;
            }
            forceDirectoryIfSupported(Objects.requireNonNull(target.getParent(), "manifest parent"));
        }

        private boolean published() {
            return published;
        }

        private void discard() throws IOException {
            IOException failure = null;
            try {
                Files.deleteIfExists(staged);
            } catch (IOException cleanupFailure) {
                failure = cleanupFailure;
            }
            if (rollback != null) {
                try {
                    Files.deleteIfExists(rollback);
                } catch (IOException cleanupFailure) {
                    if (failure == null) {
                        failure = cleanupFailure;
                    } else {
                        failure.addSuppressed(cleanupFailure);
                    }
                }
            }
            if (failure != null) {
                throw failure;
            }
        }

        @Override
        public void close() throws IOException {
            discard();
        }

        private void restoreOriginal() throws IOException {
            if (rollback == null) {
                Files.deleteIfExists(target);
            } else {
                move(rollback, target);
            }
            forceDirectoryIfSupported(Objects.requireNonNull(target.getParent(), "manifest parent"));
        }
    }

    private record PreparedEditableImport(
            File dataFolder,
            StructureTransactionWriter.PreparedRemoval removal
    ) {
    }

    private record MetadataEntry(Path path, String relativePath) {
    }

    private record DirectoryMove(
            File target,
            File backup,
            String originalHash,
            String originalMarkerHash,
            String originalIdentity,
            String targetRootIdentity,
            String scratchRootIdentity,
            String targetRootFileIdentity,
            String scratchRootFileIdentity
    ) {
    }

    record InstallPlan(
            File target,
            File pending,
            File backup,
            File pendingRoot,
            boolean hadTarget,
            boolean publishRequired,
            boolean contentChanged,
            String originalHash,
            String desiredHash,
            String originalMarkerHash,
            String desiredMarkerHash,
            String originalIdentity,
            String desiredIdentity,
            String targetRootIdentity,
            String scratchRootIdentity,
            String targetRootFileIdentity,
            String scratchRootFileIdentity,
            String id,
            String url,
            VerifiedStagingInstall legacyWorldAuthorization
    ) {
    }

    private record LegacyStagingSnapshot(
            Path normalizedTarget,
            Path realTarget,
            String targetIdentity,
            String contentHash,
            String markerHash
    ) {
    }

    private record StagingScratch(StagingScratchKind kind, String id, Path path) {
    }

    @FunctionalInterface
    interface DirectoryDeleter {
        void delete(File directory);
    }

    private static final class Ownership {
        private final int schemaVersion;
        private final String id;
        private final String url;
        private final String versionId;
        private final String versionNumber;
        private final String sha1;
        private final String contentHash;
        private final List<String> structureKeys;
        private final List<String> templateKeys;

        private Ownership(
                int schemaVersion,
                String id,
                String url,
                String versionId,
                String versionNumber,
                String sha1,
                String contentHash,
                List<String> structureKeys,
                List<String> templateKeys
        ) {
            this.schemaVersion = schemaVersion;
            this.id = id;
            this.url = url;
            this.versionId = versionId;
            this.versionNumber = versionNumber;
            this.sha1 = sha1;
            this.contentHash = contentHash;
            this.structureKeys = List.copyOf(structureKeys);
            this.templateKeys = List.copyOf(templateKeys);
        }

        private Entry toEntry() {
            Entry entry = new Entry();
            entry.id = id;
            entry.url = url;
            entry.versionId = versionId;
            entry.versionNumber = versionNumber;
            entry.sha1 = sha1;
            entry.structureKeys = normalizeKeys(structureKeys);
            entry.templateKeys = normalizeKeys(templateKeys);
            return entry;
        }
    }
}
