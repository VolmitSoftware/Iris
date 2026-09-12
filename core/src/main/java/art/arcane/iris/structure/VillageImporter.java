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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        Object server;
        Object registryAccess;
        Object structureManager;
        String writeNote = "";
        try {
            Object craftServer = Bukkit.getServer();
            Object dedicated = invoke(craftServer, "getHandle");
            server = invoke(dedicated, "getServer");
            registryAccess = resolveRegistryAccess(server);
            structureManager = invoke(server, "getStructureManager");
        } catch (Throwable e) {
            reportFailure(e);
            return failed("Failed to access server registries via reflection: " + e, losses, true);
        }
        if (registryAccess == null) {
            return failed("Could not resolve RegistryAccess from the server", losses, true);
        }

        Object startPool;
        int maxDepth;
        int maxDistanceFromCenter;
        try {
            Object structureRegistry = lookupRegistry(registryAccess, "STRUCTURE");
            Object structure = registryGet(structureRegistry, structureKey);
            if (structure == null) {
                return failed("No structure registered for key " + structureKey, losses);
            }
            if (!structure.getClass().getName().endsWith("JigsawStructure")) {
                return failed("Structure " + structureKey + " is not a jigsaw structure ("
                        + structure.getClass().getSimpleName() + "); use 'import' for single-template structures", losses);
            }
            Object startPoolHolder = invoke(structure, "getStartPool");
            startPool = unwrapHolder(startPoolHolder);
            maxDepth = readIntMember(structure, "maxDepth");
            maxDistanceFromCenter = readIntMember(structure, "maxDistanceFromCenter");
        } catch (Throwable e) {
            reportFailure(e);
            return failed("Failed to read jigsaw structure graph: " + e, losses, true);
        }

        Object templatePoolRegistry;
        java.util.Random random;
        try {
            templatePoolRegistry = lookupRegistry(registryAccess, "TEMPLATE_POOL");
            random = new java.util.Random(structureKey.hashCode());
        } catch (Throwable e) {
            reportFailure(e);
            return failed("Failed to access TEMPLATE_POOL registry: " + e, losses, true);
        }

        String startPoolKey;
        try {
            startPoolKey = registryKeyOf(templatePoolRegistry, startPool);
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
            Object pool;
            try {
                pool = registryGetByKey(templatePoolRegistry, poolKey);
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
                Object fallbackHolder = invoke(pool, "getFallback");
                Object fallbackPool = unwrapHolder(fallbackHolder);
                fallbackKey = registryKeyOf(templatePoolRegistry, fallbackPool);
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

            List<?> templates;
            try {
                templates = (List<?>) invoke(pool, "getTemplates");
            } catch (Throwable e) {
                reportFailure(e);
                retryableFailure = true;
                fatalErrors.add("templates " + poolKey + ": " + e.getMessage());
                templates = List.of();
            }

            for (Object pair : templates) {
                Object element;
                int weight;
                try {
                    element = invoke(pair, "getFirst");
                    Object second = invoke(pair, "getSecond");
                    weight = second instanceof Number ? Math.max(1, ((Number) second).intValue()) : 1;
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
                Object physicalElement = elementResolution.physicalElement();
                String templateLocation = elementResolution.templateLocation();
                if (templateLocation == null) {
                    String elementType = physicalElement == null
                            ? element.getClass().getSimpleName()
                            : physicalElement.getClass().getSimpleName();
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

                    Connectors result = readConnectors(element, structureManager, random, name, irisPieceName);
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
            Object element,
            Object structureManager,
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
            Object zero = staticField("net.minecraft.core.BlockPos", "ZERO");
            Object rotationNone = staticField("net.minecraft.world.level.block.Rotation", "NONE");
            Method m = findMethod4(element.getClass(), "getShuffledJigsawBlocks");
            if (m == null) {
                losses.add(StructureLoss.warning(
                        StructureCapability.CONNECTORS,
                        "connector_extraction_unavailable",
                        "The source pool element does not expose jigsaw connector extraction on this server version.")
                        .affecting(affectedResource));
                return new Connectors(connectors, targets, losses, false);
            }
            m.setAccessible(true);
            Object random0 = freshRandomSource(random);
            List<?> blocks = (List<?>) m.invoke(element, structureManager, zero, rotationNone, random0);
            if (blocks == null) {
                losses.add(StructureLoss.warning(
                        StructureCapability.CONNECTORS,
                        "connector_extraction_returned_null",
                        "The source pool element returned no connector collection.")
                        .affecting(affectedResource));
                return new Connectors(connectors, targets, losses, false);
            }
            for (Object jigsaw : blocks) {
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

    private static Map<String, Object> connectorFrom(Object jigsaw, String baseName, String[] rawPoolKeyOut) throws Exception {
        Object info = invoke(jigsaw, "info");
        Object pos = invoke(info, "pos");
        Object blockState = invoke(info, "state");
        int x = readInt(pos, "getX");
        int y = readInt(pos, "getY");
        int z = readInt(pos, "getZ");

        Object poolKey = invoke(jigsaw, "pool");
        String poolId = identifierString(invoke(poolKey, "identifier"));
        Object nameId = invoke(jigsaw, "name");
        Object targetId = invoke(jigsaw, "target");
        Object jointType = invoke(jigsaw, "jointType");

        String front = frontFacing(blockState);
        String top = topFacing(blockState);
        rawPoolKeyOut[0] = poolId;

        ConnectorMetadata metadata = readConnectorMetadata(jigsaw, info);
        return connectorJson(
                x,
                y,
                z,
                front,
                top,
                poolId,
                baseName,
                identifierString(nameId),
                identifierString(targetId),
                jointType,
                metadata
        );
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

    static ConnectorMetadata readConnectorMetadata(Object jigsaw, Object info) throws Exception {
        Object nbt = invoke(info, "nbt");
        String finalState = readNbtString(nbt, "final_state");
        String normalizedFinalState = finalState == null || finalState.isBlank()
                ? "minecraft:air"
                : StructureImporter.normalizeJigsawFinalState(finalState);
        return new ConnectorMetadata(
                normalizedFinalState,
                readIntAccessor(jigsaw, "selectionPriority"),
                readIntAccessor(jigsaw, "placementPriority")
        );
    }

    private static String readNbtString(Object nbt, String key) throws Exception {
        if (nbt == null) {
            return null;
        }
        Method method = findMethod(nbt.getClass(), "getString", 1);
        if (method == null) {
            throw new NoSuchMethodException("getString(String) on " + nbt.getClass().getName());
        }
        method.setAccessible(true);
        Object value = method.invoke(nbt, key);
        if (value instanceof String string) {
            return string;
        }
        if (value instanceof Optional<?> optional) {
            return optional.isPresent() ? String.valueOf(optional.get()) : null;
        }
        return value == null ? null : String.valueOf(value);
    }

    private static int readIntAccessor(Object value, String accessor) throws Exception {
        Method method = findMethod(value.getClass(), accessor);
        if (method == null) {
            throw new NoSuchMethodException(accessor + "() on " + value.getClass().getName());
        }
        method.setAccessible(true);
        Object result = method.invoke(value);
        if (result instanceof Number number) {
            return number.intValue();
        }
        throw new IllegalStateException(accessor + " on " + value.getClass().getName() + " is not numeric");
    }

    private static String frontFacing(Object blockState) throws Exception {
        Class<?> jigsawBlock = Class.forName("net.minecraft.world.level.block.JigsawBlock");
        Method getFront = jigsawBlock.getMethod("getFrontFacing", Class.forName("net.minecraft.world.level.block.state.BlockState"));
        Object direction = getFront.invoke(null, blockState);
        if (direction == null) {
            return "north";
        }
        Method getName = direction.getClass().getMethod("getName");
        getName.setAccessible(true);
        return String.valueOf(getName.invoke(direction)).toLowerCase();
    }

    private static String topFacing(Object blockState) throws Exception {
        Class<?> jigsawBlock = Class.forName("net.minecraft.world.level.block.JigsawBlock");
        Method getTop = jigsawBlock.getMethod("getTopFacing", Class.forName("net.minecraft.world.level.block.state.BlockState"));
        Object direction = getTop.invoke(null, blockState);
        if (direction == null) {
            return "up";
        }
        Method getName = direction.getClass().getMethod("getName");
        getName.setAccessible(true);
        return String.valueOf(getName.invoke(direction)).toLowerCase();
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

    private static String templateLocationOf(Object element) throws Exception {
        Method m = findMethod(element.getClass(), "getTemplateLocation");
        if (m == null) {
            return null;
        }
        m.setAccessible(true);
        Object id = m.invoke(element);
        return identifierString(id);
    }

    static PoolElementResolution resolvePoolElement(Object element) throws Exception {
        if (element == null) {
            return new PoolElementResolution(null, null, 0, 0);
        }
        if (!element.getClass().getSimpleName().endsWith("ListPoolElement")) {
            return new PoolElementResolution(element, templateLocationOf(element), 0, 0);
        }
        Object rawElements = invoke(element, "getElements");
        if (!(rawElements instanceof List<?> elements)) {
            throw new IllegalStateException("getElements on " + element.getClass().getName() + " is not a list");
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

    private static Object resolveRegistryAccess(Object server) {
        try {
            Class<?> frozen = Class.forName("net.minecraft.core.RegistryAccess$Frozen");
            for (Method m : server.getClass().getMethods()) {
                if (m.getParameterCount() == 0 && frozen.isAssignableFrom(m.getReturnType())) {
                    m.setAccessible(true);
                    Object o = m.invoke(server);
                    if (o != null) {
                        return o;
                    }
                }
            }
            Class<?> ra = Class.forName("net.minecraft.core.RegistryAccess");
            for (Method m : server.getClass().getMethods()) {
                if (m.getParameterCount() == 0 && ra.isAssignableFrom(m.getReturnType())) {
                    m.setAccessible(true);
                    Object o = m.invoke(server);
                    if (o != null) {
                        return o;
                    }
                }
            }
        } catch (Throwable e) {
            reportFailure(e);
        }
        return null;
    }

    private static Object lookupRegistry(Object registryAccess, String registryName) throws Exception {
        Class<?> registries = Class.forName("net.minecraft.core.registries.Registries");
        Object resourceKey = registries.getField(registryName).get(null);
        Class<?> registryClass = Class.forName("net.minecraft.core.Registry");
        Method registryOverload = null;
        for (Method m : registryAccess.getClass().getMethods()) {
            if (m.getName().equals("lookupOrThrow") && m.getParameterCount() == 1
                    && m.getParameterTypes()[0].getName().endsWith("ResourceKey")
                    && registryClass.isAssignableFrom(m.getReturnType())) {
                registryOverload = m;
                break;
            }
        }
        if (registryOverload == null) {
            for (Method m : registryAccess.getClass().getMethods()) {
                if (m.getName().equals("lookupOrThrow") && m.getParameterCount() == 1
                        && m.getParameterTypes()[0].getName().endsWith("ResourceKey")) {
                    registryOverload = m;
                    break;
                }
            }
        }
        if (registryOverload == null) {
            throw new NoSuchMethodException("lookupOrThrow(ResourceKey) on " + registryAccess.getClass().getName());
        }
        registryOverload.setAccessible(true);
        return registryOverload.invoke(registryAccess, resourceKey);
    }

    private static Object registryGet(Object registry, NamespacedKey key) throws Exception {
        Object id = identifierOf(key);
        for (Method m : registry.getClass().getMethods()) {
            if (m.getName().equals("getValue") && m.getParameterCount() == 1 && m.getParameterTypes()[0].getName().endsWith("Identifier")) {
                m.setAccessible(true);
                return m.invoke(registry, id);
            }
        }
        for (Method m : registry.getClass().getMethods()) {
            if (m.getName().equals("getOptional") && m.getParameterCount() == 1 && m.getParameterTypes()[0].getName().endsWith("Identifier")) {
                m.setAccessible(true);
                return unwrapOptional(m.invoke(registry, id));
            }
        }
        return null;
    }

    private static Object registryGetByKey(Object registry, String key) throws Exception {
        NamespacedKey nk = NamespacedKey.fromString(key.toLowerCase());
        if (nk == null) {
            return null;
        }
        return registryGet(registry, nk);
    }

    private static String registryKeyOf(Object registry, Object value) throws Exception {
        if (value == null) {
            return null;
        }
        for (Method m : registry.getClass().getMethods()) {
            if (m.getName().equals("getKey") && m.getParameterCount() == 1) {
                m.setAccessible(true);
                Object id = m.invoke(registry, value);
                String s = identifierString(id);
                if (s != null) {
                    return s;
                }
            }
        }
        return null;
    }

    private static Object identifierOf(NamespacedKey key) throws Exception {
        Class<?> identifier = Class.forName("net.minecraft.resources.Identifier");
        try {
            Method fromNamespaceAndPath = identifier.getMethod("fromNamespaceAndPath", String.class, String.class);
            return fromNamespaceAndPath.invoke(null, key.getNamespace(), key.getKey());
        } catch (NoSuchMethodException e) {
            Method withDefaultNamespace = identifier.getMethod("parse", String.class);
            return withDefaultNamespace.invoke(null, key.toString());
        }
    }

    private static String identifierString(Object id) {
        if (id == null) {
            return null;
        }
        try {
            Method getNamespace = id.getClass().getMethod("getNamespace");
            Method getPath = id.getClass().getMethod("getPath");
            getNamespace.setAccessible(true);
            getPath.setAccessible(true);
            return getNamespace.invoke(id) + ":" + getPath.invoke(id);
        } catch (Throwable e) {
            return id.toString();
        }
    }

    private static Object unwrapHolder(Object holder) {
        if (holder == null) {
            return null;
        }
        try {
            Method value = findMethod(holder.getClass(), "value");
            if (value != null) {
                value.setAccessible(true);
                return value.invoke(holder);
            }
        } catch (Throwable ignored) {
        }
        return holder;
    }

    private static Object unwrapOptional(Object opt) {
        if (opt == null) {
            return null;
        }
        if (opt instanceof java.util.Optional<?> o) {
            return o.orElse(null);
        }
        return opt;
    }

    static int readIntMember(Object value, String memberName) throws Exception {
        Class<?> type = value.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(memberName);
                field.setAccessible(true);
                return coerceInt(field.get(value), memberName, value);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        Method method = findMethod(value.getClass(), memberName);
        if (method != null) {
            method.setAccessible(true);
            return coerceInt(method.invoke(value), memberName, value);
        }
        throw new NoSuchFieldException(memberName + " on " + value.getClass().getName());
    }

    /**
     * Members that are plain numbers on one server build are wrapper objects on another
     * (JigsawStructure.maxDistanceFromCenter became a MaxDistance{horizontal, vertical} record).
     * Numbers pass through; a wrapper contributes its horizontal component, else its largest
     * integral component, so a distance bound is never under-read.
     */
    private static int coerceInt(Object member, String memberName, Object owner) throws Exception {
        if (member instanceof Number n) {
            return n.intValue();
        }
        if (member == null) {
            throw new NoSuchFieldException(memberName + " on " + owner.getClass().getName() + " is null");
        }
        Method horizontal = findMethod(member.getClass(), "horizontal");
        if (horizontal != null && Number.class.isAssignableFrom(boxedType(horizontal.getReturnType()))) {
            horizontal.setAccessible(true);
            return ((Number) horizontal.invoke(member)).intValue();
        }
        Integer widest = null;
        for (Field component : member.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(component.getModifiers())
                    || !Number.class.isAssignableFrom(boxedType(component.getType()))) {
                continue;
            }
            component.setAccessible(true);
            Object componentValue = component.get(member);
            if (componentValue instanceof Number n && (widest == null || n.intValue() > widest)) {
                widest = n.intValue();
            }
        }
        if (widest != null) {
            return widest;
        }
        throw new NoSuchFieldException(memberName + " on " + owner.getClass().getName()
                + " is a " + member.getClass().getName() + " with no integral component");
    }

    private static Class<?> boxedType(Class<?> type) {
        return type == int.class ? Integer.class : type;
    }

    private static int readInt(Object o, String method) throws Exception {
        Method m = o.getClass().getMethod(method);
        m.setAccessible(true);
        return ((Number) m.invoke(o)).intValue();
    }

    private static Object staticField(String className, String fieldName) throws Exception {
        Class<?> c = Class.forName(className);
        Field f = c.getField(fieldName);
        return f.get(null);
    }

    private static Object invoke(Object target, String method) throws Exception {
        Method m = findMethod(target.getClass(), method);
        if (m == null) {
            throw new NoSuchMethodException(method + " on " + target.getClass().getName());
        }
        m.setAccessible(true);
        return m.invoke(target);
    }

    private static Method findMethod(Class<?> type, String name) {
        Class<?> c = type;
        while (c != null) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == 0) {
                    return m;
                }
            }
            c = c.getSuperclass();
        }
        for (Method m : type.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == 0) {
                return m;
            }
        }
        return null;
    }

    private static Method findMethod4(Class<?> type, String name) {
        return findMethod(type, name, 4);
    }

    private static Method findMethod(Class<?> type, String name, int parameterCount) {
        Class<?> c = type;
        while (c != null) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == parameterCount) {
                    return m;
                }
            }
            c = c.getSuperclass();
        }
        for (Method m : type.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == parameterCount) {
                return m;
            }
        }
        return null;
    }

    private static Object freshRandomSource(java.util.Random random) throws Exception {
        Class<?> randomSource = Class.forName("net.minecraft.util.RandomSource");
        Method create = randomSource.getMethod("create", long.class);
        return create.invoke(null, random.nextLong());
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
            Object physicalElement,
            String templateLocation,
            int listLevels,
            int omittedElements
    ) {
    }
}
