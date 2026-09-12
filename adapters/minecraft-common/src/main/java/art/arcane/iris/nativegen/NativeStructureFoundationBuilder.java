package art.arcane.iris.nativegen;

import art.arcane.iris.structure.placement.IrisStructureStiltSettings;
import art.arcane.volmlib.util.math.RNG;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import java.util.function.IntBinaryOperator;

public final class NativeStructureFoundationBuilder {
    private static final int FOUNDATION_VERTICAL_TOLERANCE = 1;

    private NativeStructureFoundationBuilder() {
    }

    private static List<FoundationColumn> foundationEnvelope(BoundingBox area, StructureStart start) {
        BoundingBox structure = NativeStructureReferenceEnvelope.contentBounds(start);
        int minX = Math.max(area.minX(), structure.minX());
        int minZ = Math.max(area.minZ(), structure.minZ());
        int maxX = Math.min(area.maxX(), structure.maxX());
        int maxZ = Math.min(area.maxZ(), structure.maxZ());
        if (minX > maxX || minZ > maxZ) {
            return List.of();
        }
        List<StructurePiece> pieces = start.getPieces();
        List<FoundationColumn> columns = new ArrayList<>((maxX - minX + 1) * (maxZ - minZ + 1));
        BitSet envelope = new BitSet(area.getYSpan());
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                envelope.clear();
                markFoundationEnvelope(envelope, pieces, area, x, z);
                int cellCount = envelope.cardinality();
                if (cellCount == 0) {
                    continue;
                }
                int[] ys = new int[cellCount];
                int cell = 0;
                for (int bit = envelope.nextSetBit(0); bit >= 0; bit = envelope.nextSetBit(bit + 1)) {
                    ys[cell++] = area.minY() + bit;
                }
                columns.add(new FoundationColumn(x, z, ys));
            }
        }
        return List.copyOf(columns);
    }

    private static void markFoundationEnvelope(BitSet envelope, List<StructurePiece> pieces, BoundingBox area,
                                               int x, int z) {
        for (StructurePiece piece : pieces) {
            BoundingBox bounds = piece.getBoundingBox();
            if (x < bounds.minX() || x > bounds.maxX() || z < bounds.minZ() || z > bounds.maxZ()) {
                continue;
            }
            int groundY = bounds.minY();
            if (piece instanceof PoolElementStructurePiece poolPiece) {
                groundY += poolPiece.getGroundLevelDelta();
                if (groundY < bounds.minY()) {
                    continue;
                }
            }
            int minY = Math.max(area.minY(), bounds.minY());
            int maxY = Math.min(area.maxY(), Math.min(bounds.maxY(), groundY + FOUNDATION_VERTICAL_TOLERANCE));
            if (minY <= maxY) {
                envelope.set(minY - area.minY(), maxY - area.minY() + 1);
            }
        }
    }

    static void placeStilts(WorldGenLevel world, BoundingBox area, String structureId,
                            StructureStart start, IrisStructureStiltSettings settings,
                            NativeStructurePostProcessor.PaletteBlockResolver paletteBlockResolver,
                            IntBinaryOperator surfaceHeight,
                            boolean surfaceStructure) {
        Objects.requireNonNull(surfaceHeight, "Structure stilts require an Iris terrain height resolver");
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        int structureHash = structureId == null ? 0 : structureId.hashCode();
        RNG rng = new RNG(world.getSeed() ^ structureHash);
        for (FoundationColumn column : foundationEnvelope(area, start)) {
            if (!isStiltColumn(column.x(), column.z(), settings.getSpacing())) {
                continue;
            }
            int foundationY = findFoundationY(world, column, position);
            if (foundationY == Integer.MIN_VALUE) {
                continue;
            }
            int terrainY = surfaceStructure
                    ? Math.max(area.minY(), Math.min(
                            area.maxY(), surfaceHeight.applyAsInt(column.x(), column.z())))
                    : area.minY() - 1;
            int anchorY = findStiltAnchorY(
                    world, column.x(), column.z(), foundationY,
                    Math.max(1, settings.getMaxDepth()), terrainY, area.minY(), position);
            if (anchorY == Integer.MIN_VALUE) {
                continue;
            }
            for (int y = foundationY - 1; y > anchorY; y--) {
                position.set(column.x(), y, column.z());
                BlockState stilt = settings.getPalette() == null
                        ? Blocks.COBBLESTONE.defaultBlockState()
                        : Objects.requireNonNull(
                                paletteBlockResolver.resolve(
                                        settings.getPalette(), rng, column.x(), y, column.z()),
                                "Stilt palette returned no block for " + structureId + " at "
                                        + column.x() + "," + y + "," + column.z());
                world.setBlock(position, stilt, 2);
            }
        }
    }

    static boolean isStiltColumn(int x, int z, int spacing) {
        int resolvedSpacing = Math.max(1, spacing);
        return resolvedSpacing == 1
                || Math.floorMod(x, resolvedSpacing) == 0
                && Math.floorMod(z, resolvedSpacing) == 0;
    }

    static int findStiltAnchorY(
            WorldGenLevel world, int x, int z, int foundationY, int maxDepth,
            int terrainY, int areaMinY, BlockPos.MutableBlockPos position) {
        int minimumAnchorY = Math.max(
                areaMinY, Math.max(terrainY, foundationY - maxDepth - 1));
        for (int y = foundationY - 1; y >= minimumAnchorY; y--) {
            BlockState state = world.getBlockState(position.set(x, y, z));
            boolean vegetation = state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES);
            if (!vegetation && state.isFaceSturdy(
                    world, position, Direction.UP, SupportType.FULL)) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    public static StiltSupportAudit auditStiltSupport(WorldGenLevel world, BoundingBox area,
                                                       StructureStart start, BlockState expectedStilt,
                                                       IntBinaryOperator surfaceHeight) {
        Objects.requireNonNull(expectedStilt, "Expected stilt state must not be null");
        Objects.requireNonNull(surfaceHeight, "Structure stilt audit requires an Iris terrain height resolver");
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        int baseColumns = 0;
        int stiltBlocks = 0;
        int stiltColumns = 0;
        int unsupportedColumns = 0;
        for (FoundationColumn column : foundationEnvelope(area, start)) {
            int foundationY = findFoundationY(world, column, position);
            if (foundationY == Integer.MIN_VALUE) {
                continue;
            }
            baseColumns++;
            int terrainY = Math.max(area.minY(), Math.min(
                    area.maxY(), surfaceHeight.applyAsInt(column.x(), column.z())));
            boolean grounded = foundationY <= terrainY + 1;
            boolean stiltColumn = false;
            for (int y = foundationY - 1; y >= area.minY(); y--) {
                if (y <= terrainY) {
                    grounded = true;
                    break;
                }
                BlockState state = world.getBlockState(position.set(column.x(), y, column.z()));
                if (state.is(expectedStilt.getBlock())) {
                    stiltBlocks++;
                    stiltColumn = true;
                    if (y == area.minY()) {
                        grounded = true;
                    }
                    continue;
                }
                boolean vegetation = state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES);
                grounded = state.isSolid() && !vegetation;
                break;
            }
            if (stiltColumn) {
                stiltColumns++;
            }
            if (!grounded) {
                unsupportedColumns++;
            }
        }
        return new StiltSupportAudit(baseColumns, stiltBlocks, stiltColumns, unsupportedColumns);
    }

    private static int findFoundationY(WorldGenLevel world, FoundationColumn column,
                                       BlockPos.MutableBlockPos position) {
        for (int cell = 0; cell < column.ys().length; cell++) {
            int y = column.ys()[cell];
            BlockState state = world.getBlockState(position.set(column.x(), y, column.z()));
            if (state.isSolid()) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    private record FoundationColumn(int x, int z, int[] ys) {
    }

    public record StiltSupportAudit(int baseColumns, int stiltBlocks, int stiltColumns,
                                    int unsupportedColumns) {
    }
}
