package art.arcane.iris.studio.jigsaw;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class JigsawStudioMainMenuRowTest {
    @Test
    public void planarRowKeepsAllSixWorkcellsLeftOfTheRowControls() {
        JigsawStudioMainMenu.WorkcellRow row = JigsawStudioMainMenu.workcellRow(6, 4);

        assertEquals(List.of(0, 1, 2, 3, 4, 5), row.indices());
        assertEquals(List.of(-3, -2, -1, 0, 1, 2), row.slots());
        assertEquals(-1, row.previousTarget());
        assertEquals(-1, row.nextTarget());
    }

    @Test
    public void largeSpatialRowsPageWithoutHidingOrOverlappingWorkcells() {
        for (int count : new int[]{7, 24}) {
            Set<Integer> reachable = new HashSet<>();
            for (int viewed = 0; viewed < count; viewed++) {
                JigsawStudioMainMenu.WorkcellRow row = JigsawStudioMainMenu.workcellRow(count, viewed);
                assertTrue(row.indices().contains(viewed));
                assertEquals(row.indices().size(), new HashSet<>(row.slots()).size());
                for (int slot : row.slots()) {
                    assertTrue(slot >= -3 && slot <= 1);
                }
                reachable.addAll(row.indices());
                if (row.nextTarget() >= 0) {
                    assertTrue(JigsawStudioMainMenu.workcellRow(count, row.nextTarget()).indices()
                            .contains(row.nextTarget()));
                }
            }
            assertEquals(count, reachable.size());
        }
        JigsawStudioMainMenu.WorkcellRow last = JigsawStudioMainMenu.workcellRow(24, 23);
        assertEquals(List.of(20, 21, 22, 23), last.indices());
        assertEquals(15, last.previousTarget());
        assertEquals(-1, last.nextTarget());
    }
}
