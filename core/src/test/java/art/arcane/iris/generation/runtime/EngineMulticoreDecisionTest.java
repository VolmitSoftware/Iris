package art.arcane.iris.generation.runtime;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EngineMulticoreDecisionTest {
    @Test
    public void liveWorldsGenerateInlineUnlessForced() {
        assertFalse(Engine.generateMulticore(false, false));
        assertTrue(Engine.generateMulticore(true, false));
    }

    @Test
    public void pregenerationSpreadsStagesAndMantleWorkAcrossCores() {
        assertTrue(Engine.generateMulticore(false, true));
        assertTrue(Engine.generateMulticore(true, true));
    }
}
