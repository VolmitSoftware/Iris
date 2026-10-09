package art.arcane.iris.engine.decorator;

import art.arcane.iris.builtin.prepare.SlopeClip;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.SeedManager;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.decoration.IrisDecorationPart;
import art.arcane.iris.engine.object.decoration.IrisDecorator;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.project.hunk.Hunk;
import art.arcane.iris.util.project.stream.ProceduralStream;
import org.bukkit.block.BlockSupport;
import org.bukkit.block.data.BlockData;
import org.junit.Test;

import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IrisShoreLineDecoratorTest {
    private static final int FLUID_HEIGHT = 4;

    @Test
    public void carvedSurfaceRejectsShorelineDecoration() {
        Fixture fixture = createFixture(false);
        PlatformBlockState carvedAir = airState();
        PlatformBlockState targetAir = airState();
        Hunk<PlatformBlockState> output = output(carvedAir, targetAir);

        fixture.shoreline.decorate(0, 0, 0, 1, -1, 0, 1, -1,
                output, fixture.biome, FLUID_HEIGHT, output.getHeight());

        assertSame(targetAir, output.get(0, FLUID_HEIGHT + 1, 0));
        verify(fixture.decorator).passesChanceGate(any(), anyDouble(), anyDouble(), eq(fixture.data));
        verify(fixture.decorator, never()).getBlockData100(
                eq(fixture.biome), any(), anyDouble(), anyDouble(), anyDouble(), eq(fixture.data));
    }

    @Test
    public void preservedFluidRejectsShorelineDecoration() {
        Fixture fixture = createFixture(false);
        PlatformBlockState fluid = mock(PlatformBlockState.class);
        PlatformBlockState targetAir = airState();
        when(fluid.isFluid()).thenReturn(true);
        Hunk<PlatformBlockState> output = output(fluid, targetAir);

        fixture.shoreline.decorate(0, 0, 0, 1, -1, 0, 1, -1,
                output, fixture.biome, FLUID_HEIGHT, output.getHeight());

        assertSame(targetAir, output.get(0, FLUID_HEIGHT + 1, 0));
        verify(fixture.decorator, never()).getBlockData100(
                eq(fixture.biome), any(), anyDouble(), anyDouble(), anyDouble(), eq(fixture.data));
    }

    @Test
    public void sturdySurfacePlacesShorelineDecoration() {
        Fixture fixture = createFixture(false);
        PlatformBlockState support = sturdyState();
        PlatformBlockState targetAir = airState();
        when(fixture.decorant.canPlaceOnto(support)).thenReturn(true);
        Hunk<PlatformBlockState> output = output(support, targetAir);

        fixture.shoreline.decorate(0, 0, 0, 1, -1, 0, 1, -1,
                output, fixture.biome, FLUID_HEIGHT, output.getHeight());

        assertSame(fixture.decorant, output.get(0, FLUID_HEIGHT + 1, 0));
    }

    @Test
    public void forcePlaceStillRejectsMissingSurface() {
        Fixture fixture = createFixture(true);
        PlatformBlockState targetAir = airState();
        Hunk<PlatformBlockState> output = output(airState(), targetAir);

        fixture.shoreline.decorate(0, 0, 0, 1, -1, 0, 1, -1,
                output, fixture.biome, FLUID_HEIGHT, output.getHeight());

        assertSame(targetAir, output.get(0, FLUID_HEIGHT + 1, 0));
    }

    @SuppressWarnings("unchecked")
    private Fixture createFixture(boolean forcePlace) {
        Engine engine = mock(Engine.class);
        SeedManager seedManager = mock(SeedManager.class);
        IrisDimension dimension = mock(IrisDimension.class);
        IrisComplex complex = mock(IrisComplex.class);
        IrisData data = mock(IrisData.class);
        IrisBiome biome = mock(IrisBiome.class);
        IrisDecorator decorator = mock(IrisDecorator.class);
        SlopeClip slope = mock(SlopeClip.class);
        PlatformBlockState decorant = mock(PlatformBlockState.class);
        ProceduralStream<Double> heightStream = mock(ProceduralStream.class);

        when(engine.getCacheID()).thenReturn(1);
        when(engine.getSeedManager()).thenReturn(seedManager);
        when(seedManager.getComponent()).thenReturn(17L);
        when(engine.getDimension()).thenReturn(dimension);
        when(dimension.getFluidHeight()).thenReturn(FLUID_HEIGHT);
        when(engine.getComplex()).thenReturn(complex);
        when(complex.getFluidHeight()).thenReturn((double) FLUID_HEIGHT);
        when(complex.getHeightStream()).thenReturn(heightStream);
        when(heightStream.get(anyDouble(), anyDouble())).thenReturn((double) FLUID_HEIGHT - 1);
        when(engine.getData()).thenReturn(data);
        when(biome.getDecoratorBucket(IrisDecorationPart.SHORE_LINE))
                .thenReturn(new IrisDecorator[]{decorator});
        when(decorator.passesChanceGate(any(), anyDouble(), anyDouble(), eq(data))).thenReturn(true);
        when(decorator.isForcePlace()).thenReturn(forcePlace);
        when(decorator.getSlopeCondition()).thenReturn(slope);
        when(slope.isDefault()).thenReturn(true);
        when(decorator.getBlockData100(eq(biome), any(), anyDouble(), anyDouble(), anyDouble(), eq(data)))
                .thenReturn(decorant);

        return new Fixture(new IrisShoreLineDecorator(engine), data, biome, decorator, decorant);
    }

    private Hunk<PlatformBlockState> output(PlatformBlockState support, PlatformBlockState target) {
        Hunk<PlatformBlockState> output = Hunk.newArrayHunk(1, FLUID_HEIGHT + 3, 1);
        output.set(0, FLUID_HEIGHT, 0, support);
        output.set(0, FLUID_HEIGHT + 1, 0, target);
        return output;
    }

    private PlatformBlockState airState() {
        PlatformBlockState air = mock(PlatformBlockState.class);
        when(air.isAir()).thenReturn(true);
        return air;
    }

    private PlatformBlockState sturdyState() {
        PlatformBlockState support = mock(PlatformBlockState.class);
        BlockData blockData = mock(BlockData.class);
        when(support.isSolid()).thenReturn(true);
        when(support.nativeHandle()).thenReturn(blockData);
        when(blockData.isFaceSturdy(any(), eq(BlockSupport.FULL))).thenReturn(true);
        return support;
    }

    private record Fixture(
            IrisShoreLineDecorator shoreline,
            IrisData data,
            IrisBiome biome,
            IrisDecorator decorator,
            PlatformBlockState decorant
    ) {
    }
}
