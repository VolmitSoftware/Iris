package art.arcane.iris.generation.decoration;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.mantle.EngineMantle;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.SeedManager;
import art.arcane.iris.generation.terrain.InferredType;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.volmlib.util.hunk.Hunk;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.BeforeClass;
import org.junit.AfterClass;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class IrisDecoratorCaveContextTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

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
    @SuppressWarnings("unchecked")
    public void explicitCaveContextSkipsFluidWithoutMutatingBiome() {
        Engine engine = mock(Engine.class);
        SeedManager seedManager = mock(SeedManager.class);
        doReturn(seedManager).when(engine).getSeedManager();
        doReturn(mock(IrisData.class)).when(engine).getData();
        IrisDimension dimension = mock(IrisDimension.class);
        doReturn(63).when(dimension).getFluidHeight();
        doReturn(dimension).when(engine).getDimension();
        IrisComplex complex = mock(IrisComplex.class);
        EngineMantle mantle = mock(EngineMantle.class);
        doReturn(complex).when(engine).getComplex();
        doReturn(mantle).when(engine).getMantle();
        doReturn(63).when(mantle).getFluidHeight(anyInt(), anyInt());

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

    @Test
    @SuppressWarnings("unchecked")
    public void underwaterSurfaceDecorationRequiresActualFluidAboveTheTerrain() {
        Hunk<PlatformBlockState> output = mock(Hunk.class);
        PlatformBlockState dry = mock(PlatformBlockState.class);
        PlatformBlockState fluid = mock(PlatformBlockState.class);
        doReturn(128).when(output).getHeight();
        doReturn(dry).when(output).get(0, 11, 0);
        doReturn(false).when(dry).isFluid();

        assertFalse(IrisSurfaceDecorator.hasFluidAbove(output, 0, 10, 0));

        doReturn(fluid).when(output).get(0, 11, 0);
        doReturn(true).when(fluid).isFluid();

        assertTrue(IrisSurfaceDecorator.hasFluidAbove(output, 0, 10, 0));
    }

    @Test
    public void aquaticPlacementClassificationCoversWaterPlantsCoralAndWaterloggedStates() {
        assertTrue(IrisSurfaceDecorator.isAquaticPlacement(state("minecraft:seagrass")));
        assertTrue(IrisSurfaceDecorator.isAquaticPlacement(state("minecraft:tall_seagrass[half=lower]")));
        assertTrue(IrisSurfaceDecorator.isAquaticPlacement(state("minecraft:kelp")));
        assertTrue(IrisSurfaceDecorator.isAquaticPlacement(state("minecraft:kelp_plant")));
        assertTrue(IrisSurfaceDecorator.isAquaticPlacement(state("minecraft:sea_pickle[pickles=2]")));
        assertTrue(IrisSurfaceDecorator.isAquaticPlacement(state("minecraft:fire_coral_fan")));

        PlatformBlockState waterlogged = state("minecraft:oak_fence[waterlogged=true]");
        doReturn(true).when(waterlogged).isWaterLogged();
        assertTrue(IrisSurfaceDecorator.isAquaticPlacement(waterlogged));

        assertFalse(IrisSurfaceDecorator.isAquaticPlacement(state("minecraft:stone")));
        assertFalse(IrisSurfaceDecorator.isAquaticPlacement(state("minecraft:dead_fire_coral_fan")));
    }

    @Test
    public void compatibleWaterMustBeHorizontallyConnected() {
        Hunk<PlatformBlockState> output = Hunk.newArrayHunk(3, 3, 1);
        PlatformBlockState centerWater = waterState();
        PlatformBlockState neighborWater = waterState();
        PlatformBlockState lava = fluidState();
        output.set(1, 1, 0, centerWater);

        assertFalse(IrisSurfaceDecorator.hasConnectedWater(output, 1, 1, 0));

        output.set(0, 1, 0, lava);
        assertFalse(IrisSurfaceDecorator.hasConnectedWater(output, 1, 1, 0));

        output.set(0, 1, 0, neighborWater);
        assertTrue(IrisSurfaceDecorator.hasConnectedWater(output, 1, 1, 0));
    }

    @Test
    public void aquaticPlacementAtFluidHeightBoundaryRestoresDryBank() {
        IrisData data = mock(IrisData.class);
        PlatformBlockState air = state("minecraft:air");
        PlatformBlockState seagrass = state("minecraft:seagrass");
        IrisDecorator decorator = aquaticDecorator(data, seagrass, false, 1);
        Hunk<PlatformBlockState> output = Hunk.newArrayHunk(3, 4, 1);
        output.set(1, 1, 0, air);

        IrisSurfaceDecorator.AquaticPlacementSnapshot snapshot = IrisSurfaceDecorator.captureAquaticPlacement(
                decorator, data, output, 1, 0, 0, 4);
        output.set(1, 1, 0, seagrass);
        snapshot.restoreIfUnsupported(output, 1, 0);

        assertSame(air, output.get(1, 1, 0));
    }

    @Test
    public void legitimateUnderwaterAquaticPlacementIsPreserved() {
        IrisData data = mock(IrisData.class);
        PlatformBlockState sourceWater = waterState();
        PlatformBlockState adjacentWater = waterState();
        PlatformBlockState kelp = state("minecraft:kelp_plant");
        IrisDecorator decorator = aquaticDecorator(data, kelp, true, 1);
        Hunk<PlatformBlockState> output = Hunk.newArrayHunk(3, 4, 1);
        output.set(1, 1, 0, sourceWater);
        output.set(0, 1, 0, adjacentWater);

        IrisSurfaceDecorator.AquaticPlacementSnapshot snapshot = IrisSurfaceDecorator.captureAquaticPlacement(
                decorator, data, output, 1, 0, 0, 4);
        output.set(1, 1, 0, kelp);
        snapshot.restoreIfUnsupported(output, 1, 0);

        assertSame(kelp, output.get(1, 1, 0));
    }

    @Test
    public void unsupportedUpperStackRestoresTheWholeAquaticPlacement() {
        IrisData data = mock(IrisData.class);
        PlatformBlockState lowerWater = waterState();
        PlatformBlockState upperWater = waterState();
        PlatformBlockState adjacentWater = waterState();
        PlatformBlockState kelp = state("minecraft:kelp_plant");
        IrisDecorator decorator = aquaticDecorator(data, kelp, true, 2);
        Hunk<PlatformBlockState> output = Hunk.newArrayHunk(3, 4, 1);
        output.set(1, 1, 0, lowerWater);
        output.set(1, 2, 0, upperWater);
        output.set(0, 1, 0, adjacentWater);

        IrisSurfaceDecorator.AquaticPlacementSnapshot snapshot = IrisSurfaceDecorator.captureAquaticPlacement(
                decorator, data, output, 1, 0, 0, 4);
        output.set(1, 1, 0, kelp);
        output.set(1, 2, 0, kelp);
        snapshot.restoreIfUnsupported(output, 1, 0);

        assertSame(lowerWater, output.get(1, 1, 0));
        assertSame(upperWater, output.get(1, 2, 0));
    }

    @Test
    public void dryWaterloggedAndTwoBlockAquaticPlacementsAreRestoredAtomically() {
        IrisData data = mock(IrisData.class);
        PlatformBlockState lowerAir = state("minecraft:air");
        PlatformBlockState upperAir = state("minecraft:air");
        PlatformBlockState lower = state("minecraft:tall_seagrass[half=lower]");
        PlatformBlockState upper = state("minecraft:tall_seagrass[half=upper]");
        IrisDecorator decorator = aquaticDecorator(data, lower, false, 1);
        Hunk<PlatformBlockState> output = Hunk.newArrayHunk(3, 4, 1);
        output.set(1, 1, 0, lowerAir);
        output.set(1, 2, 0, upperAir);

        IrisSurfaceDecorator.AquaticPlacementSnapshot snapshot = IrisSurfaceDecorator.captureAquaticPlacement(
                decorator, data, output, 1, 0, 0, 4);
        output.set(1, 1, 0, lower);
        output.set(1, 2, 0, upper);
        snapshot.restoreIfUnsupported(output, 1, 0);

        assertSame(lowerAir, output.get(1, 1, 0));
        assertSame(upperAir, output.get(1, 2, 0));

        PlatformBlockState dryWaterlogged = state("minecraft:mangrove_roots[waterlogged=true]");
        doReturn(true).when(dryWaterlogged).isWaterLogged();
        IrisDecorator waterloggedDecorator = aquaticDecorator(data, dryWaterlogged, false, 1);
        IrisSurfaceDecorator.AquaticPlacementSnapshot waterloggedSnapshot = IrisSurfaceDecorator.captureAquaticPlacement(
                waterloggedDecorator, data, output, 1, 0, 0, 4);
        output.set(1, 1, 0, dryWaterlogged);
        waterloggedSnapshot.restoreIfUnsupported(output, 1, 0);

        assertSame(lowerAir, output.get(1, 1, 0));
    }

    private IrisDecorator aquaticDecorator(
            IrisData data,
            PlatformBlockState aquatic,
            boolean stacking,
            int stackMaximum
    ) {
        IrisDecorator decorator = mock(IrisDecorator.class);
        doReturn(new PlatformBlockState[]{aquatic}).when(decorator).getBlockDataArray(data);
        doReturn(new PlatformBlockState[0]).when(decorator).getBlockDataTopsArray(data);
        doReturn(stacking).when(decorator).isStacking();
        doReturn(stackMaximum).when(decorator).getStackMax();
        return decorator;
    }

    private PlatformBlockState state(String key) {
        PlatformBlockState state = mock(PlatformBlockState.class);
        doReturn(key).when(state).key();
        return state;
    }

    private PlatformBlockState waterState() {
        PlatformBlockState water = fluidState();
        doReturn(true).when(water).isWater();
        return water;
    }

    private PlatformBlockState fluidState() {
        PlatformBlockState fluid = state("minecraft:lava");
        doReturn(true).when(fluid).isFluid();
        return fluid;
    }
}
