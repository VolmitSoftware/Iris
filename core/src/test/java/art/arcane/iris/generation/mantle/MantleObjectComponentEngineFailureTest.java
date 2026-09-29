package art.arcane.iris.generation.mantle;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.cave.CarvingMode;
import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.GenerationClosedException;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.SeedManager;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.structure.object.IObjectPlacer;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.structure.object.IrisObjectPlacement;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.stream.ProceduralStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MantleObjectComponentEngineFailureTest {
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
    public void closedHistoryRouterDuringPlacementFailsTheChunkAndCachesNoPlan() {
        GenerationClosedException closed = new GenerationClosedException("Generation-history runtime router is closed.");
        Fixture fixture = new Fixture(closed);

        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                fixture.generate();
                fail("object placement swallowed a closed generation-history router");
            } catch (GenerationClosedException failure) {
                assertSame(closed, failure);
            }
            assertEquals(attempt, fixture.placements.get());
        }
    }

    @Test
    public void objectSpecificFailureSkipsOnlyThatObject() {
        Fixture fixture = new Fixture(new IllegalArgumentException("malformed object"));

        fixture.generate();

        assertEquals(1, fixture.placements.get());
    }

    private static final class Fixture {
        private final AtomicInteger placements = new AtomicInteger();
        private final MantleObjectComponent component;
        private final MantleWriter writer;
        private final ChunkContext context;

        @SuppressWarnings("unchecked")
        private Fixture(RuntimeException placementFailure) {
            IrisDimension dimension = mock(IrisDimension.class);
            when(dimension.getAllRegions(any())).thenReturn(new KList<>());
            when(dimension.getReachableBiomes(any())).thenReturn(new KList<>());
            SeedManager seeds = mock(SeedManager.class);
            Engine engine = mock(Engine.class);
            when(engine.getDimension()).thenReturn(dimension);
            when(engine.getSeedManager()).thenReturn(seeds);
            EngineMantle engineMantle = mock(EngineMantle.class);
            IrisData data = mock(IrisData.class);
            when(engineMantle.getEngine()).thenReturn(engine);
            when(engineMantle.getData()).thenReturn(data);

            IrisObject object = mock(IrisObject.class);
            when(object.getLoadKey()).thenReturn("trees/closed-router");
            when(object.getW()).thenReturn(1);
            when(object.getH()).thenReturn(1);
            when(object.getD()).thenReturn(1);
            doAnswer((InvocationOnMock invocation) -> {
                placements.incrementAndGet();
                throw placementFailure;
            }).when(object).place(anyInt(), anyInt(), anyInt(), any(IObjectPlacer.class), any(IrisObjectPlacement.class),
                    any(RNG.class), any(), any(), any());

            IrisComplex complex = mock(IrisComplex.class);
            IrisObjectPlacement placement = mock(IrisObjectPlacement.class);
            when(placement.getChance()).thenReturn(2D);
            when(placement.getCarvingSupport()).thenReturn(CarvingMode.SURFACE_ONLY);
            when(placement.getPlace()).thenReturn(new KList<>("trees/closed-router"));
            when(placement.getAllowedCollisions()).thenReturn(new KList<>());
            when(placement.getForbiddenCollisions()).thenReturn(new KList<>());
            when(placement.getDensity(any(RNG.class), anyDouble(), anyDouble(), any())).thenReturn(1);
            when(placement.getObject(any(), any(RNG.class))).thenReturn(object);
            when(placement.scaleObject(any(RNG.class), any(), any())).thenReturn(object);

            IrisBiome biome = new IrisBiome();
            biome.setLoadKey("closed-router-biome");
            biome.setObjects(new KList<>(placement));
            IrisRegion region = new IrisRegion();
            region.setLoadKey("closed-router-region");

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
            context = mock(ChunkContext.class);
            when(context.getComplex()).thenReturn(complex);

            Mantle<Matter> mantle = mock(Mantle.class);
            when(mantle.getWorldHeight()).thenReturn(256);
            writer = mock(MantleWriter.class);
            when(writer.getMantle()).thenReturn(mantle);
            when(writer.getEngine()).thenReturn(engine);
            doAnswer((InvocationOnMock invocation) -> {
                Runnable task = invocation.getArgument(2);
                task.run();
                return null;
            }).when(writer).withChunkFence(anyInt(), anyInt(), any(Runnable.class));
            component = new MantleObjectComponent(engineMantle);
        }

        private void generate() {
            component.generateLayer(writer, 0, 0, context);
        }
    }
}
