package art.arcane.iris.engine.decorator;

import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisDecorationPart;
import art.arcane.iris.engine.object.IrisDecorator;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.project.hunk.Hunk;
import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisSeaDecoratorBoundsTest {
    @Test
    public void floorPercentageCannotGrowAboveTheWaterColumn() {
        Fixture fixture = new Fixture(12, false);
        fixture.stacking(200, 30);

        fixture.decorate(4, 8);

        assertSame(fixture.body, fixture.output.get(0, 4, 0));
        assertSame(fixture.top, fixture.output.get(0, 7, 0));
        assertNull(fixture.output.get(0, 8, 0));
    }

    @Test
    public void floorPercentageRespectsAbsoluteMaximum() {
        Fixture fixture = new Fixture(64, false);
        fixture.stacking(100, 3);

        fixture.decorate(4, 60);

        assertSame(fixture.body, fixture.output.get(0, 4, 0));
        assertSame(fixture.top, fixture.output.get(0, 6, 0));
        assertNull(fixture.output.get(0, 7, 0));
    }

    @Test
    public void surfacePercentageRespectsAbsoluteMaximum() {
        Fixture fixture = new Fixture(64, true);
        fixture.stacking(100, 3);

        fixture.decorate(4, 64);

        assertSame(fixture.body, fixture.output.get(0, 5, 0));
        assertSame(fixture.top, fixture.output.get(0, 7, 0));
        assertNull(fixture.output.get(0, 8, 0));
    }

    @Test
    public void floorStackAtOutputCeilingUsesOneTopBlock() {
        Fixture fixture = new Fixture(8, false);
        fixture.stacking(200, 30);

        fixture.decorate(7, 20);

        assertSame(fixture.top, fixture.output.get(0, 7, 0));
    }

    @Test
    public void unresolvedFloorPalettePreservesExistingWater() {
        Fixture fixture = new Fixture(8, false);
        fixture.stacking(100, 30);
        PlatformBlockState water = mock(PlatformBlockState.class);
        fixture.output.set(0, 3, 0, water);
        when(fixture.decorator.getBlockData100(any(), any(), anyDouble(), anyDouble(), anyDouble(), any())).thenReturn(null);

        fixture.decorate(3, 8);

        assertSame(water, fixture.output.get(0, 3, 0));
        assertNull(fixture.output.get(0, 4, 0));
    }

    @Test
    public void surfaceStackUsesTheShorterOutputHeight() {
        Fixture fixture = new Fixture(8, true);
        when(fixture.engine.getHeight()).thenReturn(64);
        fixture.stacking(200, 30);

        fixture.decorate(6, 64);

        assertSame(fixture.top, fixture.output.get(0, 7, 0));
    }

    @Test
    public void surfaceSingleRejectsOutputCeiling() {
        Fixture fixture = new Fixture(8, true);
        when(fixture.engine.getHeight()).thenReturn(64);

        fixture.decorate(7, 64);

        assertNull(fixture.output.get(0, 7, 0));
    }

    @Test
    public void surfaceSingleStackRejectsNegativeBaseAndEngineCeiling() {
        Fixture fixture = new Fixture(16, true);
        when(fixture.engine.getHeight()).thenReturn(8);
        fixture.stacking(1, 30);
        when(fixture.decorator.isScaleStack()).thenReturn(false);

        fixture.decorate(-1, 16);
        fixture.decorate(7, 16);

        assertNull(fixture.output.get(0, 0, 0));
        assertNull(fixture.output.get(0, 8, 0));
    }

    private static final class Fixture {
        private final IrisDecorator decorator = mock(IrisDecorator.class);
        private final IrisBiome biome = mock(IrisBiome.class);
        private final PlatformBlockState body = mock(PlatformBlockState.class);
        private final PlatformBlockState top = mock(PlatformBlockState.class);
        private final Engine engine = mock(Engine.class, RETURNS_DEEP_STUBS);
        private final Hunk<PlatformBlockState> output;
        private final IrisEngineDecorator placement;

        private Fixture(int height, boolean surface) {
            when(engine.getHeight()).thenReturn(height);
            output = Hunk.newArrayHunk(1, height, 1);
            IrisDecorationPart part = surface ? IrisDecorationPart.SEA_SURFACE : IrisDecorationPart.SEA_FLOOR;
            when(biome.getDecoratorBucket(part)).thenReturn(new IrisDecorator[]{decorator});
            when(decorator.passesChanceGate(any(), anyDouble(), anyDouble(), any())).thenReturn(true);
            when(decorator.getBlockData100(any(), any(), anyDouble(), anyDouble(), anyDouble(), any())).thenReturn(body);
            when(decorator.getBlockDataForTop(any(), any(), anyDouble(), anyDouble(), anyDouble(), any())).thenReturn(top);
            when(decorator.getTopThreshold()).thenReturn(1D);
            placement = surface ? new IrisSeaSurfaceDecorator(engine) : new IrisSeaFloorDecorator(engine);
        }

        private void stacking(int percentage, int cap) {
            when(decorator.isStacking()).thenReturn(true);
            when(decorator.isScaleStack()).thenReturn(true);
            when(decorator.getHeight(any(), anyDouble(), anyDouble(), any())).thenReturn(percentage);
            when(decorator.getAbsoluteMaxStack()).thenReturn(cap);
        }

        private void decorate(int height, int maximum) {
            placement.decorate(0, 0, 0, 0, 0, 0, 0, 0, output, biome, height, maximum);
        }
    }
}
