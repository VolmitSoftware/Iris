package art.arcane.iris.engine.decorator;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.mantle.EngineMantle;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.SeedManager;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisDecorationPart;
import art.arcane.iris.engine.object.IrisDecorator;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.engine.object.IrisSlopeClip;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.iris.util.project.hunk.Hunk;
import art.arcane.iris.util.project.stream.ProceduralStream;
import org.bukkit.block.BlockSupport;
import org.bukkit.block.data.BlockData;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.BeforeClass;
import org.junit.AfterClass;

import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IrisShoreLineDecoratorTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    private static final int FLUID_HEIGHT = 4;

    @BeforeClass
    public static void bindPlatform() {
        IrisPlatforms.unbind();
        PlatformBlockState air = mock(PlatformBlockState.class);
        doReturn(true).when(air).isAir();
        doReturn("minecraft:air").when(air).key();
        PlatformRegistries registries = mock(PlatformRegistries.class);
        doReturn(air).when(registries).block(anyString());
        doReturn(air).when(registries).air();
        IrisPlatform platform = mock(IrisPlatform.class);
        doReturn(registries).when(platform).registries();
        IrisPlatforms.bind(platform);
    }

    @AfterClass
    public static void unbindPlatform() {
        IrisPlatforms.unbind();
    }

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
    public void acceptedShoreUsesPublishedGeometry() {
        Fixture fixture = createFixture(false, false);
        PlatformBlockState support = sturdyState();
        PlatformBlockState targetAir = airState();
        when(fixture.decorant.canPlaceOnto(support)).thenReturn(true);
        Hunk<PlatformBlockState> output = output(support, targetAir);

        fixture.shoreline.decorate(0, 0, 0, 1, -1, 0, 1, -1,
                output, fixture.biome, FLUID_HEIGHT, output.getHeight());

        assertSame(targetAir, output.get(0, FLUID_HEIGHT + 1, 0));

        fixture.shoreline.decorateAcceptedShore(
                0,
                0,
                0,
                0,
                output,
                fixture.biome,
                FLUID_HEIGHT,
                output.getHeight()
        );

        assertSame(fixture.decorant, output.get(0, FLUID_HEIGHT + 1, 0));
    }

    @Test
    public void forcePlaceStillRejectsMissingSurface() {
        Fixture fixture = createFixture(true);
        PlatformBlockState targetAir = airState();
        Hunk<PlatformBlockState> output = output(airState(), targetAir);

        fixture.shoreline.decorateAcceptedShore(
                0,
                0,
                0,
                0,
                output,
                fixture.biome,
                FLUID_HEIGHT,
                output.getHeight()
        );

        assertSame(targetAir, output.get(0, FLUID_HEIGHT + 1, 0));
    }

    @Test
    public void unsupportedWaterloggedStackRestoresEveryOriginalBlock() {
        Fixture fixture = createFixture(false, false);
        PlatformBlockState support = sturdyState();
        PlatformBlockState lowerOriginal = airState();
        PlatformBlockState upperOriginal = airState();
        PlatformBlockState waterloggedTop = mock(PlatformBlockState.class);
        when(fixture.decorant.key()).thenReturn("minecraft:grass");
        when(fixture.decorant.canPlaceOnto(support)).thenReturn(true);
        when(waterloggedTop.isWaterLogged()).thenReturn(true);
        when(fixture.decorator.isStacking()).thenReturn(true);
        when(fixture.decorator.isScaleStack()).thenReturn(false);
        when(fixture.decorator.getStackMax()).thenReturn(2);
        when(fixture.decorator.getHeight(any(), anyDouble(), anyDouble(), eq(fixture.data))).thenReturn(2);
        when(fixture.decorator.getTopThreshold()).thenReturn(1D);
        when(fixture.decorator.getBlockDataArray(fixture.data)).thenReturn(new PlatformBlockState[0]);
        when(fixture.decorator.getBlockDataTopsArray(fixture.data))
                .thenReturn(new PlatformBlockState[]{waterloggedTop});
        when(fixture.decorator.getBlockDataForTop(
                eq(fixture.biome), any(), anyDouble(), anyDouble(), anyDouble(), eq(fixture.data)))
                .thenReturn(waterloggedTop);

        Hunk<PlatformBlockState> output = Hunk.newArrayHunk(1, FLUID_HEIGHT + 3, 1);
        output.set(0, FLUID_HEIGHT, 0, support);
        output.set(0, FLUID_HEIGHT + 1, 0, lowerOriginal);
        output.set(0, FLUID_HEIGHT + 2, 0, upperOriginal);

        fixture.shoreline.decorateAcceptedShore(
                0,
                0,
                0,
                0,
                output,
                fixture.biome,
                FLUID_HEIGHT,
                output.getHeight()
        );

        assertSame(lowerOriginal, output.get(0, FLUID_HEIGHT + 1, 0));
        assertSame(upperOriginal, output.get(0, FLUID_HEIGHT + 2, 0));
    }

    @SuppressWarnings("unchecked")
    private Fixture createFixture(boolean forcePlace) {
        return createFixture(forcePlace, true);
    }

    @SuppressWarnings("unchecked")
    private Fixture createFixture(boolean forcePlace, boolean naturalShore) {
        Engine engine = mock(Engine.class);
        SeedManager seedManager = mock(SeedManager.class);
        IrisDimension dimension = mock(IrisDimension.class);
        IrisComplex complex = mock(IrisComplex.class);
        IrisData data = mock(IrisData.class);
        IrisBiome biome = mock(IrisBiome.class);
        IrisDecorator decorator = mock(IrisDecorator.class);
        IrisSlopeClip slope = mock(IrisSlopeClip.class);
        PlatformBlockState decorant = mock(PlatformBlockState.class);
        ProceduralStream<Double> heightStream = mock(ProceduralStream.class);
        EngineMantle mantle = mock(EngineMantle.class);

        when(engine.getCacheID()).thenReturn(1);
        when(engine.getSeedManager()).thenReturn(seedManager);
        when(seedManager.getComponent()).thenReturn(17L);
        when(engine.getDimension()).thenReturn(dimension);
        when(dimension.getFluidHeight()).thenReturn(FLUID_HEIGHT);
        when(engine.getComplex()).thenReturn(complex);
        when(complex.getFluidHeight()).thenReturn((double) FLUID_HEIGHT);
        when(complex.getHeightStream()).thenReturn(heightStream);
        when(engine.getMantle()).thenReturn(mantle);
        when(mantle.getFluidHeight(anyInt(), anyInt())).thenReturn(FLUID_HEIGHT);
        when(heightStream.get(anyDouble(), anyDouble())).thenReturn(
                naturalShore ? (double) FLUID_HEIGHT - 1 : (double) FLUID_HEIGHT
        );
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
