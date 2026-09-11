package art.arcane.iris.engine.hydrology;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HydrologyTerrainSamplerTest {
    @Test
    public void receivingWaterRequiresKnownOceanBelowSeaLevel() {
        HydrologyTerrainSampler sampler = (x, z) -> switch (x) {
            case 0 -> HydrologyTerrainSample.ocean(59, "ocean");
            case 1 -> HydrologyTerrainSample.ocean(60, "ocean");
            case 2 -> HydrologyTerrainSample.ocean(70, "ocean");
            case 3 -> HydrologyTerrainSample.openLand(59, 0D, "land");
            default -> null;
        };

        assertTrue(sampler.receivingWater(0, 0, 60));
        for (int x = 1; x <= 4; x++) {
            assertFalse(sampler.receivingWater(x, 0, 60));
        }
    }
}
