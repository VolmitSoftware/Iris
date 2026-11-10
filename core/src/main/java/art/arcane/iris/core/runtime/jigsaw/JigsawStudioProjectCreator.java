package art.arcane.iris.core.runtime.jigsaw;

import art.arcane.iris.core.structure.authoring.StructureBackend;
import art.arcane.iris.core.structure.authoring.StructureCapability;
import art.arcane.iris.core.structure.authoring.StructureKey;
import art.arcane.iris.core.structure.authoring.StructureResourceBundle;
import art.arcane.iris.core.structure.authoring.StructureSource;
import art.arcane.iris.core.structure.authoring.StructureTransactionWriter;
import art.arcane.iris.core.structure.authoring.StructureWriteMode;
import art.arcane.iris.core.structure.authoring.StructureWriteResult;
import art.arcane.iris.engine.framework.structure.StructureResourceBundleGraphCompiler;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawBranchFailurePolicy;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawCompatibility;
import art.arcane.iris.engine.object.structure.jigsaw.JigsawDirection;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawConnector;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawMode;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawPieceEntry;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawPieceRules;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawPool;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawThemeSet;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawWorkcell;
import art.arcane.iris.core.structure.DeclaredObjects;
import art.arcane.iris.engine.object.structure.IrisStructure;
import com.volmit.geometry.BlockPosition;
import com.volmit.tooling.ObjectCodec;
import art.arcane.iris.engine.object.structure.jigsaw.JigsawJoint;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class JigsawStudioProjectCreator {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<JigsawDirection> SPATIAL_CONNECTOR_ORDER = List.of(
            JigsawDirection.NORTH_NEGATIVE_Z,
            JigsawDirection.SOUTH_POSITIVE_Z,
            JigsawDirection.EAST_POSITIVE_X,
            JigsawDirection.WEST_NEGATIVE_X,
            JigsawDirection.UP_POSITIVE_Y,
            JigsawDirection.DOWN_NEGATIVE_Y);

    private JigsawStudioProjectCreator() {
    }

    public static StructureWriteResult create(Path packRoot, ObjectCodec codec, Options options) throws IOException {
        Options activeOptions = Objects.requireNonNull(options, "Jigsaw Studio project options");
        StructureResourceBundle bundle = bundle(codec, activeOptions);
        StructureResourceBundleGraphCompiler.requireViable(bundle);
        return new StructureTransactionWriter(packRoot).write(bundle, StructureWriteMode.ADD_ONLY);
    }

    static StructureResourceBundle bundle(ObjectCodec codec, Options options) throws IOException {
        String resourceKey = options.structureKey();
        StructureKey ownershipKey = new StructureKey("iris", resourceKey);
        IrisStructure structure = new IrisStructure()
                .setStartPool(resourceKey + "/start")
                .setMaxDepth(7)
                .setMaxSizeChunks(8)
                .setMode(toModelMode(options.mode()))
                .setCompatibility(toModelCompatibility(options.compatibilityTarget()))
                .setBranchFailurePolicy(toBranchFailurePolicy(options.compatibilityTarget()))
                .setCellSize(new BlockPosition(
                        options.cellDimensions().width(),
                        options.cellDimensions().height(),
                        options.cellDimensions().depth()));
        if (options.compatibilityTarget() == JigsawStudioCompatibilityTarget.IRIS_EXTENDED) {
            structure.getThemeSets().add(new IrisJigsawThemeSet("variant-1", 1));
        }
        StructureResourceBundle.Builder bundle = StructureResourceBundle.builder(ownershipKey)
                .source(StructureSource.of(StructureSource.Kind.IRIS, ownershipKey))
                .backend(StructureBackend.IRIS_ASSEMBLY)
                .capability(StructureCapability.BLOCKS)
                .capability(StructureCapability.CONNECTORS)
                .capability(StructureCapability.IRIS_PLACEMENT);
        IrisJigsawPool pool = new IrisJigsawPool();
        if (options.mode() == JigsawStudioMode.PLANAR_JIGSAW) {
            addPlanarDefaults(bundle, codec, structure, pool, options);
        } else {
            addSpatialDefaults(bundle, codec, pool, options);
        }
        bundle.textResource("jigsaw-pools/" + resourceKey + "/start.json", GSON.toJson(pool) + "\n");
        bundle.textResource("structures/" + resourceKey + ".json", GSON.toJson(structure) + "\n");
        return bundle.build();
    }

    private static IrisJigsawMode toModelMode(JigsawStudioMode mode) {
        return mode == JigsawStudioMode.PLANAR_JIGSAW
                ? IrisJigsawMode.PLANAR_JIGSAW
                : IrisJigsawMode.SPATIAL_JIGSAW;
    }

    private static IrisJigsawCompatibility toModelCompatibility(
            JigsawStudioCompatibilityTarget compatibilityTarget
    ) {
        return compatibilityTarget == JigsawStudioCompatibilityTarget.VANILLA_PORTABLE
                ? IrisJigsawCompatibility.VANILLA_PORTABLE
                : IrisJigsawCompatibility.IRIS_EXTENDED;
    }

    private static IrisJigsawBranchFailurePolicy toBranchFailurePolicy(
            JigsawStudioCompatibilityTarget compatibilityTarget
    ) {
        return compatibilityTarget == JigsawStudioCompatibilityTarget.VANILLA_PORTABLE
                ? IrisJigsawBranchFailurePolicy.TERMINATE_BRANCH
                : IrisJigsawBranchFailurePolicy.FAIL_ASSEMBLY;
    }

    private static void addPlanarDefaults(
            StructureResourceBundle.Builder bundle,
            ObjectCodec codec,
            IrisStructure structure,
            IrisJigsawPool pool,
            Options options
    ) throws IOException {
        JigsawStudioCellDimensions dimensions = options.cellDimensions();
        BlockPosition size = new BlockPosition(dimensions.width(), dimensions.height(), dimensions.depth());
        String piecePoolKey = options.structureKey() + "/pieces";
        String capPoolKey = options.structureKey() + "/caps";
        IrisJigsawPool piecePool = new IrisJigsawPool().setFallback(capPoolKey);
        IrisJigsawPool capPool = new IrisJigsawPool();
        for (JigsawPlanarArchetype archetype : JigsawPlanarArchetype.values()) {
            String key = options.structureKey() + "/" + archetype.name().toLowerCase(Locale.ROOT);
            IrisJigsawPiece piece = planarPiece(key, piecePoolKey, size, archetype);
            if (options.compatibilityTarget() == JigsawStudioCompatibilityTarget.IRIS_EXTENDED) {
                piece.getThemes().add("variant-1");
            }
            if (archetype == JigsawPlanarArchetype.CROSS) {
                pool.getPieces().add(new IrisJigsawPieceEntry(key, 1));
            }
            if (archetype != JigsawPlanarArchetype.BLANK) {
                piecePool.getPieces().add(new IrisJigsawPieceEntry(key, 1));
            }
            if (archetype == JigsawPlanarArchetype.END) {
                if (options.compatibilityTarget() == JigsawStudioCompatibilityTarget.IRIS_EXTENDED) {
                    piece.setRules(new IrisJigsawPieceRules().setTerminal(true));
                }
                capPool.getPieces().add(new IrisJigsawPieceEntry(key, 1));
            }
            structure.getPlanarWorkcells().add(new IrisJigsawWorkcell(
                    "",
                    archetype.modelArchetype(),
                    dimensions.width(),
                    dimensions.height(),
                    dimensions.depth(),
                    true));
            bundle.resource("objects/" + key + codec.extension(), codec.encode(DeclaredObjects.empty(
                    dimensions.width(),
                    dimensions.height(),
                    dimensions.depth())));
            bundle.textResource("jigsaw-pieces/" + key + ".json", GSON.toJson(piece) + "\n");
        }
        capPool.getPieces().add(new IrisJigsawPieceEntry().setEmpty(true));
        bundle.textResource("jigsaw-pools/" + piecePoolKey + ".json", GSON.toJson(piecePool) + "\n");
        bundle.textResource("jigsaw-pools/" + capPoolKey + ".json", GSON.toJson(capPool) + "\n");
    }

    private static void addSpatialDefaults(
            StructureResourceBundle.Builder bundle,
            ObjectCodec codec,
            IrisJigsawPool pool,
            Options options
    ) throws IOException {
        JigsawStudioCellDimensions dimensions = options.cellDimensions();
        String piecePoolKey = options.structureKey() + "/pieces";
        IrisJigsawPool piecePool = new IrisJigsawPool();
        for (int connectorCount = 0; connectorCount <= SPATIAL_CONNECTOR_ORDER.size(); connectorCount++) {
            String key = options.structureKey() + "/"
                    + (connectorCount == 0 ? "start" : "connectors-" + connectorCount);
            IrisJigsawPiece piece = spatialPiece(
                    key,
                    piecePoolKey,
                    dimensions,
                    connectorCount);
            if (options.compatibilityTarget() == JigsawStudioCompatibilityTarget.IRIS_EXTENDED) {
                piece.getThemes().add("variant-1");
            }
            pool.getPieces().add(new IrisJigsawPieceEntry(key, 1));
            if (connectorCount > 0) {
                piecePool.getPieces().add(new IrisJigsawPieceEntry(key, 1));
            }
            bundle.resource("objects/" + key + codec.extension(), codec.encode(DeclaredObjects.empty(
                    dimensions.width(),
                    dimensions.height(),
                    dimensions.depth())));
            bundle.textResource("jigsaw-pieces/" + key + ".json", GSON.toJson(piece) + "\n");
        }
        piecePool.getPieces().add(new IrisJigsawPieceEntry().setEmpty(true));
        bundle.textResource("jigsaw-pools/" + piecePoolKey + ".json", GSON.toJson(piecePool) + "\n");
    }

    private static IrisJigsawPiece spatialPiece(
            String objectKey,
            String poolKey,
            JigsawStudioCellDimensions dimensions,
            int connectorCount
    ) {
        IrisJigsawPiece piece = new IrisJigsawPiece()
                .setDisplayName(connectorCount + (connectorCount == 1 ? " Connector" : " Connectors"))
                .setObject(objectKey)
                .setRotatable(true)
                .setRules(new IrisJigsawPieceRules().setMaximumPlacements(16));
        for (int index = 0; index < connectorCount; index++) {
            JigsawDirection direction = SPATIAL_CONNECTOR_ORDER.get(index);
            piece.getConnectors().add(new IrisJigsawConnector()
                    .setPosition(spatialConnectorPosition(dimensions, direction))
                    .setDirection(direction)
                    .setTop(direction.isVertical()
                            ? JigsawDirection.NORTH_NEGATIVE_Z
                            : JigsawDirection.UP_POSITIVE_Y)
                    .setPool(poolKey)
                    .setName("iris:spatial")
                    .setTargetName("iris:spatial")
                    .setJoint(JigsawJoint.ROLLABLE)
                    .setFinalState("minecraft:structure_void"));
        }
        return piece;
    }

    private static BlockPosition spatialConnectorPosition(
            JigsawStudioCellDimensions dimensions,
            JigsawDirection direction
    ) {
        int centerX = dimensions.width() / 2;
        int centerY = dimensions.height() / 2;
        int centerZ = dimensions.depth() / 2;
        return switch (direction) {
            case NORTH_NEGATIVE_Z -> new BlockPosition(centerX, centerY, 0);
            case SOUTH_POSITIVE_Z -> new BlockPosition(centerX, centerY, dimensions.depth() - 1);
            case EAST_POSITIVE_X -> new BlockPosition(dimensions.width() - 1, centerY, centerZ);
            case WEST_NEGATIVE_X -> new BlockPosition(0, centerY, centerZ);
            case UP_POSITIVE_Y -> new BlockPosition(centerX, dimensions.height() - 1, centerZ);
            case DOWN_NEGATIVE_Y -> new BlockPosition(centerX, 0, centerZ);
        };
    }

    private static IrisJigsawPiece planarPiece(
            String objectKey,
            String poolKey,
            BlockPosition dimensions,
            JigsawPlanarArchetype archetype
    ) {
        IrisJigsawPiece piece = new IrisJigsawPiece().setObject(objectKey).setRotatable(true);
        for (JigsawPlanarDirection planarDirection : archetype.canonicalTopology().directions()) {
            JigsawDirection direction = planarDirection.direction();
            piece.getConnectors().add(new IrisJigsawConnector()
                    .setPosition(IrisJigsawConnector.canonicalPlanarPosition(dimensions, direction))
                    .setDirection(direction)
                    .setTop(JigsawDirection.UP_POSITIVE_Y)
                    .setPool(poolKey)
                    .setName("iris:planar")
                    .setTargetName("iris:planar")
                    .setJoint(JigsawJoint.ALIGNED)
                    .setFinalState("minecraft:structure_void"));
        }
        return piece;
    }

    public record Options(
            String structureKey,
            JigsawStudioMode mode,
            JigsawStudioCompatibilityTarget compatibilityTarget,
            JigsawStudioCellDimensions cellDimensions
    ) {
        public Options {
            structureKey = requireResourceKey(structureKey);
            mode = Objects.requireNonNull(mode, "Jigsaw Studio mode");
            compatibilityTarget = Objects.requireNonNull(
                    compatibilityTarget,
                    "Jigsaw Studio compatibility target");
            cellDimensions = Objects.requireNonNull(cellDimensions, "Jigsaw Studio cell dimensions");
            if (mode == JigsawStudioMode.PLANAR_JIGSAW
                    && (cellDimensions.width() < 3 || cellDimensions.depth() < 3)) {
                throw new IllegalArgumentException(
                        "Planar Jigsaw Studio cells require width and depth of at least 3 blocks");
            }
        }

        static String requireResourceKey(String value) {
            String key = JigsawStudioMarkerKeyCodec.requireInternalPath(value, "resource");
            StructureResourceBundle.validateRelativePath("structures/" + key + ".json");
            new StructureKey("iris", key);
            return key;
        }
    }
}
