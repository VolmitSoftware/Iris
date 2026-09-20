package art.arcane.iris.nativegen.v26_3_R1;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.EmptyPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.ListPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

final class NativeStructureTemplateOccupancy {
    private static final OccupancyCell BLOCKER = new OccupancyCell(null, true);

    private NativeStructureTemplateOccupancy() {
    }

    static OccupancyResult resolve(
            WorldGenLevel world, PoolElementStructurePiece piece,
            BlockPos referencePosition, BoundingBox processingArea,
            Supplier<StructureTemplateManager> templates,
            PositionFilter filter, CellBudget budget) {
        List<SinglePoolElement> leaves = new ArrayList<>();
        if (!flattenSingles(piece.getElement(), leaves)) {
            return new OccupancyResult(false, Map.of());
        }
        List<LeafPlacement> placements = new ArrayList<>(leaves.size());
        Map<Long, OccupancyCell> occupancy = new HashMap<>();
        for (SinglePoolElement leaf : leaves) {
            StructurePlaceSettings settings = NativeStructureReflection.resolvePlacementSettings(
                    leaf, piece, processingArea);
            StructureTemplate template = NativeStructureReflection.resolveTemplate(leaf, templates);
            List<StructureTemplate.StructureBlockInfo> rawBlocks =
                    NativeStructureReflection.resolveTemplateBlocks(
                            template, settings, piece.getPosition());
            budget.consume(rawBlocks.size());
            placements.add(new LeafPlacement(settings, rawBlocks, template));
            for (StructureTemplate.StructureBlockInfo raw : rawBlocks) {
                BlockPos position = piece.getPosition().offset(
                        StructureTemplate.calculateRelativePosition(settings, raw.pos()));
                if (filter.retain(position)) {
                    occupancy.put(position.asLong(), BLOCKER);
                }
            }
        }
        for (LeafPlacement placement : placements) {
            List<StructureTemplate.StructureBlockInfo> processed =
                    NativeStructureReflection.processTemplateBlocks(
                            world, piece.getPosition(), referencePosition,
                            placement.settings(), placement.rawBlocks(), placement.template());
            budget.consume(processed.size());
            for (StructureTemplate.StructureBlockInfo block : processed) {
                BlockPos position = block.pos();
                if (!filter.retain(position)) {
                    continue;
                }
                BlockState state = block.state()
                        .mirror(placement.settings().getMirror())
                        .rotate(placement.settings().getRotation());
                occupancy.put(position.asLong(), new OccupancyCell(state, false));
            }
        }
        return new OccupancyResult(true, occupancy);
    }

    static Map<Long, LowestCell> lowestCells(Map<Long, OccupancyCell> occupancy) {
        Map<Long, LowestCell> lowest = new HashMap<>();
        for (Map.Entry<Long, OccupancyCell> entry : occupancy.entrySet()) {
            OccupancyCell cell = entry.getValue();
            BlockPos position = BlockPos.of(entry.getKey());
            long column = columnKey(position.getX(), position.getZ());
            LowestCell current = lowest.get(column);
            if (current == null || position.getY() < current.y()) {
                lowest.put(column, new LowestCell(
                        position.getX(), position.getY(), position.getZ(), cell));
            }
        }
        return lowest;
    }

    static Map<Long, LowestCell> lowestProcessedSolidCells(Map<Long, OccupancyCell> occupancy) {
        Map<Long, LowestCell> lowest = new HashMap<>();
        for (Map.Entry<Long, OccupancyCell> entry : occupancy.entrySet()) {
            OccupancyCell cell = entry.getValue();
            if (cell.blocker() || !isSolidBase(cell.state())) {
                continue;
            }
            BlockPos position = BlockPos.of(entry.getKey());
            long column = columnKey(position.getX(), position.getZ());
            LowestCell current = lowest.get(column);
            if (current == null || position.getY() < current.y()) {
                lowest.put(column, new LowestCell(
                        position.getX(), position.getY(), position.getZ(), cell));
            }
        }
        return lowest;
    }

    static boolean isSolidBase(BlockState state) {
        return state != null && state.isSolid()
                && !state.is(Blocks.STRUCTURE_VOID)
                && !state.is(Blocks.JIGSAW)
                && state.getFluidState().isEmpty();
    }

    static long columnKey(int x, int z) {
        return (long) x << 32 ^ z & 0xffffffffL;
    }

    private static boolean flattenSingles(
            StructurePoolElement element, List<SinglePoolElement> leaves) {
        if (element instanceof ListPoolElement listElement) {
            for (StructurePoolElement child : listElement.getElements()) {
                if (!flattenSingles(child, leaves)) {
                    return false;
                }
            }
            return true;
        }
        if (element == EmptyPoolElement.INSTANCE) {
            return true;
        }
        if (element instanceof SinglePoolElement singleElement) {
            leaves.add(singleElement);
            return true;
        }
        return false;
    }

    @FunctionalInterface
    interface PositionFilter {
        boolean retain(BlockPos position);
    }

    @FunctionalInterface
    interface CellBudget {
        void consume(int amount);
    }

    record OccupancyResult(boolean resolved, Map<Long, OccupancyCell> cells) {
    }

    record OccupancyCell(BlockState state, boolean blocker) {
    }

    record LowestCell(int x, int y, int z, OccupancyCell occupancy) {
    }

    private record LeafPlacement(
            StructurePlaceSettings settings,
            List<StructureTemplate.StructureBlockInfo> rawBlocks,
            StructureTemplate template) {
    }
}
