package art.arcane.iris.nativegen;

import art.arcane.iris.engine.framework.NativeStartPlacement;
import art.arcane.iris.engine.framework.StructureVerticalBounds;
import art.arcane.iris.spi.IrisLogging;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.ScatteredFeaturePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import net.minecraft.world.level.levelgen.structure.structures.DesertPyramidPiece;
import net.minecraft.world.level.levelgen.structure.structures.DesertPyramidStructure;
import net.minecraft.world.level.levelgen.structure.structures.JungleTemplePiece;
import net.minecraft.world.level.levelgen.structure.structures.JungleTempleStructure;
import net.minecraft.world.level.levelgen.structure.structures.OceanMonumentPieces;
import net.minecraft.world.level.levelgen.structure.structures.OceanMonumentStructure;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.IntBinaryOperator;
import java.util.function.IntSupplier;

public final class NativeStructureVerticalPlacer {
    private static final String DESERT_PYRAMID_ID = "minecraft:desert_pyramid";
    private static final String JUNGLE_PYRAMID_ID = "minecraft:jungle_pyramid";
    private static final int MONUMENT_BASE_BELOW_SEA_LEVEL = 24;
    private static final String OCEAN_MONUMENT_ID = "minecraft:monument";

    private NativeStructureVerticalPlacer() {
    }

    /**
     * Moves the platform's start {@code shift} blocks up (negative: down), as the modules asked, inside the world: an
     * ocean monument and a desert or jungle temple are aligned first (to the sea level, to the terrain) and shifted from
     * there. Returns how far the start moved.
     */
    public static int applyVerticalPlacement(StructureStart start, String structureId, int shift,
                                             int seaLevel, int worldMinY, int worldMaxYExclusive,
                                             IntBinaryOperator surfaceHeight) {
        if (isOceanMonument(start, structureId)) {
            return alignOceanMonumentToSeaLevel(
                    start, shift, seaLevel, worldMinY, worldMaxYExclusive);
        }
        if (isAdjustedScatteredStructure(start, structureId)) {
            return alignScatteredStructureToSurface(
                    start, structureId, shift, worldMinY, worldMaxYExclusive, surfaceHeight);
        }
        return applyVerticalShift(start, structureId, shift, worldMinY, worldMaxYExclusive);
    }

    /**
     * The platform start as the modules see it before it is published: its bounds, whether the platform generates it
     * underground and whether the host aligns it itself.
     */
    public static NativeStartPlacement placementOf(StructureStart start, String structureId, boolean underground,
                                                   int bottomY, IntBinaryOperator surfaceHeight) {
        BoundingBox bounds = start.getBoundingBox();
        return new NativeStartPlacement(structureId, start.getChunkPos().x(), start.getChunkPos().z(),
                bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ(),
                underground, isOceanMonument(start, structureId) || isAdjustedScatteredStructure(start, structureId),
                bottomY, surfaceHeight);
    }

    public static StructureStart relocateToMinY(StructureStart start, Structure source, int targetMinY,
                                                LevelHeightAccessor heightAccessor) {
        Objects.requireNonNull(start, "Native structure start must not be null");
        Objects.requireNonNull(source, "Native structure source must not be null");
        Objects.requireNonNull(heightAccessor, "Native structure height accessor must not be null");
        if (!start.isValid()) {
            return StructureStart.INVALID_START;
        }
        List<StructurePiece> pieces = start.getPieces();
        int minY = Integer.MAX_VALUE;
        for (StructurePiece piece : pieces) {
            minY = Math.min(minY, piece.getBoundingBox().minY());
        }
        if (minY == Integer.MAX_VALUE) {
            return StructureStart.INVALID_START;
        }
        int offsetY = Math.subtractExact(targetMinY, minY);
        if (offsetY != 0) {
            for (StructurePiece piece : pieces) {
                moveStructurePiece(piece, offsetY);
            }
        }
        int worldMinY = heightAccessor.getMinY() + 1;
        int worldMaxYExclusive = Math.addExact(
                heightAccessor.getMinY(), heightAccessor.getHeight());
        for (StructurePiece piece : pieces) {
            BoundingBox bounds = piece.getBoundingBox();
            if (bounds.minY() < worldMinY || bounds.maxY() >= worldMaxYExclusive) {
                throw new IllegalStateException("Native structure cannot fit target minimum Y "
                        + targetMinY + " inside world bounds [" + worldMinY + ","
                        + worldMaxYExclusive + ")");
            }
        }
        return new StructureStart(
                source,
                start.getChunkPos(),
                start.getReferences(),
                new PiecesContainer(List.copyOf(pieces))
        );
    }

