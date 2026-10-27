package art.arcane.iris.studio.render;

import art.arcane.iris.generation.hydrology.engine.HydrologyPlanPreview;
import art.arcane.iris.generation.hydrology.engine.HydrologyRuntime;
import art.arcane.iris.generation.hydrology.query.HydrologyFeatureType;
import art.arcane.iris.generation.hydrology.raster.ChunkWaterRaster;
import art.arcane.iris.generation.hydrology.raster.WaterRole;
import art.arcane.iris.generation.hydrology.water.Reach;
import org.junit.Test;

import java.util.concurrent.CancellationException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class WaterMapProgressTest {
    @Test
    public void completeDetailedRastersDoNotDrawPlanFallbacks() {
        HydrologyRuntime runtime = mock(HydrologyRuntime.class);
        Reach reach = mock(Reach.class);
        when(runtime.planPreview(anyInt(), anyInt(), anyInt(), anyInt()))
                .thenReturn(new HydrologyPlanPreview(List.of(reach), List.of(), List.of(), List.of(), true));
        when(runtime.rasterPreview(anyInt(), anyInt())).thenReturn(ChunkWaterRaster.EMPTY);

        WaterMap map = WaterMap.draw(runtime, 32, 0D, 0D, 1D, () -> false);

        assertFalse(map.provisional());
        verifyNoInteractions(reach);
    }

    @Test
    public void completedPixelsSurviveRuntimeRasterEviction() {
        HydrologyRuntime runtime = mock(HydrologyRuntime.class);
        ChunkWaterRaster pond = pond();
        AtomicBoolean pending = new AtomicBoolean(true);
        when(runtime.planPreview(anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(HydrologyPlanPreview.EMPTY);
        when(runtime.rasterPreview(anyInt(), anyInt())).thenReturn(ChunkWaterRaster.EMPTY);
        when(runtime.rasterPreview(0, 0)).thenReturn(pond, ChunkWaterRaster.PROVISIONAL);
        when(runtime.rasterPreview(1, 0)).thenAnswer(invocation -> pending.get() ? ChunkWaterRaster.PROVISIONAL : pond);
        WaterMap map = new WaterMap(runtime, 32, 0D, 0D, 1D);

        map.advance(() -> false);
        assertTrue(map.provisional());
        assertEquals(HydrologyFeatureType.POND, map.at(1, 1));
        assertNull(map.at(17, 1));
        pending.set(false);
        map.advance(() -> false);

        assertFalse(map.provisional());
        assertEquals(HydrologyFeatureType.POND, map.at(1, 1));
        assertEquals(HydrologyFeatureType.POND, map.at(17, 1));
        assertNull(map.at(1, 17));
        assertTrue(map.bytes() < 16_384L);
        verify(runtime, times(1)).rasterPreview(0, 0);
        verify(runtime, times(2)).rasterPreview(1, 0);
        verify(runtime, times(1)).planPreview(anyInt(), anyInt(), anyInt(), anyInt());

        map.advance(() -> false);
        verify(runtime, times(2)).rasterPreview(1, 0);
    }

    @Test
    public void cancelledWaterSamplingResumesToTheSameFinalPixels() {
        HydrologyRuntime runtime = mock(HydrologyRuntime.class);
        ChunkWaterRaster raster = pond();
        when(runtime.planPreview(anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(HydrologyPlanPreview.EMPTY);
        when(runtime.rasterPreview(anyInt(), anyInt())).thenReturn(raster);
        AtomicInteger rows = new AtomicInteger();
        WaterMap map = new WaterMap(runtime, 32, -16.5D, -16.5D, 0.5D);

        assertThrows(CancellationException.class, () -> map.advance(() -> rows.incrementAndGet() > 2));
        assertTrue(map.provisional());
        map.advance(() -> false);
        assertFalse(map.provisional());
        WaterMap expected = WaterMap.draw(runtime, 32, -16.5D, -16.5D, 0.5D, () -> false);
        for (int z = 0; z < 32; z++) {
            for (int x = 0; x < 32; x++) {
                assertEquals(expected.at(x, z), map.at(x, z));
            }
        }
    }

    private static ChunkWaterRaster pond() {
        ChunkWaterRaster raster = mock(ChunkWaterRaster.class);
        when(raster.role(anyInt(), anyInt())).thenReturn(WaterRole.POND);
        return raster;
    }
}
