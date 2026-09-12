package art.arcane.iris.world.tree;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TreeFellerPresentationTest {
    @Test
    public void pulseSizeKeepsSmallTreesReadableAndLargeTreesBounded() {
        assertEquals(4, TreeFellerPresentation.blocksPerPulse(1));
        assertEquals(4, TreeFellerPresentation.blocksPerPulse(240));
        assertEquals(10, TreeFellerPresentation.blocksPerPulse(600));
        assertEquals(64, TreeFellerPresentation.blocksPerPulse(100_000));
    }

    @Test
    public void effectSamplingStaysBoundedPerPulse() {
        assertEquals(1, TreeFellerPresentation.effectStride(4));
        assertEquals(1, TreeFellerPresentation.effectStride(16));
        assertEquals(2, TreeFellerPresentation.effectStride(17));
        assertEquals(2, TreeFellerPresentation.effectStride(31));
        assertEquals(2, TreeFellerPresentation.effectStride(32));
        assertEquals(3, TreeFellerPresentation.effectStride(33));
        assertEquals(4, TreeFellerPresentation.effectStride(64));
    }

}
