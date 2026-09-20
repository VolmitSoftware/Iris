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

package art.arcane.iris.structure;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.volmlib.nativelib.NativeAdapters;
import art.arcane.volmlib.nativelib.terrain.NativeStructureReader;
import art.arcane.iris.structure.authoring.StructureBackend;
import art.arcane.iris.structure.authoring.StructureCapability;
import art.arcane.iris.structure.authoring.StructureKey;
import art.arcane.iris.structure.authoring.StructureLoss;
import art.arcane.iris.structure.authoring.StructureResourceBundle;
import art.arcane.iris.structure.authoring.StructureSource;
import art.arcane.iris.structure.authoring.StructureWriteMode;
import art.arcane.iris.structure.authoring.StructureWriteResult;
import art.arcane.iris.structure.graph.StructureGraphValidationException;
import art.arcane.iris.structure.graph.StructureResourceBundleGraphCompiler;
import art.arcane.iris.structure.jigsaw.IrisJigsawBranchFailurePolicy;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.spi.IrisLogging;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.structure.Structure;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class VillageImporter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<String> PRINTED_FAILURE_SIGNATURES = ConcurrentHashMap.newKeySet();

    public record Result(
            boolean success,
            String message,
            int pools,
            int pieces,
            List<StructureLoss> losses,
            boolean retryableFailure
    ) {
        public Result(boolean success, String message, int pools, int pieces, List<StructureLoss> losses) {
            this(success, message, pools, pieces, losses, false);
        }

        public Result {
            losses = List.copyOf(losses);
        }
    }

    private VillageImporter() {
    }

    public static Result importVillage(IrisData data, NamespacedKey structureKey, String name, StructureImporter.Mode mode) {
        return importVillage(data, structureKey, name, mode, StructureImporter.Ownership.EDITABLE);
    }

    static Result importVillage(
            IrisData data,
            NamespacedKey structureKey,
            String name,
            StructureImporter.Mode mode,
            StructureImporter.Ownership ownership
    ) {
        StructureImporter.Mode activeMode = mode == null ? StructureImporter.Mode.ADD_ONLY : mode;
        List<StructureLoss> losses = new ArrayList<>();
        boolean retryableFailure = false;
        NativeStructureReader.Session session;
        String writeNote = "";
        try {
            session = NativeAdapters.require(NativeStructureReader.class).open();
        } catch (Throwable e) {
            reportFailure(e);
            return failed("Failed to access server registries: " + e, losses, true);
        }

        NativeStructureReader.Structure nativeStructure;
        int maxDepth;
        int maxDistanceFromCenter;
        try {
            nativeStructure = session.structure(structureKey.toString());
            if (nativeStructure == null) {
                return failed("No structure registered for key " + structureKey, losses);
            }
            if (!nativeStructure.jigsaw()) {
                return failed("Structure " + structureKey + " is not a jigsaw structure ("
                        + nativeStructure.typeName() + "); use 'import' for single-template structures", losses);
            }
            maxDepth = nativeStructure.maxDepth();
            maxDistanceFromCenter = nativeStructure.maxDistanceFromCenter();
        } catch (Throwable e) {
            reportFailure(e);
            return failed("Failed to read jigsaw structure graph: " + e, losses, true);
        }

        java.util.Random random = new java.util.Random(structureKey.hashCode());
        String startPoolKey;
        try {
            startPoolKey = nativeStructure.startPoolKey();
        } catch (Throwable e) {
            reportFailure(e);
            return failed("Could not resolve the start pool key for " + structureKey + ": " + e, losses, true);
        }
        if (startPoolKey == null) {
            return failed("Could not resolve the start pool key for " + structureKey, losses);
        }

        Set<String> visitedPools = new HashSet<>();
        Deque<String> poolQueue = new ArrayDeque<>();
        poolQueue.add(startPoolKey);

        Map<String, Map<String, Object>> emittedPools = new LinkedHashMap<>();
        Map<String, Map<String, Object>> emittedPieces = new LinkedHashMap<>();
        Map<String, IrisObject> emittedObjects = new LinkedHashMap<>();
        Map<String, ImportedTemplate> importedTemplates = new LinkedHashMap<>();
        Set<StructureCapability> capabilities = new HashSet<>();
        capabilities.add(StructureCapability.BLOCKS);
        capabilities.add(StructureCapability.CONNECTORS);
        capabilities.add(StructureCapability.IRIS_PLACEMENT);
        List<String> fatalErrors = new ArrayList<>();
        losses.add(StructureLoss.warning(
                StructureCapability.NATIVE_PLACEMENT,
                "native_placement_settings_not_imported",
                "Native jigsaw placement settings other than the start pool, maximum depth, and maximum distance are not represented by the Iris assembly."));
        int pieceBlocks = 0;
        int emittedPoolMembers = 0;

        while (!poolQueue.isEmpty()) {
            String poolKey = poolQueue.poll();
            if (!visitedPools.add(poolKey)) {
                continue;
            }
            NativeStructureReader.Pool pool;
            try {
                pool = session.pool(poolKey);
            } catch (Throwable e) {
                reportFailure(e);
                retryableFailure = true;
                fatalErrors.add("pool " + poolKey + ": " + e.getMessage());
                continue;
            }
            if (pool == null) {
                fatalErrors.add("pool " + poolKey + " is not registered");
                continue;
            }

            String irisPoolName = poolName(name, poolKey);
            List<Object> pieceEntries = new ArrayList<>();

            String fallbackKey = null;
            try {
                fallbackKey = pool.fallbackKey();
            } catch (Throwable e) {
                reportFailure(e);
                retryableFailure = true;
                losses.add(StructureLoss.warning(
                        StructureCapability.CONNECTORS,
                        "fallback_pool_not_imported",
                        "The fallback for source pool " + poolKey + " could not be resolved: " + failureDetail(e))
                        .affecting("jigsaw-pools/" + irisPoolName + ".json"));
            }
            if (fallbackKey != null && !fallbackKey.equals(poolKey)) {
                poolQueue.add(fallbackKey);
            }

            List<NativeStructureReader.Entry> templates;
            try {
                templates = pool.entries();
            } catch (Throwable e) {
                reportFailure(e);
                retryableFailure = true;
                fatalErrors.add("templates " + poolKey + ": " + e.getMessage());
                templates = List.of();
            }

            for (NativeStructureReader.Entry pair : templates) {
                NativeStructureReader.Element element;
                int weight;
                try {
                    element = pair.element();
                    weight = Math.max(1, pair.weight());
                } catch (Throwable e) {
                    reportFailure(e);
                    retryableFailure = true;
                    losses.add(StructureLoss.warning(
                            StructureCapability.LIST_ELEMENTS,
                            "pool_entry_not_imported",
                            "A source entry in pool " + poolKey + " could not be read: " + failureDetail(e))
                            .affecting("jigsaw-pools/" + irisPoolName + ".json"));
                    continue;
                }
                if (element == null) {
                    continue;
                }
                PoolElementResolution elementResolution;
                try {
                    elementResolution = resolvePoolElement(element);
                } catch (Throwable e) {
                    reportFailure(e);
                    retryableFailure = true;
                    losses.add(StructureLoss.warning(
                            StructureCapability.BLOCKS,
                            "template_location_not_imported",
                            "A source template location in pool " + poolKey + " could not be read: " + failureDetail(e))
                            .affecting("jigsaw-pools/" + irisPoolName + ".json"));
                    continue;
                }
                if (elementResolution.omittedElements() > 0) {
                    losses.add(listElementFallbackLoss(elementResolution, poolKey)
                            .affecting("jigsaw-pools/" + irisPoolName + ".json"));
                }
                NativeStructureReader.Element physicalElement = elementResolution.physicalElement();
                String templateLocation = elementResolution.templateLocation();
                if (templateLocation == null) {
                    String elementType = physicalElement == null
                            ? element.typeName()
                            : physicalElement.typeName();
                    if (elementType.endsWith("EmptyPoolElement")) {
                        pieceEntries.add(emptyPoolEntry(weight));
                        emittedPoolMembers++;
                    } else {
                        StructureCapability unsupportedCapability = unsupportedCapability(elementType);
                        losses.add(StructureLoss.warning(
                                unsupportedCapability,
                                "unsupported_pool_element",
                                "Skipped unsupported " + elementType + " in source pool " + poolKey + ".")
                                .affecting("jigsaw-pools/" + irisPoolName + ".json"));
                    }
                    continue;
                }
                NamespacedKey pieceNbtKey = NamespacedKey.fromString(templateLocation.toLowerCase());
                if (pieceNbtKey == null) {
                    fatalErrors.add("invalid piece key " + templateLocation + " in pool " + poolKey);
                    continue;
                }
                String irisPieceName = pieceName(name, templateLocation);

                ImportedTemplate importedTemplate = importedTemplates.get(irisPieceName);
                if (importedTemplate == null) {
                    Structure sourceTemplate;
                    try {
                        sourceTemplate = Bukkit.getStructureManager().loadStructure(pieceNbtKey);
                    } catch (Throwable e) {
                        reportFailure(e);
                        retryableFailure = true;
                        fatalErrors.add(templateLocation + ": failed to load structure template: " + failureDetail(e));
                        continue;
                    }
                    if (sourceTemplate == null || sourceTemplate.getPalettes().isEmpty()) {
                        fatalErrors.add(templateLocation + ": no loadable structure template was registered");
                        continue;
                    }

                    StructureImporter.CapturedStructure captured;
                    try {
                        captured = StructureImporter.captureStructure(sourceTemplate);
                    } catch (Throwable e) {
                        reportFailure(e);
                        retryableFailure = true;
                        fatalErrors.add(templateLocation + ": failed to capture structure template: " + failureDetail(e));
                        continue;
                    }
                    for (StructureLoss loss : captured.losses()) {
                        losses.add(loss.affecting("objects/" + irisPieceName + ".iob"));
                    }

                    Connectors result = readConnectors(element, random, name, irisPieceName);
                    retryableFailure |= result.retryableFailure();
                    importedTemplate = new ImportedTemplate(
                            captured.object(),
                            captured.blocks(),
                            captured.nonAirBlocks(),
                            result.json(),
                            captured.capabilities());
                    importedTemplates.put(irisPieceName, importedTemplate);
                    poolQueue.addAll(result.targetPoolKeys());
                    losses.addAll(result.losses());
                }

                PoolMemberNormalization normalization = normalizePoolMember(
                        poolKey,
                        irisPoolName,
                        poolKey.equals(startPoolKey),
                        templates.size(),
                        fallbackKey,
                        templateLocation,
                        irisPieceName,
                        weight,
                        importedTemplate.nonAirBlocks(),
                        importedTemplate.connectors());
                losses.addAll(normalization.losses());
                if (!emittedPieces.containsKey(irisPieceName)) {
                    pieceBlocks += importedTemplate.emittedBlocks(normalization);
                    capabilities.addAll(importedTemplate.emittedCapabilities(normalization));
                    if (normalization.disposition() == PoolMemberDisposition.PHYSICAL) {
                        emittedObjects.put(irisPieceName, importedTemplate.object());
                        emittedPieces.put(irisPieceName, pieceJson(
                                irisPieceName,
                                importedTemplate.connectors(),
                                importedTemplate.nonAirBlocks()));
                    }
                }
                if (!normalization.poolEntry().isEmpty()) {
                    pieceEntries.add(normalization.poolEntry());
                    emittedPoolMembers++;
                }
            }

            Map<String, Object> poolJson = new LinkedHashMap<>();
            poolJson.put("pieces", pieceEntries);
            if (fallbackKey != null && !fallbackKey.equals(poolKey)) {
                poolJson.put("fallback", poolName(name, fallbackKey));
            }
            emittedPools.put(irisPoolName, poolJson);
        }

        if (!fatalErrors.isEmpty()) {
            return failed("Failed to capture the complete graph for " + structureKey + ": " + fatalErrors.getFirst()
                    + (fatalErrors.size() == 1 ? "" : " (" + (fatalErrors.size() - 1) + " more)"), losses,
                    retryableFailure);
        }
        if (emittedPoolMembers == 0) {
            return failed("Imported 0 attachable or empty pool members for " + structureKey, losses);
        }
        for (StructureLoss loss : losses) {
            if (loss.capability() == StructureCapability.CONNECTORS) {
                capabilities.remove(StructureCapability.CONNECTORS);
                break;
            }
        }

        try {
            StructureKey sourceKey = StructureKey.parse(structureKey.toString());
            StructureSource.Kind sourceKind = structureKey.getNamespace().equals("minecraft")
                    ? StructureSource.Kind.VANILLA : StructureSource.Kind.DATAPACK;
            StructureSource source = new StructureSource(sourceKind, sourceKey, Bukkit.getBukkitVersion(), "");
            Map<String, byte[]> objectResources = new LinkedHashMap<>();
            for (Map.Entry<String, IrisObject> entry : emittedObjects.entrySet()) {
                objectResources.put(entry.getKey(), serialize(entry.getValue()));
            }
            Map<String, Object> rootStructure = structureJson(
                    structureKey.toString(),
                    poolName(name, startPoolKey),
                    maxDepth,
                    maxDistanceFromCenter);
            StructureResourceBundle bundle = buildBundle(
                    new StructureKey("iris", name),
                    source,
                    objectResources,
                    emittedPieces,
                    emittedPools,
                    rootStructure,
                    capabilities,
                    losses);
            StructureResourceBundleGraphCompiler.requireViable(bundle);
            StructureWriteMode writeMode = activeMode == StructureImporter.Mode.OVERWRITE
                    ? StructureWriteMode.OVERWRITE : StructureWriteMode.ADD_ONLY;
            StructureWriteResult writeResult = ownership.write(data, bundle, writeMode);
            reportWriteFailure(writeResult);
            if (!writeResult.successful()) {
                return new Result(false, writeFailureMessage(name, writeResult), emittedPools.size(),
                        emittedPieces.size(), losses, writeResult.failure().isPresent());
            }
            if (writeResult.committed()) {
                data.invalidateStructureResources();
            }
            writeNote = writeResultNote(writeResult);
        } catch (StructureGraphValidationException e) {
            return new Result(false, "Failed writing jigsaw resources for '" + name + "': " + e.getMessage(),
                    emittedPools.size(), emittedPieces.size(), losses, retryableFailure);
        } catch (Throwable e) {
            reportFailure(e);
            return new Result(false, "Failed writing jigsaw resources for '" + name + "': " + e,
                    emittedPools.size(), emittedPieces.size(), losses, true);
        }

        String msg = "Imported jigsaw structure " + structureKey + " as '" + name + "': " + emittedPieces.size() + " pieces, " + emittedPools.size() + " pools, " + pieceBlocks + " blocks";
        if (!losses.isEmpty()) {
            msg += " (" + losses.size() + " fidelity warning(s) recorded)";
        }
        return new Result(true, msg + writeNote, emittedPools.size(), emittedPieces.size(), losses);
    }

    static StructureResourceBundle buildBundle(
            StructureKey bundleKey,
            StructureSource source,
            Map<String, byte[]> objects,
            Map<String, Map<String, Object>> pieces,
            Map<String, Map<String, Object>> pools,
            Map<String, Object> structure,
            Set<StructureCapability> capabilities,
            List<StructureLoss> losses
    ) {
        StructureResourceBundle.Builder bundle = StructureResourceBundle.builder(bundleKey)
                .source(source)
                .backend(StructureBackend.IRIS_ASSEMBLY)
                .capabilities(capabilities)
                .losses(losses);
        for (Map.Entry<String, byte[]> entry : objects.entrySet()) {
            bundle.resource("objects/" + entry.getKey() + ".iob", entry.getValue());
        }
        for (Map.Entry<String, Map<String, Object>> entry : pieces.entrySet()) {
            bundle.textResource("jigsaw-pieces/" + entry.getKey() + ".json", GSON.toJson(entry.getValue()));
        }
        for (Map.Entry<String, Map<String, Object>> entry : pools.entrySet()) {
            bundle.textResource("jigsaw-pools/" + entry.getKey() + ".json", GSON.toJson(entry.getValue()));
        }
        bundle.textResource("structures/" + bundleKey.path() + ".json", GSON.toJson(structure));
        return bundle.build();
    }

    private record Connectors(
            List<Map<String, Object>> json,
            Set<String> targetPoolKeys,
            List<StructureLoss> losses,
            boolean retryableFailure
    ) {
    }

    record ImportedTemplate(
            IrisObject object,
            int blocks,
            int nonAirBlocks,
            List<Map<String, Object>> connectors,
            List<StructureCapability> capabilities
    ) {
        ImportedTemplate {
            connectors = List.copyOf(connectors);
            capabilities = List.copyOf(capabilities);
        }

        int emittedBlocks(PoolMemberNormalization normalization) {
            return normalization.disposition() == PoolMemberDisposition.PHYSICAL ? blocks : 0;
        }

        List<StructureCapability> emittedCapabilities(PoolMemberNormalization normalization) {
            return normalization.disposition() == PoolMemberDisposition.PHYSICAL ? capabilities : List.of();
        }
    }

    enum PoolMemberDisposition {
        PHYSICAL,
        EMPTY,
        OMITTED
    }

    record PoolMemberNormalization(
            PoolMemberDisposition disposition,
            Map<String, Object> poolEntry,
            List<StructureLoss> losses
    ) {
        PoolMemberNormalization {
            poolEntry = Collections.unmodifiableMap(new LinkedHashMap<>(poolEntry));
            losses = List.copyOf(losses);
        }
    }

    private static Connectors readConnectors(
            NativeStructureReader.Element element,
            java.util.Random random,
            String baseName,
            String pieceName
    ) {
        List<Map<String, Object>> connectors = new ArrayList<>();
        Set<String> targets = new HashSet<>();
        List<StructureLoss> losses = new ArrayList<>();
        boolean retryableFailure = false;
        String affectedResource = "jigsaw-pieces/" + pieceName + ".json";
        try {
            NativeStructureReader.ConnectorSet extracted = element.connectors(random::nextLong);
            if (extracted.status() == NativeStructureReader.ConnectorStatus.UNSUPPORTED) {
                losses.add(StructureLoss.warning(
                        StructureCapability.CONNECTORS,
                        "connector_extraction_unavailable",
                        "The source pool element does not expose jigsaw connector extraction on this server version.")
                        .affecting(affectedResource));
                return new Connectors(connectors, targets, losses, false);
            }
            if (extracted.status() == NativeStructureReader.ConnectorStatus.MISSING) {
                losses.add(StructureLoss.warning(
                        StructureCapability.CONNECTORS,
                        "connector_extraction_returned_null",
                        "The source pool element returned no connector collection.")
                        .affecting(affectedResource));
                return new Connectors(connectors, targets, losses, false);
            }
            for (NativeStructureReader.Connector jigsaw : extracted.connectors()) {
                String[] rawPoolKey = new String[1];
                try {
                    Map<String, Object> connector = connectorFrom(jigsaw, baseName, rawPoolKey);
                    connectors.add(connector);
                    if (rawPoolKey[0] != null && !rawPoolKey[0].isEmpty()) {
                        targets.add(rawPoolKey[0]);
                    }
                } catch (Throwable e) {
                    reportFailure(e);
                    retryableFailure = true;
                    losses.add(StructureLoss.warning(
                            StructureCapability.CONNECTORS,
                            "connector_not_imported",
                            "A source jigsaw connector could not be converted: " + failureDetail(e))
                            .affecting(affectedResource));
                }
            }
        } catch (Throwable e) {
            reportFailure(e);
            retryableFailure = true;
            losses.add(StructureLoss.warning(
                    StructureCapability.CONNECTORS,
                    "connector_extraction_failed",
                    "Source jigsaw connectors could not be extracted: " + failureDetail(e))
                    .affecting(affectedResource));
        }
        return new Connectors(connectors, targets, losses, retryableFailure);
    }

    private static Map<String, Object> connectorFrom(NativeStructureReader.Connector jigsaw, String baseName, String[] rawPoolKeyOut) throws Exception {
        NativeStructureReader.ConnectorData value = jigsaw.read();
        rawPoolKeyOut[0] = value.pool();
        return connectorJson(value.x(), value.y(), value.z(), value.front(), value.top(), value.pool(), baseName,
                value.name(), value.target(), value.joint(), readConnectorMetadata(value.metadata()));
    }

    static Map<String, Object> connectorJson(
            int x,
            int y,
            int z,
            String front,
            String top,
            String poolId,
            String baseName,
            String nameId,
            String targetId,
            Object jointType,
            ConnectorMetadata metadata
    ) {
        Map<String, Object> connector = new LinkedHashMap<>();
        Map<String, Object> position = new LinkedHashMap<>();
        position.put("x", x);
        position.put("y", y);
        position.put("z", z);
        connector.put("position", position);
        connector.put("direction", irisDirection(front));
        connector.put("top", irisDirection(top));
        connector.put("pool", poolId == null ? "" : poolName(baseName, poolId));
        connector.put("name", nameId);
        connector.put("targetName", targetId);
        connector.put("joint", jointType != null && jointType.toString().toUpperCase().contains("ALIGN") ? "ALIGNED" : "ROLLABLE");
        connector.put("finalState", metadata.finalState());
        connector.put("selectionPriority", metadata.selectionPriority());
        connector.put("placementPriority", metadata.placementPriority());
        return connector;
    }

    static ConnectorMetadata readConnectorMetadata(NativeStructureReader.ConnectorMetadata source) {
        String finalState = source.finalState();
        String normalizedFinalState = finalState == null || finalState.isBlank()
                ? "minecraft:air"
                : StructureImporter.normalizeJigsawFinalState(finalState);
        return new ConnectorMetadata(normalizedFinalState, source.selectionPriority(), source.placementPriority());
    }

    private static String irisDirection(String front) {
        return switch (front) {
            case "up" -> "UP_POSITIVE_Y";
            case "down" -> "DOWN_NEGATIVE_Y";
            case "south" -> "SOUTH_POSITIVE_Z";
            case "east" -> "EAST_POSITIVE_X";
            case "west" -> "WEST_NEGATIVE_X";
            default -> "NORTH_NEGATIVE_Z";
        };
    }

    static PoolElementResolution resolvePoolElement(NativeStructureReader.Element element) throws Exception {
        if (element == null) {
            return new PoolElementResolution(null, null, 0, 0);
        }
        List<NativeStructureReader.Element> elements = element.children();
        if (elements == null) {
            return new PoolElementResolution(element, element.templateLocation(), 0, 0);
        }
        if (elements.isEmpty()) {
            return new PoolElementResolution(null, null, 1, 0);
        }
        PoolElementResolution primary = resolvePoolElement(elements.getFirst());
        return new PoolElementResolution(
                primary.physicalElement(),
                primary.templateLocation(),
                primary.listLevels() + 1,
                primary.omittedElements() + elements.size() - 1
        );
    }

    static StructureLoss listElementFallbackLoss(PoolElementResolution resolution, String poolKey) {
        int omitted = resolution.omittedElements();
        String elementLabel = omitted == 1 ? "element" : "elements";
        String levelLabel = resolution.listLevels() == 1 ? "list level" : "nested list levels";
        return StructureLoss.warning(
                StructureCapability.LIST_ELEMENTS,
                "list_pool_overlays_not_imported",
                "Converted the first physical template from a ListPoolElement in source pool " + poolKey
                        + " and omitted " + omitted + " colocated " + elementLabel
                        + ", including their processors, across " + resolution.listLevels() + " " + levelLabel + "."
        );
    }

    static String poolName(String base, String poolKey) {
        StructureKey key = StructureKey.parse(poolKey);
        return base + "/pool/" + key.namespace() + "/" + key.path();
    }

    static String pieceName(String base, String templateLocation) {
        StructureKey key = StructureKey.parse(templateLocation);
        return base + "/piece/" + key.namespace() + "/" + key.path();
    }

    static Map<String, Object> pieceJson(
            String pieceName,
            List<Map<String, Object>> connectors,
            int nonAirBlocks
    ) {
        Map<String, Object> piece = new LinkedHashMap<>();
        piece.put("object", pieceName);
        piece.put("connectors", connectors);
        piece.put("rotatable", true);
        if (nonAirBlocks == 0) {
            piece.put("collidable", false);
        }
        return piece;
    }

    static PoolMemberNormalization normalizePoolMember(
            String sourcePoolKey,
            String irisPoolName,
            boolean startPoolMember,
            int sourcePoolMembershipCount,
            String sourceFallbackKey,
            String templateLocation,
            String irisPieceName,
            int weight,
            int nonAirBlocks,
            List<Map<String, Object>> connectors
    ) {
        if (!connectors.isEmpty() || startPoolMember) {
            return new PoolMemberNormalization(
                    PoolMemberDisposition.PHYSICAL,
                    piecePoolEntry(irisPieceName, weight),
                    List.of());
        }
        if (sourceFallbackKey != null
                && !sourceFallbackKey.isBlank()
                && !sourceFallbackKey.equals(sourcePoolKey)) {
            return new PoolMemberNormalization(
                    PoolMemberDisposition.PHYSICAL,
                    piecePoolEntry(irisPieceName, weight),
                    List.of());
        }
        String affectedResource = "jigsaw-pools/" + irisPoolName + ".json";
        if (nonAirBlocks == 0 && sourcePoolMembershipCount == 1) {
            StructureLoss loss = StructureLoss.warning(
                    StructureCapability.IRIS_PLACEMENT,
                    "connectorless_all_air_member_normalized_empty",
                    "Source pool member " + templateLocation + " in " + sourcePoolKey
                            + " captured no non-air blocks and exposed no jigsaw connectors;"
                            + " its singleton membership was normalized to an explicit empty Iris pool entry"
                            + fallbackContext(sourcePoolKey, sourceFallbackKey) + ".")
                    .affecting(affectedResource);
            return new PoolMemberNormalization(
                    PoolMemberDisposition.EMPTY,
                    emptyPoolEntry(weight),
                    List.of(loss));
        }
        if (nonAirBlocks == 0) {
            StructureLoss loss = StructureLoss.warning(
                    StructureCapability.IRIS_PLACEMENT,
                    "connectorless_all_air_mixed_member_omitted",
                    "Source pool member " + templateLocation + " in " + sourcePoolKey
                            + " captured no non-air blocks and exposed no jigsaw connectors;"
                            + " it was omitted from the mixed " + sourcePoolMembershipCount + "-member pool"
                            + fallbackContext(sourcePoolKey, sourceFallbackKey)
                            + ", changing source selection weights and RNG consumption.")
                    .affecting(affectedResource);
            return new PoolMemberNormalization(
                    PoolMemberDisposition.OMITTED,
                    Map.of(),
                    List.of(loss));
        }
        StructureLoss loss = StructureLoss.warning(
                StructureCapability.BLOCKS,
                "connectorless_non_air_member_omitted",
                "Source pool member " + templateLocation + " in " + sourcePoolKey
                        + " captured " + nonAirBlocks + " non-air block(s) but exposed no jigsaw connectors;"
                        + " it was omitted because it cannot attach to the Iris assembly graph"
                        + fallbackContext(sourcePoolKey, sourceFallbackKey)
                        + ", changing source selection weights and RNG consumption while dropping those blocks.")
                .affecting(affectedResource);
        return new PoolMemberNormalization(
                PoolMemberDisposition.OMITTED,
                Map.of(),
                List.of(loss));
    }

    private static String fallbackContext(String sourcePoolKey, String sourceFallbackKey) {
        if (sourceFallbackKey == null || sourceFallbackKey.isBlank()) {
            return " with no source fallback";
        }
        if (sourceFallbackKey.equals(sourcePoolKey)) {
            return " with its source self-fallback";
        }
        return " before source fallback " + sourceFallbackKey;
    }

    static Map<String, Object> emptyPoolEntry(int weight) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("empty", true);
        entry.put("weight", weight);
        return entry;
    }

    static Map<String, Object> piecePoolEntry(String pieceName, int weight) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("piece", pieceName);
        entry.put("weight", weight);
        return entry;
    }

    static Map<String, Object> structureJson(
            String source,
            String startPool,
            int maxDepth,
            int maxDistanceFromCenter
    ) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("startPool", startPool);
        root.put("maxDepth", Math.max(1, Math.min(30, maxDepth)));
        int maxSizeChunks = Math.max(1, Math.min(32, (Math.max(1, maxDistanceFromCenter) + 15) / 16));
        root.put("maxSizeChunks", maxSizeChunks);
        root.put("placeMode", "STRUCTURE_PIECE");
        root.put("branchFailurePolicy", IrisJigsawBranchFailurePolicy.TERMINATE_BRANCH.name());
        root.put("vanillaSource", source);
        return root;
    }

    private static StructureCapability unsupportedCapability(String elementType) {
        if (elementType.endsWith("ListPoolElement")) {
            return StructureCapability.LIST_ELEMENTS;
        }
        if (elementType.endsWith("FeaturePoolElement")) {
            return StructureCapability.FEATURE_ELEMENTS;
        }
        return StructureCapability.BLOCKS;
    }

    private static byte[] serialize(IrisObject object) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        object.write(output);
        return output.toByteArray();
    }

    private static Result failed(String message, List<StructureLoss> losses) {
        return new Result(false, message, 0, 0, losses);
    }

    private static Result failed(String message, List<StructureLoss> losses, boolean retryableFailure) {
        return new Result(false, message, 0, 0, losses, retryableFailure);
    }

    private static void reportFailure(Throwable failure) {
        IrisLogging.reportError(failure);
        if (shouldPrintFullTrace(failure)) {
        }
    }

    /**
     * True the first time a failure signature is seen. A bulk import repeats the same failure once
     * per registered structure, so printing every trace buries the boot log in hundreds of copies
     * of one problem; the per-structure "[fail] key: message" line still reports each occurrence.
     */
    static boolean shouldPrintFullTrace(Throwable failure) {
        if (failure == null) {
            return false;
        }
        StackTraceElement[] trace = failure.getStackTrace();
        String signature = failure.getClass().getName() + '|' + failure.getMessage()
                + '|' + (trace.length == 0 ? "" : trace[0].toString());
        return PRINTED_FAILURE_SIGNATURES.add(signature);
    }

    static void resetFailureLogState() {
        PRINTED_FAILURE_SIGNATURES.clear();
    }

    private static void reportWriteFailure(StructureWriteResult result) {
        result.failure().ifPresent(VillageImporter::reportFailure);
    }

    private static String writeResultNote(StructureWriteResult result) {
        return result.status() == StructureWriteResult.Status.COMMITTED_CLEANUP_REQUIRED
                ? " (committed; staging cleanup is required, see console)" : "";
    }

    private static String writeFailureMessage(String name, StructureWriteResult result) {
        if (result.status() == StructureWriteResult.Status.ADD_ONLY_CONFLICT) {
            return "Skipped (add-only): '" + name + "' already exists";
        }
        if (!result.conflicts().isEmpty()) {
            StructureWriteResult.Conflict conflict = result.conflicts().getFirst();
            return "Import conflict for '" + name + "': " + conflict.relativePath() + " is "
                    + conflict.reason().name().toLowerCase() + ". Existing authored files were preserved.";
        }
        String failure = result.failure().map(VillageImporter::failureDetail).orElse(result.status().name());
        return "Failed writing jigsaw import for '" + name + "': " + failure;
    }

    private static String failureDetail(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    record ConnectorMetadata(String finalState, int selectionPriority, int placementPriority) {
    }

    record PoolElementResolution(
            NativeStructureReader.Element physicalElement,
            String templateLocation,
            int listLevels,
            int omittedElements
    ) {
    }
}
