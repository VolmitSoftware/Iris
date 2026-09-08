package art.arcane.iris.modded;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ModdedIrisLogLevelCoverageTest {
    @Test
    public void slf4jStyleArgumentsAndTrailingThrowableArePreserved() {
        RuntimeException failure = new RuntimeException("broken");

        ModdedIrisLog.RenderedLog rendered = ModdedIrisLog.render("chunk {},{} failed", 4, 9, failure);

        assertEquals("chunk 4,9 failed", rendered.message());
        assertEquals(failure, rendered.error());
    }
}
