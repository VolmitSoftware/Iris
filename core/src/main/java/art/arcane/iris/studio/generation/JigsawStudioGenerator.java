package art.arcane.iris.studio.generation;

import art.arcane.iris.studio.jigsaw.JigsawPlanarDirection;
import art.arcane.iris.studio.jigsaw.JigsawPlanarTopology;
import art.arcane.iris.studio.jigsaw.JigsawStudioActivation;
import art.arcane.iris.studio.jigsaw.JigsawStudioBay;
import art.arcane.iris.studio.jigsaw.JigsawStudioBounds;
import art.arcane.iris.studio.jigsaw.JigsawStudioCellDimensions;
import art.arcane.iris.studio.jigsaw.JigsawStudioControlPosition;
import art.arcane.iris.studio.jigsaw.JigsawStudioLayout;
import art.arcane.iris.studio.jigsaw.JigsawStudioSession;
import art.arcane.iris.studio.jigsaw.JigsawStudioVariant;
import art.arcane.iris.studio.jigsaw.JigsawStudioService;
import art.arcane.iris.generation.chunk.TerrainChunk;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.GenerationSessionLease;
import art.arcane.iris.generation.runtime.WrongEngineBroException;
import art.arcane.iris.pack.value.IrisDirection;
import art.arcane.iris.structure.jigsaw.IrisJigsawConnector;
import art.arcane.iris.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.structure.object.IrisObjectRotation;
import art.arcane.iris.pack.value.IrisPosition;
import art.arcane.iris.structure.jigsaw.JigsawJoint;
import art.arcane.iris.generation.block.TileData;
import art.arcane.iris.platform.bukkit.BukkitWorldBinding;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.generation.block.B;
import art.arcane.iris.generation.block.VectorMap;
import art.arcane.iris.generation.geometry.IrisBlockVector;
import art.arcane.volmlib.util.math.Vector3i;
import art.arcane.iris.generation.context.IrisContext;
import art.arcane.volmlib.util.collection.KList;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class JigsawStudioGenerator extends EnginedStudioGenerator {
    private static final int CHECKER_SIZE = 4;

    private final JigsawStudioActivation.Request request;
    private final JigsawStudioSession session;
    private final PlatformBlockState lightFloor;
    private final PlatformBlockState darkFloor;
    private final PlatformBlockState frame;
    private final PlatformBlockState topologyBase;
    private final PlatformBlockState topologyPath;
    private final PlatformBlockState connectorCap;
    private final PlatformBlockState invalidMarker;
    private final PlatformBlockState controlChest;
    private final FinalStateRenderer finalStateRenderer;
    private final AtomicBoolean serviceRegistered = new AtomicBoolean(false);
    private final ConcurrentMap<RenderKey, RenderedBay> renderedBays = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, PlatformBlockState> jigsawStates = new ConcurrentHashMap<>();

    public static boolean isLightFloor(int blockX, int blockZ) {
        int tileX = Math.floorDiv(blockX, CHECKER_SIZE);
        int tileZ = Math.floorDiv(blockZ, CHECKER_SIZE);
        return ((tileX + tileZ) & 1) == 0;
    }

    public JigsawStudioGenerator(
            Engine engine,
            JigsawStudioActivation.Request request,
            JigsawStudioSession session
    ) {
        this(
                engine,
                request,
                session,
                B.getState("minecraft:smooth_stone"),
                B.getState("minecraft:polished_deepslate"),
                B.getState("minecraft:white_concrete"),
                B.getState("minecraft:light_gray_wool"),
                B.getState("minecraft:red_wool"),
                B.getState("minecraft:sea_lantern"),
                B.getState("minecraft:red_stained_glass"),
                B.getState("minecraft:chest[facing=south,type=single,waterlogged=false]"),
                JigsawStudioGenerator::rotatedFinalState
        );
    }

    JigsawStudioGenerator(
            Engine engine,
            JigsawStudioActivation.Request request,
            JigsawStudioSession session,
            PlatformBlockState lightFloor,
            PlatformBlockState darkFloor,
            PlatformBlockState frame,
            PlatformBlockState topologyBase,
            PlatformBlockState topologyPath,
            PlatformBlockState connectorCap,
            PlatformBlockState invalidMarker,
            PlatformBlockState controlChest,
            FinalStateRenderer finalStateRenderer
    ) {
        super(engine);
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
        this.finalStateRenderer = Objects.requireNonNull(
                finalStateRenderer,
                "Jigsaw Studio connector final-state renderer");
        if (!request.requestId().equals(session.sessionId())) {
            throw new IllegalArgumentException("Jigsaw Studio request and session IDs do not match");
        }
        if (request.mode() != session.layout().mode()) {
            throw new IllegalArgumentException("Jigsaw Studio request and session modes do not match");
        }
    }

    public JigsawStudioActivation.Request getRequest() {
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
    public void generateChunk(Engine engine, TerrainChunk terrainChunk, int chunkX, int chunkZ)
            throws WrongEngineBroException {
        try (GenerationSessionLease lease = engine.acquireGenerationLease("bukkit_jigsaw_studio_stage");
             IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
            paintChunk(terrainChunk, chunkX, chunkZ);
            ensureServiceRegistered(engine);
            if (serviceRegistered.get()) {
                JigsawStudioService.get().markChunkGenerated(engine, this, chunkX, chunkZ);
            }
        }
    }

    public void paintChunk(TerrainChunk terrainChunk, int chunkX, int chunkZ) {
        Objects.requireNonNull(terrainChunk, "Jigsaw Studio terrain chunk");
        int floorY = Math.max(terrainChunk.getMinHeight(), JigsawStudioLayout.FLOOR_Y);
        if (floorY >= terrainChunk.getMaxHeight()) {
            return;
        }

        int chunkWorldX = chunkX << 4;
        int chunkWorldZ = chunkZ << 4;
        paintFloor(terrainChunk, floorY, chunkWorldX, chunkWorldZ);
        paintControlChest(terrainChunk, chunkWorldX, chunkWorldZ);

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
            paintCage(terrainChunk, workcell, chunkWorldX, chunkWorldZ);
            if (workcell.archetype().isPresent()) {
                paintTopologyGlyph(terrainChunk, workcell, chunkWorldX, chunkWorldZ);
            }
            RenderedBay renderedBay = renderBay(workcell);
            if (!renderedBay.valid()) {
                paintInvalidBay(terrainChunk, workcell, chunkWorldX, chunkWorldZ);
                continue;
            }
            paintObject(terrainChunk, workcell, renderedBay, chunkWorldX, chunkWorldZ);
            if (session.workcellSnapshot(workcell.stableId()).connectorsVisible()) {
                paintConnectors(terrainChunk, workcell, renderedBay, chunkWorldX, chunkWorldZ);
            } else {
                paintHiddenConnectorFinalStates(
                        terrainChunk,
                        workcell,
                        renderedBay,
                        chunkWorldX,
                        chunkWorldZ);
            }
        }
    }

    private void ensureServiceRegistered(Engine engine) {
        if (serviceRegistered.get()) {
            return;
        }
        World world = BukkitWorldBinding.world(engine.getTarget().getWorld());
        if (world == null) {
            return;
        }
        publishServiceRegistration(() -> JigsawStudioService.get().register(engine, this));
    }

    void publishServiceRegistration(Runnable registration) {
        Objects.requireNonNull(registration, "Jigsaw Studio service registration");
        if (serviceRegistered.get()) {
            return;
        }
        synchronized (serviceRegistered) {
            if (serviceRegistered.get()) {
                return;
            }
            registration.run();
            serviceRegistered.set(true);
        }
    }

    private void paintFloor(TerrainChunk terrainChunk, int floorY, int chunkWorldX, int chunkWorldZ) {
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldX = chunkWorldX + localX;
                int worldZ = chunkWorldZ + localZ;
                PlatformBlockState block = isLightFloor(worldX, worldZ) ? lightFloor : darkFloor;
                terrainChunk.setBlock(localX, floorY, localZ, block);
            }
        }
    }

    private void paintControlChest(TerrainChunk terrainChunk, int chunkWorldX, int chunkWorldZ) {
        JigsawStudioControlPosition control = getLayout().controlPosition();
        setWorldBlock(
                terrainChunk,
                control.worldX(),
                control.worldY(),
                control.worldZ(),
                controlChest,
                chunkWorldX,
                chunkWorldZ);
    }

    private void paintCage(
            TerrainChunk terrainChunk,
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
                terrainChunk,
                minimumX,
                maximumX,
                bottomY,
                minimumZ,
                maximumZ,
                chunkWorldX,
                chunkWorldZ);
        paintRectangle(
                terrainChunk,
                minimumX,
                maximumX,
                topY,
                minimumZ,
                maximumZ,
                chunkWorldX,
                chunkWorldZ);
        for (int y = bottomY + 1; y < topY; y++) {
            setWorldBlock(terrainChunk, minimumX, y, minimumZ, frame, chunkWorldX, chunkWorldZ);
            setWorldBlock(terrainChunk, maximumX, y, minimumZ, frame, chunkWorldX, chunkWorldZ);
            setWorldBlock(terrainChunk, minimumX, y, maximumZ, frame, chunkWorldX, chunkWorldZ);
            setWorldBlock(terrainChunk, maximumX, y, maximumZ, frame, chunkWorldX, chunkWorldZ);
        }
    }

    private void paintRectangle(
            TerrainChunk terrainChunk,
            int minimumX,
            int maximumX,
            int y,
            int minimumZ,
            int maximumZ,
            int chunkWorldX,
            int chunkWorldZ
    ) {
        for (int x = minimumX; x <= maximumX; x++) {
            setWorldBlock(terrainChunk, x, y, minimumZ, frame, chunkWorldX, chunkWorldZ);
            setWorldBlock(terrainChunk, x, y, maximumZ, frame, chunkWorldX, chunkWorldZ);
        }
        for (int z = minimumZ + 1; z < maximumZ; z++) {
            setWorldBlock(terrainChunk, minimumX, y, z, frame, chunkWorldX, chunkWorldZ);
            setWorldBlock(terrainChunk, maximumX, y, z, frame, chunkWorldX, chunkWorldZ);
        }
    }

    private void paintTopologyGlyph(
            TerrainChunk terrainChunk,
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
                PlatformBlockState state = topologyGlyphState(topology, bounds.dimensions(), localX, localZ,
                        centerX, centerZ);
                setWorldBlock(terrainChunk, worldX, glyphY, worldZ, state, chunkWorldX, chunkWorldZ);
            }
        }
    }

    private PlatformBlockState topologyGlyphState(
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
        IrisJigsawPiece piece = request.source().getJigsawPieceLoader().load(variant.pieceKey(), false);
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
        IrisObject object = request.source().getObjectLoader().load(variant.objectKey(), false);
        if (object == null) {
            return RenderedBay.invalid(
                    workcell.bounds().dimensions(),
                    "object '" + variant.objectKey() + "' is missing");
        }
        if (object.getW() < 1 || object.getH() < 1 || object.getD() < 1) {
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

        IrisObjectRotation rotation = IrisObjectRotation.of(0, -90.0D * quarterTurns, 0);
        List<RenderedBlock> blocks = renderBlocks(object, dimensions, quarterTurns, rotation);
        if (blocks == null) {
            return RenderedBay.invalid(
                    dimensions,
                    "object '" + variant.objectKey() + "' contains an invalid or out-of-bounds block");
        }

        ConnectorRenderResult connectors = renderConnectors(piece, object, dimensions, quarterTurns, rotation);
        if (!connectors.failure().isEmpty()) {
            return RenderedBay.invalid(dimensions, connectors.failure());
        }
        return RenderedBay.valid(dimensions, blocks, connectors.connectors());
    }

    private List<RenderedBlock> renderBlocks(
            IrisObject object,
            JigsawStudioCellDimensions dimensions,
            int quarterTurns,
            IrisObjectRotation rotation
    ) {
        VectorMap<PlatformBlockState> sourceBlocks = object.getBlocks();
        VectorMap<TileData> sourceTiles = object.getStates();
        Vector3i center = object.getCenter();
        List<RenderedBlock> blocks = new ArrayList<>(sourceBlocks.size());
        for (Map.Entry<IrisBlockVector, PlatformBlockState> entry : sourceBlocks) {
            IrisBlockVector signed = entry.getKey();
            int sourceX = signed.getBlockX() + center.getBlockX();
            int sourceY = signed.getBlockY() + center.getBlockY();
            int sourceZ = signed.getBlockZ() + center.getBlockZ();
            if (!inside(sourceX, sourceY, sourceZ, object.getW(), object.getH(), object.getD())) {
                return null;
            }
            RotatedPosition position = rotatePosition(
                    sourceX,
                    sourceY,
                    sourceZ,
                    object.getW(),
                    object.getD(),
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
            PlatformBlockState blockState = quarterTurns == 0
                    ? entry.getValue()
                    : rotation.rotate(entry.getValue(), 0, 0, 0);
            if (blockState == null) {
                return null;
            }
            TileData tileData = sourceTiles.get(signed);
            blocks.add(new RenderedBlock(
                    position.x(),
                    position.y(),
                    position.z(),
                    blockState,
                    tileData == null ? null : tileData.clone()));
        }
        return List.copyOf(blocks);
    }

    private ConnectorRenderResult renderConnectors(
            IrisJigsawPiece piece,
            IrisObject object,
            JigsawStudioCellDimensions dimensions,
            int quarterTurns,
            IrisObjectRotation rotation
    ) {
        KList<IrisJigsawConnector> sourceConnectors = piece.getConnectors();
        if (sourceConnectors == null) {
            return ConnectorRenderResult.invalid("piece '" + piece.getLoadKey() + "' has no connector list");
        }
        List<RenderedConnector> connectors = new ArrayList<>(sourceConnectors.size());
        for (int index = 0; index < sourceConnectors.size(); index++) {
            IrisJigsawConnector source = sourceConnectors.get(index);
            if (!validConnectorMetadata(source)) {
                return ConnectorRenderResult.invalid("connector " + index + " has incomplete metadata");
            }
            IrisPosition sourcePosition = source.getPosition();
            if (!inside(
                    sourcePosition.getX(),
                    sourcePosition.getY(),
                    sourcePosition.getZ(),
                    object.getW(),
                    object.getH(),
                    object.getD())) {
                return ConnectorRenderResult.invalid("connector " + index + " is outside its object bounds");
            }
            RotatedPosition position = rotatePosition(
                    sourcePosition.getX(),
                    sourcePosition.getY(),
                    sourcePosition.getZ(),
                    object.getW(),
                    object.getD(),
                    quarterTurns);
            IrisDirection front = quarterTurns == 0
                    ? source.getDirection()
                    : rotation.rotate(source.getDirection());
            IrisDirection top = quarterTurns == 0
                    ? source.getTop()
                    : rotation.rotate(source.getTop());
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
                    .setPosition(new IrisPosition(position.x(), position.y(), position.z()))
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

    private static String rotatedFinalState(
            String finalState,
            IrisObjectRotation rotation,
            int quarterTurns
    ) {
        PlatformBlockState state = B.getStateOrNull(finalState, false);
        if (state == null) {
            return null;
        }
        if (quarterTurns == 0) {
            return state.key();
        }
        PlatformBlockState rotated = rotation.rotate(state, 0, 0, 0);
        return rotated == null ? null : rotated.key();
    }

    private void paintObject(
            TerrainChunk terrainChunk,
            JigsawStudioBay workcell,
            RenderedBay renderedBay,
            int chunkWorldX,
            int chunkWorldZ
    ) {
        JigsawStudioBounds bounds = workcell.bounds();
        for (RenderedBlock block : renderedBay.blocks()) {
            setWorldBlock(
                    terrainChunk,
                    bounds.originX() + block.x(),
                    bounds.originY() + block.y(),
                    bounds.originZ() + block.z(),
                    block.state(),
                    chunkWorldX,
                    chunkWorldZ);
        }
    }

    private void paintConnectors(
            TerrainChunk terrainChunk,
            JigsawStudioBay workcell,
            RenderedBay renderedBay,
            int chunkWorldX,
            int chunkWorldZ
    ) {
        JigsawStudioBounds bounds = workcell.bounds();
        for (RenderedConnector connector : renderedBay.connectors()) {
            PlatformBlockState state = jigsawStates.computeIfAbsent(
                    connector.orientation(),
                    key -> B.getState("minecraft:jigsaw[orientation=" + key + "]"));
            setWorldBlock(
                    terrainChunk,
                    bounds.originX() + connector.x(),
                    bounds.originY() + connector.y(),
                    bounds.originZ() + connector.z(),
                    state == null ? invalidMarker : state,
                    chunkWorldX,
                    chunkWorldZ);
        }
    }

    private void paintHiddenConnectorFinalStates(
            TerrainChunk terrainChunk,
            JigsawStudioBay workcell,
            RenderedBay renderedBay,
            int chunkWorldX,
            int chunkWorldZ
    ) {
        Set<RenderedPosition> occupied = new HashSet<>(renderedBay.blocks().size());
        for (RenderedBlock block : renderedBay.blocks()) {
            occupied.add(new RenderedPosition(block.x(), block.y(), block.z()));
        }
        JigsawStudioBounds bounds = workcell.bounds();
        for (RenderedConnector connector : renderedBay.connectors()) {
            if (occupied.contains(new RenderedPosition(connector.x(), connector.y(), connector.z()))) {
                continue;
            }
            PlatformBlockState finalState = B.getStateOrNull(connector.connector().getFinalState(), false);
            setWorldBlock(
                    terrainChunk,
                    bounds.originX() + connector.x(),
                    bounds.originY() + connector.y(),
                    bounds.originZ() + connector.z(),
                    finalState == null ? invalidMarker : finalState,
                    chunkWorldX,
                    chunkWorldZ);
        }
    }

    private void paintInvalidBay(
            TerrainChunk terrainChunk,
            JigsawStudioBay workcell,
            int chunkWorldX,
            int chunkWorldZ
    ) {
        JigsawStudioBounds bounds = workcell.bounds();
        int y = bounds.originY();
        int length = Math.min(bounds.dimensions().width(), bounds.dimensions().depth());
        for (int offset = 0; offset < length; offset++) {
            setWorldBlock(
                    terrainChunk,
                    bounds.originX() + offset,
                    y,
                    bounds.originZ() + offset,
                    invalidMarker,
                    chunkWorldX,
                    chunkWorldZ);
            setWorldBlock(
                    terrainChunk,
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

    static String orientation(IrisDirection front, IrisDirection top) {
        IrisDirection activeFront = Objects.requireNonNull(front, "Jigsaw connector front");
        IrisDirection activeTop = Objects.requireNonNull(top, "Jigsaw connector top");
        if (activeFront == IrisDirection.UP_POSITIVE_Y || activeFront == IrisDirection.DOWN_NEGATIVE_Y) {
            if (activeTop.isVertical()) {
                throw new IllegalArgumentException("Vertical jigsaw fronts require a horizontal top direction");
            }
            return directionName(activeFront) + "_" + directionName(activeTop);
        }
        if (activeTop != IrisDirection.UP_POSITIVE_Y) {
            throw new IllegalArgumentException("Horizontal jigsaw fronts require an upward top direction");
        }
        return directionName(activeFront) + "_up";
    }

    private static JigsawStudioCellDimensions rotatedDimensions(IrisObject object, int quarterTurns) {
        return Math.floorMod(quarterTurns, 2) == 0
                ? new JigsawStudioCellDimensions(object.getW(), object.getH(), object.getD())
                : new JigsawStudioCellDimensions(object.getD(), object.getH(), object.getW());
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
                .setPosition(new IrisPosition(
                        source.getPosition().getX(),
                        source.getPosition().getY(),
                        source.getPosition().getZ()))
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

    private static String directionName(IrisDirection direction) {
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
            TerrainChunk terrainChunk,
            int worldX,
            int worldY,
            int worldZ,
            PlatformBlockState block,
            int chunkWorldX,
            int chunkWorldZ
    ) {
        int localX = worldX - chunkWorldX;
        int localZ = worldZ - chunkWorldZ;
        if (localX < 0 || localX >= 16 || localZ < 0 || localZ >= 16) {
            return;
        }
        if (worldY < terrainChunk.getMinHeight() || worldY >= terrainChunk.getMaxHeight()) {
            return;
        }
        terrainChunk.setBlock(localX, worldY, localZ, block);
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

    public record RenderedBlock(int x, int y, int z, PlatformBlockState state, TileData tileData) {
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
        String render(String finalState, IrisObjectRotation rotation, int quarterTurns);
    }

    record RotatedPosition(int x, int y, int z) {
    }

    private record RenderedPosition(int x, int y, int z) {
    }
}
