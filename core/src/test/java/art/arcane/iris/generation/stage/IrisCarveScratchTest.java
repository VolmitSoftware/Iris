package art.arcane.iris.generation.stage;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.volmlib.util.matter.MatterCavern;
import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

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
        scratch.caveBiomeCache.put(42L, new IrisBiome());
        scratch.customCaveBiomePresent = true;

        scratch.reset();

        assertTrue(scratch.columnMasks[0].isEmpty());
        assertTrue(scratch.boundaryMasks[0].isEmpty());
        assertTrue(scratch.customBiomeCache.isEmpty());
        assertTrue(scratch.caveBiomeCache.isEmpty());
        assertFalse(scratch.customCaveBiomePresent);
        int[] wallCount = new int[1];
        scratch.walls.forEach((x, y, z, value) -> wallCount[0]++);
        assertEquals(0, wallCount[0]);
    }

    @Test
    public void maskWallsMatchTheNeighborScanOfCarvedCells() {
        Random random = new Random(52_117L);
        for (int round = 0; round < 40; round++) {
            CarveColumnMask[] columns = new CarveColumnMask[256];
            CarveColumnMask[] walls = new CarveColumnMask[256];
            for (int index = 0; index < 256; index++) {
                columns[index] = new CarveColumnMask();
                walls[index] = new CarveColumnMask();
                walls[index].add(random.nextInt(700));
            }
            int cells = random.nextInt(4000);
            int height = 64 + random.nextInt(700);
            for (int cell = 0; cell < cells; cell++) {
                columns[random.nextInt(256)].add(1 + random.nextInt(height - 1));
            }
            Set<String> expected = new HashSet<>();
            for (int index = 0; index < 256; index++) {
                int rx = index >> 4;
                int rz = index & 15;
                for (int y = columns[index].nextSetBit(0); y >= 0; y = columns[index].nextSetBit(y + 1)) {
                    if (rz < 15 && !columns[index + 1].contains(y)) {
                        expected.add(key(rx, y, rz + 1));
                    }
                    if (rx < 15 && !columns[index + 16].contains(y)) {
                        expected.add(key(rx + 1, y, rz));
                    }
                    if (rz > 0 && !columns[index - 1].contains(y)) {
                        expected.add(key(rx, y, rz - 1));
                    }
                    if (rx > 0 && !columns[index - 16].contains(y)) {
                        expected.add(key(rx - 1, y, rz));
                    }
                }
            }
            IrisCarveModifier.collectInternalWalls(walls, columns);
            Set<String> actual = new HashSet<>();
            for (int index = 0; index < 256; index++) {
                for (int y = walls[index].nextSetBit(0); y >= 0; y = walls[index].nextSetBit(y + 1)) {
                    actual.add(key(index >> 4, y, index & 15));
                    assertFalse(columns[index].contains(y));
                }
                assertEquals(walls[index].nextSetBit(0) < 0, walls[index].isEmpty());
            }
            assertEquals(expected, actual);
        }
    }

    private static String key(int x, int y, int z) {
        return x + ":" + y + ":" + z;
    }
}
