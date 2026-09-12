package art.arcane.iris.studio.jigsaw;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.structure.authoring.StructureKey;
import art.arcane.iris.structure.authoring.StructureOwnershipManifest;
import art.arcane.iris.structure.authoring.StructureTransactionWriter;
import art.arcane.iris.structure.graph.PlanarJigsawWorkcellResolver;
import art.arcane.iris.pack.value.IrisDirection;
import art.arcane.iris.structure.jigsaw.IrisJigsawConnector;
import art.arcane.iris.structure.jigsaw.IrisJigsawMode;
import art.arcane.iris.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.structure.jigsaw.IrisJigsawPieceEntry;
import art.arcane.iris.structure.jigsaw.IrisJigsawPool;
import art.arcane.iris.structure.jigsaw.IrisJigsawWorkcellArchetype;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.pack.value.IrisPosition;
import art.arcane.iris.structure.placement.IrisStructure;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

public final class JigsawStudioGraphMapper {
    private JigsawStudioGraphMapper() {
    }

    public static JigsawStudioLayout map(IrisData data, IrisStructure structure) {
        Objects.requireNonNull(data, "Jigsaw Studio graph mapping requires pack data");
        Objects.requireNonNull(structure, "Jigsaw Studio graph mapping requires a structure");
        JigsawStudioMode mode = structure.resolvedMode() == IrisJigsawMode.PLANAR_JIGSAW
                ? JigsawStudioMode.PLANAR_JIGSAW
                : JigsawStudioMode.SPATIAL_JIGSAW;
        JigsawStudioVariantCatalog catalog = catalog(data, structure, mode);
        IrisPosition configuredCell = structure.getCellSize();
        JigsawStudioCellDimensions dimensions = configuredCell == null
                ? new JigsawStudioCellDimensions(15, 15, 15)
                : new JigsawStudioCellDimensions(
                        Math.max(1, configuredCell.getX()),
                        Math.max(1, configuredCell.getY()),
                        Math.max(1, configuredCell.getZ()));
        if (mode == JigsawStudioMode.SPATIAL_JIGSAW) {
            dimensions = expandSpatialDimensions(data, catalog, dimensions);
            return JigsawStudioLayout.createSpatial(
                    dimensions,
                    catalog,
                    structure.getSpatialWorkcellDisplayName());
        }
        Map<IrisJigsawWorkcellArchetype, PlanarJigsawWorkcellResolver.ResolvedWorkcell> resolved =
                PlanarJigsawWorkcellResolver.resolve(structure);
        List<JigsawStudioWorkcellSpec> workcells = new ArrayList<>(resolved.size());
        for (JigsawPlanarArchetype archetype : JigsawPlanarArchetype.values()) {
            PlanarJigsawWorkcellResolver.ResolvedWorkcell workcell = resolved.get(archetype.modelArchetype());
            workcells.add(new JigsawStudioWorkcellSpec(
                    archetype,
                    workcell.displayName(),
                    new JigsawStudioCellDimensions(
                            workcell.width(),
                            workcell.height(),
                            workcell.depth()),
                    workcell.enabled()));
        }
        return JigsawStudioLayout.createPlanar(dimensions, workcells, catalog);
    }

    public static JigsawStudioVariantCatalog catalog(
            IrisData data,
            IrisStructure structure,
            JigsawStudioMode mode
    ) {
        IrisData source = Objects.requireNonNull(data, "Jigsaw Studio catalog pack data");
        IrisStructure root = Objects.requireNonNull(structure, "Jigsaw Studio catalog structure");
        JigsawStudioMode activeMode = Objects.requireNonNull(mode, "Jigsaw Studio catalog mode");
        OwnedResources owned = ownedResources(source, root);
        Map<String, MutableVariant> variants = new LinkedHashMap<>();
        Set<String> visitedPools = new LinkedHashSet<>();
        Set<String> queuedPools = new HashSet<>();
        ArrayDeque<String> pools = new ArrayDeque<>();
        enqueuePool(root.getStartPool(), pools, queuedPools);
        traversePools(source, activeMode, owned, pools, queuedPools, visitedPools, variants);

        for (String poolKey : owned.poolKeys()) {
            enqueuePool(poolKey, pools, queuedPools);
        }
        traversePools(source, activeMode, owned, pools, queuedPools, visitedPools, variants);

        for (String pieceKey : owned.pieceKeys()) {
            addOwnedVariant(source, activeMode, owned, pieceKey, variants);
        }

        List<JigsawStudioVariant> built = new ArrayList<>(variants.size());
        for (MutableVariant variant : variants.values()) {
            built.add(variant.build(source));
        }
        return new JigsawStudioVariantCatalog(built, owned.editable());
    }

    public static JigsawPlanarTopology topologyOf(IrisJigsawPiece piece) {
        Objects.requireNonNull(piece, "Planar topology requires a jigsaw piece");
        int mask = 0;
        if (piece.getConnectors() != null) {
            for (IrisJigsawConnector connector : piece.getConnectors()) {
                if (connector == null || connector.getDirection() == null) {
                    continue;
                }
                mask |= directionBit(connector.getDirection());
            }
        }
        return JigsawPlanarTopology.fromMask(mask);
    }

