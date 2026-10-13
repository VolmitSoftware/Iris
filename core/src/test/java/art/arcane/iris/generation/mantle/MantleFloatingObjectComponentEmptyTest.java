package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.biome.FloatingIslandBoundarySampler;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.biome.IrisFloatingChildBiomes;
import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.SeedManager;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.structure.object.IrisObjectPlacement;
import art.arcane.iris.testsupport.PlatformBinding;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import art.arcane.volmlib.util.mantle.flag.ReservedFlag;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.mantle.runtime.MantleDataAdapter;
import art.arcane.volmlib.util.mantle.runtime.MantleHooks;
import art.arcane.volmlib.util.matter.Matter;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class MantleFloatingObjectComponentEmptyTest {
    @ClassRule
    public static final PlatformBinding PLATFORM = PlatformBinding.mockPlatform();

    @Test
    public void absentFloatingObjectsDoNotReadColumnsOrWriteMatter() {
        Fixture fixture = new Fixture(new IrisBiome());
        MantleWriter writer = mock(MantleWriter.class);
        ChunkContext context = mock(ChunkContext.class);

        assertEquals(0, fixture.component.getRadius());
        fixture.component.generateLayer(writer, 4, -6, context);

        verifyNoInteractions(writer, context);
    }

    @Test
    public void floatingTerrainWithoutObjectsHasNoObjectReach() {
        Fixture fixture = new Fixture(floatingBiome(new IrisFloatingChildBiomes()));

        assertEquals(0, fixture.component.getRadius());
    }

    @Test
    public void explicitPlacementListsKeepTheirMinimumReachWhenObjectsAreMissing() {
        IrisObjectPlacement placement = new IrisObjectPlacement().setPlace(new KList<>("test/missing"));
        List<IrisFloatingChildBiomes> entries = List.of(
                new IrisFloatingChildBiomes().setFloatingObjects(new KList<>(placement)),
                new IrisFloatingChildBiomes().setExtraObjects(new KList<>(placement)),
                new IrisFloatingChildBiomes().setTopObjectOverrides(new KList<>(placement)),
                new IrisFloatingChildBiomes().setBottomObjectOverrides(new KList<>(placement))
        );

        for (IrisFloatingChildBiomes entry : entries) {
            Fixture fixture = new Fixture(floatingBiome(entry));
            assertEquals(16, fixture.component.getRadius());
        }
    }

    @Test
    public void inheritedParentObjectsKeepTheirMinimumReach() {
        IrisBiome parent = floatingBiome(new IrisFloatingChildBiomes())
                .setObjects(new KList<>(new IrisObjectPlacement()));
        Fixture fixture = new Fixture(parent);

        assertEquals(16, fixture.component.getRadius());
    }

    @Test
    public void inheritedTargetObjectsKeepTheirMinimumReach() {
        IrisFloatingChildBiomes entry = new IrisFloatingChildBiomes().setBiome("test/target");
        Fixture fixture = new Fixture(floatingBiome(entry));
        IrisBiome target = new IrisBiome().setObjects(new KList<>(new IrisObjectPlacement()));
        when(fixture.biomes.load("test/target")).thenReturn(target);

        assertEquals(16, fixture.component.getRadius());
    }

    @Test
    public void disabledInheritanceLeavesObjectlessFloatingTerrainEmpty() {
        IrisBiome parent = floatingBiome(new IrisFloatingChildBiomes().setInheritObjects(false))
                .setObjects(new KList<>(new IrisObjectPlacement()));
        Fixture fixture = new Fixture(parent);

        assertEquals(0, fixture.component.getRadius());
    }

    @Test
    public void hotloadRechecksObjectsAcrossPackChanges() {
        Fixture fixture = new Fixture(new IrisBiome());
        assertEquals(0, fixture.component.getRadius());

        IrisBiome active = floatingBiome(new IrisFloatingChildBiomes()
                .setExtraObjects(new KList<>(new IrisObjectPlacement())));
        when(fixture.dimension.getReachableBiomes(any())).thenReturn(new KList<>(active));
        fixture.component.hotload();
        assertEquals(16, fixture.component.getRadius());

        when(fixture.dimension.getReachableBiomes(any())).thenReturn(new KList<>(new IrisBiome()));
        fixture.component.hotload();
        assertEquals(0, fixture.component.getRadius());
        assertEquals(0, fixture.component.getRadius());
        verify(fixture.dimension, times(3)).getReachableBiomes(any());
    }

    @Test
    public void failedReachabilityScanKeepsTheComponentActive() {
        Fixture fixture = new Fixture(new IrisBiome());
        when(fixture.dimension.getReachableBiomes(any())).thenThrow(new IllegalStateException("Unavailable biome"));

        assertEquals(16, fixture.component.getRadius());
    }

    @Test
    public void failedTargetResolutionKeepsTheComponentActive() {
        IrisFloatingChildBiomes entry = spy(new IrisFloatingChildBiomes());
        Fixture fixture = new Fixture(floatingBiome(entry));
        doThrow(new IllegalStateException("Unavailable floating biome")).when(entry).getRealBiome(any(), any());

        assertEquals(16, fixture.component.getRadius());
    }

    @Test
    public void unresolvedTargetKeepsTheComponentActive() {
        IrisFloatingChildBiomes entry = spy(new IrisFloatingChildBiomes());
        Fixture fixture = new Fixture(floatingBiome(entry));
        doReturn(null).when(entry).getRealBiome(any(), any());

        assertEquals(16, fixture.component.getRadius());
    }

    @Test
    public void unresolvedPlacementListKeepsTheComponentActive() {
        Fixture fixture = new Fixture(floatingBiome(new IrisFloatingChildBiomes().setExtraObjects(null)));

        assertEquals(16, fixture.component.getRadius());
    }

    @Test
    public void emptyComponentShrinksTheWindowAndPreservesCompletionFlags() {
        Fixture compact = new Fixture(new IrisBiome());
        Fixture conservative = new Fixture(new IrisBiome());
        MantleFloatingObjectComponent conservativeComponent = new ConservativeFloatingObjects(conservative.engineMantle);

        GenerationResult compactResult = compact.generate(compact.component);
        GenerationResult conservativeResult = conservative.generate(conservativeComponent);

        assertEquals(9, compactResult.carveCalls());
        assertEquals(25, conservativeResult.carveCalls());
        assertEquals(0, compact.columnReads.get());
        assertEquals(2304, conservative.columnReads.get());
        assertEquals(9, compactResult.loadedChunks());
        assertEquals(25, conservativeResult.loadedChunks());
        for (MantleFlag flag : List.of(ReservedFlag.CARVED, ReservedFlag.FLOATING_OBJECT, MantleFlag.PLANNED)) {
            assertTrue(compactResult.center().isFlagged(flag));
            assertEquals(conservativeResult.center().isFlagged(flag), compactResult.center().isFlagged(flag));
        }
    }

    private static IrisBiome floatingBiome(IrisFloatingChildBiomes entry) {
        return new IrisBiome().setFloatingChildBiomes(new KList<>(entry));
    }

    private static final class Fixture {
        private final IrisDimension dimension = mock(IrisDimension.class);
        private final Engine engine = mock(Engine.class);
        private final EngineMantle engineMantle = mock(EngineMantle.class);
        private final IrisComplex complex = mock(IrisComplex.class);
        private final ChunkContext context = mock(ChunkContext.class);
        private final ResourceLoader<IrisBiome> biomes;
        private final AtomicInteger columnReads = new AtomicInteger();
        private final MantleFloatingObjectComponent component;

        @SuppressWarnings("unchecked")
        private Fixture(IrisBiome biome) {
            biomes = mock(ResourceLoader.class);
            ResourceLoader<IrisObject> objects = mock(ResourceLoader.class);
            IrisData data = mock(IrisData.class);
            when(data.getBiomeLoader()).thenReturn(biomes);
            when(data.getObjectLoader()).thenReturn(objects);
            when(dimension.isUseMantle()).thenReturn(true);
            when(dimension.getReachableBiomes(any())).thenReturn(new KList<>(biome));
            when(engine.getDimension()).thenReturn(dimension);
            when(engine.getHeight()).thenReturn(256);
            when(engine.getSeedManager()).thenReturn(mock(SeedManager.class));
            when(engine.getMantle()).thenReturn(engineMantle);
            when(engineMantle.getEngine()).thenReturn(engine);
            when(engineMantle.getData()).thenReturn(data);
            when(engineMantle.getComplex()).thenReturn(complex);
            when(complex.allowsNewGenerationChunk(anyInt(), anyInt())).thenReturn(true);
            when(complex.allowsMantleChunkWrite(anyInt(), anyInt())).thenReturn(true);
            when(context.getComplex()).thenReturn(complex);
            when(context.getFloatingIslandBoundarySampler()).thenReturn(new FloatingIslandBoundarySampler((x, z) -> {
                columnReads.incrementAndGet();
                return biome;
            }));
            component = new MantleFloatingObjectComponent(engineMantle);
        }

        @SuppressWarnings("unchecked")
        private GenerationResult generate(MantleFloatingObjectComponent floating) {
            Map<Long, MantleChunk<Matter>> chunks = new HashMap<>();
            MantleDataAdapter<Matter> adapter = mock(MantleDataAdapter.class);
            Mantle<Matter> mantle = mock(Mantle.class);
            when(mantle.useChunk(anyInt(), anyInt())).thenAnswer(invocation -> {
                int x = invocation.getArgument(0);
                int z = invocation.getArgument(1);
                long key = ((long) x << 32) ^ (z & 0xffffffffL);
                return chunks.computeIfAbsent(key, ignored -> new MantleChunk<>(16, x, z, adapter, MantleHooks.NONE)).use();
            });
            MantleComponent carving = mock(MantleComponent.class);
            when(carving.isEnabled()).thenReturn(true);
            when(carving.getFlag()).thenReturn(ReservedFlag.CARVED);
            when(carving.getOutputRadius()).thenReturn(1);
            when(carving.getGenerationPhase()).thenReturn(MatterGenerationPhase.TERRAIN);
            when(carving.getPrerequisiteFlags()).thenReturn(MantleComponent.EMPTY_PREREQUISITES);
            AtomicInteger carveCalls = new AtomicInteger();
            doAnswer(invocation -> {
                carveCalls.incrementAndGet();
                return null;
            }).when(carving).generateLayer(any(), anyInt(), anyInt(), any());
            MatterGenerator generator = mock(MatterGenerator.class, CALLS_REAL_METHODS);
            when(generator.getEngine()).thenReturn(engine);
            when(generator.getMantle()).thenReturn(mantle);
            when(generator.getComponents()).thenReturn(List.of(
                    new MantlePass(List.of(carving), 0, 0),
                    new MantlePass(List.of(floating), 0, 0)
            ));

            generator.generateMatter(0, 0, false, context);

            return new GenerationResult(chunks.get(0L), carveCalls.get(), chunks.size());
        }
    }

    private static final class ConservativeFloatingObjects extends MantleFloatingObjectComponent {
        private ConservativeFloatingObjects(EngineMantle engineMantle) {
            super(engineMantle);
        }

        @Override
        protected int computeRadius() {
            return Math.max(16, super.computeRadius());
        }
    }

    private record GenerationResult(MantleChunk<Matter> center, int carveCalls, int loadedChunks) {
    }
}
