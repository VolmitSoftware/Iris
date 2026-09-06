package art.arcane.iris.engine.object.fungi;

import art.arcane.iris.engine.object.IrisFungus;
import art.arcane.iris.util.common.math.Vector3i;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FungusStemBuilderTest {
    @Test
    public void straightStemsKeepOddAndEvenWidthAnchors() {
        for (int width = 1; width <= 3; width++) {
            IrisFungus fungus = new IrisFungus();
            fungus.setStemWidth(width);
            fungus.setStemCurve(0D);
            fungus.setStemWaveAmplitude(0D);
            Map<Vector3i, FungusCellRole> roles = new HashMap<>();

            double[] top = FungusStemBuilder.build(roles, fungus, 7, 421L);

            assertArrayEquals(new double[]{0D, 6D, 0D}, top, 0D);
            assertEquals(width * width * 7, roles.size());
            int origin = width == 3 ? -1 : 0;
            for (int y = 0; y < 7; y++) {
                for (int x = origin; x < origin + width; x++) {
                    for (int z = origin; z < origin + width; z++) {
                        assertEquals(FungusCellRole.STEM, roles.get(new Vector3i(x, y, z)));
                    }
                }
            }
        }
    }

    @Test
    public void curvedStemsKeepEveryLayerAndReturnTheirCapAnchor() {
        IrisFungus fungus = new IrisFungus();
        fungus.setStemWidth(3);
        fungus.setStemCurve(22D);
        fungus.setStemLeanAzimuth(225D);
        fungus.setStemWaveAmplitude(2D);
        fungus.setStemWavePeriods(1.5D);
        Map<Vector3i, FungusCellRole> roles = new HashMap<>();

        double[] top = FungusStemBuilder.build(roles, fungus, 24, -733L);

        assertEquals(24 * 9, roles.size());
        assertTrue(top[0] < 0D);
        assertTrue(top[2] < 0D);
        assertEquals(23D, top[1], 0D);
        assertEquals(FungusCellRole.STEM, roles.get(new Vector3i(
                (int) Math.round(top[0]), 23, (int) Math.round(top[2]))));
        for (int y = 0; y < 24; y++) {
            int count = 0;
            for (Vector3i position : roles.keySet()) {
                if (position.getY() == y) {
                    count++;
                }
            }
            assertEquals(9, count);
        }
    }
}
