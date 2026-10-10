package art.arcane.iris.nativegen;

import art.arcane.iris.engine.framework.nativefit.NativeTemplateCells;
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

    /**
     * The piece's processed template blocks inside {@code region}, processed against the world as it is now, as the
     * modules' fit reads them: removed blocks have no state, and the count of template blocks read is reported.
     */
    static NativeTemplateCells<BlockState> cells(
            WorldGenLevel world, PoolElementStructurePiece piece, BlockPos referencePosition, BoundingBox region,
            Supplier<StructureTemplateManager> templates) {
        int[] readBlocks = {0};
        OccupancyResult occupancy = resolve(world, piece, referencePosition, region, templates, region::isInside,
                amount -> readBlocks[0] = Math.addExact(readBlocks[0], amount));
        if (!occupancy.resolved()) {
            return new NativeTemplateCells<>(false, new int[0], new int[0], new int[0], new BlockState[0], 0);
        }
        int size = occupancy.cells().size();
        int[] xs = new int[size];
        int[] ys = new int[size];
        int[] zs = new int[size];
        BlockState[] states = new BlockState[size];
        int cell = 0;
        for (Map.Entry<Long, OccupancyCell> entry : occupancy.cells().entrySet()) {
            BlockPos position = BlockPos.of(entry.getKey());
            xs[cell] = position.getX();
            ys[cell] = position.getY();
            zs[cell] = position.getZ();
            states[cell] = entry.getValue().state();
            cell++;
        }
        return new NativeTemplateCells<>(true, xs, ys, zs, states, readBlocks[0]);
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

    private record LeafPlacement(
            StructurePlaceSettings settings,
            List<StructureTemplate.StructureBlockInfo> rawBlocks,
            StructureTemplate template) {
    }
}
