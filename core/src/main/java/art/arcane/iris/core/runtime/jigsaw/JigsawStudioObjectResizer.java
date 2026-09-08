package art.arcane.iris.core.runtime.jigsaw;

import art.arcane.iris.engine.object.IrisDirection;
import art.arcane.iris.engine.object.IrisJigsawConnector;
import art.arcane.iris.engine.object.IrisJigsawPiece;
import art.arcane.iris.engine.object.IrisJigsawWorkcellArchetype;
import art.arcane.iris.engine.object.IrisObject;
import art.arcane.iris.engine.object.IrisPosition;
import art.arcane.iris.engine.object.TileData;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.math.IrisBlockVector;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class JigsawStudioObjectResizer {
    private JigsawStudioObjectResizer() {
    }

    static PlanarPieceObjectResize resizePlanarPieceObject(
            IrisObject source,
            IrisJigsawPiece piece,
            JigsawPlanarArchetype archetype,
            JigsawStudioCellDimensions dimensions,
            String pieceKey
    ) throws IOException {
        IrisObject sourceObject = Objects.requireNonNull(source, "Planar Jigsaw Studio source object");
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
        if (sourceObject.getW() < 1 || sourceObject.getH() < 1 || sourceObject.getD() < 1) {
            throw new IOException("Planar piece '" + targetPieceKey + "' has invalid object dimensions");
        }
        if (IrisJigsawWorkcellArchetype.fromPiece(targetPiece) != targetArchetype.modelArchetype()) {
            throw new IOException("Planar piece '" + targetPieceKey + "' does not belong to "
                    + targetArchetype.stableId());
        }

        int quarterTurns = targetArchetype.modelArchetype().sourceToCanonicalQuarterTurns(targetPiece);
        JigsawStudioCellDimensions sourceDimensions = new JigsawStudioCellDimensions(
                sourceObject.getW(),
                sourceObject.getH(),
                sourceObject.getD());
        JigsawStudioCellDimensions canonicalDimensions = canonicalDimensions(sourceDimensions, quarterTurns);
        Map<LocalPosition, PlatformBlockState> canonicalBlocks = canonicalBlocks(
                sourceObject,
                quarterTurns,
                targetPieceKey);
        Map<LocalPosition, TileData> canonicalTiles = canonicalTiles(
                sourceObject,
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
        IrisObject resizedObject = rebuildSourceObject(
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
            connectorResize.connector().setPosition(sourcePosition.toIrisPosition());
            if (!connectorResize.sourceCanonical().equals(connectorResize.targetCanonical())) {
                relocatedConnectors++;
            }
        }
        return new PlanarPieceObjectResize(resizedObject, relocatedConnectors);
    }

    private static Map<LocalPosition, PlatformBlockState> canonicalBlocks(
            IrisObject source,
            int quarterTurns,
            String pieceKey
    ) throws IOException {
        Map<LocalPosition, PlatformBlockState> blocks = new LinkedHashMap<>();
        for (Map.Entry<IrisBlockVector, PlatformBlockState> entry : source.getBlocks()) {
            LocalPosition sourcePosition = unsignedPosition(entry.getKey(), source);
            requireInside(sourcePosition, source.getW(), source.getH(), source.getD(),
                    "stored block", pieceKey);
            PlatformBlockState state = entry.getValue();
            if (state == null) {
                throw new IOException("Planar piece '" + pieceKey + "' contains a null stored block state");
            }
            LocalPosition canonicalPosition = toCanonical(
                    sourcePosition,
                    source.getW(),
                    source.getD(),
                    quarterTurns);
            if (blocks.putIfAbsent(canonicalPosition, state) != null) {
                throw new IOException("Planar piece '" + pieceKey
                        + "' maps more than one stored block to " + canonicalPosition.describe());
            }
        }
        return blocks;
    }

    private static Map<LocalPosition, TileData> canonicalTiles(
            IrisObject source,
            int quarterTurns,
            String pieceKey
    ) throws IOException {
        Map<LocalPosition, TileData> tiles = new LinkedHashMap<>();
        for (Map.Entry<IrisBlockVector, TileData> entry : source.getStates()) {
            LocalPosition sourcePosition = unsignedPosition(entry.getKey(), source);
            requireInside(sourcePosition, source.getW(), source.getH(), source.getD(),
                    "tile data", pieceKey);
            TileData tile = entry.getValue();
            if (tile == null) {
                throw new IOException("Planar piece '" + pieceKey + "' contains null tile data");
            }
            LocalPosition canonicalPosition = toCanonical(
                    sourcePosition,
                    source.getW(),
                    source.getD(),
                    quarterTurns);
            if (tiles.putIfAbsent(canonicalPosition, tile.clone()) != null) {
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
        IrisPosition sourceSize = dimensionsPosition(sourceDimensions);
        IrisPosition canonicalSize = dimensionsPosition(canonicalDimensions);
        IrisPosition targetSize = dimensionsPosition(targetDimensions);
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
            IrisDirection canonicalDirection = rotateHorizontalDirection(
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
            Map<LocalPosition, PlatformBlockState> blocks,
            Map<LocalPosition, TileData> tiles,
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
            PlatformBlockState state = blocks.remove(connectorResize.sourceCanonical());
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
            Map<LocalPosition, PlatformBlockState> blocks,
            Map<LocalPosition, TileData> tiles,
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

    private static IrisObject rebuildSourceObject(
            Map<LocalPosition, PlatformBlockState> canonicalBlocks,
            Map<LocalPosition, TileData> canonicalTiles,
            JigsawStudioCellDimensions sourceDimensions,
            int quarterTurns,
            String pieceKey
    ) throws IOException {
        IrisObject resized = new IrisObject(
                sourceDimensions.width(),
                sourceDimensions.height(),
                sourceDimensions.depth());
        Set<LocalPosition> sourcePositions = new LinkedHashSet<>();
        for (Map.Entry<LocalPosition, PlatformBlockState> entry : canonicalBlocks.entrySet()) {
            LocalPosition sourcePosition = toSource(entry.getKey(), sourceDimensions, quarterTurns);
            if (!sourcePositions.add(sourcePosition)) {
                throw new IOException("Planar piece '" + pieceKey
                        + "' maps multiple stored blocks to " + sourcePosition.describe());
            }
            resized.setUnsigned(
                    sourcePosition.x(),
                    sourcePosition.y(),
                    sourcePosition.z(),
                    entry.getValue());
        }
        sourcePositions.clear();
        for (Map.Entry<LocalPosition, TileData> entry : canonicalTiles.entrySet()) {
            LocalPosition sourcePosition = toSource(entry.getKey(), sourceDimensions, quarterTurns);
            if (!sourcePositions.add(sourcePosition)) {
                throw new IOException("Planar piece '" + pieceKey
                        + "' maps multiple tile payloads to " + sourcePosition.describe());
            }
            resized.setUnsignedTile(
                    sourcePosition.x(),
                    sourcePosition.y(),
                    sourcePosition.z(),
                    entry.getValue().clone());
        }
        return resized;
    }

    private static LocalPosition unsignedPosition(IrisBlockVector signed, IrisObject object) {
        return new LocalPosition(
                signed.getBlockX() + object.getCenter().getBlockX(),
                signed.getBlockY() + object.getCenter().getBlockY(),
                signed.getBlockZ() + object.getCenter().getBlockZ());
    }

    static JigsawStudioCellDimensions canonicalDimensions(
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

    private static IrisDirection rotateHorizontalDirection(IrisDirection direction, int quarterTurns) {
        JigsawPlanarDirection planarDirection = switch (direction) {
            case NORTH_NEGATIVE_Z -> JigsawPlanarDirection.NORTH;
            case EAST_POSITIVE_X -> JigsawPlanarDirection.EAST;
            case SOUTH_POSITIVE_Z -> JigsawPlanarDirection.SOUTH;
            case WEST_NEGATIVE_X -> JigsawPlanarDirection.WEST;
            case UP_POSITIVE_Y, DOWN_NEGATIVE_Y -> throw new IllegalArgumentException(
                    "Planar connector direction must be horizontal");
        };
        return planarDirection.rotateClockwise(quarterTurns).irisDirection();
    }

    static IrisPosition dimensionsPosition(JigsawStudioCellDimensions dimensions) {
        return new IrisPosition(dimensions.width(), dimensions.height(), dimensions.depth());
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

    static IrisObject resizeObject(
            IrisObject source,
            JigsawStudioCellDimensions dimensions,
            String pieceKey
    ) throws IOException {
        IrisObject object = Objects.requireNonNull(source, "Jigsaw Studio source object");
        JigsawStudioCellDimensions target = Objects.requireNonNull(
                dimensions,
                "Jigsaw Studio target object dimensions");
        String normalizedPiece = pieceKey == null || pieceKey.isBlank() ? "unknown" : pieceKey;
        IrisObject resized = new IrisObject(target.width(), target.height(), target.depth());
        for (Map.Entry<IrisBlockVector, PlatformBlockState> entry : object.getBlocks()) {
            IrisBlockVector position = entry.getKey();
            LocalPosition unsigned = unsignedPosition(position, object);
            if (!inside(unsigned, target)) {
                throw new IOException("Spatial piece '" + normalizedPiece
                        + "' has a stored block, including explicit air, at " + unsigned.describe()
                        + " that would be cropped by the requested variant size");
            }
            resized.setUnsigned(
                    unsigned.x(),
                    unsigned.y(),
                    unsigned.z(),
                    entry.getValue());
        }
        for (Map.Entry<IrisBlockVector, TileData> entry : object.getStates()) {
            IrisBlockVector position = entry.getKey();
            LocalPosition unsigned = unsignedPosition(position, object);
            if (!inside(unsigned, target)) {
                throw new IOException("Spatial piece '" + normalizedPiece + "' has tile data at "
                        + unsigned.describe() + " that would be cropped by the requested variant size");
            }
            resized.setUnsignedTile(
                    unsigned.x(),
                    unsigned.y(),
                    unsigned.z(),
                    entry.getValue().clone());
        }
        return resized;
    }

    static void requireConnectorsInside(
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

    static String describeDimensions(JigsawStudioCellDimensions dimensions) {
        return dimensions.width() + "x" + dimensions.height() + "x" + dimensions.depth();
    }

    record PlanarPieceObjectResize(IrisObject object, int relocatedConnectors) {
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

    private record BlockRelocation(LocalPosition target, PlatformBlockState state, boolean present) {
        private BlockRelocation {
            Objects.requireNonNull(target, "Planar Jigsaw Studio connector block target");
            if (present) {
                Objects.requireNonNull(state, "Planar Jigsaw Studio connector block state");
            }
        }
    }

    private record LocalPosition(int x, int y, int z) {
        private static LocalPosition from(IrisPosition position) {
            return new LocalPosition(position.getX(), position.getY(), position.getZ());
        }

        private IrisPosition toIrisPosition() {
            return new IrisPosition(x, y, z);
        }

        private String describe() {
            return x + "," + y + "," + z;
        }
    }
}
