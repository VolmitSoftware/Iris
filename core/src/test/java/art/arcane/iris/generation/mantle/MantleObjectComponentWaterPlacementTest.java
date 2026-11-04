package art.arcane.iris.generation.mantle;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.cave.CarvingMode;
import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.iris.generation.decoration.IrisProceduralObjects;
import art.arcane.iris.generation.decoration.IrisProceduralPlacement;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.SeedManager;
import art.arcane.iris.generation.runtime.UpperDimensionContext;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.structure.object.IObjectPlacer;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.structure.object.IrisObjectPlacement;
import art.arcane.iris.structure.object.ObjectWaterRule;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.volmlib.util.stream.ProceduralStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.stubbing.Answer;

import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MantleObjectComponentWaterPlacementTest {
    private static final Answer<Integer> PLACES = call -> 64;
    private static final Answer<Integer> REJECTS = call -> -1;
    private static final Answer<Integer> THROWS = call -> {
        throw new IllegalArgumentException("malformed object");
    };

    private IrisSettings previousSettings;

    @Before
    public void bindSettings() {
        previousSettings = IrisSettings.settings;
        IrisSettings.settings = new IrisSettings();
    }

    @After
    public void restoreSettings() {
        IrisSettings.settings = previousSettings;
    }

    @Test
    public void acceptedSurfacePlacementIsRecordedThenEnded() {
        Fixture fixture = new Fixture(PLACES);
        fixture.biome.setObjects(new KList<>(fixture.surfacePlacement()));

        fixture.generate();

        fixture.assertRecordedThenEnded();
    }

    @Test
    public void rejectedSurfacePlacementEndsWithoutBeingRecorded() {
        Fixture fixture = new Fixture(REJECTS);
        fixture.biome.setObjects(new KList<>(fixture.surfacePlacement()));

        fixture.generate();

        fixture.assertEndedUnrecorded();
    }

    @Test
    public void thrownSurfacePlacementEndsWithoutBeingRecorded() {
        Fixture fixture = new Fixture(THROWS);
        fixture.biome.setObjects(new KList<>(fixture.surfacePlacement()));

        fixture.generate();

        fixture.assertEndedUnrecorded();
    }

    @Test
    public void rejectedCavePlacementEndsWithoutBeingRecorded() {
        Fixture fixture = new Fixture(REJECTS);
        fixture.biome.setObjects(new KList<>(fixture.cavePlacement()));

        fixture.generate();

        fixture.assertEndedUnrecorded();
    }

    @Test
    public void thrownCavePlacementEndsWithoutBeingRecorded() {
        Fixture fixture = new Fixture(THROWS);
        fixture.biome.setObjects(new KList<>(fixture.cavePlacement()));

        fixture.generate();

        fixture.assertEndedUnrecorded();
    }

    @Test
    public void proceduralPlacementOutsideTheTransitionFootprintEndsWithoutBeingRecorded() {
        Fixture fixture = new Fixture(PLACES);
        fixture.biome.setProceduralObjects(fixture.proceduralObjects());
        when(fixture.complex.allowsNewGenerationFootprint(anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(false);

        fixture.generate();

        fixture.assertEndedUnrecorded();
    }

    @Test
    public void rejectedProceduralPlacementEndsWithoutBeingRecorded() {
        Fixture fixture = new Fixture(REJECTS);
        fixture.biome.setProceduralObjects(fixture.proceduralObjects());

        fixture.generate();

        fixture.assertEndedUnrecorded();
    }

    @Test
    public void thrownProceduralPlacementEndsWithoutBeingRecorded() {
        Fixture fixture = new Fixture(THROWS);
        fixture.biome.setProceduralObjects(fixture.proceduralObjects());

        fixture.generate();

        fixture.assertEndedUnrecorded();
    }

    @Test
    public void rejectedUpperPlacementEndsWithoutBeingRecorded() {
        Fixture fixture = new Fixture(REJECTS);
        fixture.upperObjects();

        fixture.generate();

        fixture.assertEndedUnrecorded();
    }

    @Test
    public void thrownUpperPlacementEndsWithoutBeingRecorded() {
        Fixture fixture = new Fixture(THROWS);
        fixture.upperObjects();

        fixture.generate();

        fixture.assertEndedUnrecorded();
    }

    private static final class Fixture {
        private final IrisBiome biome = new IrisBiome();
        private final IrisComplex complex = mock(IrisComplex.class);
        private final IrisObject object = mock(IrisObject.class);
        private final Engine engine = mock(Engine.class);
        private final IrisDimension dimension = mock(IrisDimension.class);
        private final ObjectPassPlacer placer = mock(ObjectPassPlacer.class);
        private final ChunkContext context = mock(ChunkContext.class);
        private final MantleObjectComponent component;

        @SuppressWarnings("unchecked")
        private Fixture(Answer<Integer> placement) {
            when(dimension.getAllRegions(any())).thenReturn(new KList<>());
            when(dimension.getReachableBiomes(any())).thenReturn(new KList<>());
            when(engine.getDimension()).thenReturn(dimension);
            when(engine.getSeedManager()).thenReturn(mock(SeedManager.class));
            when(engine.getHeight()).thenReturn(256);
            when(engine.getComplex()).thenReturn(complex);
            EngineMantle engineMantle = mock(EngineMantle.class);
            when(engineMantle.getEngine()).thenReturn(engine);
            when(engineMantle.getData()).thenReturn(mock(IrisData.class));
            when(engineMantle.getComplex()).thenReturn(complex);
            when(engineMantle.trueHeight(anyInt(), anyInt())).thenReturn(120);

            when(object.getLoadKey()).thenReturn("trees/water");
            when(object.getW()).thenReturn(1);
            when(object.getH()).thenReturn(1);
            when(object.getD()).thenReturn(1);
            doAnswer(placement).when(object).place(anyInt(), anyInt(), any(IObjectPlacer.class), any(IrisObjectPlacement.class),
                    any(RNG.class), any(), any(), any());
            doAnswer(placement).when(object).place(anyInt(), anyInt(), anyInt(), any(IObjectPlacer.class), any(IrisObjectPlacement.class),
                    any(RNG.class), any(), any(), any());

            biome.setLoadKey("water-biome");
            IrisRegion region = new IrisRegion();
            region.setLoadKey("water-region");
            ProceduralStream<IrisRegion> regions = mock(ProceduralStream.class);
            when(regions.get(anyDouble(), anyDouble())).thenReturn(region);
            ProceduralStream<IrisBiome> biomes = mock(ProceduralStream.class);
            when(biomes.get(anyDouble(), anyDouble())).thenReturn(biome);
            ProceduralStream<Integer> heights = mock(ProceduralStream.class);
            when(heights.get(anyDouble(), anyDouble())).thenReturn(64);
            when(complex.allowsNewGenerationChunk(anyInt(), anyInt())).thenReturn(true);
            when(complex.allowsNewGenerationFootprint(anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(true);
            when(complex.getRegionStream()).thenReturn(regions);
            when(complex.getTrueBiomeStream()).thenReturn(biomes);
            when(complex.getRoundedHeighteightStream()).thenReturn(heights);
            when(context.getComplex()).thenReturn(complex);
            when(placer.getEngine()).thenReturn(engine);
            when(placer.getCarvedColumn(anyInt(), anyInt(), anyInt())).thenAnswer(call -> {
                byte[] column = new byte[call.<Integer>getArgument(2)];
                Arrays.fill(column, 20, 28, (byte) 1);
                return column;
            });
            when(placer.getDataIfPresent(anyInt(), anyInt(), anyInt(), eq(MatterCavern.class)))
                    .thenReturn(new MatterCavern(true, "", (byte) 0));
            component = new MantleObjectComponent(engineMantle);
        }

        private IrisObjectPlacement surfacePlacement() {
            IrisObjectPlacement placement = placement(CarvingMode.SURFACE_ONLY);
            when(placement.getDensity(any(RNG.class), anyDouble(), anyDouble(), any())).thenReturn(1);
            when(placement.getObject(any(), any(RNG.class))).thenReturn(object);
            return placement;
        }

        private IrisObjectPlacement cavePlacement() {
            IrisObjectPlacement placement = placement(CarvingMode.CARVING_ONLY);
            when(placement.getDensity(any(RNG.class), anyDouble(), anyDouble(), any())).thenReturn(1);
            when(placement.getObject(any(), any(RNG.class))).thenReturn(object);
            return placement;
        }

        private IrisProceduralObjects proceduralObjects() {
            IrisObjectPlacement placement = placement(CarvingMode.SURFACE_ONLY);
            IrisProceduralPlacement procedural = mock(IrisProceduralPlacement.class);
            when(procedural.getChance()).thenReturn(2D);
            when(procedural.getName()).thenReturn("water-procedural");
            when(procedural.getDensity()).thenReturn(1);
            when(procedural.asPlacement()).thenReturn(placement);
            when(procedural.getVariantObject(any(), any(), any())).thenReturn(object);
            IrisProceduralObjects objects = mock(IrisProceduralObjects.class);
            when(objects.isEmpty()).thenReturn(false);
            when(objects.getAllPlacements()).thenReturn(new KList<>(procedural));
            return objects;
        }

        private void upperObjects() {
            IrisObjectPlacement forced = placement(CarvingMode.ANYWHERE);
            IrisObjectPlacement source = placement(CarvingMode.SURFACE_ONLY);
            when(source.getDensity(any(RNG.class), anyDouble(), anyDouble(), any())).thenReturn(1);
            when(source.getObject(any(UpperDimensionContext.class), any(RNG.class))).thenReturn(object);
            when(source.toPlacement(anyString())).thenReturn(forced);
            IrisBiome upperBiome = new IrisBiome();
            upperBiome.setLoadKey("upper-biome");
            upperBiome.setObjects(new KList<>(source));
            UpperDimensionContext upper = mock(UpperDimensionContext.class);
            when(upper.getUpperBiome(anyInt(), anyInt())).thenReturn(upperBiome);
            when(upper.getDimension()).thenReturn(dimension);
            when(upper.getEffectiveSurfaceY(anyInt(), anyInt())).thenReturn(64);
            when(engine.getUpperContext()).thenReturn(upper);
            when(dimension.isUpperDimensionObjects()).thenReturn(true);
        }

        private IrisObjectPlacement placement(CarvingMode carving) {
            IrisObjectPlacement placement = mock(IrisObjectPlacement.class);
            when(placement.getChance()).thenReturn(2D);
            when(placement.getCarvingSupport()).thenReturn(carving);
            when(placement.getPlace()).thenReturn(new KList<>("trees/water"));
            when(placement.getAllowedCollisions()).thenReturn(new KList<>());
            when(placement.getForbiddenCollisions()).thenReturn(new KList<>());
            when(placement.scaleObject(any(RNG.class), any(), any())).thenReturn(object);
            when(placement.waterRule()).thenReturn(ObjectWaterRule.ENTERS_WATER);
            return placement;
        }

        private void generate() {
            component.generateOrigin(placer, 0, 0, context);
        }

        private void assertEndedUnrecorded() {
            InOrder order = inOrder(placer);
            order.verify(placer).beginObjectPlacement(ObjectWaterRule.ENTERS_WATER);
            order.verify(placer).endObjectPlacement();
            verify(placer, never()).recordObjectPlacement(anyInt());
        }

        private void assertRecordedThenEnded() {
            InOrder order = inOrder(placer);
            order.verify(placer).beginObjectPlacement(ObjectWaterRule.ENTERS_WATER);
            order.verify(placer).recordObjectPlacement(anyInt());
            order.verify(placer).endObjectPlacement();
        }
    }
}
