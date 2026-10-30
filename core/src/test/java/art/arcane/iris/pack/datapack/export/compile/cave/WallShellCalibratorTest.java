package art.arcane.iris.pack.datapack.export.compile.cave;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.ir.Axis;
import art.arcane.iris.pack.datapack.export.ir.Field;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;

public class WallShellCalibratorTest {
    private static final int MIN_Y = -64;
    private static final int MAX_Y = 64;

    @Test
    public void bandHoldsTheOneBlockHorizontalWall() {
        double band = WallShellCalibrator.band(new CaveInterior(ridges(0.4), MIN_Y, MAX_Y), Map.of(), DataVersion.V26_3);

        assertEquals(0.1, band, 1.0e-6);
    }

    @Test
    public void bandScalesWithTheFieldGradient() {
        double band = WallShellCalibrator.band(new CaveInterior(ridges(1.2), MIN_Y, MAX_Y), Map.of(), DataVersion.V26_3);

        assertEquals(0.3, band, 1.0e-6);
    }

    @Test
    public void fieldsWithoutCavesHaveAnEmptyBand() {
        double band = WallShellCalibrator.band(new CaveInterior(new Field.Const(1.0), MIN_Y, MAX_Y), Map.of(), DataVersion.V26_3);

        assertEquals(0.0, band, 0.0);
    }

    @Test
    public void calibrationIsDeterministic() {
        CaveInterior interior = new CaveInterior(ridges(1.2), 0, 40);

        assertEquals(WallShellCalibrator.band(interior, Map.of(), DataVersion.V26_3), WallShellCalibrator.band(interior, Map.of(), DataVersion.V26_3), 0.0);
    }

    private static Field ridges(double amplitude) {
        return new Field.Coord(Axis.X, Field.Tiling.MIRRORED_REPEAT, 0.0, 8.0, -amplitude, amplitude);
    }
}
