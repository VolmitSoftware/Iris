package art.arcane.iris.core.pregenerator.methods;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AsyncPregenMethodConcurrencyCapTest {
    @Test
    public void paperLikeRecommendedCapTracksWorkerThreads() {
        assertEquals(8, AsyncPregenMethod.computePaperLikeRecommendedCap(1));
        assertEquals(8, AsyncPregenMethod.computePaperLikeRecommendedCap(4));
        assertEquals(24, AsyncPregenMethod.computePaperLikeRecommendedCap(12));
        assertEquals(96, AsyncPregenMethod.computePaperLikeRecommendedCap(80));
    }

    @Test
    public void foliaRecommendedCapTracksWorkerThreads() {
        assertEquals(64, AsyncPregenMethod.computeFoliaRecommendedCap(1));
        assertEquals(64, AsyncPregenMethod.computeFoliaRecommendedCap(12));
        assertEquals(80, AsyncPregenMethod.computeFoliaRecommendedCap(20));
        assertEquals(192, AsyncPregenMethod.computeFoliaRecommendedCap(80));
    }

    @Test
    public void runtimeCapUsesGlobalCeilingAndWorkerRecommendation() {
        assertEquals(80, AsyncPregenMethod.applyRuntimeConcurrencyCap(256, true, 20));
        assertEquals(12, AsyncPregenMethod.applyRuntimeConcurrencyCap(12, true, 20));
        assertEquals(64, AsyncPregenMethod.applyRuntimeConcurrencyCap(256, true, 8));
        assertEquals(16, AsyncPregenMethod.applyRuntimeConcurrencyCap(256, false, 8));
        assertEquals(20, AsyncPregenMethod.applyRuntimeConcurrencyCap(20, false, 40));
    }
}
