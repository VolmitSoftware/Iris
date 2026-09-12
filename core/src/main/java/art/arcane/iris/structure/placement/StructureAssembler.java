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

package art.arcane.iris.structure.placement;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.structure.graph.CompiledStructureGraph;
import art.arcane.iris.structure.graph.JigsawPoolSelection;
import art.arcane.iris.structure.graph.PlanarJigsawWorkcellResolver;
import art.arcane.iris.structure.graph.StructureAssemblyResult;
import art.arcane.iris.structure.graph.StructureAssemblyStatus;
import art.arcane.iris.structure.graph.StructureGraphCatalog;
import art.arcane.iris.structure.graph.StructureGraphCompilation;
import art.arcane.iris.structure.graph.StructureGraphCompiler;
import art.arcane.iris.structure.graph.StructureGraphDiagnostic;
import art.arcane.iris.structure.graph.StructureGraphResolver;
import art.arcane.iris.pack.value.IrisDirection;
import art.arcane.iris.structure.jigsaw.IrisJigsawBranchFailurePolicy;
import art.arcane.iris.structure.jigsaw.IrisJigsawConnector;
import art.arcane.iris.structure.jigsaw.IrisJigsawMode;
import art.arcane.iris.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.structure.jigsaw.IrisJigsawPieceEntry;
import art.arcane.iris.structure.jigsaw.IrisJigsawPool;
import art.arcane.iris.structure.jigsaw.IrisJigsawWorkcellArchetype;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.structure.object.IrisObjectRotation;
import art.arcane.iris.pack.value.IrisPosition;
import art.arcane.iris.structure.jigsaw.JigsawJoint;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class StructureAssembler {
    private static final int HARD_PIECE_CAP = 512;
    private static final int MAX_DEPTH = 30;
    private static final int MAX_SIZE_CHUNKS = 32;
    private static final int[] NO_ROTATION = {0};
    private static final int[] Y_DEGREES = {0, 90, 180, 270};

    private final StructureGraphResolver resolver;
    private final CompiledStructureGraph graph;
    private final IrisStructure structure;
    private final int originX;
    private final int originY;
    private final int originZ;
    private final int radius;
    private final Map<IrisJigsawWorkcellArchetype, PlanarJigsawWorkcellResolver.ResolvedWorkcell>
            planarWorkcells;

    private StructureAssembler(AssemblyOptions options) {
        this.resolver = options.resolver();
        this.graph = options.graph();
        this.structure = options.structure();
        this.originX = options.origin().getX();
        this.originY = options.origin().getY();
        this.originZ = options.origin().getZ();
        this.planarWorkcells = this.structure.resolvedMode() == IrisJigsawMode.PLANAR_JIGSAW
                ? PlanarJigsawWorkcellResolver.resolve(this.structure)
                : Map.of();
        this.radius = this.structure.getMaxSizeChunks() >= 1
                && this.structure.getMaxSizeChunks() <= MAX_SIZE_CHUNKS
                ? this.structure.getMaxSizeChunks() * 16 : 0;
    }

    public static StructureAssembler forData(IrisData data, IrisStructure structure, IrisPosition origin) {
        IrisData activeData = Objects.requireNonNull(data, "Structure assembly data must not be null");
        IrisStructure activeStructure = Objects.requireNonNull(
                structure, "Structure assembly structure must not be null");
        return forCompilation(StructureGraphCatalog.compile(activeData, activeStructure), origin);
    }

    public static StructureAssembler forResolver(StructureGraphResolver resolver, IrisStructure structure,
                                                  IrisPosition origin) {
        StructureGraphResolver activeResolver = Objects.requireNonNull(
                resolver, "Structure assembly resolver must not be null");
        IrisStructure activeStructure = Objects.requireNonNull(
                structure, "Structure assembly structure must not be null");
        return forCompilation(StructureGraphCompiler.compile(activeStructure, activeResolver), origin);
    }

    public static StructureAssembler forCompilation(StructureGraphCompilation compilation, IrisPosition origin) {
        StructureGraphCompilation activeCompilation = Objects.requireNonNull(
                compilation, "Structure graph compilation must not be null");
        if (activeCompilation.hasErrors()) {
            throw invalidGraph(activeCompilation);
        }
        return new StructureAssembler(new AssemblyOptions(
                StructureGraphResolver.forCompiledGraph(activeCompilation.getGraph()),
                activeCompilation.getGraph(), activeCompilation.getGraph().getStructure(), origin));
    }

    public StructureAssemblyResult assemble(RNG rng) {
        Objects.requireNonNull(rng, "Structure assembly RNG must not be null");
        if (radius == 0) {
            throw new IllegalStateException("Structure '" + structure.getLoadKey()
                    + "' has maxSizeChunks outside 1.." + MAX_SIZE_CHUNKS);
        }
        if (structure.getMaxDepth() < 1 || structure.getMaxDepth() > MAX_DEPTH) {
            throw new IllegalStateException("Structure '" + structure.getLoadKey()
                    + "' has maxDepth outside 1.." + MAX_DEPTH);
        }
        String startPoolKey = normalize(structure.getStartPool());
        IrisJigsawPool startPool = resolver.loadPool(startPoolKey);
        if (startPool == null || startPool.getPieces() == null || startPool.getPieces().isEmpty()) {
            throw new IllegalStateException("Structure '" + structure.getLoadKey()
                    + "' has no resolvable start pool '" + startPoolKey + "'");
        }

        KList<PlacedStructurePiece> placed = new KList<>();
        Deque<PieceExpansion> pending = new ArrayDeque<>();
        Map<String, Integer> placementCounts = new HashMap<>();
        Map<PlacedStructurePiece, Set<IrisJigsawConnector>> consumedConnectors = new IdentityHashMap<>();
        Map<PlacedStructurePiece, Map<IrisJigsawConnector, OpenConnector>> openConnectors =
                new IdentityHashMap<>();
        String selectedTheme = JigsawPoolSelection.selectTheme(structure, rng);

        List<IrisJigsawPieceEntry> eligibleStartEntries = eligibleEntries(
                startPool, selectedTheme, 0, placementCounts, true, false);
        if (eligibleStartEntries.isEmpty()) {
            return StructureAssemblyResult.failed(
                    StructureAssemblyStatus.FAILED_RULES,
                    placed,
                    selectedTheme,
                    "The start pool has no entry eligible for theme '" + themeLabel(selectedTheme)
                            + "' at depth zero");
        }
        List<IrisJigsawPieceEntry> startEntries = chanceEligibleEntries(eligibleStartEntries, rng);
        if (startEntries.isEmpty()) {
            return StructureAssemblyResult.intentionalEmpty(
                    selectedTheme,
                    "No start-pool membership passed its chance gate");
        }
        IrisJigsawPieceEntry startEntry = weightedPick(startEntries, rng);
        if (startEntry == null) {
            throw new IllegalStateException("Structure '" + structure.getLoadKey()
                    + "' start pool has no positively weighted entry");
        }
        if (startEntry.isEmpty()) {
            return StructureAssemblyResult.intentionalEmpty(
                    selectedTheme,
                    "The selected start-pool membership intentionally produces no structure");
        }
        String startPieceKey = normalize(startEntry.getPiece());
        IrisJigsawPiece startPiece = resolver.loadPiece(startPieceKey);
        if (startPiece == null) {
            throw new IllegalStateException("Structure '" + structure.getLoadKey()
                    + "' start pool references missing piece '" + startPieceKey + "'");
        }
        String startObjectKey = normalize(startPiece.getObject());
        IrisObject startObject = resolver.loadObject(startObjectKey);
        if (startObject == null) {
            throw new IllegalStateException("Structure '" + structure.getLoadKey()
                    + "' start piece references missing object '" + startObjectKey + "'");
        }
        if (startPiece.getConnectors() == null) {
            throw new IllegalStateException("Structure '" + structure.getLoadKey()
                    + "' start piece has no connector list");
        }

        int startRotY = startPiece.isRotatable() ? Y_DEGREES[rng.i(Y_DEGREES.length)] : 0;
        IrisObjectRotation startRot = IrisObjectRotation.of(0, startRotY, 0);
        PlacedStructurePiece start = build(startPiece, startObject, originX, originY, originZ, startRot);
        placed.add(start);
        registerPlacedPiece(consumedConnectors, start, null);
        incrementPlacement(placementCounts, startPieceKey);
        if (!startPiece.resolvedRules().isTerminal()) {
            enqueuePieceExpansion(pending, openConnectors, start, startObject, 0, 1, null, 0);
        }

        while (!pending.isEmpty()) {
            PieceExpansion expansion = removeNextPieceExpansion(pending);
            for (OpenConnector connector : expansion.connectors()) {
                if (isConsumed(consumedConnectors, connector)) {
                    continue;
                }
                if (placed.size() >= HARD_PIECE_CAP) {
                    return StructureAssemblyResult.failed(
                            StructureAssemblyStatus.HARD_CAP,
                            placed,
                            selectedTheme,
                            "Exceeded the hard piece cap of " + HARD_PIECE_CAP
                                    + " with queued piece expansions unresolved");
                }
                int depth = connector.depth;
                if (depth > structure.getMaxDepth()) {
                    continue;
                }

                IrisJigsawPool pool = resolver.loadPool(connector.pool);
                if (pool == null) {
                    throw new IllegalStateException("Structure '" + structure.getLoadKey()
                            + "' references missing connector pool '" + connector.pool + "'");
                }

                AttachmentResult attachment = attachOne(
                        pending,
                        placed,
                        placementCounts,
                        consumedConnectors,
                        openConnectors,
                        pool,
                        connector,
                        depth,
                        selectedTheme,
                        rng);
                consume(consumedConnectors, connector.sourcePiece(), connector.sourceConnector());
                if (attachment.state() == AttachmentState.FAILED) {
                    return StructureAssemblyResult.failed(
                            StructureAssemblyStatus.FAILED_UNCAPPED,
                            placed,
                            selectedTheme,
                            attachment.detail());
                }
            }
        }
        String unsatisfiedRule = unsatisfiedMinimumPlacement(placementCounts, selectedTheme);
        if (!unsatisfiedRule.isEmpty()) {
            return StructureAssemblyResult.failed(
                    StructureAssemblyStatus.FAILED_RULES,
                    placed,
                    selectedTheme,
                    unsatisfiedRule);
        }
        return StructureAssemblyResult.complete(placed, selectedTheme);
    }

    private AttachmentResult attachOne(
            Deque<PieceExpansion> pending,
            KList<PlacedStructurePiece> placed,
            Map<String, Integer> placementCounts,
            Map<PlacedStructurePiece, Set<IrisJigsawConnector>> consumedConnectors,
            Map<PlacedStructurePiece, Map<IrisJigsawConnector, OpenConnector>> openConnectors,
            IrisJigsawPool pool,
            OpenConnector connector,
            int depth,
            String selectedTheme,
            RNG rng
    ) {
        if (pool.getPieces() == null) {
            throw assemblyFailure("connector pool '" + connector.pool + "' has no piece list");
        }
        if (closeAgainstPlacedNeighbor(placed, consumedConnectors, connector)) {
            return AttachmentResult.success(AttachmentState.CLOSED);
        }
        boolean fallbackRequired = pool.requiresFallback(structure.isRequireCaps());
        if (depth < structure.getMaxDepth() && !pool.getPieces().isEmpty()) {
            AttachmentState primary = attachFromPool(
                    pending,
                    placed,
                    placementCounts,
                    consumedConnectors,
                    openConnectors,
                    pool,
                    connector,
                    selectedTheme,
                    false,
                    !fallbackRequired,
                    rng);
            if (primary != AttachmentState.NO_MATCH) {
                return AttachmentResult.success(primary);
            }
        } else if (pool.getPieces().isEmpty() && !fallbackRequired) {
            return AttachmentResult.success(AttachmentState.TERMINATED);
        }
        String fallbackKey = JigsawPoolSelection.directFallbackKey(pool);
        if (fallbackKey.isEmpty()) {
            if (branchFailureRequiresAssemblyFailure(fallbackRequired, depth)) {
                return AttachmentResult.failed(
                        "Connector pool '" + connector.pool
                                + "' could not place a piece and has no direct fallback at "
                                + connectorLocation(connector));
            }
            return AttachmentResult.success(AttachmentState.TERMINATED);
        }
        IrisJigsawPool fallback = resolver.loadPool(fallbackKey);
        if (fallback == null) {
            throw assemblyFailure("references missing authored fallback pool '" + fallbackKey + "'");
        }
        if (fallback.getPieces() == null) {
            throw assemblyFailure("authored fallback pool '" + fallbackKey + "' has no piece list");
        }
        if (fallback.getPieces().isEmpty()) {
            return fallbackRequired
                    ? AttachmentResult.failed(
                    "Required fallback pool '" + fallbackKey + "' has no physical terminal piece")
                    : AttachmentResult.success(AttachmentState.TERMINATED);
        }
        AttachmentState fallbackState = attachFromPool(
                pending,
                placed,
                placementCounts,
                consumedConnectors,
                openConnectors,
                fallback,
                connector,
                selectedTheme,
                fallbackRequired,
                !fallbackRequired,
                rng);
        if (fallbackState != AttachmentState.NO_MATCH) {
            return AttachmentResult.success(fallbackState);
        }
        if (branchFailureRequiresAssemblyFailure(fallbackRequired, depth)) {
            return AttachmentResult.failed(
                    "Direct fallback pool '" + fallbackKey + "' could not place"
                            + (fallbackRequired ? " a required terminal piece" : " a compatible piece")
                            + " at " + connectorLocation(connector));
        }
        return AttachmentResult.success(AttachmentState.TERMINATED);
    }

    private boolean branchFailureRequiresAssemblyFailure(boolean fallbackRequired, int depth) {
        return fallbackRequired
                || depth < structure.getMaxDepth()
                && structure.resolvedBranchFailurePolicy() == IrisJigsawBranchFailurePolicy.FAIL_ASSEMBLY;
    }

    private AttachmentState attachFromPool(
            Deque<PieceExpansion> pending,
            KList<PlacedStructurePiece> placed,
            Map<String, Integer> placementCounts,
            Map<PlacedStructurePiece, Set<IrisJigsawConnector>> consumedConnectors,
            Map<PlacedStructurePiece, Map<IrisJigsawConnector, OpenConnector>> openConnectors,
            IrisJigsawPool pool,
            OpenConnector connector,
            String selectedTheme,
            boolean terminalOnly,
            boolean allowEmpty,
            RNG rng
    ) {
        int ruleDepth = Math.min(connector.pieceDepth, structure.getMaxDepth());
        for (IrisJigsawPieceEntry entry : weightedOrder(
                pool,
                selectedTheme,
                ruleDepth,
                placementCounts,
                allowEmpty,
                terminalOnly,
                rng)) {
            if (entry.isEmpty()) {
                return AttachmentState.TERMINATED;
            }
            String pieceName = normalize(entry.getPiece());
            if (pieceName.isEmpty()) {
                throw assemblyFailure("pool contains a weighted entry without a piece key");
            }
            IrisJigsawPiece piece = resolver.loadPiece(pieceName);
            if (piece == null) {
                throw assemblyFailure("pool references missing piece '" + pieceName + "'");
            }
            String objectKey = normalize(piece.getObject());
            if (objectKey.isEmpty()) {
                throw assemblyFailure("piece '" + pieceName + "' has no object key");
            }
            IrisObject object = resolver.loadObject(objectKey);
            if (object == null) {
                throw assemblyFailure("piece '" + pieceName + "' references missing object '"
                        + objectKey + "'");
            }
            if (piece.getConnectors() == null) {
                throw assemblyFailure("piece '" + pieceName + "' has no connector list");
            }

            for (IrisJigsawConnector pieceConnector : piece.getConnectors()) {
                requireConnector(pieceConnector, pieceName);
            }
            List<IrisJigsawConnector> candidateConnectors = orderedConnectors(piece.getConnectors(), null);
            for (IrisJigsawConnector cb : candidateConnectors) {
                if (connector.targetName == null) {
                    throw assemblyFailure("open connector from pool '" + connector.pool + "' has no target name");
                }
                if (!Objects.equals(connector.channel, cb.getChannel())) {
                    continue;
                }
                if (!cb.getName().equals(connector.targetName)) {
                    continue;
                }

                IrisDirection needed = connector.facing.reverse();
                int[] rotations = piece.isRotatable() ? rotationCandidates(connector.joint, rng) : NO_ROTATION;
                for (int yDeg : rotations) {
                    IrisObjectRotation rot = IrisObjectRotation.of(0, yDeg, 0);
                    IrisDirection rotatedFace = rot.rotate(cb.getDirection());
                    if (rotatedFace != needed) {
                        continue;
                    }
                    if (connector.joint == JigsawJoint.ALIGNED && rot.rotate(cb.getTop()) != connector.top) {
                        continue;
                    }

                    IrisPosition rcr = connectorOffset(object, cb, rot);
                    int wcx = connector.wx + connector.facing.x();
                    int wcy = connector.wy + connector.facing.y();
                    int wcz = connector.wz + connector.facing.z();
                    int px = wcx - rcr.getX();
                    int py = wcy - rcr.getY();
                    int pz = wcz - rcr.getZ();

                    PlacedStructurePiece candidate = build(piece, object, px, py, pz, rot);
                    if (!withinRadius(candidate) || collides(placed, candidate)) {
                        continue;
                    }
                    List<ConnectorClosure> closures = requiredClosures(
                            placed,
                            consumedConnectors,
                            connector,
                            candidate,
                            cb);
                    if (closures == null) {
                        continue;
                    }
                    if (!capReservationsRemainViable(
                            placed,
                            placementCounts,
                            consumedConnectors,
                            openConnectors,
                            connector,
                            candidate,
                            cb,
                            closures,
                            pieceName,
                            selectedTheme)) {
                        continue;
                    }

                    placed.add(candidate);
                    registerPlacedPiece(consumedConnectors, candidate, cb);
                    for (ConnectorClosure closure : closures) {
                        consume(consumedConnectors, closure.existingPiece(), closure.existingConnector());
                        consume(consumedConnectors, candidate, closure.candidateConnector());
                    }
                    incrementPlacement(placementCounts, pieceName);
                    if (!piece.resolvedRules().isTerminal() && connector.depth < structure.getMaxDepth()) {
                        enqueuePieceExpansion(
                                pending,
                                openConnectors,
                                candidate,
                                object,
                                connector.depth + 1,
                                connector.pieceDepth + 1,
                                cb,
                                connector.placementPriority);
                    }
                    return AttachmentState.ATTACHED;
                }
            }
        }
        return AttachmentState.NO_MATCH;
    }

    private boolean closeAgainstPlacedNeighbor(
            KList<PlacedStructurePiece> placed,
            Map<PlacedStructurePiece, Set<IrisJigsawConnector>> consumedConnectors,
            OpenConnector source
    ) {
        int targetX = source.wx() + source.facing().x();
        int targetY = source.wy() + source.facing().y();
        int targetZ = source.wz() + source.facing().z();
        for (PlacedStructurePiece candidatePiece : placed) {
            if (candidatePiece == source.sourcePiece() || candidatePiece.getPiece().getConnectors() == null) {
                continue;
            }
            for (IrisJigsawConnector candidate : candidatePiece.getPiece().getConnectors()) {
                if (isConsumed(consumedConnectors, candidatePiece, candidate)
                        || !existingConnectorMatches(source, candidatePiece, candidate,
                        targetX, targetY, targetZ)) {
                    continue;
                }
                consume(consumedConnectors, candidatePiece, candidate);
                return true;
            }
        }
        return false;
    }

    private List<ConnectorClosure> requiredClosures(
            KList<PlacedStructurePiece> placed,
            Map<PlacedStructurePiece, Set<IrisJigsawConnector>> consumedConnectors,
            OpenConnector attachmentSource,
            PlacedStructurePiece candidatePiece,
            IrisJigsawConnector attachmentConnector
    ) {
        if (!structure.isRequireCaps()) {
            return List.of();
        }
        List<ConnectorClosure> closures = new ArrayList<>();
        Set<IrisJigsawConnector> matchedCandidate = Collections.newSetFromMap(new IdentityHashMap<>());
        Map<PlacedStructurePiece, Set<IrisJigsawConnector>> matchedExisting = new IdentityHashMap<>();
        matchedCandidate.add(attachmentConnector);
        matchedExisting.computeIfAbsent(
                attachmentSource.sourcePiece(),
                ignored -> Collections.newSetFromMap(new IdentityHashMap<>()))
                .add(attachmentSource.sourceConnector());

        for (PlacedStructurePiece existingPiece : placed) {
            for (IrisJigsawConnector existingConnector : existingPiece.getPiece().getConnectors()) {
                if (normalize(existingConnector.getPool()).isEmpty()
                        || isConsumed(consumedConnectors, existingPiece, existingConnector)
                        || isMatched(matchedExisting, existingPiece, existingConnector)) {
                    continue;
                }
                IrisPosition target = connectorTarget(existingPiece, existingConnector);
                if (!contains(candidatePiece, target)) {
                    continue;
                }
                IrisJigsawConnector matchingCandidate = matchingConnector(
                        existingPiece,
                        existingConnector,
                        candidatePiece,
                        matchedCandidate);
                if (matchingCandidate == null) {
                    return null;
                }
                matchedCandidate.add(matchingCandidate);
                matchedExisting.computeIfAbsent(
                        existingPiece,
                        ignored -> Collections.newSetFromMap(new IdentityHashMap<>()))
                        .add(existingConnector);
                closures.add(new ConnectorClosure(existingPiece, existingConnector, matchingCandidate));
            }
        }

        if (candidatePiece.getPiece().resolvedRules().isTerminal()) {
            return closures;
        }
        for (IrisJigsawConnector candidateConnector : candidatePiece.getPiece().getConnectors()) {
            if (matchedCandidate.contains(candidateConnector)
                    || normalize(candidateConnector.getPool()).isEmpty()) {
                continue;
            }
            IrisPosition target = connectorTarget(candidatePiece, candidateConnector);
            PlacedStructurePiece occupied = containingPiece(placed, target);
            if (occupied == null) {
                continue;
            }
            Set<IrisJigsawConnector> unavailable = matchedExisting.getOrDefault(occupied, Set.of());
            IrisJigsawConnector matchingExisting = matchingConnector(
                    candidatePiece,
                    candidateConnector,
                    occupied,
                    unavailable);
            if (matchingExisting == null
                    || isConsumed(consumedConnectors, occupied, matchingExisting)) {
                return null;
            }
            matchedCandidate.add(candidateConnector);
            matchedExisting.computeIfAbsent(
                    occupied,
                    ignored -> Collections.newSetFromMap(new IdentityHashMap<>()))
                    .add(matchingExisting);
            closures.add(new ConnectorClosure(occupied, matchingExisting, candidateConnector));
        }
        return closures;
    }

    private boolean capReservationsRemainViable(
            KList<PlacedStructurePiece> placed,
            Map<String, Integer> placementCounts,
            Map<PlacedStructurePiece, Set<IrisJigsawConnector>> consumedConnectors,
            Map<PlacedStructurePiece, Map<IrisJigsawConnector, OpenConnector>> openConnectors,
            OpenConnector attachmentSource,
            PlacedStructurePiece candidatePiece,
            IrisJigsawConnector attachmentConnector,
            List<ConnectorClosure> closures,
            String candidatePieceKey,
            String selectedTheme
    ) {
        if (!structure.isRequireCaps() || structure.resolvedMode() != IrisJigsawMode.PLANAR_JIGSAW) {
            return true;
        }
        List<OpenConnector> unresolved = new ArrayList<>();
        for (PlacedStructurePiece placedPiece : placed) {
            Map<IrisJigsawConnector, OpenConnector> definitions = openConnectors.get(placedPiece);
            if (definitions == null) {
                continue;
            }
            for (IrisJigsawConnector connector : placedPiece.getPiece().getConnectors()) {
                OpenConnector open = definitions.get(connector);
                if (open == null
                        || isConsumed(consumedConnectors, placedPiece, connector)
                        || connectorWillBeConsumed(
                        placedPiece, connector, attachmentSource, candidatePiece, attachmentConnector, closures)) {
                    continue;
                }
                unresolved.add(open);
            }
        }
        if (!candidatePiece.getPiece().resolvedRules().isTerminal()
                && attachmentSource.depth() < structure.getMaxDepth()) {
            for (IrisJigsawConnector connector : candidatePiece.getPiece().getConnectors()) {
                if (normalize(connector.getPool()).isEmpty()
                        || connectorWillBeConsumed(
                        candidatePiece, connector, attachmentSource, candidatePiece, attachmentConnector, closures)) {
                    continue;
                }
                unresolved.add(openConnector(
                        candidatePiece,
                        candidatePiece.getObject(),
                        connector,
                        attachmentSource.depth() + 1,
                        attachmentSource.pieceDepth() + 1));
            }
        }

        List<PlacedStructurePiece> occupied = new ArrayList<>(placed.size() + unresolved.size() + 1);
        occupied.addAll(placed);
        occupied.add(candidatePiece);
        Map<String, Integer> reservedCounts = new HashMap<>(placementCounts);
        incrementPlacement(reservedCounts, candidatePieceKey);
        for (OpenConnector open : unresolved) {
            CapReservation reservation = reserveTerminalCap(
                    open,
                    occupied,
                    reservedCounts,
                    selectedTheme);
            if (reservation == null) {
                return false;
            }
            occupied.add(reservation.piece());
            incrementPlacement(reservedCounts, reservation.pieceKey());
        }
        return true;
    }

    private boolean connectorWillBeConsumed(
            PlacedStructurePiece piece,
            IrisJigsawConnector connector,
            OpenConnector attachmentSource,
            PlacedStructurePiece candidatePiece,
            IrisJigsawConnector attachmentConnector,
            List<ConnectorClosure> closures
    ) {
        if (piece == attachmentSource.sourcePiece() && connector == attachmentSource.sourceConnector()
                || piece == candidatePiece && connector == attachmentConnector
                || piece == candidatePiece && candidatePiece.getPiece().resolvedRules().isTerminal()) {
            return true;
        }
        for (ConnectorClosure closure : closures) {
            if (piece == closure.existingPiece() && connector == closure.existingConnector()
                    || piece == candidatePiece && connector == closure.candidateConnector()) {
                return true;
            }
        }
        return false;
    }

    private CapReservation reserveTerminalCap(
            OpenConnector source,
            List<PlacedStructurePiece> occupied,
            Map<String, Integer> placementCounts,
            String selectedTheme
    ) {
        IrisJigsawPool sourcePool = resolver.loadPool(source.pool());
        if (sourcePool == null) {
            return null;
        }
        String fallbackKey = JigsawPoolSelection.directFallbackKey(sourcePool);
        if (fallbackKey.isEmpty()) {
            return null;
        }
        IrisJigsawPool fallback = resolver.loadPool(fallbackKey);
        if (fallback == null || fallback.getPieces() == null) {
            return null;
        }
        int ruleDepth = Math.min(source.pieceDepth(), structure.getMaxDepth());
        for (IrisJigsawPieceEntry entry : eligibleEntries(
                fallback,
                selectedTheme,
                ruleDepth,
                placementCounts,
                false,
                true)) {
            if (entry.isEmpty() || entry.getChance() <= 0D) {
                continue;
            }
            String pieceKey = normalize(entry.getPiece());
            IrisJigsawPiece piece = resolver.loadPiece(pieceKey);
            if (piece == null || piece.getConnectors() == null) {
                continue;
            }
            IrisObject object = resolver.loadObject(normalize(piece.getObject()));
            if (object == null) {
                continue;
            }
            for (IrisJigsawConnector connector : orderedConnectors(piece.getConnectors(), null)) {
                if (!Objects.equals(source.channel(), connector.getChannel())
                        || !Objects.equals(source.targetName(), connector.getName())) {
                    continue;
                }
                int[] rotations = piece.isRotatable() ? Y_DEGREES : NO_ROTATION;
                for (int yDegrees : rotations) {
                    IrisObjectRotation rotation = IrisObjectRotation.of(0, yDegrees, 0);
                    if (rotation.rotate(connector.getDirection()) != source.facing().reverse()
                            || source.joint() == JigsawJoint.ALIGNED
                            && rotation.rotate(connector.getTop()) != source.top()) {
                        continue;
                    }
                    IrisPosition offset = connectorOffset(object, connector, rotation);
                    int logicalX = source.wx() + source.facing().x() - offset.getX();
                    int logicalY = source.wy() + source.facing().y() - offset.getY();
                    int logicalZ = source.wz() + source.facing().z() - offset.getZ();
                    PlacedStructurePiece reservation = build(
                            piece,
                            object,
                            logicalX,
                            logicalY,
                            logicalZ,
                            rotation);
                    if (withinRadius(reservation) && !collides(occupied, reservation)) {
                        return new CapReservation(pieceKey, reservation);
                    }
                }
            }
        }
        return null;
    }

    private IrisJigsawConnector matchingConnector(
            PlacedStructurePiece sourcePiece,
            IrisJigsawConnector sourceConnector,
            PlacedStructurePiece targetPiece,
            Set<IrisJigsawConnector> unavailable
    ) {
        IrisPosition target = connectorTarget(sourcePiece, sourceConnector);
        IrisDirection sourceFacing = sourcePiece.getRotation().rotate(sourceConnector.getDirection());
        IrisDirection sourceTop = sourcePiece.getRotation().rotate(sourceConnector.getTop());
        for (IrisJigsawConnector candidate : targetPiece.getPiece().getConnectors()) {
            if (unavailable.contains(candidate)
                    || !Objects.equals(sourceConnector.getChannel(), candidate.getChannel())
                    || !Objects.equals(sourceConnector.getTargetName(), candidate.getName())) {
                continue;
            }
            IrisPosition candidatePosition = connectorPosition(targetPiece, candidate);
            if (!target.equals(candidatePosition)
                    || targetPiece.getRotation().rotate(candidate.getDirection()) != sourceFacing.reverse()) {
                continue;
            }
            if (sourceConnector.getJoint() != JigsawJoint.ALIGNED
                    || targetPiece.getRotation().rotate(candidate.getTop()) == sourceTop) {
                return candidate;
            }
        }
        return null;
    }

    private IrisPosition connectorPosition(PlacedStructurePiece piece, IrisJigsawConnector connector) {
        IrisPosition center = logicalCenter(piece);
        IrisPosition offset = connectorOffset(piece.getObject(), connector, piece.getRotation());
        return new IrisPosition(
                center.getX() + offset.getX(),
                center.getY() + offset.getY(),
                center.getZ() + offset.getZ());
    }

    private IrisPosition connectorTarget(PlacedStructurePiece piece, IrisJigsawConnector connector) {
        IrisPosition position = connectorPosition(piece, connector);
        IrisDirection facing = piece.getRotation().rotate(connector.getDirection());
        return new IrisPosition(
                position.getX() + facing.x(),
                position.getY() + facing.y(),
                position.getZ() + facing.z());
    }

    private PlacedStructurePiece containingPiece(
            KList<PlacedStructurePiece> pieces,
            IrisPosition position
    ) {
        for (PlacedStructurePiece piece : pieces) {
            if (contains(piece, position)) {
                return piece;
            }
        }
        return null;
    }

    private boolean contains(PlacedStructurePiece piece, IrisPosition position) {
        return position.getX() >= piece.getMinX() && position.getX() <= piece.getMaxX()
                && position.getY() >= piece.getMinY() && position.getY() <= piece.getMaxY()
                && position.getZ() >= piece.getMinZ() && position.getZ() <= piece.getMaxZ();
    }

    private boolean isMatched(
            Map<PlacedStructurePiece, Set<IrisJigsawConnector>> matched,
            PlacedStructurePiece piece,
            IrisJigsawConnector connector
    ) {
        Set<IrisJigsawConnector> connectors = matched.get(piece);
        return connectors != null && connectors.contains(connector);
    }

    private boolean existingConnectorMatches(
            OpenConnector source,
            PlacedStructurePiece candidatePiece,
            IrisJigsawConnector candidate,
            int targetX,
            int targetY,
            int targetZ
    ) {
        requireConnector(candidate, candidatePiece.getObject().getLoadKey());
        if (!Objects.equals(source.channel(), candidate.getChannel())
                || !Objects.equals(source.targetName(), candidate.getName())) {
            return false;
        }
        IrisPosition rotated = connectorOffset(
                candidatePiece.getObject(), candidate, candidatePiece.getRotation());
        IrisPosition center = logicalCenter(candidatePiece);
        if (center.getX() + rotated.getX() != targetX
                || center.getY() + rotated.getY() != targetY
                || center.getZ() + rotated.getZ() != targetZ) {
            return false;
        }
        if (candidatePiece.getRotation().rotate(candidate.getDirection()) != source.facing().reverse()) {
            return false;
        }
        return source.joint() != JigsawJoint.ALIGNED
                || candidatePiece.getRotation().rotate(candidate.getTop()) == source.top();
    }

    private void registerPlacedPiece(
            Map<PlacedStructurePiece, Set<IrisJigsawConnector>> consumedConnectors,
            PlacedStructurePiece piece,
            IrisJigsawConnector attachment
    ) {
        Set<IrisJigsawConnector> consumed = Collections.newSetFromMap(new IdentityHashMap<>());
        if (piece.getPiece().resolvedRules().isTerminal()) {
            consumed.addAll(piece.getPiece().getConnectors());
        } else if (attachment != null) {
            consumed.add(attachment);
        }
        consumedConnectors.put(piece, consumed);
    }

    private boolean isConsumed(
            Map<PlacedStructurePiece, Set<IrisJigsawConnector>> consumedConnectors,
            OpenConnector connector
    ) {
        return isConsumed(consumedConnectors, connector.sourcePiece(), connector.sourceConnector());
    }

    private boolean isConsumed(
            Map<PlacedStructurePiece, Set<IrisJigsawConnector>> consumedConnectors,
            PlacedStructurePiece piece,
            IrisJigsawConnector connector
    ) {
        Set<IrisJigsawConnector> consumed = consumedConnectors.get(piece);
        return consumed != null && consumed.contains(connector);
    }

    private void consume(
            Map<PlacedStructurePiece, Set<IrisJigsawConnector>> consumedConnectors,
            PlacedStructurePiece piece,
            IrisJigsawConnector connector
    ) {
        Set<IrisJigsawConnector> consumed = consumedConnectors.get(piece);
        if (consumed == null) {
            throw assemblyFailure("placed piece connector ownership was not registered");
        }
        consumed.add(connector);
    }

    private void enqueuePieceExpansion(
                                       Deque<PieceExpansion> pending,
                                       Map<PlacedStructurePiece, Map<IrisJigsawConnector, OpenConnector>> openConnectors,
                                       PlacedStructurePiece p,
                                       IrisObject object, int depth, int pieceDepth, IrisJigsawConnector skip,
                                       int placementPriority) {
        if (p.getPiece().getConnectors() == null) {
            throw assemblyFailure("placed piece for object '" + object.getLoadKey() + "' has no connector list");
        }
        List<OpenConnector> connectors = new ArrayList<>();
        Map<IrisJigsawConnector, OpenConnector> definitions = new IdentityHashMap<>();
        for (IrisJigsawConnector con : orderedConnectors(p.getPiece().getConnectors(), skip)) {
            requireConnector(con, object.getLoadKey());
            if (con.getPool() == null || con.getPool().isBlank()) {
                continue;
            }
            OpenConnector openConnector = openConnector(p, object, con, depth, pieceDepth);
            connectors.add(openConnector);
            definitions.put(con, openConnector);
        }
        openConnectors.put(p, definitions);
        if (!connectors.isEmpty()) {
            pending.addLast(new PieceExpansion(connectors, placementPriority));
        }
    }

    private OpenConnector openConnector(
            PlacedStructurePiece piece,
            IrisObject object,
            IrisJigsawConnector connector,
            int depth,
            int pieceDepth
    ) {
        IrisPosition offset = connectorOffset(object, connector, piece.getRotation());
        IrisPosition center = logicalCenter(piece);
        return new OpenConnector(
                piece,
                connector,
                center.getX() + offset.getX(),
                center.getY() + offset.getY(),
                center.getZ() + offset.getZ(),
                piece.getRotation().rotate(connector.getDirection()),
                piece.getRotation().rotate(connector.getTop()),
                normalize(connector.getPool()),
                connector.getName(),
                connector.getTargetName(),
                connector.getChannel(),
                connector.getJoint(),
                depth,
                pieceDepth,
                connector.getPlacementPriority());
    }

    private List<IrisJigsawConnector> orderedConnectors(List<IrisJigsawConnector> connectors,
                                                        IrisJigsawConnector skip) {
        List<IrisJigsawConnector> ordered = new ArrayList<>(connectors.size());
        for (IrisJigsawConnector connector : connectors) {
            if (connector != skip) {
                ordered.add(connector);
            }
        }
        ordered.sort((first, second) -> Integer.compare(
                second.getSelectionPriority(), first.getSelectionPriority()));
        return ordered;
    }

    private PieceExpansion removeNextPieceExpansion(Deque<PieceExpansion> pending) {
        int highestPriority = Integer.MIN_VALUE;
        for (PieceExpansion expansion : pending) {
            highestPriority = Math.max(highestPriority, expansion.placementPriority());
        }
        Iterator<PieceExpansion> iterator = pending.iterator();
        while (iterator.hasNext()) {
            PieceExpansion expansion = iterator.next();
            if (expansion.placementPriority() == highestPriority) {
                iterator.remove();
                return expansion;
            }
        }
        throw assemblyFailure("piece expansion queue was unexpectedly empty");
    }

    private PlacedStructurePiece build(IrisJigsawPiece piece, IrisObject object, int x, int y, int z, IrisObjectRotation rot) {
        int width = Math.max(1, object.getW());
        int height = Math.max(1, object.getH());
        int depth = Math.max(1, object.getD());
        if (structure.resolvedMode() == IrisJigsawMode.PLANAR_JIGSAW) {
            IrisPosition dimensions = rotatedDimensions(object, rot);
            IrisPosition offset = planarPlacementOffset(object, rot);
            int minX = x - dimensions.getX() / 2;
            int minY = y - dimensions.getY() / 2;
            int minZ = z - dimensions.getZ() / 2;
            return new PlacedStructurePiece(
                    piece,
                    object,
                    x + offset.getX(),
                    y + offset.getY(),
                    z + offset.getZ(),
                    rot,
                    minX,
                    minY,
                    minZ,
                    minX + dimensions.getX() - 1,
                    minY + dimensions.getY() - 1,
                    minZ + dimensions.getZ() - 1);
        }
        int localMinX = -(width / 2);
        int localMinY = -(height / 2);
        int localMinZ = -(depth / 2);
        int localMaxX = localMinX + width - 1;
        int localMaxY = localMinY + height - 1;
        int localMaxZ = localMinZ + depth - 1;
        IrisPosition rotatedMin = rot.rotate(new IrisPosition(localMinX, localMinY, localMinZ), 0, 0, 0);
        IrisPosition rotatedMax = rot.rotate(new IrisPosition(localMaxX, localMaxY, localMaxZ), 0, 0, 0);
        int minX = Math.min(rotatedMin.getX(), rotatedMax.getX());
        int minY = Math.min(rotatedMin.getY(), rotatedMax.getY());
        int minZ = Math.min(rotatedMin.getZ(), rotatedMax.getZ());
        int maxX = Math.max(rotatedMin.getX(), rotatedMax.getX());
        int maxY = Math.max(rotatedMin.getY(), rotatedMax.getY());
        int maxZ = Math.max(rotatedMin.getZ(), rotatedMax.getZ());
        return new PlacedStructurePiece(piece, object, x, y, z, rot,
                x + minX, y + minY, z + minZ, x + maxX, y + maxY, z + maxZ);
    }

    private boolean withinRadius(PlacedStructurePiece p) {
        return p.getMinX() >= originX - radius && p.getMaxX() <= originX + radius
                && p.getMinZ() >= originZ - radius && p.getMaxZ() <= originZ + radius;
    }

    private boolean collides(List<PlacedStructurePiece> placed, PlacedStructurePiece candidate) {
        if (!candidate.getPiece().isCollidable()) {
            return false;
        }
        for (PlacedStructurePiece p : placed) {
            if (p.getPiece().isCollidable() && p.intersects(candidate)) {
                return true;
            }
        }
        return false;
    }

    private int[] rotationCandidates(JigsawJoint joint, RNG rng) {
        if (joint == JigsawJoint.ALIGNED) {
            return Y_DEGREES;
        }
        int[] shuffled = {0, 90, 180, 270};
        for (int i = shuffled.length - 1; i > 0; i--) {
            int j = rng.i(i + 1);
            int t = shuffled[i];
            shuffled[i] = shuffled[j];
            shuffled[j] = t;
        }
        return shuffled;
    }

    private IrisPosition centerOf(IrisObject object) {
        return new IrisPosition(object.getW() / 2, object.getH() / 2, object.getD() / 2);
    }

    private IrisPosition connectorOffset(
            IrisObject object,
            IrisJigsawConnector connector,
            IrisObjectRotation rotation
    ) {
        if (structure.resolvedMode() == IrisJigsawMode.PLANAR_JIGSAW) {
            IrisPosition dimensions = rotatedDimensions(object, rotation);
            IrisPosition position = IrisJigsawConnector.canonicalPlanarPosition(
                    dimensions,
                    rotation.rotate(connector.getDirection()));
            return new IrisPosition(
                    position.getX() - dimensions.getX() / 2,
                    position.getY() - dimensions.getY() / 2,
                    position.getZ() - dimensions.getZ() / 2);
        }
        IrisPosition center = centerOf(object);
        IrisPosition relative = new IrisPosition(
                connector.getPosition().getX() - center.getX(),
                connector.getPosition().getY() - center.getY(),
                connector.getPosition().getZ() - center.getZ());
        return rotation.rotate(relative, 0, 0, 0);
    }

    private IrisPosition logicalCenter(PlacedStructurePiece piece) {
        if (structure.resolvedMode() != IrisJigsawMode.PLANAR_JIGSAW) {
            return new IrisPosition(piece.getX(), piece.getY(), piece.getZ());
        }
        IrisPosition offset = planarPlacementOffset(piece.getObject(), piece.getRotation());
        return new IrisPosition(
                piece.getX() - offset.getX(),
                piece.getY() - offset.getY(),
                piece.getZ() - offset.getZ());
    }

    private IrisPosition rotatedDimensions(IrisObject object, IrisObjectRotation rotation) {
        int width = Math.max(1, object.getW());
        int height = Math.max(1, object.getH());
        int depth = Math.max(1, object.getD());
        return quarterTurns(rotation) % 2 == 0
                ? new IrisPosition(width, height, depth)
                : new IrisPosition(depth, height, width);
    }

    private IrisPosition planarPlacementOffset(IrisObject object, IrisObjectRotation rotation) {
        int width = Math.max(1, object.getW());
        int depth = Math.max(1, object.getD());
        return switch (quarterTurns(rotation)) {
            case 0 -> new IrisPosition();
            case 1 -> new IrisPosition(0, 0, width % 2 == 0 ? -1 : 0);
            case 2 -> new IrisPosition(
                    width % 2 == 0 ? -1 : 0,
                    0,
                    depth % 2 == 0 ? -1 : 0);
            case 3 -> new IrisPosition(depth % 2 == 0 ? -1 : 0, 0, 0);
            default -> throw new IllegalStateException("Unsupported planar quarter-turn count");
        };
    }

    private int quarterTurns(IrisObjectRotation rotation) {
        return switch (rotation.rotate(IrisDirection.NORTH_NEGATIVE_Z)) {
            case NORTH_NEGATIVE_Z -> 0;
            case WEST_NEGATIVE_X -> 1;
            case SOUTH_POSITIVE_Z -> 2;
            case EAST_POSITIVE_X -> 3;
            case UP_POSITIVE_Y, DOWN_NEGATIVE_Y -> throw new IllegalStateException(
                    "Planar jigsaw rotation moved north onto a vertical axis");
        };
    }

    private IrisJigsawPieceEntry weightedPick(List<IrisJigsawPieceEntry> entries, RNG rng) {
        long total = 0L;
        for (IrisJigsawPieceEntry entry : entries) {
            total += entry.getWeight();
        }
        if (total <= 0) {
            return null;
        }
        long target = rng.nextLong(total);
        for (IrisJigsawPieceEntry entry : entries) {
            target -= entry.getWeight();
            if (target < 0) {
                return entry;
            }
        }
        return null;
    }

    private KList<IrisJigsawPieceEntry> weightedOrder(
            IrisJigsawPool pool,
            String selectedTheme,
            int depth,
            Map<String, Integer> placementCounts,
            boolean allowEmpty,
            boolean terminalOnly,
            RNG rng
    ) {
        List<IrisJigsawPieceEntry> eligible = eligibleEntries(
                pool,
                selectedTheme,
                depth,
                placementCounts,
                allowEmpty,
                terminalOnly);
        KList<IrisJigsawPieceEntry> remaining = new KList<>(chanceEligibleEntries(eligible, rng));
        KList<IrisJigsawPieceEntry> order = new KList<>();
        while (!remaining.isEmpty()) {
            long total = 0L;
            for (IrisJigsawPieceEntry e : remaining) {
                total += e.getWeight();
            }
            long t = rng.nextLong(total);
            int idx = 0;
            for (int i = 0; i < remaining.size(); i++) {
                t -= remaining.get(i).getWeight();
                if (t < 0) {
                    idx = i;
                    break;
                }
            }
            order.add(remaining.remove(idx));
        }
        return order;
    }

    private List<IrisJigsawPieceEntry> eligibleEntries(
            IrisJigsawPool pool,
            String selectedTheme,
            int depth,
            Map<String, Integer> placementCounts,
            boolean allowEmpty,
            boolean terminalOnly
    ) {
        if (pool.getPieces() == null) {
            throw assemblyFailure("pool has no piece list");
        }
        // A pool the version-content gate excluded has nothing left to place: treat it as empty so the branch
        // terminates or falls back, instead of failing the whole assembly.
        if (pool.isCompatExcluded()) {
            return List.of();
        }
        List<IrisJigsawPieceEntry> eligible = new ArrayList<>();
        List<IrisJigsawPieceEntry> required = new ArrayList<>();
        for (IrisJigsawPieceEntry entry : pool.getPieces()) {
            if (entry == null || entry.getWeight() <= 0) {
                throw assemblyFailure("pool contains a null or non-positive weighted entry");
            }
            if (entry.isEmpty()) {
                if (allowEmpty) {
                    eligible.add(entry);
                }
                continue;
            }
            String pieceKey = normalize(entry.getPiece());
            if (pieceKey.isEmpty()) {
                throw assemblyFailure("pool contains a weighted entry without a piece key");
            }
            IrisJigsawPiece piece = resolver.loadPiece(pieceKey);
            if (piece == null) {
                throw assemblyFailure("pool references missing piece '" + pieceKey + "'");
            }
            if (piece.isCompatExcluded()) {
                continue;
            }
            int placements = placementCounts.getOrDefault(pieceKey, 0);
            if (!pieceEnabled(piece)
                    || terminalOnly && !piece.resolvedRules().isTerminal()
                    || !JigsawPoolSelection.pieceEligible(piece, selectedTheme, depth, placements)) {
                continue;
            }
            eligible.add(entry);
            if (JigsawPoolSelection.needsMinimumPlacement(piece, placements)) {
                required.add(entry);
            }
        }
        return required.isEmpty() ? eligible : required;
    }

    private List<IrisJigsawPieceEntry> chanceEligibleEntries(
            List<IrisJigsawPieceEntry> entries,
            RNG rng
    ) {
        List<IrisJigsawPieceEntry> eligible = new ArrayList<>(entries.size());
        for (IrisJigsawPieceEntry entry : entries) {
            if (JigsawPoolSelection.passesChance(entry, rng)) {
                eligible.add(entry);
            }
        }
        return eligible;
    }

    private boolean pieceEnabled(IrisJigsawPiece piece) {
        return structure.resolvedMode() != IrisJigsawMode.PLANAR_JIGSAW
                || PlanarJigsawWorkcellResolver.workcell(planarWorkcells, piece).enabled();
    }

    private void incrementPlacement(Map<String, Integer> placementCounts, String pieceKey) {
        placementCounts.merge(normalize(pieceKey), 1, Integer::sum);
    }

    private String unsatisfiedMinimumPlacement(
            Map<String, Integer> placementCounts,
            String selectedTheme
    ) {
        for (Map.Entry<String, IrisJigsawPiece> entry : graph.getPieces().entrySet()) {
            IrisJigsawPiece piece = entry.getValue();
            if (!pieceEnabled(piece) || !piece.supportsTheme(selectedTheme)) {
                continue;
            }
            int actual = placementCounts.getOrDefault(entry.getKey(), 0);
            int minimum = piece.resolvedRules().getMinimumPlacements();
            if (actual < minimum) {
                return "Piece '" + entry.getKey() + "' requires at least " + minimum
                        + " placement(s) for theme '" + themeLabel(selectedTheme)
                        + "' but assembled " + actual;
            }
        }
        return "";
    }

    private String themeLabel(String selectedTheme) {
        return selectedTheme == null || selectedTheme.isBlank() ? "<unthemed>" : selectedTheme;
    }

    private String connectorLocation(OpenConnector connector) {
        return connector.wx() + "," + connector.wy() + "," + connector.wz()
                + " facing " + connector.facing();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private void requireConnector(IrisJigsawConnector connector, String pieceKey) {
        if (connector == null || connector.getPosition() == null || connector.getDirection() == null
                || connector.getTop() == null || connector.getName() == null
                || connector.getTargetName() == null || connector.getChannel() == null
                || connector.getJoint() == null || connector.getFinalState() == null
                || connector.getFinalState().isBlank()) {
            throw assemblyFailure("piece '" + pieceKey + "' contains a malformed connector");
        }
    }

    private IllegalStateException assemblyFailure(String detail) {
        return new IllegalStateException("Structure '" + structure.getLoadKey() + "' assembly failed: " + detail);
    }

    private static IllegalStateException invalidGraph(StructureGraphCompilation compilation) {
        String structureKey = compilation.getGraph().getStructureKey();
        StringBuilder detail = new StringBuilder();
        for (StructureGraphDiagnostic diagnostic : compilation.getDiagnostics()) {
            if (diagnostic.severity() != StructureGraphDiagnostic.Severity.ERROR) {
                continue;
            }
            if (!detail.isEmpty()) {
                detail.append("; ");
            }
            detail.append(diagnostic.message());
        }
        return new IllegalStateException("Structure '" + structureKey
                + "' assembly rejected its invalid graph: " + detail);
    }

    private record OpenConnector(PlacedStructurePiece sourcePiece, IrisJigsawConnector sourceConnector,
                                 int wx, int wy, int wz, IrisDirection facing, IrisDirection top,
                                 String pool, String name,
                                 String targetName, String channel, JigsawJoint joint, int depth,
                                 int pieceDepth, int placementPriority) {
    }

    private record PieceExpansion(List<OpenConnector> connectors, int placementPriority) {
    }

    private record ConnectorClosure(
            PlacedStructurePiece existingPiece,
            IrisJigsawConnector existingConnector,
            IrisJigsawConnector candidateConnector
    ) {
    }

    private record CapReservation(String pieceKey, PlacedStructurePiece piece) {
    }

    private enum AttachmentState {
        ATTACHED,
        CLOSED,
        TERMINATED,
        NO_MATCH,
        FAILED
    }

    private record AttachmentResult(AttachmentState state, String detail) {
        private AttachmentResult {
            Objects.requireNonNull(state);
            detail = detail == null ? "" : detail;
        }

        private static AttachmentResult success(AttachmentState state) {
            if (state == AttachmentState.NO_MATCH || state == AttachmentState.FAILED) {
                throw new IllegalArgumentException("A successful attachment requires a resolved state");
            }
            return new AttachmentResult(state, "");
        }

        private static AttachmentResult failed(String detail) {
            return new AttachmentResult(AttachmentState.FAILED, detail);
        }
    }

    private record AssemblyOptions(StructureGraphResolver resolver, CompiledStructureGraph graph,
                                   IrisStructure structure,
                                   IrisPosition origin) {
        private AssemblyOptions {
            Objects.requireNonNull(resolver, "Structure assembly resolver must not be null");
            Objects.requireNonNull(graph, "Compiled structure graph must not be null");
            Objects.requireNonNull(structure, "Structure assembly structure must not be null");
            Objects.requireNonNull(origin, "Structure assembly origin must not be null");
        }
    }
}
