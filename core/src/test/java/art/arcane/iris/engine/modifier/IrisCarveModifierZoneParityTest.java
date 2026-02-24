package art.arcane.iris.engine.modifier;

import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class IrisCarveModifierZoneParityTest {
    private static Constructor<?> columnMaskConstructor;
    private static Method addMethod;
    private static Method nextSetBitMethod;
    private static Method clearMethod;

    @BeforeClass
    public static void setup() throws Exception {
        Class<?> columnMaskClass = Class.forName("art.arcane.iris.engine.modifier.IrisCarveModifier$ColumnMask");
        columnMaskConstructor = columnMaskClass.getDeclaredConstructor();
        addMethod = columnMaskClass.getDeclaredMethod("add", int.class);
        nextSetBitMethod = columnMaskClass.getDeclaredMethod("nextSetBit", int.class);
        clearMethod = columnMaskClass.getDeclaredMethod("clear");
        columnMaskConstructor.setAccessible(true);
        addMethod.setAccessible(true);
        nextSetBitMethod.setAccessible(true);
        clearMethod.setAccessible(true);
    }

    @Test
    public void randomColumnZonesMatchLegacySortedResolver() throws Exception {
        Object columnMask = columnMaskConstructor.newInstance();
        Random random = new Random(913_447L);
        int maxHeight = 320;

        for (int scenario = 0; scenario < 400; scenario++) {
            clearMethod.invoke(columnMask);

            int sampleSize = 1 + random.nextInt(180);
            Set<Integer> uniqueHeights = new HashSet<>();
            while (uniqueHeights.size() < sampleSize) {
                uniqueHeights.add(random.nextInt(480) - 80);
            }

            int[] heights = toIntArray(uniqueHeights);
            for (int index = 0; index < heights.length; index++) {
                addMethod.invoke(columnMask, heights[index]);
            }

            List<String> expectedZones = legacyZones(heights, maxHeight);
            List<String> actualZones = bitsetZones(columnMask, maxHeight);
            assertEquals("scenario=" + scenario, expectedZones, actualZones);
        }
    }

    @Test
    public void edgeColumnsMatchLegacySortedResolver() throws Exception {
        Object columnMask = columnMaskConstructor.newInstance();
        int maxHeight = 320;
        int[][] scenarios = new int[][]{
                {-10, -1, 0, 1, 2, 5, 6, 9, 10, 11, 12, 200, 201, 205},
                {300, 301, 302, 304, 305, 307, 308, 309, 310, 400, 401},
                {0, 2, 4, 6, 8, 10, 12},
                {10, 11, 12, 13, 14, 15, 16, 17}
        };

        for (int scenario = 0; scenario < scenarios.length; scenario++) {
            clearMethod.invoke(columnMask);
            int[] heights = Arrays.copyOf(scenarios[scenario], scenarios[scenario].length);
            for (int index = 0; index < heights.length; index++) {
                addMethod.invoke(columnMask, heights[index]);
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

    private List<String> bitsetZones(Object columnMask, int maxHeight) throws Exception {
        List<String> zones = new ArrayList<>();
        int firstHeight = nextSetBit(columnMask, 0);
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

            y = nextSetBit(columnMask, y + 1);
        }

        if (isValidZone(floor, ceiling, maxHeight)) {
            zones.add(zoneKey(floor, ceiling));
        }

        return zones;
    }

    private int nextSetBit(Object columnMask, int fromBit) throws Exception {
        return (Integer) nextSetBitMethod.invoke(columnMask, fromBit);
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
