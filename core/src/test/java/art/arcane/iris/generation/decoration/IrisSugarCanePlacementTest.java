package art.arcane.iris.generation.decoration;

import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.hydrology.HydrologyColumnLayer;
import art.arcane.iris.generation.hydrology.HydrologyColumnSample;
import art.arcane.iris.generation.hydrology.HydrologyFeatureRef;
import art.arcane.iris.generation.hydrology.HydrologyFeatureType;
import art.arcane.iris.generation.mantle.EngineMantle;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.hunk.Hunk;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisSugarCanePlacementTest {
    @Test
    public void floatingSingleRequiresSuitableSoilAndAdjacentWater() {
        verifySupport(Placement.FLOATING_SINGLE);
    }

    @Test
    public void floatingStackRequiresSuitableSoilAndAdjacentWater() {
        verifySupport(Placement.FLOATING_STACK);
    }

    @Test
    public void ceilingSingleRequiresSuitableSoilAndAdjacentWater() {
        verifySupport(Placement.CEILING_SINGLE);
    }

    @Test
    public void ceilingStackRequiresSuitableSoilAndAdjacentWater() {
        verifySupport(Placement.CEILING_STACK);
    }

    @Test
    public void floatingCaneUsesAcceptedWaterAcrossTheChunkBoundary() {
        for (Placement placement : List.of(Placement.FLOATING_SINGLE, Placement.FLOATING_STACK)) {
            Fixture fixture = new Fixture(1);
            fixture.output.set(0, 3, 0, fixture.soil);
            IrisComplex complex = mock(IrisComplex.class);
            when(fixture.engine.getComplex()).thenReturn(complex);
            HydrologyFeatureRef feature = new HydrologyFeatureRef(
                    11L, HydrologyFeatureType.RIFFLE, 12L, 13L, -17, 3, -16, 1, 0, false);
            HydrologyColumnLayer layer = new HydrologyColumnLayer(feature, 1, 3, 3,
                    true, false, false, true, false, false, true, true, false,
                    "water", "river", "mouth", "shore", "bank", "cave");
            when(complex.sampleHydrologyColumn(-17, -16)).thenReturn(new HydrologyColumnSample(
                    -17, -16, 6, 0, false, "parent", List.of(layer)));
            when(complex.resolveHydrologyFluid("water", -17, -16)).thenReturn(fixture.water);

            fixture.place(placement);

            assertSame(fixture.cane, fixture.output.get(0, 4, 0));
        }
    }

    private static void verifySupport(Placement placement) {
        Fixture fixture = new Fixture(3);
        fixture.output.set(1, 3, 1, fixture.stone);
        fixture.output.set(0, 3, 1, fixture.water);
        fixture.place(placement);
        assertSame(fixture.air, fixture.output.get(1, 4, 1));

        fixture.output.set(1, 3, 1, fixture.soil);
        fixture.output.set(0, 3, 1, fixture.air);
        fixture.place(placement);
        assertSame(fixture.air, fixture.output.get(1, 4, 1));

        fixture.output.set(0, 3, 1, fixture.water);
        fixture.place(placement);
        assertSame(fixture.cane, fixture.output.get(1, 4, 1));
    }

    private static PlatformBlockState state(String key) {
        PlatformBlockState state = mock(PlatformBlockState.class);
        when(state.key()).thenReturn(key);
        return state;
    }

    private enum Placement {
        FLOATING_SINGLE,
        FLOATING_STACK,
        CEILING_SINGLE,
        CEILING_STACK
    }

    private static final class Fixture {
        private final PlatformBlockState air = state("minecraft:air");
        private final PlatformBlockState stone = state("minecraft:stone");
        private final PlatformBlockState soil = state("minecraft:dirt");
        private final PlatformBlockState water = state("minecraft:water");
        private final PlatformBlockState cane = state("minecraft:sugar_cane[age=0]");
        private final IrisDecorator decorator = mock(IrisDecorator.class);
        private final Engine engine = mock(Engine.class);
        private final EngineMantle mantle = mock(EngineMantle.class);
        private final Hunk<PlatformBlockState> output;
        private final int local;

        private Fixture(int size) {
            local = size / 2;
            output = Hunk.newArrayHunk(size, 8, size);
            when(air.isAir()).thenReturn(true);
            when(water.isWater()).thenReturn(true);
            when(cane.canPlaceOnto(soil)).thenReturn(true);
            when(cane.canPlaceOnto(cane)).thenReturn(true);
            when(mantle.getEngine()).thenReturn(engine);
            when(decorator.isForcePlace()).thenReturn(true);
            when(decorator.getHeight(any(), anyDouble(), anyDouble(), any())).thenReturn(2);
            when(decorator.pickBlockData(any(), any(), anyDouble(), anyDouble())).thenReturn(cane);
            when(decorator.pickBlockDataTop(any(), any(), anyDouble(), anyDouble())).thenReturn(cane);
            output.fill(air);
        }

        private void place(Placement placement) {
            int world = -16 + local;
            switch (placement) {
                case FLOATING_SINGLE -> DecoratorCore.placeFloatingSimple(decorator, local, local, world, world,
                        3, 4, output, new RNG(1L), null, mantle);
                case FLOATING_STACK -> DecoratorCore.placeFloatingStacked(decorator, local, local, world, world,
                        3, 4, output, new RNG(1L), null, mantle);
                case CEILING_SINGLE -> DecoratorCore.placeSingleAt(decorator, local, local, world, 4, world,
                        output, new RNG(1L), null, false, mantle);
                case CEILING_STACK -> DecoratorCore.placeStackDown(decorator, local, local, world, world,
                        4, 0, output, new RNG(1L), null, 2, new DecoratorCore.PlaceOpts(), mantle);
            }
        }
    }
}
