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

package art.arcane.iris.core.structure;

import art.arcane.iris.core.structure.authoring.StructureBackend;
import art.arcane.iris.core.structure.authoring.StructureCapability;
import art.arcane.iris.core.structure.authoring.StructureKey;
import art.arcane.iris.core.structure.authoring.StructureLoss;
import art.arcane.iris.core.structure.authoring.StructureResourceBundle;
import art.arcane.iris.core.structure.authoring.StructureSource;
import art.arcane.iris.core.structure.authoring.StructureWriteMode;
import art.arcane.iris.core.structure.authoring.StructureWriteResult;
import art.arcane.iris.engine.framework.structure.StructureGraphValidationException;
import art.arcane.iris.engine.framework.structure.StructureResourceBundleGraphCompiler;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawBranchFailurePolicy;
import art.arcane.iris.engine.object.prefab.IrisObject;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.volmit.geometry.BlockFace;
import com.volmit.resource.ResourceId;
import com.volmit.tooling.JigsawStructureInfo;
import com.volmit.tooling.PlatformStructures;
import com.volmit.tooling.StructureAccessException;
import com.volmit.tooling.StructureTemplate;
import com.volmit.tooling.TemplatePool;
import com.volmit.tooling.ToolPack;

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
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class VillageImporter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<String> PRINTED_FAILURE_SIGNATURES = ConcurrentHashMap.newKeySet();
    private static final String EMPTY_POOL_ELEMENT_TYPE = "EmptyPoolElement";
    private static final String LIST_POOL_ELEMENT_TYPE = "ListPoolElement";
    private static final String FEATURE_POOL_ELEMENT_TYPE = "FeaturePoolElement";
    private static final String AIR_FINAL_STATE = "minecraft:air";

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

    public static Result importVillage(ToolPack pack, PlatformStructures platform, ResourceId structureKey, String name,
                                       StructureImporter.Mode mode) {
        return importVillage(pack, platform, structureKey, name, mode, StructureImporter.Ownership.EDITABLE);
    }

    static Result importVillage(
            ToolPack pack,
            PlatformStructures platform,
            ResourceId structureKey,
            String name,
            StructureImporter.Mode mode,
            StructureImporter.Ownership ownership
    ) {
        StructureImporter.Mode activeMode = mode == null ? StructureImporter.Mode.ADD_ONLY : mode;
        List<StructureLoss> losses = new ArrayList<>();
        boolean retryableFailure = false;
        String writeNote = "";

        JigsawStructureInfo structure;
        try {
            Optional<JigsawStructureInfo> info = platform.jigsaw(structureKey);
            if (info.isEmpty()) {
                return failed(platform.structures().contains(structureKey)
                        ? "Structure " + structureKey + " is not a jigsaw structure; use 'import' for single-template structures"
                        : "No structure registered for key " + structureKey, losses);
            }
            structure = info.get();
        } catch (StructureAccessException e) {
            reportFailure(pack, e);
            return failed(e.getMessage(), losses, e.retryable());
        } catch (Throwable e) {
            reportFailure(pack, e);
            return failed("Failed to read jigsaw structure graph: " + e, losses, true);
        }
        int maxDepth = structure.maxDepth();
        int maxDistanceFromCenter = structure.maxDistance();
        Random random = new Random(structure.shuffleSeed());
        String startPoolKey = structure.startPool().toString();

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
            Optional<TemplatePool> registered;
            try {
                registered = platform.pool(ResourceId.parse(poolKey));
            } catch (StructureAccessException e) {
                reportFailure(pack, e);
                retryableFailure = true;
                fatalErrors.add(e.getMessage());
                continue;
            } catch (Throwable e) {
                reportFailure(pack, e);
                retryableFailure = true;
                fatalErrors.add("pool " + poolKey + ": " + e.getMessage());
                continue;
            }
            if (registered.isEmpty()) {
                fatalErrors.add("pool " + poolKey + " is not registered");
                continue;
            }
            TemplatePool pool = registered.get();

            String irisPoolName = poolName(name, poolKey);
            List<Object> pieceEntries = new ArrayList<>();

            if (pool.fallbackFailure().isPresent()) {
                retryableFailure = true;
                losses.add(StructureLoss.warning(
                        StructureCapability.CONNECTORS,
                        "fallback_pool_not_imported",
                        "The fallback for source pool " + poolKey + " could not be resolved: " + pool.fallbackFailure().get())
                        .affecting("jigsaw-pools/" + irisPoolName + ".json"));
            }
            String fallbackKey = pool.fallback().map(ResourceId::toString).orElse(null);
            if (fallbackKey != null && !fallbackKey.equals(poolKey)) {
                poolQueue.add(fallbackKey);
            }

            List<TemplatePool.Member> templates = pool.members();
            for (TemplatePool.Member member : templates) {
                if (member instanceof TemplatePool.Absent) {
                    continue;
                }
                if (member instanceof TemplatePool.Unreadable unreadable) {
                    retryableFailure = true;
                    losses.add(unreadableMemberLoss(unreadable, poolKey).affecting("jigsaw-pools/" + irisPoolName + ".json"));
                    continue;
                }
                TemplatePool.Element element = (TemplatePool.Element) member;
                int weight = element.weight();
                if (element.omittedElements() > 0) {
                    losses.add(listElementFallbackLoss(element.listLevels(), element.omittedElements(), poolKey)
                            .affecting("jigsaw-pools/" + irisPoolName + ".json"));
                }
                if (element.template().isEmpty()) {
                    if (element.type().endsWith(EMPTY_POOL_ELEMENT_TYPE)) {
                        pieceEntries.add(emptyPoolEntry(weight));
                        emittedPoolMembers++;
                    } else {
                        StructureCapability unsupportedCapability = unsupportedCapability(element.type());
                        losses.add(StructureLoss.warning(
                                unsupportedCapability,
                                "unsupported_pool_element",
                                "Skipped unsupported " + element.type() + " in source pool " + poolKey + ".")
                                .affecting("jigsaw-pools/" + irisPoolName + ".json"));
                    }
                    continue;
                }
                ResourceId pieceNbtKey = element.template().get();
                String templateLocation = pieceNbtKey.toString();
                String irisPieceName = pieceName(name, templateLocation);

                ImportedTemplate importedTemplate = importedTemplates.get(irisPieceName);
                if (importedTemplate == null) {
                    Optional<StructureTemplate> sourceTemplate;
                    try {
                        sourceTemplate = platform.template(pieceNbtKey);
                    } catch (Throwable e) {
                        reportFailure(pack, e);
                        retryableFailure = true;
                        fatalErrors.add(templateLocation + ": failed to load structure template: " + failureDetail(e));
                        continue;
                    }
                    if (sourceTemplate.isEmpty()) {
                        fatalErrors.add(templateLocation + ": no loadable structure template was registered");
                        continue;
                    }

                    StructureImporter.CapturedStructure captured;
                    try {
                        captured = StructureImporter.captureStructure(sourceTemplate.get());
                    } catch (Throwable e) {
                        reportFailure(pack, e);
                        retryableFailure = true;
                        fatalErrors.add(templateLocation + ": failed to capture structure template: " + failureDetail(e));
                        continue;
                    }
                    for (StructureLoss loss : captured.losses()) {
                        losses.add(loss.affecting("objects/" + irisPieceName + pack.objects().extension()));
                    }

                    Connectors result = readConnectors(pack, element, random, name, irisPieceName);
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
            StructureSource.Kind sourceKind = structureKey.namespace().equals("minecraft")
                    ? StructureSource.Kind.VANILLA : StructureSource.Kind.DATAPACK;
            StructureSource source = new StructureSource(sourceKind, sourceKey, structure.gameVersion(), "");
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
            StructureWriteResult writeResult = ownership.write(pack, bundle, writeMode);
            reportWriteFailure(pack, writeResult);
            if (!writeResult.successful()) {
                return new Result(false, writeFailureMessage(name, writeResult), emittedPools.size(),
                        emittedPieces.size(), losses, writeResult.failure().isPresent());
            }
            writeNote = writeResultNote(writeResult);
        } catch (StructureGraphValidationException e) {
            return new Result(false, "Failed writing jigsaw resources for '" + name + "': " + e.getMessage(),
                    emittedPools.size(), emittedPieces.size(), losses, retryableFailure);
        } catch (Throwable e) {
            reportFailure(pack, e);
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

    private static StructureLoss unreadableMemberLoss(TemplatePool.Unreadable unreadable, String poolKey) {
        if (unreadable.part() == TemplatePool.Part.ENTRY) {
            return StructureLoss.warning(
                    StructureCapability.LIST_ELEMENTS,
                    "pool_entry_not_imported",
                    "A source entry in pool " + poolKey + " could not be read: " + unreadable.detail());
        }
        return StructureLoss.warning(
                StructureCapability.BLOCKS,
                "template_location_not_imported",
                "A source template location in pool " + poolKey + " could not be read: " + unreadable.detail());
    }

    private static Connectors readConnectors(
            ToolPack pack,
            TemplatePool.Element element,
            Random random,
            String baseName,
            String pieceName
    ) {
        List<Map<String, Object>> connectors = new ArrayList<>();
        Set<String> targets = new HashSet<>();
        List<StructureLoss> losses = new ArrayList<>();
        boolean retryableFailure = false;
        String affectedResource = "jigsaw-pieces/" + pieceName + ".json";
        try {
            TemplatePool.Connectors read = element.connectors().read(random::nextLong);
            if (read.availability() == TemplatePool.Availability.UNAVAILABLE) {
                losses.add(StructureLoss.warning(
                        StructureCapability.CONNECTORS,
                        "connector_extraction_unavailable",
                        "The source pool element does not expose jigsaw connector extraction on this server version.")
                        .affecting(affectedResource));
                return new Connectors(connectors, targets, losses, false);
            }
            if (read.availability() == TemplatePool.Availability.NO_COLLECTION) {
                losses.add(StructureLoss.warning(
                        StructureCapability.CONNECTORS,
                        "connector_extraction_returned_null",
                        "The source pool element returned no connector collection.")
                        .affecting(affectedResource));
                return new Connectors(connectors, targets, losses, false);
            }
            for (TemplatePool.Connector connector : read.connectors()) {
                if (connector instanceof TemplatePool.UnreadableConnector unreadable) {
                    retryableFailure = true;
                    losses.add(StructureLoss.warning(
                            StructureCapability.CONNECTORS,
                            "connector_not_imported",
                            "A source jigsaw connector could not be converted: " + unreadable.detail())
                            .affecting(affectedResource));
                    continue;
                }
                TemplatePool.JigsawConnector jigsaw = (TemplatePool.JigsawConnector) connector;
                connectors.add(connectorJson(jigsaw, baseName));
                jigsaw.pool().ifPresent(pool -> targets.add(pool.toString()));
            }
        } catch (Throwable e) {
            reportFailure(pack, e);
            retryableFailure = true;
            losses.add(StructureLoss.warning(
                    StructureCapability.CONNECTORS,
                    "connector_extraction_failed",
                    "Source jigsaw connectors could not be extracted: " + failureDetail(e))
                    .affecting(affectedResource));
        }
        return new Connectors(connectors, targets, losses, retryableFailure);
    }

    static Map<String, Object> connectorJson(TemplatePool.JigsawConnector jigsaw, String baseName) {
        Map<String, Object> connector = new LinkedHashMap<>();
        Map<String, Object> position = new LinkedHashMap<>();
        position.put("x", jigsaw.position().x());
        position.put("y", jigsaw.position().y());
        position.put("z", jigsaw.position().z());
        connector.put("position", position);
        connector.put("direction", irisDirection(jigsaw.front()));
        connector.put("top", irisDirection(jigsaw.top()));
        connector.put("pool", jigsaw.pool().map(pool -> poolName(baseName, pool.toString())).orElse(""));
        connector.put("name", jigsaw.name());
        connector.put("targetName", jigsaw.target());
        connector.put("joint", jigsaw.aligned() ? "ALIGNED" : "ROLLABLE");
        connector.put("finalState", jigsaw.finalState()
                .map(StructureImporter::normalizeJigsawFinalState)
                .orElse(AIR_FINAL_STATE));
        connector.put("selectionPriority", jigsaw.selectionPriority());
        connector.put("placementPriority", jigsaw.placementPriority());
        return connector;
    }

    private static String irisDirection(BlockFace front) {
        return switch (front) {
            case UP -> "UP_POSITIVE_Y";
            case DOWN -> "DOWN_NEGATIVE_Y";
            case SOUTH -> "SOUTH_POSITIVE_Z";
            case EAST -> "EAST_POSITIVE_X";
            case WEST -> "WEST_NEGATIVE_X";
            default -> "NORTH_NEGATIVE_Z";
        };
    }

    static StructureLoss listElementFallbackLoss(int listLevels, int omitted, String poolKey) {
        String elementLabel = omitted == 1 ? "element" : "elements";
        String levelLabel = listLevels == 1 ? "list level" : "nested list levels";
        return StructureLoss.warning(
                StructureCapability.LIST_ELEMENTS,
                "list_pool_overlays_not_imported",
                "Converted the first physical template from a ListPoolElement in source pool " + poolKey
                        + " and omitted " + omitted + " colocated " + elementLabel
                        + ", including their processors, across " + listLevels + " " + levelLabel + "."
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
        if (elementType.endsWith(LIST_POOL_ELEMENT_TYPE)) {
            return StructureCapability.LIST_ELEMENTS;
        }
        if (elementType.endsWith(FEATURE_POOL_ELEMENT_TYPE)) {
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

    private static void reportFailure(ToolPack pack, Throwable failure) {
        pack.log().error("Jigsaw structure import failed", failure);
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

    private static void reportWriteFailure(ToolPack pack, StructureWriteResult result) {
        result.failure().ifPresent(failure -> reportFailure(pack, failure));
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
}
