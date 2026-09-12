package art.arcane.iris.world.pregen;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PregenRateTrackerTest {
    @Test
    public void shortRunIsNotDilutedByInitialZeroSample() {
        PregenRateTracker tracker = new PregenRateTracker(0L, 0L);

        tracker.sample(0L, 0L);
        for (int second = 1; second <= 5; second++) {
            PregenRates rates = tracker.sample(second * 200L, second * 1_000L);
            assertRates(200D, rates);
        }
    }

    @Test
    public void rollingWindowsUseTheirActualElapsedTime() {
        PregenRateTracker tracker = new PregenRateTracker(0L, 0L);
        long completed = 0L;
        PregenRates rates = PregenRates.ZERO;

        for (int second = 1; second <= 70; second++) {
            completed += second <= 10 ? 500L : second <= 40 ? 200L : 100L;
            rates = tracker.sample(completed, second * 1_000L);
        }

        assertEquals(200D, rates.overall(), 0.001D);
        assertEquals(100D, rates.tenSecond(), 0.001D);
        assertEquals(100D, rates.thirtySecond(), 0.001D);
        assertEquals(150D, rates.sixtySecond(), 0.001D);
    }

    @Test
    public void delayedSamplesDivideByWallClockTime() {
        PregenRateTracker tracker = new PregenRateTracker(5_000L, 100L);

        PregenRates rates = tracker.sample(700L, 9_000L);

        assertRates(150D, rates);
    }

    @Test
    public void ringWrapRetainsSixtySecondWindow() {
        PregenRateTracker tracker = new PregenRateTracker(0L, 0L);
        PregenRates rates = PregenRates.ZERO;

        for (int second = 1; second <= 200; second++) {
            rates = tracker.sample(second * 75L, second * 1_000L);
        }

        assertRates(75D, rates);
    }

    @Test
    public void terminalSampleIncludesProgressAfterLastTickerTick() {
        PregenRateTracker tracker = new PregenRateTracker(0L, 0L);
        tracker.sample(800L, 4_000L);

        PregenRates rates = tracker.sample(1_000L, 5_000L);

        assertRates(200D, rates);
    }

    private static void assertRates(double expected, PregenRates rates) {
        assertEquals(expected, rates.overall(), 0.001D);
        assertEquals(expected, rates.tenSecond(), 0.001D);
        assertEquals(expected, rates.thirtySecond(), 0.001D);
        assertEquals(expected, rates.sixtySecond(), 0.001D);
    }
}