    static JigsawStudioCellDimensions expandSpatialDimensions(
            IrisData data,
            JigsawStudioVariantCatalog catalog,
            JigsawStudioCellDimensions configured
    ) {
        if (data.getObjectLoader() == null) {
            return configured;
        }
        int width = configured.width();
        int height = configured.height();
        int depth = configured.depth();
        for (JigsawStudioVariant variant : catalog.spatialVariants()) {
            IrisObject object = data.getObjectLoader().load(variant.objectKey());
            if (object == null) {
                continue;
            }
            int objectWidth = object.getW();
            int objectDepth = object.getD();
            if (variant.rotatable()) {
                int horizontalSpan = Math.max(objectWidth, objectDepth);
                objectWidth = horizontalSpan;
                objectDepth = horizontalSpan;
            }
            width = Math.max(width, objectWidth);
            height = Math.max(height, object.getH());
            depth = Math.max(depth, objectDepth);
        }
        return new JigsawStudioCellDimensions(width, height, depth);
    }

    private static void traversePools(
            IrisData data,
            JigsawStudioMode mode,
            OwnedResources owned,
            ArrayDeque<String> pools,
            Set<String> queuedPools,
            Set<String> visitedPools,
            Map<String, MutableVariant> variants
    ) {
        while (!pools.isEmpty()) {
            String poolKey = pools.removeFirst();
            if (!visitedPools.add(poolKey)) {
                continue;
            }
            IrisJigsawPool pool = data.getJigsawPoolLoader().load(poolKey);
            if (pool == null) {
                continue;
            }
            enqueuePool(pool.getFallback(), pools, queuedPools);
            if (pool.getPieces() == null) {
                continue;
            }
            for (int entryIndex = 0; entryIndex < pool.getPieces().size(); entryIndex++) {
                IrisJigsawPieceEntry entry = pool.getPieces().get(entryIndex);
                if (entry == null || entry.isEmpty() || entry.getPiece() == null || entry.getPiece().isBlank()) {
                    continue;
                }
                String pieceKey = entry.getPiece();
                IrisJigsawPiece piece = data.getJigsawPieceLoader().load(pieceKey);
                if (piece == null || piece.getObject() == null || piece.getObject().isBlank()) {
                    continue;
                }
                MutableVariant variant = variants.computeIfAbsent(
                        pieceKey,
                        key -> new MutableVariant(
                                key,
                                piece,
                                mode,
                                owned.owns(key, piece.getObject())));
                variant.addMembership(new JigsawStudioPoolMembership(
                        poolKey,
                        entryIndex,
                        entry.getWeight(),
                        entry.getChance()));
                enqueueConnectorPools(piece, pools, queuedPools);
            }
        }
    }

    private static void addOwnedVariant(
            IrisData data,
            JigsawStudioMode mode,
            OwnedResources owned,
            String pieceKey,
            Map<String, MutableVariant> variants
    ) {
        if (variants.containsKey(pieceKey)) {
            return;
        }
        IrisJigsawPiece piece = data.getJigsawPieceLoader().load(pieceKey);
        if (piece == null || piece.getObject() == null || piece.getObject().isBlank()) {
            return;
        }
        variants.put(pieceKey, new MutableVariant(pieceKey, piece, mode, owned.owns(pieceKey, piece.getObject())));
    }

