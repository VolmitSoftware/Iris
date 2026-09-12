package art.arcane.iris.world.history;

import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class ChunkGenerationSemanticsTest {
    @Test
    public void builderCreatesADeeplyImmutableCanonicalRecord() {
        ChunkGenerationSemantics semantics = ChunkGenerationSemantics.builder(-4, 9, 12L)
                .addSurfaceBiomes(List.of("iris:wetlands", "iris:forest"))
                .addCaveBiome("iris:limestone_caves")
                .addRegion("temperate")
                .addRiverProfile("rivers/default")
                .addObject("procedural/tree/towering-oak#3@2")
                .addStructure("minecraft:village_plains", -55, 71, 151)
                .build();

        assertEquals(List.of("iris:forest", "iris:wetlands"), List.copyOf(semantics.surfaceBiomeKeys()));
        assertEquals(Set.of("iris:limestone_caves"), semantics.caveBiomeKeys());
        assertEquals(Set.of("temperate"), semantics.regionKeys());
        assertEquals(Set.of("rivers/default"), semantics.riverProfileKeys());
        assertEquals(Set.of("procedural/tree/towering-oak#3@2"), semantics.objectKeys());
        assertEquals(
                Set.of(new ChunkGenerationSemantics.StructureOccurrence(
                        "minecraft:village_plains",
                        new ChunkGenerationSemantics.BlockPosition(-55, 71, 151)
                )),
                semantics.structures()
        );
        assertThrows(UnsupportedOperationException.class, () -> semantics.surfaceBiomeKeys().add("iris:desert"));
        assertThrows(UnsupportedOperationException.class, () -> semantics.structures().clear());
    }

    @Test
    public void activationAndResourceKeysAreStrict() {
        assertThrows(IllegalArgumentException.class, () -> ChunkGenerationSemantics.builder(0, 0, 0L));
        assertThrows(IllegalArgumentException.class, () -> ChunkGenerationSemantics.builder(0, 0, -1L));
        assertThrows(
                IllegalArgumentException.class,
                () -> ChunkGenerationSemantics.builder(0, 0, 1L).addSurfaceBiome("")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> ChunkGenerationSemantics.builder(0, 0, 1L).addSurfaceBiome(" Iris:Forest")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> ChunkGenerationSemantics.builder(0, 0, 1L).addSurfaceBiome("iris:For\nest")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> ChunkGenerationSemantics.builder(0, 0, 1L).addSurfaceBiome("iris\\forest")
        );
    }

    @Test
    public void packIdentifiersPreserveCaseAndFilenameCharacters() {
        ChunkGenerationSemantics semantics = ChunkGenerationSemantics.builder(0, 0, 1L)
                .addObject("trees/mixed/AmySmol10")
                .addObject("trees/mixed/amysmol10")
                .addSurfaceBiome("mountain/Cute_Cliffs+")
                .addCaveBiome("Caves/Crystal Gallery")
                .addRegion("Région/Highlands")
                .addRiverProfile("Rivers/Cold+")
                .addStructure("Structures/Tower+", 1, 64, 1)
                .build();

        assertEquals(Set.of("trees/mixed/AmySmol10", "trees/mixed/amysmol10"), semantics.objectKeys());
        assertEquals(Set.of("mountain/Cute_Cliffs+"), semantics.surfaceBiomeKeys());
        assertEquals(Set.of("Caves/Crystal Gallery"), semantics.caveBiomeKeys());
        assertEquals(Set.of("Région/Highlands"), semantics.regionKeys());
        assertEquals(Set.of("Rivers/Cold+"), semantics.riverProfileKeys());
        assertEquals("Structures/Tower+", semantics.structures().iterator().next().key());
    }

    @Test
    public void equivalentBuilderOrderProducesEqualRecords() {
        ChunkGenerationSemantics first = ChunkGenerationSemantics.builder(2, 3, 4L)
                .addSurfaceBiome("iris:b")
                .addSurfaceBiome("iris:a")
                .addStructure("iris:tower", 34, 90, 50)
                .addStructure("iris:ruin", 35, 64, 51)
                .build();
        ChunkGenerationSemantics second = ChunkGenerationSemantics.builder(2, 3, 4L)
                .addStructure("iris:ruin", 35, 64, 51)
                .addSurfaceBiome("iris:a")
                .addStructure("iris:tower", 34, 90, 50)
                .addSurfaceBiome("iris:b")
                .build();

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    public void mergeUnionsStageFactsAndPropagatesSeal() {
        ChunkGenerationSemantics first = ChunkGenerationSemantics.builder(2, 3, 4L)
                .addSurfaceBiome("iris:forest")
                .addRegion("temperate")
                .build();
        ChunkGenerationSemantics second = ChunkGenerationSemantics.builder(2, 3, 4L)
                .addCaveBiome("iris:limestone_caves")
                .addRegion("temperate")
                .addStructure("iris:tower", 40, 90, 55)
                .seal()
                .build();

        ChunkGenerationSemantics merged = first.merge(second);

        assertEquals(Set.of("iris:forest"), merged.surfaceBiomeKeys());
        assertEquals(Set.of("iris:limestone_caves"), merged.caveBiomeKeys());
        assertEquals(Set.of("temperate"), merged.regionKeys());
        assertEquals(second.structures(), merged.structures());
        assertTrue(merged.sealed());
        assertFalse(first.sealed());
    }

    @Test
    public void sealedRecordAcceptsRetriesButRejectsNewFacts() {
        ChunkGenerationSemantics sealed = ChunkGenerationSemantics.builder(2, 3, 4L)
                .addSurfaceBiome("iris:forest")
                .addRegion("temperate")
                .seal()
                .build();
        ChunkGenerationSemantics retry = ChunkGenerationSemantics.builder(2, 3, 4L)
                .addSurfaceBiome("iris:forest")
                .build();
        ChunkGenerationSemantics newFact = ChunkGenerationSemantics.builder(2, 3, 4L)
                .addSurfaceBiome("iris:desert")
                .build();

        assertSame(sealed, sealed.merge(retry));
        assertThrows(IllegalStateException.class, () -> sealed.merge(newFact));
        assertThrows(
                IllegalStateException.class,
                () -> sealed.merge(ChunkGenerationSemantics.builder(2, 3, 5L).build())
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> sealed.merge(ChunkGenerationSemantics.builder(3, 3, 4L).build())
        );
    }
}
