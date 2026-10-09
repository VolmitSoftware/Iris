package art.arcane.iris.engine.framework.structure;

import art.arcane.iris.builtin.structure.StructureAdjustment;
import art.arcane.iris.builtin.structure.StructureSetFrequency;
import art.arcane.iris.builtin.structure.StructureSettings;
import com.volmit.block.BlockSpec;
import com.volmit.block.PaletteSpec;
import com.volmit.resource.ResourceId;
import com.volmit.vanilla.StructureStilts;
import com.volmit.vanilla.StructureTerrain;
import com.volmit.vanilla.VanillaStructureAdjustment;
import com.volmit.vanilla.YBand;
import com.google.gson.Gson;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** What a dimension's iris:structure settings say about the platform's own structures (was IrisImportedStructureControlTest). */
public class ImportedStructuresTest {
    private static final StructureStilts COBBLE = StructureStilts.of(PaletteSpec.of(List.of(BlockSpec.of("minecraft:cobblestone"))));

    @Test
    public void defaultSettingsGenerateEveryStructureAndChangeNothing() {
        ImportedStructures defaults = imported(new StructureSettings());

        assertFalse(defaults.disables("minecraft:village_plains"));
        assertFalse(defaults.disables("nova_structures:desert_temple"));
        assertEquals(VanillaStructureAdjustment.NONE, defaults.adjustment("minecraft:stronghold", true));
        assertEquals(1D, defaults.setup().frequency(ResourceId.parse("minecraft:villages")), 0D);
        assertFalse(defaults.setup().protectsVanillaDefinitions());
    }

    @Test
    public void disabledMatchesFamiliesAtResourceBoundariesAndWholeNamespaces() {
        ImportedStructures imported = imported(settings(List.of("minecraft:village", "example:", "", " "), List.of(), 0, true,
                List.of(), List.of()));

        assertTrue(imported.disables("minecraft:village_plains"));
        assertTrue(imported.disables("minecraft:village_taiga"));
        assertTrue(imported.disables("example:castle"));
        assertFalse(imported.disables("minecraft:villager_outpost"));
        assertFalse(imported.disables("minecraft:stronghold"));
        assertFalse(imported.disables("other:castle"));
    }

    @Test
    public void disabledExactComparesNormalizedCompleteKeysOnly() {
        ImportedStructures imported = imported(settings(List.of(), List.of("  MINECRAFT:RUINED_PORTAL  "), 0, true,
                List.of(), List.of()));

        assertTrue(imported.disables("minecraft:ruined_portal"));
        assertTrue(imported.disables(" MINECRAFT:RUINED_PORTAL "));
        assertFalse(imported.disables("minecraft:ruined_portal_nether"));
        assertFalse(imported.disables(""));
    }

    @Test
    public void datapackOverridesFalseProtectsDefinitionsWithoutDisablingAnyNamespace() {
        ImportedStructures imported = imported(settings(List.of("aquaculture:treasure_vault"), List.of(), 0, false,
                List.of(), List.of()));

        assertTrue(imported.setup().protectsVanillaDefinitions());
        assertFalse(imported.disables("nova_structures:desert_temple"));
        assertFalse(imported.disables("minecraft:village_plains"));
        assertTrue(imported.disables("aquaculture:treasure_vault"));
    }

    @Test
    public void frequencyOverridesUseTheNormalizedSetKeyAndTheLastEntry() {
        ImportedStructures imported = imported(settings(List.of(), List.of(), 0, true, List.of(
                new StructureSetFrequency("minecraft:nether_complexes", 1.05D),
                new StructureSetFrequency(" MINECRAFT:NETHER_COMPLEXES ", 1.1D),
                new StructureSetFrequency("no namespace", 3D)), List.of()));

        assertEquals(1.1D, imported.setup().frequency(ResourceId.parse("minecraft:nether_complexes")), 0D);
        assertEquals(1D, imported.setup().frequency(ResourceId.parse("minecraft:nether_fossils")), 0D);
        assertEquals(1, imported.setup().frequencies().size());
    }

    @Test
    public void undergroundShiftAppliesOnlyToUndergroundStepsAndMatchingShiftsStack() {
        ImportedStructures imported = imported(settings(List.of(), List.of(), 8, true, List.of(), List.of(
                adjustment(List.of("minecraft:trial"), -48, null, false, null, null),
                adjustment(List.of("minecraft:trial_chambers"), -16, null, false, null, null))));

        assertEquals(-56, imported.adjustment("minecraft:trial_chambers", true).yShift());
        assertEquals(8, imported.adjustment("minecraft:stronghold", true).yShift());
        assertEquals(0, imported.adjustment("minecraft:village_plains", false).yShift());
    }

