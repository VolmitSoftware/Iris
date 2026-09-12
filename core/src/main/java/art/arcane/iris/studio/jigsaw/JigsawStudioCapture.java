package art.arcane.iris.studio.jigsaw;

import art.arcane.iris.pack.value.IrisDirection;
import art.arcane.iris.structure.jigsaw.IrisJigsawConnector;
import art.arcane.iris.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.structure.object.IrisObjectRotation;
import art.arcane.iris.pack.value.IrisPosition;
import art.arcane.iris.structure.jigsaw.JigsawJoint;
import art.arcane.iris.generation.block.TileData;
import art.arcane.iris.platform.bukkit.BukkitBlockState;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.generation.block.B;
import art.arcane.volmlib.util.collection.KMap;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Jigsaw;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static art.arcane.iris.studio.jigsaw.JigsawStudioChunkWriter.restoreCapturedMetadataForDisplay;
import static art.arcane.iris.studio.jigsaw.JigsawStudioService.chunkKey;
import static art.arcane.iris.studio.jigsaw.JigsawStudioService.failureMessage;

final class JigsawStudioCapture {
    private static final Comparator<IrisJigsawConnector> NEW_CONNECTOR_SOURCE_POSITION_ORDER = Comparator
            .comparingInt((IrisJigsawConnector connector) -> connector.getPosition().getX())
            .thenComparingInt(connector -> connector.getPosition().getY())
            .thenComparingInt(connector -> connector.getPosition().getZ());

    private JigsawStudioCapture() {
    }

