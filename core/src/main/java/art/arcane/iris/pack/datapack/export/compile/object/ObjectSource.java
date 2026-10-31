package art.arcane.iris.pack.datapack.export.compile.object;

import art.arcane.iris.generation.decoration.tree.TreeGrounding;
import art.arcane.iris.pack.datapack.export.StructureTemplateEncoder;
import art.arcane.iris.pack.datapack.export.TemplateBlock;
import art.arcane.iris.pack.datapack.export.TemplateVolume;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.volmlib.util.math.Vector3i;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.ToIntFunction;

public record ObjectSource(TemplateVolume volume, int baseY, TreeRoots roots) {
    public ObjectSource {
        Objects.requireNonNull(volume, "Object volume");
        Objects.requireNonNull(roots, "Object roots");
        if (baseY > 0) {
            throw new IllegalArgumentException("Object base offset must not be positive, got " + baseY);
        }
    }

    public static ObjectSource of(IrisObject object, String resource, List<ExportDiagnostic> diagnostics) {
        List<TemplateBlock> blocks = StructureTemplateEncoder.blocks(object, resource, diagnostics);
        Vector3i center = object.getCenter();
        Axis x = Axis.of(blocks, TemplateBlock::x, object.getW(), center.getX());
        Axis z = Axis.of(blocks, TemplateBlock::z, object.getD(), center.getZ());
        int lowestY = 0;
        int highestY = object.getH() - 1;
        for (TemplateBlock block : blocks) {
            lowestY = Math.min(lowestY, block.y());
            highestY = Math.max(highestY, block.y());
        }
        List<TemplateBlock> placed = new ArrayList<>(blocks.size());
        Map<Long, TemplateBlock> byPosition = new HashMap<>(blocks.size() * 2);
        for (TemplateBlock block : blocks) {
            TemplateBlock shifted = new TemplateBlock(x.shift(block.x()), block.y() - lowestY, z.shift(block.z()),
                    block.state().waterlogged(false), block.nbt());
            placed.add(shifted);
            byPosition.put(position(shifted.x(), shifted.y(), shifted.z()), shifted);
        }
        TreeRoots roots = roots(object.getTreeGrounding(), center, x, z, lowestY, byPosition);
        return new ObjectSource(new TemplateVolume(x.size(), highestY - lowestY + 1, z.size(), placed), lowestY, roots);
    }

    private static TreeRoots roots(TreeGrounding grounding, Vector3i center, Axis x, Axis z, int lowestY, Map<Long, TemplateBlock> blocks) {
        if (grounding == null) {
            return TreeRoots.NONE;
        }
        List<TemplateBlock> cells = new ArrayList<>(grounding.roots().size());
        for (TreeGrounding.Root root : grounding.roots()) {
            TemplateBlock block = blocks.get(position(x.shift(root.x() + center.getX()), root.y() + center.getY() - lowestY,
                    z.shift(root.z() + center.getZ())));
            if (block != null) {
                cells.add(block);
            }
        }
        return new TreeRoots(cells, grounding.maxExtension());
    }

    private static long position(int x, int y, int z) {
        return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (z & 0x1FFFFF);
    }

    private record Axis(int size, int center, int nominalCenter) {
        static Axis of(List<TemplateBlock> blocks, ToIntFunction<TemplateBlock> coordinate, int size, int center) {
            int low = 0;
            int high = size - 1;
            for (TemplateBlock block : blocks) {
                low = Math.min(low, coordinate.applyAsInt(block));
                high = Math.max(high, coordinate.applyAsInt(block));
            }
            if (low == 0 && high == size - 1) {
                return new Axis(size, center, center);
            }
            int reach = Math.max(center - low, high - center);
            return new Axis(reach * 2 + 1, reach, center);
        }

        int shift(int value) {
            return value - nominalCenter + center;
        }
    }
}
