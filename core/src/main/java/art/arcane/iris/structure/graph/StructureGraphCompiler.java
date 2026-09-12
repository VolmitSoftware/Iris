package art.arcane.iris.structure.graph;

import art.arcane.iris.structure.placement.PlacedStructurePiece;
import art.arcane.iris.structure.placement.StructureAssembler;
import art.arcane.iris.pack.value.IrisDirection;
import art.arcane.iris.structure.jigsaw.IrisJigsawBranchFailurePolicy;
import art.arcane.iris.structure.jigsaw.IrisJigsawCompatibility;
import art.arcane.iris.structure.jigsaw.IrisJigsawConnector;
import art.arcane.iris.structure.jigsaw.IrisJigsawMode;
import art.arcane.iris.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.structure.jigsaw.IrisJigsawPieceEntry;
import art.arcane.iris.structure.jigsaw.IrisJigsawPieceRules;
import art.arcane.iris.structure.jigsaw.IrisJigsawPool;
import art.arcane.iris.structure.jigsaw.IrisJigsawThemeSet;
import art.arcane.iris.structure.jigsaw.IrisJigsawWorkcellArchetype;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.pack.value.IrisPosition;
import art.arcane.iris.structure.placement.IrisStructure;
import art.arcane.iris.structure.jigsaw.JigsawJoint;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class StructureGraphCompiler {
    private static final int ASSEMBLY_PIECE_CAP = 512;
    private static final int MAX_DEPTH = 30;
    private static final int MAX_SIZE_CHUNKS = 32;
    private static final int[] NO_ROTATION = {0};
    private static final int[] Y_ROTATIONS = {0, 90, 180, 270};
    private static final List<Long> ASSEMBLY_SEEDS = List.of(0L, 1L, 2L, 3L, 5L, 8L, 13L, 21L);

    private StructureGraphCompiler() {
    }

    public static StructureGraphCompilation compile(IrisStructure structure, StructureGraphResolver resolver) {
        CompilationState state = new CompilationState(
                Objects.requireNonNull(structure), Objects.requireNonNull(resolver));
        state.loadClosure();
        state.detectFallbackCycles();
        state.analyzeReachability();
        state.validatePlanarReachableObjects();
        state.validateRuleFeasibility();
        state.validateRequiredCaps();
        CompiledStructureGraph graph = state.buildGraph();
        StructureGraphCompilation validated = StructureGraphCompilation.builder(graph)
                .diagnostics(state.diagnostics())
                .build();
        List<StructureGraphAssemblySample> samples = state.sampleAssemblies(validated);
        state.reportPieceCapSamples(samples);
        return StructureGraphCompilation.builder(graph)
                .diagnostics(state.diagnostics())
                .assemblySamples(samples)
                .build();
    }

    private static final class CompilationState {
        private final IrisStructure structure;
        private final StructureGraphResolver resolver;
        private final CompiledStructureGraph.Builder graph;
        private final Deque<String> pendingPools = new ArrayDeque<>();
        private final Map<String, List<PoolReference>> poolReferences = new LinkedHashMap<>();
        private final Map<String, List<PoolEntryReference>> poolEntries = new LinkedHashMap<>();
        private final Map<String, List<ConnectorReference>> pieceConnectors = new LinkedHashMap<>();
        private final Map<String, String> pieceObjects = new LinkedHashMap<>();
        private final Set<String> processedPools = new HashSet<>();
        private final Set<String> missingPools = new HashSet<>();
        private final Set<String> attemptedPieces = new HashSet<>();
        private final Set<String> missingPieces = new HashSet<>();
        private final Set<String> attemptedObjects = new HashSet<>();
        private final Set<String> missingObjects = new HashSet<>();
        private final Set<String> reachableEntries = new LinkedHashSet<>();
        private final Set<PieceReachState> reachablePieceStates = new LinkedHashSet<>();
        private final Map<String, Set<String>> reachablePiecesByTheme = new LinkedHashMap<>();
        private final Map<ReachableConnectorKey, List<OrientedConnector>> activeConnectors = new LinkedHashMap<>();
        private final List<StructureGraphDiagnostic> diagnostics = new ArrayList<>();
        private final Set<String> diagnosticKeys = new HashSet<>();
        private Map<IrisJigsawWorkcellArchetype, PlanarJigsawWorkcellResolver.ResolvedWorkcell>
                planarWorkcells = Map.of();

        private CompilationState(IrisStructure structure, StructureGraphResolver resolver) {
            this.structure = structure;
            this.resolver = resolver;
            this.graph = CompiledStructureGraph.builder(structure);
        }

        private void loadClosure() {
            validateStructureLimits();
            String startPool = normalize(structure.getStartPool());
            if (startPool.isEmpty()) {
                addDiagnostic(StructureGraphDiagnostic.Code.MISSING_START_POOL,
                        "Structure '" + structureKey() + "' does not declare a start pool.",
                        "structure:start-pool:empty");
                return;
            }

            requestPool(startPool, new PoolReference(
                    "Structure '" + structureKey() + "' references missing start pool '" + startPool + "'.",
                    StructureGraphDiagnostic.Code.MISSING_START_POOL));
            while (!pendingPools.isEmpty()) {
                processPool(pendingPools.removeFirst());
            }
        }

        private void validateStructureLimits() {
            validateThemes();
            if (structure.getMaxDepth() < 1 || structure.getMaxDepth() > MAX_DEPTH) {
                addDiagnostic(StructureGraphDiagnostic.Code.INVALID_MAX_DEPTH,
                        "Structure '" + structureKey() + "' has maxDepth " + structure.getMaxDepth()
                                + "; it must be between 1 and " + MAX_DEPTH + ".",
                        "structure:max-depth");
            }
            if (structure.getMaxSizeChunks() < 1 || structure.getMaxSizeChunks() > MAX_SIZE_CHUNKS) {
                addDiagnostic(StructureGraphDiagnostic.Code.INVALID_MAX_SIZE,
                        "Structure '" + structureKey() + "' has maxSizeChunks " + structure.getMaxSizeChunks()
                                + "; it must be between 1 and " + MAX_SIZE_CHUNKS + ".",
                        "structure:max-size");
            }
            if (structure.resolvedMode() != IrisJigsawMode.PLANAR_JIGSAW) {
                return;
            }
            try {
                planarWorkcells = PlanarJigsawWorkcellResolver.resolve(structure);
            } catch (IllegalArgumentException exception) {
                boolean legacy = structure.getPlanarWorkcells() == null || structure.getPlanarWorkcells().isEmpty();
                addDiagnostic(
                        legacy
                                ? StructureGraphDiagnostic.Code.INVALID_PLANAR_CELL_SIZE
                                : StructureGraphDiagnostic.Code.INVALID_PLANAR_WORKCELL,
                        "Structure '" + structureKey() + "' has invalid planar workcells: "
                                + exception.getMessage() + ".",
                        "structure:planar-workcells");
            }
        }

        private void validateThemes() {
            KList<IrisJigsawThemeSet> themeSets = structure.getThemeSets();
            if (themeSets == null) {
                addDiagnostic(StructureGraphDiagnostic.Code.INVALID_THEME,
                        "Structure '" + structureKey() + "' declares a null themeSets list.",
                        "structure:themes:null");
                return;
            }
            Set<String> keys = new LinkedHashSet<>();
            for (int index = 0; index < themeSets.size(); index++) {
                IrisJigsawThemeSet theme = themeSets.get(index);
                if (theme == null) {
                    addDiagnostic(StructureGraphDiagnostic.Code.INVALID_THEME,
                            "Structure '" + structureKey() + "' themeSets[" + index + "] is null.",
                            "structure:theme:null:" + index);
                    continue;
                }
                String key = normalize(theme.getKey());
                if (key.isEmpty() || !key.equals(theme.getKey())) {
                    addDiagnostic(StructureGraphDiagnostic.Code.INVALID_THEME,
                            "Structure '" + structureKey() + "' themeSets[" + index
                                    + "] must declare a non-blank, whitespace-normalized key.",
                            "structure:theme:key:" + index);
                } else if (!keys.add(key)) {
                    addDiagnostic(StructureGraphDiagnostic.Code.INVALID_THEME,
                            "Structure '" + structureKey() + "' declares duplicate theme key '" + key + "'.",
                            "structure:theme:duplicate:" + key);
                }
                if (theme.getWeight() <= 0) {
                    addDiagnostic(StructureGraphDiagnostic.Code.INVALID_WEIGHT,
                            "Structure '" + structureKey() + "' theme '" + key
                                    + "' has non-positive weight " + theme.getWeight() + ".",
                            "structure:theme:weight:" + index);
                }
            }
            if (!themeSets.isEmpty()
                    && structure.resolvedCompatibility() == IrisJigsawCompatibility.VANILLA_PORTABLE) {
                addDiagnostic(StructureGraphDiagnostic.Code.NON_PORTABLE_METADATA,
                        "Structure '" + structureKey()
                                + "' declares coherent Iris theme sets in a VANILLA_PORTABLE graph.",
                        "structure:portable:themes");
            }
            if (structure.isRequireCaps()
                    && structure.resolvedCompatibility() == IrisJigsawCompatibility.VANILLA_PORTABLE) {
                addDiagnostic(StructureGraphDiagnostic.Code.NON_PORTABLE_METADATA,
                        "Structure '" + structureKey()
                                + "' requires physical terminal caps, which vanilla jigsaws cannot enforce.",
                        "structure:portable:require-caps");
            }
            if (structure.resolvedCompatibility() == IrisJigsawCompatibility.VANILLA_PORTABLE
                    && structure.resolvedBranchFailurePolicy()
                    != IrisJigsawBranchFailurePolicy.TERMINATE_BRANCH) {
                addDiagnostic(StructureGraphDiagnostic.Code.NON_PORTABLE_METADATA,
                        "Structure '" + structureKey()
                                + "' fails unresolved optional connector branches, which vanilla jigsaws terminate.",
                        "structure:portable:branch-failure-policy");
            }
        }

        private void requestPool(String rawKey, PoolReference reference) {
            String key = normalize(rawKey);
            if (key.isEmpty()) {
                return;
            }
            poolReferences.computeIfAbsent(key, ignored -> new ArrayList<>()).add(reference);
            if (missingPools.contains(key)) {
                addDiagnostic(reference.code(), reference.message(), "missing-pool:" + key + ":" + reference.message());
                return;
            }
            if (!processedPools.contains(key) && !pendingPools.contains(key)) {
                pendingPools.addLast(key);
            }
        }

        private void processPool(String key) {
            if (!processedPools.add(key)) {
                return;
            }
            IrisJigsawPool pool = resolver.loadPool(key);
            if (pool == null) {
                missingPools.add(key);
                for (PoolReference reference : poolReferences.getOrDefault(key, List.of())) {
                    addDiagnostic(reference.code(), reference.message(),
                            "missing-pool:" + key + ":" + reference.message());
                }
                return;
            }

            graph.pools().put(key, pool);
            List<PoolEntryReference> entries = new ArrayList<>();
            poolEntries.put(key, entries);
            KList<IrisJigsawPieceEntry> configuredEntries = pool.getPieces();
            if (configuredEntries == null || configuredEntries.isEmpty()) {
                if (key.equals(normalize(structure.getStartPool()))) {
                    addDiagnostic(StructureGraphDiagnostic.Code.EMPTY_START_POOL,
                            "Jigsaw pool '" + key + "' is empty.",
                            "empty-pool:" + key);
                }
            } else {
                for (int index = 0; index < configuredEntries.size(); index++) {
                    scanPoolEntry(key, index, configuredEntries.get(index), entries);
                }
            }

            String fallback = JigsawPoolSelection.directFallbackKey(pool);
            if (pool.isMandatoryFallback() && fallback.isEmpty()) {
                addDiagnostic(StructureGraphDiagnostic.Code.MISSING_REQUIRED_FALLBACK,
                        "Jigsaw pool '" + key
                                + "' requires a direct fallback but does not declare one.",
                        "pool:required-fallback:" + key);
            }
            if (pool.isMandatoryFallback()
                    && structure.resolvedCompatibility() == IrisJigsawCompatibility.VANILLA_PORTABLE) {
                addDiagnostic(StructureGraphDiagnostic.Code.NON_PORTABLE_METADATA,
                        "Jigsaw pool '" + key
                                + "' declares mandatoryFallback, which vanilla jigsaws cannot enforce.",
                        "pool:portable:mandatory-fallback:" + key);
            }
            if (!fallback.isEmpty()) {
                requestPool(fallback, new PoolReference(
                        "Jigsaw pool '" + key + "' references missing fallback pool '" + fallback + "'.",
                        StructureGraphDiagnostic.Code.MISSING_POOL));
            }
        }

        private void scanPoolEntry(String poolKey, int index, IrisJigsawPieceEntry entry,
                                   List<PoolEntryReference> entries) {
            if (entry == null) {
                addDiagnostic(StructureGraphDiagnostic.Code.INVALID_POOL_ENTRY,
                        "Jigsaw pool '" + poolKey + "' pieces[" + index + "] is null.",
                        "pool-entry:null:" + poolKey + ":" + index);
                return;
            }

            String pieceKey = normalize(entry.getPiece());
            PoolEntryReference entryReference = new PoolEntryReference(
                    poolKey, index, pieceKey, entry.getWeight(), entry.getChance(), entry.isEmpty());
            entries.add(entryReference);
            if (entry.getWeight() <= 0) {
                addDiagnostic(StructureGraphDiagnostic.Code.INVALID_WEIGHT,
                        "Jigsaw pool '" + poolKey + "' pieces[" + index + "] has non-positive weight "
                                + entry.getWeight() + ".",
                        "pool-entry:weight:" + poolKey + ":" + index);
            }
            if (!Double.isFinite(entry.getChance()) || entry.getChance() < 0D || entry.getChance() > 1D) {
                addDiagnostic(StructureGraphDiagnostic.Code.INVALID_CHANCE,
                        "Jigsaw pool '" + poolKey + "' pieces[" + index + "] has chance "
                                + entry.getChance() + "; it must be finite and within 0..1.",
                        "pool-entry:chance:" + poolKey + ":" + index);
            }
            if (entry.getChance() != 1D
                    && structure.resolvedCompatibility() == IrisJigsawCompatibility.VANILLA_PORTABLE) {
                addDiagnostic(StructureGraphDiagnostic.Code.NON_PORTABLE_METADATA,
                        "Jigsaw pool '" + poolKey + "' pieces[" + index
                                + "] declares an Iris chance gate in a VANILLA_PORTABLE graph.",
                        "pool-entry:portable-chance:" + poolKey + ":" + index);
            }
            if (entry.isEmpty()) {
                if (!pieceKey.isEmpty()) {
                    addDiagnostic(StructureGraphDiagnostic.Code.INVALID_POOL_ENTRY,
                            "Jigsaw pool '" + poolKey + "' pieces[" + index
                                    + "] declares both empty=true and a piece.",
                            "pool-entry:ambiguous-empty:" + poolKey + ":" + index);
                }
                return;
            }
            if (pieceKey.isEmpty()) {
                addDiagnostic(StructureGraphDiagnostic.Code.MISSING_PIECE,
                        "Jigsaw pool '" + poolKey + "' pieces[" + index + "] does not declare a piece.",
                        "pool-entry:piece-empty:" + poolKey + ":" + index);
                return;
            }
            loadPiece(pieceKey, "Jigsaw pool '" + poolKey + "' pieces[" + index
                    + "] references missing piece '" + pieceKey + "'.");
        }

        private void loadPiece(String key, String missingMessage) {
            if (missingPieces.contains(key)) {
                addDiagnostic(StructureGraphDiagnostic.Code.MISSING_PIECE, missingMessage,
                        "missing-piece:" + key + ":" + missingMessage);
                return;
            }
            if (!attemptedPieces.add(key)) {
                return;
            }

            IrisJigsawPiece piece = resolver.loadPiece(key);
            if (piece == null) {
                missingPieces.add(key);
                addDiagnostic(StructureGraphDiagnostic.Code.MISSING_PIECE, missingMessage,
                        "missing-piece:" + key + ":" + missingMessage);
                return;
            }

            graph.pieces().put(key, piece);
            IrisObject object = loadPieceObject(key, piece);
            validatePieceMetadata(key, piece);
            scanConnectors(key, piece, object);
        }

        private void validatePieceMetadata(String pieceKey, IrisJigsawPiece piece) {
            KList<String> themes = piece.getThemes();
            if (themes == null) {
                addDiagnostic(StructureGraphDiagnostic.Code.INVALID_THEME,
                        "Jigsaw piece '" + pieceKey + "' declares a null themes list.",
                        "piece:themes:null:" + pieceKey);
            } else {
                Set<String> declaredThemes = declaredThemeKeys();
                Set<String> seenThemes = new LinkedHashSet<>();
                for (int index = 0; index < themes.size(); index++) {
                    String configured = themes.get(index);
                    String theme = normalize(configured);
                    if (theme.isEmpty() || !theme.equals(configured)) {
                        addDiagnostic(StructureGraphDiagnostic.Code.INVALID_THEME,
                                "Jigsaw piece '" + pieceKey + "' themes[" + index
                                        + "] must be non-blank and whitespace-normalized.",
                                "piece:theme:key:" + pieceKey + ":" + index);
                    } else if (!seenThemes.add(theme)) {
                        addDiagnostic(StructureGraphDiagnostic.Code.INVALID_THEME,
                                "Jigsaw piece '" + pieceKey + "' declares duplicate theme '" + theme + "'.",
                                "piece:theme:duplicate:" + pieceKey + ":" + theme);
                    } else if (!declaredThemes.contains(theme)) {
                        addDiagnostic(StructureGraphDiagnostic.Code.INVALID_THEME,
                                "Jigsaw piece '" + pieceKey + "' references undeclared theme '" + theme + "'.",
                                "piece:theme:undeclared:" + pieceKey + ":" + theme);
                    }
                }
            }

            IrisJigsawPieceRules rules = piece.resolvedRules();
            boolean validDepths = rules.getMinimumDepth() >= 0
                    && rules.getMaximumDepth() >= rules.getMinimumDepth()
                    && rules.getMaximumDepth() <= MAX_DEPTH;
            boolean validPlacements = rules.getMinimumPlacements() >= 0
                    && rules.getMaximumPlacements() >= 0
                    && rules.getMinimumPlacements() <= ASSEMBLY_PIECE_CAP
                    && rules.getMaximumPlacements() <= ASSEMBLY_PIECE_CAP
                    && (rules.getMaximumPlacements() == 0
                    || rules.getMinimumPlacements() <= rules.getMaximumPlacements());
            if (!validDepths || !validPlacements) {
                addDiagnostic(StructureGraphDiagnostic.Code.INVALID_PIECE_RULE,
                        "Jigsaw piece '" + pieceKey
                                + "' has invalid depth or placement-count rules.",
                        "piece:rules:" + pieceKey);
            }
            if (structure.resolvedCompatibility() == IrisJigsawCompatibility.VANILLA_PORTABLE
                    && (themes != null && !themes.isEmpty() || !defaultRules(rules))) {
                addDiagnostic(StructureGraphDiagnostic.Code.NON_PORTABLE_METADATA,
                        "Jigsaw piece '" + pieceKey
                                + "' declares Iris theme or placement rules in a VANILLA_PORTABLE graph.",
                        "piece:portable:metadata:" + pieceKey);
            }
        }

        private Set<String> declaredThemeKeys() {
            Set<String> themes = new LinkedHashSet<>();
            if (structure.getThemeSets() == null) {
                return themes;
            }
            for (IrisJigsawThemeSet theme : structure.getThemeSets()) {
                if (theme != null && !normalize(theme.getKey()).isEmpty()) {
                    themes.add(normalize(theme.getKey()));
                }
            }
            return themes;
        }

        private boolean defaultRules(IrisJigsawPieceRules rules) {
            return rules.getMinimumDepth() == 0
                    && rules.getMaximumDepth() == MAX_DEPTH
                    && rules.getMinimumPlacements() == 0
                    && rules.getMaximumPlacements() == 0
                    && !rules.isTerminal();
        }

        private IrisObject loadPieceObject(String pieceKey, IrisJigsawPiece piece) {
            String objectKey = normalize(piece.getObject());
            pieceObjects.put(pieceKey, objectKey);
            if (objectKey.isEmpty()) {
                addDiagnostic(StructureGraphDiagnostic.Code.MISSING_OBJECT,
                        "Jigsaw piece '" + pieceKey + "' does not declare an object.",
                        "piece-object:empty:" + pieceKey);
                return null;
            }
            if (missingObjects.contains(objectKey)) {
                addDiagnostic(StructureGraphDiagnostic.Code.MISSING_OBJECT,
                        "Jigsaw piece '" + pieceKey + "' references missing object '" + objectKey + "'.",
                        "missing-object:" + objectKey + ":" + pieceKey);
                return null;
            }
            if (!attemptedObjects.add(objectKey)) {
                return graph.objects().get(objectKey);
            }

            IrisObject object = resolver.loadObject(objectKey);
            if (object == null) {
                missingObjects.add(objectKey);
                addDiagnostic(StructureGraphDiagnostic.Code.MISSING_OBJECT,
                        "Jigsaw piece '" + pieceKey + "' references missing object '" + objectKey + "'.",
                        "missing-object:" + objectKey + ":" + pieceKey);
                return null;
            }
            graph.objects().put(objectKey, object);
            if (object.getW() < 1 || object.getH() < 1 || object.getD() < 1) {
                addDiagnostic(StructureGraphDiagnostic.Code.INVALID_OBJECT_BOUNDS,
                        "Object '" + objectKey + "' used by jigsaw piece '" + pieceKey
                                + "' has invalid dimensions " + object.getW() + "x" + object.getH() + "x"
                                + object.getD() + ".",
                        "object-bounds:" + objectKey);
            }
            return object;
        }

        private void scanConnectors(String pieceKey, IrisJigsawPiece piece, IrisObject object) {
            List<ConnectorReference> references = new ArrayList<>();
            pieceConnectors.put(pieceKey, references);
            KList<IrisJigsawConnector> connectors = piece.getConnectors();
            if (connectors == null) {
                addDiagnostic(StructureGraphDiagnostic.Code.INVALID_CONNECTOR,
                        "Jigsaw piece '" + pieceKey + "' declares a null connector list.",
                        "connector-list:null:" + pieceKey);
                return;
            }
            for (int index = 0; index < connectors.size(); index++) {
                IrisJigsawConnector connector = connectors.get(index);
                if (connector == null) {
                    addDiagnostic(StructureGraphDiagnostic.Code.INVALID_CONNECTOR,
                            "Jigsaw piece '" + pieceKey + "' connectors[" + index + "] is null.",
                            "connector:null:" + pieceKey + ":" + index);
                    continue;
                }

                boolean validDirection = connector.getDirection() != null;
                if (!validDirection) {
                    addDiagnostic(StructureGraphDiagnostic.Code.INVALID_CONNECTOR_DIRECTION,
                            "Jigsaw piece '" + pieceKey + "' connectors[" + index
                                    + "] does not declare a direction.",
                            "connector:direction:" + pieceKey + ":" + index);
                }
                boolean validTop = connector.getTop() != null;
                if (!validTop) {
                    addDiagnostic(StructureGraphDiagnostic.Code.INVALID_CONNECTOR_DIRECTION,
                            "Jigsaw piece '" + pieceKey + "' connectors[" + index
                                    + "] does not declare a top direction.",
                            "connector:top:" + pieceKey + ":" + index);
                }
                boolean validJoint = connector.getJoint() != null;
                if (!validJoint) {
                    addDiagnostic(StructureGraphDiagnostic.Code.INVALID_CONNECTOR,
                            "Jigsaw piece '" + pieceKey + "' connectors[" + index
                                    + "] does not declare a joint type.",
                            "connector:joint:" + pieceKey + ":" + index);
                }
                boolean validPosition = isValidPosition(connector.getPosition(), object);
                if (connector.getPosition() == null || object != null && !validPosition) {
                    addDiagnostic(StructureGraphDiagnostic.Code.INVALID_CONNECTOR_POSITION,
                            invalidPositionMessage(pieceKey, index, connector.getPosition(), object),
                            "connector:position:" + pieceKey + ":" + index);
                }

                boolean validNames = connector.getName() != null && connector.getTargetName() != null;
                if (!validNames) {
                    addDiagnostic(StructureGraphDiagnostic.Code.INVALID_CONNECTOR,
                            "Jigsaw piece '" + pieceKey + "' connectors[" + index
                                    + "] must declare non-null name and targetName values.",
                            "connector:names:" + pieceKey + ":" + index);
                }

                boolean validMetadata = connector.getChannel() != null
                        && connector.getFinalState() != null
                        && !connector.getFinalState().isBlank();
                if (!validMetadata) {
                    addDiagnostic(StructureGraphDiagnostic.Code.INVALID_CONNECTOR_METADATA,
                            "Jigsaw piece '" + pieceKey + "' connectors[" + index
                                    + "] must declare a channel and non-blank finalState.",
                            "connector:metadata:" + pieceKey + ":" + index);
                }
                if (structure.resolvedCompatibility() == IrisJigsawCompatibility.VANILLA_PORTABLE
                        && connector.getChannel() != null && !connector.getChannel().isBlank()) {
                    addDiagnostic(StructureGraphDiagnostic.Code.NON_PORTABLE_CONNECTOR,
                            "Jigsaw piece '" + pieceKey + "' connectors[" + index
                                    + "] declares Iris-only channel '" + connector.getChannel()
                                    + "' in a VANILLA_PORTABLE structure.",
                            "connector:portable-channel:" + pieceKey + ":" + index);
                }

                boolean validPlanarContract = validatePlanarConnector(
                        pieceKey, index, connector, object, validDirection, validTop, validPosition);

                String poolKey = normalize(connector.getPool());
                if (poolKey.isEmpty()) {
                    addDiagnostic(StructureGraphDiagnostic.Code.MISSING_CONNECTOR_POOL,
                            "Jigsaw piece '" + pieceKey + "' connectors[" + index
                                    + "] does not declare a target pool.",
                            "connector:pool-empty:" + pieceKey + ":" + index);
                } else {
                    requestPool(poolKey, new PoolReference(
                            "Jigsaw piece '" + pieceKey + "' connectors[" + index
                                    + "] references missing pool '" + poolKey + "'.",
                            StructureGraphDiagnostic.Code.MISSING_POOL));
                }
                references.add(new ConnectorReference(
                        pieceKey, index, connector, validPosition, validDirection, validTop, validJoint, validNames,
                        validMetadata, validPlanarContract));
            }
        }

        private boolean validatePlanarConnector(String pieceKey, int index, IrisJigsawConnector connector,
                                                IrisObject object, boolean validDirection, boolean validTop,
                                                boolean validPosition) {
            if (structure.resolvedMode() != IrisJigsawMode.PLANAR_JIGSAW) {
                return true;
            }

            boolean valid = true;
            if (validDirection && connector.getDirection().isVertical()) {
                addDiagnostic(StructureGraphDiagnostic.Code.INVALID_PLANAR_CONNECTOR,
                        "Jigsaw piece '" + pieceKey + "' connectors[" + index + "] uses vertical direction "
                                + connector.getDirection().name()
                                + "; planar connectors must face north, east, south,"
                                + " or west.",
                        "connector:planar-direction:" + pieceKey + ":" + index);
                valid = false;
            }
            if (validTop && connector.getTop() != IrisDirection.UP_POSITIVE_Y) {
                addDiagnostic(StructureGraphDiagnostic.Code.INVALID_PLANAR_CONNECTOR,
                        "Jigsaw piece '" + pieceKey + "' connectors[" + index + "] has top "
                                + connector.getTop().name()
                                + "; planar connectors must use UP_POSITIVE_Y to keep"
                                + " connected cells on one Y level.",
                        "connector:planar-top:" + pieceKey + ":" + index);
                valid = false;
            }

            if (!validDirection || connector.getDirection().isVertical()
                    || object == null || !validPosition) {
                return valid;
            }
            IrisPosition objectSize = new IrisPosition(object.getW(), object.getH(), object.getD());
            IrisPosition expected = IrisJigsawConnector.canonicalPlanarPosition(
                    objectSize, connector.getDirection());
            if (!expected.equals(connector.getPosition())) {
                addDiagnostic(StructureGraphDiagnostic.Code.INVALID_PLANAR_CONNECTOR,
                        "Jigsaw piece '" + pieceKey + "' connectors[" + index + "] is at "
                                + connector.getPosition() + "; the canonical " + connector.getDirection().name()
                                + " face-center socket for object dimensions " + objectSize + " is " + expected + ".",
                        "connector:planar-position:" + pieceKey + ":" + index);
                valid = false;
            }
            return valid;
        }

        private String invalidPositionMessage(String pieceKey, int index, IrisPosition position, IrisObject object) {
            if (position == null) {
                return "Jigsaw piece '" + pieceKey + "' connectors[" + index
                        + "] does not declare a position.";
            }
            if (object == null) {
                return "Jigsaw piece '" + pieceKey + "' connectors[" + index
                        + "] position " + position + " cannot be checked because its object is missing.";
            }
            return "Jigsaw piece '" + pieceKey + "' connectors[" + index + "] position " + position
                    + " is outside object bounds 0.." + (object.getW() - 1) + ", 0.." + (object.getH() - 1)
                    + ", 0.." + (object.getD() - 1) + ".";
        }

        private boolean isValidPosition(IrisPosition position, IrisObject object) {
            if (position == null || object == null) {
                return false;
            }
            return position.getX() >= 0 && position.getX() < object.getW()
                    && position.getY() >= 0 && position.getY() < object.getH()
                    && position.getZ() >= 0 && position.getZ() < object.getD();
        }

        private void detectFallbackCycles() {
            Map<String, Integer> states = new HashMap<>();
            for (String poolKey : graph.pools().keySet()) {
                detectFallbackCycle(poolKey, states);
            }
        }

        private void detectFallbackCycle(String startPool, Map<String, Integer> states) {
            if (states.getOrDefault(startPool, 0) == 2) {
                return;
            }
            List<String> path = new ArrayList<>();
            Map<String, Integer> pathIndexes = new HashMap<>();
            String poolKey = startPool;
            while (!poolKey.isEmpty() && graph.pools().containsKey(poolKey)
                    && states.getOrDefault(poolKey, 0) != 2) {
                Integer cycleStart = pathIndexes.get(poolKey);
                if (cycleStart != null) {
                    List<String> cycle = new ArrayList<>(path.subList(cycleStart, path.size()));
                    cycle.add(poolKey);
                    String cyclePath = String.join(" -> ", cycle);
                    addDiagnostic(StructureGraphDiagnostic.Code.FALLBACK_CYCLE,
                            "Jigsaw pool fallback cycle detected: " + cyclePath + ".",
                            "fallback-cycle:" + cyclePath);
                    break;
                }
                pathIndexes.put(poolKey, path.size());
                path.add(poolKey);
                states.put(poolKey, 1);
                IrisJigsawPool pool = graph.pools().get(poolKey);
                poolKey = pool == null ? "" : JigsawPoolSelection.directFallbackKey(pool);
            }
            for (String visitedPool : path) {
                states.put(visitedPool, 2);
            }
        }

        private void analyzeReachability() {
            String startPool = normalize(structure.getStartPool());
            if (!graph.pools().containsKey(startPool)) {
                return;
            }
            graph.reachablePools().add(startPool);
            for (String theme : reachabilityThemes()) {
                analyzeThemeReachability(startPool, theme);
            }
            reportUnmatchedReachableConnectors();
            reportUnreachableResources();
        }

        private void analyzeThemeReachability(String startPool, String theme) {
            Deque<PieceReachState> pendingPieces = new ArrayDeque<>();
            boolean viableStart = false;
            for (PoolEntryReference entry : poolEntries.getOrDefault(startPool, List.of())) {
                if (!entryIsViable(entry, theme, 0)) {
                    continue;
                }
                viableStart = true;
                markReachableEntry(entry, theme);
                if (entry.empty()) {
                    continue;
                }
                IrisJigsawPiece piece = graph.pieces().get(entry.pieceKey());
                if (piece == null) {
                    continue;
                }
                for (int rotation : rotationsFor(piece)) {
                    markReachablePieceState(
                            new PieceReachState(entry.pieceKey(), -1, rotation, 0, theme),
                            pendingPieces);
                }
            }
            if (!viableStart) {
                boolean disabledStart = false;
                for (PoolEntryReference entry : poolEntries.getOrDefault(startPool, List.of())) {
                    if (!entry.empty() && entryIsResolvable(entry) && !pieceEnabled(entry.pieceKey())) {
                        disabledStart = true;
                        break;
                    }
                }
                if (disabledStart) {
                    addDiagnostic(StructureGraphDiagnostic.Code.NO_ENABLED_START_PIECE,
                            "Structure '" + structureKey()
                                    + "' has no enabled planar workcell represented by a viable start entry.",
                            "structure:no-enabled-start:" + theme);
                } else if (!theme.isEmpty()) {
                    addDiagnostic(StructureGraphDiagnostic.Code.UNSATISFIABLE_PIECE_RULE,
                            "Structure '" + structureKey() + "' theme '" + theme
                                    + "' has no positive-chance start entry eligible at depth zero.",
                            "structure:no-theme-start:" + theme);
                } else {
                    addDiagnostic(StructureGraphDiagnostic.Code.NO_VIABLE_START_PIECE,
                            "Structure '" + structureKey() + "' has no start-pool entry with a positive weight,"
                                    + " positive chance, resolvable piece, and resolvable object.",
                            "structure:no-viable-start");
                }
            }

            while (!pendingPieces.isEmpty()) {
                PieceReachState pieceState = pendingPieces.removeFirst();
                for (ConnectorReference source : pieceConnectors.getOrDefault(pieceState.pieceKey(), List.of())) {
                    if (source.index() == pieceState.skippedConnectorIndex() || !source.canSource()) {
                        continue;
                    }
                    OrientedConnector orientedSource = new OrientedConnector(source, pieceState.rotation());
                    ReachableConnectorKey connectorKey = new ReachableConnectorKey(
                            pieceState.theme(), source.id(), pieceState.depth());
                    activeConnectors.computeIfAbsent(connectorKey, ignored -> new ArrayList<>())
                            .add(orientedSource);
                    boolean includePrimary = pieceState.depth() < Math.max(1, structure.getMaxDepth());
                    int candidateDepth = Math.min(
                            pieceState.depth() + 1,
                            Math.max(1, structure.getMaxDepth()));
                    List<String> candidatePools = candidatePools(
                            normalize(source.connector().getPool()), includePrimary);
                    for (String candidatePool : candidatePools) {
                        graph.reachablePools().add(candidatePool);
                        for (PoolEntryReference entry : poolEntries.getOrDefault(candidatePool, List.of())) {
                            if (!entryIsViable(entry, pieceState.theme(), candidateDepth)) {
                                continue;
                            }
                            if (entry.empty()) {
                                markReachableEntry(entry, pieceState.theme());
                                continue;
                            }
                            List<ConnectorMatch> compatible = findCompatibleConnectors(
                                    orientedSource, entry.pieceKey());
                            if (!compatible.isEmpty()) {
                                markReachableEntry(entry, pieceState.theme());
                                if (!includePrimary) {
                                    continue;
                                }
                                for (ConnectorMatch target : compatible) {
                                    markReachablePieceState(
                                            new PieceReachState(entry.pieceKey(), target.connector().index(),
                                                    target.rotation(), candidateDepth, pieceState.theme()),
                                            pendingPieces);
                                }
                            }
                        }
                    }
                }
            }
        }

        private List<String> reachabilityThemes() {
            if (structure.getThemeSets() == null || structure.getThemeSets().isEmpty()) {
                return List.of("");
            }
            List<String> themes = new ArrayList<>(structure.getThemeSets().size());
            for (IrisJigsawThemeSet theme : structure.getThemeSets()) {
                if (theme != null && !normalize(theme.getKey()).isEmpty()) {
                    themes.add(normalize(theme.getKey()));
                }
            }
            return themes.isEmpty() ? List.of("") : List.copyOf(themes);
        }

        private void validatePlanarReachableObjects() {
            if (structure.resolvedMode() != IrisJigsawMode.PLANAR_JIGSAW
                    || planarWorkcells.isEmpty()) {
                return;
            }
            for (String pieceKey : graph.reachablePieces()) {
                IrisJigsawPiece piece = graph.pieces().get(pieceKey);
                String objectKey = pieceObjects.get(pieceKey);
                IrisObject object = graph.objects().get(objectKey);
                if (piece == null || object == null) {
                    continue;
                }
                PlanarJigsawWorkcellResolver.ResolvedWorkcell workcell =
                        PlanarJigsawWorkcellResolver.workcell(planarWorkcells, piece);
                IrisPosition dimensions = PlanarJigsawWorkcellResolver.canonicalDimensions(piece, object);
                if (workcell.contains(dimensions)) {
                    continue;
                }
                addDiagnostic(StructureGraphDiagnostic.Code.INVALID_PLANAR_OBJECT_SIZE,
                        "Object '" + objectKey + "' used by reachable planar piece '" + pieceKey
                                + "' is " + object.getW() + "x" + object.getH() + "x" + object.getD()
                                + " and canonically occupies " + dimensions.getX() + "x" + dimensions.getY()
                                + "x" + dimensions.getZ() + "; it does not fit " + workcell.archetype()
                                + " workcell " + workcell.width() + "x" + workcell.height() + "x"
                                + workcell.depth() + ".",
                        "object:planar-size:" + pieceKey);
            }
        }

        private void markReachableEntry(PoolEntryReference entry, String theme) {
            reachableEntries.add(entry.id());
            if (!entry.empty()) {
                graph.reachablePieces().add(entry.pieceKey());
                reachablePiecesByTheme.computeIfAbsent(theme, ignored -> new LinkedHashSet<>())
                        .add(entry.pieceKey());
            }
        }

        private void markReachablePieceState(PieceReachState state, Deque<PieceReachState> pendingPieces) {
            if (!graph.reachablePieces().contains(state.pieceKey())) {
                return;
            }
            IrisJigsawPiece piece = graph.pieces().get(state.pieceKey());
            if (piece == null || piece.resolvedRules().isTerminal()) {
                return;
            }
            if (reachablePieceStates.add(state)) {
                pendingPieces.addLast(state);
            }
        }

        private boolean entryIsViable(PoolEntryReference entry, String theme, int depth) {
            if (!entryIsResolvable(entry)) {
                return false;
            }
            if (entry.empty()) {
                return true;
            }
            IrisJigsawPiece piece = graph.pieces().get(entry.pieceKey());
            return piece != null
                    && pieceEnabled(entry.pieceKey())
                    && JigsawPoolSelection.pieceEligible(piece, theme, depth, 0);
        }

        private boolean entryIsResolvable(PoolEntryReference entry) {
            if (entry.weight() <= 0 || !Double.isFinite(entry.chance()) || entry.chance() <= 0D
                    || entry.chance() > 1D) {
                return false;
            }
            if (entry.empty()) {
                return entry.pieceKey().isEmpty();
            }
            if (entry.pieceKey().isEmpty() || !graph.pieces().containsKey(entry.pieceKey())) {
                return false;
            }
            String objectKey = pieceObjects.getOrDefault(entry.pieceKey(), "");
            return !objectKey.isEmpty() && graph.objects().containsKey(objectKey);
        }

        private boolean entryIsViableForAnyTheme(PoolEntryReference entry) {
            for (String theme : reachabilityThemes()) {
                for (int depth = 0; depth <= Math.max(1, structure.getMaxDepth()); depth++) {
                    if (entryIsViable(entry, theme, depth)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean pieceEnabled(String pieceKey) {
            if (structure.resolvedMode() != IrisJigsawMode.PLANAR_JIGSAW || planarWorkcells.isEmpty()) {
                return true;
            }
            IrisJigsawPiece piece = graph.pieces().get(pieceKey);
            return piece == null || PlanarJigsawWorkcellResolver.workcell(planarWorkcells, piece).enabled();
        }

        private void reportUnmatchedReachableConnectors() {
            for (Map.Entry<ReachableConnectorKey, List<OrientedConnector>> active : activeConnectors.entrySet()) {
                ReachableConnectorKey connectorKey = active.getKey();
                List<OrientedConnector> orientations = active.getValue();
                OrientedConnector representative = orientations.getFirst();
                boolean includePrimary = connectorKey.depth() < Math.max(1, structure.getMaxDepth());
                int candidateDepth = Math.min(
                        connectorKey.depth() + 1,
                        Math.max(1, structure.getMaxDepth()));
                List<String> candidates = candidatePools(
                        normalize(representative.definition().getPool()), includePrimary);
                boolean hasViableEntry = false;
                boolean hasCompatibleEntry = false;
                for (String candidatePool : candidates) {
                    for (PoolEntryReference entry : poolEntries.getOrDefault(candidatePool, List.of())) {
                        if (!entryIsViable(entry, connectorKey.theme(), candidateDepth)) {
                            continue;
                        }
                        hasViableEntry = true;
                        if (entry.empty()) {
                            hasCompatibleEntry = true;
                            break;
                        }
                        for (OrientedConnector source : orientations) {
                            if (!findCompatibleConnectors(source, entry.pieceKey()).isEmpty()) {
                                hasCompatibleEntry = true;
                                break;
                            }
                        }
                        if (hasCompatibleEntry) {
                            break;
                        }
                    }
                    if (hasCompatibleEntry) {
                        break;
                    }
                }
                if (hasViableEntry && !hasCompatibleEntry) {
                    addDiagnostic(StructureGraphDiagnostic.Code.NO_COMPATIBLE_CONNECTOR,
                            "Jigsaw piece '" + representative.connector().pieceKey() + "' connectors["
                                    + representative.connector().index() + "] targets pool '"
                                    + normalize(representative.definition().getPool())
                                    + "', but no reachable candidate exposes the requested target name with a"
                                    + " compatible direction for theme '" + themeLabel(connectorKey.theme()) + "'.",
                            "connector:no-match:" + representative.connector().pieceKey() + ":"
                                    + representative.connector().index() + ":" + connectorKey.theme()
                                    + ":" + connectorKey.depth());
                }
            }
        }

        private void reportUnreachableResources() {
            for (String poolKey : graph.pools().keySet()) {
                if (!graph.reachablePools().contains(poolKey)) {
                    addDiagnostic(StructureGraphDiagnostic.Code.UNREACHABLE_POOL,
                            "Jigsaw pool '" + poolKey + "' is in the structure closure but cannot be reached"
                                    + " from the start pool.",
                            "unreachable-pool:" + poolKey);
                    continue;
                }
                for (PoolEntryReference entry : poolEntries.getOrDefault(poolKey, List.of())) {
                    if (!entry.empty() && entryIsViableForAnyTheme(entry)
                            && !reachableEntries.contains(entry.id())) {
                        addDiagnostic(StructureGraphDiagnostic.Code.UNREACHABLE_PIECE,
                                "Jigsaw pool '" + poolKey + "' pieces[" + entry.index() + "] references piece '"
                                        + entry.pieceKey() + "', but no reachable connector can attach it.",
                                "unreachable-piece:" + entry.id());
                    }
                }
            }
        }

        private String themeLabel(String theme) {
            return theme == null || theme.isEmpty() ? "<unthemed>" : theme;
        }

        private List<ConnectorMatch> findCompatibleConnectors(OrientedConnector source,
                                                              String candidatePieceKey) {
            List<ConnectorMatch> compatible = new ArrayList<>();
            IrisJigsawPiece candidatePiece = graph.pieces().get(candidatePieceKey);
            if (candidatePiece == null) {
                return compatible;
            }
            for (ConnectorReference candidate : pieceConnectors.getOrDefault(candidatePieceKey, List.of())) {
                for (int rotation : rotationsFor(candidatePiece)) {
                    if (connectorsCompatible(source, candidate, rotation)) {
                        compatible.add(new ConnectorMatch(candidate, rotation));
                    }
                }
            }
            compatible.sort((first, second) -> Integer.compare(
                    second.connector().connector().getSelectionPriority(),
                    first.connector().connector().getSelectionPriority()));
            return compatible;
        }

        private boolean connectorsCompatible(OrientedConnector source, ConnectorReference candidate,
                                             int candidateRotation) {
            if (!source.connector().canSource() || !candidate.canTarget()) {
                return false;
            }
            IrisJigsawConnector sourceConnector = source.definition();
            IrisJigsawConnector candidateConnector = candidate.connector();
            if (!Objects.equals(sourceConnector.getChannel(), candidateConnector.getChannel())) {
                return false;
            }
            if (!Objects.equals(sourceConnector.getTargetName(), candidateConnector.getName())) {
                return false;
            }

            IrisDirection sourceDirection = rotateDirection(
                    sourceConnector.getDirection(), source.rotation());
            IrisDirection candidateDirection = rotateDirection(
                    candidateConnector.getDirection(), candidateRotation);
            if (candidateDirection != sourceDirection.reverse()) {
                return false;
            }
            if (sourceConnector.getJoint() != JigsawJoint.ALIGNED) {
                return true;
            }
            IrisDirection sourceTop = rotateDirection(sourceConnector.getTop(), source.rotation());
            IrisDirection candidateTop = rotateDirection(candidateConnector.getTop(), candidateRotation);
            return candidateTop == sourceTop;
        }

        private List<String> candidatePools(String firstPool, boolean includeFirst) {
            List<String> result = new ArrayList<>();
            IrisJigsawPool pool = graph.pools().get(firstPool);
            if (pool == null) {
                return result;
            }
            for (String candidate : JigsawPoolSelection.candidatePoolKeys(firstPool, pool, includeFirst)) {
                if (graph.pools().containsKey(candidate)) {
                    result.add(candidate);
                }
            }
            return result;
        }

        private void validateRuleFeasibility() {
            for (String theme : reachabilityThemes()) {
                int minimumTotal = 0;
                Set<String> reachable = reachablePiecesByTheme.getOrDefault(theme, Set.of());
                for (Map.Entry<String, IrisJigsawPiece> entry : graph.pieces().entrySet()) {
                    String pieceKey = entry.getKey();
                    IrisJigsawPiece piece = entry.getValue();
                    if (!pieceEnabled(pieceKey) || !piece.supportsTheme(theme)) {
                        continue;
                    }
                    IrisJigsawPieceRules rules = piece.resolvedRules();
                    if (rules.getMinimumPlacements() <= 0) {
                        continue;
                    }
                    minimumTotal = Math.addExact(minimumTotal, rules.getMinimumPlacements());
                    if (!reachable.contains(pieceKey)) {
                        addDiagnostic(StructureGraphDiagnostic.Code.UNSATISFIABLE_PIECE_RULE,
                                "Jigsaw piece '" + pieceKey + "' requires at least "
                                        + rules.getMinimumPlacements() + " placement(s) for theme '"
                                        + themeLabel(theme) + "' but is not reachable for that theme.",
                                "piece:minimum:unreachable:" + pieceKey + ":" + theme);
                    }
                    if (rules.getMinimumDepth() > Math.max(1, structure.getMaxDepth())) {
                        addDiagnostic(StructureGraphDiagnostic.Code.UNSATISFIABLE_PIECE_RULE,
                                "Jigsaw piece '" + pieceKey + "' requires placement for theme '"
                                        + themeLabel(theme) + "' but its minimumDepth "
                                        + rules.getMinimumDepth() + " exceeds the structure depth boundary.",
                                "piece:minimum:depth:" + pieceKey + ":" + theme);
                    }
                }
                if (minimumTotal > ASSEMBLY_PIECE_CAP) {
                    addDiagnostic(StructureGraphDiagnostic.Code.UNSATISFIABLE_PIECE_RULE,
                            "Structure '" + structureKey() + "' requires " + minimumTotal
                                    + " minimum piece placements for theme '" + themeLabel(theme)
                                    + "', exceeding the " + ASSEMBLY_PIECE_CAP + "-piece safety cap.",
                            "structure:minimum-total:" + theme);
                }
            }
        }

        private void validateRequiredCaps() {
            for (Map.Entry<ReachableConnectorKey, List<OrientedConnector>> active : activeConnectors.entrySet()) {
                ReachableConnectorKey connectorKey = active.getKey();
                List<OrientedConnector> orientations = active.getValue();
                OrientedConnector representative = orientations.getFirst();
                String poolKey = normalize(representative.definition().getPool());
                IrisJigsawPool pool = graph.pools().get(poolKey);
                if (pool == null || !pool.requiresFallback(structure.isRequireCaps())) {
                    continue;
                }
                String fallbackKey = JigsawPoolSelection.directFallbackKey(pool);
                if (fallbackKey.isEmpty() || !graph.pools().containsKey(fallbackKey)) {
                    addDiagnostic(StructureGraphDiagnostic.Code.MISSING_REQUIRED_FALLBACK,
                            "Reachable connector '" + representative.connector().id()
                                    + "' requires a direct fallback from pool '" + poolKey + "'.",
                            "connector:required-fallback:" + connectorKey);
                    continue;
                }
                int candidateDepth = Math.min(
                        connectorKey.depth() + 1,
                        Math.max(1, structure.getMaxDepth()));
                boolean compatibleTerminal = false;
                for (PoolEntryReference entry : poolEntries.getOrDefault(fallbackKey, List.of())) {
                    if (entry.empty() || !entryIsViable(entry, connectorKey.theme(), candidateDepth)) {
                        continue;
                    }
                    IrisJigsawPiece piece = graph.pieces().get(entry.pieceKey());
                    if (piece == null || !piece.resolvedRules().isTerminal()) {
                        continue;
                    }
                    for (OrientedConnector source : orientations) {
                        if (!findCompatibleConnectors(source, entry.pieceKey()).isEmpty()) {
                            compatibleTerminal = true;
                            break;
                        }
                    }
                    if (compatibleTerminal) {
                        break;
                    }
                }
                if (!compatibleTerminal) {
                    addDiagnostic(StructureGraphDiagnostic.Code.NO_REQUIRED_TERMINAL,
                            "Direct fallback pool '" + fallbackKey + "' has no compatible positive-chance"
                                    + " terminal piece for connector '" + representative.connector().id()
                                    + "' and theme '" + themeLabel(connectorKey.theme()) + "'.",
                            "connector:required-terminal:" + connectorKey);
                }
            }
        }

        private List<StructureGraphAssemblySample> sampleAssemblies(StructureGraphCompilation compilation) {
            if (compilation.hasErrors()) {
                return List.of();
            }
            List<StructureGraphAssemblySample> samples = new ArrayList<>(ASSEMBLY_SEEDS.size());
            Map<IrisJigsawPiece, String> pieceKeys = new IdentityHashMap<>();
            for (Map.Entry<String, IrisJigsawPiece> entry : compilation.getGraph().getPieces().entrySet()) {
                pieceKeys.put(entry.getValue(), entry.getKey());
            }
            for (long seed : ASSEMBLY_SEEDS) {
                try {
                    StructureAssembler assembler = StructureAssembler.forCompilation(
                            compilation, new IrisPosition(0, 64, 0));
                    StructureAssemblyResult result = assembler.assemble(new RNG(seed));
                    List<String> pieces = new ArrayList<>(result.pieces().size());
                    for (PlacedStructurePiece placed : result.pieces()) {
                        String pieceKey = pieceKeys.get(placed.getPiece());
                        pieces.add(pieceKey == null ? "<unknown>" : pieceKey);
                    }
                    samples.add(new StructureGraphAssemblySample(
                            seed,
                            new StructureGraphAssemblySample.Outcome(
                                    pieces,
                                    result.status(),
                                    result.detail())));
                } catch (RuntimeException exception) {
                    addDiagnostic(StructureGraphDiagnostic.Code.ASSEMBLY_RUNTIME_FAILURE,
                            "Runtime assembly sampling failed at seed " + seed + ": "
                                    + exception.getClass().getSimpleName() + ": "
                                    + failureMessage(exception) + ".",
                            "assembly:runtime:" + seed);
                }
            }
            return samples;
        }

        private String failureMessage(RuntimeException exception) {
            return exception.getMessage() == null || exception.getMessage().isBlank()
                    ? "no failure detail" : exception.getMessage();
        }

        private void reportPieceCapSamples(List<StructureGraphAssemblySample> samples) {
            List<String> cappedSeeds = new ArrayList<>();
            for (StructureGraphAssemblySample sample : samples) {
                if (sample.outcome().pieceCapReached()) {
                    cappedSeeds.add(Long.toString(sample.seed()));
                }
            }
            if (!cappedSeeds.isEmpty()) {
                addDiagnostic(StructureGraphDiagnostic.Code.ASSEMBLY_PIECE_CAP_REACHED,
                        "Deterministic structural assembly reached the " + ASSEMBLY_PIECE_CAP
                                + "-piece safety cap for seeds " + String.join(", ", cappedSeeds) + ".",
                        "assembly:piece-cap");
            }
        }

        private CompiledStructureGraph buildGraph() {
            return graph.build();
        }

        private List<StructureGraphDiagnostic> diagnostics() {
            return diagnostics;
        }

        private void addDiagnostic(StructureGraphDiagnostic.Code code, String message, String deduplicationKey) {
            if (diagnosticKeys.add(code.name() + ":" + deduplicationKey)) {
                diagnostics.add(new StructureGraphDiagnostic(code, message));
            }
        }

        private String structureKey() {
            String key = structure.getLoadKey();
            return key == null || key.isBlank() ? "<unloaded>" : key;
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static int[] rotationsFor(IrisJigsawPiece piece) {
        return piece.isRotatable() ? Y_ROTATIONS : NO_ROTATION;
    }

    private static IrisDirection rotateDirection(IrisDirection direction, int degrees) {
        if (direction.isVertical()) {
            return direction;
        }
        int turns = Math.floorMod(degrees, 360) / 90;
        IrisDirection rotated = direction;
        for (int turn = 0; turn < turns; turn++) {
            rotated = switch (rotated) {
                case NORTH_NEGATIVE_Z -> IrisDirection.WEST_NEGATIVE_X;
                case WEST_NEGATIVE_X -> IrisDirection.SOUTH_POSITIVE_Z;
                case SOUTH_POSITIVE_Z -> IrisDirection.EAST_POSITIVE_X;
                case EAST_POSITIVE_X -> IrisDirection.NORTH_NEGATIVE_Z;
                case UP_POSITIVE_Y, DOWN_NEGATIVE_Y -> rotated;
            };
        }
        return rotated;
    }

    private record PoolReference(String message, StructureGraphDiagnostic.Code code) {
    }

    private record PoolEntryReference(
            String poolKey,
            int index,
            String pieceKey,
            int weight,
            double chance,
            boolean empty
    ) {
        private String id() {
            return poolKey + "#" + index;
        }
    }

    private record ConnectorReference(String pieceKey, int index, IrisJigsawConnector connector,
                                      boolean validPosition, boolean validDirection, boolean validTop,
                                      boolean validJoint, boolean validNames, boolean validMetadata,
                                      boolean validPlanarContract) {
        private String id() {
            return pieceKey + "#" + index;
        }

        private boolean canSource() {
            return canTarget() && !normalize(connector.getPool()).isEmpty();
        }

        private boolean canTarget() {
            return connector != null && validPosition && validDirection && validTop && validJoint && validNames
                    && validMetadata && validPlanarContract;
        }
    }

    private record PieceReachState(
            String pieceKey,
            int skippedConnectorIndex,
            int rotation,
            int depth,
            String theme
    ) {
    }

    private record ReachableConnectorKey(String theme, String connectorId, int depth) {
    }

    private record OrientedConnector(ConnectorReference connector, int rotation) {
        private IrisJigsawConnector definition() {
            return connector.connector();
        }
    }

    private record ConnectorMatch(ConnectorReference connector, int rotation) {
    }

}