    static List<ChunkCaptureArea> chunkIntersections(JigsawStudioBounds bounds) {
        JigsawStudioBounds captureBounds = Objects.requireNonNull(bounds, "Jigsaw Studio capture bounds");
        List<ChunkCaptureArea> areas = new ArrayList<>();
        int minimumChunkX = captureBounds.originX() >> 4;
        int maximumChunkX = captureBounds.maxX() >> 4;
        int minimumChunkZ = captureBounds.originZ() >> 4;
        int maximumChunkZ = captureBounds.maxZ() >> 4;
        for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
            int chunkOriginX = chunkX << 4;
            int minimumX = Math.max(0, chunkOriginX - captureBounds.originX());
            int maximumX = Math.min(
                    captureBounds.dimensions().width(),
                    chunkOriginX + 16 - captureBounds.originX());
            for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
                int chunkOriginZ = chunkZ << 4;
                int minimumZ = Math.max(0, chunkOriginZ - captureBounds.originZ());
                int maximumZ = Math.min(
                        captureBounds.dimensions().depth(),
                        chunkOriginZ + 16 - captureBounds.originZ());
                areas.add(new ChunkCaptureArea(
                        chunkX,
                        chunkZ,
                        minimumX,
                        maximumX,
                        minimumZ,
                        maximumZ));
            }
        }
        return List.copyOf(areas);
    }

    static ChunkSnapshot captureChunkIntersection(
            World world,
            JigsawStudioBounds bounds,
            IrisJigsawPiece sourcePiece,
            IrisObject sourceObject,
            ChunkCaptureArea area
    ) throws IOException {
        return captureChunkIntersection(world, bounds, sourcePiece, sourceObject, area, 0);
    }

    static ChunkSnapshot captureChunkIntersection(
            World world,
            JigsawStudioBounds bounds,
            IrisJigsawPiece sourcePiece,
            IrisObject sourceObject,
            ChunkCaptureArea area,
            int displayRotationQuarterTurns
    ) throws IOException {
        return captureChunkIntersection(
                world,
                bounds,
                sourcePiece,
                sourceObject,
                area,
                displayRotationQuarterTurns,
                true);
    }

    static ChunkSnapshot captureChunkIntersection(
            World world,
            JigsawStudioBounds bounds,
            IrisJigsawPiece sourcePiece,
            IrisObject sourceObject,
            ChunkCaptureArea area,
            int displayRotationQuarterTurns,
            boolean connectorsVisible
    ) throws IOException {
        World captureWorld = Objects.requireNonNull(world, "Jigsaw Studio capture world");
        JigsawStudioBounds captureBounds = Objects.requireNonNull(bounds, "Jigsaw Studio capture bounds");
        IrisObject captureSource = Objects.requireNonNull(sourceObject, "Jigsaw Studio source object");
        ChunkCaptureArea captureArea = Objects.requireNonNull(area, "Jigsaw Studio chunk capture area");
        int quarterTurns = Math.floorMod(displayRotationQuarterTurns, 4);
        int sourceWidth = (quarterTurns & 1) == 0
                ? captureBounds.dimensions().width()
                : captureBounds.dimensions().depth();
        int sourceDepth = (quarterTurns & 1) == 0
                ? captureBounds.dimensions().depth()
                : captureBounds.dimensions().width();
        List<CapturedBlock> blocks = new ArrayList<>();
        List<CapturedConnector> connectors = new ArrayList<>();
        Map<LocalPosition, IrisJigsawConnector> hiddenConnectors = connectorsVisible
                ? Map.of()
                : displayedSourceConnectors(sourcePiece, captureBounds.dimensions(), quarterTurns);
        for (int x = captureArea.minimumX(); x < captureArea.maximumX(); x++) {
            for (int y = 0; y < bounds.dimensions().height(); y++) {
                for (int z = captureArea.minimumZ(); z < captureArea.maximumZ(); z++) {
                    Block block = captureWorld.getBlockAt(
                            captureBounds.originX() + x,
                            captureBounds.originY() + y,
                            captureBounds.originZ() + z);
                    BlockData blockData = block.getBlockData();
                    IrisJigsawConnector hiddenConnector = hiddenConnectors.get(new LocalPosition(x, y, z));
                    if (hiddenConnector != null) {
                        hiddenConnector.setFinalState(blockData.getAsString());
                        connectors.add(CapturedConnector.from(hiddenConnector));
                    }
                    if (hiddenConnector == null && blockData instanceof Jigsaw jigsaw) {
                        KMap<String, Object> nbt = BukkitPlatform.serializeTile(block.getLocation());
                        if (nbt == null) {
                            throw new IOException("Cannot read jigsaw marker NBT at "
                                    + block.getX() + "," + block.getY() + "," + block.getZ()
                                    + ". The active NMS binding must support tile serialization.");
                        }
                        IrisJigsawConnector connector;
                        try {
                            connector = JigsawStudioMarkerParser.parse(nbt, jigsaw.getOrientation(), x, y, z);
                        } catch (IllegalArgumentException exception) {
                            throw new IOException("Invalid jigsaw marker at "
                                    + block.getX() + "," + block.getY() + "," + block.getZ()
                                    + ": " + failureMessage(exception), exception);
                        }
                        restoreCapturedMetadataForDisplay(
                                connector,
                                sourcePiece,
                                captureBounds.dimensions(),
                                quarterTurns);
                        BlockData finalData;
                        try {
                            finalData = Bukkit.createBlockData(connector.getFinalState());
                        } catch (IllegalArgumentException exception) {
                            throw new IOException("Invalid final_state '" + connector.getFinalState()
                                    + "' at " + block.getX() + "," + block.getY() + "," + block.getZ(), exception);
                        }
                        connector.setFinalState(finalData.getAsString());
                        connectors.add(CapturedConnector.from(connector));
                        if (finalData.getMaterial() != Material.STRUCTURE_VOID) {
                            blocks.add(new CapturedBlock(
                                    x,
                                    y,
                                    z,
                                    BukkitBlockState.of(finalData),
                                    null));
                        }
                        continue;
                    }
                    if (isAir(blockData.getMaterial())) {
                        LocalPosition sourcePosition = inversePosition(
                                x,
                                y,
                                z,
                                sourceWidth,
                                sourceDepth,
                                quarterTurns);
                        PlatformBlockState retainedAir = retainedSourceAir(
                                captureSource,
                                sourcePosition.x(),
                                sourcePosition.y(),
                                sourcePosition.z(),
                                blockData);
                        if (retainedAir != null) {
                            blocks.add(new CapturedBlock(x, y, z, retainedAir, null));
                        }
                        continue;
                    }
                    if (blockData.getMaterial() == Material.STRUCTURE_VOID) {
                        continue;
                    }
                    blocks.add(new CapturedBlock(
                            x,
                            y,
                            z,
                            BukkitBlockState.of(blockData),
                            TileData.getTileState(block, false)));
                }
            }
        }
        return new ChunkSnapshot(captureArea, blocks, connectors);
    }

    private static Map<LocalPosition, IrisJigsawConnector> displayedSourceConnectors(
            IrisJigsawPiece sourcePiece,
            JigsawStudioCellDimensions displayDimensions,
            int displayRotationQuarterTurns
    ) throws IOException {
        IrisJigsawPiece source = Objects.requireNonNull(sourcePiece, "Jigsaw Studio source piece");
        if (source.getConnectors() == null) {
            throw new IOException("Jigsaw Studio source piece has no connector list");
        }
        int quarterTurns = Math.floorMod(displayRotationQuarterTurns, 4);
        int sourceWidth = (quarterTurns & 1) == 0
                ? displayDimensions.width()
                : displayDimensions.depth();
        int sourceDepth = (quarterTurns & 1) == 0
                ? displayDimensions.depth()
                : displayDimensions.width();
        IrisObjectRotation rotation = IrisObjectRotation.of(0, -90.0D * quarterTurns, 0);
        Map<LocalPosition, IrisJigsawConnector> displayed = new HashMap<>(source.getConnectors().size());
        for (IrisJigsawConnector sourceConnector : source.getConnectors()) {
            LocalPosition sourcePosition = connectorPosition(sourceConnector, "source piece");
            LocalPosition position = forwardPosition(
                    sourcePosition.x(),
                    sourcePosition.y(),
                    sourcePosition.z(),
                    sourceWidth,
                    sourceDepth,
                    quarterTurns);
            IrisJigsawConnector connector = CapturedConnector.from(sourceConnector).toConnector()
                    .setPosition(new IrisPosition(position.x(), position.y(), position.z()))
                    .setDirection(rotation.rotate(sourceConnector.getDirection()))
                    .setTop(rotation.rotate(sourceConnector.getTop()));
            if (displayed.put(position, connector) != null) {
                throw new IOException("Jigsaw Studio source piece has duplicate displayed connector position "
                        + connectorLocation(position));
            }
        }
        return Map.copyOf(displayed);
    }

    static PlatformBlockState retainedSourceAir(
            IrisObject sourceObject,
            int x,
            int y,
            int z,
            BlockData current
    ) {
        IrisObject source = Objects.requireNonNull(sourceObject, "Jigsaw Studio source object");
        BlockData currentState = Objects.requireNonNull(current, "Jigsaw Studio current block state");
        if (!isAir(currentState.getMaterial())
                || x < 0
                || y < 0
                || z < 0
                || x >= source.getW()
                || y >= source.getH()
                || z >= source.getD()) {
            return null;
        }
        PlatformBlockState original = source.getBlocks().get(source.getSigned(x, y, z));
        return original != null && original.isAir() ? original : null;
    }

    private static boolean isAir(Material material) {
        return material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR;
    }

    static Capture aggregateSnapshots(
            JigsawStudioBounds bounds,
            List<ChunkCaptureArea> expectedAreas,
            List<ChunkSnapshot> snapshots
    ) throws IOException {
        return aggregateSnapshots(bounds, expectedAreas, snapshots, 0);
    }

    static Capture aggregateSnapshots(
            JigsawStudioBounds bounds,
            List<ChunkCaptureArea> expectedAreas,
            List<ChunkSnapshot> snapshots,
            int displayRotationQuarterTurns
    ) throws IOException {
        JigsawStudioBounds captureBounds = Objects.requireNonNull(bounds, "Jigsaw Studio capture bounds");
        int quarterTurns = Math.floorMod(displayRotationQuarterTurns, 4);
        List<ChunkCaptureArea> requiredAreas = List.copyOf(expectedAreas);
        List<ChunkSnapshot> capturedSnapshots = List.copyOf(snapshots);
        Map<Long, ChunkCaptureArea> requiredByChunk = new HashMap<>();
        for (ChunkCaptureArea area : requiredAreas) {
            if (requiredByChunk.put(chunkKey(area.chunkX(), area.chunkZ()), area) != null) {
                throw new IOException("Duplicate required bay chunk " + area.chunkX() + "," + area.chunkZ());
            }
        }
        if (capturedSnapshots.size() != requiredByChunk.size()) {
            throw new IOException("Expected " + requiredByChunk.size() + " bay chunk snapshot(s), received "
                    + capturedSnapshots.size());
        }
        Map<Long, ChunkSnapshot> capturedByChunk = new HashMap<>();
        for (ChunkSnapshot snapshot : capturedSnapshots) {
            ChunkCaptureArea area = snapshot.area();
            long key = chunkKey(area.chunkX(), area.chunkZ());
            ChunkCaptureArea required = requiredByChunk.get(key);
            if (!area.equals(required)) {
                throw new IOException("Unexpected bay chunk intersection for "
                        + area.chunkX() + "," + area.chunkZ());
            }
            if (capturedByChunk.put(key, snapshot) != null) {
                throw new IOException("Duplicate bay chunk snapshot " + area.chunkX() + "," + area.chunkZ());
            }
        }
        if (!capturedByChunk.keySet().containsAll(requiredByChunk.keySet())) {
            throw new IOException("One or more required bay chunk snapshots are missing");
        }

        List<ChunkSnapshot> ordered = new ArrayList<>(capturedSnapshots);
        ordered.sort(Comparator
                .comparingInt((ChunkSnapshot snapshot) -> snapshot.area().chunkX())
                .thenComparingInt(snapshot -> snapshot.area().chunkZ()));
        int sourceWidth = (quarterTurns & 1) == 0
                ? captureBounds.dimensions().width()
                : captureBounds.dimensions().depth();
        int sourceDepth = (quarterTurns & 1) == 0
                ? captureBounds.dimensions().depth()
                : captureBounds.dimensions().width();
        IrisObject object = new IrisObject(
                sourceWidth,
                captureBounds.dimensions().height(),
                sourceDepth);
        IrisObjectRotation inverseRotation = IrisObjectRotation.of(0, 90.0D * quarterTurns, 0);
        List<IrisJigsawConnector> connectors = new ArrayList<>();
        Set<LocalPosition> capturedBlocks = new HashSet<>();
        Set<LocalPosition> capturedConnectors = new HashSet<>();
        boolean hasBlockEntities = false;
        for (ChunkSnapshot snapshot : ordered) {
            ChunkCaptureArea area = snapshot.area();
            for (CapturedBlock block : snapshot.blocks()) {
                if (!area.contains(block.x(), block.z())
                        || block.y() < 0
                        || block.y() >= captureBounds.dimensions().height()) {
                    throw new IOException("Captured block falls outside bay chunk "
                            + area.chunkX() + "," + area.chunkZ());
                }
                LocalPosition position = inversePosition(
                        block.x(), block.y(), block.z(), sourceWidth, sourceDepth, quarterTurns);
                if (!capturedBlocks.add(position)) {
                    throw new IOException("Duplicate captured block at "
                            + block.x() + "," + block.y() + "," + block.z());
                }
                PlatformBlockState sourceState = quarterTurns == 0
                        ? block.state()
                        : inverseRotation.rotate(block.state(), 0, 0, 0);
                if (sourceState == null) {
                    throw new IOException("Captured block state cannot be inverse-rotated at "
                            + block.x() + "," + block.y() + "," + block.z());
                }
                object.setUnsigned(position.x(), position.y(), position.z(), sourceState);
                TileData tileData = block.tileData();
                if (tileData != null) {
                    object.setUnsignedTile(position.x(), position.y(), position.z(), tileData.clone());
                    hasBlockEntities = true;
                }
            }
            for (CapturedConnector capturedConnector : snapshot.connectors()) {
                if (!area.contains(capturedConnector.x(), capturedConnector.z())
                        || capturedConnector.y() < 0
                        || capturedConnector.y() >= captureBounds.dimensions().height()) {
                    throw new IOException("Captured connector falls outside bay chunk "
                            + area.chunkX() + "," + area.chunkZ());
                }
                LocalPosition position = inversePosition(
                        capturedConnector.x(),
                        capturedConnector.y(),
                        capturedConnector.z(),
                        sourceWidth,
                        sourceDepth,
                        quarterTurns);
                if (!capturedConnectors.add(position)) {
                    throw new IOException("Duplicate captured connector at "
                            + capturedConnector.x() + "," + capturedConnector.y() + ","
                            + capturedConnector.z());
                }
                IrisJigsawConnector connector = capturedConnector.toConnector();
                if (quarterTurns != 0) {
                    connector.setPosition(new IrisPosition(position.x(), position.y(), position.z()));
                    connector.setDirection(inverseRotation.rotate(connector.getDirection()));
                    connector.setTop(inverseRotation.rotate(connector.getTop()));
                    PlatformBlockState finalState = B.getStateOrNull(connector.getFinalState(), false);
                    if (finalState == null) {
                        throw new IOException("Captured connector final state cannot be parsed at "
                                + capturedConnector.x() + "," + capturedConnector.y() + ","
                                + capturedConnector.z());
                    }
                    PlatformBlockState sourceFinalState = inverseRotation.rotate(finalState, 0, 0, 0);
                    if (sourceFinalState == null) {
                        throw new IOException("Captured connector final state cannot be inverse-rotated at "
                                + capturedConnector.x() + "," + capturedConnector.y() + ","
                                + capturedConnector.z());
                    }
                    connector.setFinalState(sourceFinalState.key());
                }
                connectors.add(connector);
            }
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            object.write(output);
            return new Capture(output.toByteArray(), connectors, hasBlockEntities);
        }
    }

    static List<IrisJigsawConnector> preserveCapturedConnectorOrder(
            IrisJigsawPiece sourcePiece,
            List<IrisJigsawConnector> capturedConnectors
    ) throws IOException {
        IrisJigsawPiece source = Objects.requireNonNull(sourcePiece, "Jigsaw Studio source piece");
        List<IrisJigsawConnector> sourceConnectors = source.getConnectors();
        if (sourceConnectors == null) {
            throw new IOException("Jigsaw Studio source piece has no connector list");
        }
        List<IrisJigsawConnector> captured = new ArrayList<>(Objects.requireNonNull(
                capturedConnectors,
                "Jigsaw Studio captured connectors"));
        Map<LocalPosition, IrisJigsawConnector> capturedByPosition = new HashMap<>(captured.size());
        for (IrisJigsawConnector connector : captured) {
            LocalPosition position = connectorPosition(connector, "captured");
            if (capturedByPosition.put(position, connector) != null) {
                throw new IOException("Jigsaw Studio capture has duplicate connector position "
                        + connectorLocation(position));
            }
        }

        List<IrisJigsawConnector> ordered = new ArrayList<>(captured.size());
        Set<LocalPosition> sourcePositions = new HashSet<>(sourceConnectors.size());
        for (IrisJigsawConnector connector : sourceConnectors) {
            LocalPosition position = connectorPosition(connector, "source piece");
            if (!sourcePositions.add(position)) {
                throw new IOException("Jigsaw Studio source piece has duplicate connector position "
                        + connectorLocation(position));
            }
            IrisJigsawConnector matched = capturedByPosition.remove(position);
            if (matched != null) {
                ordered.add(matched);
            }
        }

        List<IrisJigsawConnector> appended = new ArrayList<>(capturedByPosition.values());
        appended.sort(NEW_CONNECTOR_SOURCE_POSITION_ORDER);
        ordered.addAll(appended);
        return List.copyOf(ordered);
    }

    private static LocalPosition connectorPosition(IrisJigsawConnector connector, String owner) throws IOException {
        if (connector == null || connector.getPosition() == null) {
            throw new IOException("Jigsaw Studio " + owner + " contains a connector without a position");
        }
        IrisPosition position = connector.getPosition();
        return new LocalPosition(position.getX(), position.getY(), position.getZ());
    }

    private static String connectorLocation(LocalPosition position) {
        return position.x() + "," + position.y() + "," + position.z();
    }

    static LocalPosition inversePosition(
            int x,
            int y,
            int z,
            int sourceWidth,
            int sourceDepth,
            int displayRotationQuarterTurns
    ) {
        return switch (Math.floorMod(displayRotationQuarterTurns, 4)) {
            case 0 -> new LocalPosition(x, y, z);
            case 1 -> new LocalPosition(z, y, sourceDepth - 1 - x);
            case 2 -> new LocalPosition(sourceWidth - 1 - x, y, sourceDepth - 1 - z);
            case 3 -> new LocalPosition(sourceWidth - 1 - z, y, x);
            default -> throw new IllegalStateException("Unreachable Jigsaw Studio inverse rotation");
        };
    }

    private static LocalPosition forwardPosition(
            int x,
            int y,
            int z,
            int sourceWidth,
            int sourceDepth,
            int displayRotationQuarterTurns
    ) {
        return switch (Math.floorMod(displayRotationQuarterTurns, 4)) {
            case 0 -> new LocalPosition(x, y, z);
            case 1 -> new LocalPosition(sourceDepth - 1 - z, y, x);
            case 2 -> new LocalPosition(sourceWidth - 1 - x, y, sourceDepth - 1 - z);
            case 3 -> new LocalPosition(z, y, sourceWidth - 1 - x);
            default -> throw new IllegalStateException("Unreachable Jigsaw Studio display rotation");
        };
    }

    static void requireWorkcellTopology(
            JigsawStudioBay workcell,
            List<IrisJigsawConnector> connectors,
            int displayRotationQuarterTurns
    ) throws IOException {
        JigsawPlanarArchetype expected = workcell.archetype().orElse(null);
        if (expected == null) {
            return;
        }
        int mask = 0;
        for (IrisJigsawConnector connector : connectors) {
            mask |= switch (connector.getDirection()) {
                case NORTH_NEGATIVE_Z -> JigsawPlanarDirection.NORTH.bit();
                case EAST_POSITIVE_X -> JigsawPlanarDirection.EAST.bit();
                case SOUTH_POSITIVE_Z -> JigsawPlanarDirection.SOUTH.bit();
                case WEST_NEGATIVE_X -> JigsawPlanarDirection.WEST.bit();
                case UP_POSITIVE_Y, DOWN_NEGATIVE_Y -> throw new WorkcellTopologyException(
                        "Planar workcell '" + workcell.stableId()
                                + "' cannot save a vertical connector. Remove it or use Reset Connector Blocks.");
            };
        }
        JigsawPlanarTopology sourceTopology = JigsawPlanarTopology.fromMask(mask);
        JigsawPlanarTopology displayedTopology = sourceTopology.rotateClockwise(displayRotationQuarterTurns);
        if (displayedTopology != expected.canonicalTopology()) {
            throw new WorkcellTopologyException("Workcell '" + workcell.stableId() + "' requires "
                    + topologyDescription(expected.canonicalTopology())
                    + ", but the edited markers form "
                    + topologyDescription(displayedTopology)
                    + ". Use Reset Connector Blocks to restore the saved topology, or edit the markers to match the floor glyph.");
        }
    }

    private static String topologyDescription(JigsawPlanarTopology topology) {
        int connectorCount = topology.directions().size();
        return topology.name().toLowerCase(Locale.ROOT).replace('_', ' ')
                + " (" + connectorCount + " horizontal connector"
                + (connectorCount == 1 ? "" : "s") + ")";
    }

    static void storeConnectorFinalState(
            IrisObject object,
            int x,
            int y,
            int z,
            BlockData finalData
    ) {
        Objects.requireNonNull(object, "Jigsaw Studio captured object");
        BlockData activeFinalData = Objects.requireNonNull(finalData, "Jigsaw connector final state");
        if (activeFinalData.getMaterial() != Material.STRUCTURE_VOID) {
            object.setUnsigned(x, y, z, BukkitBlockState.of(activeFinalData));
        }
    }

    record ChunkCaptureArea(
            int chunkX,
            int chunkZ,
            int minimumX,
            int maximumX,
            int minimumZ,
            int maximumZ
    ) {
        ChunkCaptureArea {
            if (minimumX < 0 || minimumZ < 0 || maximumX <= minimumX || maximumZ <= minimumZ) {
                throw new IllegalArgumentException("Jigsaw Studio chunk capture intersection is invalid");
            }
        }

        boolean contains(int x, int z) {
            return x >= minimumX && x < maximumX && z >= minimumZ && z < maximumZ;
        }
    }

    record CapturedBlock(
            int x,
            int y,
            int z,
            PlatformBlockState state,
            TileData tileData
    ) {
        CapturedBlock {
            Objects.requireNonNull(state, "Jigsaw Studio captured block state");
            tileData = tileData == null ? null : tileData.clone();
        }

        public TileData tileData() {
            return tileData == null ? null : tileData.clone();
        }
    }

    record CapturedConnector(
            int x,
            int y,
            int z,
            IrisDirection direction,
            IrisDirection top,
            String pool,
            String name,
            String targetName,
            String channel,
            JigsawJoint joint,
            String finalState,
            int selectionPriority,
            int placementPriority
    ) {
        CapturedConnector {
            Objects.requireNonNull(direction, "Jigsaw Studio captured connector direction");
            Objects.requireNonNull(top, "Jigsaw Studio captured connector top");
            Objects.requireNonNull(pool, "Jigsaw Studio captured connector pool");
            Objects.requireNonNull(name, "Jigsaw Studio captured connector name");
            Objects.requireNonNull(targetName, "Jigsaw Studio captured connector target");
            channel = channel == null ? "" : channel;
            Objects.requireNonNull(joint, "Jigsaw Studio captured connector joint");
            Objects.requireNonNull(finalState, "Jigsaw Studio captured connector final state");
        }

        static CapturedConnector from(IrisJigsawConnector connector) {
            IrisJigsawConnector source = Objects.requireNonNull(connector, "Jigsaw Studio captured connector");
            IrisPosition position = source.getPosition();
            return new CapturedConnector(
                    position.getX(),
                    position.getY(),
                    position.getZ(),
                    source.getDirection(),
                    source.getTop(),
                    source.getPool(),
                    source.getName(),
                    source.getTargetName(),
                    source.getChannel(),
                    source.getJoint(),
                    source.getFinalState(),
                    source.getSelectionPriority(),
                    source.getPlacementPriority());
        }

        IrisJigsawConnector toConnector() {
            return new IrisJigsawConnector()
                    .setPosition(new IrisPosition(x, y, z))
                    .setDirection(direction)
                    .setTop(top)
                    .setPool(pool)
                    .setName(name)
                    .setTargetName(targetName)
                    .setChannel(channel)
                    .setJoint(joint)
                    .setFinalState(finalState)
                    .setSelectionPriority(selectionPriority)
                    .setPlacementPriority(placementPriority);
        }
    }

    record ChunkSnapshot(
            ChunkCaptureArea area,
            List<CapturedBlock> blocks,
            List<CapturedConnector> connectors
    ) {
        ChunkSnapshot {
            Objects.requireNonNull(area, "Jigsaw Studio captured chunk area");
            blocks = List.copyOf(blocks);
            connectors = List.copyOf(connectors);
        }
    }

    record LocalPosition(int x, int y, int z) {
    }

    record Capture(byte[] objectContent, List<IrisJigsawConnector> connectors, boolean hasBlockEntities) {
        Capture {
            objectContent = Objects.requireNonNull(objectContent, "Jigsaw Studio object content").clone();
            connectors = List.copyOf(connectors);
        }

        public byte[] objectContent() {
            return objectContent.clone();
        }
    }

    static final class WorkcellTopologyException extends IOException {
        WorkcellTopologyException(String message) {
            super(message);
        }
    }
}
