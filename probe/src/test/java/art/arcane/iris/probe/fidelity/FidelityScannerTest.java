package art.arcane.iris.probe.fidelity;

import art.arcane.iris.configuration.IrisSettings;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

public class FidelityScannerTest {
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
    public void scanMergesEveryChunkOfTheSpiral() {
        FidelityStats stats = FidelityScanner.scan(new BandedSource(null), 10, -4, 1, 4, 3);

        assertEquals(144L, stats.columns());
        assertEquals(Map.of("plains", 128L, "forest", 16L), stats.biomeColumns());
        assertEquals(144L, stats.heightColumns());
    }

    @Test
    public void failedChunkFailsTheScanWithItsCause() {
        IllegalStateException broken = new IllegalStateException("region missing");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> FidelityScanner.scan(new BandedSource(broken), 0, 0, 1, 4, 2));

        assertSame(broken, failure.getCause());
    }

    private record BandedSource(RuntimeException failure) implements FidelitySource {
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
        public FidelityChunk sample(int chunkX, int chunkZ) {
            if (failure != null && chunkX == 1 && chunkZ == 1) {
                throw failure;
            }
            String biome = chunkX == 10 && chunkZ == -4 ? "forest" : "plains";
            return new SyntheticFidelityChunk(MIN_Y).biomes(biome, biome, 16);
        }

        @Override
        public void close() {
        }
    }
}
