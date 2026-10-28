package art.arcane.iris.studio.jigsaw;

import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class JigsawStudioLayoutColumnTest {
    @Test
    public void columnLookupFocusesTheWorkcellFromItsFloorToTheTeleportHeight() {
        JigsawStudioLayout layout = JigsawStudioLayout.create(
                JigsawStudioMode.PLANAR_JIGSAW,
                new JigsawStudioCellDimensions(7, 5, 7),
                JigsawStudioVariantCatalog.empty());
        JigsawStudioBay cross = layout.get("workcell/cross");
        JigsawStudioBounds bounds = cross.bounds();
        int x = bounds.originX() + 3;
        int z = bounds.originZ() + 3;

        assertSame(cross, layout.findColumn(x, bounds.originY() - 1, z));
        assertSame(cross, layout.findColumn(x, bounds.maxY() + 2, z));
        assertSame(cross, layout.findColumn(x, bounds.maxY() + JigsawStudioLayout.COLUMN_HEADROOM, z));
        assertNull(layout.findColumn(x, bounds.maxY() + JigsawStudioLayout.COLUMN_HEADROOM + 1, z));
        assertNull(layout.findColumn(bounds.originX() - 1, bounds.originY(), z));
        assertNull(layout.findAt(x, bounds.maxY() + 2, z));
    }
}
