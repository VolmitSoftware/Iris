package art.arcane.iris.pack.datapack.export.compile.object;

import art.arcane.iris.pack.datapack.export.StructureTemplateEncoder;
import art.arcane.iris.pack.datapack.export.TemplateBlock;
import art.arcane.iris.pack.datapack.export.TemplateVolume;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.volmlib.util.math.Vector3i;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.ToIntFunction;

public record ObjectSource(TemplateVolume volume, int baseY) {
    public ObjectSource {
        Objects.requireNonNull(volume, "Object volume");
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
        for (TemplateBlock block : blocks) {
            placed.add(new TemplateBlock(x.shift(block.x()), block.y() - lowestY, z.shift(block.z()),
                    block.state().waterlogged(false), block.nbt()));
        }
        return new ObjectSource(new TemplateVolume(x.size(), highestY - lowestY + 1, z.size(), placed), lowestY);
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
