package art.arcane.iris.probe;

import art.arcane.iris.generation.hydrology.HydrologyTileCache;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class HydrologyPlanDigestProbeTest {
    @Test
    public void pregenerationAreaCoversTheRegionsOfTheRadius() {
        assertArea(HydrologyPlanDigestProbe.pregenerationArea(1000), -1024L, 1023L);
        assertArea(HydrologyPlanDigestProbe.pregenerationArea(1500), -1536L, 1535L);
        assertArea(HydrologyPlanDigestProbe.pregenerationArea(2000), -2048L, 2047L);
    }

    private static void assertArea(HydrologyTileCache.PregenerationArea area, long minimum, long maximum) {
        assertEquals(minimum, area.minimumBlockX());
        assertEquals(minimum, area.minimumBlockZ());
        assertEquals(maximum, area.maximumBlockX());
        assertEquals(maximum, area.maximumBlockZ());
    }
}
