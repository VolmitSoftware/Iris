package art.arcane.iris.pack.datapack.export.compile.object;

import art.arcane.iris.pack.datapack.export.TemplateBlock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record TreeRoots(List<TemplateBlock> cells, int reach) {
    private static final int MAX_REACH = 16;
    private static final Comparator<TemplateBlock> COLUMN_ORDER = Comparator
            .comparingInt(TemplateBlock::x)
            .thenComparingInt(TemplateBlock::z);
    public static final TreeRoots NONE = new TreeRoots(List.of(), 0);

    public TreeRoots {
        cells = lowestPerColumn(cells);
        reach = Math.max(0, Math.min(MAX_REACH, reach));
    }

    public boolean present() {
        return !cells.isEmpty();
    }

    public TreeRoots lift(int dy) {
        if (dy == 0 || cells.isEmpty()) {
            return this;
        }
        List<TemplateBlock> lifted = new ArrayList<>(cells.size());
        for (TemplateBlock cell : cells) {
            lifted.add(new TemplateBlock(cell.x(), cell.y() + dy, cell.z(), cell.state(), null));
        }
        return new TreeRoots(lifted, reach);
    }

    private static List<TemplateBlock> lowestPerColumn(List<TemplateBlock> cells) {
        Map<Long, TemplateBlock> columns = new HashMap<>();
        for (TemplateBlock cell : cells) {
            long key = ((long) cell.x() << 32) | (cell.z() & 0xFFFFFFFFL);
            TemplateBlock previous = columns.get(key);
            if (previous == null || cell.y() < previous.y()) {
                columns.put(key, new TemplateBlock(cell.x(), cell.y(), cell.z(), cell.state(), null));
            }
        }
        List<TemplateBlock> lowest = new ArrayList<>(columns.values());
        lowest.sort(COLUMN_ORDER);
        return List.copyOf(lowest);
    }
}
