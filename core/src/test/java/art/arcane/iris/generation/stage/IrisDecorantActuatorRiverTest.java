package art.arcane.iris.generation.stage;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.iris.generation.context.ChunkedDataCache;
import art.arcane.iris.generation.decoration.HydrologyVegetation;
import art.arcane.iris.generation.decoration.IrisCeilingDecorator;
import art.arcane.iris.generation.decoration.IrisDecorator;
import art.arcane.iris.generation.decoration.IrisShoreLineDecorator;
import art.arcane.iris.generation.decoration.IrisSurfaceDecorator;
import art.arcane.iris.generation.hydrology.raster.WaterRole;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.EngineDecorator;
import art.arcane.iris.generation.runtime.EngineMetrics;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.hunk.Hunk;
import art.arcane.volmlib.util.stream.ProceduralStream;
import art.arcane.volmlib.util.stream.interpolation.Interpolated;
import org.junit.Test;
import org.mockito.InOrder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IrisDecorantActuatorRiverTest {
    @Test
    public void waterRoleControlsShorelineDecoration() {
        for (WaterRole role : WaterRole.values()) {
            IrisDecorantActuator.ShorelineDecorationMode expected = switch (role) {
                case NONE -> IrisDecorantActuator.ShorelineDecorationMode.LEGACY;
                case SHORE, POND_SHORE -> IrisDecorantActuator.ShorelineDecorationMode.ACCEPTED;
                case CHANNEL, BANK, POND, DAM, MOUTH, CASCADE, WATERFALL, INLET -> IrisDecorantActuator.ShorelineDecorationMode.NONE;
            };
            assertEquals(role.name(), expected, IrisDecorantActuator.shorelineDecorationMode(role, 65, 65));
        }
        assertEquals(IrisDecorantActuator.ShorelineDecorationMode.NONE, IrisDecorantActuator.shorelineDecorationMode(WaterRole.NONE, 64, 65));
        assertEquals(IrisDecorantActuator.ShorelineDecorationMode.ACCEPTED, IrisDecorantActuator.shorelineDecorationMode(WaterRole.SHORE, 62, 65));
    }

    @Test
    public void channelPondAndFallColumnsAreNeverDecorated() {
        for (WaterRole role : WaterRole.values()) {
            boolean skipped = role == WaterRole.CHANNEL || role == WaterRole.POND || role == WaterRole.CASCADE || role == WaterRole.WATERFALL;
            assertEquals(role.name(), skipped, IrisDecorantActuator.skipsDecoration(role));
            Decorant decorant = new Decorant(role);

            decorant.actuate();

            verify(decorant.surface, times(skipped ? 0 : 1)).decorate(eq(0), eq(0), eq(11), eq(-4), same(decorant.output),
                    same(decorant.biome), eq(60), anyInt());
        }
    }

    @Test
    public void waterPlantsGrowOnEveryHydrologyColumnBeforeTheBiomeDecorates() {
        for (WaterRole role : WaterRole.values()) {
            for (boolean biomeDecorated : new boolean[]{true, false}) {
                Decorant decorant = new Decorant(role);
                if (!biomeDecorated) {
                    when(decorant.biome.getDecorators()).thenReturn(new KList<>());
                }

                decorant.actuate();

                verify(decorant.vegetation, times(role == WaterRole.NONE ? 0 : 1)).decorate(same(decorant.output), eq(0), eq(0), eq(11), eq(-4),
                        eq(role), any(HydrologyVegetation.Water.class), any());
                if (role != WaterRole.NONE && biomeDecorated && !IrisDecorantActuator.skipsDecoration(role)) {
                    InOrder order = inOrder(decorant.vegetation, decorant.surface);
                    order.verify(decorant.vegetation).decorate(same(decorant.output), eq(0), eq(0), eq(11), eq(-4), eq(role), any(), any());
                    order.verify(decorant.surface).decorate(eq(0), eq(0), eq(11), eq(-4), same(decorant.output), same(decorant.biome), eq(60),
                            anyInt());
                }
            }
        }
        Decorant hydrologyOff = new Decorant(WaterRole.NONE);
        hydrologyOff.actuate();
        verify(hydrologyOff.vegetation, never()).decorate(any(), anyInt(), anyInt(), anyInt(), anyInt(), any(), any(), any());
    }

    @Test
    public void cavesBelowWaterColumnsKeepTheirCaveDecoration() {
        Decorant decorant = new Decorant(WaterRole.CHANNEL);
        NativeBlockState floor = state("minecraft:stone");
        when(floor.isSolid()).thenReturn(true);
        decorant.output.set(0, 30, 0, floor);
        for (int y = 61; y <= 64; y++) {
            decorant.output.set(0, y, 0, waterState());
        }
        IrisBiome cave = mock(IrisBiome.class);

        decorant.actuator.decorateCaveColumn(decorant.output, cave, 0, 0, 11, -4, 60);

        verify(decorant.surface).decorate(eq(0), eq(0), eq(11), eq(-4), same(decorant.output), same(cave), eq(30), eq(60));
        verify(decorant.ceiling).decorate(eq(0), eq(0), eq(11), eq(-4), same(decorant.output), same(cave), eq(59), eq(29));
    }

    @Test
    public void seaDecoratorsRequireConnectedWaterBelowTheConfiguredSurface() {
        Hunk<NativeBlockState> output = Hunk.newArrayHunk(3, 4, 1);
        NativeBlockState water = mock(NativeBlockState.class);
        NativeBlockState adjacentWater = mock(NativeBlockState.class);
        doReturn(true).when(water).isWater();
        doReturn(true).when(adjacentWater).isWater();
        output.set(1, 1, 0, water);

        assertFalse(IrisDecorantActuator.hasConnectedSurfaceWater(output, 1, 0, 0, 1));
        assertFalse(IrisDecorantActuator.hasConnectedSurfaceWater(output, 1, 0, 0, 2));

        output.set(0, 1, 0, adjacentWater);
        assertTrue(IrisDecorantActuator.hasConnectedSurfaceWater(output, 1, 0, 0, 2));
    }

    @Test
    public void seaDecoratorStacksRequireConnectedWaterAtEveryPlacedLevel() {
        Hunk<NativeBlockState> output = Hunk.newArrayHunk(3, 4, 1);
        NativeBlockState lowerWater = waterState();
        NativeBlockState upperWater = waterState();
        NativeBlockState adjacentWater = waterState();
        output.set(1, 1, 0, lowerWater);
        output.set(1, 2, 0, upperWater);
        output.set(0, 1, 0, adjacentWater);

        assertFalse(IrisDecorantActuator.hasConnectedWaterColumn(output, 1, 0, 1, 2));

        output.set(0, 2, 0, waterState());
        assertTrue(IrisDecorantActuator.hasConnectedWaterColumn(output, 1, 0, 1, 2));
    }

    @Test
    public void dryAquaticSeaSurfacePlacementRestoresTheOriginalBlock() {
        Hunk<NativeBlockState> output = Hunk.newArrayHunk(3, 3, 1);
        NativeBlockState air = state("minecraft:air");
        NativeBlockState seagrass = state("minecraft:seagrass");
        output.set(1, 1, 0, air);

        output.set(1, 1, 0, seagrass);
        IrisDecorantActuator.restoreUnsupportedAquaticPlacement(output, 1, 1, 0, air);

        assertSame(air, output.get(1, 1, 0));
    }

    private static NativeBlockState waterState() {
        NativeBlockState water = state("minecraft:water");
        doReturn(true).when(water).isWater();
        return water;
    }

    private static NativeBlockState state(String key) {
        NativeBlockState state = mock(NativeBlockState.class);
        doReturn(key).when(state).key();
        return state;
    }

    private static final class Decorant {
        private final Engine engine = mock(Engine.class);
        private final IrisComplex complex = mock(IrisComplex.class);
        private final ChunkContext context = mock(ChunkContext.class);
        private final IrisBiome biome = mock(IrisBiome.class);
        private final IrisSurfaceDecorator surface = mock(IrisSurfaceDecorator.class);
        private final IrisCeilingDecorator ceiling = mock(IrisCeilingDecorator.class);
        private final HydrologyVegetation vegetation = mock(HydrologyVegetation.class);
        private final IrisDecorantActuator actuator = mock(IrisDecorantActuator.class, CALLS_REAL_METHODS);
        private final Hunk<NativeBlockState> output = Hunk.newArrayHunk(1, 96, 1);

        private Decorant(WaterRole role) {
            NativeBlockState stone = state("minecraft:stone");
            when(stone.isSolid()).thenReturn(true);
            output.set(0, 60, 0, stone);
            when(engine.getDimension()).thenReturn(new IrisDimension());
            when(engine.getComplex()).thenReturn(complex);
            when(engine.getHeight()).thenReturn(96);
            when(engine.getMetrics()).thenReturn(new EngineMetrics(8));
            when(complex.allowsNewDiscreteContentAt(11, -4)).thenReturn(true);
            when(complex.getWaterRoleStream()).thenReturn(ProceduralStream.of((x, z) -> role, Interpolated.of(value -> 0D, value -> role)));
            when(complex.getWaterHeadStream()).thenReturn(ProceduralStream.ofDouble((x, z) -> 40D));
            when(biome.getDecorators()).thenReturn(new KList<>(mock(IrisDecorator.class)));
            when(context.getRoundedHeight(0, 0)).thenReturn(60);
            when(context.getBiome()).thenReturn(new ChunkedDataCache<>(
                    ProceduralStream.of((x, z) -> biome, Interpolated.of(value -> 0D, value -> biome)), 11, -4, false));
            doReturn(engine).when(actuator).getEngine();
            doReturn(surface).when(actuator).getSurfaceDecorator();
            doReturn(ceiling).when(actuator).getCeilingDecorator();
            doReturn(vegetation).when(actuator).getHydrologyVegetation();
            doReturn(mock(IrisShoreLineDecorator.class)).when(actuator).getShoreLineDecorator();
            doReturn(mock(EngineDecorator.class)).when(actuator).getSeaSurfaceDecorator();
            doReturn(mock(EngineDecorator.class)).when(actuator).getSeaFloorDecorator();
        }

        private void actuate() {
            actuator.onActuate(11, -4, output, false, context);
        }
    }
}
