package art.arcane.iris.structure.export;

import art.arcane.iris.structure.graph.CompiledStructureGraph;
import art.arcane.iris.structure.graph.PlanarJigsawWorkcellResolver;
import art.arcane.iris.structure.graph.StructureGraphCompilation;
import art.arcane.iris.structure.graph.StructureGraphCompiler;
import art.arcane.iris.structure.graph.StructureGraphDiagnostic;
import art.arcane.iris.structure.jigsaw.IrisJigsawCompatibility;
import art.arcane.iris.structure.jigsaw.IrisJigsawConnector;
import art.arcane.iris.structure.jigsaw.IrisJigsawMode;
import art.arcane.iris.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.structure.jigsaw.IrisJigsawPieceEntry;
import art.arcane.iris.structure.jigsaw.IrisJigsawPieceRules;
import art.arcane.iris.structure.jigsaw.IrisJigsawPool;
import art.arcane.iris.structure.jigsaw.IrisJigsawWorkcellArchetype;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.pack.value.IrisPosition;
import art.arcane.iris.structure.placement.IrisStructure;
import art.arcane.iris.structure.object.ObjectPlaceMode;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.generation.geometry.IrisBlockVector;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class VanillaJigsawExportCompiler {
    private static final int MAX_DEPTH = 20;
    private static final int MAX_HORIZONTAL_DISTANCE = 128;
    private static final int MAX_VERTICAL_DISTANCE = 4064;
    private static final int MIN_ABSOLUTE_Y = -2032;
    private static final int MAX_ABSOLUTE_Y = 2031;
    private static final int MAX_POOL_WEIGHT = 150;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    Compilation compile(VanillaJigsawExportRequest request) {
        List<VanillaJigsawExportDiagnostic> diagnostics = new ArrayList<>();
        validateIdentity(request, diagnostics);
        validateSettings(request.settings(), diagnostics);

        IrisStructure structure;
        try {
            structure = request.source().loadStructure();
        } catch (RuntimeException exception) {
            addError(diagnostics,
                    VanillaJigsawExportDiagnostic.Code.SOURCE_STRUCTURE_MISSING,
                    request.source().structureKey(),
                    "Could not load Iris structure '" + request.source().structureKey() + "': "
                            + exception.getMessage());
            return rejected(diagnostics);
        }
        if (structure == null) {
            addError(diagnostics,
                    VanillaJigsawExportDiagnostic.Code.SOURCE_STRUCTURE_MISSING,
                    request.source().structureKey(),
                    "Iris structure '" + request.source().structureKey() + "' does not exist.");
            return rejected(diagnostics);
        }

        validateStructure(structure, request.settings(), diagnostics);
        StructureGraphCompilation graphCompilation;
        try {
            graphCompilation = StructureGraphCompiler.compile(structure, request.source().resolver());
        } catch (RuntimeException exception) {
            addError(diagnostics,
                    VanillaJigsawExportDiagnostic.Code.GRAPH_VALIDATION,
                    request.source().structureKey(),
                    "Structure graph compilation failed: " + exception.getMessage());
            return rejected(diagnostics);
        }
        addGraphDiagnostics(graphCompilation, diagnostics);
        CompiledStructureGraph graph = graphCompilation.getGraph();
        validateGraph(graph, diagnostics);
        if (hasErrors(diagnostics)) {
            return rejected(diagnostics);
        }

        try {
            Map<String, byte[]> resources = createResources(request, graph);
            return new Compilation(resources, List.copyOf(diagnostics));
        } catch (IOException | RuntimeException exception) {
            addError(diagnostics,
                    VanillaJigsawExportDiagnostic.Code.SERIALIZATION_FAILED,
                    request.source().structureKey(),
                    "Vanilla datapack serialization failed: " + exception.getMessage());
            return rejected(diagnostics);
        }
    }

    private void validateIdentity(
            VanillaJigsawExportRequest request,
            List<VanillaJigsawExportDiagnostic> diagnostics
    ) {
        if (!VanillaResourceIdentifier.validNamespace(request.namespace())) {
            addError(diagnostics,
                    VanillaJigsawExportDiagnostic.Code.INVALID_NAMESPACE,
                    request.namespace(),
                    "Datapack namespace must match [a-z0-9_.-]+.");
        }
        if (!VanillaResourceIdentifier.validPath(request.resourcePath())) {
            addError(diagnostics,
                    VanillaJigsawExportDiagnostic.Code.INVALID_RESOURCE_PATH,
                    request.resourcePath(),
                    "Datapack resource path must use lowercase resource-path characters without traversal.");
        }
    }

    private void validateSettings(
            VanillaJigsawExportSettings settings,
            List<VanillaJigsawExportDiagnostic> diagnostics
    ) {
        if (settings.biomes().isEmpty()) {
            addError(diagnostics,
                    VanillaJigsawExportDiagnostic.Code.INVALID_BIOME,
                    "biomes",
                    "At least one biome identifier is required.");
        }
        for (String biome : settings.biomes()) {
            if (!VanillaResourceIdentifier.validIdentifier(biome)) {
                addError(diagnostics,
                        VanillaJigsawExportDiagnostic.Code.INVALID_BIOME,
                        biome,
                        "Biome identifier '" + biome + "' is not a valid namespaced identifier.");
            }
        }
        if (settings.startHeight() < MIN_ABSOLUTE_Y || settings.startHeight() > MAX_ABSOLUTE_Y) {
            addError(diagnostics,
                    VanillaJigsawExportDiagnostic.Code.INVALID_SETTINGS,
                    "start_height",
                    "Absolute start height must be between " + MIN_ABSOLUTE_Y + " and " + MAX_ABSOLUTE_Y + ".");
        }
        if (settings.maxDistanceVertical() < 1 || settings.maxDistanceVertical() > MAX_VERTICAL_DISTANCE) {
            addError(diagnostics,
                    VanillaJigsawExportDiagnostic.Code.INVALID_MAX_DISTANCE,
                    "max_distance_from_center.vertical",
                    "Vertical maximum distance must be between 1 and " + MAX_VERTICAL_DISTANCE + ".");
        }
        if (settings.spacing() < 0 || settings.spacing() > 4096
                || settings.separation() < 0 || settings.separation() > 4096
                || settings.spacing() <= settings.separation()) {
            addError(diagnostics,
                    VanillaJigsawExportDiagnostic.Code.INVALID_SETTINGS,
                    "random_spread",
                    "Random-spread spacing and separation must be within 0..4096, with spacing greater than separation.");
        }
        if (settings.salt() < 0) {
            addError(diagnostics,
                    VanillaJigsawExportDiagnostic.Code.INVALID_SETTINGS,
                    "random_spread.salt",
                    "Random-spread salt must be non-negative.");
        }
        if (!Float.isFinite(settings.frequency()) || settings.frequency() < 0.0F || settings.frequency() > 1.0F) {
            addError(diagnostics,
                    VanillaJigsawExportDiagnostic.Code.INVALID_SETTINGS,
                    "random_spread.frequency",
                    "Random-spread frequency must be finite and within 0..1.");
        }
    }

    private void validateStructure(
            IrisStructure structure,
            VanillaJigsawExportSettings settings,
            List<VanillaJigsawExportDiagnostic> diagnostics
    ) {
        if (structure.resolvedCompatibility() != IrisJigsawCompatibility.VANILLA_PORTABLE) {
            addError(diagnostics,
                    VanillaJigsawExportDiagnostic.Code.UNSUPPORTED_COMPATIBILITY,
                    structureKey(structure),
                    "Strict export requires compatibility VANILLA_PORTABLE.");
        }
        if (structure.getPlaceMode() != ObjectPlaceMode.STRUCTURE_PIECE) {
            addError(diagnostics,
                    VanillaJigsawExportDiagnostic.Code.UNSUPPORTED_PLACE_MODE,
                    structureKey(structure),
                    "Vanilla export requires placeMode STRUCTURE_PIECE; Iris terrain and stilt modes have no vanilla jigsaw equivalent.");
        }
        if (structure.getEdit() != null && !structure.getEdit().isEmpty()) {
            addError(diagnostics,
                    VanillaJigsawExportDiagnostic.Code.UNSUPPORTED_EDIT,
                    structureKey(structure),
                    "Structure-wide Iris block edits cannot be represented losslessly by vanilla template pools.");
        }
        if (structure.getLoot() != null && !structure.getLoot().isEmpty()) {
            addError(diagnostics,
                    VanillaJigsawExportDiagnostic.Code.UNSUPPORTED_LOOT,
                    structureKey(structure),
                    "Structure-wide Iris loot injection cannot be represented losslessly by this vanilla exporter.");
        }
        if (structure.getThemeSets() != null && !structure.getThemeSets().isEmpty()) {
            addError(diagnostics,
                    VanillaJigsawExportDiagnostic.Code.UNSUPPORTED_THEME_METADATA,
                    structureKey(structure),
                    "Coherent Iris theme selection has no lossless vanilla jigsaw representation.");
        }
        if (structure.isRequireCaps()) {
            addError(diagnostics,
                    VanillaJigsawExportDiagnostic.Code.UNSUPPORTED_REQUIRED_CAPS,
                    structureKey(structure),
                    "Vanilla jigsaws cannot enforce Iris requireCaps terminal-closure semantics.");
        }
        if (structure.getMaxDepth() < 1 || structure.getMaxDepth() > MAX_DEPTH) {
            addError(diagnostics,
                    VanillaJigsawExportDiagnostic.Code.INVALID_MAX_DEPTH,
                    structureKey(structure),
                    "Minecraft 26.2 jigsaw size must be within 0..20; Iris export requires 1..20.");
        }
        long horizontalDistance = (long) structure.getMaxSizeChunks() * 16L;
        if (horizontalDistance < 1L || horizontalDistance > MAX_HORIZONTAL_DISTANCE) {
            addError(diagnostics,
                    VanillaJigsawExportDiagnostic.Code.INVALID_MAX_DISTANCE,
                    structureKey(structure),
                    "Iris maxSizeChunks maps to " + horizontalDistance
                            + " blocks, outside Minecraft 26.2's 1..128 horizontal limit.");
        }
        int terrainPadding = settings.terrainAdaptation() == VanillaJigsawExportSettings.TerrainAdaptation.NONE
                ? 0 : 12;
        if (horizontalDistance + terrainPadding > MAX_HORIZONTAL_DISTANCE) {
            addError(diagnostics,
                    VanillaJigsawExportDiagnostic.Code.INVALID_MAX_DISTANCE,
                    structureKey(structure),
                    "Horizontal distance plus vanilla terrain-adaptation padding must not exceed 128 blocks.");
        }
    }

    private void addGraphDiagnostics(
            StructureGraphCompilation graphCompilation,
            List<VanillaJigsawExportDiagnostic> diagnostics
    ) {
        for (StructureGraphDiagnostic diagnostic : graphCompilation.getDiagnostics()) {
            VanillaJigsawExportDiagnostic.Severity severity = diagnostic.severity()
                    == StructureGraphDiagnostic.Severity.ERROR
                    ? VanillaJigsawExportDiagnostic.Severity.ERROR
                    : VanillaJigsawExportDiagnostic.Severity.WARNING;
            diagnostics.add(new VanillaJigsawExportDiagnostic(
                    severity,
                    VanillaJigsawExportDiagnostic.Code.GRAPH_VALIDATION,
                    diagnostic.code().name(),
                    diagnostic.message()));
        }
    }

    private void validateGraph(
            CompiledStructureGraph graph,
            List<VanillaJigsawExportDiagnostic> diagnostics
    ) {
        Map<IrisJigsawWorkcellArchetype, PlanarJigsawWorkcellResolver.ResolvedWorkcell> workcells =
                resolvedWorkcells(graph.getStructure());
        for (Map.Entry<String, IrisJigsawPool> poolEntry : graph.getPools().entrySet()) {
            String poolKey = poolEntry.getKey();
            if (!VanillaResourceIdentifier.validPath(poolKey)) {
                addError(diagnostics,
                        VanillaJigsawExportDiagnostic.Code.INVALID_RESOURCE_PATH,
                        poolKey,
                        "Iris pool keys must be valid lowercase resource paths for vanilla export.");
            }
            validatePool(graph, workcells, poolKey, poolEntry.getValue(), diagnostics);
        }
        for (Map.Entry<String, IrisJigsawPiece> pieceEntry : graph.getPieces().entrySet()) {
            if (!pieceEnabled(workcells, pieceEntry.getValue())) {
                continue;
            }
            String pieceKey = pieceEntry.getKey();
            if (!VanillaResourceIdentifier.validPath(pieceKey)) {
                addError(diagnostics,
                        VanillaJigsawExportDiagnostic.Code.INVALID_RESOURCE_PATH,
                        pieceKey,
                        "Iris piece keys must be valid lowercase resource paths for vanilla export.");
            }
            validatePiece(graph, pieceKey, pieceEntry.getValue(), diagnostics);
        }
    }

    private void validatePool(
            CompiledStructureGraph graph,
            Map<IrisJigsawWorkcellArchetype, PlanarJigsawWorkcellResolver.ResolvedWorkcell> workcells,
            String poolKey,
            IrisJigsawPool pool,
            List<VanillaJigsawExportDiagnostic> diagnostics
    ) {
        if (pool.isMandatoryFallback()) {
            addError(diagnostics,
                    VanillaJigsawExportDiagnostic.Code.UNSUPPORTED_REQUIRED_CAPS,
                    poolKey,
                    "Vanilla template pools cannot enforce Iris mandatoryFallback terminal closure.");
        }
        if (pool.getFallback() != null && !pool.getFallback().isBlank()
                && !VanillaResourceIdentifier.validPath(pool.getFallback().trim())) {
            addError(diagnostics,
                    VanillaJigsawExportDiagnostic.Code.INVALID_RESOURCE_PATH,
                    poolKey,
                    "Fallback pool key '" + pool.getFallback() + "' is not a valid vanilla resource path.");
        }
        if (pool.getPieces() == null) {
            return;
        }
        for (int index = 0; index < pool.getPieces().size(); index++) {
            IrisJigsawPieceEntry entry = pool.getPieces().get(index);
            if (entry == null) {
                continue;
            }
            if (!entry.isEmpty()
                    && !pieceEnabled(workcells, graph.getPieces().get(trim(entry.getPiece())))) {
                continue;
            }
            if (entry.getWeight() < 1 || entry.getWeight() > MAX_POOL_WEIGHT) {
                addError(diagnostics,
                        VanillaJigsawExportDiagnostic.Code.INVALID_POOL_WEIGHT,
                        poolKey + "/pieces[" + index + "]",
                        "Minecraft 26.2 template-pool weights must be within 1..150.");
            }
            if (entry.getChance() != 1D) {
                addError(diagnostics,
                        VanillaJigsawExportDiagnostic.Code.UNSUPPORTED_CHANCE,
                        poolKey + "/pieces[" + index + "]",
                        "Independent Iris membership chance cannot be represented by vanilla pool weights.");
            }
            if (!entry.isEmpty() && !VanillaResourceIdentifier.validPath(trim(entry.getPiece()))) {
                addError(diagnostics,
                        VanillaJigsawExportDiagnostic.Code.INVALID_RESOURCE_PATH,
                        poolKey + "/pieces[" + index + "]",
                        "Piece key '" + entry.getPiece() + "' is not a valid vanilla resource path.");
            }
        }
    }

    private void validatePiece(
            CompiledStructureGraph graph,
            String pieceKey,
            IrisJigsawPiece piece,
            List<VanillaJigsawExportDiagnostic> diagnostics
    ) {
        if (piece.getThemes() != null && !piece.getThemes().isEmpty()) {
            addError(diagnostics,
                    VanillaJigsawExportDiagnostic.Code.UNSUPPORTED_THEME_METADATA,
                    pieceKey,
                    "Iris piece theme membership has no vanilla template-pool equivalent.");
        }
        IrisJigsawPieceRules rules = piece.resolvedRules();
        if (rules.getMinimumDepth() != 0
                || rules.getMaximumDepth() != 30
                || rules.getMinimumPlacements() != 0
                || rules.getMaximumPlacements() != 0
                || rules.isTerminal()) {
            addError(diagnostics,
                    VanillaJigsawExportDiagnostic.Code.UNSUPPORTED_PIECE_RULES,
                    pieceKey,
                    "Iris depth, placement-count, and terminal rules cannot be serialized losslessly to vanilla.");
        }
        if (!piece.isRotatable()) {
            addError(diagnostics,
                    VanillaJigsawExportDiagnostic.Code.UNSUPPORTED_FIXED_ROTATION,
                    pieceKey,
                    "Vanilla template pools do not provide an exact fixed-rotation equivalent for Iris rotatable=false.");
        }
        if (!piece.isCollidable()) {
            addError(diagnostics,
                    VanillaJigsawExportDiagnostic.Code.UNSUPPORTED_NON_COLLIDABLE_PIECE,
                    pieceKey,
                    "Vanilla template pools cannot serialize Iris collidable=false assembly metadata.");
        }
        IrisObject object = graph.getObjects().get(trim(piece.getObject()));
        if (object == null) {
            return;
        }
        validateObject(pieceKey, object, diagnostics);
        validateConnectors(graph, pieceKey, piece, object, diagnostics);
    }

    private void validateObject(
            String pieceKey,
            IrisObject object,
            List<VanillaJigsawExportDiagnostic> diagnostics
    ) {
        if (!object.getStates().isEmpty()) {
            addError(diagnostics,
                    VanillaJigsawExportDiagnostic.Code.UNSUPPORTED_TILE_DATA,
                    pieceKey,
                    "The .iob contains tile payloads; exact registry-aware block-entity NBT export is not available in core.");
        }
        for (Map.Entry<IrisBlockVector, PlatformBlockState> entry : object.getBlocks()) {
            PlatformBlockState state = entry.getValue();
            String resource = pieceKey + "@" + entry.getKey();
            IrisBlockVector position = entry.getKey();
            int x = position.getBlockX() + object.getCenter().getX();
            int y = position.getBlockY() + object.getCenter().getY();
            int z = position.getBlockZ() + object.getCenter().getZ();
            if (x < 0 || x >= object.getW() || y < 0 || y >= object.getH() || z < 0 || z >= object.getD()) {
                addError(diagnostics,
                        VanillaJigsawExportDiagnostic.Code.INVALID_BLOCK_STATE,
                        resource,
                        "The .iob contains a block outside its declared dimensions.");
                continue;
            }
            if (state.isCustom()) {
                addError(diagnostics,
                        VanillaJigsawExportDiagnostic.Code.UNSUPPORTED_CUSTOM_BLOCK,
                        resource,
                        "Custom-content block '" + state.key() + "' is not available in unmodded vanilla.");
                continue;
            }
            if (state.hasTileEntity()) {
                addError(diagnostics,
                        VanillaJigsawExportDiagnostic.Code.UNSUPPORTED_BLOCK_ENTITY,
                        resource,
                        "Block '" + state.key() + "' requires block-entity NBT that core cannot export losslessly.");
            }
            try {
                VanillaBlockState parsed = VanillaBlockState.parse(state.key());
                if (parsed.name().equals("minecraft:jigsaw")
                        || parsed.name().equals("minecraft:structure_block")
                        || parsed.name().equals("minecraft:structure_void")) {
                    addError(diagnostics,
                            VanillaJigsawExportDiagnostic.Code.UNSUPPORTED_MARKER_BLOCK,
                            resource,
                            "Marker block '" + parsed.name() + "' must be represented by Iris connector metadata, not object blocks.");
                }
            } catch (IllegalArgumentException exception) {
                addError(diagnostics,
                        VanillaJigsawExportDiagnostic.Code.INVALID_BLOCK_STATE,
                        resource,
                        exception.getMessage());
            }
        }
    }

    private void validateConnectors(
            CompiledStructureGraph graph,
            String pieceKey,
            IrisJigsawPiece piece,
            IrisObject object,
            List<VanillaJigsawExportDiagnostic> diagnostics
    ) {
        if (piece.getConnectors() == null) {
            return;
        }
        Set<String> positions = new LinkedHashSet<>();
        for (int index = 0; index < piece.getConnectors().size(); index++) {
            IrisJigsawConnector connector = piece.getConnectors().get(index);
            if (connector == null) {
                continue;
            }
            String resource = pieceKey + "/connectors[" + index + "]";
            if (connector.getChannel() != null && !connector.getChannel().isBlank()) {
                addError(diagnostics,
                        VanillaJigsawExportDiagnostic.Code.UNSUPPORTED_CHANNEL,
                        resource,
                        "Iris connector channels have no vanilla jigsaw NBT equivalent.");
            }
            validateConnectorIdentifier(connector.getName(), resource + "/name", diagnostics);
            validateConnectorIdentifier(connector.getTargetName(), resource + "/target", diagnostics);
            String poolKey = trim(connector.getPool());
            if (!VanillaResourceIdentifier.validPath(poolKey) || !graph.getPools().containsKey(poolKey)) {
                addError(diagnostics,
                        VanillaJigsawExportDiagnostic.Code.INVALID_RESOURCE_PATH,
                        resource + "/pool",
                        "Connector pool '" + connector.getPool() + "' cannot be mapped to an exported template pool.");
            }
            try {
                VanillaStructureTemplateEncoder.orientation(connector.getDirection(), connector.getTop());
            } catch (IllegalArgumentException | NullPointerException exception) {
                addError(diagnostics,
                        VanillaJigsawExportDiagnostic.Code.INVALID_CONNECTOR_ORIENTATION,
                        resource,
                        exception.getMessage() == null ? "Connector orientation is incomplete." : exception.getMessage());
            }
            IrisPosition position = connector.getPosition();
            if (position == null || !inside(position, object)) {
                continue;
            }
            String positionKey = position.getX() + "," + position.getY() + "," + position.getZ();
            if (!positions.add(positionKey)) {
                addError(diagnostics,
                        VanillaJigsawExportDiagnostic.Code.DUPLICATE_CONNECTOR_POSITION,
                        resource,
                        "Multiple vanilla jigsaw block entities cannot occupy " + positionKey + ".");
            }
            validateFinalState(object, connector, resource, diagnostics);
        }
    }

    private void validateConnectorIdentifier(
            String value,
            String resource,
            List<VanillaJigsawExportDiagnostic> diagnostics
    ) {
        try {
            VanillaResourceIdentifier.normalizeConnectorIdentifier(value);
        } catch (IllegalArgumentException exception) {
            addError(diagnostics,
                    VanillaJigsawExportDiagnostic.Code.INVALID_CONNECTOR_ID,
                    resource,
                    exception.getMessage());
        }
    }

    private void validateFinalState(
            IrisObject object,
            IrisJigsawConnector connector,
            String resource,
            List<VanillaJigsawExportDiagnostic> diagnostics
    ) {
        VanillaBlockState configured;
        try {
            configured = VanillaBlockState.parse(connector.getFinalState());
        } catch (IllegalArgumentException exception) {
            addError(diagnostics,
                    VanillaJigsawExportDiagnostic.Code.INVALID_CONNECTOR_FINAL_STATE,
                    resource,
                    exception.getMessage());
            return;
        }
        IrisPosition position = connector.getPosition();
        PlatformBlockState objectState = object.getBlocks().get(
                object.getSigned(position.getX(), position.getY(), position.getZ()));
        String expectedSource = objectState == null ? "minecraft:structure_void" : objectState.key();
        try {
            VanillaBlockState expected = VanillaBlockState.parse(expectedSource);
            if (!configured.canonical().equals(expected.canonical())) {
                addError(diagnostics,
                        VanillaJigsawExportDiagnostic.Code.INVALID_CONNECTOR_FINAL_STATE,
                        resource,
                        "Connector finalState '" + configured.canonical() + "' does not match the .iob block '"
                                + expected.canonical() + "' at that position. Use minecraft:structure_void for an absent block.");
            }
        } catch (IllegalArgumentException exception) {
            addError(diagnostics,
                    VanillaJigsawExportDiagnostic.Code.INVALID_CONNECTOR_FINAL_STATE,
                    resource,
                    "The .iob block under the connector is not vanilla-compatible: " + exception.getMessage());
        }
    }

    private Map<String, byte[]> createResources(
            VanillaJigsawExportRequest request,
            CompiledStructureGraph graph
    ) throws IOException {
        Map<String, byte[]> resources = new LinkedHashMap<>();
        putJson(resources, "pack.mcmeta", packMetadata(request));
        putJson(resources, biomeTagPath(request), biomeTag(request));
        putJson(resources, processorPath(request), processorList());
        putJson(resources, structurePath(request), structure(request, graph.getStructure()));
        putJson(resources, structureSetPath(request), structureSet(request));

        for (Map.Entry<String, IrisJigsawPool> poolEntry : graph.getPools().entrySet()) {
            putJson(resources,
                    poolPath(request, poolEntry.getKey()),
                    templatePool(request, graph, poolEntry.getValue()));
        }

        VanillaStructureTemplateEncoder encoder = new VanillaStructureTemplateEncoder();
        Map<IrisJigsawWorkcellArchetype, PlanarJigsawWorkcellResolver.ResolvedWorkcell> workcells =
                resolvedWorkcells(graph.getStructure());
        for (Map.Entry<String, IrisJigsawPiece> pieceEntry : graph.getPieces().entrySet()) {
            if (!pieceEnabled(workcells, pieceEntry.getValue())) {
                continue;
            }
            IrisObject object = graph.getObjects().get(trim(pieceEntry.getValue().getObject()));
            byte[] template = encoder.encode(
                    object,
                    pieceEntry.getValue(),
                    poolKey -> poolIdentifier(request, trim(poolKey)));
            resources.put(templatePath(request, pieceEntry.getKey()), template);
        }
        return resources;
    }

    private JsonObject packMetadata(VanillaJigsawExportRequest request) {
        JsonObject pack = new JsonObject();
        pack.addProperty("description", request.description());
        JsonArray minimum = new JsonArray();
        minimum.add(107);
        minimum.add(1);
        pack.add("min_format", minimum);
        pack.addProperty("max_format", 107);
        JsonObject root = new JsonObject();
        root.add("pack", pack);
        return root;
    }

    private JsonObject biomeTag(VanillaJigsawExportRequest request) {
        JsonObject root = new JsonObject();
        root.addProperty("replace", false);
        JsonArray values = new JsonArray();
        for (String biome : request.settings().biomes()) {
            values.add(biome);
        }
        root.add("values", values);
        return root;
    }

    private JsonObject processorList() {
        JsonObject root = new JsonObject();
        root.add("processors", new JsonArray());
        return root;
    }

    private JsonObject structure(VanillaJigsawExportRequest request, IrisStructure structure) {
        VanillaJigsawExportSettings settings = request.settings();
        JsonObject root = new JsonObject();
        root.addProperty("type", "minecraft:jigsaw");
        root.addProperty("biomes", "#" + request.namespace() + ":" + request.resourcePath());
        int horizontalDistance = structure.getMaxSizeChunks() * 16;
        if (horizontalDistance == settings.maxDistanceVertical()) {
            root.addProperty("max_distance_from_center", horizontalDistance);
        } else {
            JsonObject distance = new JsonObject();
            distance.addProperty("horizontal", horizontalDistance);
            distance.addProperty("vertical", settings.maxDistanceVertical());
            root.add("max_distance_from_center", distance);
        }
        if (settings.projectHeightmap() != VanillaJigsawExportSettings.ProjectHeightmap.NONE) {
            root.addProperty("project_start_to_heightmap", settings.projectHeightmap().serializedName());
        }
        root.addProperty("size", structure.getMaxDepth());
        root.add("spawn_overrides", new JsonObject());
        JsonObject startHeight = new JsonObject();
        startHeight.addProperty("absolute", settings.startHeight());
        root.add("start_height", startHeight);
        root.addProperty("start_pool", poolIdentifier(request, trim(structure.getStartPool())));
        root.addProperty("step", settings.generationStep().serializedName());
        root.addProperty("terrain_adaptation", settings.terrainAdaptation().serializedName());
        root.addProperty("use_expansion_hack", settings.expansionHack());
        return root;
    }

    private JsonObject structureSet(VanillaJigsawExportRequest request) {
        JsonObject structureEntry = new JsonObject();
        structureEntry.addProperty("structure", structureIdentifier(request));
        structureEntry.addProperty("weight", 1);
        JsonArray structures = new JsonArray();
        structures.add(structureEntry);

        VanillaJigsawExportSettings settings = request.settings();
        JsonObject placement = new JsonObject();
        placement.addProperty("type", "minecraft:random_spread");
        placement.addProperty("frequency", settings.frequency());
        placement.addProperty("salt", settings.salt());
        placement.addProperty("separation", settings.separation());
        placement.addProperty("spacing", settings.spacing());
        placement.addProperty("spread_type", settings.spreadType().serializedName());

        JsonObject root = new JsonObject();
        root.add("placement", placement);
        root.add("structures", structures);
        return root;
    }

    private JsonObject templatePool(
            VanillaJigsawExportRequest request,
            CompiledStructureGraph graph,
            IrisJigsawPool pool
    ) {
        JsonObject root = new JsonObject();
        String fallback = trim(pool.getFallback());
        root.addProperty("fallback", fallback.isEmpty() ? "minecraft:empty" : poolIdentifier(request, fallback));
        JsonArray elements = new JsonArray();
        Map<IrisJigsawWorkcellArchetype, PlanarJigsawWorkcellResolver.ResolvedWorkcell> workcells =
                resolvedWorkcells(graph.getStructure());
        if (pool.getPieces() != null) {
            for (IrisJigsawPieceEntry entry : pool.getPieces()) {
                if (!entry.isEmpty()
                        && !pieceEnabled(workcells, graph.getPieces().get(trim(entry.getPiece())))) {
                    continue;
                }
                JsonObject weightedElement = new JsonObject();
                JsonObject element = new JsonObject();
                if (entry.isEmpty()) {
                    element.addProperty("element_type", "minecraft:empty_pool_element");
                } else {
                    element.addProperty("element_type", "minecraft:single_pool_element");
                    element.addProperty("location", templateIdentifier(request, trim(entry.getPiece())));
                    element.addProperty("processors", processorIdentifier(request));
                    element.addProperty("projection", "rigid");
                }
                weightedElement.add("element", element);
                weightedElement.addProperty("weight", entry.getWeight());
                elements.add(weightedElement);
            }
        }
        root.add("elements", elements);
        return root;
    }

    private static Map<IrisJigsawWorkcellArchetype, PlanarJigsawWorkcellResolver.ResolvedWorkcell>
    resolvedWorkcells(IrisStructure structure) {
        return structure.resolvedMode() == IrisJigsawMode.PLANAR_JIGSAW
                ? PlanarJigsawWorkcellResolver.resolve(structure)
                : Map.of();
    }

    private static boolean pieceEnabled(
            Map<IrisJigsawWorkcellArchetype, PlanarJigsawWorkcellResolver.ResolvedWorkcell> workcells,
            IrisJigsawPiece piece
    ) {
        return workcells.isEmpty()
                || piece == null
                || PlanarJigsawWorkcellResolver.workcell(workcells, piece).enabled();
    }

    private void putJson(Map<String, byte[]> resources, String path, JsonObject value) {
        resources.put(path, (GSON.toJson(value) + "\n").getBytes(StandardCharsets.UTF_8));
    }

    private String biomeTagPath(VanillaJigsawExportRequest request) {
        return "data/" + request.namespace() + "/tags/worldgen/biome/" + request.resourcePath() + ".json";
    }

    private String processorPath(VanillaJigsawExportRequest request) {
        return "data/" + request.namespace() + "/worldgen/processor_list/"
                + request.resourcePath() + "/empty.json";
    }

    private String structurePath(VanillaJigsawExportRequest request) {
        return "data/" + request.namespace() + "/worldgen/structure/" + request.resourcePath() + ".json";
    }

    private String structureSetPath(VanillaJigsawExportRequest request) {
        return "data/" + request.namespace() + "/worldgen/structure_set/" + request.resourcePath() + ".json";
    }

    private String poolPath(VanillaJigsawExportRequest request, String poolKey) {
        return "data/" + request.namespace() + "/worldgen/template_pool/"
                + request.resourcePath() + "/pool/" + poolKey + ".json";
    }

    private String templatePath(VanillaJigsawExportRequest request, String pieceKey) {
        return "data/" + request.namespace() + "/structure/"
                + request.resourcePath() + "/piece/" + pieceKey + ".nbt";
    }

    private String structureIdentifier(VanillaJigsawExportRequest request) {
        return request.namespace() + ":" + request.resourcePath();
    }

    private String processorIdentifier(VanillaJigsawExportRequest request) {
        return request.namespace() + ":" + request.resourcePath() + "/empty";
    }

    private String poolIdentifier(VanillaJigsawExportRequest request, String poolKey) {
        return request.namespace() + ":" + request.resourcePath() + "/pool/" + poolKey;
    }

    private String templateIdentifier(VanillaJigsawExportRequest request, String pieceKey) {
        return request.namespace() + ":" + request.resourcePath() + "/piece/" + pieceKey;
    }

    private boolean inside(IrisPosition position, IrisObject object) {
        return position.getX() >= 0 && position.getX() < object.getW()
                && position.getY() >= 0 && position.getY() < object.getH()
                && position.getZ() >= 0 && position.getZ() < object.getD();
    }

    private boolean hasErrors(List<VanillaJigsawExportDiagnostic> diagnostics) {
        for (VanillaJigsawExportDiagnostic diagnostic : diagnostics) {
            if (diagnostic.isBlocking()) {
                return true;
            }
        }
        return false;
    }

    private Compilation rejected(List<VanillaJigsawExportDiagnostic> diagnostics) {
        return new Compilation(Map.of(), List.copyOf(diagnostics));
    }

    private void addError(
            List<VanillaJigsawExportDiagnostic> diagnostics,
            VanillaJigsawExportDiagnostic.Code code,
            String resource,
            String message
    ) {
        diagnostics.add(new VanillaJigsawExportDiagnostic(
                VanillaJigsawExportDiagnostic.Severity.ERROR,
                code,
                resource,
                message));
    }

    private String structureKey(IrisStructure structure) {
        String key = structure.getLoadKey();
        return key == null || key.isBlank() ? "<unloaded>" : key;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    record Compilation(
            Map<String, byte[]> resources,
            List<VanillaJigsawExportDiagnostic> diagnostics
    ) {
        Compilation {
            resources = Map.copyOf(resources);
            diagnostics = List.copyOf(diagnostics);
        }

        boolean hasErrors() {
            for (VanillaJigsawExportDiagnostic diagnostic : diagnostics) {
                if (diagnostic.isBlocking()) {
                    return true;
                }
            }
            return false;
        }
    }
}
