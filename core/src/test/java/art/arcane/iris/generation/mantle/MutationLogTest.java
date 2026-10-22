package art.arcane.iris.generation.mantle;

import art.arcane.iris.testsupport.KeyedBlockState;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class MutationLogTest {
    @Test
    public void logKeepsPositionsValuesAndFlagsInOrder() {
        NativeBlockState state = new KeyedBlockState("minecraft:stone");
        MutationLog log = MutationLog.of(List.of(
                new ObjectDestinationTransaction.SetMutation(new ObjectDestinationTransaction.DataKey(3, 70, -2, String.class), "marker", true),
                new ObjectDestinationTransaction.CustomBlockMutation(new ObjectDestinationTransaction.DataKey(-17, 4, 40, NativeBlockState.class), state, false)));
        for (int index = 0; index < 100; index++) {
            log.add(index, index + 1, -index, "grow" + index, (index & 1) == 0, (index & 2) == 0);
        }

        assertEquals(102, log.size());
        assertEquals(3, log.x(0));
        assertEquals(70, log.y(0));
        assertEquals(-2, log.z(0));
        assertEquals("marker", log.value(0));
        assertFalse(log.custom(0));
        assertTrue(log.canopy(0));
        assertSame(String.class, log.type(0));
        assertSame(state, log.value(1));
        assertTrue(log.custom(1));
        assertFalse(log.canopy(1));
        assertSame(NativeBlockState.class, log.type(1));
        assertEquals(99, log.x(101));
        assertEquals("grow99", log.value(101));
        assertFalse(log.custom(101));
        assertTrue(log.canopy(98));
        assertFalse(log.canopy(100));
    }

    @Test
    public void mismatchedMutationTypesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> MutationLog.of(List.of(
                new ObjectDestinationTransaction.SetMutation(new ObjectDestinationTransaction.DataKey(0, 4, 0, Integer.class), "text", false))));
    }

    @Test
    public void planSlicesMatchThePlanBuiltFromTheSameMutations() {
        MutationLog log = new MutationLog();
        List<ObjectDestinationTransaction.Mutation> tail = new ArrayList<>();
        for (int index = 0; index < 64; index++) {
            int x = (index * 7) - 30;
            int y = 4 + index;
            int z = (index * 5) - 20;
            String value = "value" + (index % 9);
            boolean canopy = index % 4 == 0;
            log.add(x, y, z, value, false, canopy);
            if (index >= 10) {
                tail.add(new ObjectDestinationTransaction.SetMutation(new ObjectDestinationTransaction.DataKey(x, y, z, String.class), value, canopy));
            }
        }
        ObjectSourcePlan sliced = new ObjectSourcePlan(log, 10, List.of());
        ObjectSourcePlan expected = new ObjectSourcePlan(tail);

        assertEquals(expected.destinationCount(), sliced.destinationCount());
        int total = 0;
        for (int chunkX = -3; chunkX <= 30; chunkX++) {
            for (int chunkZ = -3; chunkZ <= 30; chunkZ++) {
                List<ObjectDestinationTransaction.Mutation> mutations = sliced.mutationsFor(chunkX, chunkZ);
                assertEquals(expected.mutationsFor(chunkX, chunkZ), mutations);
                total += mutations.size();
            }
        }
        assertEquals(54, total);
    }
}
