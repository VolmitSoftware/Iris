package art.arcane.iris.core.runtime.jigsaw;

import art.arcane.iris.core.structure.authoring.StructureKey;
import art.arcane.iris.core.structure.authoring.StructureHash;
import art.arcane.iris.core.structure.authoring.StructureOwnershipManifest;
import art.arcane.iris.core.structure.authoring.StructureResourceBundle;
import art.arcane.iris.core.structure.authoring.StructureTransactionWriter;
import art.arcane.iris.core.structure.authoring.StructureWriteOptions;
import art.arcane.iris.core.structure.authoring.StructureWriteResult;
import art.arcane.iris.engine.framework.structure.PlanarJigsawWorkcellResolver;
import art.arcane.iris.engine.framework.structure.StructureResourceBundleGraphCompiler;
import art.arcane.iris.engine.object.structure.jigsaw.JigsawDirection;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawConnector;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawMode;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawPool;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawWorkcellArchetype;
import art.arcane.iris.engine.object.structure.IrisStructure;
import art.arcane.iris.engine.object.structure.jigsaw.JigsawJoint;
import art.arcane.volmlib.util.math.IrisBlockVector;
import com.volmit.block.BlockState;
import com.volmit.geometry.BlockPosition;
import com.volmit.object.BlockEntityData;
import com.volmit.tooling.ObjectCodec;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class JigsawStudioGraphEditor {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private JigsawStudioGraphEditor() {
    }

    public static StructureWriteResult createPiece(
            Path packRoot,
            ObjectCodec codec,
            String structureKey,
            String poolKey,
            String pieceKey,
            int weight,
            JigsawStudioCellDimensions dimensions,
            JigsawPlanarTopology topology
    ) throws IOException {
        if (weight < 1) {
            throw new IllegalArgumentException("Jigsaw piece weight must be positive");
        }
        String normalizedPiece = JigsawStudioProjectCreator.Options.requireResourceKey(pieceKey);
        String normalizedPool = JigsawStudioProjectCreator.Options.requireResourceKey(poolKey);
        OwnedGraph graph = loadOwnedGraph(packRoot, structureKey);
        String poolResource = "jigsaw-pools/" + normalizedPool + ".json";
        if (!graph.manifest().resourceHashes().containsKey(poolResource)) {
            throw new IOException("The target pool is not owned by this jigsaw project: " + poolKey);
        }
        String pieceResource = "jigsaw-pieces/" + normalizedPiece + ".json";
        String objectResource = "objects/" + normalizedPiece + ".iob";
        if (graph.manifest().resourceHashes().containsKey(pieceResource)
                || graph.manifest().resourceHashes().containsKey(objectResource)) {
            throw new IOException("The jigsaw project already owns piece or object '" + normalizedPiece + "'");
        }

        StructureResourceBundle.Builder bundle = graph.bundleBuilder();
        for (Map.Entry<String, String> resource : graph.manifest().resourceHashes().entrySet()) {
            Path resourcePath = resolveOwnedResource(graph.root(), resource.getKey());
            byte[] content = Files.readAllBytes(resourcePath);
            if (resource.getKey().equals(poolResource)) {
                content = addPoolEntry(content, normalizedPiece, weight, resourcePath);
            }
            bundle.resource(resource.getKey(), content);
        }
        IrisJigsawPiece piece = new IrisJigsawPiece()
                .setObject(normalizedPiece)
                .setRotatable(true);
        if (topology != null) {
            BlockPosition cellSize = new BlockPosition(
                    dimensions.width(), dimensions.height(), dimensions.depth());
            for (JigsawPlanarDirection planarDirection : topology.directions()) {
                JigsawDirection direction = planarDirection.direction();
                piece.getConnectors().add(new IrisJigsawConnector()
                        .setPosition(IrisJigsawConnector.canonicalPlanarPosition(cellSize, direction))
                        .setDirection(direction)
                        .setTop(JigsawDirection.UP_POSITIVE_Y)
                        .setPool(normalizedPool)
                        .setName("iris:planar")
                        .setTargetName("iris:planar")
                        .setJoint(JigsawJoint.ALIGNED)
                        .setFinalState("minecraft:structure_void"));
            }
        }
        JigsawStudioObject object = JigsawStudioObject.empty(dimensions.width(), dimensions.height(), dimensions.depth());
        bundle.textResource(pieceResource, GSON.toJson(piece) + "\n");
        bundle.resource(objectResource, codec.encode(object.toGenerated()));
        return write(graph, bundle.build());
    }

    public static StructureWriteResult duplicatePiece(
            Path packRoot,
            ObjectCodec codec,
            String structureKey,
            String sourcePieceKey,
            String targetPieceKey
    ) throws IOException {
        return createVariantFromPiece(
                packRoot,
                codec,
                structureKey,
                sourcePieceKey,
                targetPieceKey,
                VariantObjectMode.COPY_SOURCE);
    }

    public static StructureWriteResult createBlankVariant(
            Path packRoot,
            ObjectCodec codec,
            String structureKey,
            String sourcePieceKey,
            String targetPieceKey
    ) throws IOException {
        return createVariantFromPiece(
                packRoot,
                codec,
                structureKey,
                sourcePieceKey,
                targetPieceKey,
                VariantObjectMode.EMPTY_SOURCE_SIZE);
    }

    public static VariantFamilyCreation duplicateActiveFamily(
            Path packRoot,
            String structureKey,
            Map<String, String> sourcePieceKeysByWorkcell,
            String newThemeKey
    ) throws IOException {
        Map<String, String> requestedSources = Map.copyOf(Objects.requireNonNull(
                sourcePieceKeysByWorkcell,
                "Jigsaw Studio theme-set source pieces"));
        String themeKey = JigsawStudioProjectCreator.Options.requireResourceKey(newThemeKey);
        OwnedGraph graph = loadOwnedGraph(packRoot, structureKey);
        String structureResource = "structures/" + graph.manifest().structure().path() + ".json";
        if (!graph.manifest().resourceHashes().containsKey(structureResource)) {
            throw new IOException("The owned graph manifest does not include " + structureResource);
        }
        IrisStructure structure = readStructure(
                resolveOwnedResource(graph.root(), structureResource),
                structureResource);
        Map<String, JigsawPlanarArchetype> expectedSources = expectedThemeSetSources(
                structure,
                requestedSources.keySet());
        requireExactThemeSetSources(requestedSources, expectedSources.keySet());

        Map<String, byte[]> resources = readOwnedResources(graph);
        resources.put(
                structureResource,
                appendThemeSet(resources.get(structureResource), themeKey, structureResource));
        Map<String, String> newPieceKeysByWorkcell = new LinkedHashMap<>();
        Map<String, String> newPieceKeysBySource = new LinkedHashMap<>();
        for (Map.Entry<String, JigsawPlanarArchetype> expected : expectedSources.entrySet()) {
            String stableId = expected.getKey();
            String sourcePieceKey = JigsawStudioProjectCreator.Options.requireResourceKey(
                    requestedSources.get(stableId));
            if (newPieceKeysBySource.containsKey(sourcePieceKey)) {
                throw new IOException("Theme-set source piece '" + sourcePieceKey
                        + "' was selected for more than one workcell");
            }
            String sourcePieceResource = "jigsaw-pieces/" + sourcePieceKey + ".json";
            if (!graph.manifest().resourceHashes().containsKey(sourcePieceResource)) {
                throw new IOException("Theme-set source piece '" + sourcePieceKey
                        + "' is not owned by this jigsaw project");
            }
            IrisJigsawPiece sourcePiece = readPiece(
                    resolveOwnedResource(graph.root(), sourcePieceResource),
                    sourcePieceResource);
            JigsawPlanarArchetype expectedArchetype = expected.getValue();
            if (expectedArchetype != null
                    && IrisJigsawWorkcellArchetype.fromPiece(sourcePiece)
                    != expectedArchetype.modelArchetype()) {
                throw new IOException("Theme-set source piece '" + sourcePieceKey
                        + "' does not belong to " + stableId);
            }
            if (sourcePiece.getObject() == null || sourcePiece.getObject().isBlank()) {
                throw new IOException("Theme-set source piece '" + sourcePieceKey
                        + "' does not declare an object");
            }
            String sourceObjectKey = JigsawStudioProjectCreator.Options.requireResourceKey(sourcePiece.getObject());
            String sourceObjectResource = "objects/" + sourceObjectKey + ".iob";
            if (!graph.manifest().resourceHashes().containsKey(sourceObjectResource)) {
                throw new IOException("Theme-set source object '" + sourceObjectKey
                        + "' is not owned by this jigsaw project");
            }
            String variantFolder = expectedArchetype == null
                    ? expectedSources.size() == 1
                    ? "spatial"
                    : "spatial/" + sourcePieceKey
                    : expectedArchetype.name().toLowerCase(Locale.ROOT);
            String targetPieceKey = graph.manifest().structure().path()
                    + "/variants/" + variantFolder + "/" + themeKey;
            String targetPieceResource = "jigsaw-pieces/" + targetPieceKey + ".json";
            String targetObjectResource = "objects/" + targetPieceKey + ".iob";
            requireAvailableThemeSetTarget(graph, targetPieceResource);
            requireAvailableThemeSetTarget(graph, targetObjectResource);
            resources.put(
                    targetPieceResource,
                    duplicatePieceForTheme(
                            resources.get(sourcePieceResource),
                            targetPieceKey,
                            themeKey,
                            sourcePieceResource));
            resources.put(targetObjectResource, resources.get(sourceObjectResource).clone());
            newPieceKeysByWorkcell.put(stableId, targetPieceKey);
            newPieceKeysBySource.put(sourcePieceKey, targetPieceKey);
        }

        for (Map.Entry<String, byte[]> resource : new ArrayList<>(resources.entrySet())) {
            String relativePath = resource.getKey();
            if (!relativePath.startsWith("jigsaw-pools/") || !relativePath.endsWith(".json")) {
                continue;
            }
            resources.put(
                    relativePath,
                    duplicatePoolMemberships(
                            resource.getValue(),
                            newPieceKeysBySource,
                            relativePath).content());
        }

        StructureResourceBundle.Builder bundle = graph.bundleBuilder();
        for (Map.Entry<String, byte[]> resource : resources.entrySet()) {
            bundle.resource(resource.getKey(), resource.getValue());
        }
        StructureWriteResult result = write(graph, bundle.build());
        return new VariantFamilyCreation(newPieceKeysByWorkcell, result);
    }

    public static String nextVariantKey(
            Path packRoot,
            String structureKey,
            String archetypeKey
    ) throws IOException {
        String normalizedStructure = JigsawStudioProjectCreator.Options.requireResourceKey(structureKey);
        String normalizedArchetype = JigsawStudioProjectCreator.Options.requireResourceKey(archetypeKey);
        OwnedGraph graph = loadOwnedGraph(packRoot, normalizedStructure);
        String prefix = normalizedStructure + "/variants/" + normalizedArchetype + "/variant-";
        for (int index = 1; index <= 100_000; index++) {
            String candidate = prefix + index;
            if (!graph.manifest().resourceHashes().containsKey("jigsaw-pieces/" + candidate + ".json")
                    && !graph.manifest().resourceHashes().containsKey("objects/" + candidate + ".iob")) {
                return candidate;
            }
        }
        throw new IOException("No deterministic variant key remains below " + prefix);
    }

    public static List<String> ownedPoolKeys(Path packRoot, String structureKey) throws IOException {
        OwnedGraph graph = loadOwnedGraph(packRoot, structureKey);
        List<String> pools = new ArrayList<>();
        for (String relativePath : graph.manifest().resourceHashes().keySet()) {
            if (!relativePath.startsWith("jigsaw-pools/") || !relativePath.endsWith(".json")) {
                continue;
            }
            pools.add(relativePath.substring("jigsaw-pools/".length(), relativePath.length() - ".json".length()));
        }
        pools.sort(Comparator.naturalOrder());
        return List.copyOf(pools);
    }

    public static boolean ownsPiece(
            Path packRoot,
            String structureKey,
            String pieceKey,
            String objectKey
    ) throws IOException {
        OwnedGraph graph = loadOwnedGraph(packRoot, structureKey);
        String normalizedPiece = JigsawStudioProjectCreator.Options.requireResourceKey(pieceKey);
        String normalizedObject = JigsawStudioProjectCreator.Options.requireResourceKey(objectKey);
        return graph.manifest().resourceHashes().containsKey("jigsaw-pieces/" + normalizedPiece + ".json")
                && graph.manifest().resourceHashes().containsKey("objects/" + normalizedObject + ".iob");
    }

    public static StructureWriteResult createPool(
            Path packRoot,
            String structureKey,
            String poolKey,
            String fallbackPoolKey
    ) throws IOException {
        String normalizedPool = JigsawStudioProjectCreator.Options.requireResourceKey(poolKey);
        String fallback = fallbackPoolKey == null ? "" : fallbackPoolKey.trim();
        OwnedGraph graph = loadOwnedGraph(packRoot, structureKey);
        String poolResource = "jigsaw-pools/" + normalizedPool + ".json";
        if (graph.manifest().resourceHashes().containsKey(poolResource)) {
            throw new IOException("The jigsaw project already owns pool '" + normalizedPool + "'");
        }
        if (!fallback.isBlank()) {
            String fallbackResource = "jigsaw-pools/"
                    + JigsawStudioProjectCreator.Options.requireResourceKey(fallback) + ".json";
            if (!graph.manifest().resourceHashes().containsKey(fallbackResource)) {
                throw new IOException("Fallback pool '" + fallback + "' is not owned by this jigsaw project");
            }
        }

        StructureResourceBundle.Builder bundle = graph.bundleBuilder();
        for (String relativePath : graph.manifest().resourceHashes().keySet()) {
            bundle.resource(relativePath, Files.readAllBytes(resolveOwnedResource(graph.root(), relativePath)));
        }
        IrisJigsawPool pool = new IrisJigsawPool().setFallback(fallback);
        bundle.textResource(poolResource, GSON.toJson(pool) + "\n");
        return write(graph, bundle.build());
    }

    public static StructureWriteResult updateRotatable(
            Path packRoot,
            String structureKey,
            String pieceKey,
            boolean rotatable
    ) throws IOException {
        String normalizedPiece = JigsawStudioProjectCreator.Options.requireResourceKey(pieceKey);
        OwnedGraph graph = loadOwnedGraph(packRoot, structureKey);
        String pieceResource = "jigsaw-pieces/" + normalizedPiece + ".json";
        if (!graph.manifest().resourceHashes().containsKey(pieceResource)) {
            throw new IOException("Piece '" + normalizedPiece + "' is not owned by this jigsaw project");
        }
        StructureResourceBundle.Builder bundle = graph.bundleBuilder();
        for (String relativePath : graph.manifest().resourceHashes().keySet()) {
            byte[] content = Files.readAllBytes(resolveOwnedResource(graph.root(), relativePath));
            if (relativePath.equals(pieceResource)) {
                JsonElement parsed = JsonParser.parseString(new String(content, StandardCharsets.UTF_8));
                if (!parsed.isJsonObject()) {
                    throw new IOException("Jigsaw piece is not a JSON object: " + pieceResource);
                }
                JsonObject piece = parsed.getAsJsonObject();
                piece.addProperty("rotatable", rotatable);
                content = (GSON.toJson(piece) + "\n").getBytes(StandardCharsets.UTF_8);
            }
            bundle.resource(relativePath, content);
        }
        return write(graph, bundle.build());
    }

    public static StructureWriteResult updatePieceThemes(
            Path packRoot,
            String structureKey,
            String pieceKey,
            List<String> themes
    ) throws IOException {
        List<String> normalizedThemes = normalizeThemes(themes);
        return updateOwnedPiece(
                packRoot,
                structureKey,
                pieceKey,
                (piece, pieceResource) -> {
                    JsonArray values = new JsonArray();
                    for (String theme : normalizedThemes) {
                        values.add(theme);
                    }
                    piece.add("themes", values);
                });
    }

    public static StructureWriteResult updatePieceDisplayName(
            Path packRoot,
            String structureKey,
            String pieceKey,
            String displayName
    ) throws IOException {
        String normalizedName = normalizeDisplayName(displayName);
        return updateOwnedPiece(
                packRoot,
                structureKey,
                pieceKey,
                (piece, pieceResource) -> {
                    if (normalizedName.isEmpty()) {
                        piece.remove("displayName");
                    } else {
                        piece.addProperty("displayName", normalizedName);
                    }
                });
    }

    public static StructureWriteResult updatePieceRules(
            Path packRoot,
            String structureKey,
            String pieceKey,
            JigsawStudioPieceRules rules
    ) throws IOException {
        JigsawStudioPieceRules normalizedRules = Objects.requireNonNull(
                rules,
                "Jigsaw Studio piece rules");
        return updateOwnedPiece(
                packRoot,
                structureKey,
                pieceKey,
                (piece, pieceResource) -> {
                    JsonObject values = new JsonObject();
                    values.addProperty("minimumDepth", normalizedRules.minimumDepth());
                    values.addProperty("maximumDepth", normalizedRules.maximumDepth());
                    values.addProperty("minimumPlacements", normalizedRules.minimumPlacements());
                    values.addProperty("maximumPlacements", normalizedRules.maximumPlacements());
                    values.addProperty("terminal", normalizedRules.terminal());
                    piece.add("rules", values);
                });
    }

    public static PieceDeletionResult deletePieceVariant(
            Path packRoot,
            String structureKey,
            String pieceKey
    ) throws IOException {
        String normalizedPiece = JigsawStudioProjectCreator.Options.requireResourceKey(pieceKey);
        OwnedGraph graph = loadOwnedGraph(packRoot, structureKey);
        String pieceResource = "jigsaw-pieces/" + normalizedPiece + ".json";
        if (!graph.manifest().resourceHashes().containsKey(pieceResource)) {
            throw new IOException("Piece '" + normalizedPiece + "' is not owned by this jigsaw project");
        }
        IrisJigsawPiece targetPiece = readPiece(
                resolveOwnedResource(graph.root(), pieceResource),
                pieceResource);
        if (targetPiece.getObject() == null || targetPiece.getObject().isBlank()) {
            throw new IOException("Piece '" + normalizedPiece + "' does not declare an object");
        }
        String objectKey = JigsawStudioProjectCreator.Options.requireResourceKey(targetPiece.getObject());
        String objectResource = "objects/" + objectKey + ".iob";
        boolean removeObject = graph.manifest().resourceHashes().containsKey(objectResource)
                && !otherOwnedPieceReferencesObject(graph, normalizedPiece, objectKey);
        requireDeletableArchetype(graph, normalizedPiece, targetPiece);

        StructureResourceBundle.Builder bundle = graph.bundleBuilder();
        int removedMemberships = 0;
        int changedPools = 0;
        for (String relativePath : graph.manifest().resourceHashes().keySet()) {
            if (relativePath.equals(pieceResource) || removeObject && relativePath.equals(objectResource)) {
                continue;
            }
            byte[] content = Files.readAllBytes(resolveOwnedResource(graph.root(), relativePath));
            if (relativePath.startsWith("jigsaw-pools/") && relativePath.endsWith(".json")) {
                PoolEntryRemoval removal = removePoolEntries(content, normalizedPiece, relativePath);
                content = removal.content();
                removedMemberships += removal.removedEntries();
                if (removal.removedEntries() > 0) {
                    changedPools++;
                }
            }
            bundle.resource(relativePath, content);
        }
        StructureResourceBundle updatedBundle = bundle.build();
        try {
            StructureResourceBundleGraphCompiler.requireViable(updatedBundle);
        } catch (RuntimeException exception) {
            throw new IOException("Deleting piece '" + normalizedPiece
                    + "' would make the jigsaw graph non-viable: " + failureMessage(exception), exception);
        }
        StructureWriteResult result = writeCompiled(graph, updatedBundle);
        return new PieceDeletionResult(
                result,
                removedMemberships,
                changedPools,
                1,
                removeObject ? 1 : 0);
    }

    public static VariantResizeResult resizePieceObject(
            Path packRoot,
            ObjectCodec codec,
            String structureKey,
            String pieceKey,
            JigsawStudioCellDimensions dimensions
    ) throws IOException {
        String normalizedPiece = JigsawStudioProjectCreator.Options.requireResourceKey(pieceKey);
        JigsawStudioCellDimensions targetDimensions = Objects.requireNonNull(
                dimensions,
                "Jigsaw Studio variant dimensions");
        OwnedGraph graph = loadOwnedGraph(packRoot, structureKey);
        String pieceResource = "jigsaw-pieces/" + normalizedPiece + ".json";
        if (!graph.manifest().resourceHashes().containsKey(pieceResource)) {
            throw new IOException("Piece '" + normalizedPiece + "' is not owned by this jigsaw project");
        }
        IrisJigsawPiece piece = readPiece(resolveOwnedResource(graph.root(), pieceResource), pieceResource);
        if (piece.getObject() == null || piece.getObject().isBlank()) {
            throw new IOException("Piece '" + normalizedPiece + "' does not declare an object");
        }
        String objectKey = JigsawStudioProjectCreator.Options.requireResourceKey(piece.getObject());
        String objectResource = "objects/" + objectKey + ".iob";
        if (!graph.manifest().resourceHashes().containsKey(objectResource)) {
            throw new IOException("Object '" + objectKey + "' is not owned by this jigsaw project");
        }
        requireExclusiveObjectReference(graph, normalizedPiece, objectKey);
        Path objectPath = resolveOwnedResource(graph.root(), objectResource);
        JigsawStudioObject source = JigsawStudioObject.of(codec.decode(Files.readAllBytes(objectPath)));
        String structureResource = "structures/"
                + JigsawStudioProjectCreator.Options.requireResourceKey(structureKey) + ".json";
        IrisStructure structure = readStructure(
                resolveOwnedResource(graph.root(), structureResource),
                structureResource);
        JigsawStudioObject resizedObject;
        JigsawStudioCellDimensions sourceDimensions = new JigsawStudioCellDimensions(
                source.width(),
                source.height(),
                source.depth());
        JigsawStudioCellDimensions previousDimensions;
        int relocatedConnectors = 0;
        if (structure.resolvedMode() == IrisJigsawMode.PLANAR_JIGSAW) {
            IrisJigsawWorkcellArchetype archetype = IrisJigsawWorkcellArchetype.fromPiece(piece);
            JigsawPlanarArchetype planarArchetype = JigsawPlanarArchetype.fromModel(archetype);
            int quarterTurns = archetype.sourceToCanonicalQuarterTurns(piece);
            previousDimensions = canonicalDimensions(sourceDimensions, quarterTurns);
            PlanarJigsawWorkcellResolver.ResolvedWorkcell workcell =
                    PlanarJigsawWorkcellResolver.resolve(structure).get(archetype);
            if (workcell == null || !workcell.contains(dimensionsPosition(targetDimensions))) {
                throw new IOException("Variant '" + normalizedPiece + "' size "
                        + describeDimensions(targetDimensions) + " exceeds the "
                        + planarArchetype.displayName() + " workcell capacity "
                        + describeDimensions(new JigsawStudioCellDimensions(
                        workcell == null ? 1 : workcell.width(),
                        workcell == null ? 1 : workcell.height(),
                        workcell == null ? 1 : workcell.depth()))
                        + "; increase that workcell capacity first");
            }
            PlanarPieceObjectResize resized = resizePlanarPieceObject(
                    source,
                    piece,
                    planarArchetype,
                    targetDimensions,
                    normalizedPiece);
            resizedObject = resized.object();
            relocatedConnectors = resized.relocatedConnectors();
        } else {
            previousDimensions = sourceDimensions;
            requireConnectorsInside(piece, targetDimensions, normalizedPiece);
            resizedObject = resizeObject(source, targetDimensions, normalizedPiece);
        }

        StructureResourceBundle.Builder bundle = graph.bundleBuilder();
        for (String relativePath : graph.manifest().resourceHashes().keySet()) {
            byte[] content;
            if (relativePath.equals(objectResource)) {
                content = codec.encode(resizedObject.toGenerated());
            } else if (relativePath.equals(pieceResource)) {
                content = (GSON.toJson(piece) + "\n").getBytes(StandardCharsets.UTF_8);
            } else {
                content = Files.readAllBytes(resolveOwnedResource(graph.root(), relativePath));
            }
            bundle.resource(relativePath, content);
        }
        StructureWriteResult writeResult = write(graph, bundle.build());
        return new VariantResizeResult(
                writeResult,
                previousDimensions,
                targetDimensions,
                relocatedConnectors);
    }

    public static WorkcellCapacityResult updatePlanarWorkcellCapacity(
            Path packRoot,
            String structureKey,
            JigsawPlanarArchetype archetype,
            JigsawStudioCellDimensions dimensions
    ) throws IOException {
        JigsawPlanarArchetype targetArchetype = Objects.requireNonNull(
                archetype,
                "Planar Jigsaw Studio archetype");
        JigsawStudioCellDimensions targetDimensions = Objects.requireNonNull(
                dimensions,
                "Planar Jigsaw Studio workcell dimensions");
        if (targetDimensions.width() < 3 || targetDimensions.depth() < 3) {
            throw new IllegalArgumentException(
                    "Planar Jigsaw Studio workcell width and depth must each be at least 3 blocks");
        }
        OwnedGraph graph = loadOwnedGraph(packRoot, structureKey);
        String structureResource = "structures/" + graph.manifest().structure().path() + ".json";
        if (!graph.manifest().resourceHashes().containsKey(structureResource)) {
            throw new IOException("The owned graph manifest does not include " + structureResource);
        }
        IrisStructure structure = readStructure(
                resolveOwnedResource(graph.root(), structureResource),
                structureResource);
        if (structure.resolvedMode() != IrisJigsawMode.PLANAR_JIGSAW) {
            throw new IOException("Workcell resizing requires a planar Jigsaw Studio structure");
        }

        Map<String, byte[]> resources = readOwnedResources(graph);
        int checkedVariants = 0;
        for (String relativePath : graph.manifest().resourceHashes().keySet()) {
            if (!relativePath.startsWith("jigsaw-pieces/") || !relativePath.endsWith(".json")) {
                continue;
            }
            IrisJigsawPiece piece = readPiece(resolveOwnedResource(graph.root(), relativePath), relativePath);
            if (IrisJigsawWorkcellArchetype.fromPiece(piece) != targetArchetype.modelArchetype()) {
                continue;
            }
            checkedVariants++;
        }

        Path structurePath = resolveOwnedResource(graph.root(), structureResource);
        resources.put(
                structureResource,
                JigsawStudioStructureEditor.updateWorkcell(
                        resources.get(structureResource),
                        targetArchetype,
                        targetDimensions,
                        null,
                        null,
                        structurePath));
        StructureResourceBundle.Builder bundle = graph.bundleBuilder();
        for (Map.Entry<String, byte[]> resource : resources.entrySet()) {
            bundle.resource(resource.getKey(), resource.getValue());
        }
        StructureWriteResult writeResult;
        try {
            writeResult = write(graph, bundle.build());
        } catch (RuntimeException exception) {
            throw new IOException("Workcell capacity " + describeDimensions(targetDimensions)
                    + " cannot contain every " + targetArchetype.displayName()
                    + " variant: " + failureMessage(exception), exception);
        }
        return new WorkcellCapacityResult(writeResult, checkedVariants);
    }

    public static StructureWriteResult updateConnectorChannel(
            Path packRoot,
            String structureKey,
            String pieceKey,
            BlockPosition position,
            String channel
    ) throws IOException {
        String normalizedPiece = JigsawStudioProjectCreator.Options.requireResourceKey(pieceKey);
        BlockPosition connectorPosition = Objects.requireNonNull(position, "Jigsaw connector position");
        String normalizedChannel = normalizeChannel(channel);
        OwnedGraph graph = loadOwnedGraph(packRoot, structureKey);
        String pieceResource = "jigsaw-pieces/" + normalizedPiece + ".json";
        if (!graph.manifest().resourceHashes().containsKey(pieceResource)) {
            throw new IOException("Piece '" + normalizedPiece + "' is not owned by this jigsaw project");
        }
        StructureResourceBundle.Builder bundle = graph.bundleBuilder();
        boolean found = false;
        for (String relativePath : graph.manifest().resourceHashes().keySet()) {
            byte[] content = Files.readAllBytes(resolveOwnedResource(graph.root(), relativePath));
            if (relativePath.equals(pieceResource)) {
                IrisJigsawPiece piece;
                try {
                    piece = GSON.fromJson(new String(content, StandardCharsets.UTF_8), IrisJigsawPiece.class);
                } catch (RuntimeException exception) {
                    throw new IOException("Jigsaw piece is not valid JSON: " + pieceResource, exception);
                }
                if (piece == null) {
                    throw new IOException("Jigsaw piece is empty: " + pieceResource);
                }
                for (IrisJigsawConnector connector : piece.getConnectors()) {
                    if (connectorPosition.equals(connector.getPosition())) {
                        connector.setChannel(normalizedChannel);
                        found = true;
                    }
                }
                content = (GSON.toJson(piece) + "\n").getBytes(StandardCharsets.UTF_8);
            }
            bundle.resource(relativePath, content);
        }
        if (!found) {
            throw new IOException("Piece '" + normalizedPiece + "' has no connector at "
                    + connectorPosition.x() + "," + connectorPosition.y() + ","
                    + connectorPosition.z());
        }
        return write(graph, bundle.build());
    }

    private static OwnedGraph loadOwnedGraph(Path packRoot, String structureKey) throws IOException {
        Path root = Objects.requireNonNull(packRoot, "Jigsaw Studio pack root")
                .toAbsolutePath().normalize();
        StructureKey ownershipKey = StructureKey.parse(structureKey, "iris");
        StructureTransactionWriter writer = new StructureTransactionWriter(root);
        Path manifestPath = writer.ownershipManifestPath(ownershipKey);
        if (!Files.isRegularFile(manifestPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("This graph is read-only because it is not Studio-owned; create a new Jigsaw Studio project before editing pieces");
        }
        byte[] manifestContent = Files.readAllBytes(manifestPath);
        StructureOwnershipManifest manifest = JigsawStudioAuthoringAccess.requireEditable(
                StructureOwnershipManifest.fromJson(manifestContent));
        return new OwnedGraph(root, writer, manifest, StructureHash.sha256(manifestContent));
    }

    private static Map<String, JigsawPlanarArchetype> expectedThemeSetSources(
            IrisStructure structure,
            Set<String> requestedStableIds
    ) throws IOException {
        Map<String, JigsawPlanarArchetype> expected = new LinkedHashMap<>();
        if (structure.resolvedMode() == IrisJigsawMode.SPATIAL_JIGSAW) {
            if (requestedStableIds.isEmpty()) {
                throw new IOException("A spatial theme set requires at least one workcell source");
            }
            List<String> stableIds = new ArrayList<>(requestedStableIds);
            stableIds.sort(Comparator.naturalOrder());
            for (String stableId : stableIds) {
                if (!stableId.equals(JigsawStudioLayout.SPATIAL_WORKCELL_ID)
                        && !stableId.startsWith(JigsawStudioLayout.SPATIAL_WORKCELL_ID + "/")) {
                    throw new IOException("Invalid spatial workcell source '" + stableId + "'");
                }
                expected.put(stableId, null);
            }
            return expected;
        }
        Map<IrisJigsawWorkcellArchetype, PlanarJigsawWorkcellResolver.ResolvedWorkcell> workcells;
        try {
            workcells = PlanarJigsawWorkcellResolver.resolve(structure);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Planar workcell configuration is invalid: "
                    + exception.getMessage(), exception);
        }
        for (JigsawPlanarArchetype archetype : JigsawPlanarArchetype.values()) {
            PlanarJigsawWorkcellResolver.ResolvedWorkcell workcell = workcells.get(archetype.modelArchetype());
            if (workcell != null && workcell.enabled()) {
                expected.put(archetype.stableId(), archetype);
            }
        }
        if (expected.isEmpty()) {
            throw new IOException("A planar theme set requires at least one enabled workcell");
        }
        return expected;
    }

    private static void requireExactThemeSetSources(
            Map<String, String> requestedSources,
            Set<String> expectedStableIds
    ) throws IOException {
        Set<String> missing = new LinkedHashSet<>(expectedStableIds);
        missing.removeAll(requestedSources.keySet());
        Set<String> unexpected = new LinkedHashSet<>(requestedSources.keySet());
        unexpected.removeAll(expectedStableIds);
        if (!missing.isEmpty() || !unexpected.isEmpty()) {
            throw new IOException("Theme-set sources must match enabled workcells exactly; missing="
                    + missing + ", unexpected=" + unexpected);
        }
    }

    private static Map<String, byte[]> readOwnedResources(OwnedGraph graph) throws IOException {
        Map<String, byte[]> resources = new LinkedHashMap<>();
        for (String relativePath : graph.manifest().resourceHashes().keySet()) {
            resources.put(
                    relativePath,
                    Files.readAllBytes(resolveOwnedResource(graph.root(), relativePath)));
        }
        return resources;
    }

    private static byte[] appendThemeSet(
            byte[] content,
            String themeKey,
            String structureResource
    ) throws IOException {
        JsonElement parsed = JsonParser.parseString(new String(content, StandardCharsets.UTF_8));
        if (!parsed.isJsonObject()) {
            throw new IOException("Jigsaw structure is not a JSON object: " + structureResource);
        }
        JsonObject structure = parsed.getAsJsonObject();
        JsonArray themeSets;
        if (!structure.has("themeSets")) {
            themeSets = new JsonArray();
            structure.add("themeSets", themeSets);
        } else if (!structure.get("themeSets").isJsonArray()) {
            throw new IOException("Jigsaw structure has an invalid themeSets value: " + structureResource);
        } else {
            themeSets = structure.getAsJsonArray("themeSets");
        }
        for (JsonElement element : themeSets) {
            if (element.isJsonObject()
                    && element.getAsJsonObject().has("key")
                    && themeKey.equals(element.getAsJsonObject().get("key").getAsString())) {
                throw new IOException("Jigsaw structure already declares theme '" + themeKey + "'");
            }
        }
        JsonObject theme = new JsonObject();
        theme.addProperty("key", themeKey);
        theme.addProperty("weight", 1);
        themeSets.add(theme);
        return (GSON.toJson(structure) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private static StructureWriteResult createVariantFromPiece(
            Path packRoot,
            ObjectCodec codec,
            String structureKey,
            String sourcePieceKey,
            String targetPieceKey,
            VariantObjectMode objectMode
    ) throws IOException {
        String normalizedSource = JigsawStudioProjectCreator.Options.requireResourceKey(sourcePieceKey);
        String normalizedTarget = JigsawStudioProjectCreator.Options.requireResourceKey(targetPieceKey);
        OwnedGraph graph = loadOwnedGraph(packRoot, structureKey);
        String sourcePieceResource = "jigsaw-pieces/" + normalizedSource + ".json";
        String targetPieceResource = "jigsaw-pieces/" + normalizedTarget + ".json";
        String targetObjectResource = "objects/" + normalizedTarget + ".iob";
        if (!graph.manifest().resourceHashes().containsKey(sourcePieceResource)) {
            throw new IOException("Source piece '" + normalizedSource + "' is not owned by this project");
        }
        requireAvailableVariantTarget(graph, targetPieceResource);
        requireAvailableVariantTarget(graph, targetObjectResource);

        Path sourcePiecePath = resolveOwnedResource(graph.root(), sourcePieceResource);
        byte[] sourcePieceContent = Files.readAllBytes(sourcePiecePath);
        IrisJigsawPiece sourcePiece = readPiece(sourcePiecePath, sourcePieceResource);
        if (sourcePiece.getObject() == null || sourcePiece.getObject().isBlank()) {
            throw new IOException("Source piece '" + normalizedSource + "' does not declare an object");
        }
        String sourceObjectKey = JigsawStudioProjectCreator.Options.requireResourceKey(sourcePiece.getObject());
        String sourceObjectResource = "objects/" + sourceObjectKey + ".iob";
        if (!graph.manifest().resourceHashes().containsKey(sourceObjectResource)) {
            throw new IOException("Source object '" + sourceObjectKey + "' is not owned by this project");
        }
        Path sourceObjectPath = resolveOwnedResource(graph.root(), sourceObjectResource);
        byte[] targetObjectContent;
        if (objectMode == VariantObjectMode.COPY_SOURCE) {
            targetObjectContent = Files.readAllBytes(sourceObjectPath);
        } else {
            JigsawStudioObject source = JigsawStudioObject.of(codec.decode(Files.readAllBytes(sourceObjectPath)));
            targetObjectContent = codec.encode(
                    JigsawStudioObject.empty(source.width(), source.height(), source.depth()).toGenerated());
        }

        Map<String, String> targetPieceKeysBySource = Map.of(normalizedSource, normalizedTarget);
        StructureResourceBundle.Builder bundle = graph.bundleBuilder();
        int duplicatedMemberships = 0;
        for (String relativePath : graph.manifest().resourceHashes().keySet()) {
            byte[] content = Files.readAllBytes(resolveOwnedResource(graph.root(), relativePath));
            if (relativePath.startsWith("jigsaw-pools/") && relativePath.endsWith(".json")) {
                PoolMembershipDuplication duplication = duplicatePoolMemberships(
                        content,
                        targetPieceKeysBySource,
                        relativePath);
                content = duplication.content();
                duplicatedMemberships += duplication.duplicatedEntries();
            }
            bundle.resource(relativePath, content);
        }
        if (duplicatedMemberships == 0) {
            throw new IOException("Source piece '" + normalizedSource
                    + "' has no owned pool membership to copy; select an owned pool explicitly");
        }
        bundle.resource(
                targetPieceResource,
                duplicatePieceForVariant(sourcePieceContent, normalizedTarget, sourcePieceResource));
        bundle.resource(targetObjectResource, targetObjectContent);
        return write(graph, bundle.build());
    }

    private static void requireAvailableVariantTarget(
            OwnedGraph graph,
            String relativePath
    ) throws IOException {
        StructureResourceBundle.validateRelativePath(relativePath);
        Path target = graph.root().resolve(relativePath).normalize();
        if (!target.startsWith(graph.root())) {
            throw new IOException("Variant target escapes the pack root: " + relativePath);
        }
        if (graph.manifest().resourceHashes().containsKey(relativePath)
                || Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Variant target already exists: " + relativePath);
        }
    }

    private static void requireAvailableThemeSetTarget(
            OwnedGraph graph,
            String relativePath
    ) throws IOException {
        StructureResourceBundle.validateRelativePath(relativePath);
        Path target = graph.root().resolve(relativePath).normalize();
        if (!target.startsWith(graph.root())) {
            throw new IOException("Theme-set target escapes the pack root: " + relativePath);
        }
        if (graph.manifest().resourceHashes().containsKey(relativePath)
                || Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Theme-set target already exists: " + relativePath);
        }
    }

    private static byte[] duplicatePieceForTheme(
            byte[] content,
            String targetPieceKey,
            String themeKey,
            String sourcePieceResource
    ) throws IOException {
        JsonElement parsed = JsonParser.parseString(new String(content, StandardCharsets.UTF_8));
        if (!parsed.isJsonObject()) {
            throw new IOException("Jigsaw piece is not a JSON object: " + sourcePieceResource);
        }
        JsonObject piece = parsed.getAsJsonObject().deepCopy();
        piece.addProperty("object", targetPieceKey);
        JsonArray themes = new JsonArray();
        themes.add(themeKey);
        piece.add("themes", themes);
        return (GSON.toJson(piece) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] duplicatePieceForVariant(
            byte[] content,
            String targetPieceKey,
            String sourcePieceResource
    ) throws IOException {
        JsonElement parsed = JsonParser.parseString(new String(content, StandardCharsets.UTF_8));
        if (!parsed.isJsonObject()) {
            throw new IOException("Jigsaw piece is not a JSON object: " + sourcePieceResource);
        }
        JsonObject piece = parsed.getAsJsonObject().deepCopy();
        piece.addProperty("object", targetPieceKey);
        return (GSON.toJson(piece) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private static PoolMembershipDuplication duplicatePoolMemberships(
            byte[] content,
            Map<String, String> targetPieceKeysBySource,
            String poolResource
    ) throws IOException {
        JsonElement parsed = JsonParser.parseString(new String(content, StandardCharsets.UTF_8));
        if (!parsed.isJsonObject()) {
            throw new IOException("Jigsaw pool is not a JSON object: " + poolResource);
        }
        JsonObject pool = parsed.getAsJsonObject();
        JsonArray pieces = pool.getAsJsonArray("pieces");
        if (pieces == null) {
            throw new IOException("Jigsaw pool does not declare a pieces array: " + poolResource);
        }
        JsonArray expanded = new JsonArray();
        int duplicatedEntries = 0;
        for (JsonElement element : pieces) {
            expanded.add(element.deepCopy());
            if (!element.isJsonObject() || !element.getAsJsonObject().has("piece")) {
                continue;
            }
            String sourcePieceKey = element.getAsJsonObject().get("piece").getAsString();
            String targetPieceKey = targetPieceKeysBySource.get(sourcePieceKey);
            if (targetPieceKey == null) {
                continue;
            }
            JsonObject duplicate = element.getAsJsonObject().deepCopy();
            duplicate.addProperty("piece", targetPieceKey);
            expanded.add(duplicate);
            duplicatedEntries++;
        }
        if (duplicatedEntries == 0) {
            return new PoolMembershipDuplication(content, 0);
        }
        pool.add("pieces", expanded);
        return new PoolMembershipDuplication(
                (GSON.toJson(pool) + "\n").getBytes(StandardCharsets.UTF_8),
                duplicatedEntries);
    }

    private static StructureWriteResult updateOwnedPiece(
            Path packRoot,
            String structureKey,
            String pieceKey,
            PieceContentEditor editor
    ) throws IOException {
        String normalizedPiece = JigsawStudioProjectCreator.Options.requireResourceKey(pieceKey);
        OwnedGraph graph = loadOwnedGraph(packRoot, structureKey);
        String pieceResource = "jigsaw-pieces/" + normalizedPiece + ".json";
        if (!graph.manifest().resourceHashes().containsKey(pieceResource)) {
            throw new IOException("Piece '" + normalizedPiece + "' is not owned by this jigsaw project");
        }
        StructureResourceBundle.Builder bundle = graph.bundleBuilder();
        for (String relativePath : graph.manifest().resourceHashes().keySet()) {
            byte[] content = Files.readAllBytes(resolveOwnedResource(graph.root(), relativePath));
            if (relativePath.equals(pieceResource)) {
                JsonElement parsed = JsonParser.parseString(new String(content, StandardCharsets.UTF_8));
                if (!parsed.isJsonObject()) {
                    throw new IOException("Jigsaw piece is not a JSON object: " + pieceResource);
                }
                JsonObject piece = parsed.getAsJsonObject();
                editor.edit(piece, pieceResource);
                content = (GSON.toJson(piece) + "\n").getBytes(StandardCharsets.UTF_8);
            }
            bundle.resource(relativePath, content);
        }
        return write(graph, bundle.build());
    }

    private static List<String> normalizeThemes(List<String> themes) {
        Objects.requireNonNull(themes, "Jigsaw Studio piece themes");
        Set<String> normalized = new LinkedHashSet<>();
        for (String theme : themes) {
            Objects.requireNonNull(theme, "Jigsaw Studio piece theme");
            String key = theme.trim();
            if (key.isEmpty() || !key.equals(theme)) {
                throw new IllegalArgumentException(
                        "Jigsaw Studio piece themes must be non-blank and whitespace-normalized");
            }
            if (!normalized.add(key)) {
                throw new IllegalArgumentException("Duplicate Jigsaw Studio piece theme '" + key + "'");
            }
        }
        return List.copyOf(normalized);
    }

    public static String normalizeDisplayName(String displayName) {
        String normalized = displayName == null ? "" : displayName.trim();
        if (normalized.codePointCount(0, normalized.length()) > 64) {
            throw new IllegalArgumentException("Jigsaw Studio display names cannot exceed 64 visible characters");
        }
        for (int index = 0; index < normalized.length(); ) {
            int codePoint = normalized.codePointAt(index);
            if (Character.isISOControl(codePoint) || codePoint == '§') {
                throw new IllegalArgumentException(
                        "Jigsaw Studio display names cannot contain control or formatting characters");
            }
            index += Character.charCount(codePoint);
        }
        return normalized;
    }

    private static boolean otherOwnedPieceReferencesObject(
            OwnedGraph graph,
            String targetPieceKey,
            String objectKey
    ) throws IOException {
        for (String relativePath : graph.manifest().resourceHashes().keySet()) {
            if (!relativePath.startsWith("jigsaw-pieces/") || !relativePath.endsWith(".json")) {
                continue;
            }
            String pieceKey = resourceKey(relativePath, "jigsaw-pieces/", ".json");
            if (pieceKey.equals(targetPieceKey)) {
                continue;
            }
            IrisJigsawPiece piece = readPiece(resolveOwnedResource(graph.root(), relativePath), relativePath);
            if (objectKey.equals(piece.getObject())) {
                return true;
            }
        }
        return false;
    }

    private static void requireDeletableArchetype(
            OwnedGraph graph,
            String targetPieceKey,
            IrisJigsawPiece targetPiece
    ) throws IOException {
        String structureResource = "structures/" + graph.manifest().structure().path() + ".json";
        if (!graph.manifest().resourceHashes().containsKey(structureResource)) {
            throw new IOException("The owned graph manifest does not include " + structureResource);
        }
        IrisStructure structure = readStructure(
                resolveOwnedResource(graph.root(), structureResource),
                structureResource);
        if (structure.resolvedMode() == IrisJigsawMode.SPATIAL_JIGSAW) {
            if (countOtherOwnedPieces(graph, targetPieceKey, null) == 0) {
                throw new IOException("Cannot delete the final spatial jigsaw variant");
            }
            return;
        }
        IrisJigsawWorkcellArchetype archetype = IrisJigsawWorkcellArchetype.fromPiece(targetPiece);
        Map<IrisJigsawWorkcellArchetype, PlanarJigsawWorkcellResolver.ResolvedWorkcell> workcells;
        try {
            workcells = PlanarJigsawWorkcellResolver.resolve(structure);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Planar workcell configuration is invalid: "
                    + exception.getMessage(), exception);
        }
        PlanarJigsawWorkcellResolver.ResolvedWorkcell workcell = workcells.get(archetype);
        if (workcell != null && workcell.enabled()
                && countOtherOwnedPieces(graph, targetPieceKey, archetype) == 0) {
            throw new IOException("Cannot delete the final variant for enabled planar workcell "
                    + JigsawPlanarArchetype.fromModel(archetype).stableId());
        }
    }

    private static int countOtherOwnedPieces(
            OwnedGraph graph,
            String targetPieceKey,
            IrisJigsawWorkcellArchetype archetype
    ) throws IOException {
        int count = 0;
        for (String relativePath : graph.manifest().resourceHashes().keySet()) {
            if (!relativePath.startsWith("jigsaw-pieces/") || !relativePath.endsWith(".json")) {
                continue;
            }
            String pieceKey = resourceKey(relativePath, "jigsaw-pieces/", ".json");
            if (pieceKey.equals(targetPieceKey)) {
                continue;
            }
            if (archetype == null) {
                count++;
                continue;
            }
            IrisJigsawPiece piece = readPiece(resolveOwnedResource(graph.root(), relativePath), relativePath);
            if (IrisJigsawWorkcellArchetype.fromPiece(piece) == archetype) {
                count++;
            }
        }
        return count;
    }

    private static PoolEntryRemoval removePoolEntries(
            byte[] content,
            String pieceKey,
            String poolResource
    ) throws IOException {
        JsonElement parsed = JsonParser.parseString(new String(content, StandardCharsets.UTF_8));
        if (!parsed.isJsonObject()) {
            throw new IOException("Jigsaw pool is not a JSON object: " + poolResource);
        }
        JsonObject pool = parsed.getAsJsonObject();
        JsonArray pieces = pool.getAsJsonArray("pieces");
        if (pieces == null) {
            throw new IOException("Jigsaw pool does not declare a pieces array: " + poolResource);
        }
        int removed = 0;
        for (int index = pieces.size() - 1; index >= 0; index--) {
            JsonElement entry = pieces.get(index);
            if (entry.isJsonObject()
                    && entry.getAsJsonObject().has("piece")
                    && pieceKey.equals(entry.getAsJsonObject().get("piece").getAsString())) {
                pieces.remove(index);
                removed++;
            }
        }
        if (removed == 0) {
            return new PoolEntryRemoval(content, 0);
        }
        return new PoolEntryRemoval(
                (GSON.toJson(pool) + "\n").getBytes(StandardCharsets.UTF_8),
                removed);
    }

    private static String resourceKey(String relativePath, String prefix, String suffix) {
        return relativePath.substring(prefix.length(), relativePath.length() - suffix.length());
    }

    private static String failureMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private static Path resolveOwnedResource(Path root, String relativePath) throws IOException {
        StructureResourceBundle.validateRelativePath(relativePath);
        Path resource = root.resolve(relativePath).normalize();
        if (!resource.startsWith(root) || !Files.isRegularFile(resource, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Owned graph resource is missing or unsafe: " + relativePath);
        }
        return resource;
    }

    private static byte[] addPoolEntry(
            byte[] content,
            String pieceKey,
            int weight,
            Path poolPath
    ) throws IOException {
        JsonElement parsed = JsonParser.parseString(new String(content, StandardCharsets.UTF_8));
        if (!parsed.isJsonObject()) {
            throw new IOException("Jigsaw pool is not a JSON object: " + poolPath);
        }
        JsonObject pool = parsed.getAsJsonObject();
        if (!pool.has("pieces") || !pool.get("pieces").isJsonArray()) {
            throw new IOException("Jigsaw pool does not declare a pieces array: " + poolPath);
        }
        JsonArray pieces = pool.getAsJsonArray("pieces");
        for (JsonElement element : pieces) {
            if (element.isJsonObject()
                    && element.getAsJsonObject().has("piece")
                    && pieceKey.equals(element.getAsJsonObject().get("piece").getAsString())) {
                throw new IOException("Pool already references piece '" + pieceKey + "'");
            }
        }
        JsonObject entry = new JsonObject();
        entry.addProperty("piece", pieceKey);
        entry.addProperty("weight", weight);
        pieces.add(entry);
        return (GSON.toJson(pool) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private static IrisJigsawPiece readPiece(Path path, String resource) throws IOException {
        try {
            IrisJigsawPiece piece = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8),
                    IrisJigsawPiece.class);
            if (piece == null) {
                throw new IOException("Jigsaw piece is empty: " + resource);
            }
            return piece;
        } catch (RuntimeException exception) {
            throw new IOException("Jigsaw piece is not valid JSON: " + resource, exception);
        }
    }

    private static IrisStructure readStructure(Path path, String resource) throws IOException {
        try {
            IrisStructure structure = GSON.fromJson(
                    Files.readString(path, StandardCharsets.UTF_8),
                    IrisStructure.class);
            if (structure == null) {
                throw new IOException("Jigsaw structure is empty: " + resource);
            }
            return structure;
        } catch (RuntimeException exception) {
            throw new IOException("Jigsaw structure is not valid JSON: " + resource, exception);
        }
    }

    private static void requireExclusiveObjectReference(
            OwnedGraph graph,
            String activePieceKey,
            String objectKey
    ) throws IOException {
        for (String relativePath : graph.manifest().resourceHashes().keySet()) {
            if (!relativePath.startsWith("jigsaw-pieces/") || !relativePath.endsWith(".json")) {
                continue;
            }
            String pieceKey = relativePath.substring(
                    "jigsaw-pieces/".length(),
                    relativePath.length() - ".json".length());
            if (pieceKey.equals(activePieceKey)) {
                continue;
            }
            IrisJigsawPiece piece = readPiece(resolveOwnedResource(graph.root(), relativePath), relativePath);
            if (objectKey.equals(piece.getObject())) {
                throw new IOException("Object '" + objectKey + "' is shared by piece '" + pieceKey
                        + "'; duplicate the active variant before resizing its object bounds");
            }
        }
    }

    static PlanarPieceObjectResize resizePlanarPieceObject(
            JigsawStudioObject source,
            IrisJigsawPiece piece,
            JigsawPlanarArchetype archetype,
            JigsawStudioCellDimensions dimensions,
            String pieceKey
    ) throws IOException {
        JigsawStudioObject sourceObject = Objects.requireNonNull(source, "Planar Jigsaw Studio source object");
        IrisJigsawPiece targetPiece = Objects.requireNonNull(piece, "Planar Jigsaw Studio piece");
        JigsawPlanarArchetype targetArchetype = Objects.requireNonNull(
                archetype,
                "Planar Jigsaw Studio archetype");
        JigsawStudioCellDimensions targetDimensions = Objects.requireNonNull(
                dimensions,
                "Planar Jigsaw Studio target dimensions");
        String targetPieceKey = pieceKey == null || pieceKey.isBlank() ? "unknown" : pieceKey;
        if (targetDimensions.width() < 3 || targetDimensions.depth() < 3) {
            throw new IllegalArgumentException(
                    "Planar Jigsaw Studio workcell width and depth must each be at least 3 blocks");
        }
        if (sourceObject.width() < 1 || sourceObject.height() < 1 || sourceObject.depth() < 1) {
            throw new IOException("Planar piece '" + targetPieceKey + "' has invalid object dimensions");
        }
        if (IrisJigsawWorkcellArchetype.fromPiece(targetPiece) != targetArchetype.modelArchetype()) {
            throw new IOException("Planar piece '" + targetPieceKey + "' does not belong to "
                    + targetArchetype.stableId());
        }

        int quarterTurns = targetArchetype.modelArchetype().sourceToCanonicalQuarterTurns(targetPiece);
        JigsawStudioCellDimensions sourceDimensions = new JigsawStudioCellDimensions(
                sourceObject.width(),
                sourceObject.height(),
                sourceObject.depth());
        JigsawStudioCellDimensions canonicalDimensions = canonicalDimensions(sourceDimensions, quarterTurns);
        JigsawStudioObject sourceContents = sourceObject;
        Map<LocalPosition, BlockState> canonicalBlocks = canonicalBlocks(
                sourceObject,
                sourceContents,
                quarterTurns,
                targetPieceKey);
        Map<LocalPosition, BlockEntityData> canonicalTiles = canonicalTiles(
                sourceObject,
                sourceContents,
                quarterTurns,
                targetPieceKey);
        List<IrisJigsawConnector> connectors = targetPiece.getConnectors();
        if (connectors == null) {
            throw new IOException("Planar piece '" + targetPieceKey + "' has no connector list");
        }
        List<ConnectorResize> connectorResizes = planConnectorResizes(
                connectors,
                sourceDimensions,
                canonicalDimensions,
                targetDimensions,
                quarterTurns,
                targetPieceKey);
        relocateConnectorPayloads(canonicalBlocks, canonicalTiles, connectorResizes, targetPieceKey);
        requireContentInsideTarget(canonicalBlocks, canonicalTiles, targetDimensions, targetPieceKey);

        JigsawStudioCellDimensions resizedSourceDimensions = sourceDimensions(targetDimensions, quarterTurns);
        JigsawStudioObject resizedObject = rebuildSourceObject(
                sourceContents,
                canonicalBlocks,
                canonicalTiles,
                resizedSourceDimensions,
                quarterTurns,
                targetPieceKey);
        int relocatedConnectors = 0;
        for (ConnectorResize connectorResize : connectorResizes) {
            LocalPosition sourcePosition = toSource(
                    connectorResize.targetCanonical(),
                    resizedSourceDimensions,
                    quarterTurns);
            connectorResize.connector().setPosition(sourcePosition.toBlockPosition());
            if (!connectorResize.sourceCanonical().equals(connectorResize.targetCanonical())) {
                relocatedConnectors++;
            }
        }
        return new PlanarPieceObjectResize(resizedObject, relocatedConnectors);
    }

    private static Map<LocalPosition, BlockState> canonicalBlocks(
            JigsawStudioObject source,
            JigsawStudioObject contents,
            int quarterTurns,
            String pieceKey
    ) throws IOException {
        Map<LocalPosition, BlockState> blocks = new LinkedHashMap<>();
        for (Map.Entry<BlockPosition, BlockState> entry : contents.blocks().entrySet()) {
            LocalPosition sourcePosition = LocalPosition.from(entry.getKey());
            requireInside(sourcePosition, source.width(), source.height(), source.depth(),
                    "stored block", pieceKey);
            BlockState state = entry.getValue();
            LocalPosition canonicalPosition = toCanonical(
                    sourcePosition,
                    source.width(),
                    source.depth(),
                    quarterTurns);
            if (blocks.putIfAbsent(canonicalPosition, state) != null) {
                throw new IOException("Planar piece '" + pieceKey
                        + "' maps more than one stored block to " + canonicalPosition.describe());
            }
        }
        return blocks;
    }

    private static Map<LocalPosition, BlockEntityData> canonicalTiles(
            JigsawStudioObject source,
            JigsawStudioObject contents,
            int quarterTurns,
            String pieceKey
    ) throws IOException {
        Map<LocalPosition, BlockEntityData> tiles = new LinkedHashMap<>();
        for (Map.Entry<BlockPosition, BlockEntityData> entry : contents.blockEntities().entrySet()) {
            LocalPosition sourcePosition = LocalPosition.from(entry.getKey());
            requireInside(sourcePosition, source.width(), source.height(), source.depth(),
                    "tile data", pieceKey);
            BlockEntityData tile = entry.getValue();
            LocalPosition canonicalPosition = toCanonical(
                    sourcePosition,
                    source.width(),
                    source.depth(),
                    quarterTurns);
            if (tiles.putIfAbsent(canonicalPosition, tile) != null) {
                throw new IOException("Planar piece '" + pieceKey
                        + "' maps more than one tile payload to " + canonicalPosition.describe());
            }
        }
        return tiles;
    }

    private static List<ConnectorResize> planConnectorResizes(
            List<IrisJigsawConnector> connectors,
            JigsawStudioCellDimensions sourceDimensions,
            JigsawStudioCellDimensions canonicalDimensions,
            JigsawStudioCellDimensions targetDimensions,
            int quarterTurns,
            String pieceKey
    ) throws IOException {
        List<ConnectorResize> planned = new ArrayList<>(connectors.size());
        Set<LocalPosition> sourcePositions = new LinkedHashSet<>();
        Set<LocalPosition> targetPositions = new LinkedHashSet<>();
        BlockPosition sourceSize = dimensionsPosition(sourceDimensions);
        BlockPosition canonicalSize = dimensionsPosition(canonicalDimensions);
        BlockPosition targetSize = dimensionsPosition(targetDimensions);
        for (int index = 0; index < connectors.size(); index++) {
            IrisJigsawConnector connector = connectors.get(index);
            if (connector == null || connector.getPosition() == null || connector.getDirection() == null
                    || connector.getDirection().isVertical()) {
                throw new IOException("Planar piece '" + pieceKey + "' contains invalid connector " + index);
            }
            LocalPosition sourcePosition = LocalPosition.from(connector.getPosition());
            requireInside(
                    sourcePosition,
                    sourceDimensions.width(),
                    sourceDimensions.height(),
                    sourceDimensions.depth(),
                    "connector " + index,
                    pieceKey);
            if (!sourcePositions.add(sourcePosition)) {
                throw new IOException("Planar piece '" + pieceKey
                        + "' has multiple connectors at " + sourcePosition.describe());
            }
            LocalPosition sourceCanonical = toCanonical(
                    sourcePosition,
                    sourceDimensions.width(),
                    sourceDimensions.depth(),
                    quarterTurns);
            JigsawDirection canonicalDirection = rotateHorizontalDirection(
                    connector.getDirection(),
                    quarterTurns);
            LocalPosition expectedSource = LocalPosition.from(IrisJigsawConnector.canonicalPlanarPosition(
                    sourceSize,
                    connector.getDirection()));
            LocalPosition expectedCanonical = LocalPosition.from(IrisJigsawConnector.canonicalPlanarPosition(
                    canonicalSize,
                    canonicalDirection));
            boolean canonicalConnector = sourcePosition.equals(expectedSource)
                    || sourceCanonical.equals(expectedCanonical);
            LocalPosition targetCanonical = canonicalConnector
                    ? LocalPosition.from(IrisJigsawConnector.canonicalPlanarPosition(
                    targetSize,
                    canonicalDirection))
                    : sourceCanonical;
            if (!inside(targetCanonical, targetDimensions)) {
                throw new IOException("Planar piece '" + pieceKey + "' connector " + index + " at "
                        + sourceCanonical.describe() + " would be cropped by the requested workcell bounds");
            }
            if (!targetPositions.add(targetCanonical)) {
                throw new IOException("Planar piece '" + pieceKey
                        + "' would place multiple connectors at " + targetCanonical.describe());
            }
            planned.add(new ConnectorResize(connector, sourceCanonical, targetCanonical));
        }
        return List.copyOf(planned);
    }

    private static void relocateConnectorPayloads(
            Map<LocalPosition, BlockState> blocks,
            Map<LocalPosition, BlockEntityData> tiles,
            List<ConnectorResize> connectorResizes,
            String pieceKey
    ) throws IOException {
        Set<LocalPosition> relocatedSources = new LinkedHashSet<>();
        for (ConnectorResize connectorResize : connectorResizes) {
            if (tiles.containsKey(connectorResize.sourceCanonical())
                    || tiles.containsKey(connectorResize.targetCanonical())) {
                throw new IOException("Planar piece '" + pieceKey + "' has tile data at connector position "
                        + connectorResize.sourceCanonical().describe()
                        + "; connector tile data cannot be resized safely");
            }
            if (!connectorResize.sourceCanonical().equals(connectorResize.targetCanonical())) {
                relocatedSources.add(connectorResize.sourceCanonical());
            }
        }
        for (ConnectorResize connectorResize : connectorResizes) {
            if (connectorResize.sourceCanonical().equals(connectorResize.targetCanonical())) {
                continue;
            }
            if (blocks.containsKey(connectorResize.targetCanonical())
                    && !relocatedSources.contains(connectorResize.targetCanonical())) {
                throw new IOException("Planar piece '" + pieceKey + "' cannot relocate connector from "
                        + connectorResize.sourceCanonical().describe() + " to "
                        + connectorResize.targetCanonical().describe()
                        + " because the destination contains a stored block");
            }
        }
        List<BlockRelocation> payloads = new ArrayList<>(relocatedSources.size());
        for (ConnectorResize connectorResize : connectorResizes) {
            if (connectorResize.sourceCanonical().equals(connectorResize.targetCanonical())) {
                continue;
            }
            boolean present = blocks.containsKey(connectorResize.sourceCanonical());
            BlockState state = blocks.remove(connectorResize.sourceCanonical());
            payloads.add(new BlockRelocation(connectorResize.targetCanonical(), state, present));
        }
        for (BlockRelocation payload : payloads) {
            if (!payload.present()) {
                continue;
            }
            if (blocks.putIfAbsent(payload.target(), payload.state()) != null) {
                throw new IOException("Planar piece '" + pieceKey
                        + "' has colliding connector block payloads at " + payload.target().describe());
            }
        }
    }

    private static void requireContentInsideTarget(
            Map<LocalPosition, BlockState> blocks,
            Map<LocalPosition, BlockEntityData> tiles,
            JigsawStudioCellDimensions dimensions,
            String pieceKey
    ) throws IOException {
        for (LocalPosition position : blocks.keySet()) {
            if (!inside(position, dimensions)) {
                throw new IOException("Planar piece '" + pieceKey + "' has a stored block, including explicit air, at "
                        + position.describe() + " that would be cropped by the requested workcell bounds");
            }
        }
        for (LocalPosition position : tiles.keySet()) {
            if (!inside(position, dimensions)) {
                throw new IOException("Planar piece '" + pieceKey + "' has tile data at "
                        + position.describe() + " that would be cropped by the requested workcell bounds");
            }
        }
    }

    private static JigsawStudioObject rebuildSourceObject(
            JigsawStudioObject sourceContents,
            Map<LocalPosition, BlockState> canonicalBlocks,
            Map<LocalPosition, BlockEntityData> canonicalTiles,
            JigsawStudioCellDimensions sourceDimensions,
            int quarterTurns,
            String pieceKey
    ) throws IOException {
        Map<BlockPosition, BlockState> blocks = new LinkedHashMap<>();
        for (Map.Entry<LocalPosition, BlockState> entry : canonicalBlocks.entrySet()) {
            LocalPosition sourcePosition = toSource(entry.getKey(), sourceDimensions, quarterTurns);
            if (blocks.put(sourcePosition.toBlockPosition(), entry.getValue()) != null) {
                throw new IOException("Planar piece '" + pieceKey
                        + "' maps multiple stored blocks to " + sourcePosition.describe());
            }
        }
        Map<BlockPosition, BlockEntityData> blockEntities = new LinkedHashMap<>();
        for (Map.Entry<LocalPosition, BlockEntityData> entry : canonicalTiles.entrySet()) {
            LocalPosition sourcePosition = toSource(entry.getKey(), sourceDimensions, quarterTurns);
            if (blockEntities.put(sourcePosition.toBlockPosition(), entry.getValue()) != null) {
                throw new IOException("Planar piece '" + pieceKey
                        + "' maps multiple tile payloads to " + sourcePosition.describe());
            }
        }
        return sourceContents.with(
                sourceDimensions.width(),
                sourceDimensions.height(),
                sourceDimensions.depth(),
                blocks,
                blockEntities);
    }

    private static JigsawStudioCellDimensions canonicalDimensions(
            JigsawStudioCellDimensions source,
            int quarterTurns
    ) {
        return Math.floorMod(quarterTurns, 2) == 0
                ? source
                : new JigsawStudioCellDimensions(source.depth(), source.height(), source.width());
    }

    private static JigsawStudioCellDimensions sourceDimensions(
            JigsawStudioCellDimensions canonical,
            int quarterTurns
    ) {
        return Math.floorMod(quarterTurns, 2) == 0
                ? canonical
                : new JigsawStudioCellDimensions(canonical.depth(), canonical.height(), canonical.width());
    }

    private static LocalPosition toCanonical(
            LocalPosition source,
            int sourceWidth,
            int sourceDepth,
            int quarterTurns
    ) {
        return switch (Math.floorMod(quarterTurns, 4)) {
            case 0 -> source;
            case 1 -> new LocalPosition(sourceDepth - 1 - source.z(), source.y(), source.x());
            case 2 -> new LocalPosition(
                    sourceWidth - 1 - source.x(),
                    source.y(),
                    sourceDepth - 1 - source.z());
            case 3 -> new LocalPosition(source.z(), source.y(), sourceWidth - 1 - source.x());
            default -> throw new IllegalStateException("Unreachable planar object rotation");
        };
    }

    private static LocalPosition toSource(
            LocalPosition canonical,
            JigsawStudioCellDimensions sourceDimensions,
            int quarterTurns
    ) {
        return switch (Math.floorMod(quarterTurns, 4)) {
            case 0 -> canonical;
            case 1 -> new LocalPosition(
                    canonical.z(),
                    canonical.y(),
                    sourceDimensions.depth() - 1 - canonical.x());
            case 2 -> new LocalPosition(
                    sourceDimensions.width() - 1 - canonical.x(),
                    canonical.y(),
                    sourceDimensions.depth() - 1 - canonical.z());
            case 3 -> new LocalPosition(
                    sourceDimensions.width() - 1 - canonical.z(),
                    canonical.y(),
                    canonical.x());
            default -> throw new IllegalStateException("Unreachable planar object rotation");
        };
    }

    private static JigsawDirection rotateHorizontalDirection(JigsawDirection direction, int quarterTurns) {
        JigsawPlanarDirection planarDirection = switch (direction) {
            case NORTH_NEGATIVE_Z -> JigsawPlanarDirection.NORTH;
            case EAST_POSITIVE_X -> JigsawPlanarDirection.EAST;
            case SOUTH_POSITIVE_Z -> JigsawPlanarDirection.SOUTH;
            case WEST_NEGATIVE_X -> JigsawPlanarDirection.WEST;
            case UP_POSITIVE_Y, DOWN_NEGATIVE_Y -> throw new IllegalArgumentException(
                    "Planar connector direction must be horizontal");
        };
        return planarDirection.rotateClockwise(quarterTurns).direction();
    }

    private static BlockPosition dimensionsPosition(JigsawStudioCellDimensions dimensions) {
        return new BlockPosition(dimensions.width(), dimensions.height(), dimensions.depth());
    }

    private static boolean inside(LocalPosition position, JigsawStudioCellDimensions dimensions) {
        return inside(position, dimensions.width(), dimensions.height(), dimensions.depth());
    }

    private static boolean inside(LocalPosition position, int width, int height, int depth) {
        return position.x() >= 0 && position.x() < width
                && position.y() >= 0 && position.y() < height
                && position.z() >= 0 && position.z() < depth;
    }

    private static void requireInside(
            LocalPosition position,
            int width,
            int height,
            int depth,
            String content,
            String pieceKey
    ) throws IOException {
        if (!inside(position, width, height, depth)) {
            throw new IOException("Planar piece '" + pieceKey + "' has " + content + " at "
                    + position.describe() + " outside its object bounds");
        }
    }

    static JigsawStudioObject resizeObject(
            JigsawStudioObject source,
            JigsawStudioCellDimensions dimensions,
            String pieceKey
    ) throws IOException {
        JigsawStudioObject object = Objects.requireNonNull(source, "Jigsaw Studio source object");
        JigsawStudioCellDimensions target = Objects.requireNonNull(
                dimensions,
                "Jigsaw Studio target object dimensions");
        String normalizedPiece = pieceKey == null || pieceKey.isBlank() ? "unknown" : pieceKey;
        JigsawStudioObject contents = object;
        for (BlockPosition position : contents.blocks().keySet()) {
            LocalPosition unsigned = LocalPosition.from(position);
            if (!inside(unsigned, target)) {
                throw new IOException("Spatial piece '" + normalizedPiece
                        + "' has a stored block, including explicit air, at " + unsigned.describe()
                        + " that would be cropped by the requested variant size");
            }
        }
        for (BlockPosition position : contents.blockEntities().keySet()) {
            LocalPosition unsigned = LocalPosition.from(position);
            if (!inside(unsigned, target)) {
                throw new IOException("Spatial piece '" + normalizedPiece + "' has tile data at "
                        + unsigned.describe() + " that would be cropped by the requested variant size");
            }
        }
        return contents.with(target.width(), target.height(), target.depth(), contents.blocks(), contents.blockEntities());
    }

    private static void requireConnectorsInside(
            IrisJigsawPiece piece,
            JigsawStudioCellDimensions dimensions,
            String pieceKey
    ) throws IOException {
        if (piece.getConnectors() == null) {
            throw new IOException("Spatial piece '" + pieceKey + "' has no connector list");
        }
        for (int index = 0; index < piece.getConnectors().size(); index++) {
            IrisJigsawConnector connector = piece.getConnectors().get(index);
            if (connector == null || connector.getPosition() == null) {
                throw new IOException("Spatial piece '" + pieceKey + "' contains invalid connector " + index);
            }
            LocalPosition position = LocalPosition.from(connector.getPosition());
            if (!inside(position, dimensions)) {
                throw new IOException("Spatial piece '" + pieceKey + "' connector " + index + " at "
                        + position.describe() + " would be cropped by the requested variant size");
            }
        }
    }

    private static String describeDimensions(JigsawStudioCellDimensions dimensions) {
        return dimensions.width() + "x" + dimensions.height() + "x" + dimensions.depth();
    }

    private static String normalizeChannel(String channel) {
        String normalized = channel == null ? "" : channel.trim();
        if (normalized.equalsIgnoreCase("none")) {
            return "";
        }
        if (normalized.length() > 128) {
            throw new IllegalArgumentException("Jigsaw connector channels cannot exceed 128 characters");
        }
        for (int index = 0; index < normalized.length(); index++) {
            if (Character.isWhitespace(normalized.charAt(index))) {
                throw new IllegalArgumentException("Jigsaw connector channels cannot contain whitespace");
            }
        }
        return normalized;
    }

    private static StructureWriteResult write(OwnedGraph graph, StructureResourceBundle bundle) throws IOException {
        StructureResourceBundleGraphCompiler.requireViable(bundle);
        return writeCompiled(graph, bundle);
    }

    private static StructureWriteResult writeCompiled(
            OwnedGraph graph,
            StructureResourceBundle bundle
    ) throws IOException {
        StructureWriteResult result = graph.writer().write(
                bundle,
                StructureWriteOptions.overwriteExpected(graph.expectedManifestHash()));
        if (!result.successful()) {
            String conflict = result.conflicts().isEmpty()
                    ? result.status().name()
                    : result.conflicts().getFirst().relativePath() + ": "
                    + result.conflicts().getFirst().reason();
            throw new IOException("Atomic graph edit was rejected: " + conflict);
        }
        return result;
    }

    public record VariantResizeResult(
            StructureWriteResult writeResult,
            JigsawStudioCellDimensions previousDimensions,
            JigsawStudioCellDimensions dimensions,
            int relocatedConnectors
    ) {
        public VariantResizeResult {
            Objects.requireNonNull(writeResult, "Jigsaw Studio variant-resize write result");
            Objects.requireNonNull(previousDimensions, "Previous Jigsaw Studio variant dimensions");
            Objects.requireNonNull(dimensions, "Jigsaw Studio variant dimensions");
            if (relocatedConnectors < 0) {
                throw new IllegalArgumentException("Jigsaw Studio relocated connector count cannot be negative");
            }
        }
    }

    public record WorkcellCapacityResult(
            StructureWriteResult writeResult,
            int checkedVariants
    ) {
        public WorkcellCapacityResult {
            Objects.requireNonNull(writeResult, "Jigsaw Studio workcell-capacity write result");
            if (checkedVariants < 0) {
                throw new IllegalArgumentException("Jigsaw Studio checked variant count cannot be negative");
            }
        }
    }

    public record PieceDeletionResult(
            StructureWriteResult writeResult,
            int removedPoolMemberships,
            int changedPools,
            int removedPieceResources,
            int removedObjectResources
    ) {
        public PieceDeletionResult {
            Objects.requireNonNull(writeResult, "Jigsaw Studio piece deletion write result");
            if (removedPoolMemberships < 0 || changedPools < 0
                    || removedPieceResources < 0 || removedObjectResources < 0) {
                throw new IllegalArgumentException("Jigsaw Studio piece deletion counts cannot be negative");
            }
        }
    }

    public record VariantFamilyCreation(
            Map<String, String> pieceKeysByWorkcell,
            StructureWriteResult writeResult
    ) {
        public VariantFamilyCreation {
            Objects.requireNonNull(pieceKeysByWorkcell, "Jigsaw Studio variant-family piece keys");
            pieceKeysByWorkcell = Collections.unmodifiableMap(new LinkedHashMap<>(pieceKeysByWorkcell));
            Objects.requireNonNull(writeResult, "Jigsaw Studio variant-family write result");
        }
    }

    @FunctionalInterface
    private interface PieceContentEditor {
        void edit(JsonObject piece, String pieceResource) throws IOException;
    }

    private record PoolEntryRemoval(byte[] content, int removedEntries) {
        private PoolEntryRemoval {
            Objects.requireNonNull(content, "Jigsaw Studio pool entry removal content");
            if (removedEntries < 0) {
                throw new IllegalArgumentException("Removed jigsaw pool entry count cannot be negative");
            }
        }
    }

    private record PoolMembershipDuplication(byte[] content, int duplicatedEntries) {
        private PoolMembershipDuplication {
            Objects.requireNonNull(content, "Jigsaw Studio duplicated pool content");
            if (duplicatedEntries < 0) {
                throw new IllegalArgumentException("Duplicated pool membership count cannot be negative");
            }
        }
    }

    private enum VariantObjectMode {
        COPY_SOURCE,
        EMPTY_SOURCE_SIZE
    }

    record PlanarPieceObjectResize(JigsawStudioObject object, int relocatedConnectors) {
        PlanarPieceObjectResize {
            Objects.requireNonNull(object, "Resized planar Jigsaw Studio object");
            if (relocatedConnectors < 0) {
                throw new IllegalArgumentException("Relocated connector count cannot be negative");
            }
        }
    }

    private record ConnectorResize(
            IrisJigsawConnector connector,
            LocalPosition sourceCanonical,
            LocalPosition targetCanonical
    ) {
        private ConnectorResize {
            Objects.requireNonNull(connector, "Planar Jigsaw Studio connector");
            Objects.requireNonNull(sourceCanonical, "Planar Jigsaw Studio source connector position");
            Objects.requireNonNull(targetCanonical, "Planar Jigsaw Studio target connector position");
        }
    }

    private record BlockRelocation(LocalPosition target, BlockState state, boolean present) {
        private BlockRelocation {
            Objects.requireNonNull(target, "Planar Jigsaw Studio connector block target");
            if (present) {
                Objects.requireNonNull(state, "Planar Jigsaw Studio connector block state");
            }
        }
    }

    private record LocalPosition(int x, int y, int z) {
        private static LocalPosition from(BlockPosition position) {
            return new LocalPosition(position.x(), position.y(), position.z());
        }

        private BlockPosition toBlockPosition() {
            return new BlockPosition(x, y, z);
        }

        private String describe() {
            return x + "," + y + "," + z;
        }
    }

    private record OwnedGraph(
            Path root,
            StructureTransactionWriter writer,
            StructureOwnershipManifest manifest,
            String expectedManifestHash
    ) {
        private StructureResourceBundle.Builder bundleBuilder() {
            return StructureResourceBundle.builder(manifest.structure())
                    .source(manifest.source())
                    .backend(manifest.backend())
                    .capabilities(manifest.capabilities())
                    .losses(manifest.losses());
        }
    }
}
