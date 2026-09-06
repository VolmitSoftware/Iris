package art.arcane.iris.engine.hydrology;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class RiverFootprintTest {
    @Test
    public void primitiveIndexKeepsSignedKeyOrderAndFullCoordinateRange() {
        Random random = new Random(836412L);
        HashMap<Long, HydrologyColumnSample> input = new HashMap<>();
        for (int index = 0; index < 2_000; index++) {
            HydrologyColumnSample column = column(random.nextInt(), random.nextInt());
            input.put(RiverFootprint.pack(column.x(), column.z()), column);
        }
        for (int x : new int[]{Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE}) {
            for (int z : new int[]{Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE}) {
                input.put(RiverFootprint.pack(x, z), column(x, z));
            }
        }
        TreeMap<Long, HydrologyColumnSample> expected = new TreeMap<>(input);
        RiverFootprint footprint = new RiverFootprint(input);
        input.clear();
        assertEquals(new ArrayList<>(expected.keySet()), new ArrayList<>(footprint.columns().keySet()));
        assertEquals(expected, footprint.columns());
        for (HydrologyColumnSample column : expected.values()) {
            assertSame(column, footprint.sample(column.x(), column.z()).orElseThrow());
        }
    }

    @Test
    public void exposedEntriesCannotMutateTheLookupIndex() {
        HydrologyColumnSample original = column(-5, 9);
        RiverFootprint footprint = new RiverFootprint(Map.of(RiverFootprint.pack(-5, 9), original));
        Map.Entry<Long, HydrologyColumnSample> entry = footprint.columns().entrySet().iterator().next();
        assertThrows(UnsupportedOperationException.class, () -> entry.setValue(column(-5, 9)));
        assertThrows(UnsupportedOperationException.class, () -> footprint.columns().values().clear());
        assertSame(original, footprint.sample(-5, 9).orElseThrow());
    }

    @Test
    public void areaQueriesKeepBothLookupPathsAndExclusiveBounds() {
        HashMap<Long, HydrologyColumnSample> input = new HashMap<>();
        for (int x = -8; x < 8; x++) {
            for (int z = -8; z < 8; z++) {
                input.put(RiverFootprint.pack(x, z), column(x, z));
            }
        }
        RiverFootprint footprint = new RiverFootprint(input);
        assertEquals(List.of(column(-1, -1), column(0, -1), column(-1, 0), column(0, 0)),
                footprint.columnsIn(-1, -1, 1, 1));
        assertEquals(new ArrayList<>(new TreeMap<>(input).values()),
                footprint.columnsIn(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertTrue(footprint.columnsIn(0, 0, 0, 1).isEmpty());
        assertTrue(footprint.columnsIn(100, 100, 101, 101).isEmpty());
    }

    private static HydrologyColumnSample column(int x, int z) {
        return new HydrologyColumnSample(x, z, 90, 63, false, "parent", List.of());
    }
}
