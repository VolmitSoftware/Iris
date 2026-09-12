package art.arcane.iris.world.history;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GenerationFindCatalogTest {
    @Test
    public void findsRecordedNamesWithoutLoadingArchivedPacksOrRuntimes() throws Exception {
        Fixture fixture = new Fixture();
        assertEquals("meadow", GenerationFindCatalog.biome(fixture.engine, "MEADOW").getLoadKey());
        assertEquals("old-region", GenerationFindCatalog.region(fixture.engine, "old-region").getLoadKey());
        assertTrue(GenerationFindCatalog.hasObjectPlacement(fixture.engine, "/OLD-TREE.iob"));
        assertTrue(GenerationFindCatalog.hasRetainedStructurePlacement(fixture.engine, "old-house"));
        assertTrue(GenerationFindCatalog.objectKeys(fixture.engine).contains("new-tree"));
        assertFalse(GenerationFindCatalog.hasObjectPlacement(fixture.engine, "unplaced"));
        assertNull(GenerationFindCatalog.biome(fixture.engine, "unrecorded"));
        verify(fixture.history, times(1)).forEachRecordedSemantic(any());
        verify(fixture.history, never()).packRoot(anyLong());
        verify(fixture.engine, never()).getActiveGenerationRuntimeBinding();
    }

    @Test
    public void retainedPackKeysKeepCaseAndDistinctCaseVariants() throws Exception {
        Fixture fixture = new Fixture();
        doAnswer(invocation -> {
            GenerationSemanticIndex.RecordConsumer consumer = invocation.getArgument(0);
            consumer.accept(ChunkGenerationSemantics.builder(0, 0, 1L)
                    .addSurfaceBiome("mountain/Cute_Cliffs+")
                    .addSurfaceBiome("mountain/cute_cliffs+")
                    .addRegion("Regions/Highlands")
                    .addObject("trees/mixed/AmySmol10")
                    .addObject("trees/mixed/amysmol10")
                    .addStructure("Structures/Tower+", 8, 70, 8)
                    .seal().build());
            return null;
        }).when(fixture.history).forEachRecordedSemantic(any());

        assertEquals("mountain/Cute_Cliffs+",
                GenerationFindCatalog.biome(fixture.engine, "mountain/Cute_Cliffs+").getLoadKey());
        assertEquals("mountain/cute_cliffs+",
                GenerationFindCatalog.biome(fixture.engine, "mountain/cute_cliffs+").getLoadKey());
        assertEquals(2, GenerationFindCatalog.biomes(fixture.engine).size());
        assertEquals("Regions/Highlands",
                GenerationFindCatalog.region(fixture.engine, "Regions/Highlands").getLoadKey());
        assertTrue(GenerationFindCatalog.objectKeys(fixture.engine).contains("trees/mixed/AmySmol10"));
        assertTrue(GenerationFindCatalog.objectKeys(fixture.engine).contains("trees/mixed/amysmol10"));
        assertTrue(GenerationFindCatalog.hasObjectPlacement(fixture.engine, "trees/mixed/AmySmol10"));
        assertTrue(GenerationFindCatalog.retainedStructureKeys(fixture.engine).contains("Structures/Tower+"));
    }

    @Test
    public void activeDefinitionsWinRecordedKeyCollisions() throws Exception {
        Fixture fixture = new Fixture();
        IrisBiome active = new IrisBiome().setName("New Meadow");
        active.setLoadKey("meadow");
        when(fixture.engine.getAllBiomes()).thenReturn(new KList<>(active));
        assertSame(active, GenerationFindCatalog.biome(fixture.engine, "meadow"));
        assertEquals(1, GenerationFindCatalog.biomes(fixture.engine).size());
    }

    @Test
    public void doesNotTreatUnsealedOrCurrentClaimsAsHistoricalDefinitions() throws Exception {
        Fixture fixture = new Fixture();
        doAnswer(invocation -> {
            GenerationSemanticIndex.RecordConsumer consumer = invocation.getArgument(0);
            consumer.accept(ChunkGenerationSemantics.builder(0, 0, 1L).addSurfaceBiome("unsealed").build());
            consumer.accept(ChunkGenerationSemantics.builder(1, 0, 2L).addSurfaceBiome("current").seal().build());
            return null;
        }).when(fixture.history).forEachRecordedSemantic(any());
        assertNull(GenerationFindCatalog.biome(fixture.engine, "unsealed"));
        assertNull(GenerationFindCatalog.biome(fixture.engine, "current"));
    }

    @Test
    public void reportsUnreadableRecordedSemantics() throws Exception {
        Fixture fixture = new Fixture();
        IOException failure = new IOException("unreadable semantic index");
        doThrow(failure).when(fixture.history).forEachRecordedSemantic(any());
        assertSame(failure, assertThrows(UncheckedIOException.class,
                () -> GenerationFindCatalog.biomes(fixture.engine)).getCause());
    }

    private static final class Fixture {
        private final IrisEngine engine = mock(IrisEngine.class);
        private final GenerationHistory history = mock(GenerationHistory.class);

        @SuppressWarnings("unchecked")
        private Fixture() throws IOException {
            GenerationHistoryRuntimeRouter router = mock(GenerationHistoryRuntimeRouter.class);
            GenerationManifest manifest = mock(GenerationManifest.class);
            GenerationActivation active = mock(GenerationActivation.class);
            when(engine.getGenerationHistoryRuntimeRouter()).thenReturn(Optional.of(router));
            when(router.history()).thenReturn(history);
            when(history.manifest()).thenReturn(manifest);
            when(manifest.activeActivation()).thenReturn(active);
            when(active.activationId()).thenReturn(2L);
            IrisData data = mock(IrisData.class);
            ResourceLoader<IrisObject> objects = mock(ResourceLoader.class);
            when(engine.getData()).thenReturn(data);
            when(data.getObjectLoader()).thenReturn(objects);
            when(objects.getPossibleKeys()).thenReturn(new String[]{"new-tree"});
            IrisDimension dimension = mock(IrisDimension.class);
            when(engine.getDimension()).thenReturn(dimension);
            when(engine.getAllBiomes()).thenReturn(new KList<>());
            when(dimension.getAllRegions(engine)).thenReturn(new KList<IrisRegion>());
            doAnswer(invocation -> {
                GenerationSemanticIndex.RecordConsumer consumer = invocation.getArgument(0);
                consumer.accept(ChunkGenerationSemantics.builder(0, 0, 1L).addSurfaceBiome("meadow")
                        .addRegion("old-region").addObject("old-tree").addStructure("old-house", 8, 70, 8)
                        .seal().build());
                return null;
            }).when(history).forEachRecordedSemantic(any());
        }
    }
}
