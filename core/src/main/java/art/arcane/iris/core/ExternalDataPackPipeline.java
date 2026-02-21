package art.arcane.iris.core;

import art.arcane.iris.Iris;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.nms.INMS;
import art.arcane.iris.core.nms.container.StructurePlacement;
import art.arcane.iris.core.link.Identifier;
import art.arcane.iris.engine.data.cache.AtomicCache;
import art.arcane.iris.engine.object.IrisObject;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.engine.object.IrisExternalDatapackReplaceTargets;
import art.arcane.iris.engine.object.IrisExternalDatapackStructurePatch;
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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
    private static final Pattern STRUCTURE_JSON_ENTRY = Pattern.compile("(?i)^data/([^/]+)/worldgen/structure/(.+)\\.json$");
    private static final Pattern STRUCTURE_SET_JSON_ENTRY = Pattern.compile("(?i)^data/([^/]+)/worldgen/structure_set/(.+)\\.json$");
    private static final Pattern CONFIGURED_FEATURE_JSON_ENTRY = Pattern.compile("(?i)^data/([^/]+)/worldgen/configured_feature/(.+)\\.json$");
    private static final Pattern PLACED_FEATURE_JSON_ENTRY = Pattern.compile("(?i)^data/([^/]+)/worldgen/placed_feature/(.+)\\.json$");
    private static final Pattern TEMPLATE_POOL_JSON_ENTRY = Pattern.compile("(?i)^data/([^/]+)/worldgen/template_pool/(.+)\\.json$");
    private static final Pattern PROCESSOR_LIST_JSON_ENTRY = Pattern.compile("(?i)^data/([^/]+)/worldgen/processor_list/(.+)\\.json$");
    private static final Pattern BIOME_HAS_STRUCTURE_TAG_ENTRY = Pattern.compile("(?i)^data/([^/]+)/tags/worldgen/biome/has_structure/(.+)\\.json$");
    private static final Pattern MODRINTH_VERSION_URL = Pattern.compile("^https?://modrinth\\.com/(?:datapack|mod|plugin|resourcepack)/([^/?#]+)/version/([^/?#]+).*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern STRUCTURE_ENTRY = Pattern.compile("(?i)(?:^|.*/)data/([^/]+)/(?:structure|structures)/(.+\\.nbt)$");
    private static final String EXTERNAL_PACK_INDEX = "datapack-imports";
    private static final String PACK_NAME = EXTERNAL_PACK_INDEX;
    private static final String MANAGED_WORLD_PACK_PREFIX = "iris-external-";
    private static final String MANAGED_PACK_META_DESCRIPTION = "Iris managed external structure datapack assets.";
    private static final String IMPORT_PREFIX = "imports";
    private static final String LOCATE_MANIFEST_PATH = "cache/external-datapack-locate-manifest.json";
    private static final String OBJECT_LOCATE_MANIFEST_PATH = "cache/external-datapack-object-locate-manifest.json";
    private static final int CONNECT_TIMEOUT_MS = 4000;
    private static final int READ_TIMEOUT_MS = 8000;
    private static final int IMPORT_PARALLELISM = Math.max(1, Math.min(8, Runtime.getRuntime().availableProcessors()));
    private static final int MAX_IN_FLIGHT = Math.max(2, IMPORT_PARALLELISM * 3);
    private static final Map<String, BlockData> BLOCK_DATA_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, String> PACK_ENVIRONMENT_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> RESOLVED_LOCATE_STRUCTURES_BY_ID = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> RESOLVED_LOCATE_STRUCTURES_BY_OBJECT_KEY = new ConcurrentHashMap<>();
    private static final AtomicCache<KMap<Identifier, StructurePlacement>> VANILLA_STRUCTURE_PLACEMENTS = new AtomicCache<>();
    private static final BlockData AIR = B.getAir();

    private ExternalDataPackPipeline() {
    }

    public static String sanitizePackNameValue(String value) {
        return sanitizePackName(value);
    }

    public static String normalizeEnvironmentValue(String value) {
        return normalizeEnvironment(value);
    }

    public static Set<String> resolveLocateStructuresForId(String id) {
        String normalizedId = normalizeLocateId(id);
        if (normalizedId.isBlank()) {
            return Set.of();
        }

        Set<String> resolved = RESOLVED_LOCATE_STRUCTURES_BY_ID.get(normalizedId);
        if (resolved != null && !resolved.isEmpty()) {
            return Set.copyOf(resolved);
        }

        Map<String, Set<String>> fromManifest = readLocateManifest();
        Set<String> manifestSet = fromManifest.get(normalizedId);
        if (manifestSet == null || manifestSet.isEmpty()) {
            return Set.of();
        }

        RESOLVED_LOCATE_STRUCTURES_BY_ID.put(normalizedId, Set.copyOf(manifestSet));
        return Set.copyOf(manifestSet);
    }

    public static Set<String> resolveLocateStructuresForObjectKey(String objectKey) {
        String normalizedObjectKey = normalizeObjectLoadKey(objectKey);
        if (normalizedObjectKey.isBlank()) {
            return Set.of();
        }

        Set<String> resolved = RESOLVED_LOCATE_STRUCTURES_BY_OBJECT_KEY.get(normalizedObjectKey);
        if (resolved != null && !resolved.isEmpty()) {
            return Set.copyOf(resolved);
        }

        Map<String, Set<String>> fromManifest = readObjectLocateManifest();
        Set<String> manifestSet = fromManifest.get(normalizedObjectKey);
        if (manifestSet == null || manifestSet.isEmpty()) {
            return Set.of();
        }

        RESOLVED_LOCATE_STRUCTURES_BY_OBJECT_KEY.put(normalizedObjectKey, Set.copyOf(manifestSet));
        return Set.copyOf(manifestSet);
    }

    public static Map<String, Set<String>> snapshotLocateStructuresById() {
        if (RESOLVED_LOCATE_STRUCTURES_BY_ID.isEmpty()) {
            Map<String, Set<String>> manifest = readLocateManifest();
            if (!manifest.isEmpty()) {
                RESOLVED_LOCATE_STRUCTURES_BY_ID.putAll(manifest);
            }
        }

        ArrayList<String> ids = new ArrayList<>(RESOLVED_LOCATE_STRUCTURES_BY_ID.keySet());
        ids.sort(String::compareTo);
        LinkedHashMap<String, Set<String>> snapshot = new LinkedHashMap<>();
        for (String id : ids) {
            if (id == null || id.isBlank()) {
                continue;
            }

            Set<String> structures = RESOLVED_LOCATE_STRUCTURES_BY_ID.get(id);
            if (structures == null || structures.isEmpty()) {
                continue;
            }

            ArrayList<String> sortedStructures = new ArrayList<>(structures);
            sortedStructures.sort(String::compareTo);
            snapshot.put(id, Set.copyOf(sortedStructures));
        }

        return Map.copyOf(snapshot);
    }

    public static Set<String> snapshotLocateStructureKeys() {
        Map<String, Set<String>> locateById = snapshotLocateStructuresById();
        LinkedHashSet<String> structures = new LinkedHashSet<>();
        for (Set<String> values : locateById.values()) {
            if (values == null || values.isEmpty()) {
                continue;
            }

            for (String value : values) {
                String normalized = normalizeLocateStructure(value);
                if (!normalized.isBlank()) {
                    structures.add(normalized);
                }
            }
        }

        return Set.copyOf(structures);
    }

    public static PipelineSummary processDatapacks(List<DatapackRequest> requests, Map<String, KList<File>> worldDatapackFoldersByPack) {
        PipelineSummary summary = new PipelineSummary();
        PACK_ENVIRONMENT_CACHE.clear();
        RESOLVED_LOCATE_STRUCTURES_BY_ID.clear();
        RESOLVED_LOCATE_STRUCTURES_BY_OBJECT_KEY.clear();

        Set<File> knownWorldDatapackFolders = new LinkedHashSet<>();
        if (worldDatapackFoldersByPack != null) {
            for (Map.Entry<String, KList<File>> entry : worldDatapackFoldersByPack.entrySet()) {
                KList<File> folders = entry.getValue();
                if (folders == null) {
                    continue;
                }
                for (File folder : folders) {
                    if (folder != null) {
                        knownWorldDatapackFolders.add(folder);
                    }
                }
            }
        }
        collectWorldDatapackFolders(knownWorldDatapackFolders);
        summary.legacyDownloadRemovals = removeLegacyGlobalDownloads();
        summary.legacyWorldCopyRemovals = removeLegacyWorldDatapackCopies(knownWorldDatapackFolders);

        List<DatapackRequest> normalizedRequests = normalizeRequests(requests);
        summary.requests = normalizedRequests.size();
        if (normalizedRequests.isEmpty()) {
            Iris.info("Downloading datapacks [0/0] Downloading/Done!");
            writeLocateManifest(Map.of());
            writeObjectLocateManifest(Map.of());
            summary.legacyWorldCopyRemovals += pruneManagedWorldDatapacks(knownWorldDatapackFolders, Set.of());
            return summary;
        }

        List<RequestedSourceInput> sourceInputs = new ArrayList<>();
        LinkedHashMap<String, Set<String>> resolvedLocateStructuresById = new LinkedHashMap<>();
        LinkedHashMap<String, Set<String>> resolvedLocateStructuresByObjectKey = new LinkedHashMap<>();
        for (int requestIndex = 0; requestIndex < normalizedRequests.size(); requestIndex++) {
            DatapackRequest request = normalizedRequests.get(requestIndex);
            if (request == null) {
                continue;
            }

            if (request.replaceVanilla() && !request.hasReplacementTargets()) {
                if (request.required()) {
                    summary.requiredFailures++;
                } else {
                    summary.optionalFailures++;
                }
                Iris.warn("Downloading datapacks [" + (requestIndex + 1) + "/" + normalizedRequests.size() + "] Failed! id=" + request.id() + " (replaceVanilla requires explicit replacement targets).");
                mergeResolvedLocateStructures(resolvedLocateStructuresById, request.id(), request.resolvedLocateStructures());
                continue;
            }

            RequestSyncResult syncResult = syncRequest(request);
            if (!syncResult.success()) {
                if (request.required()) {
                    summary.requiredFailures++;
                } else {
                    summary.optionalFailures++;
                }
                Iris.warn("Downloading datapacks [" + (requestIndex + 1) + "/" + normalizedRequests.size() + "] Failed! id=" + request.id() + " (" + syncResult.error() + ").");
                mergeResolvedLocateStructures(resolvedLocateStructuresById, request.id(), request.resolvedLocateStructures());
                continue;
            }

            if (syncResult.downloaded()) {
                summary.syncedRequests++;
                Iris.info("Downloading datapacks [" + (requestIndex + 1) + "/" + normalizedRequests.size() + "] Downloading/Done!");
            } else if (syncResult.restored()) {
                summary.restoredRequests++;
            }
            mergeResolvedLocateStructures(resolvedLocateStructuresById, request.id(), request.resolvedLocateStructures());
            sourceInputs.add(new RequestedSourceInput(syncResult.source(), request));
        }

        if (sourceInputs.isEmpty()) {
            if (summary.requiredFailures == 0) {
                summary.legacyWorldCopyRemovals += pruneManagedWorldDatapacks(knownWorldDatapackFolders, Set.of());
            }
            writeLocateManifest(resolvedLocateStructuresById);
            writeObjectLocateManifest(resolvedLocateStructuresByObjectKey);
            RESOLVED_LOCATE_STRUCTURES_BY_ID.putAll(resolvedLocateStructuresById);
            RESOLVED_LOCATE_STRUCTURES_BY_OBJECT_KEY.putAll(resolvedLocateStructuresByObjectKey);
            return summary;
        }

        File importPackFolder = Iris.instance.getDataFolder("packs", EXTERNAL_PACK_INDEX);
        File indexFile = new File(importPackFolder, "datapack-index.json");
        importPackFolder.mkdirs();

        JSONObject oldIndex = readExistingIndex(indexFile);
        Map<String, JSONObject> oldSources = mapExistingSources(oldIndex);
        JSONArray newSources = new JSONArray();
        Set<String> seenSourceKeys = new HashSet<>();
        Set<String> activeManagedWorldDatapackNames = new HashSet<>();
        ImportSummary importSummary = new ImportSummary();

        for (int sourceIndex = 0; sourceIndex < sourceInputs.size(); sourceIndex++) {
            RequestedSourceInput sourceInput = sourceInputs.get(sourceIndex);
            File entry = sourceInput.source();
            DatapackRequest request = sourceInput.request();
            if (entry == null || !entry.exists() || request == null) {
                continue;
            }

            SourceDescriptor sourceDescriptor = createSourceDescriptor(entry, request.id(), request.targetPack(), request.requiredEnvironment());
            if (sourceDescriptor.requiredEnvironment() != null) {
                String packEnvironment = resolvePackEnvironment(sourceDescriptor.targetPack());
                if (packEnvironment == null || !packEnvironment.equals(sourceDescriptor.requiredEnvironment())) {
                    if (request.required()) {
                        summary.requiredFailures++;
                    } else {
                        summary.optionalFailures++;
                    }
                    Iris.warn("Skipped external datapack source " + sourceDescriptor.sourceName()
                            + " targetPack=" + sourceDescriptor.targetPack()
                            + " requiredEnvironment=" + sourceDescriptor.requiredEnvironment()
                            + " packEnvironment=" + (packEnvironment == null ? "unknown" : packEnvironment));
                    continue;
                }
            }

            seenSourceKeys.add(sourceDescriptor.sourceKey());
            File sourceRoot = resolveSourceRoot(sourceDescriptor.targetPack(), sourceDescriptor.objectRootKey());
            JSONObject cachedSource = oldSources.get(sourceDescriptor.sourceKey());
            String cachedTargetPack = cachedSource == null
                    ? null
                    : sanitizePackName(cachedSource.optString("targetPack", defaultTargetPack()));
            boolean sameTargetPack = cachedTargetPack != null && cachedTargetPack.equals(sourceDescriptor.targetPack());
            String cachedObjectRootKey = cachedSource == null ? "" : normalizeObjectRootKey(cachedSource.optString("objectRootKey", ""));
            boolean sameObjectRoot = cachedObjectRootKey.equals(sourceDescriptor.objectRootKey());
            JSONObject activeSource = null;

            if (cachedSource != null
                    && sourceDescriptor.fingerprint().equals(cachedSource.optString("fingerprint", ""))
                    && sameTargetPack
                    && sameObjectRoot
                    && sourceRoot.exists()) {
                newSources.put(cachedSource);
                addSourceToSummary(importSummary, cachedSource, true);
                activeSource = cachedSource;
            } else {
                if (cachedTargetPack != null && cachedSource != null) {
                    File previousSourceRoot = resolveSourceRoot(cachedTargetPack, cachedObjectRootKey);
                    deleteFolder(previousSourceRoot);
                    String cachedSourceKey = cachedSource.optString("sourceKey", sourceDescriptor.sourceKey());
                    File previousLegacySourceRoot = resolveLegacySourceRoot(cachedTargetPack, cachedSourceKey);
                    deleteFolder(previousLegacySourceRoot);
                }

                deleteFolder(sourceRoot);
                sourceRoot.mkdirs();
                JSONObject sourceResult = convertSource(entry, sourceDescriptor, sourceRoot, request.id());
                newSources.put(sourceResult);
                addSourceToSummary(importSummary, sourceResult, false);
                activeSource = sourceResult;
                int conversionFailed = sourceResult.optInt("failed", 0);
                if (conversionFailed > 0) {
                    int conversionScanned = sourceResult.optInt("nbtScanned", 0);
                    int conversionSuccess = sourceResult.optInt("converted", 0);
                    Iris.warn("External datapack object import had " + conversionFailed
                            + " failed structure conversion(s) for id=" + request.id()
                            + " source=" + sourceDescriptor.sourceName()
                            + " (scanned=" + conversionScanned + ", converted=" + conversionSuccess + ").");
                }
            }

            KList<File> targetWorldFolders = resolveTargetWorldFolders(request.targetPack(), worldDatapackFoldersByPack);
            ProjectionResult projectionResult = projectSourceToWorldDatapacks(entry, sourceDescriptor, request, targetWorldFolders);
            summary.worldDatapacksInstalled += projectionResult.installedDatapacks();
            summary.worldAssetsInstalled += projectionResult.installedAssets();
            mergeResolvedLocateStructures(resolvedLocateStructuresById, request.id(), projectionResult.resolvedLocateStructures());
            LinkedHashSet<String> objectLocateTargets = new LinkedHashSet<>();
            objectLocateTargets.addAll(request.resolvedLocateStructures());
            objectLocateTargets.addAll(projectionResult.resolvedLocateStructures());
            mergeResolvedLocateStructuresByObjectKey(
                    resolvedLocateStructuresByObjectKey,
                    extractObjectKeys(activeSource),
                    objectLocateTargets
            );
            if (projectionResult.managedName() != null && !projectionResult.managedName().isBlank() && projectionResult.installedDatapacks() > 0) {
                activeManagedWorldDatapackNames.add(projectionResult.managedName());
            }
            if (projectionResult.success()) {
                Iris.verbose("External datapack projection: id=" + request.id()
                        + ", source=" + sourceDescriptor.sourceName()
                        + ", targetPack=" + request.targetPack()
                        + ", managedDatapack=" + projectionResult.managedName()
                        + ", installedDatapacks=" + projectionResult.installedDatapacks()
                        + ", installedAssets=" + projectionResult.installedAssets()
                        + ", syntheticStructureSets=" + projectionResult.syntheticStructureSets()
                        + ", success=true");
            } else {
                Iris.warn("External datapack projection: id=" + request.id()
                        + ", source=" + sourceDescriptor.sourceName()
                        + ", targetPack=" + request.targetPack()
                        + ", managedDatapack=" + projectionResult.managedName()
                        + ", installedDatapacks=" + projectionResult.installedDatapacks()
                        + ", installedAssets=" + projectionResult.installedAssets()
                        + ", syntheticStructureSets=" + projectionResult.syntheticStructureSets()
                        + ", success=false"
                        + ", reason=" + projectionResult.error());
            }
            if (!projectionResult.success()) {
                if (request.required()) {
                    summary.requiredFailures++;
                } else {
                    summary.optionalFailures++;
                }
            }
        }

        pruneRemovedSourceFolders(oldSources, seenSourceKeys);
        writeIndex(indexFile, newSources, importSummary);
        summary.setImportSummary(importSummary);
        if (summary.requiredFailures == 0) {
            summary.legacyWorldCopyRemovals += pruneManagedWorldDatapacks(knownWorldDatapackFolders, activeManagedWorldDatapackNames);
        }

        writeLocateManifest(resolvedLocateStructuresById);
        writeObjectLocateManifest(resolvedLocateStructuresByObjectKey);
        RESOLVED_LOCATE_STRUCTURES_BY_ID.putAll(resolvedLocateStructuresById);
        RESOLVED_LOCATE_STRUCTURES_BY_OBJECT_KEY.putAll(resolvedLocateStructuresByObjectKey);
        return summary;
    }

    private static File getLocateManifestFile() {
        return Iris.instance.getDataFile(LOCATE_MANIFEST_PATH);
    }

    private static File getObjectLocateManifestFile() {
        return Iris.instance.getDataFile(OBJECT_LOCATE_MANIFEST_PATH);
    }

    private static String normalizeLocateId(String id) {
        if (id == null) {
            return "";
        }

        String normalized = id.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return "";
        }

        normalized = normalized.replace("minecraft:worldgen/structure/", "");
        normalized = normalized.replace("worldgen/structure/", "");
        return normalized;
    }

    private static String normalizeLocateStructure(String structure) {
        if (structure == null || structure.isBlank()) {
            return "";
        }
        String normalized = normalizeResourceKey("minecraft", structure, "worldgen/structure/");
        if (normalized == null || normalized.isBlank()) {
            return "";
        }
        return normalized;
    }

    private static String normalizeObjectLoadKey(String objectKey) {
        if (objectKey == null) {
            return "";
        }

        String normalized = sanitizePath(objectKey);
        if (normalized.endsWith(".iob")) {
            normalized = normalized.substring(0, normalized.length() - 4);
        }
        return normalized;
    }

    private static String normalizeObjectRootKey(String requestId) {
        if (requestId == null) {
            return "external-datapack";
        }

        String normalized = sanitizePath(requestId).replace("/", "_");
        if (normalized.isBlank()) {
            return "external-datapack";
        }

        return normalized;
    }

    private static Set<String> extractObjectKeys(JSONObject source) {
        LinkedHashSet<String> objectKeys = new LinkedHashSet<>();
        if (source == null) {
            return objectKeys;
        }

        JSONArray objects = source.optJSONArray("objects");
        if (objects == null) {
            return objectKeys;
        }

        for (int i = 0; i < objects.length(); i++) {
            JSONObject object = objects.optJSONObject(i);
            if (object == null) {
                continue;
            }

            String objectKey = normalizeObjectLoadKey(object.optString("objectKey", ""));
            if (!objectKey.isBlank()) {
                objectKeys.add(objectKey);
            }
        }

        return objectKeys;
    }

    private static void mergeResolvedLocateStructures(Map<String, Set<String>> destination, String id, Set<String> resolvedStructures) {
        if (destination == null) {
            return;
        }

        String normalizedId = normalizeLocateId(id);
        if (normalizedId.isBlank() || resolvedStructures == null || resolvedStructures.isEmpty()) {
            return;
        }

        Set<String> merged = destination.computeIfAbsent(normalizedId, key -> new LinkedHashSet<>());
        for (String structure : resolvedStructures) {
            String normalizedStructure = normalizeLocateStructure(structure);
            if (!normalizedStructure.isBlank()) {
                merged.add(normalizedStructure);
            }
        }
    }

    private static void mergeResolvedLocateStructuresByObjectKey(
            Map<String, Set<String>> destination,
            Set<String> objectKeys,
            Set<String> resolvedStructures
    ) {
        if (destination == null || objectKeys == null || objectKeys.isEmpty() || resolvedStructures == null || resolvedStructures.isEmpty()) {
            return;
        }

        LinkedHashSet<String> normalizedStructures = new LinkedHashSet<>();
        for (String structure : resolvedStructures) {
            String normalized = normalizeLocateStructure(structure);
            if (!normalized.isBlank()) {
                normalizedStructures.add(normalized);
            }
        }

        if (normalizedStructures.isEmpty()) {
            return;
        }

        for (String objectKey : objectKeys) {
            String normalizedObjectKey = normalizeObjectLoadKey(objectKey);
            if (normalizedObjectKey.isBlank()) {
                continue;
            }

            Set<String> merged = destination.computeIfAbsent(normalizedObjectKey, key -> new LinkedHashSet<>());
            merged.addAll(normalizedStructures);
        }
    }

    private static void writeLocateManifest(Map<String, Set<String>> resolvedLocateStructuresById) {
        File output = getLocateManifestFile();
        LinkedHashMap<String, Set<String>> normalized = new LinkedHashMap<>();
        if (resolvedLocateStructuresById != null) {
            for (Map.Entry<String, Set<String>> entry : resolvedLocateStructuresById.entrySet()) {
                String normalizedId = normalizeLocateId(entry.getKey());
                if (normalizedId.isBlank()) {
                    continue;
                }

                LinkedHashSet<String> structures = new LinkedHashSet<>();
                Set<String> values = entry.getValue();
                if (values != null) {
                    for (String structure : values) {
                        String normalizedStructure = normalizeLocateStructure(structure);
                        if (!normalizedStructure.isBlank()) {
                            structures.add(normalizedStructure);
                        }
                    }
                }

                if (!structures.isEmpty()) {
                    normalized.put(normalizedId, Set.copyOf(structures));
                }
            }
        }

        JSONObject root = new JSONObject();
        root.put("generatedAt", Instant.now().toString());
        JSONObject mappings = new JSONObject();
        ArrayList<String> ids = new ArrayList<>(normalized.keySet());
        ids.sort(String::compareTo);
        for (String id : ids) {
            Set<String> structures = normalized.get(id);
            if (structures == null || structures.isEmpty()) {
                continue;
            }

            ArrayList<String> sortedStructures = new ArrayList<>(structures);
            sortedStructures.sort(String::compareTo);
            JSONArray values = new JSONArray();
            for (String structure : sortedStructures) {
                values.put(structure);
            }
            mappings.put(id, values);
        }
        root.put("ids", mappings);

        try {
            writeBytesToFile(root.toString(4).getBytes(StandardCharsets.UTF_8), output);
        } catch (Throwable e) {
            Iris.warn("Failed to write external datapack locate manifest " + output.getPath());
            Iris.reportError(e);
        }
    }

    private static void writeObjectLocateManifest(Map<String, Set<String>> resolvedLocateStructuresByObjectKey) {
        File output = getObjectLocateManifestFile();
        LinkedHashMap<String, Set<String>> normalized = new LinkedHashMap<>();
        if (resolvedLocateStructuresByObjectKey != null) {
            for (Map.Entry<String, Set<String>> entry : resolvedLocateStructuresByObjectKey.entrySet()) {
                String normalizedObjectKey = normalizeObjectLoadKey(entry.getKey());
                if (normalizedObjectKey.isBlank()) {
                    continue;
                }

                LinkedHashSet<String> structures = new LinkedHashSet<>();
                Set<String> values = entry.getValue();
                if (values != null) {
                    for (String structure : values) {
                        String normalizedStructure = normalizeLocateStructure(structure);
                        if (!normalizedStructure.isBlank()) {
                            structures.add(normalizedStructure);
                        }
                    }
                }

                if (!structures.isEmpty()) {
                    normalized.put(normalizedObjectKey, Set.copyOf(structures));
                }
            }
        }

        JSONObject root = new JSONObject();
        root.put("generatedAt", Instant.now().toString());
        JSONObject mappings = new JSONObject();
        ArrayList<String> objectKeys = new ArrayList<>(normalized.keySet());
        objectKeys.sort(String::compareTo);
        for (String objectKey : objectKeys) {
            Set<String> structures = normalized.get(objectKey);
            if (structures == null || structures.isEmpty()) {
                continue;
            }

            ArrayList<String> sortedStructures = new ArrayList<>(structures);
            sortedStructures.sort(String::compareTo);
            JSONArray values = new JSONArray();
            for (String structure : sortedStructures) {
                values.put(structure);
            }
            mappings.put(objectKey, values);
        }
        root.put("objects", mappings);

        try {
            writeBytesToFile(root.toString(4).getBytes(StandardCharsets.UTF_8), output);
        } catch (Throwable e) {
            Iris.warn("Failed to write external datapack object locate manifest " + output.getPath());
            Iris.reportError(e);
        }
    }

    private static Map<String, Set<String>> readLocateManifest() {
        LinkedHashMap<String, Set<String>> mapped = new LinkedHashMap<>();
        File input = getLocateManifestFile();
        if (!input.exists() || !input.isFile()) {
            return mapped;
        }

        try {
            JSONObject root = new JSONObject(Files.readString(input.toPath(), StandardCharsets.UTF_8));
            JSONObject ids = root.optJSONObject("ids");
            if (ids == null) {
                return mapped;
            }

            ArrayList<String> keys = new ArrayList<>(ids.keySet());
            keys.sort(String::compareTo);
            for (String key : keys) {
                String normalizedId = normalizeLocateId(key);
                if (normalizedId.isBlank()) {
                    continue;
                }

                LinkedHashSet<String> structures = new LinkedHashSet<>();
                JSONArray values = ids.optJSONArray(key);
                if (values != null) {
                    for (int i = 0; i < values.length(); i++) {
                        Object rawValue = values.opt(i);
                        if (rawValue == null) {
                            continue;
                        }

                        String normalizedStructure = normalizeLocateStructure(String.valueOf(rawValue));
                        if (!normalizedStructure.isBlank()) {
                            structures.add(normalizedStructure);
                        }
                    }
                }

                if (!structures.isEmpty()) {
                    mapped.put(normalizedId, Set.copyOf(structures));
                }
            }
        } catch (Throwable e) {
            Iris.warn("Failed to read external datapack locate manifest " + input.getPath());
            Iris.reportError(e);
        }

        return mapped;
    }

    private static Map<String, Set<String>> readObjectLocateManifest() {
        LinkedHashMap<String, Set<String>> mapped = new LinkedHashMap<>();
        File input = getObjectLocateManifestFile();
        if (!input.exists() || !input.isFile()) {
            return mapped;
        }

        try {
            JSONObject root = new JSONObject(Files.readString(input.toPath(), StandardCharsets.UTF_8));
            JSONObject objects = root.optJSONObject("objects");
            if (objects == null) {
                return mapped;
            }

            ArrayList<String> keys = new ArrayList<>(objects.keySet());
            keys.sort(String::compareTo);
            for (String key : keys) {
                String normalizedObjectKey = normalizeObjectLoadKey(key);
                if (normalizedObjectKey.isBlank()) {
                    continue;
                }

                LinkedHashSet<String> structures = new LinkedHashSet<>();
                JSONArray values = objects.optJSONArray(key);
                if (values != null) {
                    for (int i = 0; i < values.length(); i++) {
                        Object rawValue = values.opt(i);
                        if (rawValue == null) {
                            continue;
                        }

                        String normalizedStructure = normalizeLocateStructure(String.valueOf(rawValue));
                        if (!normalizedStructure.isBlank()) {
                            structures.add(normalizedStructure);
                        }
                    }
                }

                if (!structures.isEmpty()) {
                    mapped.put(normalizedObjectKey, Set.copyOf(structures));
                }
            }
        } catch (Throwable e) {
            Iris.warn("Failed to read external datapack object locate manifest " + input.getPath());
            Iris.reportError(e);
        }

        return mapped;
    }

    private static List<DatapackRequest> normalizeRequests(List<DatapackRequest> requests) {
        Map<String, DatapackRequest> deduplicated = new HashMap<>();
        if (requests == null) {
            return new ArrayList<>();
        }

        for (DatapackRequest request : requests) {
            if (request == null) {
                continue;
            }
            String dedupeKey = request.getDedupeKey();
            if (dedupeKey.isBlank()) {
                continue;
            }

            DatapackRequest existing = deduplicated.get(dedupeKey);
            if (existing == null) {
                deduplicated.put(dedupeKey, request);
            } else {
                deduplicated.put(dedupeKey, existing.merge(request));
            }
        }

        return new ArrayList<>(deduplicated.values());
    }

    private static RequestSyncResult syncRequest(DatapackRequest request) {
        if (request == null) {
            return RequestSyncResult.failure("request is null");
        }

        String url = request.url();
        if (url == null || url.isBlank()) {
            return RequestSyncResult.failure("url is blank");
        }

        File packSourceFolder = Iris.instance.getDataFolder("packs", request.targetPack(), "externaldatapacks");
        File cacheFolder = Iris.instance.getDataFolder("cache", "datapacks");
        packSourceFolder.mkdirs();
        cacheFolder.mkdirs();

        try {
            ResolvedRemoteFile remoteFile = resolveRemoteFile(url);
            File output = new File(packSourceFolder, remoteFile.outputFileName());
            File cached = new File(cacheFolder, remoteFile.outputFileName());
            if (isUpToDate(output, remoteFile.sha1())) {
                writeRequestMetadata(packSourceFolder, request, output.getName(), remoteFile.sha1());
                return RequestSyncResult.restored(output);
            }

            if (isUpToDate(cached, remoteFile.sha1())) {
                copyFile(cached, output);
                if (isUpToDate(output, remoteFile.sha1())) {
                    writeRequestMetadata(packSourceFolder, request, output.getName(), remoteFile.sha1());
                    return RequestSyncResult.restored(output);
                }
                output.delete();
            }

            downloadToFile(remoteFile.url(), cached);
            if (!isUpToDate(cached, remoteFile.sha1())) {
                cached.delete();
                return RequestSyncResult.failure("hash mismatch for downloaded datapack");
            }

            copyFile(cached, output);
            if (!isUpToDate(output, remoteFile.sha1())) {
                output.delete();
                return RequestSyncResult.failure("output write mismatch after download");
            }

            writeRequestMetadata(packSourceFolder, request, output.getName(), remoteFile.sha1());
            return RequestSyncResult.downloaded(output);
        } catch (Throwable e) {
            RequestSyncResult restored = restoreRequestFromMetadata(packSourceFolder, request);
            if (restored.success()) {
                return restored;
            }
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return RequestSyncResult.failure(message);
        }
    }

    private static ResolvedRemoteFile resolveRemoteFile(String url) throws IOException {
        Matcher matcher = MODRINTH_VERSION_URL.matcher(url);
        if (matcher.matches()) {
            ModrinthFile modrinthFile = resolveModrinthFile(url);
            return new ResolvedRemoteFile(modrinthFile.url(), modrinthFile.outputFileName(), modrinthFile.sha1());
        }

        String path = URI.create(url).getPath();
        String fileName = path == null || path.isBlank() ? "datapack.zip" : path;
        String extension = extension(fileName);
        String outputName = "external-" + shortHash(url) + extension;
        return new ResolvedRemoteFile(url, outputName, null);
    }

    private static void writeRequestMetadata(File packSourceFolder, DatapackRequest request, String fileName, String sha1) {
        try {
            File metadataFile = getRequestMetadataFile(packSourceFolder, request);
            JSONObject metadata = new JSONObject();
            metadata.put("url", request.url());
            metadata.put("file", fileName);
            if (sha1 != null && !sha1.isBlank()) {
                metadata.put("sha1", sha1);
            }
            metadata.put("updatedAt", Instant.now().toString());
            Files.writeString(metadataFile.toPath(), metadata.toString(4), StandardCharsets.UTF_8);
        } catch (Throwable e) {
            Iris.reportError(e);
        }
    }

    private static RequestSyncResult restoreRequestFromMetadata(File packSourceFolder, DatapackRequest request) {
        try {
            File metadataFile = getRequestMetadataFile(packSourceFolder, request);
            if (!metadataFile.exists() || !metadataFile.isFile()) {
                return RequestSyncResult.failure("no cached metadata");
            }

            JSONObject metadata = new JSONObject(Files.readString(metadataFile.toPath(), StandardCharsets.UTF_8));
            String fileName = metadata.optString("file", "");
            if (fileName.isBlank()) {
                return RequestSyncResult.failure("cached metadata missing file");
            }

            String sha1 = metadata.optString("sha1", "");
            File candidate = new File(packSourceFolder, fileName);
            if (!isUpToDate(candidate, sha1.isBlank() ? null : sha1)) {
                return RequestSyncResult.failure("cached datapack failed integrity check");
            }
            return RequestSyncResult.restored(candidate);
        } catch (Throwable e) {
            return RequestSyncResult.failure("failed to restore cached metadata");
        }
    }

    private static File getRequestMetadataFile(File packSourceFolder, DatapackRequest request) {
        return new File(packSourceFolder, ".iris-request-" + shortHash(request.getDedupeKey()) + ".json");
    }

    private static int removeLegacyGlobalDownloads() {
        File legacyFolder = Iris.instance.getDataFolder("datapacks");
        if (!legacyFolder.exists()) {
            return 0;
        }

        File[] entries = legacyFolder.listFiles();
        int removed = entries == null ? 0 : entries.length;
        deleteFolder(legacyFolder);
        return removed;
    }

    private static int removeLegacyWorldDatapackCopies(Set<File> worldDatapackFolders) {
        int removed = 0;
        for (File folder : worldDatapackFolders) {
            if (folder == null || !folder.exists() || !folder.isDirectory()) {
                continue;
            }

            File[] entries = folder.listFiles();
            if (entries == null) {
                continue;
            }

            for (File entry : entries) {
                if (entry == null) {
                    continue;
                }
                String name = entry.getName().toLowerCase(Locale.ROOT);
                if (!name.startsWith("modrinth-")) {
                    continue;
                }
                deleteFolder(entry);
                if (!entry.exists()) {
                    removed++;
                }
            }
        }
        return removed;
    }

    private static int pruneManagedWorldDatapacks(Set<File> worldDatapackFolders, Set<String> activeManagedNames) {
        int removed = 0;
        for (File folder : worldDatapackFolders) {
            if (folder == null || !folder.exists() || !folder.isDirectory()) {
                continue;
            }

            File[] entries = folder.listFiles();
            if (entries == null) {
                continue;
            }

            for (File entry : entries) {
                if (entry == null) {
                    continue;
                }
                String name = entry.getName();
                if (!name.startsWith(MANAGED_WORLD_PACK_PREFIX)) {
                    continue;
                }
                if (activeManagedNames.contains(name)) {
                    continue;
                }
                deleteFolder(entry);
                if (!entry.exists()) {
                    removed++;
                }
            }
        }
        return removed;
    }

    private static KList<File> resolveTargetWorldFolders(String targetPack, Map<String, KList<File>> worldDatapackFoldersByPack) {
        KList<File> resolved = new KList<>();
        if (worldDatapackFoldersByPack == null || worldDatapackFoldersByPack.isEmpty()) {
            return resolved;
        }

        String normalizedPack = sanitizePackName(targetPack);
        KList<File> direct = worldDatapackFoldersByPack.get(normalizedPack);
        if (direct != null) {
            for (File file : direct) {
                if (file != null && !resolved.contains(file)) {
                    resolved.add(file);
                }
            }
            return resolved;
        }

        KList<File> fallback = worldDatapackFoldersByPack.get(targetPack);
        if (fallback != null) {
            for (File file : fallback) {
                if (file != null && !resolved.contains(file)) {
                    resolved.add(file);
                }
            }
        }

        return resolved;
    }

    private static ProjectionResult projectSourceToWorldDatapacks(File source, SourceDescriptor sourceDescriptor, DatapackRequest request, KList<File> worldDatapackFolders) {
        if (source == null || sourceDescriptor == null || request == null) {
            return ProjectionResult.failure("", "invalid projection inputs");
        }

        String managedName = buildManagedWorldDatapackName(sourceDescriptor.targetPack(), sourceDescriptor.sourceKey());
        if (worldDatapackFolders == null || worldDatapackFolders.isEmpty()) {
            return ProjectionResult.success(managedName, 0, 0, Set.copyOf(request.resolvedLocateStructures()), 0);
        }

        ProjectionAssetSummary projectionAssetSummary;
        try {
            projectionAssetSummary = buildProjectedAssets(source, sourceDescriptor, request);
        } catch (Throwable e) {
            Iris.warn("Failed to prepare projected external datapack assets from " + sourceDescriptor.sourceName());
            Iris.reportError(e);
            return ProjectionResult.failure(managedName, e.getMessage());
        }

        if (projectionAssetSummary.assets().isEmpty()) {
            return ProjectionResult.success(managedName, 0, 0, projectionAssetSummary.resolvedLocateStructures(), projectionAssetSummary.syntheticStructureSets());
        }

        int installedDatapacks = 0;
        int installedAssets = 0;
        for (File worldDatapackFolder : worldDatapackFolders) {
            if (worldDatapackFolder == null) {
                continue;
            }

            try {
                worldDatapackFolder.mkdirs();
                File managedFolder = new File(worldDatapackFolder, managedName);
                deleteFolder(managedFolder);
                int copiedAssets = writeProjectedAssets(managedFolder, projectionAssetSummary.assets());
                if (copiedAssets <= 0) {
                    deleteFolder(managedFolder);
                    continue;
                }
                writeManagedPackMeta(managedFolder);
                installedDatapacks++;
                installedAssets += copiedAssets;
            } catch (Throwable e) {
                Iris.warn("Failed to project external datapack source " + sourceDescriptor.sourceName() + " into " + worldDatapackFolder.getPath());
                Iris.reportError(e);
                return ProjectionResult.failure(managedName, e.getMessage());
            }
        }

        return ProjectionResult.success(managedName, installedDatapacks, installedAssets, projectionAssetSummary.resolvedLocateStructures(), projectionAssetSummary.syntheticStructureSets());
    }

    private static ProjectionAssetSummary buildProjectedAssets(File source, SourceDescriptor sourceDescriptor, DatapackRequest request) throws IOException {
        ProjectionSelection projectionSelection = readProjectedEntries(source, request);
        if (request.required() && !projectionSelection.missingSeededTargets().isEmpty()) {
            throw new IOException("Required replaceVanilla projection missing seeded target(s): " + summarizeMissingSeededTargets(projectionSelection.missingSeededTargets()));
        }

        List<ProjectionInputAsset> inputAssets = projectionSelection.assets();
        if (inputAssets.isEmpty()) {
            return new ProjectionAssetSummary(List.of(), Set.copyOf(request.resolvedLocateStructures()), 0);
        }

        String scopeNamespace = buildScopeNamespace(sourceDescriptor, request);
        LinkedHashMap<String, String> remappedKeys = new LinkedHashMap<>();
        for (ProjectionInputAsset inputAsset : inputAssets) {
            if (inputAsset.entry().namespace().equals("minecraft") && request.alongsideMode()) {
                String remappedKey = scopeNamespace + ":" + extractPathFromKey(inputAsset.entry().key());
                remappedKeys.put(inputAsset.entry().key(), remappedKey);
            }
        }

        LinkedHashMap<String, String> remapStringValues = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : remappedKeys.entrySet()) {
            remapStringValues.put(entry.getKey(), entry.getValue());
            remapStringValues.put("#" + entry.getKey(), "#" + entry.getValue());
        }

        LinkedHashMap<String, KList<String>> scopedTagValues = new LinkedHashMap<>();
        LinkedHashSet<String> resolvedLocateStructures = new LinkedHashSet<>();
        resolvedLocateStructures.addAll(request.resolvedLocateStructures());
        LinkedHashSet<String> remappedStructureKeys = new LinkedHashSet<>();
        LinkedHashSet<String> structureSetReferences = new LinkedHashSet<>();
        LinkedHashSet<String> writtenPaths = new LinkedHashSet<>();
        ArrayList<ProjectionOutputAsset> outputAssets = new ArrayList<>();

        for (ProjectionInputAsset inputAsset : inputAssets) {
            ProjectedEntry projectedEntry = inputAsset.entry();
            String remappedKey = remappedKeys.get(projectedEntry.key());
            ProjectedEntry effectiveEntry = remappedKey == null
                    ? projectedEntry
                    : new ProjectedEntry(projectedEntry.type(), extractNamespaceFromKey(remappedKey), remappedKey);

            String outputRelativePath = buildProjectedPath(effectiveEntry);
            if (outputRelativePath == null || writtenPaths.contains(outputRelativePath)) {
                continue;
            }
            writtenPaths.add(outputRelativePath);

            byte[] outputBytes = inputAsset.bytes();
            if (projectedEntry.type() == ProjectedEntryType.STRUCTURE
                    || projectedEntry.type() == ProjectedEntryType.STRUCTURE_SET
                    || projectedEntry.type() == ProjectedEntryType.CONFIGURED_FEATURE
                    || projectedEntry.type() == ProjectedEntryType.PLACED_FEATURE
                    || projectedEntry.type() == ProjectedEntryType.TEMPLATE_POOL
                    || projectedEntry.type() == ProjectedEntryType.PROCESSOR_LIST
                    || projectedEntry.type() == ProjectedEntryType.BIOME_HAS_STRUCTURE_TAG) {
                JSONObject root = new JSONObject(new String(outputBytes, StandardCharsets.UTF_8));
                rewriteJsonValues(root, remapStringValues);

                if (projectedEntry.type() == ProjectedEntryType.STRUCTURE) {
                    Integer startHeightAbsolute = getPatchedStartHeightAbsolute(projectedEntry, request);
                    if (startHeightAbsolute == null && remappedKey != null) {
                        startHeightAbsolute = request.structureStartHeights().get(remappedKey);
                    }

                    if (startHeightAbsolute != null) {
                        JSONObject startHeight = new JSONObject();
                        startHeight.put("absolute", startHeightAbsolute);
                        root.put("start_height", startHeight);
                    }

                    if (!request.forcedBiomeKeys().isEmpty()) {
                        String scopeTagKey = scopeNamespace + ":has_structure/" + extractPathFromKey(effectiveEntry.key());
                        root.put("biomes", "#" + scopeTagKey);
                        KList<String> values = scopedTagValues.computeIfAbsent(scopeTagKey, key -> new KList<>());
                        values.addAll(request.forcedBiomeKeys());
                    }

                    remappedStructureKeys.add(effectiveEntry.key());
                    resolvedLocateStructures.add(effectiveEntry.key());
                } else if (projectedEntry.type() == ProjectedEntryType.STRUCTURE_SET) {
                    structureSetReferences.addAll(readStructureSetReferences(root));
                }

                outputBytes = root.toString(4).getBytes(StandardCharsets.UTF_8);
            }

            outputAssets.add(new ProjectionOutputAsset(outputRelativePath, outputBytes));
        }

        int syntheticStructureSets = 0;
        if (request.alongsideMode()) {
            SyntheticStructureSetResult syntheticResult = synthesizeMissingStructureSets(
                    remappedStructureKeys,
                    structureSetReferences,
                    remappedKeys,
                    scopeNamespace,
                    writtenPaths
            );
            outputAssets.addAll(syntheticResult.assets());
            syntheticStructureSets += syntheticResult.count();
        }

        for (Map.Entry<String, KList<String>> scopedEntry : scopedTagValues.entrySet()) {
            String tagKey = scopedEntry.getKey();
            String tagPath = "data/" + extractNamespaceFromKey(tagKey) + "/tags/worldgen/biome/" + extractPathFromKey(tagKey) + ".json";
            if (writtenPaths.contains(tagPath)) {
                continue;
            }
            writtenPaths.add(tagPath);

            KList<String> uniqueValues = scopedEntry.getValue().removeDuplicates().sort();
            JSONArray values = new JSONArray();
            for (String value : uniqueValues) {
                values.put(value);
            }

            JSONObject root = new JSONObject();
            root.put("replace", false);
            root.put("values", values);
            outputAssets.add(new ProjectionOutputAsset(tagPath, root.toString(4).getBytes(StandardCharsets.UTF_8)));
        }

        return new ProjectionAssetSummary(outputAssets, Set.copyOf(resolvedLocateStructures), syntheticStructureSets);
    }

    private static ProjectionSelection readProjectedEntries(File source, DatapackRequest request) throws IOException {
        if (source.isDirectory()) {
            return selectProjectedEntries(readProjectedDirectoryEntries(source), request);
        }
        if (isArchive(source.getName())) {
            return selectProjectedEntries(readProjectedArchiveEntries(source), request);
        }
        return ProjectionSelection.empty();
    }

    private static List<ProjectionInputAsset> readProjectedDirectoryEntries(File source) throws IOException {
        ArrayList<ProjectionInputAsset> assets = new ArrayList<>();
        ArrayDeque<File> queue = new ArrayDeque<>();
        queue.add(source);
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

                String relative = source.toPath().relativize(child.toPath()).toString().replace('\\', '/');
                String normalizedRelative = normalizeRelativePath(relative);
                if (normalizedRelative == null) {
                    continue;
                }

                ProjectedEntry projectedEntry = parseProjectedEntry(normalizedRelative);
                if (projectedEntry == null) {
                    continue;
                }

                byte[] bytes = Files.readAllBytes(child.toPath());
                assets.add(new ProjectionInputAsset(normalizedRelative, projectedEntry, bytes));
            }
        }
        return assets;
    }

    private static List<ProjectionInputAsset> readProjectedArchiveEntries(File source) throws IOException {
        ArrayList<ProjectionInputAsset> assets = new ArrayList<>();
        try (ZipFile zipFile = new ZipFile(source)) {
            List<? extends ZipEntry> entries = zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .sorted(Comparator.comparing(ZipEntry::getName, String.CASE_INSENSITIVE_ORDER))
                    .toList();

            for (ZipEntry zipEntry : entries) {
                String normalizedRelative = normalizeRelativePath(zipEntry.getName());
                if (normalizedRelative == null) {
                    continue;
                }

                ProjectedEntry projectedEntry = parseProjectedEntry(normalizedRelative);
                if (projectedEntry == null) {
                    continue;
                }

                try (InputStream inputStream = zipFile.getInputStream(zipEntry)) {
                    byte[] bytes = inputStream.readAllBytes();
                    assets.add(new ProjectionInputAsset(normalizedRelative, projectedEntry, bytes));
                }
            }
        }
        return assets;
    }

    private static ProjectionSelection selectProjectedEntries(List<ProjectionInputAsset> inputAssets, DatapackRequest request) {
        if (inputAssets == null || inputAssets.isEmpty() || request == null) {
            return ProjectionSelection.empty();
        }

        if (!request.alongsideMode() && request.replaceVanilla()) {
            return selectReplaceVanillaEntries(inputAssets, request);
        }

        ArrayList<ProjectionInputAsset> selected = new ArrayList<>();
        for (ProjectionInputAsset asset : inputAssets) {
            if (asset == null) {
                continue;
            }

            if (shouldProjectEntry(asset.entry(), request)) {
                selected.add(asset);
            }
        }

        return new ProjectionSelection(selected, Set.of());
    }

    private static ProjectionSelection selectReplaceVanillaEntries(List<ProjectionInputAsset> inputAssets, DatapackRequest request) {
        EnumMap<ProjectedEntryType, LinkedHashMap<String, ProjectionInputAsset>> minecraftAssets = new EnumMap<>(ProjectedEntryType.class);
        EnumMap<ProjectedEntryType, LinkedHashSet<String>> closure = new EnumMap<>(ProjectedEntryType.class);
        for (ProjectedEntryType type : ProjectedEntryType.values()) {
            minecraftAssets.put(type, new LinkedHashMap<>());
            closure.put(type, new LinkedHashSet<>());
        }

        for (ProjectionInputAsset asset : inputAssets) {
            if (asset == null || asset.entry() == null) {
                continue;
            }

            if (!"minecraft".equals(asset.entry().namespace())) {
                continue;
            }

            minecraftAssets.get(asset.entry().type()).put(asset.entry().key(), asset);
        }

        LinkedHashSet<String> missingSeededTargets = new LinkedHashSet<>();
        ArrayDeque<ProjectedDependency> queue = new ArrayDeque<>();
        enqueueSeedTargets(request.structures(), ProjectedEntryType.STRUCTURE, minecraftAssets, missingSeededTargets, queue);
        enqueueSeedTargets(request.structureSets(), ProjectedEntryType.STRUCTURE_SET, minecraftAssets, missingSeededTargets, queue);
        enqueueSeedTargets(request.configuredFeatures(), ProjectedEntryType.CONFIGURED_FEATURE, minecraftAssets, missingSeededTargets, queue);
        enqueueSeedTargets(request.placedFeatures(), ProjectedEntryType.PLACED_FEATURE, minecraftAssets, missingSeededTargets, queue);
        enqueueSeedTargets(request.templatePools(), ProjectedEntryType.TEMPLATE_POOL, minecraftAssets, missingSeededTargets, queue);
        enqueueSeedTargets(request.processorLists(), ProjectedEntryType.PROCESSOR_LIST, minecraftAssets, missingSeededTargets, queue);
        enqueueSeedTargets(request.biomeHasStructureTags(), ProjectedEntryType.BIOME_HAS_STRUCTURE_TAG, minecraftAssets, missingSeededTargets, queue);

        while (!queue.isEmpty()) {
            ProjectedDependency current = queue.removeFirst();
            if (current == null || current.key() == null || current.key().isBlank()) {
                continue;
            }

            LinkedHashSet<String> visited = closure.get(current.type());
            if (visited == null || !visited.add(current.key())) {
                continue;
            }

            ProjectionInputAsset currentAsset = minecraftAssets.get(current.type()).get(current.key());
            if (currentAsset == null) {
                continue;
            }

            if (current.type() == ProjectedEntryType.STRUCTURE_NBT) {
                continue;
            }

            if (!isJsonProjectedEntryType(current.type())) {
                continue;
            }

            try {
                JSONObject root = new JSONObject(new String(currentAsset.bytes(), StandardCharsets.UTF_8));
                LinkedHashSet<ProjectedDependency> dependencies = new LinkedHashSet<>();
                collectProjectedDependencies(root, current.type(), dependencies);
                for (ProjectedDependency dependency : dependencies) {
                    if (dependency == null || dependency.key() == null || dependency.key().isBlank()) {
                        continue;
                    }
                    if (!dependency.key().startsWith("minecraft:")) {
                        continue;
                    }
                    if (!minecraftAssets.get(dependency.type()).containsKey(dependency.key())) {
                        continue;
                    }
                    queue.addLast(dependency);
                }
            } catch (Throwable ignored) {
            }
        }

        ArrayList<ProjectionInputAsset> selected = new ArrayList<>();
        for (ProjectionInputAsset asset : inputAssets) {
            if (asset == null || asset.entry() == null) {
                continue;
            }

            if (!"minecraft".equals(asset.entry().namespace())) {
                selected.add(asset);
                continue;
            }

            LinkedHashSet<String> selectedKeys = closure.get(asset.entry().type());
            if (selectedKeys != null && selectedKeys.contains(asset.entry().key())) {
                selected.add(asset);
            }
        }

        return new ProjectionSelection(selected, Set.copyOf(missingSeededTargets));
    }

    private static void enqueueSeedTargets(
            Set<String> keys,
            ProjectedEntryType type,
            Map<ProjectedEntryType, LinkedHashMap<String, ProjectionInputAsset>> minecraftAssets,
            Set<String> missingSeededTargets,
            ArrayDeque<ProjectedDependency> queue
    ) {
        if (keys == null || keys.isEmpty()) {
            return;
        }

        LinkedHashMap<String, ProjectionInputAsset> typedAssets = minecraftAssets.get(type);
        for (String key : keys) {
            if (key == null || key.isBlank()) {
                continue;
            }

            if (typedAssets == null || !typedAssets.containsKey(key)) {
                missingSeededTargets.add(type.name().toLowerCase(Locale.ROOT) + ":" + key);
                continue;
            }

            queue.addLast(new ProjectedDependency(type, key));
        }
    }

    private static boolean isJsonProjectedEntryType(ProjectedEntryType type) {
        return type == ProjectedEntryType.STRUCTURE
                || type == ProjectedEntryType.STRUCTURE_SET
                || type == ProjectedEntryType.CONFIGURED_FEATURE
                || type == ProjectedEntryType.PLACED_FEATURE
                || type == ProjectedEntryType.TEMPLATE_POOL
                || type == ProjectedEntryType.PROCESSOR_LIST
                || type == ProjectedEntryType.BIOME_HAS_STRUCTURE_TAG;
    }

    private static void collectProjectedDependencies(Object node, ProjectedEntryType ownerType, Set<ProjectedDependency> dependencies) {
        if (node == null) {
            return;
        }

        if (node instanceof JSONObject object) {
            for (String key : object.keySet()) {
                collectProjectedDependencies(object.get(key), ownerType, dependencies);
            }
            return;
        }

        if (node instanceof JSONArray array) {
            for (int index = 0; index < array.length(); index++) {
                collectProjectedDependencies(array.get(index), ownerType, dependencies);
            }
            return;
        }

        if (!(node instanceof String rawValue)) {
            return;
        }

        String value = rawValue.trim();
        if (value.isBlank()) {
            return;
        }

        addDependency(ownerType, dependencies, value, ProjectedEntryType.STRUCTURE, "worldgen/structure/");
        addDependency(ownerType, dependencies, value, ProjectedEntryType.STRUCTURE_SET, "worldgen/structure_set/");
        addDependency(ownerType, dependencies, value, ProjectedEntryType.CONFIGURED_FEATURE, "worldgen/configured_feature/");
        addDependency(ownerType, dependencies, value, ProjectedEntryType.PLACED_FEATURE, "worldgen/placed_feature/");
        addDependency(ownerType, dependencies, value, ProjectedEntryType.TEMPLATE_POOL, "worldgen/template_pool/");
        addDependency(ownerType, dependencies, value, ProjectedEntryType.PROCESSOR_LIST, "worldgen/processor_list/");
        addDependency(ownerType, dependencies, value, ProjectedEntryType.BIOME_HAS_STRUCTURE_TAG,
                "tags/worldgen/biome/has_structure/",
                "worldgen/biome/has_structure/",
                "has_structure/");
        addDependency(ownerType, dependencies, value, ProjectedEntryType.STRUCTURE_NBT, "structure/", "structures/");
    }

    private static void addDependency(
            ProjectedEntryType ownerType,
            Set<ProjectedDependency> dependencies,
            String value,
            ProjectedEntryType dependencyType,
            String... prefixes
    ) {
        String normalized = normalizeResourceKey("minecraft", value, prefixes);
        if (normalized == null || normalized.isBlank()) {
            return;
        }

        if (ownerType == dependencyType && ownerType != ProjectedEntryType.TEMPLATE_POOL) {
            return;
        }

        dependencies.add(new ProjectedDependency(dependencyType, normalized));
    }

    private static String summarizeMissingSeededTargets(Set<String> missingSeededTargets) {
        if (missingSeededTargets == null || missingSeededTargets.isEmpty()) {
            return "";
        }

        ArrayList<String> sorted = new ArrayList<>(missingSeededTargets);
        sorted.sort(String::compareTo);
        if (sorted.size() <= 8) {
            return String.join(", ", sorted);
        }

        ArrayList<String> limited = new ArrayList<>(sorted.subList(0, 8));
        return String.join(", ", limited) + " +" + (sorted.size() - limited.size()) + " more";
    }

    private static int writeProjectedAssets(File managedFolder, List<ProjectionOutputAsset> assets) throws IOException {
        if (assets == null || assets.isEmpty()) {
            return 0;
        }

        int copied = 0;
        for (ProjectionOutputAsset asset : assets) {
            if (asset == null || asset.relativePath() == null || asset.bytes() == null) {
                continue;
            }
            File output = new File(managedFolder, asset.relativePath());
            writeBytesToFile(asset.bytes(), output);
            copied++;
        }
        return copied;
    }

    private static void writeBytesToFile(byte[] data, File output) throws IOException {
        File parent = output.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }

        File temp = parent == null
                ? new File(output.getPath() + ".tmp-" + System.nanoTime())
                : new File(parent, output.getName() + ".tmp-" + System.nanoTime());
        Files.write(temp.toPath(), data);
        try {
            Files.move(temp.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(temp.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Integer getPatchedStartHeightAbsolute(ProjectedEntry projectedEntry, DatapackRequest request) {
        if (projectedEntry == null || request == null || projectedEntry.type() != ProjectedEntryType.STRUCTURE) {
            return null;
        }
        return request.structureStartHeights().get(projectedEntry.key());
    }

    private static void rewriteJsonValues(Object root, Map<String, String> replacements) {
        if (root instanceof JSONObject object) {
            for (String key : object.keySet()) {
                Object value = object.get(key);
                Object rewritten = rewriteJsonValue(value, replacements);
                if (rewritten != value) {
                    object.put(key, rewritten);
                }
            }
            return;
        }
        if (root instanceof JSONArray array) {
            for (int i = 0; i < array.length(); i++) {
                Object value = array.get(i);
                Object rewritten = rewriteJsonValue(value, replacements);
                if (rewritten != value) {
                    array.put(i, rewritten);
                }
            }
        }
    }

    private static Object rewriteJsonValue(Object value, Map<String, String> replacements) {
        if (value instanceof JSONObject object) {
            rewriteJsonValues(object, replacements);
            return object;
        }
        if (value instanceof JSONArray array) {
            rewriteJsonValues(array, replacements);
            return array;
        }
        if (value instanceof String stringValue) {
            String replacement = replacements.get(stringValue);
            if (replacement != null) {
                return replacement;
            }
        }
        return value;
    }

    private static Set<String> readStructureSetReferences(JSONObject root) {
        LinkedHashSet<String> references = new LinkedHashSet<>();
        if (root == null) {
            return references;
        }

        JSONArray structures = root.optJSONArray("structures");
        if (structures == null) {
            return references;
        }

        for (int i = 0; i < structures.length(); i++) {
            JSONObject structure = structures.optJSONObject(i);
            if (structure == null) {
                continue;
            }
            String structureKey = structure.optString("structure", "");
            if (structureKey.isBlank()) {
                continue;
            }
            String normalizedStructure = normalizeResourceKey("minecraft", structureKey, "worldgen/structure/");
            if (normalizedStructure == null || normalizedStructure.isBlank()) {
                continue;
            }
            references.add(normalizedStructure);
        }
        return references;
    }

    private static SyntheticStructureSetResult synthesizeMissingStructureSets(
            Set<String> remappedStructureKeys,
            Set<String> structureSetReferences,
            Map<String, String> remappedKeys,
            String scopeNamespace,
            Set<String> writtenPaths
    ) {
        if (remappedStructureKeys == null || remappedStructureKeys.isEmpty()) {
            return SyntheticStructureSetResult.empty();
        }

        LinkedHashMap<String, String> reverseRemap = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : remappedKeys.entrySet()) {
            reverseRemap.put(entry.getValue(), entry.getKey());
        }

        KMap<Identifier, StructurePlacement> vanillaPlacements = VANILLA_STRUCTURE_PLACEMENTS.aquire(() -> INMS.get().collectStructures());
        if (vanillaPlacements == null || vanillaPlacements.isEmpty()) {
            return SyntheticStructureSetResult.empty();
        }

        ArrayList<ProjectionOutputAsset> assets = new ArrayList<>();
        int synthesized = 0;
        for (String remappedStructure : remappedStructureKeys) {
            if (structureSetReferences.contains(remappedStructure)) {
                continue;
            }

            String originalStructure = reverseRemap.get(remappedStructure);
            if (originalStructure == null || originalStructure.isBlank()) {
                continue;
            }

            SyntheticPlacement syntheticPlacement = findSyntheticPlacement(vanillaPlacements, originalStructure);
            if (syntheticPlacement == null) {
                Iris.warn("Unable to synthesize structure set for remapped structure " + remappedStructure + " (no vanilla placement found).");
                continue;
            }

            JSONObject structureSetRoot = buildSyntheticStructureSetJson(syntheticPlacement, remappedStructure);
            if (structureSetRoot == null) {
                Iris.warn("Unable to synthesize structure set for remapped structure " + remappedStructure + " (unsupported placement type).");
                continue;
            }

            String structurePath = sanitizePath(extractPathFromKey(remappedStructure));
            if (structurePath.isBlank()) {
                structurePath = "structure";
            }
            String syntheticSetKey = scopeNamespace + ":generated/" + structurePath.replace('/', '_');
            String syntheticPath = buildProjectedPath(new ProjectedEntry(ProjectedEntryType.STRUCTURE_SET, scopeNamespace, syntheticSetKey));
            if (syntheticPath == null) {
                continue;
            }
            if (writtenPaths.contains(syntheticPath)) {
                syntheticSetKey = syntheticSetKey + "-" + shortHash(remappedStructure + "|" + syntheticPlacement.structureSetKey());
                syntheticPath = buildProjectedPath(new ProjectedEntry(ProjectedEntryType.STRUCTURE_SET, scopeNamespace, syntheticSetKey));
            }
            if (syntheticPath == null || writtenPaths.contains(syntheticPath)) {
                continue;
            }

            writtenPaths.add(syntheticPath);
            structureSetReferences.add(remappedStructure);
            assets.add(new ProjectionOutputAsset(syntheticPath, structureSetRoot.toString(4).getBytes(StandardCharsets.UTF_8)));
            synthesized++;
        }

        return new SyntheticStructureSetResult(assets, synthesized);
    }

    private static SyntheticPlacement findSyntheticPlacement(KMap<Identifier, StructurePlacement> vanillaPlacements, String structureKey) {
        if (vanillaPlacements == null || vanillaPlacements.isEmpty() || structureKey == null || structureKey.isBlank()) {
            return null;
        }

        for (Map.Entry<Identifier, StructurePlacement> entry : vanillaPlacements.entrySet()) {
            StructurePlacement placement = entry.getValue();
            if (placement == null || placement.structures() == null || placement.structures().isEmpty()) {
                continue;
            }

            for (StructurePlacement.Structure structure : placement.structures()) {
                if (structure == null || structure.key() == null || structure.key().isBlank()) {
                    continue;
                }
                if (!structureKey.equalsIgnoreCase(structure.key())) {
                    continue;
                }
                int weight = structure.weight() > 0 ? structure.weight() : 1;
                return new SyntheticPlacement(entry.getKey(), placement, weight);
            }
        }

        return null;
    }

    private static JSONObject buildSyntheticStructureSetJson(SyntheticPlacement placement, String structureKey) {
        if (placement == null || placement.placement() == null || structureKey == null || structureKey.isBlank()) {
            return null;
        }

        JSONObject root = new JSONObject();
        JSONArray structures = new JSONArray();
        JSONObject structure = new JSONObject();
        structure.put("structure", structureKey);
        structure.put("weight", placement.weight());
        structures.put(structure);
        root.put("structures", structures);

        StructurePlacement structurePlacement = placement.placement();
        JSONObject placementJson = new JSONObject();
        placementJson.put("salt", structurePlacement.salt());
        if (structurePlacement instanceof StructurePlacement.RandomSpread randomSpread) {
            placementJson.put("type", "minecraft:random_spread");
            placementJson.put("spacing", randomSpread.spacing());
            placementJson.put("separation", randomSpread.separation());
            if (randomSpread.spreadType() != null) {
                placementJson.put("spread_type", randomSpread.spreadType().name().toLowerCase(Locale.ROOT));
            }
            float frequency = structurePlacement.frequency();
            if (frequency > 0F && frequency < 0.999999F) {
                placementJson.put("frequency", frequency);
                placementJson.put("frequency_reduction_method", "default");
            }
        } else if (structurePlacement instanceof StructurePlacement.ConcentricRings concentricRings) {
            placementJson.put("type", "minecraft:concentric_rings");
            placementJson.put("distance", concentricRings.distance());
            placementJson.put("spread", concentricRings.spread());
            placementJson.put("count", concentricRings.count());
        } else {
            return null;
        }

        root.put("placement", placementJson);
        return root;
    }

    private static String buildScopeNamespace(SourceDescriptor sourceDescriptor, DatapackRequest request) {
        String base = shortHash(sourceDescriptor.sourceKey() + "|" + request.id() + "|" + request.scopeKey());
        return "iris_external_" + base;
    }

    private static String buildProjectedPath(ProjectedEntry entry) {
        if (entry == null || entry.key() == null || entry.key().isBlank()) {
            return null;
        }

        String namespace = extractNamespaceFromKey(entry.key());
        String path = extractPathFromKey(entry.key());
        if (namespace.isBlank() || path.isBlank()) {
            return null;
        }

        return switch (entry.type()) {
            case STRUCTURE -> "data/" + namespace + "/worldgen/structure/" + path + ".json";
            case STRUCTURE_SET -> "data/" + namespace + "/worldgen/structure_set/" + path + ".json";
            case CONFIGURED_FEATURE -> "data/" + namespace + "/worldgen/configured_feature/" + path + ".json";
            case PLACED_FEATURE -> "data/" + namespace + "/worldgen/placed_feature/" + path + ".json";
            case TEMPLATE_POOL -> "data/" + namespace + "/worldgen/template_pool/" + path + ".json";
            case PROCESSOR_LIST -> "data/" + namespace + "/worldgen/processor_list/" + path + ".json";
            case BIOME_HAS_STRUCTURE_TAG -> "data/" + namespace + "/tags/worldgen/biome/has_structure/" + path + ".json";
            case STRUCTURE_NBT -> "data/" + namespace + "/structures/" + path + ".nbt";
        };
    }

    private static String extractNamespaceFromKey(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        int colon = key.indexOf(':');
        if (colon <= 0) {
            return "";
        }
        return sanitizePath(key.substring(0, colon));
    }

    private static String extractPathFromKey(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        int colon = key.indexOf(':');
        if (colon < 0 || colon + 1 >= key.length()) {
            return sanitizePath(key);
        }
        return sanitizePath(key.substring(colon + 1));
    }

    private static void writeInputStreamToFile(InputStream inputStream, File output) throws IOException {
        File parent = output.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }

        File temp = parent == null
                ? new File(output.getPath() + ".tmp-" + System.nanoTime())
                : new File(parent, output.getName() + ".tmp-" + System.nanoTime());
        Files.copy(inputStream, temp.toPath(), StandardCopyOption.REPLACE_EXISTING);
        try {
            Files.move(temp.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(temp.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void writeManagedPackMeta(File managedFolder) throws IOException {
        managedFolder.mkdirs();
        int packFormat = INMS.get().getDataVersion().getPackFormat();
        JSONObject root = new JSONObject();
        JSONObject pack = new JSONObject();
        pack.put("description", MANAGED_PACK_META_DESCRIPTION);
        pack.put("pack_format", packFormat);
        root.put("pack", pack);
        Files.writeString(new File(managedFolder, "pack.mcmeta").toPath(), root.toString(4), StandardCharsets.UTF_8);
    }

    private static boolean shouldProjectEntry(ProjectedEntry entry, DatapackRequest request) {
        if (entry == null) {
            return false;
        }

        if (!"minecraft".equals(entry.namespace())) {
            return true;
        }

        if (request.alongsideMode()) {
            return true;
        }

        if (!request.replaceVanilla()) {
            return false;
        }

        if (!request.hasReplacementTargets()) {
            return false;
        }

        return switch (entry.type()) {
            case STRUCTURE -> request.structures().contains(entry.key());
            case STRUCTURE_SET -> request.structureSets().contains(entry.key());
            case CONFIGURED_FEATURE -> request.configuredFeatures().contains(entry.key());
            case PLACED_FEATURE -> request.placedFeatures().contains(entry.key());
            case TEMPLATE_POOL -> request.templatePools().contains(entry.key());
            case PROCESSOR_LIST -> request.processorLists().contains(entry.key());
            case BIOME_HAS_STRUCTURE_TAG -> request.biomeHasStructureTags().contains(entry.key());
            case STRUCTURE_NBT -> request.structures().contains(entry.key()) || !request.templatePools().isEmpty();
        };
    }

    private static ProjectedEntry parseProjectedEntry(String relativePath) {
        String normalized = relativePath.replace('\\', '/');
        Matcher matcher = STRUCTURE_JSON_ENTRY.matcher(normalized);
        if (matcher.matches()) {
            String key = normalizeResourceKey(matcher.group(1), matcher.group(2), "worldgen/structure/");
            return key == null ? null : new ProjectedEntry(ProjectedEntryType.STRUCTURE, normalizeNamespace(matcher.group(1)), key);
        }

        matcher = STRUCTURE_SET_JSON_ENTRY.matcher(normalized);
        if (matcher.matches()) {
            String key = normalizeResourceKey(matcher.group(1), matcher.group(2), "worldgen/structure_set/");
            return key == null ? null : new ProjectedEntry(ProjectedEntryType.STRUCTURE_SET, normalizeNamespace(matcher.group(1)), key);
        }

        matcher = CONFIGURED_FEATURE_JSON_ENTRY.matcher(normalized);
        if (matcher.matches()) {
            String key = normalizeResourceKey(matcher.group(1), matcher.group(2), "worldgen/configured_feature/");
            return key == null ? null : new ProjectedEntry(ProjectedEntryType.CONFIGURED_FEATURE, normalizeNamespace(matcher.group(1)), key);
        }

        matcher = PLACED_FEATURE_JSON_ENTRY.matcher(normalized);
        if (matcher.matches()) {
            String key = normalizeResourceKey(matcher.group(1), matcher.group(2), "worldgen/placed_feature/");
            return key == null ? null : new ProjectedEntry(ProjectedEntryType.PLACED_FEATURE, normalizeNamespace(matcher.group(1)), key);
        }

        matcher = TEMPLATE_POOL_JSON_ENTRY.matcher(normalized);
        if (matcher.matches()) {
            String key = normalizeResourceKey(matcher.group(1), matcher.group(2), "worldgen/template_pool/");
            return key == null ? null : new ProjectedEntry(ProjectedEntryType.TEMPLATE_POOL, normalizeNamespace(matcher.group(1)), key);
        }

        matcher = PROCESSOR_LIST_JSON_ENTRY.matcher(normalized);
        if (matcher.matches()) {
            String key = normalizeResourceKey(matcher.group(1), matcher.group(2), "worldgen/processor_list/");
            return key == null ? null : new ProjectedEntry(ProjectedEntryType.PROCESSOR_LIST, normalizeNamespace(matcher.group(1)), key);
        }

        matcher = BIOME_HAS_STRUCTURE_TAG_ENTRY.matcher(normalized);
        if (matcher.matches()) {
            String key = normalizeResourceKey(matcher.group(1), matcher.group(2), "tags/worldgen/biome/has_structure/", "worldgen/biome/has_structure/", "has_structure/");
            return key == null ? null : new ProjectedEntry(ProjectedEntryType.BIOME_HAS_STRUCTURE_TAG, normalizeNamespace(matcher.group(1)), key);
        }

        EntryPath entryPath = resolveEntryPath(normalized);
        if (entryPath == null) {
            return null;
        }
        String key = normalizeResourceKey(entryPath.namespace, stripExtension(entryPath.structurePath));
        return key == null ? null : new ProjectedEntry(ProjectedEntryType.STRUCTURE_NBT, normalizeNamespace(entryPath.namespace), key);
    }

    private static String normalizeRelativePath(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.replace('\\', '/').replaceAll("/+", "/");
        normalized = normalized.replaceAll("^/+", "").replaceAll("/+$", "");
        if (normalized.isBlank() || normalized.contains("..")) {
            return null;
        }
        return normalized;
    }

    private static String buildManagedWorldDatapackName(String targetPack, String sourceKey) {
        String pack = sanitizePackName(targetPack);
        String source = sanitizePath(sourceKey).replace("/", "_");
        if (pack.isBlank()) {
            pack = "pack";
        }
        if (source.isBlank()) {
            source = "source";
        }
        return MANAGED_WORLD_PACK_PREFIX + pack + "-" + source;
    }

    private static String normalizeNamespace(String namespace) {
        String cleaned = sanitizePath(namespace);
        return cleaned.isBlank() ? "minecraft" : cleaned;
    }

    private static String normalizeResourceKey(String namespace, String value, String... prefixes) {
        String normalizedNamespace = normalizeNamespace(namespace);
        if (value == null || value.isBlank()) {
            return null;
        }

        String cleaned = value.trim().replace('\\', '/');
        if (cleaned.startsWith("#")) {
            cleaned = cleaned.substring(1);
        }
        if (cleaned.isBlank()) {
            return null;
        }

        String keyNamespace = normalizedNamespace;
        String path = cleaned;
        int colon = cleaned.indexOf(':');
        if (colon > 0 && colon < cleaned.length() - 1) {
            keyNamespace = normalizeNamespace(cleaned.substring(0, colon));
            path = cleaned.substring(colon + 1);
        }

        path = sanitizePath(path);
        if (path.isBlank()) {
            return null;
        }

        if (prefixes != null) {
            for (String prefix : prefixes) {
                String cleanedPrefix = sanitizePath(prefix);
                if (!cleanedPrefix.isBlank() && path.startsWith(cleanedPrefix)) {
                    path = path.substring(cleanedPrefix.length());
                }
            }
        }

        path = path.replaceAll("^/+", "").replaceAll("/+$", "");
        if (path.endsWith(".json")) {
            path = stripExtension(path);
        }
        if (path.endsWith(".nbt")) {
            path = stripExtension(path);
        }
        if (path.isBlank()) {
            return null;
        }

        return keyNamespace + ":" + path;
    }

    private static String normalizeBiomeKey(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String cleaned = value.trim().replace('\\', '/');
        if (cleaned.startsWith("#")) {
            cleaned = cleaned.substring(1);
        }
        if (cleaned.isBlank()) {
            return null;
        }

        return normalizeResourceKey("minecraft", cleaned, "worldgen/biome/");
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

    private static File resolveSourceRoot(String targetPack, String objectRootKey) {
        String pack = sanitizePackName(targetPack);
        if (pack.isEmpty()) {
            pack = defaultTargetPack();
        }
        String normalizedObjectRootKey = normalizeObjectRootKey(objectRootKey);
        if (normalizedObjectRootKey.isEmpty()) {
            normalizedObjectRootKey = "external-datapack";
        }
        return new File(Iris.instance.getDataFolder("packs", pack), "objects/" + normalizedObjectRootKey);
    }

    private static File resolveLegacySourceRoot(String targetPack, String sourceKey) {
        String pack = sanitizePackName(targetPack);
        if (pack.isEmpty()) {
            pack = defaultTargetPack();
        }
        return new File(Iris.instance.getDataFolder("packs", pack), "objects/" + IMPORT_PREFIX + "/" + sourceKey);
    }

    private static SourceDescriptor createSourceDescriptor(File entry, String requestId, String targetPack, String requiredEnvironment) {
        String base = entry.getName();
        String sanitized = sanitizePath(stripExtension(base));
        if (sanitized.isEmpty()) {
            sanitized = "source";
        }
        String objectRootKey = normalizeObjectRootKey(requestId);
        String sourceHash = shortHash(entry.getAbsolutePath() + "|" + objectRootKey);
        String sourceKey = objectRootKey + "-" + sanitized + "-" + sourceHash;
        String fingerprint = entry.isFile()
                ? "file:" + entry.length() + ":" + entry.lastModified()
                : "dir:" + directoryFingerprint(entry);
        String pack = sanitizePackName(targetPack);
        if (pack.isEmpty()) {
            pack = defaultTargetPack();
        }
        return new SourceDescriptor(sourceKey, base, fingerprint, pack, normalizeEnvironment(requiredEnvironment), objectRootKey);
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

    private static JSONObject convertSource(File entry, SourceDescriptor sourceDescriptor, File sourceRoot, String requestId) {
        SourceConversion conversion = new SourceConversion(
                sourceDescriptor.sourceKey(),
                sourceDescriptor.sourceName(),
                sourceDescriptor.targetPack(),
                sourceDescriptor.requiredEnvironment(),
                sourceDescriptor.objectRootKey(),
                requestId
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
        if (result.skipped) {
            conversion.skipped++;
            return;
        }

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

        if (isEmptyStructure(compoundTag)) {
            return ConversionResult.skipped();
        }

        IrisObject object = toObject(compoundTag);
        if (object == null) {
            return ConversionResult.failed();
        }

        String relative = objectKey;
        int slash = relative.indexOf('/');
        if (slash <= 0 || slash + 1 >= relative.length()) {
            return ConversionResult.failed();
        }
        relative = relative.substring(slash + 1);
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

    private static boolean isEmptyStructure(CompoundTag root) {
        ListTag<?> sizeList = root.getListTag("size");
        if (sizeList == null || sizeList.size() < 3) {
            return false;
        }

        Integer width = tagToInt(sizeList.get(0));
        Integer height = tagToInt(sizeList.get(1));
        Integer depth = tagToInt(sizeList.get(2));
        if (width == null || height == null || depth == null) {
            return false;
        }

        if (width != 0 || height != 0 || depth != 0) {
            return false;
        }

        ListTag<?> blocksTag = root.getListTag("blocks");
        if (blocksTag != null && blocksTag.size() > 0) {
            return false;
        }

        ListTag<?> paletteTag = root.getListTag("palette");
        return paletteTag == null || paletteTag.size() == 0;
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

            String objectRootKey = source == null ? "" : normalizeObjectRootKey(source.optString("objectRootKey", ""));
            if (!objectRootKey.isBlank()) {
                deleteFolder(resolveSourceRoot(targetPack, objectRootKey));
            }
            deleteFolder(resolveLegacySourceRoot(targetPack, sourceKey));
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

    public record DatapackRequest(
            String id,
            String url,
            String targetPack,
            String requiredEnvironment,
            boolean required,
            boolean replaceVanilla,
            Set<String> structures,
            Set<String> structureSets,
            Set<String> configuredFeatures,
            Set<String> placedFeatures,
            Set<String> templatePools,
            Set<String> processorLists,
            Set<String> biomeHasStructureTags,
            Map<String, Integer> structureStartHeights,
            Set<String> forcedBiomeKeys,
            String scopeKey,
            boolean alongsideMode,
            Set<String> resolvedLocateStructures
    ) {
        public DatapackRequest(
                String id,
                String url,
                String targetPack,
                String requiredEnvironment,
                boolean required,
                boolean replaceVanilla,
                IrisExternalDatapackReplaceTargets replaceTargets,
                KList<IrisExternalDatapackStructurePatch> structurePatches
        ) {
            this(
                    id,
                    url,
                    targetPack,
                    requiredEnvironment,
                    required,
                    replaceVanilla,
                    replaceTargets,
                    structurePatches,
                    Set.of(),
                    "dimension-root",
                    !replaceVanilla,
                    Set.of()
            );
        }

        public DatapackRequest(
                String id,
                String url,
                String targetPack,
                String requiredEnvironment,
                boolean required,
                boolean replaceVanilla,
                IrisExternalDatapackReplaceTargets replaceTargets,
                KList<IrisExternalDatapackStructurePatch> structurePatches,
                Set<String> forcedBiomeKeys,
                String scopeKey,
                boolean alongsideMode,
                Set<String> resolvedLocateStructures
        ) {
            this(
                    normalizeRequestId(id, url),
                    url == null ? "" : url.trim(),
                    normalizeRequestPack(targetPack),
                    normalizeEnvironment(requiredEnvironment),
                    required,
                    replaceVanilla,
                    normalizeTargets(replaceTargets == null ? null : replaceTargets.getStructures(), "worldgen/structure/"),
                    normalizeTargets(replaceTargets == null ? null : replaceTargets.getStructureSets(), "worldgen/structure_set/"),
                    normalizeTargets(replaceTargets == null ? null : replaceTargets.getConfiguredFeatures(), "worldgen/configured_feature/"),
                    normalizeTargets(replaceTargets == null ? null : replaceTargets.getPlacedFeatures(), "worldgen/placed_feature/"),
                    normalizeTargets(replaceTargets == null ? null : replaceTargets.getTemplatePools(), "worldgen/template_pool/"),
                    normalizeTargets(replaceTargets == null ? null : replaceTargets.getProcessorLists(), "worldgen/processor_list/"),
                    normalizeTargets(replaceTargets == null ? null : replaceTargets.getBiomeHasStructureTags(),
                            "tags/worldgen/biome/has_structure/",
                            "worldgen/biome/has_structure/",
                            "has_structure/"),
                    normalizeStructureStartHeights(structurePatches),
                    normalizeBiomeKeys(forcedBiomeKeys),
                    normalizeScopeKey(scopeKey),
                    alongsideMode,
                    normalizeLocateStructures(resolvedLocateStructures, replaceTargets == null ? null : replaceTargets.getStructures())
            );
        }

        public DatapackRequest {
            id = normalizeRequestId(id, url);
            url = url == null ? "" : url.trim();
            targetPack = normalizeRequestPack(targetPack);
            requiredEnvironment = normalizeEnvironment(requiredEnvironment);
            structures = immutableSet(structures);
            structureSets = immutableSet(structureSets);
            configuredFeatures = immutableSet(configuredFeatures);
            placedFeatures = immutableSet(placedFeatures);
            templatePools = immutableSet(templatePools);
            processorLists = immutableSet(processorLists);
            biomeHasStructureTags = immutableSet(biomeHasStructureTags);
            structureStartHeights = immutableMap(structureStartHeights);
            forcedBiomeKeys = immutableBiomeSet(forcedBiomeKeys);
            scopeKey = normalizeScopeKey(scopeKey);
            alongsideMode = alongsideMode || !replaceVanilla;
            resolvedLocateStructures = immutableLocateSet(resolvedLocateStructures, structures);
        }

        public String getDedupeKey() {
            return targetPack + "|" + url + "|" + scopeKey + "|" + replaceVanilla + "|" + required + "|" + setFingerprint(forcedBiomeKeys);
        }

        public boolean hasReplacementTargets() {
            return !structures.isEmpty()
                    || !structureSets.isEmpty()
                    || !configuredFeatures.isEmpty()
                    || !placedFeatures.isEmpty()
                    || !templatePools.isEmpty()
                    || !processorLists.isEmpty()
                    || !biomeHasStructureTags.isEmpty();
        }

        public DatapackRequest merge(DatapackRequest other) {
            if (other == null) {
                return this;
            }
            String environment = requiredEnvironment;
            if ((environment == null || environment.isBlank()) && other.requiredEnvironment != null && !other.requiredEnvironment.isBlank()) {
                environment = other.requiredEnvironment;
            }
            return new DatapackRequest(
                    id,
                    url,
                    targetPack,
                    environment,
                    required || other.required,
                    replaceVanilla || other.replaceVanilla,
                    union(structures, other.structures),
                    union(structureSets, other.structureSets),
                    union(configuredFeatures, other.configuredFeatures),
                    union(placedFeatures, other.placedFeatures),
                    union(templatePools, other.templatePools),
                    union(processorLists, other.processorLists),
                    union(biomeHasStructureTags, other.biomeHasStructureTags),
                    unionStructureStartHeights(structureStartHeights, other.structureStartHeights),
                    union(forcedBiomeKeys, other.forcedBiomeKeys),
                    normalizeScopeKey(scopeKey),
                    alongsideMode || other.alongsideMode,
                    union(resolvedLocateStructures, other.resolvedLocateStructures)
            );
        }

        private static String normalizeRequestId(String id, String url) {
            String cleaned = id == null ? "" : id.trim();
            if (!cleaned.isBlank()) {
                return cleaned;
            }
            return url == null ? "" : url.trim();
        }

        private static String normalizeRequestPack(String targetPack) {
            String sanitized = sanitizePackName(targetPack);
            if (!sanitized.isBlank()) {
                return sanitized;
            }
            return defaultTargetPack();
        }

        private static Set<String> normalizeBiomeKeys(Set<String> values) {
            LinkedHashSet<String> normalized = new LinkedHashSet<>();
            if (values == null) {
                return normalized;
            }

            for (String value : values) {
                String normalizedBiome = normalizeBiomeKey(value);
                if (normalizedBiome != null && !normalizedBiome.isBlank()) {
                    normalized.add(normalizedBiome);
                }
            }

            return normalized;
        }

        private static Set<String> normalizeLocateStructures(Set<String> values, KList<String> structureValues) {
            LinkedHashSet<String> normalized = new LinkedHashSet<>();
            if (values != null) {
                for (String value : values) {
                    String normalizedStructure = normalizeLocateStructure(value);
                    if (!normalizedStructure.isBlank()) {
                        normalized.add(normalizedStructure);
                    }
                }
            }

            if (structureValues != null) {
                for (String structure : structureValues) {
                    String normalizedStructure = normalizeLocateStructure(structure);
                    if (!normalizedStructure.isBlank()) {
                        normalized.add(normalizedStructure);
                    }
                }
            }

            return normalized;
        }

        private static Set<String> normalizeTargets(KList<String> values, String... prefixes) {
            LinkedHashSet<String> normalized = new LinkedHashSet<>();
            if (values == null) {
                return normalized;
            }
            for (String value : values) {
                String normalizedKey = normalizeResourceKey("minecraft", value, prefixes);
                if (normalizedKey != null && !normalizedKey.isBlank()) {
                    normalized.add(normalizedKey);
                }
            }
            return normalized;
        }

        private static Set<String> immutableSet(Set<String> values) {
            LinkedHashSet<String> copy = new LinkedHashSet<>();
            if (values != null) {
                copy.addAll(values);
            }
            return Set.copyOf(copy);
        }

        private static Set<String> immutableBiomeSet(Set<String> values) {
            LinkedHashSet<String> copy = new LinkedHashSet<>();
            if (values != null) {
                for (String value : values) {
                    String normalized = normalizeBiomeKey(value);
                    if (normalized != null && !normalized.isBlank()) {
                        copy.add(normalized);
                    }
                }
            }
            return Set.copyOf(copy);
        }

        private static Set<String> immutableLocateSet(Set<String> values, Set<String> structureTargets) {
            LinkedHashSet<String> copy = new LinkedHashSet<>();
            if (values != null) {
                for (String value : values) {
                    String normalized = normalizeLocateStructure(value);
                    if (!normalized.isBlank()) {
                        copy.add(normalized);
                    }
                }
            }
            if (structureTargets != null) {
                for (String structureTarget : structureTargets) {
                    String normalized = normalizeLocateStructure(structureTarget);
                    if (!normalized.isBlank()) {
                        copy.add(normalized);
                    }
                }
            }
            return Set.copyOf(copy);
        }

        private static Map<String, Integer> immutableMap(Map<String, Integer> values) {
            LinkedHashMap<String, Integer> copy = new LinkedHashMap<>();
            if (values != null) {
                copy.putAll(values);
            }
            return Map.copyOf(copy);
        }

        private static Set<String> union(Set<String> first, Set<String> second) {
            LinkedHashSet<String> merged = new LinkedHashSet<>();
            if (first != null) {
                merged.addAll(first);
            }
            if (second != null) {
                merged.addAll(second);
            }
            return merged;
        }

        private static Map<String, Integer> normalizeStructureStartHeights(KList<IrisExternalDatapackStructurePatch> patches) {
            LinkedHashMap<String, Integer> normalized = new LinkedHashMap<>();
            if (patches == null) {
                return normalized;
            }

            for (IrisExternalDatapackStructurePatch patch : patches) {
                if (patch == null || !patch.isEnabled()) {
                    continue;
                }

                String structure = patch.getStructure();
                if (structure == null || structure.isBlank()) {
                    continue;
                }

                String normalizedStructure = normalizeResourceKey("minecraft", structure, "worldgen/structure/");
                if (normalizedStructure == null || normalizedStructure.isBlank()) {
                    continue;
                }

                normalized.put(normalizedStructure, patch.getStartHeightAbsolute());
            }

            return normalized;
        }

        private static Map<String, Integer> unionStructureStartHeights(Map<String, Integer> first, Map<String, Integer> second) {
            LinkedHashMap<String, Integer> merged = new LinkedHashMap<>();
            if (first != null) {
                merged.putAll(first);
            }
            if (second != null) {
                merged.putAll(second);
            }
            return merged;
        }

        private static String normalizeScopeKey(String value) {
            String normalized = sanitizePath(value).replace("/", "_");
            if (normalized.isBlank()) {
                return "dimension-root";
            }
            return normalized;
        }

        private static String setFingerprint(Set<String> values) {
            if (values == null || values.isEmpty()) {
                return "none";
            }
            ArrayList<String> sorted = new ArrayList<>(values);
            sorted.sort(String::compareTo);
            return shortHash(String.join(",", sorted));
        }
    }

    public static final class PipelineSummary {
        private int requests;
        private int syncedRequests;
        private int restoredRequests;
        private int optionalFailures;
        private int requiredFailures;
        private int importedSources;
        private int cachedSources;
        private int scannedStructures;
        private int convertedStructures;
        private int failedConversions;
        private int skippedConversions;
        private int entitiesIgnored;
        private int blockEntities;
        private int worldDatapacksInstalled;
        private int worldAssetsInstalled;
        private int legacyDownloadRemovals;
        private int legacyWorldCopyRemovals;

        private void setImportSummary(ImportSummary importSummary) {
            if (importSummary == null) {
                return;
            }
            this.importedSources = importSummary.getSources();
            this.cachedSources = importSummary.getCachedSources();
            this.scannedStructures = importSummary.getNbtScanned();
            this.convertedStructures = importSummary.getConverted();
            this.failedConversions = importSummary.getFailed();
            this.skippedConversions = importSummary.getSkipped();
            this.entitiesIgnored = importSummary.getEntitiesIgnored();
            this.blockEntities = importSummary.getBlockEntities();
        }

        public int getRequests() {
            return requests;
        }

        public int getSyncedRequests() {
            return syncedRequests;
        }

        public int getRestoredRequests() {
            return restoredRequests;
        }

        public int getOptionalFailures() {
            return optionalFailures;
        }

        public int getRequiredFailures() {
            return requiredFailures;
        }

        public int getImportedSources() {
            return importedSources;
        }

        public int getCachedSources() {
            return cachedSources;
        }

        public int getScannedStructures() {
            return scannedStructures;
        }

        public int getConvertedStructures() {
            return convertedStructures;
        }

        public int getFailedConversions() {
            return failedConversions;
        }

        public int getSkippedConversions() {
            return skippedConversions;
        }

        public int getEntitiesIgnored() {
            return entitiesIgnored;
        }

        public int getBlockEntities() {
            return blockEntities;
        }

        public int getWorldDatapacksInstalled() {
            return worldDatapacksInstalled;
        }

        public int getWorldAssetsInstalled() {
            return worldAssetsInstalled;
        }

        public int getLegacyDownloadRemovals() {
            return legacyDownloadRemovals;
        }

        public int getLegacyWorldCopyRemovals() {
            return legacyWorldCopyRemovals;
        }
    }

    private record RequestedSourceInput(File source, DatapackRequest request) {
    }

    private record ResolvedRemoteFile(String url, String outputFileName, String sha1) {
    }

    private record RequestSyncResult(boolean success, boolean downloaded, boolean restored, File source, String error) {
        private static RequestSyncResult downloaded(File source) {
            return new RequestSyncResult(true, true, false, source, "");
        }

        private static RequestSyncResult restored(File source) {
            return new RequestSyncResult(true, false, true, source, "");
        }

        private static RequestSyncResult failure(String error) {
            return new RequestSyncResult(false, false, false, null, error == null ? "unknown error" : error);
        }
    }

    private record ProjectedEntry(ProjectedEntryType type, String namespace, String key) {
    }

    private enum ProjectedEntryType {
        STRUCTURE,
        STRUCTURE_SET,
        CONFIGURED_FEATURE,
        PLACED_FEATURE,
        TEMPLATE_POOL,
        PROCESSOR_LIST,
        STRUCTURE_NBT,
        BIOME_HAS_STRUCTURE_TAG
    }

    private record ProjectedDependency(ProjectedEntryType type, String key) {
    }

    private record ProjectionSelection(List<ProjectionInputAsset> assets, Set<String> missingSeededTargets) {
        private ProjectionSelection {
            assets = assets == null ? List.of() : List.copyOf(assets);
            missingSeededTargets = missingSeededTargets == null ? Set.of() : Set.copyOf(missingSeededTargets);
        }

        private static ProjectionSelection empty() {
            return new ProjectionSelection(List.of(), Set.of());
        }
    }

    private record ProjectionResult(
            boolean success,
            int installedDatapacks,
            int installedAssets,
            Set<String> resolvedLocateStructures,
            int syntheticStructureSets,
            String managedName,
            String error
    ) {
        private ProjectionResult {
            LinkedHashSet<String> normalized = new LinkedHashSet<>();
            if (resolvedLocateStructures != null) {
                for (String structure : resolvedLocateStructures) {
                    String normalizedStructure = normalizeLocateStructure(structure);
                    if (!normalizedStructure.isBlank()) {
                        normalized.add(normalizedStructure);
                    }
                }
            }
            resolvedLocateStructures = Set.copyOf(normalized);
            syntheticStructureSets = Math.max(0, syntheticStructureSets);
            if (error == null) {
                error = "";
            }
        }

        private static ProjectionResult success(
                String managedName,
                int installedDatapacks,
                int installedAssets,
                Set<String> resolvedLocateStructures,
                int syntheticStructureSets
        ) {
            return new ProjectionResult(true, installedDatapacks, installedAssets, resolvedLocateStructures, syntheticStructureSets, managedName, "");
        }

        private static ProjectionResult failure(String managedName, String error) {
            String message = error == null || error.isBlank() ? "projection failed" : error;
            return new ProjectionResult(false, 0, 0, Set.of(), 0, managedName, message);
        }
    }

    private record ProjectionInputAsset(String relativePath, ProjectedEntry entry, byte[] bytes) {
        private ProjectionInputAsset {
            bytes = bytes == null ? new byte[0] : Arrays.copyOf(bytes, bytes.length);
        }
    }

    private record ProjectionOutputAsset(String relativePath, byte[] bytes) {
        private ProjectionOutputAsset {
            bytes = bytes == null ? new byte[0] : Arrays.copyOf(bytes, bytes.length);
        }
    }

    private record ProjectionAssetSummary(List<ProjectionOutputAsset> assets, Set<String> resolvedLocateStructures, int syntheticStructureSets) {
        private ProjectionAssetSummary {
            assets = assets == null ? List.of() : List.copyOf(assets);
            LinkedHashSet<String> normalized = new LinkedHashSet<>();
            if (resolvedLocateStructures != null) {
                for (String structure : resolvedLocateStructures) {
                    String normalizedStructure = normalizeLocateStructure(structure);
                    if (!normalizedStructure.isBlank()) {
                        normalized.add(normalizedStructure);
                    }
                }
            }
            resolvedLocateStructures = Set.copyOf(normalized);
            syntheticStructureSets = Math.max(0, syntheticStructureSets);
        }
    }

    private record SyntheticPlacement(Identifier structureSetKey, StructurePlacement placement, int weight) {
    }

    private record SyntheticStructureSetResult(List<ProjectionOutputAsset> assets, int count) {
        private SyntheticStructureSetResult {
            assets = assets == null ? List.of() : List.copyOf(assets);
            count = Math.max(0, count);
        }

        private static SyntheticStructureSetResult empty() {
            return new SyntheticStructureSetResult(List.of(), 0);
        }
    }

    private record EntryPath(String originalPath, String namespace, String structurePath) {
    }

    private record SourceDescriptor(
            String sourceKey,
            String sourceName,
            String fingerprint,
            String targetPack,
            String requiredEnvironment,
            String objectRootKey
    ) {
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
        private final String objectRootKey;
        private final Set<String> usedKeys;
        private final JSONArray objects;
        private int nbtScanned;
        private int converted;
        private int failed;
        private int skipped;
        private int entitiesIgnored;
        private int blockEntities;

        private SourceConversion(
                String sourceKey,
                String sourceName,
                String targetPack,
                String requiredEnvironment,
                String objectRootKey,
                String requestId
        ) {
            this.sourceKey = sourceKey;
            this.sourceName = sourceName;
            this.targetPack = targetPack;
            this.requiredEnvironment = requiredEnvironment;
            String effectiveRequestId = requestId == null ? "" : requestId;
            this.objectRootKey = normalizeObjectRootKey(effectiveRequestId.isBlank() ? objectRootKey : effectiveRequestId);
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
            String baseKey = objectRootKey + "/" + structureValue;
            if (usedKeys.add(baseKey)) {
                return baseKey;
            }
            String namespacedKey = objectRootKey + "/" + namespacePath + "/" + structureValue;
            return createUniqueKey(namespacedKey, usedKeys);
        }

        private JSONObject toJson(String fingerprint) {
            JSONObject source = new JSONObject();
            source.put("sourceKey", sourceKey);
            source.put("sourceName", sourceName);
            source.put("targetPack", targetPack);
            source.put("objectRootKey", objectRootKey);
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
        private final boolean skipped;
        private final JSONObject record;
        private final int blockEntities;
        private final boolean entitiesIgnored;

        private ConversionResult(boolean success, boolean skipped, JSONObject record, int blockEntities, boolean entitiesIgnored) {
            this.success = success;
            this.skipped = skipped;
            this.record = record;
            this.blockEntities = blockEntities;
            this.entitiesIgnored = entitiesIgnored;
        }

        private static ConversionResult success(JSONObject record, int blockEntities, boolean entitiesIgnored) {
            return new ConversionResult(true, false, record, blockEntities, entitiesIgnored);
        }

        private static ConversionResult failed() {
            return new ConversionResult(false, false, null, 0, false);
        }

        private static ConversionResult skipped() {
            return new ConversionResult(false, true, null, 0, false);
        }
    }
}