    @Test
    public void theLastMatchingTerrainBandAndStiltWin() {
        StructureTerrain encase = StructureTerrain.of(StructureTerrain.Mode.ENCASE).withPadding(4, 0, 0);
        StructureStilts deep = COBBLE.withMaxDepth(96);
        ImportedStructures imported = imported(settings(List.of(), List.of(), 0, true, List.of(), List.of(
                adjustment(List.of("minecraft:stronghold"), 0, null, false, COBBLE.withMaxDepth(32),
                        StructureTerrain.of(StructureTerrain.Mode.SOURCE)),
                adjustment(List.of("minecraft:stronghold"), 0, YBand.of(-120, -20), false, null, encase),
                adjustment(List.of("minecraft:stronghold"), 0, null, false, deep, null))));

        VanillaStructureAdjustment stronghold = imported.adjustment("minecraft:stronghold", true);
        assertEquals(Optional.of(encase), stronghold.fit().terrain());
        assertEquals(Optional.of(deep), stronghold.fit().stilts());
        assertEquals(Optional.of(YBand.of(-120, -20)), stronghold.yBand());
        assertEquals(VanillaStructureAdjustment.NONE, imported.adjustment("minecraft:trial_chambers", true));
    }

    @Test
    public void aLaterSpecificAdjustmentOverridesANamespaceTerrain() {
        ImportedStructures imported = imported(settings(List.of(), List.of(), 0, true, List.of(), List.of(
                adjustment(List.of("towns_and_towers:"), 0, null, false, null, StructureTerrain.of(StructureTerrain.Mode.VACUUM)),
                adjustment(List.of("towns_and_towers:village_ocean", "towns_and_towers:mimic_desert"), 0, null, false, null,
                        StructureTerrain.of(StructureTerrain.Mode.PRESERVE)))));

        assertEquals(StructureTerrain.Mode.VACUUM, mode(imported, "towns_and_towers:village_forest"));
        assertEquals(StructureTerrain.Mode.PRESERVE, mode(imported, "towns_and_towers:village_ocean"));
        assertEquals(StructureTerrain.Mode.PRESERVE, mode(imported, "towns_and_towers:mimic_desert"));
    }

    @Test
    public void preserveSourceYMatchesTheFamilyAndKeepsTheShifts() {
        ImportedStructures imported = imported(settings(List.of(), List.of(), -64, true, List.of(), List.of(
                adjustment(List.of("minecraft:mineshaft"), 4, null, true, null, null))));

        VanillaStructureAdjustment mesa = imported.adjustment("minecraft:mineshaft_mesa", true);
        assertTrue(mesa.keepsSourceY());
        assertEquals(-60, mesa.yShift());
        assertFalse(imported.adjustment("minecraft:stronghold", true).keepsSourceY());
    }

    @Test
    public void nullListsInThePacksJsonReadAsEmpty() {
        StructureSettings nulls = new Gson().fromJson(
                "{\"disabled\": null, \"disabledExact\": null, \"adjustments\": null, \"frequencyOverrides\": null}",
                StructureSettings.class);
        ImportedStructures imported = imported(nulls);

        assertFalse(imported.disables("minecraft:monument"));
        assertEquals(VanillaStructureAdjustment.NONE, imported.adjustment("minecraft:monument", false));
        assertTrue(imported.setup().frequencies().isEmpty());
    }

    private static StructureTerrain.Mode mode(ImportedStructures imported, String key) {
        return imported.adjustment(key, false).fit().terrain().orElseThrow().mode();
    }

    private static ImportedStructures imported(StructureSettings settings) {
        return new ImportedStructures(settings);
    }

    private static StructureSettings settings(List<String> disabled, List<String> disabledExact, int undergroundYShift,
                                              boolean datapackOverrides, List<StructureSetFrequency> frequencies,
                                              List<StructureAdjustment> adjustments) {
        return new StructureSettings(disabled, disabledExact, undergroundYShift, datapackOverrides, frequencies, adjustments);
    }

    private static StructureAdjustment adjustment(List<String> match, int yShift, YBand band, boolean preserveSourceY,
                                                  StructureStilts stilt, StructureTerrain terrain) {
        return new StructureAdjustment(match, yShift, band, preserveSourceY, stilt, terrain);
    }
}
