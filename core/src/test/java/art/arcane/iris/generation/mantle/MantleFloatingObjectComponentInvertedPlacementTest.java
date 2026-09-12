package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.biome.FloatingIslandSample;
import art.arcane.iris.structure.object.FloatingObjectFootprint;
import art.arcane.iris.generation.biome.IrisFloatingChildBiomes;
import art.arcane.iris.structure.object.IrisObjectRotation;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MantleFloatingObjectComponentInvertedPlacementTest {

    private FloatingObjectFootprint footprint(int lowestSolidKeyY, int highestSolidKeyY, int tallestKx, int tallestKz) throws Exception {
        return footprint(lowestSolidKeyY, highestSolidKeyY, tallestKx, tallestKz, new long[0]);
    }

    private FloatingObjectFootprint footprint(int lowestSolidKeyY, int highestSolidKeyY,
                                              int tallestKx, int tallestKz, long[] cells) throws Exception {
        Constructor<FloatingObjectFootprint> constructor = FloatingObjectFootprint.class.getDeclaredConstructor(
                int.class,
                int.class,
                int.class,
                int.class,
                int.class,
                int.class,
                int.class,
                int.class,
                int.class,
                long[].class
        );
        constructor.setAccessible(true);
        return constructor.newInstance(
                lowestSolidKeyY,
                highestSolidKeyY,
                0,
                0,
                0,
                tallestKx,
                tallestKz,
                99,
                99,
                cells
        );
    }

    private FloatingIslandSample sample(IrisFloatingChildBiomes entry) throws Exception {
        Constructor<FloatingIslandSample> constructor = FloatingIslandSample.class.getDeclaredConstructor(
                IrisFloatingChildBiomes.class,
                int.class,
                int.class,
                int.class,
                int.class,
                boolean[].class
        );
        constructor.setAccessible(true);
        boolean[] solidMask = new boolean[10];
        Arrays.fill(solidMask, true);
        return constructor.newInstance(entry, 100, 10, 9, 10, solidMask);
    }

    @Test
    public void invertedBaseY_anchorsOriginalLowestSolidBelowBottomFace() throws Exception {
        FloatingObjectFootprint footprint = footprint(5, 30, 2, 3);

        assertEquals(104, MantleFloatingObjectComponent.invertedBaseY(100, footprint));
    }

    @Test
    public void invertedBaseX_usesTopFootprintAnchor() throws Exception {
        FloatingObjectFootprint footprint = footprint(5, 30, 2, 3);

        assertEquals(106, MantleFloatingObjectComponent.invertedBaseX(100, 8, footprint));
    }

    @Test
    public void invertedBaseZ_mirrorsTopFootprintAnchor() throws Exception {
        FloatingObjectFootprint footprint = footprint(5, 30, 2, 3);

        assertEquals(111, MantleFloatingObjectComponent.invertedBaseZ(100, 8, footprint));
    }

    @Test
    public void invertedBaseX_usesFixedYRotationAnchor() throws Exception {
        FloatingObjectFootprint footprint = footprint(5, 30, 2, 3);
        IrisObjectRotation rotation = IrisObjectRotation.xFlip180WithY(90);

        assertEquals(111, MantleFloatingObjectComponent.invertedBaseX(100, 8, footprint, rotation));
    }

    @Test
    public void invertedBaseZ_usesFixedYRotationAnchor() throws Exception {
        FloatingObjectFootprint footprint = footprint(5, 30, 2, 3);
        IrisObjectRotation rotation = IrisObjectRotation.xFlip180WithY(90);

        assertEquals(110, MantleFloatingObjectComponent.invertedBaseZ(100, 8, footprint, rotation));
    }

    @Test
    public void invertedBaseY_isStableAcrossFixedYRotation() throws Exception {
        FloatingObjectFootprint footprint = footprint(5, 30, 2, 3);
        IrisObjectRotation rotation = IrisObjectRotation.xFlip180WithY(270);

        assertEquals(104, MantleFloatingObjectComponent.invertedBaseY(100, footprint, rotation));
    }

    @Test
    public void topFootprintChecksNeighborChunkTerrain() throws Exception {
        IrisFloatingChildBiomes entry = new IrisFloatingChildBiomes();
        FloatingIslandSample sample = sample(entry);
        long[] cells = {0L, 1L << 32};
        FloatingObjectFootprint footprint = footprint(0, 0, 0, 0, cells);

        assertTrue(MantleFloatingObjectComponent.isFootprintFlat(
                footprint, 15, 0, 109,
                (x, z) -> z == 0 && (x == 15 || x == 16) ? sample : null,
                entry, 2));
        assertFalse(MantleFloatingObjectComponent.isFootprintFlat(
                footprint, 15, 0, 109,
                (x, z) -> x == 15 && z == 0 ? sample : null,
                entry, 2));
    }
}
