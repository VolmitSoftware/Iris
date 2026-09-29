package art.arcane.iris.generation.runtime;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GenerationRateWindowTest {
    @Test
    public void generationTimingsGoStaleOnceChunksStopGenerating() {
        GenerationRateWindow window = new GenerationRateWindow();

        window.sampleChunksPerSecond(0, 1_000L);
        assertEquals(20D, window.sampleChunksPerSecond(20, 2_000L), 0D);
        assertTrue(window.generationTimingsFresh(2_000L));

        assertEquals(0D, window.sampleChunksPerSecond(20, 7_000L), 0D);
        assertTrue(window.generationTimingsFresh(7_000L));
        assertEquals(0D, window.sampleChunksPerSecond(20, 12_001L), 0D);
        assertFalse(window.generationTimingsFresh(12_001L));

        window.sampleChunksPerSecond(21, 13_001L);
        assertTrue(window.generationTimingsFresh(13_001L));
    }

    @Test
    public void anIdleWorldNeverPublishesTimings() {
        GenerationRateWindow window = new GenerationRateWindow();

        window.sampleChunksPerSecond(0, 1_000L);
        window.sampleChunksPerSecond(0, 2_000L);

        assertFalse(window.generationTimingsFresh(2_000L));
    }

    @Test
    public void chunksGeneratedBeforeTheFirstSampleCountAsRecentGeneration() {
        GenerationRateWindow window = new GenerationRateWindow();

        assertEquals(0D, window.sampleChunksPerSecond(400, 5_000L), 0D);

        assertTrue(window.generationTimingsFresh(5_000L));
        assertFalse(window.generationTimingsFresh(5_000L + GenerationRateWindow.TIMING_IDLE_MILLIS + 1L));
    }
}
