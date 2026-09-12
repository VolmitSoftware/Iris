package art.arcane.iris.generation.runtime;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.generation.block.DataProvider;
import org.junit.Test;

import java.awt.Color;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class EnginePreviewColorTest {
    @Test
    public void previewUsesOneSurfaceEnvironmentAndItsHistoricalRegionDefinitions() {
        Engine engine = mock(Engine.class, CALLS_REAL_METHODS);
        IrisData data = mock(IrisData.class);
        IrisRegion region = mock(IrisRegion.class);
        IrisBiome biome = mock(IrisBiome.class);
        BiomeEnvironment environment = new BiomeEnvironment(1L, biome, region, mock(IrisDimension.class), data);
        EngineTarget target = mock(EngineTarget.class);
        doReturn(environment).when(engine).getSurfaceBiomeEnvironment(-24, 40);
        doReturn(target).when(engine).getTarget();
        doReturn(100).when(engine).getHeight(-24, 40);
        when(target.getHeight()).thenReturn(384);
        when(region.getColor(any(), any())).thenAnswer(invocation -> {
            DataProvider provider = invocation.getArgument(0);
            assertSame(data, provider.getData());
            return Color.GREEN;
        });
        when(biome.getColor(any(), any())).thenReturn(Color.YELLOW);

        assertNotNull(engine.draw(-24, 40));

        verify(engine).getSurfaceBiomeEnvironment(-24, 40);
        verify(engine, never()).getRegion(anyInt(), anyInt());
        verify(engine, never()).getSurfaceBiome(anyInt(), anyInt());
    }
}