    static int alignScatteredStructureToSurface(StructureStart start, String structureId,
                                                  int configuredOffset, int worldMinY,
                                                  int worldMaxYExclusive,
                                                  IntBinaryOperator surfaceHeight) {
        Objects.requireNonNull(surfaceHeight, "Scattered native structure requires a terrain height resolver");
        ScatteredFeaturePiece piece = requireAdjustedScatteredPiece(start, structureId);
        BoundingBox bounds = start.getBoundingBox();
        BoundingBox pieceBounds = piece.getBoundingBox();
        int surfaceY = representativeScatteredSurfaceY(structureId, pieceBounds, surfaceHeight);
        int targetMinY = Math.addExact(Math.addExact(surfaceY, 1), configuredOffset);
        int requestedMove = Math.subtractExact(targetMinY, pieceBounds.minY());
        int offsetY = StructureVerticalBounds.clampOffset(
                bounds.minY(), bounds.maxY(), requestedMove, worldMinY, worldMaxYExclusive);
        if (offsetY != 0) {
            moveStructureStart(start, bounds, offsetY);
        }
        setScatteredHeightPosition(piece, Math.max(0, piece.getBoundingBox().minY()));
        return offsetY;
    }

    /** Moves the start {@code shift} blocks, less where it would leave the world. Returns how far it moved. */
    public static int applyVerticalShift(StructureStart start, String structureId, int shift, int worldMinY,
                                         int worldMaxYExclusive) {
        BoundingBox bounds = start.getBoundingBox();
        int offsetY = StructureVerticalBounds.clampOffset(
                bounds.minY(), bounds.maxY(), shift, worldMinY, worldMaxYExclusive);
        if (offsetY != shift) {
            IrisLogging.warn("Native structure " + structureId + " at " + bounds.minX() + "," + bounds.minZ()
                    + " clamped to the world's height: wanted a shift of " + shift + ", used " + offsetY);
        }
        if (offsetY == 0) {
            return 0;
        }
        moveStructureStart(start, bounds, offsetY);
        return offsetY;
    }

    static int alignOceanMonumentToSeaLevel(StructureStart start, int configuredOffset, int seaLevel,
                                             int worldMinY, int worldMaxYExclusive) {
        OceanMonumentPieces.MonumentBuilding building = requireOceanMonumentBuilding(start);
        BoundingBox bounds = start.getBoundingBox();
        int targetMinY = Math.addExact(
                Math.subtractExact(seaLevel, MONUMENT_BASE_BELOW_SEA_LEVEL), configuredOffset);
        int requestedOffset = Math.subtractExact(targetMinY, building.getBoundingBox().minY());
        int offsetY = StructureVerticalBounds.clampOffset(
                bounds.minY(), bounds.maxY(), requestedOffset, worldMinY, worldMaxYExclusive);
        if (offsetY != requestedOffset) {
            throw new IllegalStateException("Ocean monument cannot align to sea level " + seaLevel
                    + " with configured offset " + configuredOffset + " inside world bounds ["
                    + worldMinY + "," + worldMaxYExclusive + ")");
        }
        if (offsetY == 0) {
            return 0;
        }
        moveStructureStart(start, bounds, offsetY);
        return offsetY;
    }

    /** Keeps an ocean monument at the sea level, shifted by {@code shift}, before each chunk places its pieces. */
    public static void ensureMonumentSeaLevelAlignment(StructureStart start, String structureId,
                                                       IntSupplier shift, int seaLevel,
                                                       int worldMinY, int worldMaxYExclusive) {
        if (!isOceanMonument(start, structureId)) {
            return;
        }
        synchronized (start) {
            alignOceanMonumentToSeaLevel(
                    start, shift.getAsInt(), seaLevel, worldMinY, worldMaxYExclusive);
        }
    }

    private static boolean isOceanMonument(StructureStart start, String structureId) {
        return OCEAN_MONUMENT_ID.equals(structureId)
                && start != null
                && start.getStructure() instanceof OceanMonumentStructure;
    }

    private static boolean isAdjustedScatteredStructure(StructureStart start, String structureId) {
        if (start == null) {
            return false;
        }
        Structure source = start.getStructure();
        return (DESERT_PYRAMID_ID.equals(structureId) && source instanceof DesertPyramidStructure)
                || (JUNGLE_PYRAMID_ID.equals(structureId) && source instanceof JungleTempleStructure);
    }

    private static ScatteredFeaturePiece requireAdjustedScatteredPiece(StructureStart start,
                                                                        String structureId) {
        Objects.requireNonNull(start, "Scattered native structure start must not be null");
        List<StructurePiece> pieces = start.getPieces();
        if (pieces.size() != 1) {
            throw new IllegalStateException(structureId + " must contain exactly one scattered piece, found "
                    + pieces.size());
        }
        StructurePiece piece = pieces.get(0);
        if (DESERT_PYRAMID_ID.equals(structureId) && piece instanceof DesertPyramidPiece desertPyramid) {
            return desertPyramid;
        }
        if (JUNGLE_PYRAMID_ID.equals(structureId) && piece instanceof JungleTemplePiece jungleTemple) {
            return jungleTemple;
        }
        throw new IllegalStateException(structureId + " contains unexpected piece "
                + piece.getClass().getName());
    }