    private static OwnedResources ownedResources(IrisData data, IrisStructure structure) {
        String structureKey = structure.getLoadKey();
        File dataFolder = data.getDataFolder();
        if (structureKey == null || structureKey.isBlank() || dataFolder == null) {
            return OwnedResources.empty();
        }
        try {
            Path root = dataFolder.toPath().toAbsolutePath().normalize();
            StructureTransactionWriter writer = new StructureTransactionWriter(root);
            Path manifestPath = writer.ownershipManifestPath(StructureKey.parse(structureKey, "iris"));
            if (!Files.isRegularFile(manifestPath, LinkOption.NOFOLLOW_LINKS)) {
                return OwnedResources.empty();
            }
            StructureOwnershipManifest manifest = StructureOwnershipManifest.fromJson(Files.readAllBytes(manifestPath));
            Set<String> pieceKeys = new TreeSet<>();
            Set<String> poolKeys = new TreeSet<>();
            Set<String> objectKeys = new TreeSet<>();
            for (String path : manifest.resourceHashes().keySet()) {
                addOwnedKey(path, "jigsaw-pieces/", ".json", pieceKeys);
                addOwnedKey(path, "jigsaw-pools/", ".json", poolKeys);
                addOwnedKey(path, "objects/", ".iob", objectKeys);
            }
            boolean editable = JigsawStudioAuthoringAccess.isEditable(manifest);
            return new OwnedResources(pieceKeys, poolKeys, objectKeys, editable);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to read Jigsaw Studio ownership for structure '" + structureKey + "'",
                    exception);
        }
    }

    private static void addOwnedKey(String path, String prefix, String suffix, Set<String> keys) {
        if (path.startsWith(prefix) && path.endsWith(suffix) && path.length() > prefix.length() + suffix.length()) {
            keys.add(path.substring(prefix.length(), path.length() - suffix.length()));
        }
    }

    private static int directionBit(IrisDirection direction) {
        return switch (direction) {
            case NORTH_NEGATIVE_Z -> JigsawPlanarDirection.NORTH.bit();
            case EAST_POSITIVE_X -> JigsawPlanarDirection.EAST.bit();
            case SOUTH_POSITIVE_Z -> JigsawPlanarDirection.SOUTH.bit();
            case WEST_NEGATIVE_X -> JigsawPlanarDirection.WEST.bit();
            case UP_POSITIVE_Y, DOWN_NEGATIVE_Y -> 0;
        };
    }

    private static void enqueueConnectorPools(
            IrisJigsawPiece piece,
            ArrayDeque<String> pools,
            Set<String> queuedPools
    ) {
        if (piece.getConnectors() == null) {
            return;
        }
        for (IrisJigsawConnector connector : piece.getConnectors()) {
            if (connector != null) {
                enqueuePool(connector.getPool(), pools, queuedPools);
            }
        }
    }

    private static void enqueuePool(String poolKey, ArrayDeque<String> pools, Set<String> queuedPools) {
        if (poolKey == null || poolKey.isBlank() || !queuedPools.add(poolKey)) {
            return;
        }
        pools.addLast(poolKey);
    }

    private static final class MutableVariant {
        private final String pieceKey;
        private final IrisJigsawPiece piece;
        private final JigsawStudioMode mode;
        private final boolean owned;
        private final List<JigsawStudioPoolMembership> memberships = new ArrayList<>();

        private MutableVariant(
                String pieceKey,
                IrisJigsawPiece piece,
                JigsawStudioMode mode,
                boolean owned
        ) {
            this.pieceKey = pieceKey;
            this.piece = piece;
            this.mode = mode;
            this.owned = owned;
        }

        private void addMembership(JigsawStudioPoolMembership membership) {
            memberships.add(membership);
        }

        private JigsawStudioVariant build(IrisData data) {
            Optional<JigsawStudioCellDimensions> dimensions = objectDimensions(data, piece, mode);
            return new JigsawStudioVariant(
                    pieceKey,
                    piece.getObject(),
                    piece.getDisplayName(),
                    dimensions,
                    mode,
                    mode == JigsawStudioMode.PLANAR_JIGSAW
                            ? Optional.of(topologyOf(piece))
                            : Optional.empty(),
                    piece.isRotatable(),
                    owned,
                    piece.getThemes() == null ? List.of() : piece.getThemes(),
                    JigsawStudioPieceRules.from(piece.resolvedRules()),
                    memberships);
        }

        private static Optional<JigsawStudioCellDimensions> objectDimensions(
                IrisData data,
                IrisJigsawPiece piece,
                JigsawStudioMode mode
        ) {
            if (data.getObjectLoader() == null) {
                return Optional.empty();
            }
            IrisObject object = data.getObjectLoader().load(piece.getObject());
            if (object == null) {
                return Optional.empty();
            }
            JigsawStudioCellDimensions sourceDimensions = new JigsawStudioCellDimensions(
                    object.getW(),
                    object.getH(),
                    object.getD());
            if (mode == JigsawStudioMode.SPATIAL_JIGSAW) {
                return Optional.of(sourceDimensions);
            }
            JigsawPlanarTopology topology = topologyOf(piece);
            int quarterTurns = JigsawPlanarArchetype.fromTopology(topology)
                    .sourceToCanonicalQuarterTurns(topology);
            return Math.floorMod(quarterTurns, 2) == 0
                    ? Optional.of(sourceDimensions)
                    : Optional.of(new JigsawStudioCellDimensions(
                    sourceDimensions.depth(),
                    sourceDimensions.height(),
                    sourceDimensions.width()));
        }
    }

    private record OwnedResources(
            Set<String> pieceKeys,
            Set<String> poolKeys,
            Set<String> objectKeys,
            boolean editable
    ) {
        private OwnedResources {
            pieceKeys = Collections.unmodifiableSet(new LinkedHashSet<>(pieceKeys));
            poolKeys = Collections.unmodifiableSet(new LinkedHashSet<>(poolKeys));
            objectKeys = Collections.unmodifiableSet(new LinkedHashSet<>(objectKeys));
        }

        private boolean owns(String pieceKey, String objectKey) {
            return editable && pieceKeys.contains(pieceKey) && objectKeys.contains(objectKey);
        }

        private static OwnedResources empty() {
            return new OwnedResources(Set.of(), Set.of(), Set.of(), false);
        }
    }
}
