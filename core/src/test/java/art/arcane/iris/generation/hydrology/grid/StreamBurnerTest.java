package art.arcane.iris.generation.hydrology.grid;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class StreamBurnerTest {
    @Test
    public void burnLowersEachListedCellOnce() {
        DemGrid grid = GridFixtures.uniform(4, 4, 10f);
        StreamBurner.burn(grid, List.of(new int[]{0, 1, 2}, new int[]{2, 3, 7}), 2f);
        float[] values = grid.values();
        for (int cell = 0; cell < values.length; cell++) {
            boolean burned = cell == 0 || cell == 1 || cell == 2 || cell == 3 || cell == 7;
            assertEquals(burned ? 8f : 10f, values[cell], 0f);
        }
    }
}
