package art.arcane.iris.engine.platform.studio.generators;

import art.arcane.iris.engine.framework.structure.StructureResources;
import art.arcane.iris.core.runtime.jigsaw.JigsawPlanarDirection;
import art.arcane.iris.core.runtime.jigsaw.JigsawPlanarTopology;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioBay;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioBounds;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioCellDimensions;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioControlPosition;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioLayout;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioSession;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioVariant;
import art.arcane.iris.core.service.JigsawStudioBlocks;
import art.arcane.iris.core.service.JigsawStudioRequest;
import art.arcane.iris.engine.framework.structure.assembly.PieceTurn;
import art.arcane.iris.engine.object.structure.jigsaw.JigsawDirection;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawConnector;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.engine.object.structure.jigsaw.JigsawJoint;
import art.arcane.volmlib.util.collection.KList;
import com.volmit.block.BlockRegistry;
import com.volmit.block.BlockState;
import com.volmit.geometry.BlockPosition;
import com.volmit.object.BlockEntityData;
import com.volmit.object.ObjectBlock;
import com.volmit.object.PlaceableObject;
import com.volmit.resource.PackResources;
import com.volmit.tooling.ToolPack;
import com.volmit.tooling.world.ChunkCanvas;
import com.volmit.tooling.world.ChunkPainter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class JigsawStudioGenerator implements ChunkPainter {
    private static final int CHECKER_SIZE = 4;
    private static final int CHUNK_SIZE = 16;

    private final ToolPack pack;
    private final JigsawStudioRequest request;
    private final JigsawStudioSession session;
    private final BlockState lightFloor;
    private final BlockState darkFloor;
    private final BlockState frame;
    private final BlockState topologyBase;
    private final BlockState topologyPath;
    private final BlockState connectorCap;
    private final BlockState invalidMarker;
    private final BlockState controlChest;
    private final FinalStateRenderer finalStateRenderer;
    private final ConcurrentMap<RenderKey, RenderedBay> renderedBays = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, BlockState> jigsawStates = new ConcurrentHashMap<>();

    public JigsawStudioGenerator(
            ToolPack pack,
            JigsawStudioRequest request,
            JigsawStudioSession session
    ) {
        this(
                pack,
                request,
                session,
                BlockState.parse("minecraft:smooth_stone"),
                BlockState.parse("minecraft:polished_deepslate"),
                BlockState.parse("minecraft:white_concrete"),
                BlockState.parse("minecraft:light_gray_wool"),
                BlockState.parse("minecraft:red_wool"),
                BlockState.parse("minecraft:sea_lantern"),
                BlockState.parse("minecraft:red_stained_glass"),
                BlockState.parse("minecraft:chest[facing=south,type=single,waterlogged=false]"),
                null
        );
    }

    JigsawStudioGenerator(
            ToolPack pack,
            JigsawStudioRequest request,
            JigsawStudioSession session,
            BlockState lightFloor,
            BlockState darkFloor,
            BlockState frame,
            BlockState topologyBase,
            BlockState topologyPath,
            BlockState connectorCap,
            BlockState invalidMarker,
            BlockState controlChest,
            FinalStateRenderer finalStateRenderer
    ) {
        this.pack = Objects.requireNonNull(pack, "Jigsaw Studio pack");
        this.request = Objects.requireNonNull(request, "Jigsaw Studio request");
        this.session = Objects.requireNonNull(session, "Jigsaw Studio session");
        this.lightFloor = Objects.requireNonNull(lightFloor, "Jigsaw Studio light floor block");
        this.darkFloor = Objects.requireNonNull(darkFloor, "Jigsaw Studio dark floor block");
        this.frame = Objects.requireNonNull(frame, "Jigsaw Studio frame block");
        this.topologyBase = Objects.requireNonNull(topologyBase, "Jigsaw Studio topology base block");
        this.topologyPath = Objects.requireNonNull(topologyPath, "Jigsaw Studio topology path block");
        this.connectorCap = Objects.requireNonNull(connectorCap, "Jigsaw Studio connector cap block");
        this.invalidMarker = Objects.requireNonNull(invalidMarker, "Jigsaw Studio invalid marker block");
        this.controlChest = Objects.requireNonNull(controlChest, "Jigsaw Studio control chest block");
        this.finalStateRenderer = finalStateRenderer == null ? this::rotatedFinalState : finalStateRenderer;
        if (!request.requestId().equals(session.sessionId())) {
            throw new IllegalArgumentException("Jigsaw Studio request and session IDs do not match");
        }
        if (request.mode() != session.layout().mode()) {
            throw new IllegalArgumentException("Jigsaw Studio request and session modes do not match");
        }
    }

    public ToolPack getPack() {
        return pack;
    }

    public JigsawStudioRequest getRequest() {
        return request;
    }

    public JigsawStudioSession getSession() {
        return session;
    }

    public JigsawStudioLayout getLayout() {
        return session.layout();
    }

    public RenderedBay renderBay(JigsawStudioBay workcell) {
        JigsawStudioBay activeWorkcell = requireWorkcell(workcell);
        JigsawStudioSession.WorkcellSnapshot snapshot = session.workcellSnapshot(activeWorkcell.stableId());
        if (snapshot.activeVariantKey().isEmpty()) {
            return RenderedBay.empty(activeWorkcell.bounds().dimensions());
        }
        JigsawStudioVariant variant = session.activeVariant(activeWorkcell.stableId()).orElse(null);
        if (variant == null) {
            return RenderedBay.invalid(
                    activeWorkcell.bounds().dimensions(),
                    "active variant '" + snapshot.activeVariantKey() + "' is missing from the Studio catalog");
        }
        return renderVariant(activeWorkcell, variant, snapshot.loadGeneration());
    }

    public RenderedBay renderVariant(JigsawStudioBay workcell, JigsawStudioVariant variant) {
        return buildRenderedBay(requireAcceptedVariant(workcell, variant), variant);
    }

    public RenderedBay renderVariant(
            JigsawStudioBay workcell,
            JigsawStudioVariant variant,
            long loadGeneration
    ) {
        JigsawStudioBay activeWorkcell = requireAcceptedVariant(workcell, variant);
        RenderKey key = new RenderKey(activeWorkcell.stableId(), variant.pieceKey(), loadGeneration);
        return renderedBays.computeIfAbsent(key, ignored -> buildRenderedBay(activeWorkcell, variant));
    }

    public void invalidateRender(String workcellId) {
        if (workcellId != null) {
            renderedBays.keySet().removeIf(key -> key.workcellId().equals(workcellId));
        }
    }

    public void invalidateRender(String workcellId, String pieceKey) {
        if (workcellId != null && pieceKey != null) {
            renderedBays.keySet().removeIf(key -> key.workcellId().equals(workcellId)
                    && key.pieceKey().equals(pieceKey));
        }
    }

    @Override
    public void paint(int chunkX, int chunkZ, ChunkCanvas canvas) {
        paintChunk(canvas, chunkX, chunkZ);
    }

    public void paintChunk(ChunkCanvas canvas, int chunkX, int chunkZ) {
        Objects.requireNonNull(canvas, "Jigsaw Studio chunk canvas");
        int floorY = Math.max(canvas.minY(), JigsawStudioLayout.FLOOR_Y);
        if (floorY >= canvas.maxY()) {
            return;
        }

        int chunkWorldX = chunkX << 4;
        int chunkWorldZ = chunkZ << 4;
        paintFloor(canvas, floorY, chunkWorldX, chunkWorldZ);
        paintControlChest(canvas, chunkWorldX, chunkWorldZ);

        int chunkMaxX = chunkWorldX + 15;
        int chunkMaxZ = chunkWorldZ + 15;
        for (JigsawStudioBay workcell : getLayout().bays()) {
            JigsawStudioBounds bounds = workcell.bounds();
            if (!bounds.intersectsHorizontal(
                    chunkWorldX - 1,
                    chunkWorldZ - 1,
                    chunkMaxX + 1,
                    chunkMaxZ + 1)) {
                continue;
            }
            paintCage(canvas, workcell, chunkWorldX, chunkWorldZ);
            if (workcell.archetype().isPresent()) {
                paintTopologyGlyph(canvas, workcell, chunkWorldX, chunkWorldZ);
            }
            RenderedBay renderedBay = renderBay(workcell);
            if (!renderedBay.valid()) {
                paintInvalidBay(canvas, workcell, chunkWorldX, chunkWorldZ);
                continue;
            }
            paintObject(canvas, workcell, renderedBay, chunkWorldX, chunkWorldZ);
            if (session.workcellSnapshot(workcell.stableId()).connectorsVisible()) {
                paintConnectors(canvas, workcell, renderedBay, chunkWorldX, chunkWorldZ);
            } else {
                paintHiddenConnectorFinalStates(
                        canvas,
                        workcell,
                        renderedBay,
                        chunkWorldX,
                        chunkWorldZ);
            }
        }
    }

    private void paintFloor(ChunkCanvas canvas, int floorY, int chunkWorldX, int chunkWorldZ) {
        for (int localX = 0; localX < CHUNK_SIZE; localX++) {
            for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {
                int worldX = chunkWorldX + localX;
                int worldZ = chunkWorldZ + localZ;
                int tileX = Math.floorDiv(worldX, CHECKER_SIZE);
                int tileZ = Math.floorDiv(worldZ, CHECKER_SIZE);
                BlockState block = ((tileX + tileZ) & 1) == 0 ? lightFloor : darkFloor;
                canvas.set(localX, floorY, localZ, block);
            }
        }
    }

    private void paintControlChest(ChunkCanvas canvas, int chunkWorldX, int chunkWorldZ) {
        JigsawStudioControlPosition control = getLayout().controlPosition();
        setWorldBlock(
                canvas,
                control.worldX(),
                control.worldY(),
                control.worldZ(),
                controlChest,
                chunkWorldX,
                chunkWorldZ);
    }

    private void paintCage(
            ChunkCanvas canvas,
            JigsawStudioBay workcell,
            int chunkWorldX,
            int chunkWorldZ
    ) {
        JigsawStudioBounds bounds = workcell.bounds();
        int minimumX = bounds.originX() - 1;
        int maximumX = bounds.maxX() + 1;
        int minimumZ = bounds.originZ() - 1;
        int maximumZ = bounds.maxZ() + 1;
        int bottomY = bounds.originY();
        int topY = bounds.maxY() + 1;

        paintRectangle(
                canvas,
                minimumX,
                maximumX,
                bottomY,
                minimumZ,
                maximumZ,
                chunkWorldX,
                chunkWorldZ);
        paintRectangle(
                canvas,
                minimumX,
                maximumX,
                topY,
                minimumZ,
                maximumZ,
                chunkWorldX,
                chunkWorldZ);
        for (int y = bottomY + 1; y < topY; y++) {
            setWorldBlock(canvas, minimumX, y, minimumZ, frame, chunkWorldX, chunkWorldZ);
            setWorldBlock(canvas, maximumX, y, minimumZ, frame, chunkWorldX, chunkWorldZ);
            setWorldBlock(canvas, minimumX, y, maximumZ, frame, chunkWorldX, chunkWorldZ);
            setWorldBlock(canvas, maximumX, y, maximumZ, frame, chunkWorldX, chunkWorldZ);
        }
    }

    private void paintRectangle(
            ChunkCanvas canvas,
            int minimumX,
            int maximumX,
            int y,
            int minimumZ,
            int maximumZ,
            int chunkWorldX,
            int chunkWorldZ
    ) {
        for (int x = minimumX; x <= maximumX; x++) {
            setWorldBlock(canvas, x, y, minimumZ, frame, chunkWorldX, chunkWorldZ);
            setWorldBlock(canvas, x, y, maximumZ, frame, chunkWorldX, chunkWorldZ);
        }
        for (int z = minimumZ + 1; z < maximumZ; z++) {
            setWorldBlock(canvas, minimumX, y, z, frame, chunkWorldX, chunkWorldZ);
            setWorldBlock(canvas, maximumX, y, z, frame, chunkWorldX, chunkWorldZ);
        }
    }

    private void paintTopologyGlyph(
            ChunkCanvas canvas,
            JigsawStudioBay workcell,
            int chunkWorldX,
            int chunkWorldZ
    ) {
        JigsawPlanarTopology topology = workcell.topology().orElseThrow();
        JigsawStudioBounds bounds = workcell.bounds();
        int minimumX = Math.max(bounds.originX(), chunkWorldX);
        int maximumX = Math.min(bounds.maxX(), chunkWorldX + 15);
        int minimumZ = Math.max(bounds.originZ(), chunkWorldZ);
        int maximumZ = Math.min(bounds.maxZ(), chunkWorldZ + 15);
        if (minimumX > maximumX || minimumZ > maximumZ) {
            return;
        }
        int glyphY = bounds.originY() - 1;
        int centerX = bounds.dimensions().width() / 2;
        int centerZ = bounds.dimensions().depth() / 2;
        for (int worldX = minimumX; worldX <= maximumX; worldX++) {
            int localX = worldX - bounds.originX();
            for (int worldZ = minimumZ; worldZ <= maximumZ; worldZ++) {
                int localZ = worldZ - bounds.originZ();
                BlockState state = topologyGlyphState(topology, bounds.dimensions(), localX, localZ,
                        centerX, centerZ);
                setWorldBlock(canvas, worldX, glyphY, worldZ, state, chunkWorldX, chunkWorldZ);
            }
        }
    }

    private BlockState topologyGlyphState(
            JigsawPlanarTopology topology,
            JigsawStudioCellDimensions dimensions,
            int localX,
            int localZ,
            int centerX,
            int centerZ
    ) {
        if (isConnectorCap(topology, dimensions, localX, localZ, centerX, centerZ)) {
            return connectorCap;
        }
        if (isTopologyPath(topology, localX, localZ, centerX, centerZ)) {
            return topologyPath;
        }
        return topologyBase;
    }

    static boolean isConnectorCap(
            JigsawPlanarTopology topology,
            JigsawStudioCellDimensions dimensions,
            int localX,
            int localZ,
            int centerX,
            int centerZ
    ) {
        return topology.connects(JigsawPlanarDirection.NORTH) && localX == centerX && localZ == 0
                || topology.connects(JigsawPlanarDirection.EAST)
                && localX == dimensions.width() - 1 && localZ == centerZ
                || topology.connects(JigsawPlanarDirection.SOUTH)
                && localX == centerX && localZ == dimensions.depth() - 1
                || topology.connects(JigsawPlanarDirection.WEST) && localX == 0 && localZ == centerZ;
    }

    static boolean isTopologyPath(
            JigsawPlanarTopology topology,
            int localX,
            int localZ,
            int centerX,
            int centerZ
    ) {
        if (topology == JigsawPlanarTopology.BLANK) {
            return false;
        }
        boolean vertical = localX == centerX
                && (topology.connects(JigsawPlanarDirection.NORTH) && localZ <= centerZ
                || topology.connects(JigsawPlanarDirection.SOUTH) && localZ >= centerZ);
        boolean horizontal = localZ == centerZ
                && (topology.connects(JigsawPlanarDirection.WEST) && localX <= centerX
                || topology.connects(JigsawPlanarDirection.EAST) && localX >= centerX);
        return vertical || horizontal;
    }

    private RenderedBay buildRenderedBay(JigsawStudioBay workcell, JigsawStudioVariant variant) {
        PackResources resources = pack.resources();
        IrisJigsawPiece piece = StructureResources.piece(resources, variant.pieceKey());
        if (piece == null) {
            return RenderedBay.invalid(
                    workcell.bounds().dimensions(),
                    "piece '" + variant.pieceKey() + "' is missing");
        }
        if (piece.getObject() == null || piece.getObject().isBlank()) {
            return RenderedBay.invalid(
                    workcell.bounds().dimensions(),
                    "piece '" + variant.pieceKey() + "' has no object");
        }
        if (!piece.getObject().equals(variant.objectKey())) {
            return RenderedBay.invalid(
                    workcell.bounds().dimensions(),
                    "piece '" + variant.pieceKey() + "' changed its object from '" + variant.objectKey()
                            + "' to '" + piece.getObject() + "'");
        }
        PlaceableObject object = resources.objects().object(resources.idOf(variant.objectKey())).orElse(null);
        if (object == null) {
            return RenderedBay.invalid(
                    workcell.bounds().dimensions(),
                    "object '" + variant.objectKey() + "' is missing");
        }
        if (object.width() < 1 || object.height() < 1 || object.depth() < 1) {
            return RenderedBay.invalid(
                    workcell.bounds().dimensions(),
                    "object '" + variant.objectKey() + "' has invalid dimensions");
        }

        int quarterTurns = variant.sourceToCanonicalQuarterTurns();
        JigsawStudioCellDimensions dimensions = rotatedDimensions(object, quarterTurns);
        if (!fits(dimensions, workcell.bounds().dimensions())) {
            return RenderedBay.invalid(
                    dimensions,
                    "object '" + variant.objectKey() + "' does not fit workcell '" + workcell.stableId() + "'");
        }

        PieceTurn rotation = PieceTurn.aroundY(-90 * quarterTurns);
        List<RenderedBlock> blocks = renderBlocks(resources.blocks(), object, dimensions, quarterTurns, rotation);
        if (blocks == null) {
            return RenderedBay.invalid(
                    dimensions,
                    "object '" + variant.objectKey() + "' contains an invalid or out-of-bounds block");
        }

        ConnectorRenderResult connectors = renderConnectors(variant.pieceKey(), piece, object, dimensions, quarterTurns, rotation);
        if (!connectors.failure().isEmpty()) {
            return RenderedBay.invalid(dimensions, connectors.failure());
        }
        return RenderedBay.valid(dimensions, blocks, connectors.connectors());
    }

    private List<RenderedBlock> renderBlocks(
            BlockRegistry registry,
            PlaceableObject object,
            JigsawStudioCellDimensions dimensions,
            int quarterTurns,
            PieceTurn rotation
    ) {
        BlockPosition center = object.center();
        List<ObjectBlock> sourceBlocks = object.geometry().blocks();
        List<RenderedBlock> blocks = new ArrayList<>(sourceBlocks.size());
        for (ObjectBlock source : sourceBlocks) {
            int sourceX = source.position().x() + center.x();
            int sourceY = source.position().y() + center.y();
            int sourceZ = source.position().z() + center.z();
            if (!inside(sourceX, sourceY, sourceZ, object.width(), object.height(), object.depth())) {
                return null;
            }
            RotatedPosition position = rotatePosition(
                    sourceX,
                    sourceY,
                    sourceZ,
                    object.width(),
                    object.depth(),
                    quarterTurns);
            if (!inside(
                    position.x(),
                    position.y(),
                    position.z(),
                    dimensions.width(),
                    dimensions.height(),
                    dimensions.depth())) {
                return null;
            }
            BlockState blockState = quarterTurns == 0
                    ? source.state()
                    : JigsawStudioBlocks.rotated(registry, source.state(), rotation);
            if (blockState == null) {
                return null;
            }
            blocks.add(new RenderedBlock(
                    position.x(),
                    position.y(),
                    position.z(),
                    blockState,
                    source.blockEntity().orElse(null)));
        }
        return List.copyOf(blocks);
    }

    private ConnectorRenderResult renderConnectors(
            String pieceKey,
            IrisJigsawPiece piece,
            PlaceableObject object,
            JigsawStudioCellDimensions dimensions,
            int quarterTurns,
            PieceTurn rotation
    ) {
        List<IrisJigsawConnector> sourceConnectors = piece.getConnectors();
        if (sourceConnectors == null) {
            return ConnectorRenderResult.invalid("piece '" + pieceKey + "' has no connector list");
        }
        List<RenderedConnector> connectors = new ArrayList<>(sourceConnectors.size());
        for (int index = 0; index < sourceConnectors.size(); index++) {
            IrisJigsawConnector source = sourceConnectors.get(index);
            if (!validConnectorMetadata(source)) {
                return ConnectorRenderResult.invalid("connector " + index + " has incomplete metadata");
            }
            BlockPosition sourcePosition = source.getPosition();
            if (!inside(
                    sourcePosition.x(),
                    sourcePosition.y(),
                    sourcePosition.z(),
                    object.width(),
                    object.height(),
                    object.depth())) {
                return ConnectorRenderResult.invalid("connector " + index + " is outside its object bounds");
            }
            RotatedPosition position = rotatePosition(
                    sourcePosition.x(),
                    sourcePosition.y(),
                    sourcePosition.z(),
                    object.width(),
                    object.depth(),
                    quarterTurns);
            JigsawDirection front = quarterTurns == 0
                    ? source.getDirection()
                    : source.getDirection().turnedBy(rotation.quarterTurns());
            JigsawDirection top = quarterTurns == 0
                    ? source.getTop()
                    : source.getTop().turnedBy(rotation.quarterTurns());
            String orientation;
            try {
                orientation = orientation(front, top);
            } catch (IllegalArgumentException exception) {
                return ConnectorRenderResult.invalid("connector " + index + " has an invalid orientation: "
                        + exception.getMessage());
            }
            String finalState = finalStateRenderer.render(source.getFinalState(), rotation, quarterTurns);
            if (finalState == null) {
                return ConnectorRenderResult.invalid("connector " + index + " final state '"
                        + source.getFinalState() + "' cannot be parsed and rotated");
            }
            IrisJigsawConnector connector = copyConnector(source)
                    .setPosition(new BlockPosition(position.x(), position.y(), position.z()))
                    .setDirection(front)
                    .setTop(top)
                    .setFinalState(finalState);
            if (!inside(
                    position.x(),
                    position.y(),
                    position.z(),
                    dimensions.width(),
                    dimensions.height(),
                    dimensions.depth())) {
                return ConnectorRenderResult.invalid("connector " + index + " rotated outside its object bounds");
            }
            connectors.add(new RenderedConnector(
                    position.x(), position.y(), position.z(), connector, orientation));
        }
        return ConnectorRenderResult.valid(connectors);
    }

    private String rotatedFinalState(String finalState, PieceTurn rotation, int quarterTurns) {
        BlockRegistry registry = pack.resources().blocks();
        BlockState state = registry.find(finalState).orElse(null);
        if (state == null) {
            return null;
        }
        if (quarterTurns == 0) {
            return state.key();
        }
        BlockState rotated = JigsawStudioBlocks.rotated(registry, state, rotation);
        return rotated == null ? null : rotated.key();
    }

    private void paintObject(
            ChunkCanvas canvas,
            JigsawStudioBay workcell,
            RenderedBay renderedBay,
            int chunkWorldX,
            int chunkWorldZ
    ) {
        JigsawStudioBounds bounds = workcell.bounds();
        for (RenderedBlock block : renderedBay.blocks()) {
            setWorldBlock(
                    canvas,
                    bounds.originX() + block.x(),
                    bounds.originY() + block.y(),
                    bounds.originZ() + block.z(),
                    block.state(),
                    chunkWorldX,
                    chunkWorldZ);
        }
    }

    private void paintConnectors(
            ChunkCanvas canvas,
            JigsawStudioBay workcell,
            RenderedBay renderedBay,
            int chunkWorldX,
            int chunkWorldZ
    ) {
        JigsawStudioBounds bounds = workcell.bounds();
        for (RenderedConnector connector : renderedBay.connectors()) {
            BlockState state = jigsawStates.computeIfAbsent(
                    connector.orientation(),
                    key -> pack.resources().blocks()
                            .find("minecraft:jigsaw[orientation=" + key + "]")
                            .orElse(invalidMarker));
            setWorldBlock(
                    canvas,
                    bounds.originX() + connector.x(),
                    bounds.originY() + connector.y(),
                    bounds.originZ() + connector.z(),
                    state,
                    chunkWorldX,
                    chunkWorldZ);
        }
    }

    private void paintHiddenConnectorFinalStates(
            ChunkCanvas canvas,
            JigsawStudioBay workcell,
            RenderedBay renderedBay,
            int chunkWorldX,
            int chunkWorldZ
    ) {
        Set<RenderedPosition> occupied = new HashSet<>(renderedBay.blocks().size());
        for (RenderedBlock block : renderedBay.blocks()) {
            occupied.add(new RenderedPosition(block.x(), block.y(), block.z()));
        }
        BlockRegistry registry = pack.resources().blocks();
        JigsawStudioBounds bounds = workcell.bounds();
        for (RenderedConnector connector : renderedBay.connectors()) {
            if (occupied.contains(new RenderedPosition(connector.x(), connector.y(), connector.z()))) {
                continue;
            }
            BlockState finalState = registry.find(connector.connector().getFinalState()).orElse(null);
            setWorldBlock(
                    canvas,
                    bounds.originX() + connector.x(),
                    bounds.originY() + connector.y(),
                    bounds.originZ() + connector.z(),
                    finalState == null ? invalidMarker : finalState,
                    chunkWorldX,
                    chunkWorldZ);
        }
    }

    private void paintInvalidBay(
            ChunkCanvas canvas,
            JigsawStudioBay workcell,
            int chunkWorldX,
            int chunkWorldZ
    ) {
        JigsawStudioBounds bounds = workcell.bounds();
        int y = bounds.originY();
        int length = Math.min(bounds.dimensions().width(), bounds.dimensions().depth());
        for (int offset = 0; offset < length; offset++) {
            setWorldBlock(
                    canvas,
                    bounds.originX() + offset,
                    y,
                    bounds.originZ() + offset,
                    invalidMarker,
                    chunkWorldX,
                    chunkWorldZ);
            setWorldBlock(
                    canvas,
                    bounds.maxX() - offset,
                    y,
                    bounds.originZ() + offset,
                    invalidMarker,
                    chunkWorldX,
                    chunkWorldZ);
        }
    }

    private JigsawStudioBay requireWorkcell(JigsawStudioBay workcell) {
        JigsawStudioBay activeWorkcell = Objects.requireNonNull(workcell, "Jigsaw Studio workcell");
        if (getLayout().get(activeWorkcell.stableId()) != activeWorkcell) {
            throw new IllegalArgumentException("Workcell does not belong to this Jigsaw Studio layout");
        }
        return activeWorkcell;
    }

    private JigsawStudioBay requireAcceptedVariant(JigsawStudioBay workcell, JigsawStudioVariant variant) {
        JigsawStudioBay activeWorkcell = requireWorkcell(workcell);
        if (!getLayout().accepts(activeWorkcell, Objects.requireNonNull(variant, "Jigsaw Studio variant"))) {
            throw new IllegalArgumentException("Variant '" + variant.pieceKey()
                    + "' does not belong to workcell '" + activeWorkcell.stableId() + "'");
        }
        return activeWorkcell;
    }

    static RotatedPosition rotatePosition(
            int x,
            int y,
            int z,
            int width,
            int depth,
            int quarterTurns
    ) {
        return switch (Math.floorMod(quarterTurns, 4)) {
            case 0 -> new RotatedPosition(x, y, z);
            case 1 -> new RotatedPosition(depth - 1 - z, y, x);
            case 2 -> new RotatedPosition(width - 1 - x, y, depth - 1 - z);
            case 3 -> new RotatedPosition(z, y, width - 1 - x);
            default -> throw new IllegalStateException("Unreachable Jigsaw Studio rotation");
        };
    }

    static String orientation(JigsawDirection front, JigsawDirection top) {
        JigsawDirection activeFront = Objects.requireNonNull(front, "Jigsaw connector front");
        JigsawDirection activeTop = Objects.requireNonNull(top, "Jigsaw connector top");
        if (activeFront == JigsawDirection.UP_POSITIVE_Y || activeFront == JigsawDirection.DOWN_NEGATIVE_Y) {
            if (activeTop.isVertical()) {
                throw new IllegalArgumentException("Vertical jigsaw fronts require a horizontal top direction");
            }
            return directionName(activeFront) + "_" + directionName(activeTop);
        }
        if (activeTop != JigsawDirection.UP_POSITIVE_Y) {
            throw new IllegalArgumentException("Horizontal jigsaw fronts require an upward top direction");
        }
        return directionName(activeFront) + "_up";
    }

    private static JigsawStudioCellDimensions rotatedDimensions(PlaceableObject object, int quarterTurns) {
        return Math.floorMod(quarterTurns, 2) == 0
                ? new JigsawStudioCellDimensions(object.width(), object.height(), object.depth())
                : new JigsawStudioCellDimensions(object.depth(), object.height(), object.width());
    }

    private static boolean fits(JigsawStudioCellDimensions object, JigsawStudioCellDimensions bay) {
        return object.width() <= bay.width()
                && object.height() <= bay.height()
                && object.depth() <= bay.depth();
    }

    private static boolean inside(int x, int y, int z, int width, int height, int depth) {
        return x >= 0 && x < width
                && y >= 0 && y < height
                && z >= 0 && z < depth;
    }

    private static boolean validConnectorMetadata(IrisJigsawConnector connector) {
        return connector != null
                && connector.getPosition() != null
                && connector.getDirection() != null
                && connector.getTop() != null
                && connector.getJoint() != null
                && connector.getPool() != null
                && !connector.getPool().isBlank()
                && connector.getName() != null
                && !connector.getName().isBlank()
                && connector.getTargetName() != null
                && !connector.getTargetName().isBlank()
                && connector.getFinalState() != null
                && !connector.getFinalState().isBlank();
    }

    private static IrisJigsawConnector copyConnector(IrisJigsawConnector source) {
        return new IrisJigsawConnector()
                .setPosition(new BlockPosition(
                        source.getPosition().x(),
                        source.getPosition().y(),
                        source.getPosition().z()))
                .setDirection(source.getDirection())
                .setTop(source.getTop())
                .setPool(source.getPool())
                .setName(source.getName())
                .setTargetName(source.getTargetName())
                .setChannel(source.getChannel() == null ? "" : source.getChannel())
                .setJoint(source.getJoint() == null ? JigsawJoint.ROLLABLE : source.getJoint())
                .setFinalState(source.getFinalState())
                .setSelectionPriority(source.getSelectionPriority())
                .setPlacementPriority(source.getPlacementPriority());
    }

    private static String directionName(JigsawDirection direction) {
        return switch (direction) {
            case UP_POSITIVE_Y -> "up";
            case DOWN_NEGATIVE_Y -> "down";
            case NORTH_NEGATIVE_Z -> "north";
            case SOUTH_POSITIVE_Z -> "south";
            case EAST_POSITIVE_X -> "east";
            case WEST_NEGATIVE_X -> "west";
        };
    }

    private void setWorldBlock(
            ChunkCanvas canvas,
            int worldX,
            int worldY,
            int worldZ,
            BlockState block,
            int chunkWorldX,
            int chunkWorldZ
    ) {
        int localX = worldX - chunkWorldX;
        int localZ = worldZ - chunkWorldZ;
        if (localX < 0 || localX >= CHUNK_SIZE || localZ < 0 || localZ >= CHUNK_SIZE) {
            return;
        }
        if (worldY < canvas.minY() || worldY >= canvas.maxY()) {
            return;
        }
        canvas.set(localX, worldY, localZ, block);
    }

    public record RenderedBay(
            JigsawStudioCellDimensions dimensions,
            List<RenderedBlock> blocks,
            List<RenderedConnector> connectors,
            String failure
    ) {
        public RenderedBay {
            dimensions = Objects.requireNonNull(dimensions, "Jigsaw Studio rendered dimensions");
            blocks = List.copyOf(blocks);
            connectors = List.copyOf(connectors);
            failure = failure == null ? "" : failure.trim();
        }

        public static RenderedBay valid(
                JigsawStudioCellDimensions dimensions,
                List<RenderedBlock> blocks,
                List<RenderedConnector> connectors
        ) {
            return new RenderedBay(dimensions, blocks, connectors, "");
        }

        public static RenderedBay empty(JigsawStudioCellDimensions dimensions) {
            return valid(dimensions, List.of(), List.of());
        }

        public static RenderedBay invalid(JigsawStudioCellDimensions dimensions, String failure) {
            return new RenderedBay(dimensions, List.of(), List.of(), failure);
        }

        public boolean valid() {
            return failure.isEmpty();
        }
    }

    /** A block of the object in the workcell, with the block entity it carries; the entity is null for a plain block. */
    public record RenderedBlock(int x, int y, int z, BlockState state, BlockEntityData blockEntity) {
        public RenderedBlock {
            state = Objects.requireNonNull(state, "Jigsaw Studio rendered block state");
        }
    }

    public record RenderedConnector(
            int x,
            int y,
            int z,
            IrisJigsawConnector connector,
            String orientation
    ) {
        public RenderedConnector {
            connector = Objects.requireNonNull(connector, "Jigsaw Studio rendered connector");
            orientation = Objects.requireNonNull(orientation, "Jigsaw Studio connector orientation");
        }
    }

    private record RenderKey(String workcellId, String pieceKey, long loadGeneration) {
    }

    private record ConnectorRenderResult(List<RenderedConnector> connectors, String failure) {
        private ConnectorRenderResult {
            connectors = List.copyOf(connectors);
            failure = failure == null ? "" : failure;
        }

        private static ConnectorRenderResult valid(List<RenderedConnector> connectors) {
            return new ConnectorRenderResult(connectors, "");
        }

        private static ConnectorRenderResult invalid(String failure) {
            return new ConnectorRenderResult(List.of(), failure);
        }
    }

    @FunctionalInterface
    interface FinalStateRenderer {
        String render(String finalState, PieceTurn rotation, int quarterTurns);
    }

    record RotatedPosition(int x, int y, int z) {
    }

    private record RenderedPosition(int x, int y, int z) {
    }
}
