package art.arcane.iris.studio.jigsaw;

import art.arcane.iris.structure.authoring.StructureKey;
import art.arcane.iris.structure.authoring.StructureHash;
import art.arcane.iris.structure.authoring.StructureOwnershipManifest;
import art.arcane.iris.structure.authoring.StructureResourceBundle;
import art.arcane.iris.structure.authoring.StructureTransactionWriter;
import art.arcane.iris.structure.authoring.StructureWriteOptions;
import art.arcane.iris.structure.authoring.StructureWriteResult;
import art.arcane.iris.structure.graph.PlanarJigsawWorkcellResolver;
import art.arcane.iris.structure.graph.StructureResourceBundleGraphCompiler;
import art.arcane.iris.pack.value.IrisDirection;
import art.arcane.iris.structure.jigsaw.IrisJigsawConnector;
import art.arcane.iris.structure.jigsaw.IrisJigsawMode;
import art.arcane.iris.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.structure.jigsaw.IrisJigsawPool;
import art.arcane.iris.structure.jigsaw.IrisJigsawWorkcellArchetype;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.pack.value.IrisPosition;
import art.arcane.iris.structure.placement.IrisStructure;
import art.arcane.iris.structure.jigsaw.JigsawJoint;
import art.arcane.iris.generation.geometry.IrisBlockVector;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class JigsawStudioGraphEditor {
    private JigsawStudioGraphEditor() {
    }

    public static StructureWriteResult createPiece(
            Path packRoot,
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
                content = JigsawStudioGraphDocuments.addPoolEntry(content, normalizedPiece, weight, resourcePath);
            }
            bundle.resource(resource.getKey(), content);
        }
        IrisJigsawPiece piece = new IrisJigsawPiece()
                .setObject(normalizedPiece)
                .setRotatable(true);
        if (topology != null) {
            IrisPosition cellSize = new IrisPosition(
                    dimensions.width(), dimensions.height(), dimensions.depth());
            for (JigsawPlanarDirection planarDirection : topology.directions()) {
                IrisDirection direction = planarDirection.irisDirection();
                piece.getConnectors().add(new IrisJigsawConnector()
                        .setPosition(IrisJigsawConnector.canonicalPlanarPosition(cellSize, direction))
                        .setDirection(direction)
                        .setTop(IrisDirection.UP_POSITIVE_Y)
                        .setPool(normalizedPool)
                        .setName("iris:planar")
                        .setTargetName("iris:planar")
                        .setJoint(JigsawJoint.ALIGNED)
                        .setFinalState("minecraft:structure_void"));
            }
        }
        IrisObject object = new IrisObject(dimensions.width(), dimensions.height(), dimensions.depth());
        bundle.textResource(pieceResource, JigsawStudioGraphDocuments.GSON.toJson(piece) + "\n");
        bundle.resource(objectResource, JigsawStudioProjectCreator.serialize(object));
        return write(graph, bundle.build());
    }

    public static StructureWriteResult duplicatePiece(
            Path packRoot,
            String structureKey,
            String sourcePieceKey,
            String targetPieceKey
    ) throws IOException {
        return createVariantFromPiece(
                packRoot,
                structureKey,
                sourcePieceKey,
                targetPieceKey,
                VariantObjectMode.COPY_SOURCE);
    }

    public static StructureWriteResult createBlankVariant(
            Path packRoot,
            String structureKey,
            String sourcePieceKey,
            String targetPieceKey
    ) throws IOException {
        return createVariantFromPiece(
                packRoot,
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
        Map<String, JigsawPlanarArchetype> expectedSources = JigsawStudioGraphDocuments.expectedThemeSetSources(
                structure,
                requestedSources.keySet());
        JigsawStudioGraphDocuments.requireExactThemeSetSources(requestedSources, expectedSources.keySet());

        Map<String, byte[]> resources = readOwnedResources(graph);
        resources.put(
                structureResource,
                JigsawStudioGraphDocuments.appendThemeSet(resources.get(structureResource), themeKey, structureResource));
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
            JigsawStudioGraphInspector.requireAvailableThemeSetTarget(graph, targetPieceResource);
            JigsawStudioGraphInspector.requireAvailableThemeSetTarget(graph, targetObjectResource);
            resources.put(
                    targetPieceResource,
                    JigsawStudioGraphDocuments.duplicatePieceForTheme(
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
                    JigsawStudioGraphDocuments.duplicatePoolMemberships(
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
        bundle.textResource(poolResource, JigsawStudioGraphDocuments.GSON.toJson(pool) + "\n");
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
                content = (JigsawStudioGraphDocuments.GSON.toJson(piece) + "\n").getBytes(StandardCharsets.UTF_8);
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
        List<String> normalizedThemes = JigsawStudioGraphDocuments.normalizeThemes(themes);
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
                && !JigsawStudioGraphInspector.otherOwnedPieceReferencesObject(graph, normalizedPiece, objectKey);
        JigsawStudioGraphInspector.requireDeletableArchetype(graph, normalizedPiece, targetPiece);

        StructureResourceBundle.Builder bundle = graph.bundleBuilder();
        int removedMemberships = 0;
        int changedPools = 0;
        for (String relativePath : graph.manifest().resourceHashes().keySet()) {
            if (relativePath.equals(pieceResource) || removeObject && relativePath.equals(objectResource)) {
                continue;
            }
            byte[] content = Files.readAllBytes(resolveOwnedResource(graph.root(), relativePath));
            if (relativePath.startsWith("jigsaw-pools/") && relativePath.endsWith(".json")) {
                JigsawStudioGraphDocuments.PoolEntryRemoval removal = JigsawStudioGraphDocuments.removePoolEntries(content, normalizedPiece, relativePath);
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
        JigsawStudioGraphInspector.requireExclusiveObjectReference(graph, normalizedPiece, objectKey);
        Path objectPath = resolveOwnedResource(graph.root(), objectResource);
        IrisObject source = new IrisObject();
        source.read(objectPath.toFile());
        String structureResource = "structures/"
                + JigsawStudioProjectCreator.Options.requireResourceKey(structureKey) + ".json";
        IrisStructure structure = readStructure(
                resolveOwnedResource(graph.root(), structureResource),
                structureResource);
        IrisObject resizedObject;
        JigsawStudioCellDimensions sourceDimensions = new JigsawStudioCellDimensions(
                source.getW(),
                source.getH(),
                source.getD());
        JigsawStudioCellDimensions previousDimensions;
        int relocatedConnectors = 0;
        if (structure.resolvedMode() == IrisJigsawMode.PLANAR_JIGSAW) {
            IrisJigsawWorkcellArchetype archetype = IrisJigsawWorkcellArchetype.fromPiece(piece);
            JigsawPlanarArchetype planarArchetype = JigsawPlanarArchetype.fromModel(archetype);
            int quarterTurns = archetype.sourceToCanonicalQuarterTurns(piece);
            previousDimensions = JigsawStudioObjectResizer.canonicalDimensions(sourceDimensions, quarterTurns);
            PlanarJigsawWorkcellResolver.ResolvedWorkcell workcell =
                    PlanarJigsawWorkcellResolver.resolve(structure).get(archetype);
            if (workcell == null || !workcell.contains(JigsawStudioObjectResizer.dimensionsPosition(targetDimensions))) {
                throw new IOException("Variant '" + normalizedPiece + "' size "
                        + JigsawStudioObjectResizer.describeDimensions(targetDimensions) + " exceeds the "
                        + planarArchetype.displayName() + " workcell capacity "
                        + JigsawStudioObjectResizer.describeDimensions(new JigsawStudioCellDimensions(
                        workcell == null ? 1 : workcell.width(),
                        workcell == null ? 1 : workcell.height(),
                        workcell == null ? 1 : workcell.depth()))
                        + "; increase that workcell capacity first");
            }
            JigsawStudioObjectResizer.PlanarPieceObjectResize resized = JigsawStudioObjectResizer.resizePlanarPieceObject(
                    source,
                    piece,
                    planarArchetype,
                    targetDimensions,
                    normalizedPiece);
            resizedObject = resized.object();
            relocatedConnectors = resized.relocatedConnectors();
        } else {
            previousDimensions = sourceDimensions;
            JigsawStudioObjectResizer.requireConnectorsInside(piece, targetDimensions, normalizedPiece);
            resizedObject = JigsawStudioObjectResizer.resizeObject(source, targetDimensions, normalizedPiece);
        }

        StructureResourceBundle.Builder bundle = graph.bundleBuilder();
        for (String relativePath : graph.manifest().resourceHashes().keySet()) {
            byte[] content;
            if (relativePath.equals(objectResource)) {
                content = JigsawStudioProjectCreator.serialize(resizedObject);
            } else if (relativePath.equals(pieceResource)) {
                content = (JigsawStudioGraphDocuments.GSON.toJson(piece) + "\n").getBytes(StandardCharsets.UTF_8);
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
            throw new IOException("Workcell capacity " + JigsawStudioObjectResizer.describeDimensions(targetDimensions)
                    + " cannot contain every " + targetArchetype.displayName()
                    + " variant: " + failureMessage(exception), exception);
        }
        return new WorkcellCapacityResult(writeResult, checkedVariants);
    }

    public static StructureWriteResult updateConnectorChannel(
            Path packRoot,
            String structureKey,
            String pieceKey,
            IrisPosition position,
            String channel
    ) throws IOException {
        String normalizedPiece = JigsawStudioProjectCreator.Options.requireResourceKey(pieceKey);
        IrisPosition connectorPosition = Objects.requireNonNull(position, "Jigsaw connector position");
        String normalizedChannel = JigsawStudioGraphDocuments.normalizeChannel(channel);
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
                    piece = JigsawStudioGraphDocuments.GSON.fromJson(new String(content, StandardCharsets.UTF_8), IrisJigsawPiece.class);
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
                content = (JigsawStudioGraphDocuments.GSON.toJson(piece) + "\n").getBytes(StandardCharsets.UTF_8);
            }
            bundle.resource(relativePath, content);
        }
        if (!found) {
            throw new IOException("Piece '" + normalizedPiece + "' has no connector at "
                    + connectorPosition.getX() + "," + connectorPosition.getY() + ","
                    + connectorPosition.getZ());
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

    private static Map<String, byte[]> readOwnedResources(OwnedGraph graph) throws IOException {
        Map<String, byte[]> resources = new LinkedHashMap<>();
        for (String relativePath : graph.manifest().resourceHashes().keySet()) {
            resources.put(
                    relativePath,
                    Files.readAllBytes(resolveOwnedResource(graph.root(), relativePath)));
        }
        return resources;
    }

    private static StructureWriteResult createVariantFromPiece(
            Path packRoot,
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
        JigsawStudioGraphInspector.requireAvailableVariantTarget(graph, targetPieceResource);
        JigsawStudioGraphInspector.requireAvailableVariantTarget(graph, targetObjectResource);

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
            IrisBlockVector sourceSize = IrisObject.sampleSize(sourceObjectPath.toFile());
            targetObjectContent = JigsawStudioProjectCreator.serialize(new IrisObject(
                    sourceSize.getBlockX(),
                    sourceSize.getBlockY(),
                    sourceSize.getBlockZ()));
        }

        Map<String, String> targetPieceKeysBySource = Map.of(normalizedSource, normalizedTarget);
        StructureResourceBundle.Builder bundle = graph.bundleBuilder();
        int duplicatedMemberships = 0;
        for (String relativePath : graph.manifest().resourceHashes().keySet()) {
            byte[] content = Files.readAllBytes(resolveOwnedResource(graph.root(), relativePath));
            if (relativePath.startsWith("jigsaw-pools/") && relativePath.endsWith(".json")) {
                JigsawStudioGraphDocuments.PoolMembershipDuplication duplication = JigsawStudioGraphDocuments.duplicatePoolMemberships(
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
                JigsawStudioGraphDocuments.duplicatePieceForVariant(sourcePieceContent, normalizedTarget, sourcePieceResource));
        bundle.resource(targetObjectResource, targetObjectContent);
        return write(graph, bundle.build());
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
                content = (JigsawStudioGraphDocuments.GSON.toJson(piece) + "\n").getBytes(StandardCharsets.UTF_8);
            }
            bundle.resource(relativePath, content);
        }
        return write(graph, bundle.build());
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

    private static String failureMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    static Path resolveOwnedResource(Path root, String relativePath) throws IOException {
        StructureResourceBundle.validateRelativePath(relativePath);
        Path resource = root.resolve(relativePath).normalize();
        if (!resource.startsWith(root) || !Files.isRegularFile(resource, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Owned graph resource is missing or unsafe: " + relativePath);
        }
        return resource;
    }

    static IrisJigsawPiece readPiece(Path path, String resource) throws IOException {
        try {
            IrisJigsawPiece piece = JigsawStudioGraphDocuments.GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8),
                    IrisJigsawPiece.class);
            if (piece == null) {
                throw new IOException("Jigsaw piece is empty: " + resource);
            }
            return piece;
        } catch (RuntimeException exception) {
            throw new IOException("Jigsaw piece is not valid JSON: " + resource, exception);
        }
    }

    static IrisStructure readStructure(Path path, String resource) throws IOException {
        try {
            IrisStructure structure = JigsawStudioGraphDocuments.GSON.fromJson(
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

    private enum VariantObjectMode {
        COPY_SOURCE,
        EMPTY_SOURCE_SIZE
    }

    record OwnedGraph(
            Path root,
            StructureTransactionWriter writer,
            StructureOwnershipManifest manifest,
            String expectedManifestHash
    ) {
        StructureResourceBundle.Builder bundleBuilder() {
            return StructureResourceBundle.builder(manifest.structure())
                    .source(manifest.source())
                    .backend(manifest.backend())
                    .capabilities(manifest.capabilities())
                    .losses(manifest.losses());
        }
    }
}
