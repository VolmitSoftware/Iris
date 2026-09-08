package art.arcane.iris.engine.framework;

import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.DimensionStackContext;
import art.arcane.iris.engine.DimensionStackLayout;
import art.arcane.iris.engine.mantle.EngineMantle;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisRegion;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBiome;
import art.arcane.iris.spi.PlatformBiomeWriter;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class EngineHostHeightTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Before
    public void bindPlatform() {
        IrisPlatforms.unbind();
        PlatformBiome biome = mock(PlatformBiome.class);
        PlatformBlockState block = mock(PlatformBlockState.class);
        PlatformRegistries registries = mock(PlatformRegistries.class);
        when(registries.biome(anyString())).thenReturn(biome);
        when(registries.block(anyString())).thenReturn(block);
        PlatformBiomeWriter biomeWriter = mock(PlatformBiomeWriter.class);
        when(biomeWriter.biomeIdFor(anyString())).thenReturn(1);
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.registries()).thenReturn(registries);
        when(platform.biomeWriter()).thenReturn(biomeWriter);
        IrisPlatforms.bind(platform);
    }

    @After
    public void unbindPlatform() {
        IrisPlatforms.unbind();
    }

    @Test
    public void coldMainThreadQueryUsesNaturalHostTerrain() {
        Engine engine = mock(Engine.class);
        EngineMantle mantle = mock(EngineMantle.class);
        IrisComplex complex = mock(IrisComplex.class);
        when(engine.getMantle()).thenReturn(mantle);
        when(engine.getComplex()).thenReturn(complex);
        when(engine.answersFromNaturalTerrain(12, -8)).thenReturn(true);
        when(complex.naturalTrueHeight(12, -8)).thenReturn(42);
        when(mantle.getFluidHeight()).thenReturn(50);

        assertEquals(42, Engine.hostHeight(engine, 12, -8, true));
        assertEquals(50, Engine.hostHeight(engine, 12, -8, false));
        verify(mantle, never()).trueHeight(12, -8);
        verify(mantle, never()).getFluidHeight(12, -8);
    }

    @Test
    public void plannedQueryUsesHostColumnTerrainAndFluid() {
        Engine engine = mock(Engine.class);
        EngineMantle mantle = mock(EngineMantle.class);
        when(engine.getMantle()).thenReturn(mantle);
        when(mantle.trueHeight(3, 7)).thenReturn(45);
        when(mantle.getFluidHeight(3, 7)).thenReturn(48);

        assertEquals(45, Engine.hostHeight(engine, 3, 7, true));
        assertEquals(48, Engine.hostHeight(engine, 3, 7, false));
    }

    @Test
    public void yAwareRegionUsesTheOwningStackLayer() {
        Engine engine = mock(Engine.class, CALLS_REAL_METHODS);
        DimensionStackContext stack = mock(DimensionStackContext.class);
        DimensionStackLayout.Layer layer = mock(DimensionStackLayout.Layer.class);
        DimensionStackLayout layout = mock(DimensionStackLayout.class);
        IrisRegion expected = mock(IrisRegion.class);
        when(engine.getDimensionStackContext()).thenReturn(stack);
        when(stack.getLayout(12, -8)).thenReturn(layout);
        when(layout.layerAt(96)).thenReturn(layer);
        when(layer.region()).thenReturn(expected);

        assertEquals(expected, engine.getRegion(12, 96, -8));
    }

    @Test
    public void stackQueryConsumersUseCacheAwareLayouts() {
        Engine engine = mock(Engine.class, CALLS_REAL_METHODS);
        DimensionStackContext stack = mock(DimensionStackContext.class);
        DimensionStackLayout layout = mock(DimensionStackLayout.class);
        DimensionStackLayout.Layer bottom = mock(DimensionStackLayout.Layer.class);
        DimensionStackLayout.Layer upper = mock(DimensionStackLayout.Layer.class);
        IrisRegion region = mock(IrisRegion.class);
        IrisBiome biome = mock(IrisBiome.class);
        when(engine.getDimensionStackContext()).thenReturn(stack);
        when(stack.getLayout(12, -8)).thenReturn(layout);
        when(layout.layersBottomToTop()).thenReturn(List.of(bottom, upper));
        when(layout.surfaceLayer()).thenReturn(upper);
        when(layout.layerAt(96)).thenReturn(upper);
        when(upper.region()).thenReturn(region);
        when(upper.biome()).thenReturn(biome);

        assertSame(region, engine.getRegion(12, -8));
        assertSame(biome, engine.getSurfaceBiome(12, -8));
        assertSame(biome, engine.getBiome(12, 96, -8));
        assertSame(biome, engine.getBiomeOrMantle(12, 96, -8));

        verify(stack, times(4)).getLayout(12, -8);
        verify(stack, never()).sample(anyInt(), anyInt());
    }
}
