package art.arcane.iris.studio.render;

import art.arcane.iris.generation.hydrology.engine.HydrologyPlanPreview;
import art.arcane.iris.generation.hydrology.engine.HydrologyRuntime;
import art.arcane.iris.generation.hydrology.query.HydrologyFeatureType;
import art.arcane.iris.generation.hydrology.underground.Grotto;
import art.arcane.iris.generation.hydrology.water.HydrologyTier;
import art.arcane.iris.generation.hydrology.water.Pond;
import art.arcane.iris.generation.hydrology.water.Reach;
import art.arcane.iris.testsupport.WaterScene;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class WaterMapTest {
    private static final int PIXELS = 32;
    private static final double MID_STEP = 4D;
    private static final int LEVEL = 62;
    private static final int POND_X = 30;
    private static final int POND_Z = 30;

    @Test
    public void channelsDrawOverPondsAtMidZoom() {
        HydrologyTier tier = WaterScene.stream();
        Reach across = WaterScene.line(1L, tier, 4, POND_Z, 60, POND_Z, (x, z) -> LEVEL);
        Reach fall = WaterScene.line(2L, tier, POND_X, 10, POND_X, 50, (x, z) -> z < POND_Z ? LEVEL + 8 : LEVEL);
        Pond pond = WaterScene.pond(3L, LEVEL, POND_X, POND_Z, 10);

        WaterMap map = draw(List.of(across, fall), List.of(pond), List.of());

        assertEquals("the fall where both channels cross the pond", HydrologyFeatureType.WATERFALL, map.at(7, 7));
        assertEquals("the channel across the pond", HydrologyFeatureType.STREAM, map.at(6, 7));
        assertEquals("the channel below the fall", HydrologyFeatureType.STREAM, map.at(7, 9));
        assertEquals("open pond water", HydrologyFeatureType.POND, map.at(9, 9));
    }

    @Test
    public void pondsDrawOverLakesAtMidZoom() {
        Pond pond = WaterScene.pond(4L, LEVEL, POND_X, POND_Z, 10);
        Pond lake = WaterScene.pond(5L, LEVEL, 44, POND_Z, 12);

        WaterMap map = draw(List.of(), List.of(pond), List.of(lake));

        assertEquals("pond and lake share the pixel", HydrologyFeatureType.POND, map.at(9, 7));
        assertEquals("open lake water", HydrologyFeatureType.LAKE, map.at(13, 7));
    }

    @Test
    public void grottosDrawAtMidZoom() {
        Grotto grotto = new Grotto(9L, LEVEL, 0.5f, 0, new float[]{4f, 60f}, new float[]{POND_Z, POND_Z}, new float[]{4f, 4f}, new float[]{5f, 5f}, new float[]{3f, 3f}, new float[]{0f, 0f},
                new float[0], new float[0], new float[0], new int[0], new long[0]);
        HydrologyRuntime runtime = mock(HydrologyRuntime.class);
        when(runtime.planPreview(anyInt(), anyInt(), anyInt(), anyInt()))
                .thenReturn(new HydrologyPlanPreview(List.of(), List.of(), List.of(), List.of(grotto), true));
        WaterMap map = new WaterMap(runtime, PIXELS, 0D, 0D, MID_STEP);
        map.advance(() -> false);
        assertEquals(HydrologyFeatureType.GROTTO, map.at((int) (30D / MID_STEP), (int) (POND_Z / MID_STEP)));
    }

    private static WaterMap draw(List<Reach> reaches, List<Pond> ponds, List<Pond> lakes) {
        HydrologyRuntime runtime = mock(HydrologyRuntime.class);
        when(runtime.planPreview(anyInt(), anyInt(), anyInt(), anyInt()))
                .thenReturn(new HydrologyPlanPreview(reaches, ponds, lakes, true));
        when(runtime.channelType(any(HydrologyTier.class))).thenReturn(HydrologyFeatureType.STREAM);
        WaterMap map = new WaterMap(runtime, PIXELS, 0D, 0D, MID_STEP);
        map.advance(() -> false);
        return map;
    }
}
