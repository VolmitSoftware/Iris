package art.arcane.iris.engine.decorator;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.SeedManager;
import art.arcane.iris.engine.object.world.biome.InferredType;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.decoration.IrisDecorationPart;
import art.arcane.iris.engine.object.decoration.IrisDecorator;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.project.hunk.Hunk;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class IrisDecoratorCaveContextTest {
    @Test
    @SuppressWarnings("unchecked")
    public void explicitCaveContextSkipsFluidWithoutMutatingBiome() {
        Engine engine = mock(Engine.class);
        SeedManager seedManager = mock(SeedManager.class);
        doReturn(seedManager).when(engine).getSeedManager();
        doReturn(mock(IrisData.class)).when(engine).getData();
        IrisDimension dimension = mock(IrisDimension.class);
        doReturn(63).when(dimension).getFluidHeight();
        doReturn(dimension).when(engine).getDimension();

        IrisDecorator decorator = mock(IrisDecorator.class);
        doReturn(true).when(decorator).passesChanceGate(any(), anyDouble(), anyDouble(), any());
        doReturn(true).when(decorator).isStacking();
        doReturn(true).when(decorator).isForcePlace();
        doReturn(1).when(decorator).getHeight(any(), anyDouble(), anyDouble(), any());

        IrisBiome biome = mock(IrisBiome.class);
        doReturn(InferredType.LAND).when(biome).getInferredType();
        doReturn(new IrisDecorator[]{decorator}).when(biome).getDecoratorBucket(IrisDecorationPart.NONE);
        doReturn(new IrisDecorator[]{decorator}).when(biome).getDecoratorBucket(IrisDecorationPart.CEILING);

        PlatformBlockState fluid = mock(PlatformBlockState.class);
        doReturn(true).when(fluid).isFluid();
        Hunk<PlatformBlockState> output = mock(Hunk.class);
        doReturn(128).when(output).getHeight();
        doReturn(fluid).when(output).get(0, 10, 0);

        IrisSurfaceDecorator surfaceDecorator = new IrisSurfaceDecorator(engine);
        surfaceDecorator.decorate(0, 0, 0, 0, 0, 0, 0, 0, output, biome, InferredType.CAVE, 10, 10);
        DecoratorCore.PlaceOpts surfaceOptions = DecoratorCore.SCRATCH_OPTS.get();
        assertTrue(surfaceOptions.caveSkipFluid);
        assertFalse(surfaceOptions.underwater);

        IrisCeilingDecorator ceilingDecorator = new IrisCeilingDecorator(engine);
        ceilingDecorator.decorate(0, 0, 0, 0, 0, 0, 0, 0, output, biome, InferredType.CAVE, 10, 10);
        DecoratorCore.PlaceOpts ceilingOptions = DecoratorCore.SCRATCH_OPTS.get();
        assertTrue(ceilingOptions.caveSkipFluid);
        assertEquals(InferredType.LAND, biome.getInferredType());
    }

    @Test
    public void nullInferenceRemainsSafeForNormalSurfaceContext() {
        assertTrue(IrisSurfaceDecorator.isUnderwater(null, 10, 63));
        assertFalse(IrisSurfaceDecorator.skipsFluid(null));
    }
}