    private static int representativeScatteredSurfaceY(String structureId, BoundingBox bounds,
                                                        IntBinaryOperator surfaceHeight) {
        if (DESERT_PYRAMID_ID.equals(structureId)) {
            return lowestSurfaceY(bounds, surfaceHeight);
        }
        if (JUNGLE_PYRAMID_ID.equals(structureId)) {
            return averageSurfaceY(bounds, surfaceHeight);
        }
        throw new IllegalStateException("Unsupported scattered native structure " + structureId);
    }

    private static int lowestSurfaceY(BoundingBox bounds, IntBinaryOperator surfaceHeight) {
        int lowestY = Integer.MAX_VALUE;
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                lowestY = Math.min(lowestY, surfaceHeight.applyAsInt(x, z));
            }
        }
        if (lowestY == Integer.MAX_VALUE) {
            throw new IllegalStateException("Scattered native structure has an empty terrain footprint");
        }
        return lowestY;
    }

    private static int averageSurfaceY(BoundingBox bounds, IntBinaryOperator surfaceHeight) {
        long totalY = 0L;
        long columns = 0L;
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                totalY += surfaceHeight.applyAsInt(x, z);
                columns++;
            }
        }
        if (columns == 0L) {
            throw new IllegalStateException("Scattered native structure has an empty terrain footprint");
        }
        return Math.toIntExact(totalY / columns);
    }

    private static void setScatteredHeightPosition(ScatteredFeaturePiece piece, int heightPosition) {
        try {
            NativeStructureReflection.ScatteredHeightPositionAccess.FIELD.setInt(piece, heightPosition);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("Cannot lock scattered native structure height", error);
        }
    }

    private static OceanMonumentPieces.MonumentBuilding requireOceanMonumentBuilding(StructureStart start) {
        Objects.requireNonNull(start, "Ocean monument start must not be null");
        List<StructurePiece> pieces = start.getPieces();
        OceanMonumentPieces.MonumentBuilding building = null;
        for (StructurePiece piece : pieces) {
            if (piece instanceof OceanMonumentPieces.MonumentBuilding monumentBuilding) {
                building = monumentBuilding;
            }
        }
        if (pieces.size() != 1 || building == null) {
            throw new IllegalStateException("minecraft:monument must contain exactly one MonumentBuilding, found "
                    + pieces.size() + " pieces");
        }
        return building;
    }

    private static void moveStructureStart(StructureStart start, BoundingBox cachedBounds, int offsetY) {
        for (StructurePiece piece : start.getPieces()) {
            moveStructurePiece(piece, offsetY);
        }
        cachedBounds.move(0, offsetY, 0);
    }

    private static void moveStructurePiece(StructurePiece piece, int offsetY) {
        piece.move(0, offsetY, 0);
        if (piece instanceof OceanMonumentPieces.MonumentBuilding building) {
            for (StructurePiece child : monumentChildPieces(building)) {
                child.move(0, offsetY, 0);
            }
        }
        if (piece instanceof PoolElementStructurePiece poolPiece) {
            List<JigsawJunction> junctions = poolPiece.getJunctions();
            for (int i = 0; i < junctions.size(); i++) {
                JigsawJunction junction = junctions.get(i);
                junctions.set(i, new JigsawJunction(
                        junction.getSourceX(),
                        junction.getSourceGroundY() + offsetY,
                        junction.getSourceZ(),
                        junction.getDeltaY(),
                        junction.getDestProjection()));
            }
        }
    }

    static List<StructurePiece> monumentChildPieces(OceanMonumentPieces.MonumentBuilding building) {
        Object value;
        try {
            value = NativeStructureReflection.MonumentChildPiecesAccess.FIELD.get(building);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("Cannot read Ocean Monument child pieces", error);
        }
        if (!(value instanceof List<?> children)) {
            throw new IllegalStateException("Ocean Monument child-pieces field is not a list");
        }
        List<StructurePiece> pieces = new ArrayList<>(children.size());
        for (Object child : children) {
            if (!(child instanceof StructurePiece monumentPiece)
                    || child.getClass().getEnclosingClass() != OceanMonumentPieces.class) {
                throw new IllegalStateException("Ocean Monument child-pieces list contains "
                        + (child == null ? "null" : child.getClass().getName()));
            }
            pieces.add(monumentPiece);
        }
        return List.copyOf(pieces);
    }
}
