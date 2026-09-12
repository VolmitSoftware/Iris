package art.arcane.iris.structure.nativegen;

import art.arcane.iris.generation.runtime.Engine;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.structure.placement.IrisStructurePlacement;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NativeStructureGenerationPolicyTest {
    @Test
    public void generationStatusMessagesAreSharedAcrossPlatforms() {
        assertEquals(
                "Native structure minecraft:village_plains is disabled by this dimension's importedStructures settings.",
                NativeStructureGenerationPolicy.generationStatusMessage(
                        "minecraft:village_plains", NativeStructureGenerationStatus.DISABLED_BY_PACK));
        assertEquals(
                "Native structure minecraft:ancient_city is replaced by an Iris placement in this pack and locates through that explicit replacement.",
                NativeStructureGenerationPolicy.generationStatusMessage(
                        "minecraft:ancient_city", NativeStructureGenerationStatus.REPLACED_BY_IRIS));
    }

    /**
     * The "blanket-disable a namespace, re-place chosen structures via nativeStructures placements"
     * pattern: generation honors the placement (the planner never consults the disable list), so the
     * policy must report those keys as REPLACED_BY_IRIS — making /iris find|goto and
     * /iris structure verify locate the placement instead of claiming the key is disabled.
     */
    @Test
    public void disabledKeyWithActivePlacementResolvesAsReplacedByIris() {
        Engine engine = engineWithDisabledNamespaceAndRegionPlacement("nova_structures:tavern_oak");

        assertEquals(NativeStructureGenerationStatus.REPLACED_BY_IRIS,
                NativeStructureGenerationPolicy.resolve(engine, "nova_structures:tavern_oak", false).status());
    }

    @Test
    public void disabledKeyWithoutPlacementStaysDisabled() {
        Engine engine = engineWithDisabledNamespaceAndRegionPlacement("nova_structures:tavern_oak");

        IrisNativeStructureDecision decision =
                NativeStructureGenerationPolicy.resolve(engine, "nova_structures:witch_villa", false);
        assertEquals(NativeStructureGenerationStatus.DISABLED_BY_PACK, decision.status());
        assertFalse(decision.generate());
    }

    @Test
    public void exactDisabledKeyDoesNotDisableSiblingVariant() {
        IrisImportedStructureControl control = new IrisImportedStructureControl();
        control.getDisabledExact().add("minecraft:ruined_portal");
        Engine engine = engineWithControlAndRegionPlacement(control, "nova_structures:tavern_oak");

        assertEquals(NativeStructureGenerationStatus.DISABLED_BY_PACK,
                NativeStructureGenerationPolicy.resolve(engine, "minecraft:ruined_portal", false).status());
        assertEquals(NativeStructureGenerationStatus.GENERATE_NATIVE,
                NativeStructureGenerationPolicy.resolve(engine, "minecraft:ruined_portal_nether", false).status());
    }

    private Engine engineWithDisabledNamespaceAndRegionPlacement(String placedKey) {
        IrisImportedStructureControl control = new IrisImportedStructureControl();
        control.getDisabled().add("nova_structures:");
        return engineWithControlAndRegionPlacement(control, placedKey);
    }

    private Engine engineWithControlAndRegionPlacement(IrisImportedStructureControl control, String placedKey) {
        IrisData data = mock(IrisData.class);
        Engine engine = mock(Engine.class);
        IrisDimension dimension = mock(IrisDimension.class);

        IrisStructurePlacement placement = new IrisStructurePlacement();
        placement.getNativeStructures().add(new IrisNativeStructure().setStructure(placedKey));
        IrisRegion region = mock(IrisRegion.class);
        KList<IrisStructurePlacement> regionPlacements = new KList<>();
        regionPlacements.add(placement);
        when(region.getStructures()).thenReturn(regionPlacements);
        KList<IrisRegion> regions = new KList<>();
        regions.add(region);

        when(engine.getData()).thenReturn(data);
        when(engine.getDimension()).thenReturn(dimension);
        when(dimension.getImportedStructures()).thenReturn(control);
        when(dimension.getStructures()).thenReturn(new KList<>());
        when(dimension.getAllRegions(engine)).thenReturn(regions);
        when(dimension.getReachableBiomes(engine)).thenReturn(new KList<>());
        return engine;
    }
}
