package art.arcane.iris.engine.hydrology.surface;

import art.arcane.iris.engine.hydrology.HydrologyTerrainSample;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SurfaceCellAdmissionTest {
    @Test
    public void landAboveSeaLevelIsWritable() {
        assertTrue(SurfaceCellAdmission.writable(HydrologyTerrainSample.openLand(61, 0D, "land"), 60));
    }

    @Test
    public void oceanSubmergedAndMissingSamplesAreNotWritable() {
        assertFalse(SurfaceCellAdmission.writable(HydrologyTerrainSample.ocean(90, "ocean"), 60));
        assertFalse(SurfaceCellAdmission.writable(HydrologyTerrainSample.openLand(60, 0D, "land"), 60));
        assertFalse(SurfaceCellAdmission.writable(HydrologyTerrainSample.openLand(40, 0D, "land"), 60));
        assertFalse(SurfaceCellAdmission.writable(null, 60));
    }

    @Test
    public void mouthMayCutADrySillButCannotOwnOceanGround() {
        assertTrue(SurfaceCellAdmission.mouthLand(HydrologyTerrainSample.openLand(60, 0D, "land"), 60));
        assertFalse(SurfaceCellAdmission.mouthLand(HydrologyTerrainSample.ocean(50, "ocean"), 60));
    }
}
