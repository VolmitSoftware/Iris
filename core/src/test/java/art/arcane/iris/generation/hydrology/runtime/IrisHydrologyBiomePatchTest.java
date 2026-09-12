package art.arcane.iris.generation.hydrology.runtime;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class IrisHydrologyBiomePatchTest {
    @Test
    public void twoKeysFormCoherentDeterministicPatchesWithoutCheckerCells() {
        int minimum = -64;
        int maximum = 64;
        int adjacentPairs = 0;
        int adjacentMatches = 0;
        int checkerCells = 0;
        Set<Integer> selected = new HashSet<>();

        for (int z = minimum; z <= maximum; z++) {
            for (int x = minimum; x <= maximum; x++) {
                int value = IrisHydrologyRuntime.coherentKeyIndex(1337L, x, z, 3, 2);
                assertEquals(value, IrisHydrologyRuntime.coherentKeyIndex(1337L, x, z, 3, 2));
                selected.add(value);
                if (x < maximum) {
                    adjacentPairs++;
                    adjacentMatches += value == IrisHydrologyRuntime.coherentKeyIndex(
                            1337L,
                            x + 1,
                            z,
                            3,
                            2
                    ) ? 1 : 0;
                }
                if (z < maximum) {
                    adjacentPairs++;
                    adjacentMatches += value == IrisHydrologyRuntime.coherentKeyIndex(
                            1337L,
                            x,
                            z + 1,
                            3,
                            2
                    ) ? 1 : 0;
                }
                if (x == maximum || z == maximum) {
                    continue;
                }
                int east = IrisHydrologyRuntime.coherentKeyIndex(1337L, x + 1, z, 3, 2);
                int south = IrisHydrologyRuntime.coherentKeyIndex(1337L, x, z + 1, 3, 2);
                int southEast = IrisHydrologyRuntime.coherentKeyIndex(1337L, x + 1, z + 1, 3, 2);
                if (value == southEast && east == south && value != east) {
                    checkerCells++;
                }
            }
        }

        assertEquals(Set.of(0, 1), selected);
        assertTrue(adjacentMatches > adjacentPairs * 0.9D);
        assertEquals(0, checkerCells);
    }

    @Test
    public void oneKeyAlwaysSelectsTheOnlyEntry() {
        assertEquals(0, IrisHydrologyRuntime.coherentKeyIndex(9L, Integer.MIN_VALUE, Integer.MAX_VALUE, 5, 1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyKeySetIsRejected() {
        IrisHydrologyRuntime.coherentKeyIndex(9L, 0, 0, 5, 0);
    }
}
