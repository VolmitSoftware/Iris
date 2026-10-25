package art.arcane.iris.generation.stage;

import art.arcane.iris.generation.hydrology.raster.WaterRole;
import art.arcane.volmlib.util.stream.ProceduralStream;
import art.arcane.volmlib.util.stream.interpolation.Interpolated;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class IrisFloatingChildBiomeModifierHydrologyTest {
    @Test
    public void floatingTerrainStaysOutOfPlannedWaterUpToItsHead() {
        ProceduralStream<WaterRole> roles = ProceduralStream.of((x, z) -> switch ((int) Math.floor(x)) {
            case 0 -> WaterRole.CHANNEL;
            case 1 -> WaterRole.POND;
            case 2 -> WaterRole.BANK;
            default -> WaterRole.NONE;
        }, Interpolated.of(value -> 0D, value -> WaterRole.NONE));
        ProceduralStream<Double> heads = ProceduralStream.of((x, z) -> (int) Math.floor(x) == 1 ? 324.4D : 318D, Interpolated.DOUBLE);
        assertEquals(318, IrisFloatingChildBiomeModifier.hydrologyWaterTop(roles, heads, 0, 5));
        assertEquals(324, IrisFloatingChildBiomeModifier.hydrologyWaterTop(roles, heads, 1, 5));
        assertEquals(Integer.MIN_VALUE, IrisFloatingChildBiomeModifier.hydrologyWaterTop(roles, heads, 2, 5));
        assertEquals(Integer.MIN_VALUE, IrisFloatingChildBiomeModifier.hydrologyWaterTop(roles, heads, 3, 5));
    }
}
