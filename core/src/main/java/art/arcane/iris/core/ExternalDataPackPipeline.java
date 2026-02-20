package art.arcane.iris.core;

import art.arcane.iris.Iris;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.object.IrisObject;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.engine.object.TileData;
import art.arcane.iris.util.common.data.B;
import art.arcane.iris.util.common.math.Vector3i;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;
import art.arcane.volmlib.util.nbt.io.NBTDeserializer;
import art.arcane.volmlib.util.nbt.io.NamedTag;
import art.arcane.volmlib.util.nbt.tag.ByteArrayTag;
import art.arcane.volmlib.util.nbt.tag.ByteTag;
import art.arcane.volmlib.util.nbt.tag.CompoundTag;
import art.arcane.volmlib.util.nbt.tag.DoubleTag;
import art.arcane.volmlib.util.nbt.tag.FloatTag;
import art.arcane.volmlib.util.nbt.tag.IntTag;
import art.arcane.volmlib.util.nbt.tag.IntArrayTag;
import art.arcane.volmlib.util.nbt.tag.ListTag;
import art.arcane.volmlib.util.nbt.tag.LongTag;
import art.arcane.volmlib.util.nbt.tag.LongArrayTag;
import art.arcane.volmlib.util.nbt.tag.NumberTag;
import art.arcane.volmlib.util.nbt.tag.ShortTag;
import art.arcane.volmlib.util.nbt.tag.StringTag;
import art.arcane.volmlib.util.nbt.tag.Tag;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.Bukkit;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ExternalDataPackPipeline {
    private static final Pattern MODRINTH_VERSION_URL = Pattern.compile("^https?://modrinth\\.com/(?:datapack|mod|plugin|resourcepack)/([^/?#]+)/version/([^/?#]+).*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern STRUCTURE_ENTRY = Pattern.compile("(?i)(?:^|.*/)data/([^/]+)/(?:structure|structures)/(.+\\.nbt)$");
    private static final String PACK_NAME = "datapack-imports";
    private static final String IMPORT_PREFIX = "imports";
    private static final int CONNECT_TIMEOUT_MS = 4000;
    private static final int READ_TIMEOUT_MS = 8000;
    private static final int IMPORT_PARALLELISM = Math.max(1, Math.min(8, Runtime.getRuntime().availableProcessors()));
    private static final int MAX_IN_FLIGHT = Math.max(2, IMPORT_PARALLELISM * 3);
    private static final List<String> PINNED_MODRINTH_URLS = List.of(
            "https://modrinth.com/datapack/dungeons-and-taverns/version/v5.1.0"
    );
    private static final Map<String, BlockData> BLOCK_DATA_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, String> PACK_ENVIRONMENT_CACHE = new ConcurrentHashMap<>();
    private static final BlockData AIR = B.getAir();

    private ExternalDataPackPipeline() {
    }

    public static void syncPinnedDatapacks(File sourceFolder) {
        if (sourceFolder == null) {
            return;
        }

        File cacheFolder = Iris.instance.getDataFolder("cache", "datapacks");
        cacheFolder.mkdirs();
        boolean offline = false;
        int downloaded = 0;
        int upToDate = 0;
        int restoredFromCache = 0;
        int failed = 0;

        for (String pageUrl : PINNED_MODRINTH_URLS) {
            if (offline) {
                break;
            }

            try {
                ModrinthFile file = resolveModrinthFile(pageUrl);
                if (file == null) {
                    failed++;
                    continue;
                }

                File output = new File(sourceFolder, file.outputFileName());
                File cached = new File(cacheFolder, file.outputFileName());
                if (isUpToDate(output, file.sha1)) {
                    upToDate++;
                    continue;
                }

                if (isUpToDate(cached, file.sha1)) {
                    copyFile(cached, output);
                    if (isUpToDate(output, file.sha1)) {
                        restoredFromCache++;
                        continue;
                    }
                    output.delete();
                }

                downloadToFile(file.url, cached);
                if (!isUpToDate(cached, file.sha1)) {
                    failed++;
                    cached.delete();
                    Iris.warn("Pinned datapack hash mismatch for " + pageUrl);
                    continue;
                }

                copyFile(cached, output);
                if (!isUpToDate(output, file.sha1)) {
                    failed++;
                    output.delete();
                    Iris.warn("Pinned datapack output write mismatch for " + pageUrl);
                    continue;
                }

                downloaded++;
                Iris.info("Downloaded pinned datapack: " + output.getName());
            } catch (IOException e) {
                offline = true;
                failed++;
                Iris.warn("Pinned datapack sync skipped because Iris appears offline: " + e.getMessage());
            } catch (Throwable e) {
                failed++;
                Iris.warn("Failed pinned datapack sync for " + pageUrl + ": " + e.getMessage());
                Iris.reportError(e);
            }
        }

        if (downloaded > 0 || upToDate > 0 || restoredFromCache > 0 || failed > 0) {
            Iris.info("Pinned datapack sync: downloaded=" + downloaded
                    + ", upToDate=" + upToDate
                    + ", restoredFromCache=" + restoredFromCache
                    + ", failed=" + failed);
        }
    }

    public static int removeLegacyWorldDatapackCopies(File sourceFolder, KList<File> worldDatapackFolders) {
        if (sourceFolder == null) {
            return 0;
        }

        Set<File> datapackFolders = new LinkedHashSet<>();
        if (worldDatapackFolders != null) {
            for (File folder : worldDatapackFolders) {
                if (folder != null) {
                    datapackFolders.add(folder);
                }
            }
        }
        collectWorldDatapackFolders(datapackFolders);
        if (datapackFolders.isEmpty()) {
            return 0;
        }

        Set<String> managedNames = new HashSet<>();
        File[] sourceEntries = sourceFolder.listFiles();
        if (sourceEntries != null) {
            for (File sourceEntry : sourceEntries) {
                if (sourceEntry == null || sourceEntry.getName().startsWith(".")) {
                    continue;
                }
                if (isArchive(sourceEntry.getName())
                        || (sourceEntry.isDirectory() && looksLikeDatapackDirectory(sourceEntry))) {
                    managedNames.add(sourceEntry.getName());
                }
            }
        }

        JSONObject index = readExistingIndex(new File(Iris.instance.getDataFolder("packs", PACK_NAME), "datapack-index.json"));
        JSONArray indexedSources = index.optJSONArray("sources");
        if (indexedSources != null) {
            for (int i = 0; i < indexedSources.length(); i++) {
                JSONObject indexedSource = indexedSources.optJSONObject(i);
                if (indexedSource == null) {
                    continue;
                }

                String sourceName = indexedSource.optString("sourceName", "");
                if (!sourceName.isBlank()) {
                    managedNames.add(sourceName);
                }
            }
        }

        int removed = 0;
        for (File datapacksFolder : datapackFolders) {
            if (datapacksFolder == null || !datapacksFolder.exists() || !datapacksFolder.isDirectory()) {
                continue;
            }

            File[] datapackEntries = datapacksFolder.listFiles();
            if (datapackEntries == null || datapackEntries.length == 0) {
                continue;
            }

            for (File datapackEntry : datapackEntries) {
                if (datapackEntry == null || datapackEntry.getName().startsWith(".")) {
                    continue;
                }

                String lowerName = datapackEntry.getName().toLowerCase(Locale.ROOT);
                boolean managed = managedNames.contains(datapackEntry.getName()) || lowerName.startsWith("modrinth-");
                if (!managed) {
                    continue;
                }

                deleteFolder(datapackEntry);
                if (!datapackEntry.exists()) {
                    removed++;
                }
            }
        }

        return removed;
    }

    private static void collectWorldDatapackFolders(Set<File> folders) {
        try {
            File container = Bukkit.getWorldContainer();
            if (container == null || !container.exists() || !container.isDirectory()) {
                return;
            }

            File rootDatapacks = new File(container, "datapacks");
            if (rootDatapacks.exists() && rootDatapacks.isDirectory()) {
                folders.add(rootDatapacks);
            }

            File[] children = container.listFiles(File::isDirectory);
            if (children == null || children.length == 0) {
                return;
            }

            for (File child : children) {
                File datapacks = new File(child, "datapacks");
                if (datapacks.exists() && datapacks.isDirectory()) {
                    folders.add(datapacks);
                }
            }
        } catch (Throwable e) {
            Iris.reportError(e);
        }
    }

    public static ImportSummary importDatapackStructures(File sourceFolder) {
        ImportSummary summary = new ImportSummary();
        if (sourceFolder == null || !sourceFolder.exists()) {
            return summary;
        }
        PACK_ENVIRONMENT_CACHE.clear();

        File importPackFolder = Iris.instance.getDataFolder("packs", PACK_NAME);
        File indexFile = new File(importPackFolder, "datapack-index.json");
        importPackFolder.mkdirs();

        JSONObject oldIndex = readExistingIndex(indexFile);
        Map<String, JSONObject> oldSources = mapExistingSources(oldIndex);
        JSONArray newSources = new JSONArray();
        Set<String> seenSourceKeys = new HashSet<>();

        List<SourceInput> sources = discoverSources(sourceFolder);
        if (sources.isEmpty()) {
            pruneRemovedSourceFolders(oldSources, seenSourceKeys);
            writeIndex(indexFile, newSources, summary);
            return summary;
        }

        for (SourceInput sourceInput : sources) {
            File entry = sourceInput.source();
            if (entry == null || !entry.exists()) {
                continue;
            }

            SourceDescriptor sourceDescriptor = createSourceDescriptor(entry, sourceInput.targetPack(), sourceInput.requiredEnvironment());
            if (sourceDescriptor.requiredEnvironment() != null) {
                String packEnvironment = resolvePackEnvironment(sourceDescriptor.targetPack());
                if (packEnvironment == null || !packEnvironment.equals(sourceDescriptor.requiredEnvironment())) {
                    summary.skipped++;
                    Iris.warn("Skipped external datapack source " + sourceDescriptor.sourceName()
                            + " targetPack=" + sourceDescriptor.targetPack()
                            + " requiredEnvironment=" + sourceDescriptor.requiredEnvironment()
                            + " packEnvironment=" + (packEnvironment == null ? "unknown" : packEnvironment));
                    continue;
                }
            }

            seenSourceKeys.add(sourceDescriptor.sourceKey());
            File sourceRoot = resolveSourceRoot(sourceDescriptor.targetPack(), sourceDescriptor.sourceKey());
            JSONObject cachedSource = oldSources.get(sourceDescriptor.sourceKey());
            String cachedTargetPack = cachedSource == null
                    ? null
                    : sanitizePackName(cachedSource.optString("targetPack", defaultTargetPack()));
            boolean sameTargetPack = cachedTargetPack != null && cachedTargetPack.equals(sourceDescriptor.targetPack());

            if (cachedSource != null
                    && sourceDescriptor.fingerprint().equals(cachedSource.optString("fingerprint", ""))
                    && sameTargetPack
                    && sourceRoot.exists()) {
                newSources.put(cachedSource);
                addSourceToSummary(summary, cachedSource, true);
                continue;
            }

            if (cachedTargetPack != null && !cachedTargetPack.equals(sourceDescriptor.targetPack())) {
                File previousSourceRoot = resolveSourceRoot(cachedTargetPack, sourceDescriptor.sourceKey());
                deleteFolder(previousSourceRoot);
            }

            deleteFolder(sourceRoot);
            sourceRoot.mkdirs();
            JSONObject sourceResult = convertSource(entry, sourceDescriptor, sourceRoot);
            newSources.put(sourceResult);
            addSourceToSummary(summary, sourceResult, false);
        }

        pruneRemovedSourceFolders(oldSources, seenSourceKeys);
        writeIndex(indexFile, newSources, summary);
        return summary;
    }

    private static List<SourceInput> discoverSources(File sourceFolder) {
        List<SourceInput> sources = new ArrayList<>();
        File[] entries = sourceFolder.listFiles();
        if (entries == null || entries.length == 0) {
            return sources;
        }

        Arrays.sort(entries, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        String defaultPack = defaultTargetPack();
        for (File entry : entries) {
            if (entry == null || !entry.exists() || entry.getName().startsWith(".")) {
                continue;
            }

            if (entry.isDirectory() && !looksLikeDatapackDirectory(entry)) {
                collectContainerSources(entry, sources, defaultPack);
                continue;
            }

            if (!(entry.isDirectory() || isArchive(entry.getName()))) {
                continue;
            }

            SourceControl sourceControl = resolveSourceControl(entry, defaultPack, null);
            sources.add(new SourceInput(entry, sourceControl.targetPack(), sourceControl.requiredEnvironment()));
        }

        return sources;
    }

    private static void collectContainerSources(File container, List<SourceInput> sources, String defaultPack) {
        File[] children = container.listFiles();
        if (children == null || children.length == 0) {
            return;
        }

        Arrays.sort(children, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        SourceControl containerControl = resolveContainerControl(container, defaultPack);
        for (File child : children) {
            if (child == null || !child.exists() || child.getName().startsWith(".")) {
                continue;
            }
            if (!(child.isDirectory() || isArchive(child.getName()))) {
                continue;
            }

            SourceControl sourceControl = resolveSourceControl(child, containerControl.targetPack(), containerControl.requiredEnvironment());
            sources.add(new SourceInput(child, sourceControl.targetPack(), sourceControl.requiredEnvironment()));
        }
    }

    private static SourceControl resolveContainerControl(File container, String defaultPack) {
        String targetPack = sanitizePackName(container.getName());
        if (targetPack.isEmpty()) {
            targetPack = sanitizePackName(defaultPack);
        }
        if (targetPack.isEmpty()) {
            targetPack = defaultTargetPack();
        }

        File control = new File(container, ".iris-pack.json");
        if (!control.exists() || !control.isFile()) {
            return new SourceControl(targetPack, null);
        }

        return parseSourceControl(control, targetPack, null);
    }

    private static SourceControl resolveSourceControl(File source, String targetPack, String requiredEnvironment) {
        String sanitizedPack = sanitizePackName(targetPack);
        if (sanitizedPack.isEmpty()) {
            sanitizedPack = defaultTargetPack();
        }
        String normalizedEnvironment = normalizeEnvironment(requiredEnvironment);
        File controlFile = findSourceControlFile(source);
        if (controlFile == null) {
            return new SourceControl(sanitizedPack, normalizedEnvironment);
        }
        return parseSourceControl(controlFile, sanitizedPack, normalizedEnvironment);
    }

    private static SourceControl parseSourceControl(File controlFile, String defaultTargetPack, String defaultRequiredEnvironment) {
        String targetPack = defaultTargetPack;
        String requiredEnvironment = defaultRequiredEnvironment;
        try {
            String raw = Files.readString(controlFile.toPath(), StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(raw);
            String configuredPack = sanitizePackName(json.optString("targetPack", ""));
            if (!configuredPack.isEmpty()) {
                targetPack = configuredPack;
            }

            String configuredEnvironment = normalizeEnvironment(json.optString("environment", json.optString("dimension", "")));
            if (configuredEnvironment != null) {
                requiredEnvironment = configuredEnvironment;
            }
        } catch (Throwable e) {
            Iris.warn("Failed to parse external datapack control file " + controlFile.getPath());
            Iris.reportError(e);
        }

        return new SourceControl(targetPack, requiredEnvironment);
    }

    private static File findSourceControlFile(File source) {
        if (source == null) {
            return null;
        }

        if (source.isDirectory()) {
            File hidden = new File(source, ".iris-import.json");
            if (hidden.exists() && hidden.isFile()) {
                return hidden;
            }

            File plain = new File(source, "iris-import.json");
            if (plain.exists() && plain.isFile()) {
                return plain;
            }
        }

        File parent = source.getParentFile();
        if (parent == null) {
            return null;
        }

        File exact = new File(parent, source.getName() + ".iris.json");
        if (exact.exists() && exact.isFile()) {
            return exact;
        }

        String stripped = stripExtension(source.getName());
        if (!stripped.equals(source.getName())) {
            File strippedFile = new File(parent, stripped + ".iris.json");
            if (strippedFile.exists() && strippedFile.isFile()) {
                return strippedFile;
            }
        }

        return null;
    }

    private static String defaultTargetPack() {
        String configured = sanitizePackName(IrisSettings.get().getGenerator().getDefaultWorldType());
        if (!configured.isEmpty()) {
            return configured;
        }
        return PACK_NAME;
    }

    private static String sanitizePackName(String value) {
        String cleaned = sanitizePath(value).replace("/", "_");
        if (cleaned.contains("..")) {
            cleaned = cleaned.replace("..", "_");
        }
        return cleaned;
    }

    private static String normalizeEnvironment(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if (normalized.isEmpty()) {
            return null;
        }

        return switch (normalized) {
            case "NORMAL", "OVERWORLD" -> "OVERWORLD";
            case "NETHER", "THE_NETHER" -> "NETHER";
            case "END", "THE_END" -> "THE_END";
            default -> null;
        };
    }

    private static boolean looksLikeDatapackDirectory(File directory) {
        if (directory == null || !directory.isDirectory()) {
            return false;
        }

        File packMeta = new File(directory, "pack.mcmeta");
        if (packMeta.exists() && packMeta.isFile()) {
            return true;
        }

        File dataFolder = new File(directory, "data");
        return dataFolder.exists() && dataFolder.isDirectory();
    }

    private static String resolvePackEnvironment(String targetPack) {
        String pack = sanitizePackName(targetPack);
        if (pack.isEmpty()) {
            return null;
        }

        return PACK_ENVIRONMENT_CACHE.computeIfAbsent(pack, ExternalDataPackPipeline::resolvePackEnvironmentInternal);
    }

    private static String resolvePackEnvironmentInternal(String targetPack) {
        try {
            File packFolder = Iris.instance.getDataFolder("packs", targetPack);
            if (!packFolder.exists() || !packFolder.isDirectory()) {
                return null;
            }

            IrisData data = IrisData.get(packFolder);
            IrisDimension dimension = data.getDimensionLoader().load(targetPack, false);
            if (dimension == null) {
                String[] keys = data.getDimensionLoader().getPossibleKeys();
                if (keys.length > 0) {
                    dimension = data.getDimensionLoader().load(keys[0], false);
                }
            }
            if (dimension == null) {
                return null;
            }

            World.Environment environment = dimension.getEnvironment();
            if (environment == null) {
                return "OVERWORLD";
            }

            return normalizeEnvironment(environment.name());
        } catch (Throwable e) {
            Iris.reportError(e);
            return null;
        }
    }

    private static File resolveSourceRoot(String targetPack, String sourceKey) {
        String pack = sanitizePackName(targetPack);
        if (pack.isEmpty()) {
            pack = defaultTargetPack();
        }
        return new File(Iris.instance.getDataFolder("packs", pack), "objects/" + IMPORT_PREFIX + "/" + sourceKey);
    }

    private static SourceDescriptor createSourceDescriptor(File entry, String targetPack, String requiredEnvironment) {
        String base = entry.getName();
        String sanitized = sanitizePath(stripExtension(base));
        if (sanitized.isEmpty()) {
            sanitized = "source";
        }
        String sourceHash = shortHash(entry.getAbsolutePath());
        String sourceKey = sanitized + "-" + sourceHash;
        String fingerprint = entry.isFile()
                ? "file:" + entry.length() + ":" + entry.lastModified()
                : "dir:" + directoryFingerprint(entry);
        String pack = sanitizePackName(targetPack);
        if (pack.isEmpty()) {
            pack = defaultTargetPack();
        }
        return new SourceDescriptor(sourceKey, base, fingerprint, pack, normalizeEnvironment(requiredEnvironment));
    }

    private static String directoryFingerprint(File directory) {
        long files = 0L;
        long size = 0L;
        long latest = 0L;
        ArrayDeque<File> queue = new ArrayDeque<>();
        queue.add(directory);
        while (!queue.isEmpty()) {
            File next = queue.removeFirst();
            File[] children = next.listFiles();
            if (children == null) {
                continue;
            }
            for (File child : children) {
                if (child == null || child.getName().startsWith(".")) {
                    continue;
                }
                if (child.isDirectory()) {
                    queue.add(child);
                    continue;
                }
                files++;
                size += child.length();
                latest = Math.max(latest, child.lastModified());
            }
        }
        return files + ":" + size + ":" + latest;
    }

    private static JSONObject convertSource(File entry, SourceDescriptor sourceDescriptor, File sourceRoot) {
        SourceConversion conversion = new SourceConversion(
                sourceDescriptor.sourceKey(),
                sourceDescriptor.sourceName(),
                sourceDescriptor.targetPack(),
                sourceDescriptor.requiredEnvironment()
        );
        if (entry.isDirectory()) {
            convertDirectory(entry, conversion, sourceRoot);
        } else {
            convertArchive(entry, conversion, sourceRoot);
        }
        return conversion.toJson(sourceDescriptor.fingerprint());
    }

    private static void convertDirectory(File source, SourceConversion conversion, File sourceRoot) {
        ExecutorService executorService = Executors.newFixedThreadPool(IMPORT_PARALLELISM);
        ExecutorCompletionService<ConversionResult> completionService = new ExecutorCompletionService<>(executorService);
        int inFlight = 0;
        ArrayDeque<File> queue = new ArrayDeque<>();
        queue.add(source);
        try {
            while (!queue.isEmpty()) {
                File next = queue.removeFirst();
                File[] children = next.listFiles();
                if (children == null) {
                    continue;
                }

                Arrays.sort(children, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
                for (File child : children) {
                    if (child == null || child.getName().startsWith(".")) {
                        continue;
                    }
                    if (child.isDirectory()) {
                        queue.add(child);
                        continue;
                    }
                    if (!child.getName().toLowerCase(Locale.ROOT).endsWith(".nbt")) {
                        continue;
                    }
                    conversion.nbtScanned++;
                    String relative = source.toPath().relativize(child.toPath()).toString().replace('\\', '/');
                    EntryPath entryPath = resolveEntryPath(relative);
                    if (entryPath == null) {
                        conversion.skipped++;
                        continue;
                    }

                    String objectKey = conversion.reserveObjectKey(entryPath.namespace, entryPath.structurePath);
                    if (objectKey == null) {
                        conversion.failed++;
                        continue;
                    }

                    try {
                        byte[] bytes = Files.readAllBytes(child.toPath());
                        completionService.submit(() -> convertNbt(bytes, entryPath, objectKey, sourceRoot));
                        inFlight++;
                    } catch (Throwable e) {
                        conversion.failed++;
                        Iris.warn("Failed to convert datapack structure " + relative + " from " + source.getName());
                        Iris.reportError(e);
                    }

                    while (inFlight >= MAX_IN_FLIGHT) {
                        applyResult(conversion, takeResult(completionService));
                        inFlight--;
                    }
                }
            }
        } finally {
            while (inFlight > 0) {
                applyResult(conversion, takeResult(completionService));
                inFlight--;
            }
            executorService.shutdown();
        }
    }

    private static void convertArchive(File source, SourceConversion conversion, File sourceRoot) {
        ExecutorService executorService = Executors.newFixedThreadPool(IMPORT_PARALLELISM);
        ExecutorCompletionService<ConversionResult> completionService = new ExecutorCompletionService<>(executorService);
        int inFlight = 0;
        try (ZipFile zipFile = new ZipFile(source)) {
            List<? extends ZipEntry> entries = zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .filter(entry -> entry.getName().toLowerCase(Locale.ROOT).endsWith(".nbt"))
                    .sorted(Comparator.comparing(ZipEntry::getName, String.CASE_INSENSITIVE_ORDER))
                    .toList();

            for (ZipEntry zipEntry : entries) {
                conversion.nbtScanned++;
                EntryPath entryPath = resolveEntryPath(zipEntry.getName());
                if (entryPath == null) {
                    conversion.skipped++;
                    continue;
                }

                String objectKey = conversion.reserveObjectKey(entryPath.namespace, entryPath.structurePath);
                if (objectKey == null) {
                    conversion.failed++;
                    continue;
                }

                try (InputStream inputStream = zipFile.getInputStream(zipEntry);
                     ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                    inputStream.transferTo(outputStream);
                    byte[] bytes = outputStream.toByteArray();
                    completionService.submit(() -> convertNbt(bytes, entryPath, objectKey, sourceRoot));
                    inFlight++;
                } catch (Throwable e) {
                    conversion.failed++;
                    Iris.warn("Failed to convert datapack structure " + zipEntry.getName() + " from " + source.getName());
                    Iris.reportError(e);
                }

                while (inFlight >= MAX_IN_FLIGHT) {
                    applyResult(conversion, takeResult(completionService));
                    inFlight--;
                }
            }
        } catch (Throwable e) {
            conversion.failed++;
            Iris.warn("Failed to read datapack archive " + source.getName());
            Iris.reportError(e);
        } finally {
            while (inFlight > 0) {
                applyResult(conversion, takeResult(completionService));
                inFlight--;
            }
            executorService.shutdown();
        }
    }

    private static ConversionResult takeResult(ExecutorCompletionService<ConversionResult> completionService) {
        try {
            Future<ConversionResult> future = completionService.take();
            return future.get();
        } catch (Throwable e) {
            Iris.reportError(e);
            return ConversionResult.failed();
        }
    }

    private static void applyResult(SourceConversion conversion, ConversionResult result) {
        if (!result.success) {
            conversion.failed++;
            return;
        }

        conversion.converted++;
        conversion.blockEntities += result.blockEntities;
        if (result.entitiesIgnored) {
            conversion.entitiesIgnored++;
        }
        if (result.record != null) {
            conversion.objects.put(result.record);
        }
    }

    private static ConversionResult convertNbt(byte[] bytes, EntryPath entryPath, String objectKey, File sourceRoot) throws IOException {
        NamedTag namedTag = readNamedTag(bytes);
        Tag<?> rootTag = namedTag.getTag();
        if (!(rootTag instanceof CompoundTag compoundTag)) {
            return ConversionResult.failed();
        }

        IrisObject object = toObject(compoundTag);
        if (object == null) {
            return ConversionResult.failed();
        }

        String relative = objectKey;
        if (relative.startsWith(IMPORT_PREFIX + "/")) {
            relative = relative.substring((IMPORT_PREFIX + "/").length());
            int slash = relative.indexOf('/');
            if (slash >= 0 && slash + 1 < relative.length()) {
                relative = relative.substring(slash + 1);
            }
        }
        File output = new File(sourceRoot, relative + ".iob");
        File parent = output.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }

        object.write(output);

        ListTag<?> entities = compoundTag.getListTag("entities");
        boolean hasEntities = entities != null && !entities.getValue().isEmpty();

        JSONObject record = new JSONObject();
        record.put("sourcePath", entryPath.originalPath);
        record.put("structureId", entryPath.namespace.toLowerCase(Locale.ROOT) + ":" + stripExtension(entryPath.structurePath));
        record.put("objectKey", objectKey);
        record.put("entitiesIgnored", hasEntities);
        return ConversionResult.success(record, object.getStates().size(), hasEntities);
    }

    private static IrisObject toObject(CompoundTag root) {
        ListTag<?> sizeList = root.getListTag("size");
        if (sizeList == null || sizeList.size() < 3) {
            return null;
        }

        Integer width = tagToInt(sizeList.get(0));
        Integer height = tagToInt(sizeList.get(1));
        Integer depth = tagToInt(sizeList.get(2));
        if (width == null || height == null || depth == null || width <= 0 || height <= 0 || depth <= 0) {
            return null;
        }

        ListTag<?> paletteTag = root.getListTag("palette");
        ListTag<?> blocksTag = root.getListTag("blocks");
        if (paletteTag == null || paletteTag.size() == 0 || blocksTag == null) {
            return null;
        }

        List<BlockData> palette = buildPalette(paletteTag);
        IrisObject object = new IrisObject(width, height, depth);

        for (Object blockRaw : blocksTag.getValue()) {
            if (!(blockRaw instanceof CompoundTag blockTag)) {
                continue;
            }

            Integer stateIndex = tagToInt(blockTag.get("state"));
            if (stateIndex == null || stateIndex < 0 || stateIndex >= palette.size()) {
                continue;
            }

            int[] pos = readPos(blockTag.get("pos"));
            if (pos == null) {
                continue;
            }

            int x = pos[0];
            int y = pos[1];
            int z = pos[2];
            if (x < 0 || y < 0 || z < 0 || x >= width || y >= height || z >= depth) {
                continue;
            }

            BlockData blockData = palette.get(stateIndex);
            if (blockData == null) {
                blockData = AIR;
            }

            if (!B.isAir(blockData)) {
                object.setUnsigned(x, y, z, blockData);
            }

            CompoundTag tileNbt = blockTag.getCompoundTag("nbt");
            if (tileNbt != null && tileNbt.size() > 0) {
                KMap<String, Object> tileData = convertCompound(tileNbt);
                if (!tileData.isEmpty()) {
                    TileData state = new TileData(blockData.getMaterial(), tileData);
                    object.getStates().put(new Vector3i(x, y, z), state);
                }
            }
        }

        return object;
    }

    private static List<BlockData> buildPalette(ListTag<?> paletteTag) {
        List<BlockData> palette = new ArrayList<>(paletteTag.size());
        for (Object paletteRaw : paletteTag.getValue()) {
            BlockData blockData = AIR;
            if (paletteRaw instanceof CompoundTag paletteEntry) {
                String name = paletteEntry.getString("Name");
                String blockState = buildBlockState(name, paletteEntry.getCompoundTag("Properties"));
                BlockData resolved = resolveBlockData(blockState, name);
                blockData = resolved == null ? AIR : resolved;
            }
            palette.add(blockData);
        }
        return palette;
    }

    private static BlockData resolveBlockData(String blockState, String fallbackName) {
        String stateKey = blockState == null ? "" : blockState.toLowerCase(Locale.ROOT);
        if (!stateKey.isEmpty()) {
            BlockData cached = BLOCK_DATA_CACHE.get(stateKey);
            if (cached != null) {
                return cached;
            }
        }

        BlockData resolved = blockState == null || blockState.isBlank() ? null : B.getOrNull(blockState, false);
        if (resolved == null && fallbackName != null && !fallbackName.isBlank()) {
            String fallbackKey = fallbackName.toLowerCase(Locale.ROOT);
            BlockData fallbackCached = BLOCK_DATA_CACHE.get(fallbackKey);
            if (fallbackCached != null) {
                return fallbackCached;
            }
            resolved = B.getOrNull(fallbackName, false);
            if (resolved != null) {
                BLOCK_DATA_CACHE.putIfAbsent(fallbackKey, resolved);
            }
        }

        if (resolved == null) {
            resolved = AIR;
        }

        if (!stateKey.isEmpty()) {
            BLOCK_DATA_CACHE.putIfAbsent(stateKey, resolved);
        }

        return resolved;
    }

    private static String buildBlockState(String name, CompoundTag properties) {
        String base = name == null ? "minecraft:air" : name;
        if (properties == null || properties.size() == 0) {
            return base;
        }

        List<String> keys = new ArrayList<>(properties.keySet());
        keys.sort(String::compareTo);
        StringBuilder builder = new StringBuilder(base).append("[");
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            Tag<?> valueTag = properties.get(key);
            if (i > 0) {
                builder.append(",");
            }
            builder.append(key).append("=").append(tagToPropertyValue(valueTag));
        }
        builder.append("]");
        return builder.toString();
    }

    private static String tagToPropertyValue(Tag<?> valueTag) {
        if (valueTag instanceof StringTag stringTag) {
            return stringTag.getValue();
        }
        if (valueTag == null) {
            return "null";
        }
        String value = valueTag.valueToString();
        if (value.length() > 1 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static int[] readPos(Tag<?> posTag) {
        if (!(posTag instanceof ListTag<?> listTag) || listTag.size() < 3) {
            return null;
        }
        Integer x = tagToInt(listTag.get(0));
        Integer y = tagToInt(listTag.get(1));
        Integer z = tagToInt(listTag.get(2));
        if (x == null || y == null || z == null) {
            return null;
        }
        return new int[]{x, y, z};
    }

    private static Integer tagToInt(Tag<?> tag) {
        if (tag instanceof NumberTag<?> numberTag) {
            return numberTag.asInt();
        }
        return null;
    }

    private static NamedTag readNamedTag(byte[] bytes) throws IOException {
        IOException primary = null;
        try {
            return new NBTDeserializer(false).fromStream(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            primary = e;
        }

        try {
            return new NBTDeserializer(true).fromStream(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            if (primary != null) {
                e.addSuppressed(primary);
            }
            throw e;
        }
    }

    private static KMap<String, Object> convertCompound(CompoundTag tag) {
        KMap<String, Object> map = new KMap<>();
        for (Map.Entry<String, Tag<?>> entry : tag) {
            String key = entry.getKey();
            Object value = convertTag(entry.getValue());
            if (value != null) {
                map.put(key, value);
            }
        }
        return map;
    }

    private static Object convertTag(Tag<?> tag) {
        if (tag == null) {
            return null;
        }

        if (tag instanceof CompoundTag compoundTag) {
            return convertCompound(compoundTag);
        }

        if (tag instanceof ListTag<?> listTag) {
            KList<Object> list = new KList<>();
            for (Object child : listTag.getValue()) {
                if (!(child instanceof Tag<?> childTag)) {
                    continue;
                }
                Object converted = convertTag(childTag);
                if (converted != null) {
                    list.add(converted);
                }
            }
            return list;
        }

        if (tag instanceof ByteArrayTag byteArrayTag) {
            KList<Byte> list = new KList<>();
            for (byte value : byteArrayTag.getValue()) {
                list.add(value);
            }
            return list;
        }

        if (tag instanceof IntArrayTag intArrayTag) {
            KList<Integer> list = new KList<>();
            for (int value : intArrayTag.getValue()) {
                list.add(value);
            }
            return list;
        }

        if (tag instanceof LongArrayTag longArrayTag) {
            KList<Long> list = new KList<>();
            for (long value : longArrayTag.getValue()) {
                list.add(value);
            }
            return list;
        }

        if (tag instanceof NumberTag<?> numberTag) {
            if (tag instanceof ByteTag) {
                return numberTag.asByte();
            }
            if (tag instanceof ShortTag) {
                return numberTag.asShort();
            }
            if (tag instanceof IntTag) {
                return numberTag.asInt();
            }
            if (tag instanceof LongTag) {
                return numberTag.asLong();
            }
            if (tag instanceof FloatTag) {
                return numberTag.asFloat();
            }
            if (tag instanceof DoubleTag) {
                return numberTag.asDouble();
            }
            return numberTag.asDouble();
        }

        if (tag instanceof StringTag stringTag) {
            return stringTag.getValue();
        }

        return null;
    }

    private static String createUniqueKey(String base, Set<String> used) {
        if (used.add(base)) {
            return base;
        }
        int index = 2;
        while (true) {
            String candidate = base + "-" + index;
            if (used.add(candidate)) {
                return candidate;
            }
            index++;
        }
    }

    private static EntryPath resolveEntryPath(String path) {
        if (path == null) {
            return null;
        }
        String normalized = path.replace('\\', '/');
        Matcher matcher = STRUCTURE_ENTRY.matcher(normalized);
        if (!matcher.matches()) {
            return null;
        }
        String namespace = matcher.group(1);
        String structurePath = matcher.group(2);
        if (namespace == null || structurePath == null || namespace.isBlank() || structurePath.isBlank()) {
            return null;
        }
        return new EntryPath(normalized, namespace, structurePath);
    }

    private static boolean isArchive(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".zip") || lower.endsWith(".jar");
    }

    private static String sanitizePath(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.toLowerCase(Locale.ROOT)
                .replace('\\', '/')
                .replaceAll("[^a-z0-9_\\-./]", "_")
                .replaceAll("/+", "/")
                .replaceAll("^/+", "")
                .replaceAll("/+$", "");
        if (cleaned.contains("..")) {
            cleaned = cleaned.replace("..", "_");
        }
        return cleaned;
    }

    private static String stripExtension(String name) {
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        if (dot <= 0) {
            return name;
        }
        return name.substring(0, dot);
    }

    private static JSONObject readExistingIndex(File indexFile) {
        if (indexFile == null || !indexFile.exists()) {
            return new JSONObject();
        }
        try {
            return new JSONObject(Files.readString(indexFile.toPath(), StandardCharsets.UTF_8));
        } catch (Throwable e) {
            Iris.warn("Failed to read datapack index, rebuilding.");
            Iris.reportError(e);
            return new JSONObject();
        }
    }

    private static Map<String, JSONObject> mapExistingSources(JSONObject index) {
        Map<String, JSONObject> mapped = new HashMap<>();
        if (index == null) {
            return mapped;
        }
        JSONArray sources = index.optJSONArray("sources");
        if (sources == null) {
            return mapped;
        }
        for (int i = 0; i < sources.length(); i++) {
            JSONObject source = sources.optJSONObject(i);
            if (source == null) {
                continue;
            }
            String sourceKey = source.optString("sourceKey", "");
            if (!sourceKey.isEmpty()) {
                mapped.put(sourceKey, source);
            }
        }
        return mapped;
    }

    private static void addSourceToSummary(ImportSummary summary, JSONObject source, boolean cached) {
        if (summary == null || source == null) {
            return;
        }
        summary.sources++;
        summary.nbtScanned += source.optInt("nbtScanned", 0);
        summary.converted += source.optInt("converted", 0);
        summary.failed += source.optInt("failed", 0);
        summary.skipped += source.optInt("skipped", 0);
        summary.entitiesIgnored += source.optInt("entitiesIgnored", 0);
        summary.blockEntities += source.optInt("blockEntities", 0);
        if (cached) {
            summary.cachedSources++;
        }
    }

    private static void pruneRemovedSourceFolders(Map<String, JSONObject> oldSources, Set<String> activeSourceKeys) {
        if (oldSources == null || oldSources.isEmpty()) {
            return;
        }

        for (Map.Entry<String, JSONObject> entry : oldSources.entrySet()) {
            String sourceKey = entry.getKey();
            if (sourceKey == null || sourceKey.isEmpty() || activeSourceKeys.contains(sourceKey)) {
                continue;
            }

            JSONObject source = entry.getValue();
            String targetPack = defaultTargetPack();
            if (source != null) {
                String configuredPack = sanitizePackName(source.optString("targetPack", ""));
                if (!configuredPack.isEmpty()) {
                    targetPack = configuredPack;
                }
            }

            deleteFolder(resolveSourceRoot(targetPack, sourceKey));
        }
    }

    private static void writeIndex(File indexFile, JSONArray sources, ImportSummary summary) {
        JSONObject totals = new JSONObject();
        totals.put("sources", summary.sources);
        totals.put("cachedSources", summary.cachedSources);
        totals.put("nbtScanned", summary.nbtScanned);
        totals.put("converted", summary.converted);
        totals.put("failed", summary.failed);
        totals.put("skipped", summary.skipped);
        totals.put("entitiesIgnored", summary.entitiesIgnored);
        totals.put("blockEntities", summary.blockEntities);

        JSONObject root = new JSONObject();
        root.put("generatedAt", Instant.now().toString());
        root.put("sources", sources);
        root.put("totals", totals);

        try {
            File parent = indexFile.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            Files.writeString(indexFile.toPath(), root.toString(4), StandardCharsets.UTF_8);
        } catch (Throwable e) {
            Iris.warn("Failed to write datapack index " + indexFile.getPath());
            Iris.reportError(e);
        }
    }

    private static void deleteFolder(File folder) {
        if (folder == null || !folder.exists()) {
            return;
        }
        try {
            Files.walk(folder.toPath())
                    .sorted(Comparator.reverseOrder())
                    .map(java.nio.file.Path::toFile)
                    .forEach(File::delete);
        } catch (Throwable e) {
            Iris.reportError(e);
        }
    }

    private static ModrinthFile resolveModrinthFile(String pageUrl) throws IOException {
        Matcher matcher = MODRINTH_VERSION_URL.matcher(pageUrl);
        if (!matcher.matches()) {
            throw new IOException("Unsupported Modrinth URL format: " + pageUrl);
        }

        String slug = matcher.group(1);
        String version = matcher.group(2);
        if (slug == null || version == null) {
            throw new IOException("Invalid Modrinth URL: " + pageUrl);
        }

        String api = "https://api.modrinth.com/v2/project/"
                + URLEncoder.encode(slug, StandardCharsets.UTF_8)
                + "/version/"
                + URLEncoder.encode(version, StandardCharsets.UTF_8);
        JSONObject json = getJson(api);
        JSONArray loaders = json.optJSONArray("loaders");
        if (loaders == null || !containsIgnoreCase(loaders, "datapack")) {
            throw new IOException("Modrinth version is not a datapack: " + pageUrl);
        }

        JSONArray files = json.optJSONArray("files");
        if (files == null || files.length() == 0) {
            throw new IOException("No downloadable files in Modrinth version: " + pageUrl);
        }

        JSONObject selected = null;
        for (int i = 0; i < files.length(); i++) {
            JSONObject file = files.optJSONObject(i);
            if (file != null && file.optBoolean("primary", false)) {
                selected = file;
                break;
            }
        }

        if (selected == null) {
            selected = files.optJSONObject(0);
        }
        if (selected == null) {
            throw new IOException("Unable to select datapack file for " + pageUrl);
        }

        String fileUrl = selected.optString("url", "");
        String fileName = selected.optString("filename", "");
        if (fileUrl.isEmpty() || fileName.isEmpty()) {
            throw new IOException("Invalid file payload for " + pageUrl);
        }

        String versionId = json.optString("id", version);
        JSONObject hashes = selected.optJSONObject("hashes");
        String sha1 = hashes == null ? null : hashes.optString("sha1", null);
        String extension = extension(fileName);
        String safeSlug = sanitizePath(slug).replace("/", "_");
        if (safeSlug.isEmpty()) {
            safeSlug = "modrinth";
        }
        return new ModrinthFile(pageUrl, fileUrl, safeSlug, versionId, extension, sha1);
    }

    private static JSONObject getJson(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestProperty("User-Agent", "Iris/" + Iris.instance.getDescription().getVersion());
        connection.setRequestProperty("Accept", "application/json");
        int response = connection.getResponseCode();
        if (response < 200 || response >= 300) {
            InputStream error = connection.getErrorStream();
            String message = error == null ? "" : new String(error.readAllBytes(), StandardCharsets.UTF_8);
            throw new IOException("HTTP " + response + " for " + url + (message.isEmpty() ? "" : " - " + message));
        }
        try (InputStream inputStream = connection.getInputStream()) {
            String body = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            return new JSONObject(body);
        }
    }

    private static void downloadToFile(String url, File output) throws IOException {
        File parent = output.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        File temp = parent == null
                ? new File(output.getPath() + ".tmp-" + System.nanoTime())
                : new File(parent, output.getName() + ".tmp-" + System.nanoTime());
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", "Iris/" + Iris.instance.getDescription().getVersion());
        connection.setRequestProperty("Accept", "*/*");
        int response = connection.getResponseCode();
        if (response < 200 || response >= 300) {
            throw new IOException("HTTP " + response + " for " + url);
        }

        try (InputStream inputStream = connection.getInputStream()) {
            Files.copy(inputStream, temp.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        try {
            Files.move(temp.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(temp.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void copyFile(File from, File to) throws IOException {
        if (from == null || to == null) {
            throw new IOException("Invalid copy source/target");
        }

        File parent = to.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }

        File temp = parent == null
                ? new File(to.getPath() + ".tmp-" + System.nanoTime())
                : new File(parent, to.getName() + ".tmp-" + System.nanoTime());
        Files.copy(from.toPath(), temp.toPath(), StandardCopyOption.REPLACE_EXISTING);
        try {
            Files.move(temp.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(temp.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static boolean containsIgnoreCase(JSONArray array, String value) {
        if (array == null || value == null) {
            return false;
        }
        for (int i = 0; i < array.length(); i++) {
            Object raw = array.opt(i);
            if (raw == null) {
                continue;
            }
            if (value.equalsIgnoreCase(String.valueOf(raw))) {
                return true;
            }
        }
        return false;
    }

    private static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return ".zip";
        }
        return fileName.substring(dot).toLowerCase(Locale.ROOT);
    }

    private static boolean isUpToDate(File output, String expectedSha1) {
        if (output == null || !output.exists() || !output.isFile()) {
            return false;
        }
        if (expectedSha1 == null || expectedSha1.isBlank()) {
            return true;
        }
        try {
            return expectedSha1.equalsIgnoreCase(sha1Hex(output));
        } catch (Throwable e) {
            return false;
        }
    }

    private static String sha1Hex(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        try (InputStream inputStream = Files.newInputStream(file.toPath())) {
            byte[] buffer = new byte[16384];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        byte[] bytes = digest.digest();
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    private static String shortHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < 4 && i < bytes.length; i++) {
                builder.append(String.format("%02x", bytes[i]));
            }
            return builder.toString();
        } catch (Throwable e) {
            return "00000000";
        }
    }

    private record EntryPath(String originalPath, String namespace, String structurePath) {
    }

    private record SourceInput(File source, String targetPack, String requiredEnvironment) {
    }

    private record SourceControl(String targetPack, String requiredEnvironment) {
    }

    private record SourceDescriptor(String sourceKey, String sourceName, String fingerprint, String targetPack, String requiredEnvironment) {
    }

    private record ModrinthFile(String pageUrl, String url, String slug, String versionId, String extension, String sha1) {
        private String outputFileName() {
            String version = sanitizePath(versionId).replace("/", "_");
            if (version.isEmpty()) {
                version = "version";
            }
            return "modrinth-" + slug + "-" + version + extension;
        }
    }

    private static final class SourceConversion {
        private final String sourceKey;
        private final String sourceName;
        private final String targetPack;
        private final String requiredEnvironment;
        private final Set<String> usedKeys;
        private final JSONArray objects;
        private int nbtScanned;
        private int converted;
        private int failed;
        private int skipped;
        private int entitiesIgnored;
        private int blockEntities;

        private SourceConversion(String sourceKey, String sourceName, String targetPack, String requiredEnvironment) {
            this.sourceKey = sourceKey;
            this.sourceName = sourceName;
            this.targetPack = targetPack;
            this.requiredEnvironment = requiredEnvironment;
            this.usedKeys = new HashSet<>();
            this.objects = new JSONArray();
            this.nbtScanned = 0;
            this.converted = 0;
            this.failed = 0;
            this.skipped = 0;
            this.entitiesIgnored = 0;
            this.blockEntities = 0;
        }

        private String reserveObjectKey(String namespace, String structurePath) {
            String namespacePath = sanitizePath(namespace);
            String structureValue = sanitizePath(stripExtension(structurePath));
            if (namespacePath.isEmpty() || structureValue.isEmpty()) {
                return null;
            }
            String baseKey = IMPORT_PREFIX + "/" + sourceKey + "/" + namespacePath + "/" + structureValue;
            return createUniqueKey(baseKey, usedKeys);
        }

        private JSONObject toJson(String fingerprint) {
            JSONObject source = new JSONObject();
            source.put("sourceKey", sourceKey);
            source.put("sourceName", sourceName);
            source.put("targetPack", targetPack);
            if (requiredEnvironment != null) {
                source.put("requiredEnvironment", requiredEnvironment);
            }
            source.put("fingerprint", fingerprint);
            source.put("nbtScanned", nbtScanned);
            source.put("converted", converted);
            source.put("failed", failed);
            source.put("skipped", skipped);
            source.put("entitiesIgnored", entitiesIgnored);
            source.put("blockEntities", blockEntities);
            source.put("objects", objects);
            return source;
        }
    }

    public static final class ImportSummary {
        private int sources;
        private int cachedSources;
        private int nbtScanned;
        private int converted;
        private int failed;
        private int skipped;
        private int entitiesIgnored;
        private int blockEntities;

        public int getSources() {
            return sources;
        }

        public int getCachedSources() {
            return cachedSources;
        }

        public int getNbtScanned() {
            return nbtScanned;
        }

        public int getConverted() {
            return converted;
        }

        public int getFailed() {
            return failed;
        }

        public int getSkipped() {
            return skipped;
        }

        public int getEntitiesIgnored() {
            return entitiesIgnored;
        }

        public int getBlockEntities() {
            return blockEntities;
        }
    }

    private static final class ConversionResult {
        private final boolean success;
        private final JSONObject record;
        private final int blockEntities;
        private final boolean entitiesIgnored;

        private ConversionResult(boolean success, JSONObject record, int blockEntities, boolean entitiesIgnored) {
            this.success = success;
            this.record = record;
            this.blockEntities = blockEntities;
            this.entitiesIgnored = entitiesIgnored;
        }

        private static ConversionResult success(JSONObject record, int blockEntities, boolean entitiesIgnored) {
            return new ConversionResult(true, record, blockEntities, entitiesIgnored);
        }

        private static ConversionResult failed() {
            return new ConversionResult(false, null, 0, false);
        }
    }
}
