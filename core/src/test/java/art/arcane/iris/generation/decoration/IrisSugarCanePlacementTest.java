package art.arcane.iris.generation.decoration;

import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.hydrology.raster.WaterRole;
import art.arcane.iris.generation.mantle.EngineMantle;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.hunk.Hunk;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.stream.ProceduralStream;
import art.arcane.volmlib.util.stream.interpolation.Interpolated;
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
    public void floatingCaneUsesWaterRoleColumnsAcrossTheChunkBoundary() {
        for (Placement placement : List.of(Placement.FLOATING_SINGLE, Placement.FLOATING_STACK)) {
            Fixture fixture = new Fixture(1);
            fixture.output.set(0, 3, 0, fixture.soil);
            IrisComplex complex = mock(IrisComplex.class);
            when(fixture.engine.getComplex()).thenReturn(complex);
            when(complex.getHeightStream()).thenReturn(ProceduralStream.ofDouble((x, z) -> 1D));
            when(complex.getWaterHeadStream()).thenReturn(ProceduralStream.ofDouble((x, z) -> 3D));
            when(complex.getWaterRoleStream()).thenReturn(ProceduralStream.of((x, z) -> WaterRole.POND,
                    Interpolated.of(value -> 0D, value -> WaterRole.POND)));
            when(complex.resolveWaterFluid(-17, -16)).thenReturn(fixture.water);

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

    private static NativeBlockState state(String key) {
        NativeBlockState state = mock(NativeBlockState.class);
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
        private final NativeBlockState air = state("minecraft:air");
        private final NativeBlockState stone = state("minecraft:stone");
        private final NativeBlockState soil = state("minecraft:dirt");
        private final NativeBlockState water = state("minecraft:water");
        private final NativeBlockState cane = state("minecraft:sugar_cane[age=0]");
        private final IrisDecorator decorator = mock(IrisDecorator.class);
        private final Engine engine = mock(Engine.class);
        private final EngineMantle mantle = mock(EngineMantle.class);
        private final Hunk<NativeBlockState> output;
        private final int local;

        private Fixture(int size) {
            local = size / 2;
            output = Hunk.newArrayHunk(size, 8, size);
            when(air.isAir()).thenReturn(true);
            when(stone.isSolid()).thenReturn(true);
            when(soil.isSolid()).thenReturn(true);
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
