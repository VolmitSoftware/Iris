package art.arcane.iris.generation.stage;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class IrisCarveModifierZoneParityTest {
    @Test
    public void randomColumnZonesMatchLegacySortedResolver() {
        CarveColumnMask columnMask = new CarveColumnMask();
        Random random = new Random(913_447L);
        int maxHeight = 320;

        for (int scenario = 0; scenario < 400; scenario++) {
            columnMask.clear();

            int sampleSize = 1 + random.nextInt(180);
            Set<Integer> uniqueHeights = new HashSet<>();
            while (uniqueHeights.size() < sampleSize) {
                uniqueHeights.add(random.nextInt(480) - 80);
            }

            int[] heights = toIntArray(uniqueHeights);
            for (int index = 0; index < heights.length; index++) {
                columnMask.add(heights[index]);
            }

            List<String> expectedZones = legacyZones(heights, maxHeight);
            List<String> actualZones = bitsetZones(columnMask, maxHeight);
            assertEquals("scenario=" + scenario, expectedZones, actualZones);
        }
    }

    @Test
    public void edgeColumnsMatchLegacySortedResolver() {
        CarveColumnMask columnMask = new CarveColumnMask();
        int maxHeight = 320;
        int[][] scenarios = new int[][]{
                {-10, -1, 0, 1, 2, 5, 6, 9, 10, 11, 12, 200, 201, 205},
                {300, 301, 302, 304, 305, 307, 308, 309, 310, 400, 401},
                {0, 2, 4, 6, 8, 10, 12},
                {10, 11, 12, 13, 14, 15, 16, 17}
        };

        for (int scenario = 0; scenario < scenarios.length; scenario++) {
            columnMask.clear();
            int[] heights = Arrays.copyOf(scenarios[scenario], scenarios[scenario].length);
            for (int index = 0; index < heights.length; index++) {
                columnMask.add(heights[index]);
            }

            List<String> expectedZones = legacyZones(heights, maxHeight);
            List<String> actualZones = bitsetZones(columnMask, maxHeight);
            assertEquals("edge-scenario=" + scenario, expectedZones, actualZones);
        }
    }

    private int[] toIntArray(Set<Integer> values) {
        int[] array = new int[values.size()];
        int index = 0;
        for (Integer value : values) {
            array[index++] = value;
        }
        return array;
    }

    private List<String> legacyZones(int[] heights, int maxHeight) {
        List<String> zones = new ArrayList<>();
        if (heights.length == 0) {
            return zones;
        }

        int[] sorted = Arrays.copyOf(heights, heights.length);
        Arrays.sort(sorted);
        int floor = sorted[0];
        int ceiling = -1;
        int buf = sorted[0] - 1;
        for (int index = 0; index < sorted.length; index++) {
            int y = sorted[index];
            if (y < 0 || y > maxHeight) {
                continue;
            }

            if (y == buf + 1) {
                buf = y;
                ceiling = buf;
            } else if (isValidZone(floor, ceiling, maxHeight)) {
                zones.add(zoneKey(floor, ceiling));
                floor = y;
                ceiling = -1;
                buf = y;
            } else {
                floor = y;
                ceiling = -1;
                buf = y;
            }
        }

        if (isValidZone(floor, ceiling, maxHeight)) {
            zones.add(zoneKey(floor, ceiling));
        }

        return zones;
    }

    private List<String> bitsetZones(CarveColumnMask columnMask, int maxHeight) {
        List<String> zones = new ArrayList<>();
        int firstHeight = columnMask.nextSetBit(0);
        if (firstHeight < 0) {
            return zones;
        }

        int floor = firstHeight;
        int ceiling = -1;
        int buf = firstHeight - 1;
        int y = firstHeight;
        while (y >= 0) {
            if (y >= 0 && y <= maxHeight) {
                if (y == buf + 1) {
                    buf = y;
                    ceiling = buf;
                } else if (isValidZone(floor, ceiling, maxHeight)) {
                    zones.add(zoneKey(floor, ceiling));
                    floor = y;
                    ceiling = -1;
                    buf = y;
                } else {
                    floor = y;
                    ceiling = -1;
                    buf = y;
                }
            }

            y = columnMask.nextSetBit(y + 1);
        }

        if (isValidZone(floor, ceiling, maxHeight)) {
            zones.add(zoneKey(floor, ceiling));
        }

        return zones;
    }

    private boolean isValidZone(int floor, int ceiling, int maxHeight) {
        return floor < ceiling
                && floor >= 0
                && ceiling <= maxHeight
                && ((ceiling - floor) - 1) > 0;
    }

    private String zoneKey(int floor, int ceiling) {
        return floor + ":" + ceiling;
    }
}
