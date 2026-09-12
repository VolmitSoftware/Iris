package art.arcane.iris.pack.datapack;

import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DatapackStructureScopeIndexTest {
    private static final String TOWNS_URL = "https://modrinth.com/datapack/towns-and-towers";
    private static final String TAVERNS_URL = "https://modrinth.com/datapack/dungeons-and-taverns";

    @Test
    public void declaringDimensionAllowsOnlyItsManagedStructureSets() {
        DatapackStructureScopeIndex index = index(
                resource(TOWNS_URL, "towns_and_towers:villages"),
                resource(TAVERNS_URL, "nova_structures:taverns"));
        Set<String> declared = index.declaredSources(List.of(TOWNS_URL));

        assertTrue(index.allowsStructureSet("towns_and_towers:villages", declared));
        assertFalse(index.allowsStructureSet("nova_structures:taverns", declared));
        assertTrue(index.allowsStructureSet("minecraft:villages", declared));
    }

    @Test
    public void vanillaAndUnrelatedIrisDimensionsExcludeManagedSets() {
        DatapackStructureScopeIndex index = index(
                resource(TAVERNS_URL, "nova_structures:illager_barracks"));

        assertFalse(index.allowsStructureSet("nova_structures:illager_barracks", Set.of()));
        assertFalse(index.allowsStructureSet("nova_structures:illager_barracks",
                index.declaredSources(List.of(TOWNS_URL))));
        assertTrue(index.allowsStructureSet("minecraft:pillager_outposts", Set.of()));
    }

    @Test
    public void oneSourceCanBeSharedByMultipleDimensions() {
        DatapackStructureScopeIndex index = index(
                resource(TAVERNS_URL, "nova_structures:taverns"));

        Set<String> firstDimension = index.declaredSources(List.of(TAVERNS_URL));
        Set<String> secondDimension = index.declaredSources(List.of(TAVERNS_URL));

        assertTrue(index.allowsStructureSet("nova_structures:taverns", firstDimension));
        assertTrue(index.allowsStructureSet("nova_structures:taverns", secondDimension));
    }

    @Test
    public void duplicateSetOwnershipFailsClosedUntilEverySourceIsDeclared() {
        DatapackStructureScopeIndex index = index(
                resource(TOWNS_URL, "shared:watchtowers"),
                resource(TAVERNS_URL, "shared:watchtowers"));

        assertFalse(index.allowsStructureSet("shared:watchtowers",
                index.declaredSources(List.of(TOWNS_URL))));
        assertTrue(index.allowsStructureSet("SHARED:WATCHTOWERS",
                index.declaredSources(List.of(TOWNS_URL, TAVERNS_URL))));
    }

    @Test
    public void structureDefinitionsUseTheSameOwnerBoundary() {
        DatapackStructureScopeIndex index = DatapackStructureScopeIndex.create(List.of(
                new DatapackIngestService.StructureScopeResources(
                        TAVERNS_URL,
                        List.of("nova_structures:illager_barracks", "minecraft:pillager_outpost"),
                        List.of("nova_structures:illager_barracks"))));

        assertFalse(index.allowsStructure("nova_structures:illager_barracks", Set.of()));
        assertFalse(index.allowsStructure("minecraft:pillager_outpost", Set.of()));
        assertTrue(index.allowsStructure("minecraft:village_plains", Set.of()));
        assertTrue(index.allowsStructure("minecraft:pillager_outpost",
                index.declaredSources(List.of(TAVERNS_URL))));
    }

    @Test
    public void duplicateStructureOwnershipFailsClosedUntilEverySourceIsDeclared() {
        DatapackStructureScopeIndex index = DatapackStructureScopeIndex.create(List.of(
                new DatapackIngestService.StructureScopeResources(
                        TOWNS_URL, List.of("shared:watchtower"), List.of()),
                new DatapackIngestService.StructureScopeResources(
                        TAVERNS_URL, List.of("shared:watchtower"), List.of())));

        assertFalse(index.allowsStructure("shared:watchtower",
                index.declaredSources(List.of(TOWNS_URL))));
        assertTrue(index.allowsStructure("shared:watchtower",
                index.declaredSources(List.of(TOWNS_URL, TAVERNS_URL))));
    }

    @Test
    public void emptyResourcesDoNotClaimStructureSets() {
        DatapackStructureScopeIndex index = DatapackStructureScopeIndex.create(List.of(
                new DatapackIngestService.StructureScopeResources(null, List.of(), List.of()),
                new DatapackIngestService.StructureScopeResources(TOWNS_URL, null, null)));

        assertTrue(index.allowsStructureSet("claimed:structure_set", Set.of()));
        assertTrue(index.declaredSources(null).isEmpty());
        assertTrue(index.managedStructureSetCount() == 0);
    }

    private static DatapackStructureScopeIndex index(
            DatapackIngestService.StructureScopeResources... resources
    ) {
        return DatapackStructureScopeIndex.create(List.of(resources));
    }

    private static DatapackIngestService.StructureScopeResources resource(
            String source,
            String... structureSetKeys
    ) {
        return new DatapackIngestService.StructureScopeResources(
                source,
                List.of(),
                List.of(structureSetKeys));
    }
}
