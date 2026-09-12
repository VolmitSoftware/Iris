package art.arcane.iris.generation.stage;

import art.arcane.volmlib.util.matter.MatterCavern;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class IrisCarveScratchTest {
    @Test
    public void wallBufferResizesAndReplacesWithoutLosingEntries() {
        CarveWallBuffer buffer = new CarveWallBuffer(2);
        Map<String, MatterCavern> expected = new HashMap<>();
        for (int index = 0; index < 24; index++) {
            int x = index & 15;
            int y = 20 + index;
            int z = (index * 3) & 15;
            MatterCavern cavern = new MatterCavern(true, "cave-" + index, (byte) 0);
            buffer.put(x, y, z, cavern);
            expected.put(key(x, y, z), cavern);
        }

        MatterCavern replacement = new MatterCavern(true, "replacement", (byte) 0);
        buffer.put(5, 25, 15, replacement);
        expected.put(key(5, 25, 15), replacement);

        assertSame(replacement, buffer.get(5, 25, 15));
        assertNull(buffer.get(5, 26, 15));

        Map<String, MatterCavern> actual = new HashMap<>();
        buffer.forEach((x, y, z, cavern) -> actual.put(key(x, y, z), cavern));
        assertEquals(expected.keySet(), actual.keySet());
        for (Map.Entry<String, MatterCavern> entry : expected.entrySet()) {
            assertSame(entry.getValue(), actual.get(entry.getKey()));
        }
    }

    @Test
    public void resetClearsReusableState() {
        IrisCarveScratch scratch = new IrisCarveScratch();
        MatterCavern cavern = new MatterCavern(true, "cave", (byte) 0);
        scratch.columnMasks[0].add(12);
        scratch.boundaryMasks[0].add(13);
        scratch.walls.put(1, 12, 2, cavern);
        scratch.customBiomeCache.put("cave", null);
        scratch.customCaveBiomePresent = true;

        scratch.reset();

        assertTrue(scratch.columnMasks[0].isEmpty());
        assertTrue(scratch.boundaryMasks[0].isEmpty());
        assertTrue(scratch.customBiomeCache.isEmpty());
        assertFalse(scratch.customCaveBiomePresent);
        int[] wallCount = new int[1];
        scratch.walls.forEach((x, y, z, value) -> wallCount[0]++);
        assertEquals(0, wallCount[0]);
    }

    private static String key(int x, int y, int z) {
        return x + ":" + y + ":" + z;
    }
}
