package art.arcane.iris.probe.fidelity;

import art.arcane.iris.configuration.IrisSettings;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ColumnSurveyTest {
    private static final int MIN_Y = -64;
    private static final int MAX_Y = 320;

    private IrisSettings previousSettings;

    @Before
    public void setUp() {
        previousSettings = IrisSettings.settings;
        IrisSettings.settings = new IrisSettings();
    }

    @After
    public void tearDown() {
        IrisSettings.settings = previousSettings;
    }

    @Test
    public void surveyVisitsEveryStrideCellCentreOnce() {
        SplitSource source = new SplitSource(null);

        FidelityStats stats = ColumnSurvey.scan(source, new SurveyWindow(1000, -2000, 512, 128), 3);

        assertEquals(16L, stats.columns());
        assertEquals(16, source.visited().size());
        assertTrue(source.visited().toString(), source.visited().contains(1000 - 256 + 64 + "," + (-2000 - 256 + 64)));
        assertTrue(source.visited().toString(), source.visited().contains(1000 + 128 + 64 + "," + (-2000 + 128 + 64)));
        assertEquals(Map.of("land", 8L, "sea", 8L), stats.biomeColumns());
        assertEquals(16L, stats.heightColumns());
        assertEquals(0.5D, stats.landFraction(), 0.0D);
        assertEquals(30.0D, stats.heightQuantile(0.25D), 0.0D);
        assertEquals(80.0D, stats.heightQuantile(0.75D), 0.0D);
    }

    @Test
    public void failedColumnFailsTheSurveyWithItsCause() {
        IllegalStateException broken = new IllegalStateException("sampler closed");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> ColumnSurvey.scan(new SplitSource(broken), new SurveyWindow(0, 0, 64, 16), 2));

        assertSame(broken, failure.getCause());
    }

    private record SplitSource(RuntimeException failure, Set<String> visited) implements ColumnSource {
        private SplitSource(RuntimeException failure) {
            this(failure, ConcurrentHashMap.newKeySet());
        }

        @Override
        public String label() {
            return "synthetic";
        }

        @Override
        public int minY() {
            return MIN_Y;
        }

        @Override
        public int maxY() {
            return MAX_Y;
        }

        @Override
        public FidelityColumn column(int x, int z) {
            if (failure != null) {
                throw failure;
            }
            visited.add(x + "," + z);
            return x < 1000 ? new FidelityColumn("sea", 30, true) : new FidelityColumn("land", 80, false);
        }

        @Override
        public void close() {
        }
    }
}
